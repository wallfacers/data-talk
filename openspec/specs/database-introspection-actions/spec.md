# database-introspection-actions Specification

## Purpose

定义 DataTalk 为 OpenCode AI 提供的数据库内省类 Action 契约:`datatalk.schema_search` 用自然语言关键词在 connection 元数据中搜索候选表,`datatalk.query_history` 返回当前 session 最近 SQL 执行记录,并由 `sql_execution_history` 表持久化每条 SQL 执行的元数据。该 spec 保证 AI 在写 SQL 前能够通过这两个 Action 高效定位表与复用历史模式,避免反复盲试 `read_schema`。

## Requirements

### Requirement: schema_search Action 行为契约

DataTalk SHALL 注册一个 `@DataTalkAction(id = "datatalk.schema_search")` ActionHandler,通过 MCP 协议以工具名 `datatalk_schema_search` 暴露给 OpenCode,允许 AI 用自然语言关键词(中文/英文/拼音/缩写)在 connection 元数据中搜索候选表。该 action MUST 满足:

- `executor = SERVER`,`requiresConnection = true`,`timeoutMs = 5000`,`riskLevel = L1`,`category = METADATA`,`exposeToMcp = true`
- 输入 schema 字段:`keyword` (string, required, ≤ 200 chars), `connectionId` (optional, 默认从 session data context), `database` (optional), `schema` (optional), `limit` (integer, optional, default 10, max 30)
- 输出 schema 字段:`candidates: List<{ table, schema, database, score, matchedOn: List<string>, commentSnippet }>`、`totalCandidates`、`truncated`、`hint`
- 匹配评分:`score = 3 * (表名 ILIKE %keyword%) + 2 * (列名 ILIKE %keyword%) + 1 * (注释 ILIKE %keyword%)`,按 score 降序返回 top `limit`
- 实现位于 `data-talk-adapter` 层的 `SchemaSearchAction`,委派至 `data-talk-infrastructure` 层 `SchemaSearchRepositoryJdbc`,后者按 `connection.kind` switch 到对应方言模板
- 不支持 column comment 的 kind(如 `sqlite` / `duckdb` / `hive`)MUST 降级为"仅表名/列名匹配",`commentSnippet` 返回空字符串,**不**抛错
- `keyword` 在执行查询前 MUST 经过 `lower()` + `trim()` 归一化;`%` 与 `_` 通配字符 MUST 转义为 `\%` / `\_` 避免 SQL injection 风险
- i18n description(`messages.properties` key `action.schema_search.description`)MUST 明确说明:"在不知道表名时优先调用此 action,而非反复 `read_schema` 猜表名" 与"支持中文/英文/拼音"

#### Scenario: action 被注册并暴露到 MCP

- **GIVEN** Spring 应用启动完成
- **WHEN** 查询 `ActionRegistry.require("datatalk.schema_search")`
- **THEN** 返回的 `ActionDescriptor` 满足:`executor = SERVER`,`requiresConnection = true`,`timeoutMs = 5000`,`riskLevel` 含 `L1`,`category` 含 `METADATA`,`exposeToMcp = true`
- **AND** `DataTalkMcpService.listTools()` 返回的工具列表中包含名称 `datatalk_schema_search`,且 `inputSchema` 含字段 `keyword` `connectionId` `database` `schema` `limit`

#### Scenario: 中文关键词命中表名

- **GIVEN** MySQL connection 含表 `t_sales_order` (TABLE_COMMENT="销售订单主表") 与表 `t_user` (TABLE_COMMENT="用户表")
- **WHEN** 调用 `datatalk_schema_search(keyword="销售", connectionId="<conn>")`
- **THEN** `candidates[0].table = "t_sales_order"`
- **AND** `candidates[0].matchedOn` 含 `"table.comment"`
- **AND** `candidates[0].commentSnippet` 含子串 `"销售订单"`
- **AND** `candidates[0].score >= 1`

#### Scenario: 英文关键词同时命中表名与列名

- **GIVEN** PostgreSQL connection 含表 `users` (列 `user_email`, comment "用户邮箱")
- **WHEN** 调用 `datatalk_schema_search(keyword="user", limit=10)`
- **THEN** `candidates` 包含表 `users` 一项
- **AND** 该项的 `matchedOn` 同时含 `"table.name"` 与 `"column.user_email.name"`
- **AND** 该项的 `score >= 5`(表名 3 分 + 列名 2 分)

