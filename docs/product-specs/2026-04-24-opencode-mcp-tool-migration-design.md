# OpenCode MCP Tool Migration Design

## 背景

DataTalk 当前向 OpenCode 暴露工具的链路是：

`OpenCodeGateway.registerTools()` → `POST /plugin/register-tool` → OpenCode `GET /global/event` → DataTalk `POST /api/opencode-tool/{actionId}`

这条 legacy plugin-tool 链路目前可用，且已经有 smoke 覆盖注册、event loop、schema 精确性与回调执行。但它有两个长期问题：

- OpenCode 已原生支持 MCP，继续维护 plugin callback 链路会让 DataTalk 长期背两套入口
- 一旦 MCP 与 legacy 注册并存，同一个 `datatalk.*` action 可能被 OpenCode 同时看见两次，出现重复暴露与优先级不确定

另外，本次迁移还有两个已经确认的硬约束：

- OpenCode 的 MCP 工具对外名称不是 `datatalk.execute_sql`，而是 `serverName_toolName` 形式，例如 `datatalk_execute_sql`
- DataTalk 的大量 action 是 session-scoped 的。`execute_sql`、`get_data_context`、`datatalk.ui.*` 都依赖当前会话；单纯把工具搬到 remote MCP，如果拿不到当前 OpenCode session，就只是“工具看起来挂上了”，实际能力会失真

## 目标

1. DataTalk 仅通过 MCP 单一路径向 OpenCode 暴露 AI 可用工具
2. embedded OpenCode 模式下，工具在 OpenCode 启动完成后立刻可用，不依赖“启动后再补挂”的竞态
3. external OpenCode 模式下，DataTalk 仍能把 MCP 工具挂上去，并在首个会话使用前完成可用性对齐
4. 保留现有 ActionRegistry、SessionBus、CLIENT executor、artifact / risk / session context 语义
5. 删除 legacy tool 注册 / HTTP callback 链路与对应测试基线
6. 更新提示词、tool renderer、smoke 与契约测试到新的 MCP naming

## 非目标

- 不保留 legacy plugin tool 与 MCP 双注册模式
- 不重做 DataTalk action 模型，也不把内部 action id 全量改成下划线
- 不改写 OpenCode `/global/event` 消费链路；event loop 仍然保留
- 不保证“任意一个完全不受 DataTalk 管控、且使用未知 config dir 的外部 OpenCode 进程”也能自动加载 DataTalk 的 prompt/plugin 文件

## 关键约束与设计依据

### 1. OpenCode MCP tool naming 已固定为下划线前缀风格

OpenCode 在 MCP client 侧用 `serverName_toolName` 组装最终工具名，并会做 sanitize。因此 DataTalk 若以名为 `datatalk` 的 MCP server 暴露 `execute_sql`，OpenCode 最终对模型暴露的是 `datatalk_execute_sql`。

这意味着：

- 提示词必须改成 `datatalk_execute_sql` / `datatalk_ui_read` 风格
- 前端聊天区中依赖 tool name 的 renderer / metadata 显示需要识别新的 MCP 名
- DataTalk 内部 action id 不必同步改名，外部 MCP 名与内部 action id 可以是两套稳定映射

### 2. embedded 模式必须采用“先配 MCP，再起 OpenCode”

`open-db-studio` 的参考实现不是启动后再 `POST /mcp` 动态补挂，而是：

1. 先起本地 MCP server
2. 把 `mcp` 配置写进 `opencode.json`
3. 再启动 `opencode serve`

DataTalk 的 embedded 模式必须遵守同样顺序，否则首轮对话是否能看到工具会取决于补挂时序。

### 3. remote MCP 不会天然带上 DataTalk 所需的会话上下文

OpenCode remote MCP 配置支持静态 `headers`，但这些 header 是 server-level 的，不是 per-chat session 的。DataTalk 当前 action 执行依赖：

- DataTalk session id
- OpenCode session id
- call id

若没有上下文桥接，MCP server 无法知道“当前是哪一个 DataTalk 会话在调用这个工具”。

### 4. OpenCode plugin hook 是本次会话桥接的关键

OpenCode 在执行 MCP tool 前会触发 `tool.execute.before` hook，并把：

