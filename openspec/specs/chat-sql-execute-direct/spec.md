## ADDED Requirements

### Requirement: "执行 SQL"按钮点击后直接打开查询编辑器

AI 聊天消息中的 SQL 代码块（L1 风险，即 SELECT/EXPLAIN/SHOW/DESCRIBE）渲染的"执行 SQL"按钮，点击后 SHALL 直接调用 `openDirectSqlQueryEditorTab` 打开一个新的查询编辑器 tab，SHALL NOT 将 SQL 作为文本消息发送给 AI。

#### Scenario: L1 SELECT 点击"执行 SQL"打开编辑器

- **GIVEN** AI 回复包含 ` ```sql SELECT * FROM users``` ` 代码块
- **AND** `classifySqlRisk` 返回 `'L1'`
- **WHEN** 用户点击"执行 SQL"按钮
- **THEN** 系统 SHALL 调用 `openDirectSqlQueryEditorTab` 创建新查询编辑器 tab
- **AND** 编辑器 SQL 内容 SHALL 为 `SELECT * FROM users`
- **AND** 编辑器 `source` SHALL 为 `'user'`
- **AND** 编辑器 `entryMode` SHALL 为 `'direct_sql'`
- **AND** SHALL NOT 向 AI 发送任何消息

#### Scenario: composer 非空时也直接开编辑器

- **GIVEN** 用户在 composer 输入框中已有文本 "帮我查一下"
- **AND** AI 回复包含 ` ```sql SELECT COUNT(*) FROM orders``` ` 代码块
- **WHEN** 用户点击"执行 SQL"按钮
- **THEN** 系统 SHALL 打开新的查询编辑器 tab（行为与 composer 为空时一致）
- **AND** composer 中的已有文本 SHALL 保持不变

### Requirement: 查询编辑器 SHALL 继承当前 session 的数据上下文

点击"执行 SQL"打开的查询编辑器 SHALL 继承当前活跃 session 的 `connectionId`、`database`、`schema`。

#### Scenario: 有活跃 session 时继承完整上下文

- **GIVEN** 当前活跃 session 的数据上下文为 `{ connectionId: 'conn-1', database: 'mydb', schema: 'public' }`
- **WHEN** 用户在 AI 聊天中点击"执行 SQL"按钮
- **THEN** 打开的查询编辑器 SHALL 使用 `connectionId: 'conn-1'`、`database: 'mydb'`、`schema: 'public'`

#### Scenario: 无连接时提示用户

- **GIVEN** 当前 session 没有关联的 `connectionId`（即 `connectionId` 为 `null`）
- **WHEN** 用户点击"执行 SQL"按钮
- **THEN** 系统 SHALL 显示 toast 错误提示
- **AND** SHALL NOT 尝试打开查询编辑器

### Requirement: SELECT 语句 SHALL 自动执行

通过"执行 SQL"按钮打开的查询编辑器，SQL 自动运行 SHALL 为 `true`。

#### Scenario: SELECT 自动运行

- **GIVEN** AI 回复包含 ` ```sql SELECT id, name FROM products LIMIT 10``` ` 代码块
- **WHEN** 用户点击"执行 SQL"按钮
- **THEN** 查询编辑器打开后 SHALL 自动执行 SQL
- **AND** 结果面板 SHALL 显示查询结果

### Requirement: "解释 SQL"按钮行为不变

"解释 SQL"按钮 SHALL 继续通过 `SQL_EXPLAIN_EVENT` 将 SQL 前缀 "请解释" 后追加到 composer，不自动发送。

#### Scenario: "解释 SQL"仍追加到 composer

- **GIVEN** AI 回复包含 SQL 代码块
- **WHEN** 用户点击"解释 SQL"按钮
- **THEN** 系统 SHALL 将解释前缀 + SQL 追加到 composer 输入框
- **AND** SHALL NOT 自动发送消息
- **AND** SHALL NOT 打开查询编辑器

### Requirement: `SQL_EXECUTE_EVENT` 及 composer 监听 SHALL 移除

`sql-code-block.ts` 中的 `SQL_EXECUTE_EVENT` 常量 SHALL 移除。`prompt-composer.tsx` 中对 `SQL_EXECUTE_EVENT` 的 `addEventListener` 和 `removeEventListener` SHALL 移除。`SQL_EXPLAIN_EVENT` 及其监听 SHALL 保留。

#### Scenario: SQL_EXECUTE_EVENT 不再存在

- **GIVEN** 变更完成
- **WHEN** 检查 `sql-code-block.ts` 的 exports
- **THEN** `SQL_EXECUTE_EVENT` SHALL NOT 存在
- **AND** `SQL_EXPLAIN_EVENT` SHALL 仍存在

#### Scenario: composer 不再监听执行事件

- **GIVEN** 变更完成
- **WHEN** 检查 `prompt-composer.tsx` 的 `useEffect`
- **THEN** SHALL NOT 包含 `SQL_EXECUTE_EVENT` 的 `addEventListener` 调用
- **AND** SHALL 仍包含 `SQL_EXPLAIN_EVENT` 的 `addEventListener` 调用

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