#### Scenario: SQLite 降级—无 comment 时仅匹配表名/列名

- **GIVEN** SQLite connection 含表 `customers` (无 column comment)
- **WHEN** 调用 `datatalk_schema_search(keyword="cust")`
- **THEN** `candidates` 含表 `customers`
- **AND** 该项的 `commentSnippet = ""`(空字符串,不抛错)
- **AND** action 整体 `status = "success"`(不返回 error)

#### Scenario: 空 keyword 返回参数错误

- **WHEN** 调用 `datatalk_schema_search(keyword="")`
- **THEN** action 返回错误响应,`errorCode = "INVALID_ARGUMENT"`,`message` 含子串 "keyword"

#### Scenario: 超长 keyword 拒绝

- **WHEN** 调用 `datatalk_schema_search(keyword=<201 字符的字符串>)`
- **THEN** action 返回错误响应,`errorCode = "INVALID_ARGUMENT"`,`message` 含子串 "200"

#### Scenario: SQL 注入特殊字符被转义

- **GIVEN** MySQL connection 含表 `t_a_b` 与 `t_xyz`
- **WHEN** 调用 `datatalk_schema_search(keyword="a%b")` (`%` 是 SQL 通配符)
- **THEN** action 不应匹配 `t_xyz`(即 `%` 被转义为字面字符)
- **AND** 若存在表 `t_a%b`(实际表名含 `%`),应命中

#### Scenario: limit 上限保护

- **WHEN** 调用 `datatalk_schema_search(keyword="a", limit=999)`
- **THEN** action 返回结果 `candidates.size() <= 30`
- **AND** 响应中 `truncated = true`(若实际匹配 > 30)

#### Scenario: 不支持的 kind 报明确错误

- **GIVEN** 一个 kind 不在支持列表中的 connection(假设未来出现新 kind 尚未实现适配)
- **WHEN** 调用 `datatalk_schema_search`
- **THEN** action 返回错误响应,`errorCode = "UNSUPPORTED_DIALECT"`,`message` 含 kind 名称

### Requirement: query_history Action 行为契约

DataTalk SHALL 注册一个 `@DataTalkAction(id = "datatalk.query_history")` ActionHandler,通过 MCP 协议以工具名 `datatalk_query_history` 暴露给 OpenCode,返回本 session 最近 SQL 执行记录。该 action MUST 满足:

- `executor = SERVER`,`requiresConnection = false`(查询历史不依赖外部 connection,只读 DataTalk metadata),`timeoutMs = 3000`,`riskLevel = L1`,`category = METADATA`,`exposeToMcp = true`
- 输入 schema 字段:`limit` (integer, optional, default 10, max 50), `status` (string, optional, enum: `"success" | "failure" | "all"`, default `"success"`), `connectionId` (optional), `database` (optional)
- 输出 schema 字段:`queries: List<{ sqlText, status, executedAt, durationMs, rowCount?, errorCode?, errorMessage? }>`、`totalQueries`
- `queries` 按 `executedAt` 降序排列(最新在前)
- 数据源:`sql_execution_history` 表(Flyway V3,DataTalk metadata SQLite)
- session 范围由当前请求的 `sessionId` 隐式提供(MCP bridge 注入 `__dtOpenCodeSessionId` 后由 ActionDispatcher 解析)
- i18n description MUST 明确说明:"在写新 SQL 前 SHOULD 调用此 action 看用户最近 query 模式,以保持风格一致、避免重复探索"

#### Scenario: action 被注册并暴露到 MCP

- **GIVEN** Spring 应用启动完成
- **WHEN** 查询 `ActionRegistry.require("datatalk.query_history")`
- **THEN** 返回的 `ActionDescriptor` 满足:`executor = SERVER`,`requiresConnection = false`,`timeoutMs = 3000`,`exposeToMcp = true`
- **AND** `DataTalkMcpService.listTools()` 返回的工具列表中包含名称 `datatalk_query_history`

#### Scenario: 返回本 session 最近成功 SQL

