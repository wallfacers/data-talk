## ADDED Requirements

### Requirement: SQL 执行入口必须区分 callerKind

`ExecuteSqlAction` 的所有调用路径 SHALL 通过 `ActionContext.metadata().callerKind()` 区分用户主动触发（USER）与 AI 工具调用（AI），且 callerKind 由入口强制注入。

#### Scenario: query_editor "运行"路径必须为 USER

- **GIVEN** 用户在 query_editor 中点击"运行"
- **WHEN** 请求进入 `SqlExecuteController.execute()`
- **THEN** controller 构造的 ActionContext SHALL 满足 `metadata().callerKind() == USER`
- **AND** 即使前端遗留代码仍发送 `req.source = "ai"`，也 SHALL 被强制覆盖为 USER

#### Scenario: chat-sql-execute-direct 打开的编辑器执行路径

- **GIVEN** 用户在 AI chat 中点击代码块"执行 SQL"按钮，触发 `openDirectSqlQueryEditorTab` 后用户再点编辑器"运行"
- **WHEN** SQL 提交到 `POST /sql/execute`
- **THEN** callerKind SHALL 为 USER（因为最终是用户点的"运行"）
- **AND** 该路径不受 BulkSqlGuard 限制

#### Scenario: AI MCP 工具调用路径必须为 AI

- **GIVEN** AI 通过 OpenCode 调用 `datatalk_execute_sql` 工具
- **WHEN** `McpActionBridge` 构造 ActionContext 分发到 `ExecuteSqlAction`
- **THEN** ActionContext SHALL 满足 `metadata().callerKind() == AI`
- **AND** 该路径受 BulkSqlGuard 限制
