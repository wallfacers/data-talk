---
name: database-dialects
description: Use when the user mentions a specific database product, asks about supported data source types, picks a connection kind, or asks why a feature (ER / diagnostics / mutation) is unavailable on a given dialect. Triggers on MySQL / PostgreSQL / Oracle / SQL Server / SQLite / DuckDB / ClickHouse / TiDB / MariaDB / OceanBase / StarRocks / Apache Doris / Trino / Presto / Dameng / Apache Hive / GaussDB / 方言 / dialect / 数据库类型 / 数据源类型 / 连接类型. Covers per-dialect connection kind, default port, JDBC driver, schema visibility, SQL splitter family, risk-tier highlights, and ER / diagnostics support status.
---

# Database Dialects Skill

## When to use

- The user asks "DataTalk supports which databases?", "支持哪些数据库?", or "can I connect to <product>?"
- The user picks or names a connection kind (`mysql`, `tidb`, `clickhouse`, `oceanbase`, `gaussdb`, ...) and you need its port / driver / schema model
- A tool returns `dialect_unsupported` and you need to explain to the user which features are off on that dialect
- The user is composing SQL and asks about per-dialect risk classification (`L1` / `L2` / `L3`) or splitter behavior (DELIMITER, GO, dollar-quoting)
- The user types Chinese / English aliases ("达梦", "Dameng", "PingCAP TiDB", "蚂蚁 OceanBase") and you need the canonical kind

## When NOT to use

- Generic SELECT execution or aggregation workflow — use `[[sql-execution]]`
- Connection creation, switching, `use xxx`, `confirm=true` / `confirmationToken` mutation protocol — use `[[connection-management]]`
- SQL execution errors, `table doesn't exist` probe path, multi-candidate disambiguation — use `[[sql-error-diagnostics]]`
- ER Inspector / Designer protocol details — use `[[er-tabs]]`
- Choosing a chart type for query results — use `[[charts-and-dashboards]]`

## Common dialects matrix

| Dialect | Kind | Default port | Driver | ER (Inspector / Designer) | Diagnostics (Day-1) |
|---|---|---|---|---|---|
| MySQL (baseline) | `mysql` | 3306 | MySQL Connector/J | supported / supported | supported (EXPLAIN, lock, pool, space) |
| PostgreSQL (baseline) | `postgres` | 5432 | PostgreSQL JDBC | supported / supported | supported |
| H2 (baseline) | `h2` | embedded | H2 JDBC | dialect_unsupported / dialect_unsupported | dialect_unsupported |
| SQLite (baseline) | `sqlite` | embedded | SQLite JDBC | dialect_unsupported / dialect_unsupported | dialect_unsupported |
| MariaDB | `mariadb` | 3306 | MariaDB Connector/J | supported / supported | supported (reuses MySQL EXPLAIN) |
| TiDB | `tidb` | 4000 | MySQL Connector/J (wire-compat) | dialect_unsupported / dialect_unsupported | dialect_unsupported |
| Oracle | `oracle` | 1521 | Oracle JDBC | (Inspector dialect-supported) / dialect_unsupported | structured plan unsupported |
| SQL Server | `sqlserver` | 1433 | Microsoft JDBC | (Inspector dialect-supported) / dialect_unsupported | structured plan unsupported |
| DuckDB | `duckdb` | embedded | DuckDB JDBC | dialect_unsupported / dialect_unsupported | dialect_unsupported |
| ClickHouse | `clickhouse` | 8123 (HTTP) | ClickHouse JDBC | dialect_unsupported / dialect_unsupported | dialect_unsupported |
| Apache Doris | `apache_doris` | 9030 | MySQL Connector/J | dialect_unsupported / dialect_unsupported | dialect_unsupported |
| OceanBase (MySQL-mode) | `oceanbase` | 2881 | `com.oceanbase:oceanbase-client` | dialect_unsupported / dialect_unsupported | dialect_unsupported (all 9 hooks) |
| StarRocks | `starrocks` | 9030 | StarRocks JDBC | dialect_unsupported / dialect_unsupported | dialect_unsupported |
| Trino | `trino` | 8080 | Trino JDBC | dialect_unsupported / dialect_unsupported | dialect_unsupported |
| Presto | `presto` | 8080 | Presto JDBC | dialect_unsupported / dialect_unsupported | dialect_unsupported |
| Dameng | `dameng` | 5236 | `com.dameng:DmJdbcDriverX:8.1.x` | dialect_unsupported / dialect_unsupported | dialect_unsupported (all 7 hooks) |
| Apache Hive | `hive` | 10000 (binary) | Hive JDBC | dialect_unsupported / dialect_unsupported | dialect_unsupported |
| GaussDB | `gaussdb` | 8000 | `com.huaweicloud:gaussdbjdbc:v2.0-8.218.0` | dialect_unsupported / dialect_unsupported | dialect_unsupported |