- **GIVEN** session `S-1` 在 `sql_execution_history` 表中有 5 条 status=success 记录,executedAt 由旧到新
- **WHEN** 在 session `S-1` 上下文中调用 `datatalk_query_history(limit=3)`
- **THEN** `queries.size() = 3`
- **AND** `queries[0].executedAt > queries[1].executedAt > queries[2].executedAt`(降序)
- **AND** 所有 `queries[*].status = "success"`(默认过滤)

#### Scenario: status="failure" 过滤

- **GIVEN** session `S-1` 含 2 条 success + 3 条 failure 记录
- **WHEN** 调用 `datatalk_query_history(status="failure")`
- **THEN** `queries.size() = 3`
- **AND** 所有项 `status = "failure"`
- **AND** 所有项 `errorCode` 字段非空

#### Scenario: status="all" 包含成功与失败

- **GIVEN** session `S-1` 含 2 条 success + 3 条 failure 记录
- **WHEN** 调用 `datatalk_query_history(status="all", limit=10)`
- **THEN** `queries.size() = 5`

#### Scenario: connectionId 过滤

- **GIVEN** session `S-1` 含 connection `c1` 3 条记录 + connection `c2` 2 条记录
- **WHEN** 调用 `datatalk_query_history(connectionId="c1")`
- **THEN** `queries.size() = 3`
- **AND** 所有项的隐式 connection 都是 `c1`

#### Scenario: 跨 session 隔离

- **GIVEN** session `S-1` 含 10 条记录,session `S-2` 含 5 条记录
- **WHEN** 在 session `S-2` 上下文中调用 `datatalk_query_history()`
- **THEN** `queries.size() <= 5`(只看到 S-2 自己的记录)
- **AND** 不包含任何 S-1 的 sqlText

#### Scenario: 空历史返回空数组

- **GIVEN** session `S-3` 在 `sql_execution_history` 表中无记录
- **WHEN** 调用 `datatalk_query_history()`
- **THEN** `queries = []`
- **AND** `totalQueries = 0`
- **AND** action 整体 `status = "success"`

#### Scenario: limit 上限保护

- **WHEN** 调用 `datatalk_query_history(limit=999)`
- **THEN** action 实际查询时使用 limit=50(上限),不返回错误

### Requirement: SQL 执行历史持久化

DataTalk SHALL 通过 Flyway 迁移 `V3__sql_execution_history.sql` 创建 `sql_execution_history` 表存储每条 SQL 执行的元数据。`datatalk.execute_sql` Action 的执行流程 MUST 在 SQL **真正经 JDBC 提交执行后**(无论结果成功或失败),顺路调用 `SqlExecutionHistoryService.record(...)` 写入一条记录;此写入路径 MUST NOT 阻塞 SQL 执行结果返回到 caller,**前端代码无任何改动**。

仅在 SQL 真正执行时写入,意味着:

- 若并行 change `simplify-sql-execution-gate` 引入"DELETE 需对话式确认"流程,**第一次调用**返回 `requires_confirmation` 时(SQL 未执行)**MUST NOT** 写历史
- **第二次调用**(`confirmationId` 非空,真正执行)正常写,与普通 SQL 一致
- 用户未在 TTL 内确认导致挂起记录过期、或显式取消,**MUST NOT** 写历史(无执行 = 无历史)

表 schema 约束:
- `id INTEGER PRIMARY KEY AUTOINCREMENT`
- `session_id TEXT NOT NULL` — 隔离不同 session
- `connection_id TEXT NOT NULL`
- `database_name TEXT` (nullable)
- `schema_name TEXT` (nullable)
- `sql_text TEXT NOT NULL` — 截断到 4 KB
- `status TEXT NOT NULL CHECK (status IN ('success', 'failure'))`
- `error_code TEXT` (仅 failure 行有,nullable)
- `error_message TEXT` (仅 failure 行有,截断到 1 KB,nullable)
- `executed_at INTEGER NOT NULL` — epoch ms
- `duration_ms INTEGER` (nullable)
- `row_count INTEGER` (仅 success 行有,nullable)
- 索引 `idx_sql_history_session_executed` ON `(session_id, executed_at DESC)`
- 索引 `idx_sql_history_session_status` ON `(session_id, status, executed_at DESC)`

每个 session 最多保留 100 条记录;超出时 MUST 异步触发裁剪(`DELETE FROM ... WHERE session_id = ? AND id NOT IN (SELECT id ... LIMIT 100)`),**不**阻塞写入路径。

