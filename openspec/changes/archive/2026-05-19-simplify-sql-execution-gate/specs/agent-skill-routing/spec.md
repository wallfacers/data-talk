## MODIFIED Requirements

### Requirement: SQL 文件导入意图路由

`file-upload-routing` skill 的意图分类 SHALL 包含 SQL 文件（`analysis.type = "SQL"`），当满足以下全部条件时路由到 `datatalk_import_data`：
1. 用户消息含导入意图信号（"导入" / "入库" / "建表" / "import" / "load"）
2. `analysis.summary.statementTypes` 仅包含 INSERT、DROP、CREATE（不含 ALTER、UPDATE、DELETE 等）
3. `analysis.summary.targetTables.size = 1`

不满足以上条件时，SQL 文件保留原 riskLevel 路由规则（查询编辑器 / guarded flow）。

`AGENTS.md` 的 `## Registered Actions` 章节 SHALL 更新 `datatalk_execute_sql` 描述：该 action 执行任意 SQL（SELECT / DML / DDL），仅 DELETE 语句需对话式确认（action 返回 `requires_confirmation`，AI 向用户展示影响摘要，用户确认后 AI 二次调用执行）。SHALL NOT 再声明"read-only"或"SELECT only"硬约束。

#### Scenario: SQL 文件意图分类导入（纯 INSERT）

- **GIVEN** 用户上传 .sql 文件，`analysis.summary.statementTypes = ["INSERT"]`，`analysis.summary.targetTables = ["orders"]`
- **AND** 用户消息含"导入"
- **WHEN** `file-upload-routing` skill 决策路由
- **THEN** 路由到 `datatalk_import_data(source: { type: "file", fileId }, target: { connectionId, tableName: "orders" })`
- **AND** NOT 走 query_editor riskLevel 路径

#### Scenario: SQL 文件意图分类导入（DDL + INSERT 混合）

- **GIVEN** 用户上传 .sql 文件，`analysis.summary.statementTypes = ["DROP", "CREATE", "INSERT"]`，`analysis.summary.targetTables = ["orders"]`
- **AND** 用户消息含"导入"或"建表"
- **WHEN** `file-upload-routing` skill 决策路由
- **THEN** 路由到 `datatalk_import_data(source: { type: "file", fileId }, target: { connectionId, tableName: "orders" })`
- **AND** NOT 走 query_editor riskLevel 路径

#### Scenario: SQL 文件含 ALTER 等不支持 DDL 保留原行为

- **GIVEN** .sql 文件含 `ALTER TABLE` + `INSERT`（`statementTypes = ["ALTER", "INSERT"]`）
- **WHEN** `file-upload-routing` skill 决策路由
- **THEN** 走原 riskLevel 路径（query_editor + guarded flow）
- **AND** NOT 调用 `datatalk_import_data`

#### Scenario: SQL 文件多目标表保留原行为

- **GIVEN** .sql 文件 `targetTables = ["orders", "customers"]`
- **AND** `statementTypes` 全部为 INSERT
- **WHEN** `file-upload-routing` skill 决策路由
- **THEN** 走原 riskLevel 路径（query_editor + guarded DML flow）
- **AND** NOT 调用 `datatalk_import_data`

#### Scenario: AI 调用 execute_sql 执行 DDL/DML 不再拦截

- **GIVEN** AI 调用 `datatalk_execute_sql(sql="CREATE TABLE orders (id INT, name VARCHAR(100)); INSERT INTO orders VALUES (1, 'test')")`
- **WHEN** 后端处理该 action
- **THEN** SHALL 直接执行并返回结果
- **AND** SHALL NOT 返回 `blocked_in_chat`

#### Scenario: AI 调用 execute_sql 执行 DELETE 触发确认

- **GIVEN** AI 调用 `datatalk_execute_sql(sql="DELETE FROM orders WHERE id = 1")`
- **WHEN** 后端处理该 action
- **THEN** SHALL 返回 `{ status: "requires_confirmation", confirmationId, message, sqlPreview, affectedObjects }`
- **AND** SHALL NOT 执行任何数据库操作