> **MySQL & PostgreSQL baseline** (no dedicated `##` section below): MySQL uses `kind=mysql`, default port 3306, schema selector hidden (database-only). PostgreSQL uses `kind=postgres`, default port 5432, both database and schema selectors visible. Both are the reference dialects against which compatibility-mode kinds (MariaDB, TiDB, Doris, OceanBase, StarRocks, GaussDB) are described. H2 / SQLite are embedded baselines used mainly in tests; their ER and diagnostics support is intentionally minimal.

## MariaDB

- Connection kind: `mariadb`. MySQL-compatible; uses MariaDB Connector/J driver.
- Default port: 3306. Same fields as MySQL (host, port, database, username, password).
- Metadata and SQL execution follow the same paths as MySQL.
- Diagnostics reuse MySQL EXPLAIN.
- ER Designer: supported. Reuses MySQL DDL generation. `kind=mariadb` connections bind to `mariadb` dialect designers; `kind=mysql` connections also bind to `mariadb` designers. ER details — see `[[er-tabs]]`.
- Schema visibility: database selector visible, schema hidden (same as MySQL).
- Chat-path execution: SELECT / INSERT / UPDATE / DDL all run via `datatalk_execute_sql`; only DELETE triggers the in-chat `confirmationId` flow — see `[[sql-execution]]`.

## TiDB

- Canonical kind: `tidb`. Reject any user attempt to map TiDB to `mysql`.
- Default port: 4000.
- Protocol: MySQL 5.7 / 8.0 wire-compatible. SQL splitting, formatting, and most DML/DDL behave like MySQL. The TiDB-only L3 statements listed below have cluster-wide impact and should be surfaced to the user before running them via `datatalk_execute_sql`.
- TiDB-only L3 (must surface confirmation): `ADMIN CANCEL/PAUSE/RESUME DDL JOBS`, `BACKUP DATABASE`, `RESTORE DATABASE`, `IMPORT INTO`, `LOAD DATA INFILE`, `FLASHBACK CLUSTER/DATABASE/TABLE`, `ALTER/CREATE/DROP PLACEMENT POLICY`, `KILL TIDB`, `SET GLOBAL`, `BATCH ON ... INSERT/UPDATE/DELETE`.
- TiDB-only L2: `SPLIT TABLE ... BETWEEN ... AND ...`, `RECOVER TABLE`, `ANALYZE TABLE`, `ALTER TABLE ... COMPACT`, `ADMIN CHECK TABLE/INDEX`.
- TiDB-only L1 read-only introspection (safe to suggest to the user): `SHOW PLACEMENT`, `SHOW PLACEMENT FOR ...`, `SHOW PLACEMENT LABELS`, `SHOW TABLE <t> REGIONS`, `SHOW SPLIT REGIONS`, `SHOW STATS_HEALTHY`, `SHOW STATS_HISTOGRAMS`, `SHOW STATS_META`, `SHOW STATS_BUCKETS`, `ADMIN SHOW DDL`, `ADMIN SHOW DDL JOBS`. Use these when the user asks about cluster topology, region distribution, statistics health, or DDL job state.
- Diagnostics (lock / pool / table_space / EXPLAIN-real / index-hints / terminate / optimize) are `dialect_unsupported` on TiDB Day-1. Suggest the user run `EXPLAIN ANALYZE` or the L1 introspection statements above manually in the Query Editor when execution plans, region distribution, or statistics are needed.
- ER Inspector and Designer are `dialect_unsupported` on TiDB Day-1 — see `[[er-tabs]]`.
- User may type "TiDB", "tidb", "PingCAP TiDB". Map to canonical `tidb` only. Do not invent aliases.
- Chat-path execution: SELECT / INSERT / UPDATE / DDL all run via `datatalk_execute_sql`; only DELETE triggers the in-chat `confirmationId` flow. For TiDB-only L3 statements, explicitly preview the SQL to the user before running — see `[[sql-execution]]`.

