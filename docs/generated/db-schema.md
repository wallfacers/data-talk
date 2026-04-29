# 数据库 Schema 参考

> 自动生成自 `server/data-talk-infrastructure/src/main/resources/db/migration/`
> 最后更新：2026-04-29

**版本历史**
- V8 (2026-04-19): dropped `messages` table — OpenCode is now authoritative for message persistence; DataTalk only stores `events` for SSE resume.
- V11 (2026-04-24): added artifact origin fields `origin_message_id` / `origin_part_id` and index `idx_artifacts_origin`.
- V12 (2026-04-27): added persistent stage tabs, payload storage, and FTS5 content index (`stage_tabs`, `stage_tab_payload`, `stage_tab_index`).
- V13 (2026-04-28): removed `stage_tabs.scope`, rebuilt FTS rowid mapping, and changed `origin_session_id` FK from `ON DELETE CASCADE` to `ON DELETE SET NULL`.
- V14 (2026-04-29): added physical file artifact index table `file_artifact` with application-managed `session_id` / `connection_id` references.

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
| origin_message_id | TEXT | | 产出该工件的聊天消息 ID |
| origin_part_id | TEXT | | 产出该工件的聊天 part ID |

索引：
- `idx_artifacts_session(session_id, created_at)`
- `idx_artifacts_origin(session_id, origin_message_id, origin_part_id)`

## file_artifact — 物理文件型产物

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| id | TEXT | PK | 文件产物 ID |
| scope | TEXT | NOT NULL, CHECK(session/workspace) | 归属范围：session 临时区或 workspace 资产库 |
| status | TEXT | NOT NULL, CHECK(temporary/candidate/archived/discarded) | 生命周期状态；严格 4 状态，无 Removed |
| kind | TEXT | NOT NULL, CHECK(report/er_diagram/sql_script/dataset/other) | 文件产物类型 |
| session_id | TEXT | | 来源会话 ID；不设 FK，archived 行可在会话删除后置 NULL |
| connection_id | TEXT | | 归属连接 ID；不设 FK，application 层管理引用 |
| filename | TEXT | NOT NULL | 文件名 |
| physical_path | TEXT | NOT NULL | 物理路径 |
| size_bytes | INTEGER | NOT NULL | 文件大小 |
| mime_type | TEXT | | MIME 类型 |
| title | TEXT | | 展示标题 |
| summary | TEXT | | 摘要 |
| created_at | INTEGER | NOT NULL | 创建时间（epoch millis） |
| updated_at | INTEGER | NOT NULL | 更新时间（epoch millis） |
| archived_at | INTEGER | | 归档时间（epoch millis） |
| metadata_json | TEXT | | frontmatter / 扩展元数据 JSON |

索引：
- `idx_file_artifact_session(session_id)`，部分索引条件 `WHERE scope = 'session'`
- `idx_file_artifact_connection(connection_id)`，部分索引条件 `WHERE scope = 'workspace'`
- `idx_file_artifact_status(status)`

说明：
- 与 `artifacts` payload 型工件表完全独立。
- `session_id` / `connection_id` 不设 FK；删除与解绑由 application 层显式管理。

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

## stage_tabs — 工作台 Tab 元数据

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| id | TEXT | PK | Tab ID |
| type | TEXT | NOT NULL | Tab 类型（如 `query_editor` / `file_preview`） |
| title | TEXT | NOT NULL | Tab 标题 |
| connection_id | TEXT | | 关联连接 ID |
| database_name | TEXT | | 关联数据库名 |
| schema_name | TEXT | | 关联 schema 名 |
| origin_session_id | TEXT | FK → sessions, NULLABLE, `ON DELETE SET NULL` | 来源会话软标签；会话删除后置空，Tab 保留 |
| payload_version | INTEGER | NOT NULL, DEFAULT 1 | 元数据/内容乐观锁版本 |
| pinned | INTEGER | NOT NULL, DEFAULT 0 | 是否置顶 |
| archived | INTEGER | NOT NULL, DEFAULT 0 | 是否归档 |
| archived_at | INTEGER | | 归档时间 |
| created_at | INTEGER | NOT NULL | 创建时间 |
| last_touched_at | INTEGER | NOT NULL | 最近触达时间 |

索引：
- `idx_stage_tabs_active(archived, last_touched_at DESC)`，部分索引条件 `WHERE archived = 0`
- `idx_stage_tabs_type(type, archived)`
- `idx_stage_tabs_origin(origin_session_id)`

说明：
- V13 起不再有 `scope` 列；持久化层所有 Tab 都是工作台级记录。
- `origin_session_id` 仅用于来源标记，不再决定生命周期。

## stage_tab_payload — Tab 内容快照

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| tab_id | TEXT | PK, FK → stage_tabs, `ON DELETE CASCADE` | 对应 Tab |
| payload_json | TEXT | NOT NULL | Tab payload JSON |
| content_text | TEXT | NOT NULL | 供搜索/AI 使用的纯文本内容 |
| content_version | INTEGER | NOT NULL | 内容版本 |
| updated_at | INTEGER | NOT NULL | 更新时间 |

## stage_tab_index — Tab 全文索引

FTS5 虚表，列：

- `title`
- `content`
- `type`（UNINDEXED）
- `archived`（UNINDEXED）

说明：
- 使用 `tokenize = 'trigram'`
- 通过 `rowid = stage_tabs.rowid` 与 `stage_tabs` 对齐
- V13 迁移会重建 rowid 映射，确保历史索引命中不漂移

同步触发器：
- `stage_tabs_ai`
- `stage_tabs_au`
- `stage_tabs_ad`
- `stage_tab_payload_aiu`
- `stage_tab_payload_au`
- `stage_tab_payload_ad`
