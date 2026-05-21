## ADDED Requirements

### Requirement: DML 执行前 SHALL 自动捕获 before-state 快照

对每条 DML ExecutionUnit（INSERT/UPDATE/DELETE），系统 SHALL 在执行前尝试捕获受影响行的 before-state 快照。快照捕获 SHALL 通过解析 SQL WHERE 子句并执行 `SELECT * FROM table WHERE <condition>` 实现。

#### Scenario: UPDATE 语句捕获 before-state

- **GIVEN** 用户执行 `UPDATE users SET name='Bob' WHERE id=1`
- **AND** `users` 表有主键 `id`
- **WHEN** SqlExecuteService 处理该 ExecutionUnit
- **THEN** 系统 SHALL 在执行 UPDATE 前执行 `SELECT * FROM users WHERE id=1` 获取旧行数据
- **AND** 快照 SHALL 包含 `{id: 1, name: 'Alice', ...}` 的完整列值

#### Scenario: DELETE 语句捕获 before-state

- **GIVEN** 用户执行 `DELETE FROM orders WHERE status='cancelled'`
- **AND** `orders` 表有主键 `id`
- **WHEN** SqlExecuteService 处理该 ExecutionUnit
- **THEN** 系统 SHALL 在执行 DELETE 前执行 `SELECT * FROM orders WHERE status='cancelled'` 获取将被删除的行

#### Scenario: INSERT 语句无需 before-state

- **GIVEN** 用户执行 `INSERT INTO users (name) VALUES ('Alice')`
- **WHEN** SqlExecuteService 处理该 ExecutionUnit
- **THEN** 系统 SHALL NOT 执行 before-state SELECT（INSERT 回滚仅需知道新插入的主键）

### Requirement: 无主键的表 SHALL 标记为不可回滚

系统 SHALL 通过 `DatabaseMetaData.getPrimaryKeys()` 检测目标表是否有主键。无主键的表的 DML 操作 SHALL 仍记录到 undo_log，但 `undoable` 字段 SHALL 为 `false`。

#### Scenario: 无主键表的 INSERT

- **GIVEN** `logs` 表没有主键
- **WHEN** 用户执行 `INSERT INTO logs (message) VALUES ('test')`
- **THEN** 系统 SHALL 创建 undo_log 记录，`undoable = false`
- **AND** 前端 SHALL NOT 显示 Undo 按钮

### Requirement: 受影响行超过 100 SHALL 标记为不可回滚

before-state SELECT 返回的行数若超过 100 行，系统 SHALL 停止快照捕获并标记 `undoable = false`。检测方式：先执行 `SELECT COUNT(*) FROM table WHERE <condition>`，超过阈值则跳过完整快照。

#### Scenario: UPDATE 影响超过 100 行

- **GIVEN** 用户执行 `UPDATE products SET price=price*1.1 WHERE category='books'`
- **AND** 该条件匹配 500 行
- **WHEN** before-state 捕获流程检测行数
- **THEN** 系统 SHALL 标记 `undoable = false`，不执行完整 SELECT
- **AND** undo_log 记录仍存在，`affected_rows = 500`

#### Scenario: DELETE 影响恰好 100 行

- **GIVEN** DELETE 匹配 100 行
- **WHEN** before-state 捕获流程检测行数
- **THEN** 系统 SHALL 执行完整 SELECT 并存储 100 行的快照
- **AND** `undoable = true`

### Requirement: 系统 SHALL 自动生成 inverse SQL

对每个可回滚的 DML 操作，系统 SHALL 生成对应的 inverse SQL：
- INSERT → `DELETE FROM table WHERE pk IN (...)` — 主键值从 `getGeneratedKeys()` 或 VALUES 解析
- UPDATE → `UPDATE table SET col1=old1, col2=old2, ... WHERE pk=old_pk` — 使用 before-state 中的旧值
- DELETE → `INSERT INTO table (col1, col2, ...) VALUES (row1_vals), (row2_vals), ...` — 使用 before-state 中的完整行数据

#### Scenario: INSERT 的 inverse SQL

- **GIVEN** 执行 `INSERT INTO users (name) VALUES ('Alice')`，自增主键生成 id=42
- **WHEN** 系统生成 inverse SQL
- **THEN** inverse SQL SHALL 为 `DELETE FROM users WHERE id = 42`

#### Scenario: UPDATE 的 inverse SQL

- **GIVEN** 执行 `UPDATE users SET name='Bob' WHERE id=1`，before-state 为 `{id:1, name:'Alice'}`
- **WHEN** 系统生成 inverse SQL
- **THEN** inverse SQL SHALL 为 `UPDATE users SET name='Alice' WHERE id=1`

#### Scenario: DELETE 的 inverse SQL

- **GIVEN** 执行 `DELETE FROM users WHERE id=1`，before-state 为 `{id:1, name:'Alice'}`
- **WHEN** 系统生成 inverse SQL
- **THEN** inverse SQL SHALL 为 `INSERT INTO users (id, name) VALUES (1, 'Alice')`

### Requirement: undo_log 记录 SHALL 持久化到 SQLite 元数据库

每条 DML 操作 SHALL 在 `undo_log` 表中创建一条记录，包含：`id`(UUID)、`session_id`、`connection_id`、`database_name`、`schema_name`、`table_name`、`operation`、`original_sql`、`inverse_sql`、`before_state`(JSON)、`affected_rows`、`undoable`、`status`、`expires_at`、`created_at`。

#### Scenario: 可回滚 DML 的 undo_log 记录

- **GIVEN** 可回滚的 UPDATE 执行成功
- **WHEN** 事务 commit 后
- **THEN** undo_log 表 SHALL 包含一条记录，`status='active'`、`undoable=true`、`inverse_sql` 非空

