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
- `GET /mcp`：如 OpenCode remote transport 需要，可提供 SSE/streamable HTTP 兼容入口（具体形式取决于 §9 钉死的 transport 版本）

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

#### 1.1 `tools/list` 过滤来源（避免悬空白名单）

为避免"哪些 action 暴露给 MCP"散落在多处硬编码，`ActionDescriptor` 新增一个布尔字段 `exposeToMcp`：

```java
public record ActionDescriptor(
    ...,
    RiskLevel riskLevel,
    Category category,
    boolean exposeToMcp        // NEW — false 表示该 action 不出现在 tools/list
) {}
```

- 现有所有 `@DataTalkAction` 注解默认 `exposeToMcp = true`
- `datatalk.demo.echo` 等已 deprecated 的诊断 action 显式 `exposeToMcp = false`
- `tools/list` 唯一过滤条件：`registry.all().filter(ActionDescriptor::exposeToMcp)`
- 不在 controller 里再写一份"哪些 action 跳过"的硬编码

#### 1.2 无会话上下文阶段的 `/mcp` 行为

OpenCode 启动后第一次拉 `tools/list` 时还没有任何 OC session，自然不会有 §2 的隐藏字段。`/mcp` 端点的会话桥接策略必须区分方法：

| MCP method | 是否需要 session 桥接 | 缺失 `__dt*` 字段时的行为 |
|------------|----------------------|--------------------------|
| `initialize` / `notifications/initialized` | 否 | 正常返回 |
| `tools/list` | 否 | 正常返回过滤后的 descriptor |
| `tools/call` | 是 | 返回 MCP 标准错误：`-32602 invalid params: missing session context` |

这条边界让 OpenCode 启动顺序与首次模型调用的时序解耦：listing 永远可用，调用永远走桥接。

### 2. 用 OpenCode plugin 注入隐藏上下文，而不是污染 schema

DataTalk 会向 OpenCode config dir 写入一个本地 plugin，例如 `plugins/datatalk-mcp-context.js`。

职责：

- 仅拦截 `datatalk_*` MCP tools
- 在 `tool.execute.before` 中向 `output.args` 注入保留字段：
  - `__dtOpenCodeSessionId` — 来自 hook 提供的 `input.sessionID`
  - `__dtCallId` — 来自 hook 提供的 `input.callID`
  - `__dtBridgeNonce` — 来自启动时随机生成、与 DataTalk MCP server 共享的一次性进程级 nonce（详见 §7）

#### 2.1 隐藏字段防伪与覆盖契约（trust 仅来自 plugin）

模型理论上可以在工具入参里自行写入 `__dtOpenCodeSessionId` 等字段。设计上必须假设这一定会发生，因此 MCP server 与 plugin 之间约定一条强一致流程：

1. **plugin 在注入前 strip**：plugin hook 必须先从 `output.args` 删除任何 `__dt*` 前缀字段，再注入自己的版本。这保证"plugin 路径上的 input 不会带模型预置的伪造值"
2. **MCP server 在校验前再 strip 一次**：MCP server 收到 `tools/call` 的第一步是把 args 中所有 `__dt*` 字段抽出来放到一个独立 `BridgeFields` 对象，原 args 中删除；之后无论是 schema 校验还是 ActionDispatcher 执行，都看不到任何 `__dt*` 字段
3. **nonce 校验**：MCP server 验证 `BridgeFields.__dtBridgeNonce` 与本进程启动时生成的 nonce 完全相等，否则按 `-32001 unauthenticated bridge` 拒绝。即便有外部进程能直连 `/mcp` 端点（参见 §7 的网络层防御），由于 nonce 仅以本机文件形式落到 plugin，外部进程无法仿造 plugin 的注入

#### 2.2 `tools/call` 的处理流程

DataTalk MCP server 在 `tools/call` 的处理流程改为：