## Oracle

- Connection kind: `oracle`. Has `oracleServiceType` field: `"service"` (default) or `"sid"`.
- Default port: 1521. Fields: host, port, database (service name or SID), username, password.
- Schema context uses Oracle owner / schema. No independent catalog — service name is set at connection level.
- PL/SQL blocks risk: anonymous blocks (`BEGIN...END`) are high-risk. `EXECUTE IMMEDIATE` and dynamic SQL are flagged.
- ER Designer: unsupported. Use query_editor + read_schema instead — see `[[er-tabs]]`.
- Diagnostics: structured execution plan unsupported. Use raw `EXPLAIN PLAN FOR` + `DBMS_XPLAN` — error-side reasoning lives in `[[sql-error-diagnostics]]`.
- Schema visibility: schema / owner visible, no independent catalog selector.
- Chat-path execution: SELECT / INSERT / UPDATE / DDL all run via `datatalk_execute_sql`; only DELETE triggers the in-chat `confirmationId` flow — see `[[sql-execution]]`.

## SQL Server

- Connection kind: `sqlserver` (alias `mssql` is normalized to `sqlserver`).
- Default port: 1433. Fields: host, port, database, username, password.
- Extra connection options: `sqlserverEncrypt` (default true), `sqlserverTrustServerCertificate` (default true), `sqlserverInstanceName` (optional).
- Schema context uses `databaseName` + schema (similar to PostgreSQL).
- Risk keywords: `EXEC`, `EXECUTE`, `BACKUP`, `DBCC`, `KILL`, `SHUTDOWN` are flagged.
- `GO` batch splitter is not in Day-1 scope; multi-statement batches use semicolons.
- ER Designer: unsupported. Use query_editor + read_schema instead — see `[[er-tabs]]`.
- Diagnostics: structured execution plan unsupported. Use `SET SHOWPLAN_TEXT ON` or SSMS — error-side reasoning lives in `[[sql-error-diagnostics]]`.
- Schema visibility: both database and schema visible (like PostgreSQL).
- Chat-path execution: SELECT / INSERT / UPDATE / DDL all run via `datatalk_execute_sql`; only DELETE triggers the in-chat `confirmationId` flow — see `[[sql-execution]]`.

## DuckDB

- Connection kind: `duckdb`. Embedded analytical SQL engine — no host / port.
- Fields: `databaseName` (file path or `:memory:`), `readOnly` (boolean, defaults to false). No host, port, username, or password.
- Chat-path execution: SELECT / INSERT / UPDATE / DDL all run via `datatalk_execute_sql`; only DELETE triggers the in-chat `confirmationId` flow — see `[[sql-execution]]`.
- File operations (`COPY`, `EXPORT DATABASE`, `IMPORT DATABASE`), extension commands (`INSTALL`, `LOAD`, `CREATE SECRET`), and external file / network access (`read_csv`, `read_parquet`, `read_json`, `glob`, `httpfs`, S3 paths) are not supported by the DuckDB embedded engine in DataTalk's deployment.
- `ATTACH` and `DETACH` are not supported.
- DuckDB file paths are backend-local only.
- Read-only connections cannot execute mutations.
- Schema context: schema selector is visible (normally `main`). No independent database selector.
- SQL splitter: generic (no DELIMITER, no PL/SQL, no GO).
- Diagnostics: structured unsupported. EXPLAIN, lock info, pool status, table space, index hints, terminate session, and optimize table are all unsupported.
- ER Inspector: Day-1 `dialect_unsupported` (embedded engine, foreign-key metadata not yet verified) — see `[[er-tabs]]`.
- ER Designer: Day-1 `dialect_unsupported`.

