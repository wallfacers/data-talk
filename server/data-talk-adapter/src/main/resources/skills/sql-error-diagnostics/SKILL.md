---
name: sql-error-diagnostics
description: Use when a SQL run fails or a performance question arrives. Triggers on SQL 报错 / 表不存在 / 列不存在 / 多候选 / 性能分析 / EXPLAIN / 索引建议 / 锁等待 / 慢查询 / 连接池 / 表空间 / SQL execution failed / table not found / Unknown column / ambiguous candidates / Select a database first / slow query / index hint / lock blocking / pool full / disk usage. Owns the three SQL-failure diagnostic classes (syntax, object-not-found incl. cross-DB probe, ambiguous candidates) and the five diagnostic tools `datatalk_explain_query` / `datatalk_index_hints` / `datatalk_lock_info` / `datatalk_pool_status` / `datatalk_table_space`.
---

# SQL Error Diagnostics Skill

## When to use

- `datatalk_execute_sql`, `datatalk_explain_query`, `datatalk_index_hints`, or query-editor `run_sql` returns an error.
- User reports symptoms such as "SQL 报错", "表不存在", "找不到列", "Select a database first", "matches multiple candidates", "为什么这么慢", "show execution plan", "blocked", "hung", "连接池满了", "表占多大".
- A previous step received structured `unsupported: true` from a diagnostic tool and you need to explain why.

## When NOT to use

- The user only asks about SQL correctness or style without running it — answer directly.
- The user has not run any SQL and is asking general product/help questions.
- The user is browsing rows in the query editor and the editor itself surfaces the empty state — let the editor render emptiness; do not diagnose.
- Truncated tool output with a saved file path that is not an error — see `[[artifacts-output]]`.
- Schema read returned `truncated=true` (large schema page, not a failure) — see `[[sql-execution]]`.

## Three diagnostic classes

When SQL execution fails, classify the error before retrying. Never chain further diagnostics on the same SQL until the class is known and addressed.

### Syntax errors

Signals: parser errors, "syntax error near …", "Unknown column 'x' in 'field list'", reserved-word collisions.

Workflow:

1. Read the failing SQL and the error message verbatim. Identify the exact token or column the engine complained about.
2. If a column name is the problem, call `datatalk_read_schema` with explicit `tables` for the table(s) referenced by the SQL to confirm the real column set. Do not pass a wide schema sweep.
3. Propose a corrected SQL to the user (or to `apply_text_edits` in the editor — see `[[ui-contract]]`). Do not silently re-run the original.
4. Never invent columns from the table name. Base the fix on returned schema only.

### Object not found

Signals: "table doesn't exist", "relation does not exist", "Unknown table 'x'", "no such table".

Workflow (own this branch end-to-end):

1. **Stop retrying with the same context.** Do not chain further diagnostics on the failing SQL.
2. **Locate the table via schema read.** Call `datatalk_read_schema` with `pattern=<missing>` and a narrow `limit`. This is the canonical first probe.
3. **Cross-DB probe (MySQL / MariaDB only).** If step 2 returns empty and the dialect is MySQL or MariaDB, run **exactly one** probe across all schemas:
   ```sql
   SELECT TABLE_SCHEMA, TABLE_NAME
   FROM INFORMATION_SCHEMA.TABLES
   WHERE TABLE_NAME = '<missing>'
      OR TABLE_NAME LIKE '%<missing>%'
   LIMIT 50
   ```
   Run it once. Do not loop per database.
4. **PostgreSQL has no cross-database SQL.** Search `information_schema.tables` within the current database only. If still empty, call `datatalk_list_connection_targets` and ask the user which database to switch to — do not probe each database one by one.
5. **Resolve the outcome:**
   - **One hit** → propose a context switch via `[[connection-management]]` (`set_data_context`) and re-run the original SQL after confirmation.
   - **Multiple hits** → present the candidate `(schema, table)` pairs to the user and ask which one they meant. Do not pick silently.
   - **Zero hits** → tell the user the table is not visible in this connection. Do **not** claim "the database is empty" or "the table has no data".