1. 抽出并删除入参中所有 `__dt*` 字段，存入 `BridgeFields`
2. 校验 `BridgeFields.__dtBridgeNonce`，失败 → `-32001`
3. 用 `OpenCodeSessionMap.dataTalkFor(openCodeSessionId)` 反查 DataTalk session id；若为 null → `-32002 unknown opencode session`
4. 用剥离后的原始 input 按现有 `ActionDescriptor.inputSchema()` 校验
5. 构造 `ActionContext(sessionId, callId, ..., openCodeSessionId, ...)`
6. 复用现有 `ActionDispatcher`，按 §6 的同步等待协议返回结果

这样做的结果：

- 模型看到的 schema 仍然与现有 action schema 一致；不暴露任何上下文字段
- DataTalk MCP server 在 `tools/call` 拿到的是可信的真实调用上下文（plugin 注入 + nonce 校验）
- SERVER / OPENCODE action 保持现有行为
- CLIENT action 仍然通过 `DtEvent.ActionInvoke` 下发到前端，client handler id 继续保持 `datatalk.ui.*`；同步等待协议见 §6
- session data context、artifact session 归属（artifact producedBy）、风险元数据、`ActionInvocationRepository` 落账都不需要重写

### 3. OpenCode 启动与 bootstrap 顺序

#### embedded OpenCode

DataTalk 统一引入一个 DataTalk-managed OpenCode config dir，默认建议收口到 `~/.data-talk/opencode/`，并让 embedded 进程显式使用它：

- `OPENCODE_CONFIG=<config-dir>/opencode.json`
- `OPENCODE_CONFIG_DIR=<config-dir>`

启动顺序固定为：

1. DataTalk 自己的 `/mcp` 端点已可接收请求（`tools/list` 立刻可用，参见 §1.2）
2. 生成 `__dtBridgeNonce` 并暂存于 MCP server 进程内
3. 写入 `plugins/datatalk-mcp-context.js`，文件内容把 nonce 以**仅当前用户可读权限（0600）**写入
4. 写入 / 合并 `opencode.json` 中的 `mcp.datatalk`（合并算法见 §3.1）
5. 写入 `AGENTS.md`
6. 启动 embedded `opencode serve`
7. OpenCode health ready 后，再启动 `/global/event` event loop

其中 `opencode.json` 至少包含：

- `mcp.datatalk = { type: "remote", url: "http://127.0.0.1:<port>/mcp", enabled: true }`（端口从 DataTalk 自己的 `server.port` 读取，不硬编码 8080）

embedded 模式下不再执行 `registerTools()`。

#### external OpenCode

external 模式不能依赖 DataTalk 进程直接控制 OpenCode 启动命令，因此策略改为”配置对齐 + 运行时 reconcile”：

1. DataTalk 向约定的 OpenCode config dir 写入 `AGENTS.md`、plugin（含本进程 nonce）、本地 `opencode.json` 片段
2. DataTalk 对外部 OpenCode 调 `PATCH /config`，把 `mcp.datatalk` 合并进其运行配置，触发 OpenCode invalidate / reload
3. DataTalk 立即再调用 `POST /mcp` 作为 runtime reconcile，确保当前进程内 MCP client 已 connect
4. 只有在 reconcile 完成后，DataTalk 才创建/使用 OpenCode session

这里的关键判断是：

- `PATCH /config` 解决”持久配置”
- `POST /mcp` 解决”当前进程立即可用”

DataTalk 对 external 的可保证边界定义为：

- 若 external OpenCode 使用 DataTalk 约定的 config dir，prompt 与 plugin 都能被加载，session-bridge 完整可用
- 若 external OpenCode 使用完全不同的 config dir，DataTalk 仍可通过 `PATCH /config` + `POST /mcp` 挂上 remote MCP server，但无法承诺其会自动加载 DataTalk 写出的 `AGENTS.md` / plugin 文件
- **plugin 加载失败的可观测性**：external bootstrap 完成后，DataTalk 触发一次 §风险 2 的 health-probe（`datatalk_get_data_context`）。若调用因 nonce 缺失而走 `-32001`，DataTalk 进入 degraded mode 并向前端 surfaces 明确诊断，而不是默默假装 MCP 已可用

