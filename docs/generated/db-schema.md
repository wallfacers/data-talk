# 数据库 Schema 参考

> 自动生成自 `server/data-talk-infrastructure/src/main/resources/db/migration/V1__init.sql`
> 最后更新：2026-04-16

SQLite 元数据库，由 Flyway 管理迁移。

## connections — 数据库连接配置

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| id | TEXT | PK | UUID |
| kind | TEXT | NOT NULL | 数据库类型 (mysql/postgresql/h2) |
| host | TEXT | NOT NULL | 主机地址 |
| port | INTEGER | NOT NULL | 端口 |
| database_name | TEXT | | 数据库名 |
| username | TEXT | NOT NULL | 用户名 |
| password_enc | BLOB | NOT NULL | AES-GCM 加密密码 |
| schema_digest | TEXT | | Schema 指纹 |
| created_at | INTEGER | NOT NULL | Unix 时间戳 |

## sessions — 会话

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| id | TEXT | PK | UUID |
| connection_id | TEXT | FK → connections | 关联的数据库连接 |
| title | TEXT | NOT NULL | 会话标题 |
| has_ever_sent | INTEGER | NOT NULL, DEFAULT 0 | 是否已发送过消息 (HERO→SPLIT 状态判断) |
| opencode_sid | TEXT | | OpenCode 会话 ID |
| created_at | INTEGER | NOT NULL | 创建时间 |
| updated_at | INTEGER | NOT NULL | 更新时间 |

## messages — 消息

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| id | TEXT | PK | UUID |
| session_id | TEXT | FK → sessions, NOT NULL | 所属会话 |
| role | TEXT | NOT NULL | user / assistant / system |
| parts_json | TEXT | NOT NULL | Part[] JSON，兼容 OpenCode Part union |
| created_at | INTEGER | NOT NULL | 创建时间 |

索引：`idx_messages_session(session_id, created_at)`

## artifacts — 工件（表格/图表/ER 图）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| id | TEXT | PK (复合) | 工件 ID |
| version | INTEGER | PK (复合) | 版本号 |
| session_id | TEXT | FK → sessions, NOT NULL | 所属会话 |
| kind | TEXT | NOT NULL, CHECK(table/chart/erd) | 工件类型 |
| produced_by | TEXT | NOT NULL | 产出该工件的 Action ID |
| payload_ref | TEXT | NOT NULL | 内容引用 |
| payload_size | INTEGER | NOT NULL | 内容大小 |
| supersedes_id | TEXT | | 被替代的工件 ID |
| supersedes_ver | INTEGER | | 被替代的版本 |
| pinned | INTEGER | NOT NULL, DEFAULT 0 | 是否钉住 |
| created_at | INTEGER | NOT NULL | 创建时间 |

索引：`idx_artifacts_session(session_id, created_at)`

## action_invocations — Action 调用记录

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| call_id | TEXT | PK | 调用 ID |
| session_id | TEXT | FK → sessions, NOT NULL | 所属会话 |
| action_id | TEXT | NOT NULL | Action ID |
| status | TEXT | NOT NULL | pending / running / success / error / timeout |
| input_json | TEXT | NOT NULL | 输入参数 |
| output_json | TEXT | | 输出结果 |
| error_json | TEXT | | 错误信息 |
| started_at | INTEGER | NOT NULL | 开始时间 |
| ended_at | INTEGER | | 结束时间 |

## events — 事件日志

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| event_id | INTEGER | PK (复合) | 会话内自增序号 |
| session_id | TEXT | PK (复合), FK → sessions | 所属会话 |
| event_type | TEXT | NOT NULL | DtEvent 类型名 |
| payload_json | TEXT | NOT NULL | 事件载荷 |
| ts | INTEGER | NOT NULL | Unix 时间戳 |

索引：`idx_events_ts(ts)`

## query_results — 查询结果缓存

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| handle | TEXT | PK | 结果句柄 |
| session_id | TEXT | FK → sessions, NOT NULL | 所属会话 |
| columns_json | TEXT | NOT NULL | 列定义 JSON |
| rows_ndjson | TEXT | NOT NULL | 行数据 NDJSON |
| row_count | INTEGER | NOT NULL | 行数 |
| created_at | INTEGER | NOT NULL | 创建时间 |
| ttl_at | INTEGER | NOT NULL | 过期时间 |
