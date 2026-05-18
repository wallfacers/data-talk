## ADDED Requirements

### Requirement: 批量写入（单次全量）

脚本 SHALL 能通过后端 REST API 将一组数据一次性写入目标数据库表。首次写入时必须回写 `targetTable` 到 `script_run` 记录。

#### Scenario: 自动建表 + 写入

- **GIVEN** 一个有效的 ScriptToken 和 connectionId
- **WHEN** 脚本调用 `POST /api/script-data/write`，body 包含 `{ tableName, rows[], createTable: true }`
- **THEN** 后端校验 token 有效性
- **AND** 如果目标表不存在且 `createTable=true`，后端推断列类型并执行 CREATE TABLE
- **AND** 后端执行批量 INSERT
- **AND** 返回 `{ rowsInserted, tableName, columnsCreated }`

#### Scenario: 写入已有表

- **GIVEN** 一个有效的 ScriptToken 和 connectionId，目标表已存在
- **WHEN** 脚本调用 `POST /api/script-data/write`，body 包含 `{ tableName, rows[], createTable: false }`
- **THEN** 后端校验 token + 表存在性
- **AND** 后端执行批量 INSERT（列名匹配已有表 schema）
- **AND** 返回 `{ rowsInserted, tableName }`

#### Scenario: Token 无效或过期

- **WHEN** 脚本使用无效或过期的 ScriptToken 调用 write API
- **THEN** 后端返回 HTTP 401，body 包含 `{ error: "SCRIPT_TOKEN_INVALID" }`
- **AND** 不执行任何数据库操作

#### Scenario: 列类型推断

- **WHEN** `createTable=true` 且目标表不存在
- **THEN** 后端从 `rows[]` 采样推断列类型（复用 TypeInferrer 逻辑）
- **AND** 推断规则：字符串→VARCHAR(255)，整数→BIGINT，浮点→DOUBLE，布尔→BOOLEAN，null→TEXT
- **AND** NULL 值列默认为 TEXT

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

### Requirement: Token 安全约束

ScriptToken SHALL 遵循安全约束，防止滥用。

#### Scenario: Token 一次性使用后失效

- **WHEN** ScriptToken 被用于首次 write API 调用
- **THEN** token 保持有效直到脚本运行结束（多批写入需要多次使用同一 token）
- **AND** 脚本运行结束后 token 立即失效

#### Scenario: Token 绑定 connectionId

- **WHEN** 脚本使用 token 调用 write API
- **THEN** 后端校验 token 绑定的 connectionId 与请求中的 connectionId 一致
- **AND** 不一致时返回 HTTP 403

#### Scenario: Token TTL 过期

- **WHEN** ScriptToken 签发超过 10 分钟
- **THEN** token 失效，所有 API 调用返回 HTTP 401

### Requirement: 数据源类型兼容性

ScriptDataWriteService SHALL 支持所有已注册的数据库类型。

#### Scenario: MySQL/PostgreSQL/H2/SQLite 写入

- **WHEN** 脚本向 MySQL/PostgreSQL/H2/SQLite 连接写入数据
- **THEN** DDL 和 INSERT 语句使用对应方言
- **AND** 批量 INSERT 使用对应优化策略（MySQL multi-value, PostgreSQL batch）

#### Scenario: 其他数据库类型写入

- **WHEN** 脚本向 Oracle/SQLServer/DuckDB/ClickHouse 等连接写入数据
- **THEN** 使用通用 JDBC INSERT（单条或小批次），确保兼容性

### Requirement: targetTable 回写

ScriptDataWriteService SHALL 在首次成功写入数据后，更新 `script_run.target_table` 字段。

#### Scenario: 首次写入回写 targetTable

- **GIVEN** 一个有效的 ScriptToken，`script_run.target_table` 为 NULL
- **WHEN** `POST /api/script-data/write` 成功写入 N 行（N>0）
- **THEN** 后端更新 `script_run.target_table = tableName`
- **AND** targetTable 字段不再重复更新（仅首次写入时设置）

#### Scenario: 已写入过的 run 不重复回写

- **GIVEN** 一个有效的 ScriptToken，`script_run.target_table` 已设置为 `"my_table"`
- **WHEN** 同一脚本再次调用 `POST /api/script-data/write` 写入同名表
- **THEN** 后端不重复更新 `target_table` 字段

#### Scenario: 流式写入回写 targetTable

- **GIVEN** 一个有效的流式写入 session，`script_run.target_table` 为 NULL
- **WHEN** `POST /api/script-data/batch`（batchIndex=0）成功写入
- **THEN** 后端更新 `script_run.target_table = tableName`
