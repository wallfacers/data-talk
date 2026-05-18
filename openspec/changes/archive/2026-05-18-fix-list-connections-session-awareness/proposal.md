## Why

AI 在 chat 中回答"有哪些数据库"时调用了 `datatalk_list_connections`，因为该工具响应不携带会话激活标记，模型把"返回里没有 active 字段"错误推断为"未选中数据源"，进而向用户输出"当前会话未选择任何数据源"——但实际上 UI 上明确绑定了"本地数据库"。这是一类**缺失信号 → 错误否定**的幻觉，根因在三处：

1. **工具语义模糊**：`AGENTS.md` 中 `datatalk_list_connections` 的描述（"List saved data source connections"）与 `datatalk_get_data_context`（"Read current session data context"）功能差异未在描述里说清，模型把"列出已保存连接"误当成"查看当前可用数据源"。
2. **缺少回答 Playbook**：`Identity & Hard Constraints` 提到"用户问数据库相关问题时若没有 usable 数据源，再发起 chooser"——但**没有显式要求**"先调用 `get_data_context` 确认激活态，再决定后续动作"。
3. **响应载荷缺锚点**：`ListConnectionsAction` 返回的每条连接里没有 `isActiveInSession` 标记，模型即使选错工具也无法在同一次调用里自我纠正。

要彻底解决（用户原话"测底解决"），三处都要修。

## What Changes

- **修改 `AGENTS.md` Registered Actions 工具描述**：明确区分 session-scope（如 `get_data_context`）与 global-scope（如 `list_connections`）工具；在 `list_connections` 描述中明确写出"does NOT indicate which connection is active in the current session — call `datatalk_get_data_context` first"。
- **在 `AGENTS.md` 增加 Intent Routing Gate 条目**：当用户询问"有哪些数据库 / 当前连了什么 / 现在用的什么库 / 数据源列表 / what databases / which connection"等会话归属相关问题时，**MUST** 先调用 `datatalk_get_data_context`，再决定是否调用 `datatalk_list_connections` / `datatalk_list_connection_targets`。
- **扩展 `ListConnectionsAction` 输出**：每条 connection 增加 `isActiveInSession: boolean` 字段；响应 root 增加 `activeSessionConnectionId: string | null`。后端从 `SessionDataContextRecord` 读取当前 session 选中的 connectionId 并打标记。
- **同步更新 `connection-management` SKILL.md**：在 "Session data context" 章节增加 Answer-Style Playbook 段，规定 AI 回答"有哪些 X"类问题的标准流程。
- **同步更新 i18n description**：`messages.properties` / `messages_zh_CN.properties` 中 `action.list_connections.description` 改为说明性更强的文本。
- **新增 WireMock 集成测试**：构造"已绑定 connection 的 session 询问数据库"场景，断言 AI（通过 FakeOpenCodeServer 录制的 action call 顺序）先调用 `get_data_context` 再回答；同时单元测试断言 `ListConnectionsAction` 输出携带激活标记。

## Capabilities

### New Capabilities

- `session-aware-connection-listing`: 定义 `datatalk_list_connections` 响应载荷必须携带当前 session 激活态标记的合约（顶层 `activeSessionConnectionId` + 每项 `isActiveInSession`）。

### Modified Capabilities

- `agent-skill-routing`: Registered Actions 工具表新增"session-scope vs global-scope 描述必须无歧义"的硬约束；Intent Routing Gate 新增"会话归属类问题先查 `get_data_context`"路由规则。

## Impact

- **后端代码**:
  - `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ListConnectionsAction.java` — 注入 `SessionDataContextStore`（或等价 service）读取当前 session connectionId；扩展 `outputSchema()` 与 `toMap()`；新增构造参数。
  - `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` — 修改 Registered Actions 表 + Intent Routing Gate。
  - `server/data-talk-adapter/src/main/resources/skills/connection-management/SKILL.md` — 增加 Answer-Style Playbook。
  - `server/data-talk-adapter/src/main/resources/messages.properties` & `messages_zh_CN.properties` — 更新 description 文案。
- **测试**:
  - `ListConnectionsActionTest`（新增或扩展）— 断言激活标记字段。
  - `AgentsMarkdownTest`（既有 agent-skill-routing 单测）— 校验新增条目不破坏体积上限（≤350 行）。
  - WireMock 集成测试（可选 Phase 2）— 录制 AI 调用顺序回归。
- **前端**：无影响。`list_connections` 的消费者目前只有 AI，前端 ConnectionService 走独立 REST。
- **协议**：`outputSchema` 扩展为后向兼容（新增字段不破坏既有调用者）。
- **AGENTS.md 体积**：当前 162 行（含占位符），新增条目预估 +6 行，仍远低于 350 行硬约束。
- **BUG tracker**：grep `docs/bugs/` 未发现重复 BUG；本变更内容也属于"AI 行为偏差"而非"产品功能 BUG"，按 OpenSpec 路径推进即可，不在 `docs/bugs/` 注册。
- **DATA_SOURCE_TYPE_COMPATIBILITY.md**：N/A — 本变更不引入新数据源类型，也不改 JDBC / schema discovery / SQL splitter 行为。
- **client/DESIGN.md**：N/A — 无前端 UI 改动。
