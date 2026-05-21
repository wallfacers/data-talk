## MODIFIED Requirements

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

（保持不变，与现有行为一致）当 AI 通过 `datatalk_execute_sql` 调用包含 DELETE 语句的 SQL 时，系统 SHALL 不立即执行，而是返回 `requires_confirmation` 状态。AI 负责向用户展示确认提示，用户确认后通过二次调用完成执行。

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

## REMOVED Requirements

### Requirement: 非DELETE语句直接执行

**Reason**: 原 spec 明确规定 DROP/CREATE 等 DDL 直接执行（含 DROP TABLE IF EXISTS temp_log; CREATE TABLE 的示例），这是导致安全漏洞的根源。现在所有破坏性 DDL 都被拦截。

**Migration**: 原"GIVEN DROP TABLE IF EXISTS temp_log; CREATE TABLE THEN 直接执行"场景被移除。破坏性语句（DROP）现在返回 `redirect_to_editor`，建设性语句（CREATE）独立调用时仍可直接执行。