## ClickHouse

- Connection kind: `clickhouse`. Analytical column-store database over HTTP protocol.
- Fields: `host` (hostname or IP), `port` (default 8123), `username`, `password`, `databaseName`. Protocol defaults to HTTP; set port to 8443 for HTTPS.
- Chat-path execution: SELECT / INSERT / UPDATE / DDL all run via `datatalk_execute_sql`; only DELETE triggers the in-chat `confirmationId` flow — see `[[sql-execution]]`.
- ClickHouse mutations (`INSERT`, `ALTER`, `DELETE`) are async at the engine level; results may not be immediately visible after `datatalk_execute_sql` returns. The chat-path DELETE confirmation flow still applies — see `[[sql-execution]]`.
- File and network access functions (`file`, `s3`, `url`, `remote`, `hdfs`, `odbc`, `jdbc`, `mysql`, `postgresql`) are not supported.
- Cluster and system operations (`SYSTEM`, `KILL QUERY`, `OPTIMIZE`, `ATTACH`, `DETACH`) are not supported.
- SQL splitter: generic (no DELIMITER, no PL/SQL, no GO; format / SETTINGS clauses are statement-internal).
- Risk guard: `SHOW`, `DESCRIBE`, `EXPLAIN` are L1; bounded `INSERT` and safe `CREATE TABLE` are L2; `DROP`, `TRUNCATE`, `ALTER`, `RENAME`, `GRANT`, `REVOKE`, `CREATE USER/ROLE/DICTIONARY` are L3. External table functions (`remote()`, `url()`, `s3()`, `file()`, etc.) are hard reject.
- Schema context: database selector visible (replaces schema selector as ClickHouse has no schema layer). System databases (`system`, `INFORMATION_SCHEMA`, `_temporary_and_external_tables`) are filtered.
- Diagnostics: structured unsupported. EXPLAIN, lock info, pool status, table space, index hints, terminate session, and optimize table are all unsupported.
- ER Inspector: Day-1 `dialect_unsupported` (no FK constraints in the OLTP sense) — see `[[er-tabs]]`.
- ER Designer: Day-1 `dialect_unsupported` (table engine decisions not mappable to DataTalk ER DDL contract).

## Apache Doris

- Connection kind: `apache_doris` (alias `doris` is normalized to `apache_doris`). OLAP database with MySQL-compatible network protocol.
- Fields: `host` (FE hostname or IP), `port` (default 9030 for FE MySQL protocol), `username`, `password`, `databaseName`. Uses MySQL Connector/J driver.
- Chat-path execution: SELECT / INSERT / UPDATE / DDL all run via `datatalk_execute_sql`; only DELETE triggers the in-chat `confirmationId` flow — see `[[sql-execution]]`.
- Doris DML may be async for some operations; results from `datatalk_execute_sql` may not be immediately visible. The standard DELETE confirmation flow still applies — see `[[sql-execution]]`.
- Bulk load operations (`LOAD LABEL`, `ROUTINE LOAD`, `STREAM LOAD`, `EXPORT`) and cluster management (`ADMIN`, `ALTER SYSTEM`, `SHUTDOWN`, `DECOMMISSION`) are not supported through the chat path.
- SQL splitter: reuses MySQL splitter (DELIMITER, backtick identifiers, comment handling).
- Risk guard: `SHOW`, `DESCRIBE`, `EXPLAIN` are L1; `INSERT`, `CREATE TABLE`, `CREATE INDEX`, `ANALYZE` are L2; `DROP`, `TRUNCATE`, `ALTER`, `GRANT`, `REVOKE`, `CREATE USER/ROLE`, `LOAD`, `ROUTINE LOAD`, `EXPORT`, `ADMIN`, `SHUTDOWN` are L3.
- Schema context: database selector visible (Doris databases map to MySQL-style catalogs). Schema selector hidden (Doris has no independent schema layer).
- Diagnostics: Day-1 structured unsupported. EXPLAIN, lock info, pool status, table space, index hints, terminate session, and optimize table are all unsupported.
- ER Inspector: Day-1 `dialect_unsupported` (foreign-key metadata not verified for Doris) — see `[[er-tabs]]`.
- ER Designer: Day-1 `dialect_unsupported` (Doris DDL has unique distribution / partition syntax).

