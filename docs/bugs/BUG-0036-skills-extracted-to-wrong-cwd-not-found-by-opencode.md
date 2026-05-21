---
id: BUG-0036
title: bezel / data-ingestion skill 解压到 JVM cwd 而非 OpenCode 进程 cwd，OpenCode 找不到 skill
status: fixed
priority: P1
source: manual-report
modules: [ingestion, opencode]
discovered: 2026-05-13
discoveredBy: human
testRunId: null
fixCommit: da41f530
fixPlanRef: null
duplicateOf: null
regression: false
---

## Summary

后端启动时 `OpenCodeProcessManager.doStart()` 用 `Paths.get("")`（= JVM 当前工作目录）作为 skill 解压根目录，但随后 ProcessBuilder 把 OpenCode 子进程的 cwd 切到 `~/.data-talk/opencode/`。两者不一致，导致 bezel / data-ingestion skill 实际被解压在了 backend 启动目录下（例如 `server/data-talk-adapter/.opencode/skills/`），OpenCode 子进程在自己的 cwd 下找 `.opencode/skills/` 始终找不到。

## Reproduction Steps

1. 在干净环境下用 `mvn spring-boot:run -pl data-talk-adapter` 启动 backend。
2. 后端日志显示 `Extracted bundled NPM deps ...` 之类成功信息。
3. 检查 `~/.data-talk/opencode/.opencode/skills/data-ingestion/` 是否存在。

## Expected vs Actual

- **Expected**：`~/.data-talk/opencode/.opencode/skills/data-ingestion/SKILL.md` 存在；OpenCode 子进程能正常加载 data-ingestion / bezel skill。
- **Actual**：上述路径不存在；skill 实际被解压在了 `server/data-talk-adapter/.opencode/skills/data-ingestion/`（即启动 Maven 命令的工作目录）。

## Environment

- Backend commit: 693dae39（修复前 HEAD）
- Frontend commit: N/A
- OS / Browser: Linux WSL2
- Data source: N/A（启动期 skill 解压链路）

## Evidence

- 启动后 OpenCode 实际 cwd 下查不到 skill：

  ```
  $ ls ~/.data-talk/opencode/.opencode/
  ls: cannot access ...: No such file or directory
  ```

- skill 被错误地解压到 backend 工作目录：

  ```
  $ find server -path '*/.opencode/skills/data-ingestion*' | head
  server/data-talk-adapter/.opencode/skills/data-ingestion/SKILL.md
  server/.opencode/skills/data-ingestion/SKILL.md
  ```

## Root Cause

`server/data-talk-infrastructure/.../OpenCodeProcessManager.doStart()` 中：

```java
binaryResolver.ensureBezelSkill(Paths.get(""));          // bug
binaryResolver.ensureDataIngestionSkill(Paths.get(""));  // bug
...
ProcessBuilder pb = new ProcessBuilder(cmd)
    .directory(homeDir.resolve(OpenCodeBinaryResolver.OPENCODE_DIR).toFile())
    .redirectErrorStream(true);
```

`Paths.get("")` 解析为 JVM 当前工作目录（启动 Maven 时一般是 `server/data-talk-adapter/`），而 OpenCode 子进程 cwd 是 `~/.data-talk/opencode/`。两路径不一致，skill 解压目标 ≠ OpenCode 加载目标，导致 OpenCode 永远读不到内置 skill。

底层单元测试 `OpenCodeBinaryResolverDataIngestionTest` 用 `@TempDir tmp` 调用 `ensureDataIngestionSkill(tmp)`，覆盖了 `ensureXxxSkill` 自身行为，但**没有覆盖调用方传入的路径是否正确**，所以这条 bug 一直没被回归测试发现。

## Fix

`OpenCodeProcessManager.doStart()` 抽出 `opencodeCwd` 变量，使 skill 解压目录与 OpenCode 子进程 cwd 强制保持一致：

```java
Path opencodeCwd = opencodeWorkingDir(homeDir);  // = homeDir.resolve(".data-talk/opencode")
binaryResolver.ensureBezelSkill(opencodeCwd);
binaryResolver.ensureDataIngestionSkill(opencodeCwd);
...
ProcessBuilder pb = new ProcessBuilder(cmd)
    .directory(opencodeCwd.toFile())
    ...
```

新增 package-private 静态助手 `OpenCodeProcessManager#opencodeWorkingDir(Path)` 集中表达"OpenCode 子进程 cwd"这一概念，并删除不再使用的 `java.nio.file.Paths` import。

新增防回归单测 `opencodeWorkingDirIsUnderHomeDataTalkSoSkillsAlignWithProcessCwd`，断言 `opencodeWorkingDir(home) == home.resolve(".data-talk/opencode")`，锁定不变量"skill 解压根目录 = OpenCode 进程 cwd"。

## Verification

- `cd server && mvn -pl data-talk-infrastructure test`：相关测试通过。
- 手工验证：clean 启动后 `~/.data-talk/opencode/.opencode/skills/data-ingestion/SKILL.md` 存在。

## Notes

- bezel skill 沿用同一调用点，bug 范围一致，已在同一 commit 内修复。
- 历史误生成产物（`server/data-talk-adapter/.opencode/`、`server/.opencode/`）未在本 fix 中清理，避免误删用户本地实验数据。可单独清理；建议同时在 `.gitignore` 加入 `**/.opencode/` 防止误提交（gitignored 后这些目录不会再随仓库进入版本控制）。
- 单元层 `OpenCodeBinaryResolverDataIngestionTest` / `OpenCodeBinaryResolverBezelTest` 保持不变，它们覆盖的是 resolver 自身行为；本 fix 的回归测试聚焦在调用方传入路径正确性。
