---
id: BUG-0007
title: 后端重启后 MCP bridge nonce 漂移导致 datatalk_* 工具全部 -32001 unauthenticated bridge
status: fixed
priority: P0
source: manual-report
modules:
  - opencode
  - mcp-bridge
discovered: 2026-05-08
discoveredBy: human
testRunId: null
fixCommit: 4168e3f9
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

`OpenCodeBootstrapWriter.write()` 每次 spring-boot 启动都无条件生成新 UUID 写到 plugin 文件并 `rotateNonce()` 进 server 内存。在 `datatalk.opencode.serve.enabled=false`（外部 OpenCode 模式）下，OpenCode 进程是用户单独启动的长生命周期进程，启动时把 plugin 中的 nonce 锁进 ESM 闭包；后端重启会改写文件 nonce 但 OpenCode 进程内的 nonce 不变，于是 plugin 注入的 `__dtBridgeNonce` 与 server-side `bridgeStatus.bridgeNonce()` 不一致，所有 `datatalk_*` MCP 工具调用永久返回 `-32001 unauthenticated bridge`，直到 OpenCode 进程也跟着重启。

## Reproduction Steps

1. 设置 `datatalk.opencode.serve.enabled=false` 并先单独把 OpenCode 进程拉起（监听 4096）
2. 启动 DataTalk 后端（首次启动会写 plugin + nonce 与 OpenCode 加载的一致）
3. 重启 DataTalk 后端（不重启 OpenCode）
4. 在 DataTalk AI 对话里调用任意 `datatalk_*` 工具（例如 `datatalk_get_data_context` / `datatalk_list_connections`）

## Expected vs Actual

- **Expected**: 后端重启不影响已存活的 OpenCode 与 server 之间的 MCP bridge 认证，工具调用正常返回结果
- **Actual**: 调用立即失败 `McpError: MCP error -32001: unauthenticated bridge`；server 日志会出现 `[mcp-bridge] unauthenticated bridge tool=... expectedNoncePresent=true receivedNoncePresent=true`

## Environment

- Backend commit: `5fefaba5` (develop)
- Frontend commit: `5fefaba5` (develop, with WIP local changes)
- OS: Linux 6.6.87.2-microsoft-standard-WSL2
- OpenCode binary: v1.4.7 进程残留（serve.version 配置 1.14.41，`.current` 指向 v1.14.41，但用户进程是 v1.4.7 历史孤儿）
- Data source: N/A（认证层问题，未触达数据库）

## Evidence

进程 / 文件时间线（实际现场观察）：

```
14:12  OpenCode v1.4.7 进程 PID 10602 启动，监听 4096，加载当时的 plugin（旧 nonce N1，已锁进 ESM 闭包）
14:41  DataTalk 后端最近一次启动：
       - bootstrapReconciler.writeManagedConfig(8080)
         → OpenCodeBootstrapWriter.write():
            String nonce = UUID.randomUUID().toString();  // 生成 N2
            bridgeStatus.rotateNonce(nonce);               // server 内存 = N2
            writeTextFile(pluginFile, pluginContent(nonce));  // plugin 文件 = N2
14:42  Spring 容器 ready，但 OpenCode 进程仍持有 N1
14:43+ 用户调用 datatalk_get_data_context / datatalk_list_connections
       → plugin 注入 __dtBridgeNonce=N1
       → server compare(N1, N2) → -32001
```

文件证据：

```
$ ls -l ~/.data-talk/opencode/plugins/datatalk-mcp-context.js
-rw------- 1 ... 608 May  8 14:41 datatalk-mcp-context.js  ← plugin 在 14:41 被改写

$ grep BRIDGE_NONCE ~/.data-talk/opencode/plugins/datatalk-mcp-context.js
const BRIDGE_NONCE = '021baa57-1058-4f90-b1eb-314cac67a53a'  ← 14:41 写入的 N2

$ ps -p 10602 -o lstart,cmd
... May  8 14:12 ... opencode serve --port 4096 ...           ← 14:12 启动，持有 N1
```

设计文档对此场景的预期：`docs/product-specs/2026-04-24-opencode-mcp-tool-migration-design.md` §6.3 "nonce 校验" 把 plugin 文件设为 nonce 的权威源；当前实现违反了这一不变量（每次 write 都生成新 UUID 而非以文件为准）。

## Root Cause

`server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeBootstrapWriter.java:73`

```java
public BootstrapArtifacts write(int serverPort) throws IOException {
    Path configDir = properties.resolveConfigDir();
    Files.createDirectories(configDir);

    String nonce = UUID.randomUUID().toString();   // ← 每次都生成新值
    bridgeStatus.rotateNonce(nonce);
    ...
    writeTextFile(pluginFile, pluginContent(nonce));
    ...
}
```

OpenCode 在启动时把 plugin 当 ESM 模块加载一次，`BRIDGE_NONCE` 进入闭包不会随文件变化更新。在外部 OpenCode 模式（`serve.enabled=false`）下，后端无法重启 OpenCode 进程，因此后端任何重启都会让 plugin 内存 nonce 与 server 内存 nonce 永久不一致。

## Fix

让 `OpenCodeBootstrapWriter.write()` 优先从既有 plugin 文件读取并复用合法 nonce，仅在文件不存在 / 损坏 / 格式无法识别时才生成新 UUID：

1. 在生成新 UUID 之前，尝试用正则从 `pluginFile` 解析 `BRIDGE_NONCE = '...'`
2. 解析成功且值是非空字符串 → 用既有 nonce
3. 否则 → fallback 到 `UUID.randomUUID().toString()`
4. 用最终 nonce 调用 `bridgeStatus.rotateNonce(nonce)`，并继续后续 plugin/instructions/config 写入流程
5. 既有 plugin 文件内容保持不变时，避免无意义重写（保留 mtime，使被 OpenCode 文件 watcher 误触发的概率最小）

测试新增：第二次 `write(8080)` 复用第一次 nonce；删除 plugin 文件后再次 `write(8080)` 生成新 nonce。

## Verification

- 单元测试：`OpenCodeBootstrapWriterTest` 4 个测试通过（新增 `reusesExistingPluginNonceOnSubsequentWrite` + `generatesNewNonceWhenPluginFileMissing`）
- 全量验证：`mvn verify` BUILD SUCCESS（4 模块编译 + 单元 + 集成测试 173/173，0 失败，2 预存 skipped）
- 实景验证（待用户重启后回填）：DataTalk 后端 + OpenCode 双方都重启一次让 plugin 在 OpenCode 进程闭包内同步 N1；之后只重启 DataTalk 后端，调用 `datatalk_get_data_context` 应正常返回（不再 -32001）

## Notes

- 历史孤儿 OpenCode 进程 v1.4.7（与配置 `serve.version: 1.14.41` 不一致）是次要现象，本 BUG 不处理；用户后续重启应让 v1.14.41 真正接管
- 当前修复假设 plugin 文件是 nonce 的权威源（与设计 spec §6.3 一致）。任何能改 plugin 文件的本机 actor 同样能改任何东西，因此复用文件 nonce 不引入新攻击面
- `verified` 推进留待用户在重启后实景调用 `datatalk_get_data_context` / `datatalk_list_connections` 确认不再返回 -32001 之后再做