## OceanBase (MySQL-mode)

- Connection kind: `oceanbase` (no aliases — `oceanbase-ce`, `ob`, `obcluster`, `OceanBase` are all rejected at `ConnectionKind.normalize`).
- Day-1 first-class: MySQL-mode only. Oracle-mode (`compatibility_mode='oracle'`) returns `dialect_unsupported`.
- Fields: `host`, `port` (default 2881), `username`, `password`, `databaseName`, `compatibilityMode` (required), `oceanbaseTenant` (required), `oceanbaseCluster` (optional).
- Driver: `com.oceanbase:oceanbase-client`; URL `jdbc:oceanbase://<host>:<port>/<db>`; default port 2881.
- Username form `<user>@<tenant>` or `<user>@<tenant>#<cluster>` is composed by ConnectionService; AI must NOT fabricate this; structured fields in MCP.
- Chinese aliases (Ant OceanBase, Woqu OceanBase) are recognized in user natural-language input only; they map to canonical kind `oceanbase`, NOT to `mysql`.
- Day-1 unsupported: PROCEDURE / FUNCTION / TENANT / OUTLINE / RESOURCE POOL / ALTER SYSTEM / MAJOR-MINOR FREEZE / BACKUP-RESTORE — all classified L3 or `dialect_unsupported`.
- Chat-path execution: SELECT / INSERT / UPDATE / DDL all run via `datatalk_execute_sql`; only DELETE triggers the in-chat `confirmationId` flow — see `[[sql-execution]]`.
- SQL splitter: reuses MySQL splitter (backtick identifier support, DELIMITER handling).
- Risk guard: `SHOW`, `DESCRIBE`, `EXPLAIN` are L1; `INSERT`, `CREATE TABLE`, `CREATE INDEX` are L2; `DROP`, `TRUNCATE`, `ALTER`, `GRANT`, `REVOKE`, `CREATE OUTLINE`, `ALTER OUTLINE`, `CREATE TENANT`, `ALTER TENANT`, `DROP TENANT`, `CREATE RESOURCE POOL/UNIT`, `ALTER RESOURCE POOL/UNIT`, `DROP RESOURCE POOL/UNIT`, `ALTER SYSTEM`, `MAJOR FREEZE`, `MINOR FREEZE`, `BACKUP`, `RESTORE` are L3.
- Schema context: database selector visible (OceanBase databases). System schemas (`oceanbase`, `information_schema`, `mysql`, `SYS`, `LBACSYS`) are filtered from target discovery.
- Diagnostics: Day-1 structured unsupported. All 9 hooks (`explain_real` / `index_hints` / `lock_info` / `pool_status` / `table_space` / `terminate_session` / `optimize_table` / `er_inspector` / `er_designer`) return `dialect_unsupported`.
- ER Inspector: Day-1 `dialect_unsupported` — see `[[er-tabs]]`.
- ER Designer: Day-1 `dialect_unsupported`.

## StarRocks

