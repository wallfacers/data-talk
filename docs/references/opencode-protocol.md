# OpenCode MCP Integration Reference

本文档记录 DataTalk 迁移到 MCP single-path 时依赖的 OpenCode 行为。结论基于 2026-04-24 对上游 OpenCode 源码快照 `5c5069b6227c` 的本地核对，重点核实以下前提：

- remote MCP transport 选择顺序
- plugin hook 名与调用时机
- local plugin 加载位置
- `PATCH /config` 的更新语义
- `POST /mcp` 的 runtime mount 能力

## 1. OpenCode 侧已确认的行为

### 1.1 Remote MCP transport

- `remote` MCP server 的配置结构来自 `packages/opencode/src/config/mcp.ts`，关键字段为 `type=remote`、`url`、`headers`、`oauth`、`timeout`。
- OpenCode 在 `packages/opencode/src/mcp/index.ts` 中连接 remote MCP 时，先尝试 `StreamableHTTPClientTransport`，失败后再回退到 `SSEClientTransport`。
- 对 DataTalk 的含义：后端 `/mcp` 入口应优先兼容 streamable HTTP，请求/响应形态上不要依赖 SSE-only 语义。

### 1.2 Runtime config update

- OpenCode instance 路由暴露 `GET /config` 与 `PATCH /config`。
- `PATCH /config` 的 request body 使用完整 `Config.Info` schema 校验，不是局部 patch schema。
- `Config.update()` 会把传入配置与现有 `config.json` 深合并写回，然后调用 `Instance.dispose()`。
- 对 DataTalk 的含义：external 模式可以采用 `PATCH /config` 持久化 MCP 配置，但要把它视为一次 instance reload/invalidate 边界。

### 1.3 Runtime MCP mount

- OpenCode instance 路由暴露 `GET /mcp` 与 `POST /mcp`。
- `POST /mcp` request body 形如：

```json
{
  "name": "datatalk",
  "config": {
    "type": "remote",
    "url": "http://127.0.0.1:8080/mcp",
    "headers": {
      "X-DataTalk-Mcp-Nonce": "nonce-value"
    }
  }
}
```

- 成功响应是按 server name 返回的状态 map，例如：

```json
{
  "datatalk": {
    "status": "connected"
  }
}
```

- `GET /mcp` 同样返回 name -> status map，可用于 reconcile 后 health probe。
- 对 DataTalk 的含义：external 模式无需重启 OpenCode 进程即可补挂 DataTalk MCP server。

### 1.4 Plugin loading

- OpenCode 官方插件文档声明两类本地自动加载目录：
  - 项目级：`.opencode/plugins/`
  - 全局级：`~/.config/opencode/plugins/`
- `packages/opencode/src/config/plugin.ts` 进一步确认，OpenCode 会扫描 config dir 下的 `plugin/*.{ts,js}` 与 `plugins/*.{ts,js}`。
- `packages/opencode/src/config/paths.ts` 说明 config dir 来源包含：
  - 全局 config dir
  - 向上查找得到的项目 `.opencode`
  - `OPENCODE_CONFIG_DIR`（若显式设置）
- 对 DataTalk 的含义：bootstrap 只需要把托管插件写进 OpenCode config dir 下的 `plugins/` 即可，不需要额外私有加载协议。

### 1.5 Plugin hook names and timing

- OpenCode 在 `packages/opencode/src/session/prompt.ts` 中对 registry tool 和 MCP tool 都触发：
  - `tool.execute.before`
  - `tool.execute.after`
- 对 MCP tool，`tool.execute.before` 发生在实际 `execute()` 之前，hook 输入里含 `tool`、`sessionID`、`callID`，并可对待执行参数对象做改写；`tool.execute.after` 在执行完成后拿到结果。
- 对 DataTalk 的含义：bridge plugin 可以在 `tool.execute.before` 注入隐藏字段，例如 OpenCode session id、call id 与 nonce，而无需暴露给模型 prompt 或 action schema。

### 1.6 MCP tool naming

- OpenCode 在 `packages/opencode/src/mcp/index.ts` 中对 server name 与原始 MCP tool name 做同一套 sanitize：
  - 非 `[A-Za-z0-9_-]` 字符全部替换成 `_`
- 对外暴露的最终工具名格式为：

```text
<sanitized_server_name>_<sanitized_tool_name>
```

- 例如：
  - MCP server `datatalk` + tool `execute_sql` -> `datatalk_execute_sql`
  - MCP server `datatalk` + tool `ui_read` -> `datatalk_ui_read`
- 对 DataTalk 的含义：后端、prompt、frontend renderer 必须统一切到 `datatalk_*` 命名；旧的 `datatalk.*` 只保留在 DataTalk 内部 action id / CLIENT handler id。

## 2. DataTalk 实现约束

### 2.1 Naming boundary

- DataTalk action 真名继续保留点号命名，例如 `datatalk.execute_sql`、`datatalk.ui.read`。
- MCP 暴露层通过 `McpNameMapper` 做稳定映射：
  - internal action id -> MCP logical tool name
  - MCP logical tool name + OpenCode sanitize 结果 -> OpenCode runtime tool name
- 前端 built-in renderer 注册键使用 OpenCode 运行时工具名，即 `datatalk_execute_sql`、`datatalk_read_file` 这类下划线形式。

### 2.2 Bootstrap / reconcile strategy

- embedded 模式：优先写入托管 config/plugin，再启动 `opencode serve`。
- external 模式：优先 `PATCH /config`，随后 `POST /mcp` 做 runtime reconcile，最后用 `GET /mcp` 或等价 health probe 检查状态。
- 因为 `PATCH /config` 会触发 instance dispose，`POST /mcp` 不能被当成持久化替代，只能补齐当前 runtime。

### 2.3 Hidden bridge fields

- bridge plugin 注入的 `__dt*` 隐藏字段只存在于 `tools/call` 请求路径。
- DataTalk `/mcp` server 在 schema 校验前先 strip 掉这些隐藏字段，并在独立安全校验里验证：
  - nonce
  - OpenCode session -> DataTalk session 映射
  - loopback / origin gate
- 这些字段不进入 `ActionDescriptor.inputSchema`，也不暴露给模型。

## 3. Minimal request examples

### 3.1 External mode config patch

`PATCH /config` 使用完整 config schema；下例只展示与 DataTalk 相关的最小片段：

```json
{
  "mcp": {
    "datatalk": {
      "type": "remote",
      "url": "http://127.0.0.1:8080/mcp",
      "headers": {
        "X-DataTalk-Mcp-Nonce": "nonce-value"
      }
    }
  }
}
```

### 3.2 Runtime MCP add

```json
{
  "name": "datatalk",
  "config": {
    "type": "remote",
    "url": "http://127.0.0.1:8080/mcp",
    "headers": {
      "X-DataTalk-Mcp-Nonce": "nonce-value"
    }
  }
}
```

响应示例：

```json
{
  "datatalk": {
    "status": "connected"
  }
}
```

### 3.3 Runtime MCP status

```json
{
  "datatalk": {
    "status": "connected"
  }
}
```

## 4. Current implementation note

- 当前没有证据表明 DataTalk 必须为 MCP 单独开第二个 listener。
- 因此本期默认把 `/mcp` 挂在现有 Spring Boot 端口上，并优先用 per-route gate 实现 loopback-only 访问控制。
- 若后续实测发现 OpenCode remote MCP client 对当前 Spring MVC/SSE 组合存在不可绕过的兼容性问题，再升级为独立 listener 方案；在那之前不提前引入额外端口与进程复杂度。
