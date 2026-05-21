## MODIFIED Requirements

### Requirement: 流式写入（多批次增量）

脚本 SHALL 能通过后端 REST API 分多批次将数据流式写入目标数据库表。

#### Scenario: 开始流式写入 session

- **WHEN** 脚本调用 `POST /api/script-data/batch`，body 包含 `{ tableName, rows: [...chunk1...], batchIndex: 0, createTable: true, totalBatches: null }`
- **THEN** 后端创建写入 session，建表（如果需要），执行第一批 INSERT
- **AND** 返回 `{ sessionId, rowsInserted }`

#### Scenario: 追加批次

- **GIVEN** 一个活跃的写入 session
- **WHEN** 脚本调用 `POST /api/script-data/batch`，body 包含 `{ sessionId, rows: [...chunkN...], batchIndex: N }`
- **THEN** 后端追加执行 INSERT
- **AND** 返回 `{ sessionId, rowsInserted, totalRowsInserted }`

#### Scenario: 关闭流式写入 session

- **WHEN** 脚本调用 `POST /api/script-data/batch/close`，body 包含 `{ sessionId }`
- **THEN** 后端关闭 session，返回 `{ totalRowsInserted, tableName }`

#### Scenario: 流式写入 session 超时

- **GIVEN** 一个流式写入 session 已 5 分钟无新请求
- **WHEN** 后端扫描到超时 session
- **THEN** 自动关闭 session，保留已写入数据
- **AND** 记录 warning 日志

## ADDED Requirements

### Requirement: JDBC ResultSet 流式写入

`ScriptDataWriteService` SHALL 新增方法 `writeStream`，接受 JDBC `ResultSet` cursor 作为数据源，实现从 cursor 到目标表的流式写入。

#### Scenario: cursor 到目标表流式写入

- **GIVEN** 一个有效的源库 JDBC `ResultSet`（TYPE_FORWARD_ONLY, CONCUR_READ_ONLY）
- **AND** 目标库 connectionId 和 tableName
- **WHEN** `writeStream(connectionId, tableName, resultSet, createTable)` 被调用
- **THEN** 服务从 `ResultSet.getMetaData()` 推断列名和类型
- **AND** 如果 `createTable=true` 且目标表不存在，执行 CREATE TABLE
- **AND** 逐批（每 1000 行）从 cursor 读取 → `PreparedStatement.executeBatch()` 写入目标库
- **AND** 返回 `WriteResult { rowsInserted, tableName, columnsCreated }`
- **AND** 不关闭传入的 `ResultSet`（调用方负责管理 cursor 和连接生命周期）

#### Scenario: cursor 列类型推断

- **WHEN** `writeStream` 从 `ResultSetMetaData.getColumnType()` 推断类型
- **THEN** 映射规则：VARCHAR/CHAR/CLOB → VARCHAR(255), INTEGER/BIGINT → BIGINT, FLOAT/DOUBLE/REAL → DOUBLE, BOOLEAN/BIT → BOOLEAN, TIMESTAMP/DATE/TIME → TIMESTAMP, 其余 → TEXT
- **AND** 推断结果可通过 `columnTypes` 参数覆盖

#### Scenario: 大结果集写入不 OOM

- **GIVEN** 一个 50 万行的 `ResultSet`
- **WHEN** `writeStream` 执行
- **THEN** JVM 堆增量 SHALL < 50MB
- **AND** 每批 1000 行写入后，该批行数据可被 GC 回收
