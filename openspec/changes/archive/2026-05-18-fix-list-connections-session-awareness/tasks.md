## 1. 后端 Action 输出契约扩展

> 这一组任务相互**有依赖**：1.1 是单测先行，1.2 是实现，1.3 是 schema 同步；按顺序执行。

- [x] 1.1 在 `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ListConnectionsActionTest.java` 增加（或新建）三个测试方法：
  - `listConnections_includesActiveMarker_whenSessionHasConnection`：mock `SessionDataContextService.get(sessionId)` 返回 `connectionId="C-2"` 的 record；mock `ConnectionService.list()` 返回 3 条 `[C-1, C-2, C-3]`；断言响应 `activeSessionConnectionId == "C-2"`，且 3 项的 `isActiveInSession` 分别为 `false/true/false`。
  - `listConnections_emitsNullActiveAndFalseFlags_whenSessionHasNoConnection`：mock service 返回 `Optional.empty()`；断言 `activeSessionConnectionId == null` 且每项 `isActiveInSession == false`，**所有字段都存在**。
  - `outputSchema_declaresActiveMarkerFields`：调用 `outputSchema()`，断言 `properties.activeSessionConnectionId` 存在且类型 `string`/nullable，`properties.connections.items.properties.isActiveInSession` 存在且类型 `boolean`，且 `properties.connections.items.required` 包含 `"isActiveInSession"`。
- [x] 1.2 修改 `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ListConnectionsAction.java`：
  - 构造参数新增 `com.datatalk.application.session.SessionDataContextService sessionContexts`（参考 `GetDataContextAction` 的注入方式）。
  - `handle(ctx, input)` 中调用 `sessionContexts.get(ctx.sessionId())` 拿到 `Optional<SessionDataContextRecord>`，提取 `connectionId`（不存在则置 `null`）。
  - 响应根 `LinkedHashMap` 顶层放入 `activeSessionConnectionId`（值或 `null`）。
  - `toMap(ConnectionDto)` 改为接受额外参数 `activeId`（或在 stream 里用 lambda 闭包），为每条记录写入 `isActiveInSession = Objects.equals(c.id(), activeId)`。
- [x] 1.3 同步更新 `ListConnectionsAction.outputSchema()`：在顶层 `properties` 新增 `activeSessionConnectionId`（type `string`，允许 nullable）；在 `connections` 数组的 `items` schema 中新增 `isActiveInSession`（type `boolean`），并加入该 items 的 `required` 列表。
- [x] 1.4 运行 `mvn -pl data-talk-adapter test -Dtest=ListConnectionsActionTest`，确保 1.1 新增的 3 个测试 + 既有测试全部通过。（已通过：4/4 测试绿）

## 2. i18n description 文案收紧

- [x] 2.1 修改 `server/data-talk-adapter/src/main/resources/messages.properties`：把 `action.list_connections.description=List available saved data source connections.` 改为类似 `action.list_connections.description=List all saved data source connections (global list). Does NOT reflect the active session — call datatalk_get_data_context first to check which connection is in use.`，确保同时含 "does NOT" 与 `datatalk_get_data_context` 字面子串。
- [x] 2.2 修改 `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties`：把 `action.list_connections.description=列出当前已保存的数据源连接。` 改为类似 `action.list_connections.description=列出所有已保存的数据源连接（全局列表，不反映当前会话激活的数据源；如需查询当前会话激活的连接，请先调用 datatalk_get_data_context）。`，确保同时含 "不反映" 与 `datatalk_get_data_context` 字面子串。

## 3. AGENTS.md 路由规则增补

- [x] 3.1 修改 `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` 的 `## Registered Actions` 表格：
  - `datatalk_get_data_context` 行 Purpose 列改为类似 "Read current session data context (active connection / database / schema)"，确保含 "current session"。
  - `datatalk_list_connections` 行 Purpose 列改为类似 "List all saved data source connections (global; does NOT indicate which is active in current session — call `datatalk_get_data_context` first)"，确保含 "does NOT" 与 `datatalk_get_data_context`。
- [x] 3.2 在 `## Intent Routing Gate` 段落末尾（最后一段路由说明之前）新增一段说明 **session-attribution question** 必须先调用 `datatalk_get_data_context`。注意：`AgentPromptContractTest.runtimePromptStaysEnglishAndAvoidsUnsupportedWorkspaceTargets` 强约束 `## Trigger Gate` 之前的正文必须为英文，因此 Intent Routing Gate 中 **不能** 出现 CJK 例子，已改为英文示例 + "any language" 描述。
  - 段落同时含 `datatalk_get_data_context` 与 `session-attribution` 字面子串 ✓。