- Connection kind: `starrocks`. OLAP database with native StarRocks JDBC driver.
- Fields: `host` (FE hostname or IP), `port` (default 9030 for FE query port), `username`, `password`, `databaseName` (required, StarRocks database within `default_catalog`).
- Catalog: Day-1 uses `default_catalog` hardcoded in the JDBC URL (`jdbc:starrocks://host:9030/default_catalog.database`).
- Chat-path execution: SELECT / INSERT / UPDATE / DDL all run via `datatalk_execute_sql`; only DELETE triggers the in-chat `confirmationId` flow — see `[[sql-execution]]`.
- Catalog operations (`CREATE/DROP CATALOG`), load operations (`LOAD LABEL`, `ROUTINE LOAD`, `STREAM LOAD`, `BROKER LOAD`), export, cluster management (`ADMIN`, `ALTER SYSTEM`), and global variable changes (`SET GLOBAL`) are not supported through the chat path.
- SQL splitter: reuses MySQL splitter (backtick identifier support, DELIMITER handling).
- Risk guard: `SHOW`, `DESCRIBE`, `EXPLAIN` are L1; `INSERT`, `CREATE TABLE`, `CREATE INDEX`, `ANALYZE` are L2; `DROP`, `TRUNCATE`, `ALTER`, `GRANT`, `REVOKE`, `CREATE USER/ROLE/CATALOG`, `DROP CATALOG`, `LOAD`, `ROUTINE LOAD`, `STREAM LOAD`, `BROKER LOAD`, `CANCEL LOAD`, `EXPORT`, `ADMIN`, `SET GLOBAL`, `SET PASSWORD`, `KILL`, `SUBMIT TASK`, `CANCEL TASK`, `RENAME`, `INSERT OVERWRITE` are L3.
- Schema context: database selector visible (StarRocks databases within `default_catalog`). Schema selector hidden.
- Diagnostics: Day-1 structured unsupported. EXPLAIN, lock info, pool status, table space, index hints, terminate session, and optimize table are all unsupported.
- ER Inspector: Day-1 `dialect_unsupported` (StarRocks DDL has unique distribution / partition syntax) — see `[[er-tabs]]`.
- ER Designer: Day-1 `dialect_unsupported` (StarRocks DDL has unique distribution / partition syntax).

## Trino

- Connection kind: `trino`. Federated SQL query engine with official Trino JDBC driver.
- Fields: `host` (Trino coordinator hostname or IP), `port` (default 8080), `username`, `password` (optional), `databaseName` (Trino catalog, optional).
- Catalog / schema: two-level context model — `databaseName` maps to Trino catalog, schema maps to Trino schema. Both catalog and schema selectors are visible in the Query Editor. System catalogs (`system`, `memory`, `jmx`) are filtered from target discovery.
- Chat-path execution: SELECT / INSERT / UPDATE / DDL all run via `datatalk_execute_sql`; only DELETE triggers the in-chat `confirmationId` flow — see `[[sql-execution]]`.
- Write support is connector-dependent — `INSERT`, `CREATE TABLE AS`, `UPDATE`, `DELETE`, and DDL may or may not be available depending on the underlying federation connector. Successful execution through `datatalk_execute_sql` is not a guarantee that other connectors of the same engine accept the same statement.
- Connector caveat: different Trino connectors have different capabilities. Do not claim generic write, ER, or diagnostics support without connector-specific verification.
- SQL splitter: generic (no DELIMITER, no PL/SQL, no GO).
- Risk guard: `SHOW`, `DESCRIBE`, `EXPLAIN` are L1; `INSERT`, `CREATE TABLE`, `CREATE VIEW`, `CREATE MATERIALIZED VIEW`, `UPDATE`, `DELETE` are L2; `DROP`, `TRUNCATE`, `ALTER`, `GRANT`, `REVOKE`, `CREATE USER/ROLE/CATALOG`, `CALL`, `SET SESSION`, `RESET SESSION`, `SET PATH` are L3.
- Schema context: database selector visible (Trino catalogs via `SHOW CATALOGS`), schema selector visible (Trino schemas). Catalog + schema are applied via `setCatalog()` + `setSchema()`.
- Diagnostics: Day-1 structured unsupported. EXPLAIN, lock info, pool status, table space, index hints, terminate session, and optimize table are all unsupported.
- ER Inspector: Day-1 `dialect_unsupported` — see `[[er-tabs]]`.
- ER Designer: Day-1 `dialect_unsupported`.
- `datatalk_resolve_use_target` is required before changing catalog or schema context — see `[[connection-management]]`.
- Trino is NOT a Presto alias. Do not treat `trino` connections as Presto or route Trino SQL to Presto tools.