- `input.sessionID`
- `input.callID`
- 当前 tool name

传给 plugin，plugin 可以在真正调用 MCP tool 之前修改 `output.args`。这提供了一个可行桥：

- 模型看到的 MCP schema 保持纯净，不额外暴露内部上下文字段
- OpenCode plugin 在执行前把隐藏上下文字段注入 args
- DataTalk MCP server 从隐藏字段恢复 OpenCode session / call id，再映射回 DataTalk session

## 设计方案

### 方案选择

采用“DataTalk remote MCP server + OpenCode bootstrap config + 会话桥接 plugin”的单路径方案。

不选择的方案：

- legacy 与 MCP 双开：会造成同名工具重复暴露，长期不可维护
- 仅靠 remote MCP 静态 headers：无法携带 per-session 上下文
- 把 `sessionId` 暴露为每个工具的显式 schema 参数：会污染工具 schema，让模型承担本不该承担的上下文参数

### 1. 建立 DataTalk 自己的 `/mcp` 端点

后端新增一个最小 MCP remote server，暴露：

- `POST /mcp`：处理 `initialize`、`notifications/initialized`、`tools/list`、`tools/call`
- `GET /mcp`：如 OpenCode remote transport 需要，可提供 SSE/streamable HTTP 兼容入口

MCP server 不重新发明工具定义来源，仍以 `ActionRegistry` 为唯一真源。

映射规则：

- 内部 action id：继续保留现有形式，例如 `datatalk.execute_sql`、`datatalk.ui.read`
- MCP raw tool name：移除 `datatalk.` 前缀，并将剩余 `.` 转为 `_`
- OpenCode 最终可见名：固定为 `datatalk_<rawToolName>`

示例：

| 内部 action id | MCP raw tool name | OpenCode 最终 tool name |
|---------------|-------------------|--------------------------|
| `datatalk.execute_sql` | `execute_sql` | `datatalk_execute_sql` |
| `datatalk.get_data_context` | `get_data_context` | `datatalk_get_data_context` |
| `datatalk.ui.read` | `ui_read` | `datatalk_ui_read` |

原则：

- MCP 暴露的 input/output schema 直接来自现有 `ActionDescriptor`
- 不在对模型公开的 schema 中加入上下文字段
- `datatalk.demo.echo` 这类已经不属于生产工具面的 action，不重新带入 MCP

### 2. 用 OpenCode plugin 注入隐藏上下文，而不是污染 schema

DataTalk 会向 OpenCode config dir 写入一个本地 plugin，例如 `plugins/datatalk-mcp-context.js`。

职责：

- 仅拦截 `datatalk_*` MCP tools
- 在 `tool.execute.before` 中向 `output.args` 注入保留字段，例如：
  - `__dtOpenCodeSessionId`
  - `__dtCallId`
- 其值分别来自 OpenCode hook 提供的 `input.sessionID` 与 `input.callID`

这样做的结果：

- 模型看到的 schema 仍然与现有 action schema 一致
- DataTalk MCP server 能在 `tools/call` 时拿到真实调用上下文
- `ActionInvocationRepository`、artifact producedBy、CLIENT action pending call 等现有链路仍能继续使用 call id

DataTalk MCP server 在 `tools/call` 的处理流程改为：

1. 读取保留字段 `__dtOpenCodeSessionId` / `__dtCallId`
2. 用 `OpenCodeSessionMap.dataTalkFor(openCodeSessionId)` 反查 DataTalk session id
3. 从入参中剥离保留字段
4. 用剥离后的原始 input 按现有 `ActionDescriptor.inputSchema()` 校验
5. 构造 `ActionContext(sessionId, callId, ..., openCodeSessionId, ...)`
6. 复用现有 `ActionDispatcher`

这样：

- SERVER action 保持现有行为
- CLIENT action 仍然通过 `DtEvent.ActionInvoke` 下发到前端，client handler id 继续保持 `datatalk.ui.*`
- session data context、artifact session 归属、风险元数据都不需要重写

### 3. OpenCode 启动与 bootstrap 顺序

#### embedded OpenCode