因此，external 模式新增一个明确配置契约：

- 外部 OpenCode 若要获得完整 DataTalk MCP 能力，必须与 DataTalk 共享同一个 OpenCode config dir，或显式把该 dir 告诉 DataTalk

#### 3.1 `opencode.json` 合并算法（embedded / external 通用）

DataTalk 持有的字段范围**仅限** `mcp.datatalk`、`agents`（路径指向 DataTalk 写出的 `AGENTS.md`）、`plugins`（数组中追加 DataTalk 写出的 plugin 文件路径）。其它任何字段一律视为用户字段，不读、不改、不删。

合并算法（伪代码）：

```text
read existing opencode.json (or {} if missing)
backup → opencode.json.dt-bak-<ISO timestamp>   # 仅当文件存在且本次会改动时
deep-merge:
    config.mcp = config.mcp ?? {}
    config.mcp.datatalk = OUR_DESIRED_DATATALK_BLOCK    # 整块覆盖（DataTalk 拥有此键）
    if OUR_AGENTS_PATH not in config.agents:            # 数组去重追加
        config.agents = (config.agents ?? []) + [OUR_AGENTS_PATH]
    if OUR_PLUGIN_PATH not in config.plugins:
        config.plugins = (config.plugins ?? []) + [OUR_PLUGIN_PATH]
write atomically (write to .tmp, fsync, rename)
```

约束：

- **整块覆盖 vs 深合并**：`mcp.datatalk` 是 DataTalk 拥有的整块对象，每次启动直接覆盖；`agents`、`plugins` 是用户与 DataTalk 共享的数组，按路径去重追加
- **不删除**：若用户手动从 `mcp.datatalk` 改成别的结构，DataTalk 仍按”整块覆盖”恢复；若用户手动把 DataTalk 的 plugin 路径从 `plugins` 数组里删掉，DataTalk 不强加回（用户显式拒绝时尊重之，但启动时会 health-probe 失败并 degraded）
- **原子写**：`.tmp + rename` 防止崩溃半写；`opencode.json.dt-bak-*` 留 1 份滚动备份用于回退
- **并发写**：embedded 模式下 DataTalk 是 config 的唯一写者，不需额外锁；external 模式下若用户也在编辑，DataTalk 仅在启动 / reconcile 时各写一次，不轮询、不持续抢锁，并在每次写之前重新读最新内容做 deep-merge

#### 3.2 `OpenCodeSessionMap` 的写入时机（防 race）

`OpenCodeSessionMap.bind(dt, oc)` 必须在向 OpenCode 发出”会触发模型自主调工具”的请求**之前**完成。具体规约：

- `ChannelService` 创建 OpenCode session 后立刻 `bind`，**先 bind、后向 OpenCode 转发任何 user message**
- 若有任何路径绕过 `ChannelService` 创建 OC session（包括恢复历史 / 接管已存在 session），必须在使用前由对应入口显式 `bind`
- MCP server `tools/call` 在 `dataTalkFor(...)` 返回 null 时直接 `-32002`（参见 §2.2 step 3），不做任何”等一会儿再试”的隐式重试

这条规约把”mapping 必然在 tools/call 之前就绪”变成入口侧的责任，MCP server 端只做硬校验。

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

- `client/src/features/chat/components/tools/renderers/index.ts` 中所有 `ToolRegistry.register(...)` 调用的注册键，必须从今天的"裸短名"全量 rename 到带 `datatalk_` 前缀的 MCP 终名。**这是注册键的 hard rename，不是新增映射**——遗漏一个就会让对应工具静默 fallback 到默认 renderer
- `client/src/features/actions/ui-handlers.ts` 与 client handler registration 不改 action id（继续 `datatalk.ui.*`）

renderer 注册键 rename 清单（必须全部完成才视为切完）：

