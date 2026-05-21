# StarRocks Driver Decision

Date: 2026-05-08
Decision: Use native StarRocks JDBC driver (`com.starrocks:starrocks-connector-j:1.1.1`)

## Driver Details

| Property | Value |
|---|---|
| Group ID | `com.starrocks` |
| Artifact ID | `starrocks-connector-j` |
| Version | `1.1.1` |
| License | Apache 2.0 |
| Driver class | `com.starrocks.jdbc.StarRocksDriver` |
| URL prefix | `jdbc:starrocks://` |
| Protocol | MySQL-compatible (Type 4 JDBC) |
| Default port | 9030 |

## URL Shape

```
jdbc:starrocks://<fe_host>:<fe_query_port>/<catalog>.<database>
```

Example: `jdbc:starrocks://host:9030/default_catalog.analytics`

## Catalog/Database Day-1 Model

StarRocks has a two-level namespace: `catalog.database`. The existing `ConnectionRecord` has no catalog field.

**Day-1 decision: hardcode catalog to `default_catalog`.**

- `databaseName` stores the StarRocks database name only
- URL builder composes `default_catalog.<databaseName>`
- No new persistence fields or migrations needed
- Target discovery lists databases within `default_catalog`
- Schema selector hidden (no independent schema level)
- Day-2: add explicit catalog field with migration

## Timeout Behavior

StarRocks JDBC driver supports MySQL-style timeout parameters:
- `connectTimeout` (milliseconds)
- `socketTimeout` (milliseconds)

## Reuse Map

| Component | Decision | Reason |
|---|---|---|
| JdbcUrlBuilder | New `jdbc:starrocks://` case with catalog.database composition | Native driver has its own URL scheme |
| ConnectionService timeout | MySQL-style params (connectTimeout + socketTimeout ms) | Driver is MySQL-protocol compatible |
| SqlExecuteService context | Route to `setCatalog()` path (same as MySQL/MariaDB/Doris) | StarRocks uses catalog for database context |
| DefaultSqlStatementSplitters | Route to generic splitter | StarRocks procedural SQL behavior unverified; day-1 simple scripts only |
| CalciteSqlRiskAnalyzer | Add StarRocks-specific classification | StarRocks has unique catalog/load/cluster management commands |
| DiagnosticsProvider | Structured unsupported (day-1) | EXPLAIN output unverified |
| ER | Structured unsupported (day-1) | Dialect enum has no starrocks → auto-reject |