6. Other engines (SQLite / DuckDB / H2 / ClickHouse / SQL Server, etc.) follow step 2 only; if empty, fall back to step 4's `datatalk_list_connection_targets` flow.

### Ambiguous candidates

Signals: "matches multiple candidates", "Select a database/schema first", `datatalk_resolve_use_target` returning `ambiguous` or `not_found`.

Workflow:

1. Do **not** say "the database has no data". The error is a context-resolution failure, not an emptiness signal.
2. Call `datatalk_list_connection_targets` (no `connectionId` if a session connection is already active) or `datatalk_resolve_use_target` to enumerate the candidates.
3. Present the candidates to the user and ask which database / schema to use. Never guess.
4. After user picks, route the context switch through `[[connection-management]]` (`set_data_context`), then re-run.

## Diagnostic tool surface

| MCP Tool                  | Purpose                                              | Engine support                                                                 |
|---------------------------|------------------------------------------------------|--------------------------------------------------------------------------------|
| `datatalk_explain_query`  | Normalized execution plan tree                       | All 14 first-class kinds (Oracle structured-unsupported)                       |
| `datatalk_index_hints`    | B-tree index recommendations from EXPLAIN            | Row-store kinds only (mysql/postgresql/h2/sqlite/sqlserver/mariadb/tidb)       |
| `datatalk_lock_info`      | Current blocking chain (holder/waiter, wait ms, SQL) | mysql/postgresql, partial h2; others structured-unsupported                    |
| `datatalk_pool_status`    | Server-side connection statistics                    | mysql/postgresql, partial h2; others structured-unsupported                    |
| `datatalk_table_space`    | Table storage stats and reclaimable bytes            | mysql/postgresql, partial h2; others structured-unsupported                    |

## datatalk_explain_query

- **Purpose**: Get a normalized execution plan tree for a SQL statement.
- **Input**: `{ "sql": "<sql>" }`. Connection context is taken from the current session.
- **Output**: `{ dialect, rawText, nodes, warnings, unsupported }`.
- **Call when**: user asks "why is this slow", "show execution plan", or any query performance question.
- **Do not call when**: the user only asks about SQL correctness, not performance.
- EXPLAIN never executes `user_sql`. Per engine: `SET SHOWPLAN_XML ON` for sqlserver, `EXPLAIN PLAN` for clickhouse, `TYPE LOGICAL` for presto/trino, plain `EXPLAIN` for the rest.

## datatalk_index_hints

- **Purpose**: Get index recommendations by internally running EXPLAIN and analyzing the result.
- **Input**: `{ "sql": "<sql>" }`.
- **Output**: `{ recommendations: [...], explainSummary, unsupported }`.
- **Call when**: user asks for index advice, or `datatalk_explain_query` reveals `FULL_SCAN` nodes.
- **Do not call when**: the table has fewer than ~1000 rows (full scan is typically acceptable).
- Column-store / federated / data-warehouse engines return structured `unsupported` with kind-specific guidance (partitioning, sort key, connector pushdown). Never recommend B-tree indexes for those kinds.

## datatalk_lock_info

- **Purpose**: Get the current blocking chain — holder/waiter pairs, lock types, wait duration, current SQL.
- **Input**: `{}` (uses session connection context).
- **Output**: `{ blockingChain, recommendations }` or `{ unsupported: true, reason }`.
- **Call when**: user says "query is stuck", "blocked", "hung", "who is locking", or any wait/timeout complaint.
- **Do not call when**: the user only asks about query performance — use `datatalk_explain_query` instead.
- If the blocking chain identifies a holder and the user agrees to kill it, route session termination through `[[connection-management]]` (`datatalk_terminate_session` is a confirmable mutation owned there).

## datatalk_pool_status

- **Purpose**: Get server-side connection statistics — active, idle, max connections, running threads.
- **Input**: `{}`.
- **Output**: `{ scope, activeConnections, idleConnections, maxConnections, threadsRunning, waitingConnections, identifier, recommendations }` or `{ unsupported: true, reason }`.
- **Call when**: user asks "how many connections", "connection pool full", "too many sessions", or server capacity questions.
- **Do not call when**: the user asks about their own DataTalk client-side connection settings.

