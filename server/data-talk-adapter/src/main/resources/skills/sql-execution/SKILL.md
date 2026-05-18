---
name: sql-execution
description: Use when the user asks to query data, read table metadata, run SELECT, or answer an analytical question against a SQL connection. Triggers on phrases like 查询/统计/分析/读表/取数/count/select/查一下/aggregate/group by/trend/top N/explore schema/describe table. Covers the READ-ONLY `datatalk_execute_sql` contract, `datatalk_read_schema` discovery vs describe modes, "table doesn't exist" probe path, and the analytical aggregation-first workflow.
---

# SQL Execution Skill

## When to use

- "Count orders by region in the last 30 days"
- "查询 users 表的近 7 天注册量趋势"
- "Read schema for tables matching `order_*`"
- "分析一下营收按品类的分布"
- "Top 10 customers by spend, return as a table"
- "Describe columns of the `invoices` table"

## When NOT to use

- Editing or running SQL inside an open `query_editor` tab — that is the query-editor workflow, not this skill.
- Writing data (INSERT/UPDATE/DELETE/DDL) — `datatalk_execute_sql` rejects it. For confirmed mutations use the SQL workbench.
- Switching session connection / database / schema — see `[[connection-management]]` for `datatalk_set_data_context` and `datatalk_resolve_use_target`.
- Charting from a result — once the table artifact is in hand, hand off to the charts skill.
- Ingesting external HTTP/CSV/JSON sources — that is `[[data-collection]]`.

## Tool surface

| MCP Tool | Required input | Returns |
|----------|---------------|---------|
| `datatalk_read_schema` | none if session has active connection; otherwise `connectionId` | Tables (discovery mode) or columns (describe mode); paging via `cursor`; may set `truncated=true` |
| `datatalk_execute_sql` | `sql` (optional `connectionId`, `database`, `schema`, `pageSize`) | Table artifact + preview rows; inherits session context when fields omitted |

## Read-only guarantee

`datatalk_execute_sql` is **READ-ONLY**. Only `SELECT` and `WITH` (CTE) queries are accepted. Any DDL (`CREATE`, `ALTER`, `DROP`, `TRUNCATE`) or DML (`INSERT`, `UPDATE`, `DELETE`, `MERGE`, `REPLACE`) attempt is rejected by the tool; do not try to bypass with stacked statements, vendor-specific procedural blocks, or session settings that mutate state. If the user asks for a mutation, route them to the SQL workbench instead of attempting it here.

`use xxx` is a context switch, not SQL — do not pass it to `datatalk_execute_sql`. See `[[connection-management]]`.

## Schema reading rules

`datatalk_read_schema` has two modes:

- **Discovery mode** (no `tables` argument): lists tables in the active or specified connection. For large schemas, **always** include a narrow `pattern` and a sensible `limit`. Use `cursor` to page when more rows exist than `limit`.
- **Describe mode** (explicit `tables` array): returns column metadata for those tables only. Never pass a large table list to describe mode — keep it scoped to the tables actually needed for the user's request.

If the response contains `truncated=true`, the **schema list itself was cut off** (not a large-output file). Recover by one of:

1. Narrow `pattern` with a business keyword the user mentioned (`order`, `customer`, `inv_2024`, etc.).
2. Lower `limit`, then paginate with the returned `cursor` if needed.
3. Ask the user to pick from the candidates you already have rather than guessing.

This `truncated` flag is the schema-result-truncation signal. The distinct "tool output was persisted to disk because it was too big to inline" semantics — i.e. when a response says output was truncated and gives a `saved file path` — belongs to `[[artifacts-output]]`; do not conflate the two.

## "Table doesn't exist" probe path

> Before writing any SQL on an unfamiliar schema (first-time table use, multi-table JOIN, keyword-based table discovery), go through `[[exploring-data]]` first — it defines the Pre-Action Exploration Protocol with ordering, budget, and escalation. The probe below is the fallback *after* a SQL has already failed with table-not-found.

When `datatalk_execute_sql` (or `datatalk_explain_query` / `datatalk_index_hints` / query-editor `run_sql`) returns "table doesn't exist" / "relation does not exist" / "Unknown table", **stop retrying the same SQL** and run this entry probe:

1. `datatalk_schema_search(keyword=<missing-name>)` for Chinese / English / pinyin / synonym candidates; if empty, fall back to `datatalk_read_schema(pattern=<missing-name>)` against the active connection.
2. If empty, branch by dialect:
   - **MySQL / MariaDB** — one cross-database probe:
     ```sql
     SELECT TABLE_SCHEMA, TABLE_NAME
     FROM INFORMATION_SCHEMA.TABLES
     WHERE TABLE_NAME='<missing>' OR TABLE_NAME LIKE '%<missing>%'
     LIMIT 50
     ```
     `INFORMATION_SCHEMA` is the standard cross-database metadata catalog; in MySQL it can see all databases the user has access to.
   - **PostgreSQL** — search `information_schema.tables` within the current database. PG cannot cross databases via SQL, so if the result is still empty, call `datatalk_list_connection_targets` and ask the user which database to switch to (`[[connection-management]]`); do not probe each database one by one.
   - **Other dialects** — fall back to `datatalk_read_schema` with broader patterns; if still empty, call `datatalk_list_connection_targets`.
3. Interpret results:
   - **One hit** — propose `datatalk_set_data_context` (`[[connection-management]]`) for the target database/schema, then re-run the original SQL.
   - **Multiple hits** — present candidates and ask the user to choose. Do not silently pick one.
   - **Zero hits** — say the table is not visible in this connection. **Do not** claim the database is empty.

Full diagnostics for other SQL failure modes (syntax errors, deeper object-resolution chains, multi-candidate disambiguation beyond the entry probe) live in `[[sql-error-diagnostics]]`.

## Analytical query workflow

For analytical questions (grouped counts, time-bucketed metrics, comparisons, trends, top-N, chart inputs, report aggregates):

1. If schema is needed, follow the Schema reading rules above.
2. Call `datatalk_execute_sql` with a **bounded `pageSize`** and an **aggregated SQL** — `GROUP BY`, `SUM`, `COUNT`, `AVG`, window functions — so the result is the smallest table that answers the question. Do not fetch broad raw rows unless the user explicitly asked for raw data.
3. Use the returned table artifact / preview rows to answer the question, write the summary, or feed a chart.
4. On candidate-ambiguity errors ("matches multiple candidates", "Select a database/schema first"), follow the entry probe above and `[[connection-management]]` — never claim the database has no data.
5. Zero-row analytical results are legitimate answers: report them with framing (e.g. "no orders in the last 30 days, so no trend can be computed"), not as failures.

## Recommended workflow (short form)

1. Confirm session context is set (or accept inheritance) — switch via `[[connection-management]]` if not.
2. `datatalk_read_schema` — discovery first, describe only when columns are needed; handle `truncated=true` per the rules above.
3. `datatalk_execute_sql` — aggregated, bounded `pageSize`, READ-ONLY.
4. On errors, run the "Table doesn't exist" probe path or defer to `[[sql-error-diagnostics]]`.
5. Hand the table artifact off to the next skill (chart, dashboard, report) when the user asks.