## Presto

- Connection kind: `presto`. Federated SQL query engine separate from Trino; uses Presto JDBC driver.
- Fields: `host` (Presto coordinator hostname or IP), `port` (default 8080), `username`, `password`, `databaseName` (Presto catalog, optional), and schema (via session data context).
- Catalog / schema: two-level context model — `databaseName` maps to Presto catalog, schema maps to Presto schema. Both catalog and schema selectors are visible in the Query Editor.
- Chat-path execution: SELECT / INSERT / UPDATE / DDL all run via `datatalk_execute_sql`; only DELETE triggers the in-chat `confirmationId` flow — see `[[sql-execution]]`.
- Write support is connector-dependent — `INSERT`, `CREATE TABLE AS`, `UPDATE`, `DELETE`, and DDL may or may not be available depending on the underlying federation connector. Successful execution through `datatalk_execute_sql` is not a guarantee that other connectors of the same engine accept the same statement.
- Connector caveat: different Presto connectors have different capabilities. Do not claim generic write, ER, or diagnostics support without connector-specific verification.
- SQL splitter: generic (no DELIMITER, no PL/SQL, no GO).
- Risk guard: `SHOW`, `DESCRIBE`, `EXPLAIN` are L1; `INSERT`, `CREATE TABLE`, `CREATE VIEW`, `CREATE MATERIALIZED VIEW`, `UPDATE`, `DELETE` are L2; `DROP`, `TRUNCATE`, `ALTER`, `GRANT`, `REVOKE`, `CREATE USER/ROLE`, `CALL`, `SET SESSION`, `RESET SESSION`, `SET PATH`, `PREPARE`, `EXECUTE`, `DEALLOCATE PREPARE`, `START TRANSACTION`, `COMMIT`, `ROLLBACK` are L3.
- Schema context: database selector visible (Presto catalogs), schema selector visible (Presto schemas). System catalogs (`system`, `jmx`) are filtered from target discovery.
- Diagnostics: Day-1 structured unsupported. EXPLAIN, lock info, pool status, table space, index hints, terminate session, and optimize table are all unsupported.
- ER Inspector: Day-1 `dialect_unsupported` — see `[[er-tabs]]`.
- ER Designer: Day-1 `dialect_unsupported`.
- Presto is NOT a Trino alias. Do not treat `presto` connections as Trino or route Presto SQL to Trino tools.

## Dameng

- **Canonical kind:** `dameng` (lower-case). **No aliases**: `dm`, `dm8`, `DM`, `DM8`, `DM7`, `dameng7`, `dameng8`, `Wuhan Dameng`, `Dameng` are all rejected at `ConnectionKind.normalize`.
- **Driver:** `com.dameng:DmJdbcDriverX:8.1.x` (Maven Central direct; commercial license; offline jar in repo is **forbidden** by Wave C umbrella §10).
- **Driver class:** `dm.jdbc.driver.DmDriver`.
- **URL:** `jdbc:dm://<host>:<port>` (server-level; **no `/<database>` suffix**; default port 5236).
- **`databaseName` field:** reused as the **initial schema name** (Oracle-precedent; not a separate database). Schema is injected post-connect via `SET SCHEMA <name>`.
- **Day-1 unsupported (returns structured `dialect_unsupported`):**
  - PL/SQL blocks (`DECLARE ... BEGIN ... END;`)
  - PROCEDURE / FUNCTION / TRIGGER / PACKAGE DDL
  - EXP / IMP CLI utility commands