| 旧注册键（今天） | 新注册键（迁移后） |
|----------------|------------------|
| `execute_sql` | `datatalk_execute_sql` |
| `preview_sql` | `datatalk_preview_sql` |
| `describe_table` | `datatalk_describe_table` |
| `list_tables` | `datatalk_list_tables` |
| `show_schema` | `datatalk_show_schema` |
| `artifact_created` | `datatalk_artifact_created` |
| `question` | `datatalk_question` |
| `read` | `datatalk_read` |

补充约束：

- renderer 注册逻辑保留单一文件入口（`renderers/index.ts`），不再额外维护"短名 → 长名"的兼容层
- 新增 vitest：`registerBuiltInRenderers` 后所有注册键都以 `datatalk_` 开头，且与 backend `/mcp` 暴露的 `tools/list` 名集合精确对齐（通过 fixture 校验）

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

### 6. MCP `tools/call` 响应模型与 CLIENT action 等待协议

CLIENT executor 类型今天通过 `PendingCallRegistry` + `desc.timeoutMs()` 提供同步等待语义：legacy `ToolCallController` 直接把 `ActionDispatcher.dispatch(...)` 返回的 `CompletionStage<Object>` 透传到 HTTP 响应里，OpenCode 在另一端阻塞等回执。新的 MCP 链路必须**保留同一同步语义**，否则 CLIENT action 在 MCP 路径上会语义失真。

#### 6.1 同步等待协议

`/mcp` 端点对 `tools/call` 的处理是"长 HTTP 响应 + Spring async/`DeferredResult`"，行为定义如下：

| executor 类型 | `tools/call` 响应时机 |
|-------------|----------------------|
| SERVER | dispatcher 返回的 `CompletionStage` 完成后立即响应 |
| OPENCODE | 同上 |
| CLIENT | 等 `PendingCallRegistry` 解析（前端 `action_result` 到达），或 `desc.timeoutMs()` 触发超时后响应 |

实现规约：

- `/mcp` controller 返回 `DeferredResult<...>`，timeout 设置为 `desc.timeoutMs() + 5s`（小幅 buffer 容纳网络抖动），到点未完成时返回 MCP 标准错误 `-32003 client action timed out` 而不是让 HTTP 层 502
- CLIENT 超时与 HTTP 超时统一由 dispatcher 一侧控制，HTTP 层不重复实现倒计时
- 期间不向 MCP client 推任何 progress 通知（保持"最小 MCP server"，不引入 streaming 复杂度）；如未来需要，再走 `notifications/progress` 升级，本期不做

#### 6.2 前端断线 / 拒绝 / 异常的 fallback

| 场景 | 行为 |
|------|------|
| 前端 `action_result` 携带成功结果 | `tools/call` 返回 `{ content: [...] }` |
| 前端 `action_result` 携带错误结果 | `tools/call` 返回 `{ content: [...], isError: true }` |
| 前端在 timeout 前未回执 | `tools/call` 返回 `-32003` |
| 前端 SSE 断线 / SessionBus 没有订阅者 | dispatcher 立刻 fail：`PendingCallRegistry` 检测到无消费者时通过 `whenComplete` 走 fail 分支；`tools/call` 返回 `-32004 no client subscriber` |

约束：

- CLIENT action 不会因为 HTTP 端 timeout 而"成功了但模型已经看不到结果"——pending future 与 HTTP 响应是同一个 `CompletionStage`，谁先完成谁定结果
- `ActionInvocationRepository` 在所有上述路径都正常落账（已被 `ActionDispatcher.whenComplete` 覆盖）

### 7. `/mcp` 端点的访问控制

`shared-secret` 与 `ToolCallController` 删除后，`/mcp` 是新链路上**唯一可被外部触达的 SQL/UI 执行入口**。设计必须显式定义其威胁模型与防御层。

#### 7.1 威胁模型

