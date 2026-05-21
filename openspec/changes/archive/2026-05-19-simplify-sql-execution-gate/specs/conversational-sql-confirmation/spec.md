## ADDED Requirements

### Requirement: DELETE 语句 SHALL 触发对话式确认

当 AI 通过 `datatalk_execute_sql` 调用包含 DELETE 语句的 SQL 时，系统 SHALL 不立即执行，而是返回 `requires_confirmation` 状态，包含影响摘要和 `confirmationId`。AI 负责向用户展示确认提示。用户在聊天中确认后，AI 通过二次调用携带 `confirmationId` + `confirmed=true` 完成执行。

#### Scenario: DELETE WHERE 需对话确认

- **GIVEN** AI 调用 `datatalk_execute_sql(sql="DELETE FROM orders WHERE status='expired'")`
- **WHEN** `SqlExecuteService` 检测到 DELETE 语句
- **THEN** 返回 `{ status: "requires_confirmation", confirmationId: "<uuid>", message: "即将执行 DELETE 操作", sqlPreview: "DELETE FROM orders WHERE status='expired'", affectedObjects: ["orders"] }`
- **AND** 不执行任何数据库操作
- **AND** SQL 上下文（sql, connectionId, sessionId, database, schema）存入 `SqlPendingConfirmationStore`

#### Scenario: DELETE 无 WHERE 需对话确认

- **GIVEN** AI 调用 `datatalk_execute_sql(sql="DELETE FROM temp_log")`
- **WHEN** `SqlExecuteService` 检测到 DELETE 无 WHERE 子句
- **THEN** 返回 `{ status: "requires_confirmation", confirmationId: "<uuid>", message: "即将清空表中所有数据", sqlPreview: "DELETE FROM temp_log", affectedObjects: ["temp_log"] }`

#### Scenario: 非DELETE语句直接执行

- **GIVEN** AI 调用 `datatalk_execute_sql(sql="DROP TABLE IF EXISTS temp_log; CREATE TABLE temp_log (id INT)")`
- **WHEN** SQL 包含 DROP + CREATE 但无 DELETE
- **THEN** 系统 SHALL 直接执行并返回正常结果
- **AND** SHALL NOT 返回 `requires_confirmation`

#### Scenario: 用户确认后二次调用执行

- **GIVEN** 第一次调用返回了 `confirmationId = "abc-123"`
- **AND** 用户在聊天中确认
- **WHEN** AI 调用 `datatalk_execute_sql(confirmationId="abc-123", confirmed=true)`
- **THEN** 系统从 `SqlPendingConfirmationStore` 取出挂起的 SQL 和上下文
- **AND** 执行 DELETE 语句
- **AND** 返回正常执行结果 `{ status: "executed", rowCount: N, ... }`
- **AND** 从 Store 中移除该 confirmationId

#### Scenario: 用户拒绝确认

- **GIVEN** 第一次调用返回了 `confirmationId = "abc-123"`
- **WHEN** 用户在聊天中表示拒绝（如"不要执行"/"取消"）
- **THEN** AI 不发起二次调用，confirmationId 自然过期（5 分钟 TTL）

#### Scenario: confirmationId 过期

- **GIVEN** `confirmationId = "abc-123"` 已创建超过 5 分钟
- **WHEN** AI 调用 `datatalk_execute_sql(confirmationId="abc-123", confirmed=true)`
- **THEN** 返回 `{ status: "confirmation_expired", message: "确认已过期，请重新发起操作" }`
- **AND** SHALL NOT 执行任何 SQL

#### Scenario: confirmationId 不存在

- **GIVEN** `confirmationId = "nonexistent"` 从未创建
- **WHEN** AI 调用 `datatalk_execute_sql(confirmationId="nonexistent", confirmed=true)`
- **THEN** 返回错误 `{ status: "confirmation_invalid", message: "无效的确认 ID" }`

### Requirement: SqlPendingConfirmationStore SHALL 使用内存存储且自动过期

挂起的确认信息 SHALL 存储在服务端内存（ConcurrentHashMap），不持久化。每条记录 TTL 为 5 分钟。服务重启后所有挂起确认丢失。

#### Scenario: TTL 自动清理

- **GIVEN** 一条 confirmationId 创建于 T0
- **WHEN** T0 + 5 分钟后 AI 尝试使用该 confirmationId
- **THEN** 该记录已被清理，返回 `confirmation_expired`

#### Scenario: 服务重启后确认丢失

- **GIVEN** 服务有 3 条活跃的 pending confirmations
- **WHEN** 服务重启
- **THEN** 所有 3 条确认丢失
- **AND** AI 使用旧 confirmationId 调用将返回 `confirmation_expired`

### Requirement: 确认信息 SHALL 包含影响摘要

`requires_confirmation` 返回 SHALL 包含足够信息让 AI 向用户生成有意义的确认提示。

#### Scenario: 返回结构完整

- **WHEN** DELETE 语句触发 `requires_confirmation`
- **THEN** 返回结果 SHALL 包含以下字段：
  - `status`: "requires_confirmation"
  - `confirmationId`: UUID 字符串
  - `message`: 操作描述（如"即将执行 DELETE 操作"）
  - `sqlPreview`: 完整 SQL 文本
  - `affectedObjects`: 受影响的表名列表