#### Scenario: 不可回滚 DML 的 undo_log 记录

- **GIVEN** 无主键表的 INSERT 执行成功
- **WHEN** 事务 commit 后
- **THEN** undo_log 表 SHALL 包含一条记录，`status='active'`、`undoable=false`、`inverse_sql` 为空

### Requirement: Undo 操作 SHALL 通过 REST 端点执行

系统 SHALL 提供 `POST /api/sql/undo` 端点，接受 `{ undoLogId, confirmed, riskAck }`。该端点 SHALL：
1. 查询 undo_log 记录，校验 status='active'、undoable=true、未过期
2. 若未 confirmed，返回 inverse SQL 供前端展示确认
3. 若 confirmed，在目标数据库上执行 inverse SQL
4. 更新 undo_log status 为 'undone'

#### Scenario: 首次调用获取确认信息

- **GIVEN** undo_log 记录存在且 undoable=true、未过期
- **WHEN** `POST /api/sql/undo { undoLogId: "abc", confirmed: false }`
- **THEN** 返回 `{ status: "requires_confirmation", inverseSql: "...", affectedRows: 3, tableName: "users" }`

#### Scenario: 确认后执行回滚

- **GIVEN** 获取确认信息成功
- **WHEN** `POST /api/sql/undo { undoLogId: "abc", confirmed: true, riskAck: "L2" }`
- **THEN** 系统 SHALL 在目标数据库执行 inverse SQL
- **AND** 更新 undo_log status 为 'undone'
- **AND** 返回 `{ status: "undone", affectedRows: 3 }`

#### Scenario: 已过期记录

- **GIVEN** undo_log 记录的 expires_at 已过
- **WHEN** `POST /api/sql/undo { undoLogId: "abc" }`
- **THEN** 返回 HTTP 404，错误信息包含 "expired"

#### Scenario: 已回滚记录

- **GIVEN** undo_log 记录 status='undone'
- **WHEN** `POST /api/sql/undo { undoLogId: "abc" }`
- **THEN** 返回 HTTP 409，错误信息包含 "already undone"

### Requirement: undo_log 记录 SHALL 在 3 天后过期

每条 undo_log 记录的 `expires_at` SHALL 为 `created_at + 3天`。过期后状态 SHALL 由定时任务清理为 'expired'。

#### Scenario: 新记录的过期时间

- **GIVEN** 在 2026-05-14 10:00 创建 undo_log 记录
- **WHEN** 检查 expires_at
- **THEN** expires_at SHALL 为 2026-05-17 10:00

### Requirement: 系统 SHALL 每日清理过期的 undo_log 记录

一个 Spring `@Scheduled` 任务 SHALL 每天运行，将 `status='active' AND expires_at < now` 的记录更新为 `status='expired'`，并删除 `status='expired'` 超过 7 天的记录。

#### Scenario: 过期记录被标记

- **GIVEN** undo_log 有 5 条 active 记录，其中 2 条 expires_at 已过
- **WHEN** 定时清理任务运行
- **THEN** 2 条过期记录 status 更新为 'expired'
- **AND** 3 条未过期记录不变

### Requirement: DML 执行结果 SHALL 包含 undo 信息

`SqlExecuteResultItem` (kind='dml_summary') SHALL 扩展 `undoLogId` 和 `undoable` 两个可选字段。仅当 DML 成功执行且 undo_log 记录创建后，这两个字段才有值。

#### Scenario: 可回滚 DML 的结果

- **GIVEN** UPDATE 成功执行且 undoable=true
- **WHEN** 返回结果
- **THEN** `SqlExecuteResultItem` SHALL 包含 `undoLogId: "uuid-xxx"`, `undoable: true`

#### Scenario: 不可回滚 DML 的结果

- **GIVEN** INSERT 在无主键表上成功执行
- **WHEN** 返回结果
- **THEN** `SqlExecuteResultItem` SHALL 包含 `undoable: false`
- **AND** `undoLogId` SHALL 为 null 或不存在

### Requirement: DML Summary Panel SHALL 显示 Undo 按钮

对 `undoable === true` 的 DML 结果，前端 SHALL 在 DML Summary Panel 中显示一个 "Undo" 按钮。点击后 SHALL 显示确认弹窗，展示 inverse SQL 预览，用户确认后调用 `/api/sql/undo`。

#### Scenario: 可回滚 DML 显示 Undo 按钮

- **GIVEN** DML 结果 `undoable=true`
- **WHEN** DML Summary Panel 渲染
- **THEN** SHALL 显示 "Undo" 按钮

#### Scenario: 不可回滚 DML 不显示 Undo 按钮

- **GIVEN** DML 结果 `undoable=false` 或字段不存在
- **WHEN** DML Summary Panel 渲染
- **THEN** SHALL NOT 显示 "Undo" 按钮

#### Scenario: 点击 Undo 显示确认弹窗

- **GIVEN** 用户点击 Undo 按钮
- **WHEN** 前端调用 `POST /api/sql/undo { confirmed: false }`
- **THEN** SHALL 展示 AlertDialog，包含 inverse SQL 预览、受影响行数、表名
- **AND** 确认按钮使用 `warning` variant

#### Scenario: 确认 Undo 执行回滚

- **GIVEN** 用户在确认弹窗中点击 "Confirm Undo"
- **WHEN** 前端调用 `POST /api/sql/undo { confirmed: true, riskAck: "L2" }`
- **THEN** DML Summary Panel SHALL 更新为 "Undo completed" 状态
- **AND** Undo 按钮 SHALL 消失
