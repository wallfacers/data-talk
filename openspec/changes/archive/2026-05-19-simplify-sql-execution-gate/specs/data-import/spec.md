## MODIFIED Requirements

### Requirement: 文件导入到数据库表

系统 SHALL 提供一个 MCP action `datatalk_import_data`，允许 AI 通过文件引用（fileId）将上传的 CSV/Excel/JSON/SQL 文件数据导入到目标数据库表。AI 只传递引用参数，实际数据不经过 AI 上下文。对于 SQL 文件，系统 SHALL 支持纯 INSERT 和 DDL+INSERT 混合两种格式。

#### Scenario: CSV 文件导入

- **GIVEN** 用户已上传一个 CSV 文件（fileId 有效，文件存在于磁盘）
- **AND** 用户有一个活跃的数据库连接（connectionId 有效）
- **WHEN** AI 调用 `datatalk_import_data(source: { type: "file", fileId }, target: { connectionId, tableName: "orders" })`
- **THEN** 后端从 fileId 解析物理路径，流式读取 CSV（逐行 BufferedReader，每 1000 行一批）
- **AND** 如果 `createTable=true`（默认）且目标表不存在，推断列类型并执行 CREATE TABLE
- **AND** 执行批量 INSERT（PreparedStatement.executeBatch）
- **AND** 返回 `{ rowsImported, tableName, columns[], warnings[], sampleRows: 前3行 }`
- **AND** AI 上下文中不包含任何实际数据行

#### Scenario: SQL 文件导入（DDL + INSERT 混合，单目标表）

- **GIVEN** 用户已上传一个 .sql 文件，内容为 `DROP TABLE IF EXISTS orders; CREATE TABLE orders (...); INSERT INTO orders ... VALUES (...); INSERT INTO orders ... VALUES (...)`
- **AND** 所有 INSERT 的目标表相同（`orders`）
- **WHEN** AI 调用 `datatalk_import_data(source: { type: "file", fileId }, target: { connectionId, tableName: "orders" })`
- **THEN** 后端使用 `SqlStreamReader` 分离 DDL 前缀和 INSERT 数据
- **AND** 先执行 DDL 前缀（`DROP TABLE IF EXISTS orders; CREATE TABLE orders (...)`）via JDBC `Statement.execute()`
- **AND** 然后流式解析 INSERT 语句，批量写入目标表
- **AND** 返回 `{ rowsImported, tableName, columns[], warnings[], sampleRows }`

#### Scenario: SQL 文件导入（纯 INSERT INTO，单目标表）

- **GIVEN** 用户已上传一个 .sql 文件，内容全部为 `INSERT INTO ... VALUES (...)` 语句，且所有语句的目标表相同
- **WHEN** AI 调用 `datatalk_import_data(source: { type: "file", fileId }, target: { connectionId, tableName })`
- **THEN** 后端使用 `SqlStatementSplitter` 状态机分句，再对每条完整语句正则匹配 INSERT INTO，提取表名、列名、值
- **AND** 以第一条 INSERT 的列名为准，后续 INSERT 列名不一致时该行跳过并添加 warning
- **AND** 批量写入目标表，返回 `{ rowsImported, tableName, columns[], warnings[], sampleRows }`
- **AND** 如果所有 INSERT 语句均解析失败，返回错误 `{ errorCode: "SQL_PARSE_FAILED", message: "..." }`

#### Scenario: SQL 文件 DDL 目标表与参数 tableName 不一致

- **GIVEN** .sql 文件 DDL 为 `CREATE TABLE legacy_orders (...)`，INSERT 目标也为 `legacy_orders`
- **WHEN** AI 调用 `datatalk_import_data(target: { tableName: "orders" })`
- **THEN** 返回错误 `{ errorCode: "TABLE_NAME_MISMATCH", message: "SQL file targets legacy_orders but tableName=orders" }`
- **AND** 不执行任何操作

#### Scenario: SQL 文件包含不支持的 DDL 语句

- **GIVEN** .sql 文件包含 `ALTER TABLE orders ADD COLUMN ...` 或 `CREATE INDEX ...`
- **WHEN** AI 调用 `datatalk_import_data`
- **THEN** 返回错误 `{ errorCode: "UNSUPPORTED_DDL", message: "SQL file contains unsupported DDL: ALTER TABLE. Only DROP TABLE IF EXISTS and CREATE TABLE are supported" }`
- **AND** 建议用户使用查询编辑器执行

#### Scenario: SQL 文件 DROP TABLE 未带 IF EXISTS

- **GIVEN** .sql 文件包含 `DROP TABLE orders;`（无 IF EXISTS）
- **WHEN** AI 调用 `datatalk_import_data`
- **THEN** 系统 SHALL 仍执行该 DDL（不强制要求 IF EXISTS）
- **AND** 如果表不存在且数据库方言不支持无 IF EXISTS 的 DROP，DDL 执行失败 SHALL 记入 `warnings` 并继续执行后续 INSERT（此时 INSERT 可能因表不存在而失败，该失败作为最终错误返回）

#### Scenario: SQL 文件多目标表

- **GIVEN** .sql 文件包含 `INSERT INTO orders ...` 和 `INSERT INTO customers ...` 两个不同目标表
- **WHEN** AI 调用 `datatalk_import_data`
- **THEN** 返回错误 `{ errorCode: "MULTI_TABLE_NOT_SUPPORTED", message: "SQL file targets multiple tables [orders, customers]; use query editor instead" }`