- **Day-1 L3 admin commands (risk label `dameng_admin_command`):** TABLESPACE / USER / ROLE DDL, GRANT / REVOKE, DROP TABLE / VIEW / INDEX / SEQUENCE / SYNONYM. These execute via `datatalk_execute_sql` like other SQL; explicitly preview the SQL to the user before running because of their admin-level impact — see `[[sql-execution]]`.
- **All 7 diagnostics hooks** (`lock_info`, `pool_status`, `table_space`, `terminate_session`, `optimize_table`, `explain_real`, `index_hints`) return structured `dialect_unsupported` Day-1.
- **ER hooks** (`er_inspector`, `er_designer`) return structured `dialect_unsupported` Day-1 — see `[[er-tabs]]`.
- Dameng is **not** an alias of Oracle. AI must not persist or display dameng connections as `oracle` in any code path.

## Apache Hive

- Connection kind: `hive`. Apache HiveServer2 data warehouse with Hive JDBC driver.
- Fields: `host` (HiveServer2 hostname or IP), `port` (default 10000 for binary transport), `username`, `password`, `databaseName` (Hive database).
- Transport / auth: Day-1 supports binary transport only (port 10000) with username/password or username-only authentication. HTTP transport, SSL, Kerberos, ZooKeeper service discovery, custom headers / cookies, and Knox-style deployments are NOT supported.
- Catalog / schema: database-only context — `databaseName` maps to Hive database. Schema selector is hidden in the Query Editor.
- `datatalk_resolve_use_target` is required before changing database context via `USE` — see `[[connection-management]]`.
- Chat-path execution: SELECT / INSERT / UPDATE / DDL all run via `datatalk_execute_sql`; only DELETE triggers the in-chat `confirmationId` flow — see `[[sql-execution]]`.
- SQL splitter: generic (no DELIMITER, no PL/SQL, no GO).
- Risk guard: `SHOW`, `DESCRIBE`, `EXPLAIN` are L1; `INSERT`, `CREATE TABLE`, `CREATE VIEW`, `ANALYZE` are L2; `DROP`, `TRUNCATE`, `ALTER`, `GRANT`, `REVOKE`, `CREATE USER/ROLE/FUNCTION`, `LOAD DATA`, `ADD JAR`, `TRANSFORM`, `MSCK`, `SET`, `IMPORT`, `EXPORT` are L3.
- Diagnostics: Day-1 structured unsupported. EXPLAIN, lock info, pool status, table space, index hints, terminate session, and optimize table are all unsupported.
- ER Inspector: Day-1 `dialect_unsupported` — see `[[er-tabs]]`.
- ER Designer: Day-1 `dialect_unsupported`.

## GaussDB

- **Canonical kind:** `gaussdb`
- **Protocol:** PostgreSQL-compatible (centralized mode), password authentication
- **JDBC URL:** `jdbc:postgresql://host:8000/database`
- **Default port:** 8000
- **Driver:** `com.huaweicloud:gaussdbjdbc:v2.0-8.218.0`
- **SQL splitter:** PG splitter (dollar-quoted, PL/pgSQL, stored procedures)
- **Risk rules:** PG baseline + 5 GaussDB-specific L3 (`CREATE RESOURCE POOL`, `ALTER COORDINATOR`, `DROP NODE`, `SHUTDOWN`, `ALTER SYSTEM SET`). All SQL executes via `datatalk_execute_sql`; explicitly preview the SQL to the user before running these L3 statements because of their cluster impact — see `[[sql-execution]]`.
- **Diagnostics:** All unsupported (Day-1)
- **ER:** Unsupported (Day-1) — see `[[er-tabs]]`
- **Day-2:** EXPLAIN diagnostics, ER DDL, SSL / TLS
- **Day-3:** Distributed / DWS mode, Kerberos
- Chat-path execution: SELECT / INSERT / UPDATE / DDL all run via `datatalk_execute_sql`; only DELETE triggers the in-chat `confirmationId` flow — see `[[sql-execution]]`.
