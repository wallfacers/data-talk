## ADDED Requirements

### Requirement: 脚本写 API 生成的 DDL 与 INSERT SHALL 按目标 connection kind 引用标识符

`POST /api/script-data/write` 与 `POST /api/script-data/batch` 在 `createTable=true` 时生成的 CREATE TABLE 和后续 INSERT 语句 SHALL 通过 `IdentifierQuoter.quote(id, kind)` 按目标 connection kind 派发引用，覆盖 19 种 first-class kind。

#### Scenario: 脚本写 MySQL 目标 SHALL 生成反引号 DDL

- **GIVEN** 目标 connection kind 为 `mysql`
- **AND** 脚本以 `{ rows: [...], createTable: true, tableName: "events" }` 调用 `/api/script-data/write`
- **WHEN** 后端处理请求
- **THEN** 执行的 DDL 形如 `` CREATE TABLE `events` (...) ``
- **AND** 执行的 INSERT 形如 `` INSERT INTO `events` (...) VALUES (?, ?, ...) ``

#### Scenario: 脚本写 PostgreSQL 目标 SHALL 生成双引号 DDL

- **GIVEN** 目标 connection kind 为 `postgresql`
- **WHEN** 同上请求
- **THEN** DDL/INSERT 用双引号引用所有标识符

#### Scenario: 脚本写 SQL Server 目标 SHALL 生成方括号 DDL

- **GIVEN** 目标 connection kind 为 `sqlserver`
- **WHEN** 同上请求
- **THEN** DDL/INSERT 用方括号引用所有标识符
