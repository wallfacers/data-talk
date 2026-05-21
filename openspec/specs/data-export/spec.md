## ADDED Requirements

### Requirement: 服务端流式数据导出

系统 SHALL 提供一个 MCP action `datatalk_export_data`，允许 AI 触发服务端数据导出，支持 CSV/JSON/Excel(.xlsx)/SQL INSERT 四种格式。

#### Scenario: CSV 同步流式导出（<10K 行）

- **GIVEN** 用户有一个活跃的数据库连接
- **WHEN** AI 调用 `datatalk_export_data(source: { connectionId, sql: "SELECT * FROM orders" }, format: "csv")`
- **AND** 查询结果 < 10,000 行
- **THEN** 后端使用 JDBC cursor（fetchSize=500）流式读取
- **AND** 通过 Spring `StreamingResponseBody` 直接流式输出 CSV 到 HTTP 响应
- **AND** 返回 `{ downloadUrl, rowCount, fileSize, format: "csv" }`

#### Scenario: JSON 同步流式导出（<10K 行）

- **WHEN** AI 调用 `datatalk_export_data` 且 `format: "json"`
- **THEN** 输出 JSON 数组格式 `[ {key: value}, ... ]`
- **AND** UTF-8 编码

#### Scenario: Excel 同步流式导出（<10K 行）

- **WHEN** AI 调用 `datatalk_export_data` 且 `format: "xlsx"`
- **THEN** 使用 Apache POI `SXSSFWorkbook`（窗口 100 行）流式写入
- **AND** 第一行为列名 header
- **AND** 列类型使用字符串表示

#### Scenario: SQL INSERT 格式导出（<10K 行）

- **WHEN** AI 调用 `datatalk_export_data` 且 `format: "sql_insert"`
- **THEN** 输出 batch INSERT 格式：`INSERT INTO table_name (col1, col2) VALUES (v1, v2), (v3, v4), ...;`
- **AND** 每 100 行一个 INSERT 语句
- **AND** 字符串值正确转义单引号

### Requirement: 大文件导出（≥10K 行）

系统 SHALL 对 ≥10K 行的导出使用后台 virtual thread 写临时文件模式。

#### Scenario: 10K-100K 行导出

- **GIVEN** 查询结果在 10,000 到 100,000 行之间
- **WHEN** AI 调用 `datatalk_export_data`
- **THEN** 后端创建一个 export job（exportId）
- **AND** 立即返回 `{ downloadUrl: null, exportId, status: "processing", estimatedRows }`
- **AND** 后台 virtual thread 使用 JDBC cursor + 流式格式化写入临时文件 `~/.data-talk/exports/{exportId}/{filename}`
- **AND** 写入完成后通过 SSE 推送 `export.completed` 事件（含 downloadUrl）
- **AND** AI 收到事件后向用户汇报下载链接

#### Scenario: >100K 行导出

- **GIVEN** 查询结果 > 100,000 行
- **WHEN** AI 调用 `datatalk_export_data`
- **THEN** 同 10K-100K 流程
- **AND** 如果 format 为 "xlsx" 且行数 > 1,048,576，返回错误 `{ errorCode: "XLSX_ROW_LIMIT_EXCEEDED", message: "Excel format supports max 1,048,576 rows. Please use CSV format instead." }`

#### Scenario: 下载临时文件

- **GIVEN** 一个已完成的 export job（exportId 有效，文件未过期）
- **WHEN** 前端请求 `GET /api/exports/{exportId}/download`
- **THEN** 返回文件流（Content-Type 和 Content-Disposition 根据格式设置）

#### Scenario: 导出文件不存在或已过期

- **WHEN** 前端请求一个已删除或过期的 export 文件
- **THEN** 返回 HTTP 404

#### Scenario: 临时文件惰性清理

- **GIVEN** 一个导出文件创建于 70 分钟前（超过 1 小时 TTL）
- **WHEN** 任何请求到达 `DataExportController`（不一定是该文件）
- **THEN** 控制器在处理请求前检查目标文件 `lastModified`，超过 TTL 的文件被删除

#### Scenario: 应用启动时清理残留导出文件

