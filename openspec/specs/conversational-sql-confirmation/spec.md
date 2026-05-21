# conversational-sql-confirmation Specification

## Purpose

定义 AI 路径下 DELETE 语句的对话式确认机制和破坏性 DDL 的编辑器引导机制：AI 调用 `datatalk_execute_sql` 包含 DELETE 时，后端不立即执行，而是返回 `requires_confirmation` 状态；包含破坏性 DDL 时，返回 `redirect_to_editor` 状态，AI 打开 query_editor 让用户自行执行。

## Requirements

### Requirement: 破坏性 DDL 语句 SHALL 拦截并引导到编辑器

当 AI 通过 `datatalk_execute_sql` 调用包含破坏性 DDL 的 SQL 时，系统 SHALL 不执行，而是返回 `redirect_to_editor` 状态。AI SHALL 打开 query_editor 写入 SQL，让用户自行确认执行。

**拦截机制**：基于原始 SQL 文本的关键词扫描，按 `;` 切分为独立语句后，检测每条语句的首关键词或子句模式。不依赖 `CalciteSqlRiskAnalyzer` 的 riskLevel/reason（对 MySQL/PG/H2 等主流库的 DDL 为 null/"parse_failed"）。

破坏性 DDL 关键词/模式：
- 首关键词：`DROP`、`TRUNCATE`、`GRANT`、`REVOKE`、`DENY`、`KILL`、`SHUTDOWN`、`PURGE`
- 子句模式：`SET GLOBAL`（`(?i)^\s*SET\s+GLOBAL\b`）、`ALTER ... DROP`（`(?i)ALTER\s+\w+\s+.*\bDROP\b`）、`INSERT OVERWRITE`（`(?i)^\s*INSERT\s+OVERWRITE\b`）

AI 收到 `redirect_to_editor` 后 SHALL 执行以下单步动作：

```
datatalk_ui_exec(
  object  = "workspace",
  action  = "open",
  params  = {
    type:    "query_editor",
    title:   "<描述性标题>",
    payload: {
      initialSql: "<redirect 响应中的 sql>",
      autoRun:    false
    }
  }
)
```

然后告知用户 SQL 已写入编辑器，请在编辑器中确认后执行。`workspace open` 强制要求 `type` + `title`（UiExecAction schema），必须提供。`autoRun` 必须为 `false`——redirect 的全部意义是让用户手动确认。不需要单独 `workspace focus`——`open` 自带聚焦，且 focus 要求 `target`（tab id）在 open 返回前不可用。

#### Scenario: DROP TABLE 被拦截（MySQL/PG/H2 连接）

- **GIVEN** AI 使用 MySQL/PostgreSQL/H2 连接调用 `datatalk_execute_sql(sql="DROP TABLE td_orders")`
- **AND** `CalciteSqlRiskAnalyzer` 对此 SQL 返回 `riskLevel = null, reason = "parse_failed:..."`
- **WHEN** `ExecuteSqlAction` 检测到 SQL 首关键词为 `DROP`
- **THEN** 返回 `{ status: "redirect_to_editor", reason: "destructive_ddl", sql: "DROP TABLE td_orders", affectedObjects: ["td_orders"], message: "...", suggestion: "use_query_editor" }`
- **AND** 不执行任何数据库操作

#### Scenario: TRUNCATE TABLE 被拦截（MySQL/PG/H2 连接）

- **GIVEN** AI 使用 MySQL/PostgreSQL/H2 连接调用 `datatalk_execute_sql(sql="TRUNCATE TABLE td_orders")`
- **AND** `CalciteSqlRiskAnalyzer` 对此 SQL 返回 `riskLevel = null, reason = "parse_failed:..."`
- **WHEN** `ExecuteSqlAction` 检测到 SQL 首关键词为 `TRUNCATE`
- **THEN** 返回 `{ status: "redirect_to_editor", ... }`
- **AND** 不执行任何数据库操作

#### Scenario: ALTER TABLE DROP COLUMN 被拦截

- **GIVEN** AI 调用 `datatalk_execute_sql(sql="ALTER TABLE users DROP COLUMN phone")`
- **WHEN** `ExecuteSqlAction` 检测到 ALTER ... DROP 子句
- **THEN** 返回 `{ status: "redirect_to_editor", ... }`
- **AND** 不执行任何数据库操作

#### Scenario: GRANT 语句被拦截

