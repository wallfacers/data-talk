## ADDED Requirements

### Requirement: `datatalk_import_data` 生成的 DDL 与 INSERT SHALL 按目标 connection kind 引用标识符

`datatalk_import_data` 在 `createTable=true` 时生成的 `CREATE TABLE` 语句和后续 `INSERT INTO ... (cols...) VALUES (?...)` 语句中，所有表名和列名 SHALL 通过 `IdentifierQuoter.quote(id, kind)` 引用，使其在 MySQL（反引号）、PostgreSQL/H2/SQLite/Oracle（双引号）、SQLServer（方括号）等方言下都能正确解析。

#### Scenario: MySQL 目标连接 import_data 生成反引号 DDL

- **GIVEN** 目标 connection kind 为 `mysql`
- **AND** 上传的 CSV 含列 `id, name, created_at`
- **AND** `tableName="orders"`, `createTable=true`
- **WHEN** AI 调用 `datatalk_import_data`
- **THEN** 生成的 DDL 形如 `` CREATE TABLE `orders` (`id` BIGINT, `name` VARCHAR(255), `created_at` VARCHAR(255)) ``
- **AND** 生成的 INSERT 形如 `` INSERT INTO `orders` (`id`, `name`, `created_at`) VALUES (?, ?, ?) ``
- **AND** MySQL 默认 sql_mode 下不抛 syntax error

#### Scenario: PostgreSQL 目标连接 import_data 生成双引号 DDL

- **GIVEN** 目标 connection kind 为 `postgresql`
- **AND** 其他条件同上
- **WHEN** AI 调用 `datatalk_import_data`
- **THEN** 生成的 DDL 形如 `CREATE TABLE "orders" ("id" BIGINT, "name" VARCHAR(255), "created_at" VARCHAR(255))`

#### Scenario: SQL Server 目标连接 import_data 生成方括号 DDL

- **GIVEN** 目标 connection kind 为 `sqlserver`
- **WHEN** AI 调用 `datatalk_import_data`
- **THEN** 生成的 DDL 形如 `CREATE TABLE [orders] ([id] BIGINT, [name] VARCHAR(255), [created_at] VARCHAR(255))`

#### Scenario: MySQL 含保留字列名 SHALL 正确引用

- **GIVEN** 目标 connection kind 为 `mysql`
- **AND** 上传的 CSV 含列 `select, order, group`
- **WHEN** AI 调用 `datatalk_import_data`
- **THEN** 生成的 INSERT 中列名形如 `` `select`, `order`, `group` ``，可执行不报关键字冲突

### Requirement: 跨库表复制 SHALL 按目标 connection kind 引用标识符

`datatalk_import_data(source.type="query")` 通过 `ScriptDataWriteService.writeStream` 把源 ResultSet 写入目标库时，生成的 CREATE TABLE 与 INSERT INTO SHALL 使用 `IdentifierQuoter` 按目标 kind 派发引用，不取决于源 kind。

#### Scenario: 源 PostgreSQL 目标 MySQL 复制 SHALL 在目标侧使用反引号

- **GIVEN** 源 connection kind 为 `postgresql`，目标 connection kind 为 `mysql`
- **WHEN** AI 调用 `datatalk_import_data(source={ type:"query", connectionId: pgId, sql:"SELECT id, name FROM users" }, target={ connectionId: mysqlId, tableName:"users_copy" })`
- **THEN** 目标侧执行的 DDL 形如 `` CREATE TABLE `users_copy` (`id` ..., `name` ...) ``
- **AND** 目标侧执行的 INSERT 形如 `` INSERT INTO `users_copy` (`id`, `name`) VALUES (?, ?) ``