- **可信**：本机内运行、由 DataTalk 写出 plugin、且持有当前进程 nonce 的 OpenCode 实例
- **不可信**：本机或局域网其他进程；浏览器跨站请求；未持有 nonce 的任意 HTTP 客户端
- 不在威胁模型内：拥有本机用户级文件访问权的攻击者（这种情况整个 DataTalk 进程信任域已破，配置 / 凭据 / 数据库连接全部暴露，不试图防御）

#### 7.2 防御层（叠加生效）

1. **网络层**：embedded 模式下 `/mcp` 默认 bind `127.0.0.1`，不监听公网。external 模式若 OpenCode 跨机部署需要远端可达，必须显式配置 `datatalk.mcp.bind = 0.0.0.0` 并同时启用第 3 层 token
2. **CSRF / Origin 层**：`/mcp` 拒绝带 `Origin` 头但 origin 不在白名单的请求，挡住浏览器跨站调用（OpenCode 是 server-side 客户端，不会带 `Origin`）
3. **应用层 nonce**（参见 §2.1）：`tools/call` 必须携带 `__dtBridgeNonce` 且与本进程 nonce 完全相等。nonce 仅以 0600 文件落到 plugin，每次 DataTalk 启动重新生成

`tools/list` 与 `initialize` 不要求 nonce，但仍受网络层与 Origin 层约束。

#### 7.3 不做的事

- 不引入 OAuth / mTLS / JWT 等重型机制（与"最小 MCP server"原则不符）
- 不复用 legacy `shared-secret` 配置项（参见 §9.2 的 deprecation 路径）

### 8. 前提条件与设计输入

下列项目在进入实施拆分前必须确认；任一项与设计预设不符都需要回到设计阶段：

| 输入 | 预设 | 验证方式 |
|------|------|---------|
| OpenCode MCP client transport | `streamable-http`（POST `/mcp` JSON-RPC + 可选 SSE upgrade） | 阅读 OpenCode 当前版本 MCP client 源码，记录到 `docs/references/opencode-protocol.md` |
| OpenCode plugin hook 名 | `tool.execute.before` | 同上 |
| OpenCode plugin 文件加载形式 | ES module，从 `OPENCODE_CONFIG_DIR/plugins/*.js` 自动加载 | 同上 |
| OpenCode `PATCH /config` 是否触发 mcp client invalidate | 是 | 实测 + 文档化 |
| OpenCode `POST /mcp`（runtime mount）端点存在 | 是 | 同上 |

实施拆分的任务 1 第一步必须把上述 5 项落到 `docs/references/opencode-protocol.md`，否则后续编码全部基于假设。

### 9. 兼容性与配置项 deprecation

#### 9.1 新增配置

- `datatalk.mcp.enabled` — 默认 `true`；保留是为了关键回退（若 MCP server 出现严重 bug，可临时关闭整条 MCP 路径，但同时 OpenCode 将完全看不到工具，仅供应急）
- `datatalk.mcp.bind` — 默认 `127.0.0.1`，仅 external + 跨机场景才允许 `0.0.0.0`
- `datatalk.opencode.config-dir` — DataTalk-managed config dir 路径，默认 `${user.home}/.data-talk/opencode/`

#### 9.2 删除 / 弃用

- `datatalk.opencode.shared-secret` — 删除。处理路径：
  - 启动时若检测到该配置项**仍被显式设置**（非默认），打印 WARN 一次："datatalk.opencode.shared-secret is removed since MCP migration; remove from your application.yml"
  - Spring Boot `@ConfigurationProperties` binding 下未知字段默认被忽略，因此存留不会启动失败
  - release notes 与 [docs/exec-plans/tech-debt-tracker.md](../exec-plans/tech-debt-tracker.md) 显式登记
- `datatalk.opencode.plugin-callback-base` — 同上处理

#### 9.3 grace window

本期为 hard cutover：legacy `/plugin/register-tool`、`/api/opencode-tool/*`、`shared-secret` 校验、`ToolCallBridge` 在同一发布版本内全部移除，不保留并行运行窗口。理由：