DataTalk 统一引入一个 DataTalk-managed OpenCode config dir，默认建议收口到 `~/.data-talk/opencode/`，并让 embedded 进程显式使用它：

- `OPENCODE_CONFIG=<config-dir>/opencode.json`
- `OPENCODE_CONFIG_DIR=<config-dir>`

启动顺序固定为：

1. DataTalk 自己的 `/mcp` 端点已可接收请求
2. 写入 / 合并 `opencode.json` 中的 `mcp.datatalk`
3. 写入 `AGENTS.md`
4. 写入 `plugins/datatalk-mcp-context.js`
5. 启动 embedded `opencode serve`
6. OpenCode health ready 后，再启动 `/global/event` event loop

其中 `opencode.json` 至少包含：

- `mcp.datatalk = { type: "remote", url: "http://127.0.0.1:8080/mcp", enabled: true }`

embedded 模式下不再执行 `registerTools()`。

#### external OpenCode

external 模式不能依赖 DataTalk 进程直接控制 OpenCode 启动命令，因此策略改为“配置对齐 + 运行时 reconcile”：

1. DataTalk 向约定的 OpenCode config dir 写入 `AGENTS.md`、plugin、本地 `opencode.json` 片段
2. DataTalk 对外部 OpenCode 调 `PATCH /config`，把 `mcp.datatalk` 合并进其运行配置，触发 OpenCode invalidate / reload
3. DataTalk 立即再调用 `POST /mcp` 作为 runtime reconcile，确保当前进程内 MCP client 已 connect
4. 只有在 reconcile 完成后，DataTalk 才创建/使用 OpenCode session

这里的关键判断是：

- `PATCH /config` 解决“持久配置”
- `POST /mcp` 解决“当前进程立即可用”

DataTalk 对 external 的可保证边界定义为：

- 若 external OpenCode 使用 DataTalk 约定的 config dir，prompt 与 plugin 都能被加载，session-bridge 完整可用
- 若 external OpenCode 使用完全不同的 config dir，DataTalk 仍可通过 `PATCH /config` + `POST /mcp` 挂上 remote MCP server，但无法承诺其会自动加载 DataTalk 写出的 `AGENTS.md` / plugin 文件

因此，external 模式新增一个明确配置契约：

- 外部 OpenCode 若要获得完整 DataTalk MCP 能力，必须与 DataTalk 共享同一个 OpenCode config dir，或显式把该 dir 告诉 DataTalk

### 4. 现有 DataTalk 类职责的收缩与替换

#### 保留

- `OpenCodeEventLoop`
- `OpenCodeSessionMap`
- `ChannelService` 对 OpenCode session 的创建 / 发消息 / abort / history 查询
- `ActionRegistry`
- `ActionDispatcher`

#### 收缩

`OpenCodeGateway` 不再负责 tool push，只保留：

- `createOpenCodeSession()`
- `forwardUserMessage(...)`
- `deleteOpenCodeSession(...)`
- `abortOpenCodeSession(...)`
- `listMessages(...)`

#### 删除

- `OpenCodeGateway.registerTools()`
- `OpenCodeHttpClient.registerTool(...)`
- `ToolCallController`
- `ToolCallBridge`
- `datatalk.opencode.plugin-callback-base`
- `datatalk.opencode.shared-secret`

#### 新增

- `DataTalkMcpController` / `DataTalkMcpService`：实现最小 MCP remote server
- `McpActionBridge`：把 `tools/call` 映射回 `ActionDispatcher`
- `OpenCodeBootstrapWriter`：负责写 `opencode.json`、`AGENTS.md`、plugin 文件
- `OpenCodeBootstrapReconciler`：embedded / external 统一 bootstrap 与 runtime reconcile
- `OpenCodeHttpClient` 新增 `GET/PATCH /config`、`GET/POST /mcp` 等调用

### 5. Prompt、renderer 与测试的联动规则

#### Prompt

`server/data-talk-adapter/src/main/resources/agents/AGENTS.md` 中所有工具引用统一改为 MCP naming：

- `datatalk_execute_sql`
- `datatalk_get_data_context`
- `datatalk_ui_read`
- `datatalk_ui_patch`
- `datatalk_ui_exec`
- `datatalk_ui_list`