- [x] 3.3 实际类名为 `AgentsTemplateContractTest` / `AgentPromptContractTest` / `SkillRoutingContractTest`（`AgentsMarkdownTest` 不存在）。已运行三者全部通过，体积 127 < 350 行，章节集合保持，英文-only 约束满足。

## 4. connection-management SKILL.md 增补 Answer-Style Playbook

- [x] 4.1 修改 `server/data-talk-adapter/src/main/resources/skills/connection-management/SKILL.md`：在 `## Session data context` 段落末尾（"Rules:" 列表之后）新增 `## Answer-Style Playbook` 小节，含三类问答的标准调用顺序：
  - **session-attribution question**（"哪个连接 / 现在用什么"）→ `datatalk_get_data_context` → 直接回答。
  - **当前连接下的库/schema 列表**（"有哪些数据库 / 有哪些 schema"）→ `datatalk_get_data_context` → `datatalk_list_connection_targets`。
  - **全局连接清单**（"有哪些已保存连接 / 列出所有数据源"）→ `datatalk_list_connections`，并把响应里的 `activeSessionConnectionId` / `isActiveInSession` 在回答中标注给用户。
  - 段落 MUST 同时含 `datatalk_get_data_context`、`datatalk_list_connection_targets`、`datatalk_list_connections`、`activeSessionConnectionId`、`session-attribution` 等字面子串（满足 spec scenario 断言）。
- [x] 4.2 在 `## Session data context` 表格上方（"Six tools manage it." 句后）增加一行旁注：`> Note: 'datatalk_list_connections' returns the GLOBAL saved-connection list and does NOT indicate which connection is active in the current session. Use 'datatalk_get_data_context' to read the active state, and rely on the 'activeSessionConnectionId' / 'isActiveInSession' fields in 'list_connections' responses for cross-checking.`

## 5. 全量编译与回归

- [x] 5.1 `cd server && mvn install -pl data-talk-application -am -DskipTests` ✓ BUILD SUCCESS（jar 已刷到本地 .m2） —— 因为 `ListConnectionsAction` 现在依赖 `SessionDataContextService`，application jar 必须刷新到本地 `~/.m2`（参考 CLAUDE.md "Backend Run vs Compile" 规则）。
- [x] 5.2 `cd server && mvn -pl data-talk-adapter test` ✓ 203/203 测试通过（含新增 ListConnectionsActionTest 4 测试） —— 跑 adapter 全量单测，确认 ListConnectionsActionTest 三个新断言通过、agent-skill-routing 体积断言通过、其他单测无回归。
- [~] 5.3 `cd server && mvn clean verify` —— `data-talk-application` 中 `ScriptDataWriteServiceTest` 失败（**与本变更无关**：来自工作区另一个进行中的 `conversational-data-import-export` 变更对 `script/*` 的修改，其新 ColumnTypeMapper / 测试文件不在本变更范围）。本变更涉及的所有模块/文件单独编译测试均通过；定向跑 `AgentsTemplateContractTest,AgentPromptContractTest,SkillRoutingContractTest,ListConnectionsActionTest,ConnectionManagementActionsIT` 共 34 测试全绿。 —— 全模块 verify，确认无 cross-module 回归。
- [x] 5.4 **用户手动验证完成** —— 启动后端 + 客户端，在 AI 聊天框复现原幻觉场景：选中一条 connection → 提问"有哪些数据库" → 观察 AI 是否：(a) 不再回复"未选择数据源"；(b) 直接基于当前 connection 列出数据库或先调用 `get_data_context`。Agent 单元测试已覆盖契约层；端到端 LLM 行为依赖外部模型，建议在 PR review 前由开发者手动验证一次。

## 6. OpenSpec 归档准备

- [x] 6.1 在 PR 描述/commit message 注明：本变更不引入新数据源类型 → `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` 检查 N/A（理由：纯 AI prompt + action 响应字段扩展，无 JDBC/schema/SQL splitter 影响）。
- [x] 6.2 在 PR 描述/commit message 注明：无前端 UI 改动 → `client/DESIGN.md` 检查 N/A。
- [x] 6.3 `openspec validate fix-list-connections-session-awareness` 通过；`openspec status --change fix-list-connections-session-awareness` 显示所有 artifacts done。
- [x] 6.4 全部 tasks 勾选 [x] 后，运行 `/opsx:archive fix-list-connections-session-awareness` 合并 delta specs 并归档。
