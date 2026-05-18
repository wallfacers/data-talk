## MODIFIED Requirements

### Requirement: 文件导入到数据库表

系统 SHALL 提供一个 MCP action `datatalk_import_data`，允许 AI 通过文件引用（fileId）将上传的 CSV/Excel/JSON/SQL 文件数据导入到目标数据库表。AI 只传递引用参数，实际数据不经过 AI 上下文。

#### Scenario: CSV 文件导入

- **GIVEN** 用户已上传一个 CSV 文件（fileId 有效，文件存在于磁盘）
- **AND** 用户有一个活跃的数据库连接（connectionId 有效）
- **WHEN** AI 调用 `datatalk_import_data(source: { type: "file", fileId }, target: { connectionId, tableName: "orders" })`
- **THEN** 后端从 fileId 解析物理路径，流式读取 CSV（逐行 BufferedReader，每 1000 行一批）
- **AND** 如果 `createTable=true`（默认）且目标表不存在，推断列类型并执行 CREATE TABLE
- **AND** 执行批量 INSERT（PreparedStatement.executeBatch）
- **AND** 返回 `{ rowsImported, tableName, columns[], warnings[], sampleRows: 前3行 }`
- **AND** AI 上下文中不包含任何实际数据行

#### Scenario: Excel 文件导入

- **GIVEN** 用户已上传一个 .xlsx 文件
- **WHEN** AI 调用 `datatalk_import_data(source: { type: "file", fileId }, target: { connectionId, tableName })`
- **THEN** 后端使用 POI SAX 事件模型流式解析第一个 sheet
- **AND** 第一行作为列名，后续行作为数据
- **AND** 批量写入目标表，返回导入结果

#### Scenario: JSON (array_of_objects) 文件导入

- **GIVEN** 用户已上传一个 JSON 文件，结构为对象数组 `[{key: value}, ...]`
- **WHEN** AI 调用 `datatalk_import_data(source: { type: "file", fileId }, target: { connectionId, tableName })`
- **THEN** 后端使用 Jackson JsonParser 流式解析，逐条映射为行数据
- **AND** 批量写入目标表

#### Scenario: SQL 文件导入（纯 INSERT INTO，单目标表）

- **GIVEN** 用户已上传一个 .sql 文件，内容全部为 `INSERT INTO ... VALUES (...)` 语句，且所有语句的目标表相同
- **WHEN** AI 调用 `datatalk_import_data(source: { type: "file", fileId }, target: { connectionId, tableName })`
- **THEN** 后端使用 `SqlStatementSplitter` 状态机分句，再对每条完整语句正则匹配 INSERT INTO，提取表名、列名、值
- **AND** 以第一条 INSERT 的列名为准，后续 INSERT 列名不一致时该行跳过并添加 warning
- **AND** 批量写入目标表，返回 `{ rowsImported, tableName, columns[], warnings[], sampleRows }`
- **AND** 如果所有 INSERT 语句均解析失败，返回错误 `{ errorCode: "SQL_PARSE_FAILED", message: "..." }`

#### Scenario: SQL 文件多行 VALUES

- **GIVEN** 用户已上传一个 .sql 文件，包含 `INSERT INTO t (a,b) VALUES (1,2),(3,4),(5,6)` 多行值形式
- **WHEN** AI 调用 `datatalk_import_data`
- **THEN** 后端解析多行 VALUES，每对括号作为独立行数据
- **AND** 批量写入目标表

#### Scenario: SQL 文件跨行 INSERT

- **GIVEN** 用户已上传一个 .sql 文件，包含跨多行的 INSERT 语句（列名和 VALUES 分布在不同行）
- **WHEN** AI 调用 `datatalk_import_data`
- **THEN** 状态机正确拼接跨行语句为完整语句后再解析
- **AND** 批量写入目标表

#### Scenario: SQL 文件值内包含分号或换行

- **GIVEN** 用户已上传一个 .sql 文件，INSERT 值中包含分号（如 `'error: line 1; line 2'`）
- **WHEN** AI 调用 `datatalk_import_data`
- **THEN** 状态机正确识别引号内的分号不作为语句终止符
- **AND** 值被完整解析，不截断

#### Scenario: SQL 文件解析部分失败

- **GIVEN** 一个 .sql 文件包含 100 条 INSERT 语句，其中 5 条格式异常（如包含子查询）
- **WHEN** AI 调用 `datatalk_import_data`
- **THEN** 成功解析的 95 条正常导入，5 条跳过
- **AND** 返回 `warnings` 包含 "Skipped 5 unparseable INSERT statements"

#### Scenario: SQL 文件目标表名冲突

- **GIVEN** .sql 文件 INSERT 目标为 `td_orders`
- **WHEN** AI 调用 `datatalk_import_data(target: { tableName: "other_table" })`
- **THEN** 返回错误 `{ errorCode: "TABLE_NAME_MISMATCH", message: "SQL file targets td_orders but tableName=other_table" }`
- **AND** 不执行任何写入操作

#### Scenario: SQL 文件多目标表

- **GIVEN** .sql 文件包含 `INSERT INTO orders ...` 和 `INSERT INTO customers ...` 两个不同目标表
- **WHEN** AI 调用 `datatalk_import_data`
- **THEN** 返回错误 `{ errorCode: "MULTI_TABLE_NOT_SUPPORTED", message: "SQL file targets multiple tables [orders, customers]; use query editor instead" }`

#### Scenario: 用户自定义列映射

- **GIVEN** CSV 文件有列 `["id", "name", "qty"]`
- **WHEN** AI 调用 `datatalk_import_data` 且 `columnMappings: { "qty": "quantity", "name": "full_name" }`
- **THEN** 目标表创建/写入时使用映射后的列名 `["id", "full_name", "quantity"]`

#### Scenario: 用户自定义列类型

- **GIVEN** CSV 文件有列 `["id", "price"]`，自动推断 price 为 DOUBLE
- **WHEN** AI 调用 `datatalk_import_data` 且 `columnTypes: { "price": "DECIMAL(10,2)" }`
- **THEN** CREATE TABLE 时 price 列使用 `DECIMAL(10,2)` 而非 DOUBLE

#### Scenario: fileId 无效

- **WHEN** AI 调用 `datatalk_import_data(source: { type: "file", fileId: "nonexistent" }, ...)`
- **THEN** 返回错误 `{ errorCode: "FILE_NOT_FOUND", message: "File not found" }`

#### Scenario: 文件类型不支持导入

- **GIVEN** 用户上传了一张 PNG 图片（fileId 有效）
- **WHEN** AI 调用 `datatalk_import_data(source: { type: "file", fileId }, ...)`
- **THEN** 返回错误 `{ errorCode: "UNSUPPORTED_FILE_TYPE", message: "File type IMAGE cannot be imported as tabular data" }`

#### Scenario: 目标表已存在且 createTable=false

- **GIVEN** 目标表 `orders` 已存在，有列 `id INT, name VARCHAR(100)`
- **WHEN** AI 调用 `datatalk_import_data` 且 `createTable: false`
- **THEN** 直接 INSERT，列名按源文件列名匹配目标表列
- **AND** 源文件列不在目标表中时，该列数据忽略并添加 warning
