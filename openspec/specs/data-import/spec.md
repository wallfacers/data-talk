## ADDED Requirements

### Requirement: 文件导入到数据库表

系统 SHALL 提供一个 MCP action `datatalk_import_data`，允许 AI 通过文件引用（fileId）将上传的 CSV/Excel/JSON 文件数据导入到目标数据库表。AI 只传递引用参数，实际数据不经过 AI 上下文。

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

### Requirement: 跨库表复制

系统 SHALL 支持通过 `datatalk_import_data` action 从一个数据库查询结果直接导入到另一个数据库表，数据在服务端直通，不经过 AI 上下文。

#### Scenario: 跨库复制全表

- **GIVEN** 用户有连接 A（源库）和连接 B（目标库）
- **WHEN** AI 调用 `datatalk_import_data(source: { type: "query", connectionId: "A", sql: "SELECT * FROM orders" }, target: { connectionId: "B", tableName: "orders_copy" })`
- **THEN** 后端打开源库 A 的 JDBC cursor（TYPE_FORWARD_ONLY, fetchSize=500）
- **AND** 同时打开目标库 B 的 JDBC 连接
- **AND** 流式读取 cursor → 批量写入 B（每 1000 行一批）
- **AND** 返回 `{ rowsImported, tableName, columns[], sampleRows: 前3行 }`

#### Scenario: 跨库复制带过滤

- **GIVEN** 用户有连接 A 和连接 B
- **WHEN** AI 调用 `datatalk_import_data(source: { type: "query", connectionId: "A", sql: "SELECT * FROM orders WHERE created_at > '2026-01-01'" }, target: { connectionId: "B", tableName: "recent_orders" })`
- **THEN** 只导入满足 WHERE 条件的行

#### Scenario: 源库连接失败

- **WHEN** AI 调用 `datatalk_import_data` 且源库 connectionId 无效或连接失败
- **THEN** 返回错误 `{ errorCode: "SOURCE_CONNECTION_FAILED", message: "..." }`

#### Scenario: 源库 SQL 执行失败

- **WHEN** AI 调用 `datatalk_import_data` 且 source.sql 有语法错误
- **THEN** 返回错误 `{ errorCode: "SOURCE_QUERY_FAILED", message: "..." }`

#### Scenario: 跨库复制源库异常时部分导入

- **GIVEN** 跨库复制正在进行，已写入目标库 3000 行
- **WHEN** 源库连接中断
- **THEN** 已写入目标库的 3000 行保留（已 commit，不回滚）
- **AND** 返回 `{ rowsImported: 3000, warnings: ["Source connection lost after 3000 rows"] }`

#### Scenario: 跨库复制目标库异常

- **GIVEN** 跨库复制正在进行，已 commit 2000 行到目标库
- **WHEN** 目标库出现约束冲突（如 duplicate key）
- **THEN** 已 commit 的 2000 行保留
- **AND** 返回 `{ rowsImported: 2000, warnings: ["Target constraint violation after 2000 rows"] }`

### Requirement: 导入内存恒定

`DataImportService` SHALL 使用流式解析策略，确保无论源文件大小，JVM 堆内存使用恒定。

#### Scenario: 50MB CSV 导入不 OOM

- **GIVEN** 一个 50MB 的 CSV 文件（约 50 万行）
- **WHEN** `datatalk_import_data` 执行导入
- **THEN** JVM 堆增量 SHALL < 50MB（不含 JDBC 驱动和 HikariPool 开销）
- **AND** 每批 1000 行写入后，该批行数据可被 GC 回收

#### Scenario: 导入过程可取消（Day-2 预留）

- **WHEN** 导入正在进行中
- **THEN** Day-1 不要求实现取消功能，但 action 输出 SHALL 包含 `importId` 字段，供 Day-2 扩展取消 API

### Requirement: 多文件导入语义

`datatalk_import_data` SHALL 只接受单个文件引用或单个查询作为数据源。多文件场景由 AI 逐文件多次调用 action 循环编排。

#### Scenario: 多文件导入

- **GIVEN** 用户一次拖入 3 个 CSV 文件（fileId1, fileId2, fileId3）
- **WHEN** AI 需要将 3 个文件都导入到同一张表
- **THEN** AI SHALL 依次调用 `datatalk_import_data(source: { type: "file", fileId: fileId1 }, target: { connectionId, tableName }, createTable: true)`
- **AND** 然后调用 `datatalk_import_data(source: { type: "file", fileId: fileId2 }, target: { connectionId, tableName }, createTable: false)`
- **AND** 然后调用 `datatalk_import_data(source: { type: "file", fileId: fileId3 }, target: { connectionId, tableName }, createTable: false)`
- **AND** 汇总 3 次调用结果向用户报告

#### Scenario: 多文件导入 schema 不一致

- **GIVEN** 两个 CSV 文件列名不同（fileId1 有 [id,name]，fileId2 有 [id,title]）
- **WHEN** AI 逐文件导入
- **THEN** 第二次导入时 AI 可通过 `columnMappings` 参数将 title 映射为 name，或为每个文件导入到不同的表

`datatalk_import_data` 返回结果 SHALL 包含足够信息让 AI 向用户汇报，但不包含完整数据集。

#### Scenario: 返回摘要信息

- **WHEN** 导入完成
- **THEN** 返回结果 SHALL 包含：`rowsImported`（总数）、`tableName`、`columns[]`（列名+类型+是否推断）、`sampleRows`（前 3 行）、`warnings[]`
- **AND** `sampleRows` SHALL NOT 超过 3 行
- **AND** 返回序列化后总大小 SHALL < 128KB（`DataTalkMcpService` 输出 budget）