- 双注册会导致同名工具重复暴露（参见目标 5）
- 应用层目前只有 DataTalk 一个客户端，没有外部消费方需要兼容期
- legacy 与 MCP 并存的状态机比"两条都活"或"两条都死"都更脆弱

## 风险与控制

### 风险 1：工具名字改了，但内部 action id 没改，前后端联动断裂

控制措施：明确区分“外部 MCP 名”和“内部 action id”，并分别补测试。OpenCode message renderer 走 MCP 名，`action.invoke` 继续走内部 id。

### 风险 2：external OpenCode 没加载 DataTalk plugin，导致工具可见但 session-scoped action 失真

控制措施：

- external 模式显式定义 config-dir 共享契约
- runtime reconcile 后，新增一次 bootstrap health-probe：通过 OpenCode `POST /session/{id}/message` 请求执行 `datatalk_get_data_context`（纯读、必经 session bridge、无副作用），若 MCP server 返回 `-32001 unauthenticated bridge` 即说明 plugin 未加载或 nonce 不一致
- 若 health-probe 失败，直接进入 degraded mode 并向前端 surface 明确诊断（而非沉默 WARN 日志）；degraded mode 下 DataTalk UI 给出可见 banner，告知"AI 工具桥未就绪，会话执行不可用"

### 风险 3：启动顺序回退，embedded 首轮对话拿不到工具

控制措施：embedded 模式只允许“先写 config / plugin，再起 OpenCode”；禁止恢复成启动后才挂 MCP。

### 风险 4：MCP schema 漂移，prompt 或测试继续写旧名

控制措施：保留并升级 `AgentPromptContractTest`，新增断言：

- `AGENTS.md` 中所有 `datatalk_*` 引用必须能在 `tools/list` 返回集合中找到对应项
- `tools/list` 返回的每个生产工具必须在 `AGENTS.md` 中至少出现一次
- 新增前端 vitest：`registerBuiltInRenderers` 注册的所有键集合 ≡ backend `tools/list` 名集合（通过 fixture 校验）

### 风险 5：CLIENT action 在 MCP 路径上响应模型错位

场景：`tools/call` 因为 HTTP 超时被中断，但前端事后回执，导致 `ActionInvocationRepository` 记成功而模型已收到错误。

控制措施：

- `/mcp` 的 `DeferredResult` 与 dispatcher 返回的 `CompletionStage` 共用同一个完成源——HTTP timeout 与 dispatch timeout 不允许各自独立计时
- `PendingCallRegistry` 在 future 已完成后再收到迟到 `action_result` 时，丢弃该 result 并打 WARN，而不是再次尝试解析或写库

### 风险 6：external 模式下 `opencode.json` 与用户字段冲突

场景：用户在 `opencode.json` 里手动维护 `mcp.something`、`agents`、`plugins`，DataTalk 写入时覆盖 / 删除用户字段。

控制措施：

- 严格遵守 §3.1 的"DataTalk 持有字段范围"——`mcp.datatalk` 整块覆盖；`agents` / `plugins` 仅去重追加；其它字段一律不读不改不删
- 每次 DataTalk 实际改写文件前先写一份 `opencode.json.dt-bak-<timestamp>`
- `OpenCodeBootstrapWriter` 必须实现 idempotency：连续两次写入同一份期望状态产生相同文件内容（含字节序），用于幂等性测试

## 实施拆分

下列任务**有显式依赖关系**，不可任意并行；任务内部 sub-step 列出可并行项。

### 任务 0（前置）：钉死 OpenCode 协议输入

依赖：无。**必须先完成**，才能开始任务 1。

- 阅读 OpenCode 当前版本源码，把 §8 表中 5 项 transport / hook / 加载形式 / `PATCH /config` / `POST /mcp` 的实测结果落到 `docs/references/opencode-protocol.md`
- 任务输出：`opencode-protocol.md` 新增/更新章节，列出每项的精确签名与示例 payload

