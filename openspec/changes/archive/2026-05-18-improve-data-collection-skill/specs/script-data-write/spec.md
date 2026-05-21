## MODIFIED Requirements

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

## ADDED Requirements

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