Prompt 不需要暴露隐藏上下文字段，也不要求模型显式传 `sessionId`。

#### Frontend renderer

聊天区 tool renderer 如果依赖 tool name，需要识别新的 MCP tool 名。注意区分两条链：

- OpenCode message/tool part：看到的是 `datatalk_execute_sql` 这类 MCP 名
- DataTalk `action.invoke` 下发到前端的 CLIENT action：仍然是 `datatalk.ui.read` 这类内部 action id

所以：

- `client/src/features/chat/components/tools/**` 需要对新 MCP tool name 做映射
- `client/src/features/actions/ui-handlers.ts` 与 client handler registration 不改 action id

#### 测试基线

要从“验证 legacy callback 链路”改为“验证 MCP 单路径”：

- backend 契约测试：
  - `tools/list` 暴露的是生产 action，不含 demo tool
  - `datatalk_ui_*` 等 schema 与现有 action schema 精确一致
  - `tools/call` 经隐藏上下文桥接后能真正 dispatch 到 action
- bootstrap / smoke：
  - embedded 模式验证 DataTalk 先写 config/plugin 再起 OpenCode
  - external 模式验证 DataTalk 会先 `PATCH /config` 再 `POST /mcp` reconcile
  - event loop 仍然连接成功
- prompt contract：
  - `AGENTS.md` 中引用的是 MCP 名
  - prompt 中列出的 tool 与 registry/MCP name mapping 对齐
- frontend：
  - tool renderer 识别新的 MCP 名
  - client handler registration 测试继续验证 `datatalk.ui.*`

## 风险与控制

### 风险 1：工具名字改了，但内部 action id 没改，前后端联动断裂

控制措施：明确区分“外部 MCP 名”和“内部 action id”，并分别补测试。OpenCode message renderer 走 MCP 名，`action.invoke` 继续走内部 id。

### 风险 2：external OpenCode 没加载 DataTalk plugin，导致工具可见但 session-scoped action 失真

控制措施：

- external 模式显式定义 config-dir 共享契约
- runtime reconcile 后，新增一次 bootstrap 健康检查，至少验证一个需要 session bridge 的轻量 tool 能成功执行
- 若健康检查失败，直接进入 degraded mode 并给出明确日志，不默默假装 MCP 已可用

### 风险 3：启动顺序回退，embedded 首轮对话拿不到工具

控制措施：embedded 模式只允许“先写 config / plugin，再起 OpenCode”；禁止恢复成启动后才挂 MCP。

### 风险 4：MCP schema 漂移，prompt 或测试继续写旧名

控制措施：保留并升级 `AgentPromptContractTest`，让 prompt 文本与 action→MCP mapping 自动校验。

## 实施拆分

### 任务 1：建立 MCP server 与 action→MCP mapping

- 新增 `/mcp` controller 与最小协议实现
- 增加 action id ↔ raw MCP name ↔ OpenCode final name 的集中映射
- 用 `tools/list` / `tools/call` 取代 legacy callback

### 任务 2：建立上下文桥接与 bootstrap

- 写 OpenCode plugin 文件并注入隐藏上下文
- 写 / 合并 `opencode.json`
- embedded 模式切到 config-first startup
- external 模式增加 config patch + MCP reconcile

### 任务 3：删除 legacy 链路并更新全量测试

- 删除 `/plugin/register-tool` 注册逻辑与 `/api/opencode-tool/*`
- 更新 prompt、renderer、smoke、契约测试
- 完成 embedded / external 两种模式的回归验证

## 成功标准

- DataTalk 不再调用 `POST /plugin/register-tool`
- DataTalk 不再暴露 `/api/opencode-tool/{actionId}`
- embedded 模式中，OpenCode 启动完成后的首轮会话即可看到 `datatalk_*` MCP tools
- external 模式中，DataTalk 在首轮会话前完成 MCP reconcile，并对失败场景给出明确 degraded signal
- `datatalk.ui.*` CLIENT action 仍能真正触发前端 handler，不发生静默丢失
- prompt、MCP tool name、renderer、测试全部切到新 naming，仓库内不再把 legacy callback 链路当成主路径
