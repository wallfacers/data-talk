## ADDED Requirements

### Requirement: 按 connection 分页查询 undo_log 列表

系统 SHALL 提供 `GET /api/connections/{connectionId}/op-logs` 端点，返回该 connection 下的 undo_log 分页列表。列表 SHALL 按 `created_at DESC` 排序（最新操作在前）。

#### Scenario: 默认分页查询

- **GIVEN** connection `conn-1` 下有 75 条 undo_log 记录
- **WHEN** `GET /api/connections/conn-1/op-logs?page=0&size=50`
- **THEN** 返回 HTTP 200，body 包含 `{ items: [...], total: 75, page: 0, size: 50 }`
- **AND** `items` 数组包含 50 条记录，按 created_at 降序

#### Scenario: 第二页查询

- **GIVEN** connection `conn-1` 下有 75 条 undo_log 记录
- **WHEN** `GET /api/connections/conn-1/op-logs?page=1&size=50`
- **THEN** 返回 HTTP 200，`items` 包含 25 条记录

#### Scenario: 空结果

- **GIVEN** connection `conn-2` 下无 undo_log 记录
- **WHEN** `GET /api/connections/conn-2/op-logs?page=0&size=50`
- **THEN** 返回 HTTP 200，`{ items: [], total: 0, page: 0, size: 50 }`

#### Scenario: connection 不存在

- **GIVEN** connection `nonexistent` 在数据库中不存在
- **WHEN** `GET /api/connections/nonexistent/op-logs`
- **THEN** 返回 HTTP 200，`{ items: [], total: 0 }`

### Requirement: 按 status 过滤

查询端点 SHALL 支持 `status` 查询参数，值为逗号分隔的 status 列表（pending, active, undone, expired）。

#### Scenario: 过滤 active 状态

- **GIVEN** connection `conn-1` 下有 10 条 active、3 条 undone、2 条 expired 记录
- **WHEN** `GET /api/connections/conn-1/op-logs?status=active`
- **THEN** 返回 `total: 10`，所有 items 的 status 为 `active`

#### Scenario: 多状态过滤

- **GIVEN** connection `conn-1` 下有 10 条 active、3 条 undone 记录
- **WHEN** `GET /api/connections/conn-1/op-logs?status=active,undone`
- **THEN** 返回 `total: 13`

### Requirement: 按 operation 过滤

查询端点 SHALL 支持 `operation` 查询参数，值为逗号分隔的操作类型（INSERT, UPDATE, DELETE）。

#### Scenario: 过滤 INSERT 操作

- **GIVEN** connection `conn-1` 下有 5 条 INSERT、8 条 UPDATE、3 条 DELETE 记录
- **WHEN** `GET /api/connections/conn-1/op-logs?operation=INSERT`
- **THEN** 返回 `total: 5`，所有 items 的 operation 为 `INSERT`

### Requirement: 按 table 名过滤

查询端点 SHALL 支持 `table` 查询参数，对 `table_name` 做 case-insensitive 包含匹配。

#### Scenario: 按表名过滤

- **GIVEN** connection `conn-1` 下有操作涉及 `orders`、`order_items`、`users` 表
- **WHEN** `GET /api/connections/conn-1/op-logs?table=order`
- **THEN** 返回仅包含 `orders` 和 `order_items` 的记录

### Requirement: 按时间范围过滤

查询端点 SHALL 支持 `from` 和 `to` 查询参数（毫秒时间戳），对 `created_at` 做范围过滤。

#### Scenario: 指定时间范围

- **GIVEN** connection `conn-1` 下有操作记录，created_at 分别为 T1、T2、T3
- **WHEN** `GET /api/connections/conn-1/op-logs?from=T2&to=T3`
- **THEN** 返回仅包含 created_at 在 [T2, T3] 范围内的记录

### Requirement: SQL 全文搜索

查询端点 SHALL 支持 `q` 查询参数，对 `original_sql` 做 LIKE 模糊匹配。

#### Scenario: 搜索 SQL 内容

- **GIVEN** connection `conn-1` 下有 `original_sql` 包含 `INSERT INTO orders` 的记录
- **WHEN** `GET /api/connections/conn-1/op-logs?q=INSERT INTO orders`
- **THEN** 返回匹配的记录

### Requirement: 列表响应 SHALL JOIN session title

每条 undo_log 记录 SHALL 包含对应 session 的 `title`（命名为 `sessionTitle`）。若 session 已删除，`sessionTitle` SHALL 为 null。

#### Scenario: session 存在时返回 title

- **GIVEN** undo_log 记录关联 session `sess-1`，该 session title 为 "分析订单数据"
- **WHEN** 查询返回该记录
- **THEN** item SHALL 包含 `sessionTitle: "分析订单数据"`

#### Scenario: session 已删除时返回 null

- **GIVEN** undo_log 记录关联的 session 已被删除（session_id 的 FK ON DELETE CASCADE 未触发因 undo_log 中 session_id 仅为 TEXT）
- **WHEN** 查询返回该记录
- **THEN** item SHALL 包含 `sessionTitle: null`

### Requirement: 列表响应 SHALL NOT 包含 before_state

列表端点的 items SHALL NOT 包含 `before_state` 字段，以减少传输体积。before_state SHALL 通过单独的详情端点或展开查询获取。

#### Scenario: 列表不返回 before_state

- **GIVEN** 任意分页查询
- **WHEN** 返回 items
- **THEN** 每个 item SHALL NOT 包含 `before_state` 字段
- **AND** 每个 item SHALL 包含 `id`、`sessionId`、`sessionTitle`、`databaseName`、`schemaName`、`tableName`、`operation`、`originalSql`、`inverseSql`、`affectedRows`、`undoable`、`status`、`expiresAt`、`createdAt`、`undoneAt`

### Requirement: 按 ID 获取单条 undo_log 详情

系统 SHALL 提供 `GET /api/connections/{connectionId}/op-logs/{undoLogId}` 端点，返回单条记录的完整数据（含 `before_state`）。

#### Scenario: 获取详情

- **GIVEN** undo_log 记录 `log-1` 存在，connection_id 匹配
- **WHEN** `GET /api/connections/conn-1/op-logs/log-1`
- **THEN** 返回 HTTP 200，body 包含完整记录含 `before_state`

#### Scenario: 记录不存在

- **GIVEN** undo_log 记录 `nonexistent` 不存在
- **WHEN** `GET /api/connections/conn-1/op-logs/nonexistent`
- **THEN** 返回 HTTP 404