#### Scenario: Flyway 迁移 V3 应用成功

- **GIVEN** DataTalk metadata SQLite 在 V2 状态(`user_message_attachments` 表存在,无 `sql_execution_history` 表)
- **WHEN** Spring 应用启动,Flyway 执行 `migrate()`
- **THEN** `sql_execution_history` 表存在且 schema 符合上述约束
- **AND** 两个索引存在
- **AND** Flyway `flyway_schema_history` 表含 V3 记录,`success = true`

#### Scenario: execute_sql 成功后写入 history

- **GIVEN** session `S-1` 绑定 connection `c1` (MySQL),用户通过 chat 触发 `datatalk_execute_sql(sql="SELECT count(*) FROM users", connectionId="c1")`
- **AND** 执行成功返回 row_count = 1, durationMs = 45
- **WHEN** action 完成
- **THEN** `sql_execution_history` 表中存在新记录:
  - `session_id = "S-1"`
  - `connection_id = "c1"`
  - `sql_text = "SELECT count(*) FROM users"`(或截断后版本,完整 ≤ 4 KB 时原样)
  - `status = "success"`
  - `row_count = 1`
  - `duration_ms = 45`
  - `error_code IS NULL`
  - `error_message IS NULL`
  - `executed_at` 接近当前时间戳(误差 < 2 秒)

#### Scenario: execute_sql 失败后写入 history

- **GIVEN** session `S-1`,用户触发 `datatalk_execute_sql(sql="SELECT * FROM no_such_table", connectionId="c1")`
- **AND** 执行失败,errorCode = "TABLE_NOT_FOUND",errorMessage = "no such table: no_such_table"
- **WHEN** action 完成
- **THEN** `sql_execution_history` 表中存在新记录:
  - `status = "failure"`
  - `error_code = "TABLE_NOT_FOUND"`
  - `error_message` 含子串 "no_such_table"
  - `row_count IS NULL`
  - `duration_ms` 非空(已开始执行时间已记录)

#### Scenario: 超过 100 条触发裁剪

- **GIVEN** session `S-1` 在 `sql_execution_history` 已有 100 条记录
- **WHEN** `execute_sql` 完成并新写入第 101 条
- **THEN** 该 session 在表中的记录总数最终归为 100
- **AND** 被裁剪的是 executed_at 最早的 1 条
- **AND** 裁剪是异步触发,不阻塞 execute_sql 返回(此点通过日志或异步任务计数器验证,scenario 不强制断言时间)

#### Scenario: sql_text 截断到 4 KB

- **GIVEN** session `S-1`,执行一条 sql_text 长度 5 KB 的 SQL
- **WHEN** action 完成
- **THEN** `sql_execution_history` 中该记录的 `sql_text` 长度 ≤ 4096 字节
- **AND** sql_text 末尾含 "..." 截断标识

#### Scenario: 写入失败不影响 SQL 结果返回

- **GIVEN** SQLite metadata DB 因磁盘满或锁定写入失败
- **WHEN** `execute_sql` 完成后 `SqlExecutionHistoryService.record()` 抛 IOException
- **THEN** `execute_sql` action 仍然成功返回 SQL 结果给 caller
- **AND** 写入失败仅记录 WARN 日志,不向 caller 暴露

#### Scenario: DELETE 二次确认流程仅在真正执行后写入历史

- **GIVEN** `simplify-sql-execution-gate` 已实施,session `S-1` 用户通过 chat 触发 `datatalk_execute_sql(sql="DELETE FROM users WHERE id=1", connectionId="c1")`
- **WHEN** 第一次调用返回 `requires_confirmation` + `confirmationId="X-1"`(SQL 未真正执行)
- **THEN** `sql_execution_history` 表中 session `S-1` 的记录数 **不变**
- **AND** 当用户在 5 分钟内确认,再次调用 `datatalk_execute_sql(confirmationId="X-1")` 真正删除成功
- **THEN** `sql_execution_history` 表中新增 1 条记录,`status = "success"`,`sql_text` 含 "DELETE"
- **AND** 若用户未确认,5 分钟后挂起记录过期,`sql_execution_history` 表中该 SQL 始终不出现
