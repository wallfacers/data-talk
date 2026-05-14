---
name: connection-management
description: Use when the user manages saved data source connections or switches the session data context (创建连接 / 测试连接 / 编辑连接 / 选择连接 / 切换数据库 / 切换 schema / use xxx / 数据上下文 / connection / data context / connectionId / confirmable mutation / two-phase confirm / terminate session / optimize table). Owns the 6 session-data-context tools, the 3 connection-lifecycle tools, the confirmable mutation two-phase protocol (`confirm=true` + `confirmationToken`), plus `datatalk_terminate_session` and `datatalk_optimize_table`.
---

# Connection Management Skill

## When to use

- "创建一个 MySQL 连接 / 新建 PostgreSQL 数据源"
- "测试这个连接能不能连上"
- "改一下这个连接的密码 / 端口 / 名字"
- "切换到 xxx 数据库" / "use sales_db"
- "我现在连的是哪个库？" / "当前 data context 是什么"
- "选一下连接 / 帮我选默认数据源"
- "杀掉这个数据库 session" / "终止阻塞的查询会话"
- "重整这张表 / 回收表空间 / OPTIMIZE TABLE / VACUUM FULL"

## When NOT to use

- Database dialect specifics (kind=mysql/postgresql/oracle/... DDL/DML risk-level details) — see `[[database-dialects]]`
- `datatalk_lock_info` and other diagnostic tool definitions — see `[[sql-error-diagnostics]]` (this skill only covers how diagnostics hand off into confirmable mutations)
- Per-tab `set_context` in the query editor (different from session-level `set_data_context`) — see `[[query-editor-workflow]]`
- Reading schema / executing SQL on an already-selected connection — see `[[sql-execution]]`

## Session data context

The session data context is a triple `{connectionId, database?, schema?}` describing where the next SQL or schema operation runs. Six tools manage it.

| Tool | Purpose | Required input |
|------|---------|----------------|
| `datatalk_get_data_context` | Read the current session data context | none |
| `datatalk_set_data_context` | Update the current session connection, database, and schema | `connectionId` + `selectedLevel`; plus `database` when `selectedLevel=database`; plus `schema` when `selectedLevel=schema` |
| `datatalk_resolve_use_target` | Resolve a raw `use xxx` target in the current session; returns `matched` / `ambiguous` / `not_found` | `target` |
| `datatalk_list_connection_targets` | List valid databases and schemas for a connection | none when session already has an active connection; else `connectionId` |
| `datatalk_list_connections` | List saved data source connections | none |
| `datatalk_select_connection` | Select a saved connection as the current session connection | `connectionId` |

Rules:

- Never call `datatalk_set_data_context` with only `database` or only `schema` — that is an incomplete context switch. Always include `connectionId` + `selectedLevel`.
- When a user says `use xxx`, always go through `datatalk_resolve_use_target` first; do not guess the matched target.
- On `ambiguous`, present candidates to the user. On `not_found`, surface the message / suggestions or fall back to `datatalk_list_connection_targets` / `datatalk_list_connections`.

## Connection lifecycle tools

- `datatalk_create_connection` — Create a saved connection. Only call when the user explicitly asks to add one.
  Required input: always `name` and `kind`. For `kind=sqlite`, include `databaseName` (file path or `:memory:`). For `kind=duckdb`, include `databaseName` (file path or `:memory:`) and `readOnly` (boolean). For other kinds, also include `host`, `port`, `username`, and `password`. Optional: `connectTimeout`.

- `datatalk_test_connection` — Test whether a saved connection is reachable.
  Required input: `connectionId`.

- `datatalk_update_connection_confirmable` — Two-phase update (see below). Preview first; execute only after the user explicitly agrees.
  Required input: always `connectionId`, `name`, and `kind`. For `kind=sqlite`, include `databaseName` (file path or `:memory:`). For `kind=duckdb`, include `databaseName` and `readOnly`. For other kinds, also include `host`, `port`, and `username`. Optional: `password`, `connectTimeout`, `confirm`, `confirmationToken`; when `confirm=true`, `confirmationToken` is required.

Engine-specific notes (general semantics only; full dialect rules in `[[database-dialects]]`):