## datatalk_table_space

- **Purpose**: Get table storage statistics — row count, data size, index size, reclaimable space.
- **Input**: `{ "tables": ["t1", "t2"] }` (optional; defaults to all user tables, capped at 200).
- **Output**: `{ tables, recommendations }` or `{ unsupported: true, reason }`.
- **Call when**: user asks "table size", "disk usage", "space", "how big is X", or storage-related questions.
- **Do not call when**: the user only asks about row counts — use a `SELECT COUNT` query instead.
- If significant reclaimable space is reported and the user agrees to compact, route through `[[connection-management]]` for the confirmable optimize-table mutation.

## Diagnostics workflow rules

1. Performance question received → call `datatalk_explain_query` first.
2. Plan contains `FULL_SCAN` nodes or non-empty `warnings` → call `datatalk_index_hints`.
3. Present `explainSummary` + recommendation `rationale` values as natural language to the user.
4. Do not infer index recommendations from schema alone — always base them on actual EXPLAIN output.
5. Do not run `datatalk_read_schema` before `datatalk_explain_query` to pre-load context.
6. Index recommendations are suggestions only. If the user confirms they want to create an index, generate the `CREATE INDEX` SQL and route it through the standard Guarded DDL flow.
7. Lock complaint received → call `datatalk_lock_info`.
8. `datatalk_lock_info` returns a blocking chain with `waitMillis > 5000` and recommends terminating the holder → tell the user "Holder session has been blocking", present holder details, ask confirmation, then start the terminate-session preview via `[[connection-management]]`.
9. User confirms terminate → complete the confirm phase through `[[connection-management]]`.
10. Storage / space question → call `datatalk_table_space`.
11. `datatalk_table_space` shows > 30% reclaimable space and recommends optimizing → warn about table locking; if the user agrees, start the optimize-table preview via `[[connection-management]]`.
12. Capacity / connection-count question → call `datatalk_pool_status`.
13. If a diagnostic tool returns `{ unsupported: true }`, inform the user the capability is not available for their engine and explain the reason.

## Diagnostics capability matrix

Post Day-2 support state:

- **EXPLAIN** supports all 14 first-class kinds: `mysql`, `postgresql`, `h2`, `sqlite`, `sqlserver`, `mariadb`, `tidb`, `duckdb`, `clickhouse`, `apache_doris`, `starrocks`, `presto`, `trino`, `hive`. EXPLAIN never executes `user_sql`. Oracle remains structured-unsupported.
- **INDEX_HINTS** (B-tree recommendations) supports row-store kinds only: `mysql` / `postgresql` / `h2` / `sqlite` / `sqlserver` / `mariadb` / `tidb`. Column-store / federated / data-warehouse kinds (`duckdb` / `clickhouse` / `apache_doris` / `starrocks` / `presto` / `trino` / `hive`) return structured `Unsupported` with kind-specific guidance pointing to partitioning / sort key / connector pushdown alternatives — never recommend B-tree indexes for those kinds.
- **LOCK_INFO / POOL_STATUS / TABLE_SPACE / TERMINATE_SESSION / OPTIMIZE_TABLE** are unchanged — supported on `mysql` / `postgresql`, partial on `h2`, structured-unsupported on the other 12 kinds (Day-3 scope).

## EXPLAIN warnings

- **Trino / Presto**: Logical plan shows `TableScan` as `FULL_SCAN`. The underlying connector (Hive / Iceberg / MySQL) may push down filters — verify on the source system's EXPLAIN.
- **Hive**: Partition pruning is not detected in EXPLAIN output. Verify predicates include partition columns manually; use `EXPLAIN EXTENDED` for details.
- **DuckDB**: Zone map hits are shown in EXPLAIN — if row scans dominate, check column statistics before recommending indexes.

## INDEX_HINTS impact tier

Recommendations include an impact level based on estimated rows from EXPLAIN:

- `HIGH`: rows > 1000 (large table, significant improvement).
- `MEDIUM`: rows > 100 (medium table, noticeable benefit).
- `LOW`: rows <= 100 (small table, marginal benefit).

SQLite has no row estimates; defaults to `MEDIUM`.