### 任务 1：建立 MCP server 与 action→MCP mapping

依赖：任务 0。

可并行 sub-steps：
- 1a. `ActionDescriptor` 增 `exposeToMcp` 字段；现有 `@DataTalkAction` 默认 true，`datatalk.demo.echo` 显式 false
- 1b. 新增 `/mcp` controller 与最小协议实现（`initialize` / `tools/list` / `tools/call`）；CLIENT 用 `DeferredResult` 接 dispatcher 返回值
- 1c. 集中维护 `McpNameMapper`：内部 action id ↔ raw MCP name ↔ OpenCode final name 的双向映射
- 1d. `tools/call` 实现 §2.2 的 strip → nonce → SessionMap → schema → dispatch 流程；3 类错误码（-32001/-32002/-32003/-32004）覆盖完整

### 任务 2：建立上下文桥接与 bootstrap

依赖：任务 1（plugin 注入需要 nonce，nonce 在 MCP server 进程内生成）。

可并行 sub-steps：
- 2a. 写 OpenCode plugin 文件模板（含 `__dtBridgeNonce` 占位与 strip-then-inject 实现）
- 2b. `OpenCodeBootstrapWriter`：实现 §3.1 的合并算法 + 原子写 + 备份 + 幂等性测试
- 2c. embedded 模式切到 config-first startup（按 §3 embedded 7 步顺序）
- 2d. external 模式新增 `OpenCodeBootstrapReconciler`：`PATCH /config` → `POST /mcp` → health-probe（参见风险 2）
- 2e. `OpenCodeSessionMap` 写入时机审计：`ChannelService` 与所有创建 OC session 的入口必须先 bind 后转发（参见 §3.2）

### 任务 3：删除 legacy 链路并更新全量测试

依赖：任务 1 + 任务 2 全部完成（hard cutover，没有并行运行窗口）。

可并行 sub-steps：
- 3a. 删除 `OpenCodeGateway.registerTools()`、`OpenCodeHttpClient.registerTool(...)`、`ToolCallController`、`ToolCallBridge`、`shared-secret`、`plugin-callback-base` 配置项
- 3b. 更新 `AGENTS.md` 全量改名到 `datatalk_*`；升级 `AgentPromptContractTest`（参见风险 4）
- 3c. 前端 renderer 注册键 hard rename（参见 §5 表格）+ 新增 fixture 校验
- 3d. WireMock smoke：embedded 启动顺序、external reconcile + health-probe、CLIENT action 同步等待 + 超时
- 3e. 启动时 WARN 已被设置的 `datatalk.opencode.shared-secret` / `plugin-callback-base`（参见 §9.2）

## 成功标准

- DataTalk 不再调用 `POST /plugin/register-tool`，仓库内 grep `/plugin/register-tool` 仅出现在 changelog
- DataTalk 不再暴露 `/api/opencode-tool/{actionId}`，对应 controller 类已删
- embedded 模式中，OpenCode 启动完成后的首轮会话即可看到 `datatalk_*` MCP tools（smoke 验证：进程 ready 后 ≤2s 内 `tools/list` 返回完整集合）
- external 模式中，DataTalk 在首轮会话前完成 MCP reconcile + health-probe；health-probe 失败时 UI 显示明确 degraded banner
- `datatalk.ui.*` CLIENT action 仍能真正触发前端 handler；同步等待 / 超时 / 前端断线 三类路径分别有自动化测试覆盖
- `tools/call` 拒绝不带 `__dtBridgeNonce` 的请求（-32001 测试覆盖）
- `tools/list` 与 `AGENTS.md` 双向引用一致，由 `AgentPromptContractTest` 自动断言
- 前端 `registerBuiltInRenderers` 后注册键集合与 backend `tools/list` 完全一致（vitest 断言）
- `opencode.json` 合并算法对"用户已有非 DataTalk 字段"的 fixture 输入产生幂等输出，且不丢失用户字段（单测覆盖）
