## ADDED Requirements

### Requirement: SQL INSERT 导出 SHALL 按源 connection kind 引用标识符

`datatalk_export_data(format="sql")` 生成的 `INSERT INTO ...` 语句中，表名与列名 SHALL 通过 `IdentifierQuoter.quote(id, kind)` 按源 connection kind 引用。这使得导出 .sql 文件可被同 kind 连接直接回灌（roundtrip）。

#### Scenario: MySQL 源导出 SQL 文件含反引号引用，可回灌 MySQL

- **GIVEN** 源 connection kind 为 `mysql`
- **AND** 查询返回表 `orders` 含列 `id, name, status`
- **WHEN** 调用 `datatalk_export_data(format="sql", connectionId: mysqlId, sql:"SELECT * FROM orders")`
- **THEN** 导出文件中的 INSERT 形如 `` INSERT INTO `orders` (`id`, `name`, `status`) VALUES (...) ``
- **AND** 该文件通过 `datatalk_import_data` 重新导入同一 MySQL 连接 SHALL 成功，不抛 syntax error

#### Scenario: PostgreSQL 源导出 SQL 文件含双引号引用

- **GIVEN** 源 connection kind 为 `postgresql`
- **WHEN** 导出 SQL 格式
- **THEN** INSERT 形如 `INSERT INTO "orders" ("id", "name", "status") VALUES (...)`

#### Scenario: SQL Server 源导出 SQL 文件含方括号引用

- **GIVEN** 源 connection kind 为 `sqlserver`
- **WHEN** 导出 SQL 格式
- **THEN** INSERT 形如 `INSERT INTO [orders] ([id], [name], [status]) VALUES (...)`