- **GIVEN** AI 调用 `datatalk_execute_sql(sql="GRANT SELECT ON users TO readonly_role")`
- **WHEN** `ExecuteSqlAction` 检测到首关键词 `GRANT`
- **THEN** 返回 `{ status: "redirect_to_editor", ... }`
- **AND** 不执行任何数据库操作

#### Scenario: 建设性 DDL 直接执行

- **GIVEN** AI 调用 `datatalk_execute_sql(sql="CREATE TABLE td_temp (id INT PRIMARY KEY)")`
- **WHEN** SQL 首关键词为 `CREATE`，不在破坏性列表中
- **THEN** 系统 SHALL 直接执行并返回正常结果
- **AND** SHALL NOT 返回 `redirect_to_editor`

#### Scenario: ALTER TABLE ADD COLUMN 直接执行

- **GIVEN** AI 调用 `datatalk_execute_sql(sql="ALTER TABLE users ADD COLUMN phone VARCHAR(20)")`
- **WHEN** SQL 包含 ALTER 但无 DROP 子句
- **THEN** 系统 SHALL 直接执行并返回正常结果
- **AND** SHALL NOT 返回 `redirect_to_editor`

#### Scenario: 复合 ALTER 语句含 DROP 子句被拦截

- **GIVEN** AI 调用 `datatalk_execute_sql(sql="ALTER TABLE users ADD COLUMN email VARCHAR(100), DROP COLUMN phone")`
- **WHEN** SQL 包含 ALTER ... DROP 子句
- **THEN** 返回 `{ status: "redirect_to_editor", ... }`
- **AND** 不执行任何数据库操作

#### Scenario: 多语句含 DROP 被拦截

- **GIVEN** AI 调用 `datatalk_execute_sql(sql="DROP TABLE IF EXISTS temp_log; CREATE TABLE temp_log (id INT)")`
- **WHEN** SQL 按 `;` 切分后第一条语句首关键词为 `DROP`
- **THEN** 返回 `{ status: "redirect_to_editor", ... }`
- **AND** 不执行任何数据库操作（整批拦截，不部分执行）

#### Scenario: DELETE + DROP 混合批次被 DDL 门拦截（门顺序）

- **GIVEN** AI 调用 `datatalk_execute_sql(sql="DELETE FROM td_orders; DROP TABLE td_orders")`
- **WHEN** `containsDestructiveDdl()` 在 `containsDelete()` 之前执行
- **THEN** DDL 门先命中（第二条语句含 DROP），返回 `{ status: "redirect_to_editor", ... }`
- **AND** 不走 DELETE 确认流程（`requires_confirmation`）
- **AND** 不执行任何数据库操作
- **NOTE** 若 DDL 门在 DELETE 之后，此 SQL 会被 `containsDelete()` 先捕获 → 确认后回放整批 → DROP TABLE 照样执行。因此 DDL 门必须在 DELETE 门之前

#### Scenario: INSERT OVERWRITE 被拦截

- **GIVEN** AI 调用 `datatalk_execute_sql(sql="INSERT OVERWRITE TABLE target SELECT * FROM source")`
- **WHEN** `ExecuteSqlAction` 检测到 `INSERT OVERWRITE` 模式
- **THEN** 返回 `{ status: "redirect_to_editor", ... }`
- **AND** 不执行任何数据库操作

### Requirement: DELETE 语句 SHALL 触发对话式确认

当 AI 通过 `datatalk_execute_sql` 调用包含 DELETE 语句的 SQL 时，系统 SHALL 不立即执行，而是返回 `requires_confirmation` 状态。AI 负责向用户展示确认提示，用户确认后通过二次调用完成执行。

#### Scenario: DELETE WHERE 需对话确认

- **GIVEN** AI 调用 `datatalk_execute_sql(sql="DELETE FROM orders WHERE status='expired'")`
- **WHEN** `ExecuteSqlAction` 检测到 DELETE 语句
- **THEN** 返回 `{ status: "requires_confirmation", confirmationId: "<uuid>", message: "即将执行 DELETE 操作", sqlPreview: "DELETE FROM orders WHERE status='expired'", affectedObjects: ["orders"] }`
- **AND** 不执行任何数据库操作

#### Scenario: 非破坏性非DELETE语句直接执行

- **GIVEN** AI 调用 `datatalk_execute_sql(sql="INSERT INTO orders (id, name) VALUES (1, 'test')")`
- **WHEN** SQL 非 DELETE 且非破坏性 DDL
- **THEN** 系统 SHALL 直接执行并返回正常结果
- **AND** SHALL NOT 返回 `requires_confirmation` 或 `redirect_to_editor`

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