- **SQLite** is file-scoped. `databaseName` is the SQLite file path or `:memory:`. SQLite has no independent schema selector — do not ask to switch SQLite schemas. `:memory:` is ephemeral per JDBC connection in the current backend model, so treat it as a temporary test target rather than a durable working database.
- **DuckDB** is an embedded analytical database. `databaseName` is the file path or `:memory:`. `readOnly` is a boolean (defaults to false). DuckDB has no host/port/username/password. External file operations (`COPY`, `EXPORT`, `IMPORT`), extension commands (`INSTALL`, `LOAD`), and external file/network access functions (`read_csv`, `read_parquet`, `httpfs`) are not supported. DuckDB schema selector shows (normally `main`); database selector is hidden.
- **ClickHouse** is an analytical column-store database accessed over HTTP (default port 8123). Use `host`, `port`, `username`, `password`, `databaseName` as for other network databases. File and network access functions (`file`, `s3`, `url`, `remote`, `hdfs`, `odbc`, `jdbc`, `mysql`, `postgresql`) and cluster operations (`SYSTEM`, `KILL QUERY`, `OPTIMIZE`, `ATTACH`, `DETACH`) are not supported. Mutations are async and require the SQL workbench with confirmation. ER diagrams, index hints, and diagnostics are day-1 unsupported.

## Confirmable mutation protocol

Confirmable mutation tools are **two-phase**. Both phases must complete to apply the change.

1. **Preview phase** — call with `confirm=false` (or omit `confirm`). The server returns a preview payload including `confirm_required: true` and a fresh `confirmation_token`. No mutation is applied. Present the preview to the user and obtain explicit agreement.
2. **Execute phase** — call again with `confirm=true` and `confirmationToken` copied **exactly** from the preview. The server validates the token, applies the mutation, and returns the result payload.

Hard rules:

- Never call the execute phase without first showing the preview to the user.
- `confirmationToken` is single-use and tied to the preview payload — do not reuse, do not hand-craft, do not omit when `confirm=true`.
- If the user changes any parameter between preview and execute, re-run the preview to get a new token.

The protocol applies to the following tools:

- `datatalk_update_connection_confirmable` (this skill)
- `datatalk_terminate_session` (this skill)
- `datatalk_optimize_table` (this skill)

## datatalk_terminate_session

Kill a database session.

- **Preview**: `{ "sessionId": "12345", "confirm": false }` → `{ confirm_required, confirmation_token, preview: { engine, sessionId, willRunSql, currentSql } }`.
- **Execute**: `{ "sessionId": "12345", "confirm": true, "confirmationToken": "..." }` → `{ ok, sessionId, message }`.
- **Call when**: a lock / blocking-holder diagnostic (see `[[sql-error-diagnostics]]`) identifies the offending session and the user agrees to terminate it.
- **Do not call when**: the user has not confirmed. Always present the preview first.

## datatalk_optimize_table

Reclaim table space (`OPTIMIZE TABLE` / `VACUUM FULL` by engine; dialect specifics in `[[database-dialects]]`).

- **Preview**: `{ "table": "users", "schemaName": null, "confirm": false }` → preview with `willRunSql`.
- **Execute**: second call with `confirm=true` and `confirmationToken` → `{ ok, table, schemaName, durationMs, reclaimedBytes, message }`.
- **Call when**: a table-space diagnostic (see `[[sql-error-diagnostics]]`) shows significant reclaimable space and the user agrees.
- **Do not call when**: on a production system during peak hours without explicit user acknowledgment of locking impact.

## Recommended workflow

### Switch connection, database, or schema (handles `use xxx`)

1. Call `datatalk_resolve_use_target` with the raw user phrase as `target`.
2. If the result is `matched`, call `datatalk_set_data_context` and copy the full `matched_target` fields: `connectionId=<matched_target.connectionId>`, `database=<matched_target.database>` when non-null, `schema=<matched_target.schema>` when non-null, and `selectedLevel=<matched_target.level>`.
3. If the result is `ambiguous`, present the candidates to the user; do not guess.
4. If the result is `not_found`, explain the message or suggestions; if needed, call `datatalk_list_connection_targets` or `datatalk_list_connections` to help the user choose.

### No active connection

1. If the request is not database-related, answer without a data source.
2. For a database-related request or explicit `!` SQL, call `datatalk_get_data_context` only when you need to confirm whether a usable session data context exists.
3. If no usable data source is selected, call `datatalk_list_connections` to see saved connections or suggest options.
4. Use `datatalk_ui_exec` with `object=workspace`, `action=choose_connection` only when the user needs to pick a data source interactively.