- **WHEN** 应用启动
- **THEN** 扫描 `~/.data-talk/exports/` 目录，删除所有超过 1 小时的文件和空目录

### Requirement: 大文件导出完成通知

系统 SHALL 在后台导出任务完成时通过现有 SSE channel 通知前端。

#### Scenario: export.completed SSE 事件发射

- **GIVEN** 一个 ≥10K 行的导出任务正在后台执行
- **WHEN** 后台 virtual thread 完成写入临时文件
- **THEN** 系统通过 `SessionBus` 发射 `export.completed` 事件
- **AND** 事件 payload 包含 `{ exportId, downloadUrl, rowCount, format, fileSize }`
- **AND** 前端通过现有 SSE 监听接收该事件

### Requirement: 从表名导出

`datatalk_export_data` SHALL 支持直接指定表名而非 SQL 语句。

#### Scenario: 按表名导出

- **WHEN** AI 调用 `datatalk_export_data(source: { connectionId, tableName: "orders" }, format: "csv")`
- **THEN** 后端自动生成 `SELECT * FROM orders` 并执行导出

### Requirement: 导出文件命名

导出文件 SHALL 使用有意义的默认文件名。

#### Scenario: 默认文件名

- **WHEN** AI 调用 `datatalk_export_data` 且未指定 `options.filename`
- **THEN** 默认文件名格式为 `export-{tableName或"query"}-{timestamp}.{ext}`
- **AND** timestamp 格式 `yyyyMMdd-HHmmss`

#### Scenario: 自定义文件名

- **WHEN** AI 调用 `datatalk_export_data` 且指定 `options.filename: "my-data"`
- **THEN** 文件名为 `my-data.{ext}`

### Requirement: 导出安全性

导出操作 SHALL 遵循现有 SQL 执行安全约束。

#### Scenario: SQL 注入防护

- **WHEN** AI 调用 `datatalk_export_data` 且 source.sql 包含 DML/DDL 语句
- **THEN** 导出只执行 SELECT 类查询，DML/DDL 被拒绝
- **AND** 返回错误 `{ errorCode: "QUERY_NOT_READ_ONLY" }`

#### Scenario: 结果集大小限制

- **WHEN** AI 调用 `datatalk_export_data` 且未指定 `options.maxRows`
- **THEN** 默认最大行数为 1,000,000（100 万行）
- **AND** 超限时返回 warning 并截断

### Requirement: 导出磁盘空间保护

导出服务 SHALL 限制单个导出文件大小。

#### Scenario: 单文件大小上限

- **WHEN** 导出文件超过 500MB
- **THEN** 导出停止，返回 `{ warnings: ["Export truncated at 500MB"] }`
- **AND** 已写入部分仍然可用

### Requirement: export.completed SSE 事件处理

前端 SHALL 在收到 `export.completed` SSE 事件时使用统一下载函数处理下载。

#### Scenario: Toast 下载按钮使用统一下载函数

- **GIVEN** 前端收到 `export.completed` SSE 事件
- **WHEN** toast 通知展示 "下载" 按钮
- **THEN** 点击按钮调用 `downloadFromUrl(downloadUrl, filename)` 而非 `window.open(url, '_blank')`
- **AND** filename 基于 format 推断（`export-{exportId}.{ext}`）

### Requirement: 导出记录来源会话

`DataExportService` SHALL 在创建导出任务时记录发起调用的 session ID 作为 `originSessionId`，以便存储治理追溯来源。

#### Scenario: 同步导出记录 originSessionId

- **WHEN** AI 调用 `datatalk_export_data` 且结果 < 10K 行（同步路径）
- **THEN** 返回的 `DataExportResult` 中包含 `originSessionId` 字段
- **AND** `originSessionId` 为发起调用的 session ID

#### Scenario: 异步导出记录 originSessionId

- **WHEN** AI 调用 `datatalk_export_data` 且结果 ≥ 10K 行（异步路径）
- **THEN** 创建的 export job metadata 包含 `originSessionId`
- **AND** export 列表查询可返回该字段

#### Scenario: originSessionId 为 null

- **WHEN** 导出由非 session 上下文触发（如直接 API 调用）
- **THEN** `originSessionId` 为 null

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
