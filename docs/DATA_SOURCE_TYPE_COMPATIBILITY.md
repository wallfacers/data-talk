# Data Source Type Compatibility Gate

This document is a mandatory implementation gate for AI agents. It exists to
prevent incomplete database-type work. When a task adds, changes, or depends on
a database type, the agent must use this document as a checklist and update it
when the checklist itself becomes incomplete.

## Hard Rules

- Do not treat a new data source type as a UI dropdown change or a JDBC URL
  change. DataTalk has database-type behavior in frontend UI, backend
  connection handling, schema discovery, SQL execution, diagnostics, MCP tool
  schemas, and runtime agent instructions.
- Any database-related implementation must mention this document in its plan or
  final notes. If a section is not applicable, mark it as `N/A` with a concrete
  reason.
- If the implementation discovers another database-specific branch, enum,
  prompt rule, formatter, driver, parser, or test fixture, update this document
  before claiming completion.
- Do not add prompt-only support. A database type is supported only when the
  backend can connect, discover metadata, execute SQL safely, and the frontend
  can create/use the connection without hidden assumptions.
- Runtime AI tool names exposed through OpenCode MCP use `datatalk_*` names.
  Internal action ids stay `datatalk.*`. Do not mix these two naming layers.
- For frontend-facing changes, apply `client/DESIGN.md`: semantic tokens,
  Chat/Workbench as one system, global Stage state, accessible controls, and
  i18n keys for user-visible text.

## When This Gate Applies

Use this document for all of these tasks:

- Adding a new connection kind such as SQL Server, Oracle, ClickHouse, DuckDB,
  Snowflake, MariaDB, or a cloud warehouse.
- Changing the semantics of `database`, `schema`, `catalog`, default database,
  search path, target discovery, or `use xxx`.
- Adding a new SQL execution path, metadata tool, MCP action, UI action, query
  diagnostics tool, report/chart generator that runs SQL, or schema reader.
- Changing SQL splitting, SQL risk analysis, guarded DDL/DML behavior, result
  limits, or error handling.
- Changing connection forms, connection settings, generated API types, SQL
  formatting, SQL keyword lists, Stage Query Editor context, or data source
  pickers.
- Adding or changing JDBC drivers, runtime packaging, OpenCode prompt rules, or
  agent tool contracts.

## Current Support Snapshot

This table describes the current repository state. Keep it accurate.

| Kind | Current status | Notes |
|---|---|---|
| `mysql` | First-class | Connection UI, JDBC URL, metadata, SQL execution, MySQL-specific SQL splitter with `DELIMITER` support, diagnostics provider, prompt rules. Query/editor target selection treats MySQL databases as the selectable namespace and does not expose a separate Schema selector. |
| `postgresql` / `postgres` | First-class with aliases | PostgreSQL-specific SQL splitter, schema/search-path handling, target discovery, diagnostics provider. Preserve both aliases where existing code accepts both. |
| `h2` | Development/demo support | Connection UI, JDBC URL, generic SQL splitter, schema handling, diagnostics provider. |
| `sqlite` | First-class file-scoped support | Metadata DB uses SQLite, and user SQLite files are now first-class: connection UI, JDBC URL building, connection test, target discovery, `read_schema`, Query Editor schema-less context, read-only SQL execution, prompt rules, and diagnostics are verified. Day-2 EXPLAIN real (`EXPLAIN QUERY PLAN <sql>`, id/parent columns); Day-2 INDEX_HINTS real (FULL_SCAN → BTREE, MEDIUM impact); LOCK/POOL/SPACE/TERMINATE/OPTIMIZE remain structured unsupported. `databaseName` is the SQLite file path or a temporary per-JDBC-connection `:memory:` test target; there is no independent server catalog/schema. MCP create/update connection schemas are kind-conditional, so SQLite no longer relies on fake host/port/user/password placeholders. ER Designer remains CREATE-only day-1. |
| `oracle` | First-class | Connection UI, JDBC URL (SID / service-name modes), `ojdbc11` driver, metadata discovery with 30 system schema filters, SQL execution, generic SQL splitter, 6 risk rules, and structured unsupported diagnostics. Intentionally unsupported: PL/SQL splitter, ER DDL generation, diagnostics execution. Day-2 enhancements tracked in child plan. |
| `sqlserver` | First-class | Connection UI, JDBC URL (instance name / encrypt / trust certificate), `mssql-jdbc` 12.8.1 driver, metadata discovery with system database filters, SQL execution, generic SQL splitter, 8 risk rules, and diagnostics. Day-2 EXPLAIN real (`SET SHOWPLAN_XML ON/OFF`, XXE-safe XML parsing); Day-2 INDEX_HINTS real (FULL_SCAN → BTREE, impact tier by estimated rows); LOCK/POOL/SPACE/TERMINATE/OPTIMIZE remain structured unsupported. `mssql` alias normalized to `sqlserver`. Intentionally unsupported: GO batch splitter, ER DDL generation. Day-2 enhancements tracked in child plan. |
| `mariadb` | First-class | Connection UI, JDBC URL, MariaDB Connector/J driver, metadata discovery, SQL execution. Intentionally reuses MySQL ecosystem: `MySqlSqlStatementSplitter`, `MySqlDiagnosticsProvider` (verified — reuses MySqlDiagnosticsProvider via supportedDriverTypes('mariadb')), ER DDL via `MariaDbDdlGenerator`. First-class support proven by MariaDB-specific tests at every reuse point. Day-2 enhancements: standalone risk rules, diagnostics compatibility verification via MariaDbCompatibilityIT, real-database smoke. |
| `duckdb` | First-class embedded/file support | Connection UI (mode selector, file path, read-only flag), JDBC URL (`jdbc:duckdb:`), DuckDB JDBC driver, in-memory and file modes, read-only connections, schema context (`main`), generic SQL splitter, DuckDB-specific risk rules (COPY/EXPORT/IMPORT/ATTACH/DETACH/INSTALL/LOAD/CREATE SECRET/read_csv/read_parquet/glob hard reject), type normalization (UUID, Struct, Map), diagnostics and ER. Day-2 EXPLAIN real (`EXPLAIN <sql>`, simplified flat node list via DUCKDB_GRAMMAR); Day-2 INDEX_HINTS unsupported (DuckDB column store, points to zone map); LOCK/POOL/SPACE/TERMINATE/OPTIMIZE remain structured unsupported. No host/port/username/password. MCP create/update schemas kind-conditional. Day-2: ER DDL generation, extension/file-access sandbox. |
| `clickhouse` | First-class analytical SQL support | Connection UI (host/port/username/password, protocol selector HTTP/HTTPS, default port 8123), JDBC URL (`jdbc:clickhouse://`), ClickHouse JDBC driver, database context selector, generic SQL splitter, ClickHouse risk rules (SHOW/DESCRIBE/EXPLAIN L1, bounded INSERT/safe CREATE TABLE L2, DROP/TRUNCATE/ALTER/RENAME/GRANT/REVOKE/CREATE USER/ROLE/DICTIONARY L3, file/network access functions hard reject), type normalization (UInt, Decimal, UUID, IPv4/IPv6, Array, Tuple, Map, Nested, LowCardinality, Nullable, Enum, Date, DateTime, DateTime64, String, FixedString, Bool), diagnostics and ER. Day-2 EXPLAIN real (`EXPLAIN PLAN <sql>`, 2-space indented tree via CLICKHOUSE_GRAMMAR, ScanType from Granules ratio); Day-2 INDEX_HINTS unsupported (ClickHouse uses ORDER BY primary key + data skipping indexes); LOCK/POOL/SPACE/TERMINATE/OPTIMIZE remain structured unsupported. Day-2: ER DDL generation, KILL/SYSTEM/OPTIMIZE support, cluster operations. |
| `apache_doris` | First-class OLAP SQL support | Connection UI (host/port/username/password, default FE query port 9030), JDBC URL (`jdbc:mysql://` via MySQL Connector/J), database context selector, MySQL SQL splitter reuse, Doris-specific risk rules (SHOW/DESCRIBE/EXPLAIN L1, INSERT/CREATE TABLE/CREATE INDEX/ANALYZE L2, DROP/TRUNCATE/ALTER/ALTER SYSTEM/GRANT/REVOKE/CREATE USER/ROLE/RENAME/LOAD/ROUTINE LOAD/EXPORT/ADMIN/SHUTDOWN/DECOMMISSION L3), diagnostics and ER. Day-2 EXPLAIN real (`EXPLAIN <sql>`, PLAN FRAGMENT parsed via DORIS_GRAMMAR, ScanType from PREAGGREGATION/PREDICATES/ROLLUP); Day-2 INDEX_HINTS unsupported (Apache Doris uses ROLLUP / MV / inverted indexes); LOCK/POOL/SPACE/TERMINATE/OPTIMIZE remain structured unsupported. Day-2: ER DDL generation, real connection smoke. |
| `starrocks` | First-class OLAP SQL support | Connection UI (host/port/username/password, default FE query port 9030), JDBC URL (`jdbc:starrocks://` via native StarRocks Connector/J, `default_catalog.database` composition), database context selector (required), MySQL SQL splitter reuse, StarRocks-specific risk rules (SHOW/DESCRIBE/EXPLAIN L1, INSERT/CREATE TABLE/CREATE INDEX/ANALYZE L2, DROP/TRUNCATE/ALTER/GRANT/REVOKE/CREATE USER/ROLE/CATALOG/DROP CATALOG/LOAD/ROUTINE LOAD/STREAM LOAD/BROKER LOAD/CANCEL LOAD/EXPORT/ADMIN/SET GLOBAL/SET PASSWORD/KILL/SUBMIT TASK/CANCEL TASK/RENAME/INSERT OVERWRITE L3), diagnostics and ER. Day-2 EXPLAIN real (`EXPLAIN <sql>`, reuses DORIS_GRAMMAR); Day-2 INDEX_HINTS unsupported (StarRocks uses sort key / bitmap / bloom filter); LOCK/POOL/SPACE/TERMINATE/OPTIMIZE remain structured unsupported. Day-2: ER DDL generation, explicit catalog support, real connection smoke. |
| `presto` | First-class federated SQL support | Connection UI (host/port/username/password, default port 8080), JDBC URL (`jdbc:presto://` via Presto JDBC driver), catalog/schema two-level context selectors (catalog → databaseName, schema → schema), generic SQL splitter, Presto-specific risk rules (SHOW/DESCRIBE/EXPLAIN L1, INSERT/CREATE TABLE/CREATE VIEW/CREATE MATERIALIZED VIEW/UPDATE/DELETE L2, DROP/TRUNCATE/ALTER/GRANT/REVOKE/CREATE USER/ROLE/CALL/SET SESSION/RESET SESSION/SET PATH L3), connector caveat documented, diagnostics and ER. Day-2 EXPLAIN real (`EXPLAIN (TYPE LOGICAL) <sql>`, dash-prefixed tree via TRINO_GRAMMAR, federated pushdown warning); Day-2 INDEX_HINTS unsupported (Presto is federated, indexes on underlying connector); LOCK/POOL/SPACE/TERMINATE/OPTIMIZE remain structured unsupported. Separate from Trino (no `trino` alias). Day-2: ER DDL generation, real connection smoke. |
| `trino` | First-class federated SQL support | Connection UI (host/port/username/password, default port 8080), JDBC URL (`jdbc:trino://` via Trino JDBC driver), catalog/schema two-level context selectors, generic SQL splitter, Trino-specific risk rules, diagnostics and ER. Day-2 EXPLAIN real (`EXPLAIN (TYPE LOGICAL) <sql>`, reuses TRINO_GRAMMAR); Day-2 INDEX_HINTS unsupported (Trino is federated); LOCK/POOL/SPACE/TERMINATE/OPTIMIZE remain structured unsupported. Day-2: ER DDL generation, real connection smoke. |
| `hive` | First-class data warehouse SQL support | Connection UI (host/port/username/password, default port 10000), JDBC URL (`jdbc:hive2://` via Apache Hive JDBC driver), database context selector, generic SQL splitter, Hive-specific risk rules (SHOW/DESCRIBE/EXPLAIN L1, INSERT/CREATE TABLE/CREATE VIEW/ANALYZE L2, DROP/TRUNCATE/ALTER/GRANT/REVOKE/CREATE USER/ROLE/FUNCTION/LOAD DATA/ADD JAR/TRANSFORM/MSCK/SET/IMPORT/EXPORT L3), diagnostics and ER. Day-2 EXPLAIN real (`EXPLAIN <sql>`, STAGE PLANS parsed via HIVE_GRAMMAR, partition check warning); Day-2 INDEX_HINTS unsupported (Hive uses partitioning / bucketing); LOCK/POOL/SPACE/TERMINATE/OPTIMIZE remain structured unsupported. Day-2: ER DDL generation, HTTP/Kerberos/SSL/ZooKeeper transport support. |
| `tidb` | First-class | Connection UI (host/port/username/password, default port 4000), JDBC URL (`jdbc:mysql://...?useSSL=false&allowPublicKeyRetrieval=true`) reusing existing `com.mysql:mysql-connector-j` driver, MySQL-protocol metadata reuse proven by 6 `TiDb*ReuseIT` concrete subclasses of the new `MySqlProtocolReuseRule` abstract base kit, `MySqlSqlStatementSplitter` reuse, `JdbcResultValueNormalizer` mysql baseline reuse, batch DML path, AUTO_RANDOM normalized as `BIGINT UNSIGNED`, independent `classifyTidbSpecific` risk rules covering 30+ TiDB-only patterns (ADMIN CANCEL/PAUSE/RESUME DDL JOBS L3, ADMIN CHECK TABLE L2, ADMIN SHOW DDL L1, SPLIT TABLE L2, RECOVER TABLE L2, ALTER TABLE COMPACT L2, BACKUP/RESTORE/IMPORT INTO/LOAD DATA INFILE/FLASHBACK/PLACEMENT POLICY/KILL TIDB/SET GLOBAL/BATCH ON L3, SHOW PLACEMENT/REGIONS/STATS_* L1), diagnostics and ER. Day-2 EXPLAIN real (`EXPLAIN <sql>`, tabular parsed via TiDB TabularLayout); Day-2 INDEX_HINTS real (FULL_SCAN → BTREE via SqlColumnExtractor, impact tier by estRows); LOCK/POOL/SPACE/TERMINATE/OPTIMIZE remain structured unsupported (Day-3 includes Statement Summary / ADMIN SHOW DDL). TiDB Cloud (Serverless / Dedicated) and TLS / SSL are out of scope for Day-1. Day-2: ER Inspector / Designer, TiDB-only outline keywords, brand icon. Minimum supported server version: TiDB 6.5 LTS. |
| `oceanbase` | First-class Day-1 (MySQL-mode only) | Connection UI (host/port/username/password + compatibilityMode + oceanbaseTenant + oceanbaseCluster, default port 2881), JDBC URL (`jdbc:oceanbase://<h>:<p>/<db>` via `com.oceanbase:oceanbase-client` 2.4.13 driver), `ConnectionKind.OCEANBASE` + normalize rules, `ConnectionRecord` 3 new fields (`compatibilityMode`, `oceanbaseTenant`, `oceanbaseCluster`), Flyway V18 migration, `ConnectionService.composeOceanBaseUsername` (`<user>@<tenant>` or `<user>@<tenant>#<cluster>`), MySQL splitter / metadata / normalizer reuse proven by 6 `OceanBase*ReuseIT` concrete subclasses (@Disabled pending container), `classifyOceanBaseSpecific` risk classifier with 6 anchored patterns (OUTLINE DDL, TENANT DDL, RESOURCE POOL/UNIT DDL, ALTER SYSTEM, MAJOR/MINOR FREEZE, BACKUP/RESTORE — all L3), `MultiModeConnectionShape` v1 (`CompatibilityMode` enum MYSQL/ORACLE/PG + `validateModeForKind` + `isDay1FirstClassMode`), `OceanBaseDiagnosticsProvider` all-9-hooks `dialect_unsupported`, AGENTS.md OceanBase section, MCP `ConnectionObjectType` OCEANBASE enum. Oracle-mode `dialect_unsupported` until Day-3. T1 fixture: `oceanbase/oceanbase-ce:4.2.1-lts`. Day-2: diagnostics upgrade path (explain_real/index_hints), ER Inspector/Designer. |
| `dameng` | First-class Day-1 (DM 8) | Driver `com.dameng:DmJdbcDriverX:8.1.3.140` (Maven Central direct; commercial license; no offline jar), URL `jdbc:dm://<host>:<port>` port 5236 (no `/<database>` suffix), `databaseName` field reused as initial schema name (Oracle-precedent; injected via post-connect `SET SCHEMA`), Oracle splitter (GenericSqlStatementSplitter) / metadata (ALL_TABLES/ALL_TAB_COLUMNS/ALL_IND_COLUMNS via Oracle discovery path) / normalizer (JdbcResultValueNormalizer Oracle baseline) reuse via 3 kind-private equivalence tests, 5-item DAMENG_SYSTEM_SCHEMAS filter (SYS/SYSDBA/SYSAUDITOR/SYSSSO/CTISYS), dual-channel risk classifier (5 anchored L3 admin patterns: TABLESPACE/USER/ROLE DDL + GRANT/REVOKE + DROP OBJECT → `dameng_admin_command`; 3 anchored dialect_unsupported patterns: PL/SQL blocks + PROCEDURE/FUNCTION/TRIGGER/PACKAGE DDL + EXP/IMP), all 7 diagnostics hooks dialect_unsupported (explain_real/index_hints/lock_info/pool_status/table_space/terminate_session/optimize_table), no Flyway migration, no new ConnectionRecord columns, no multi-mode. Frontend: single-mode connection form (host/port 5236/username/password + schema optional), picker label "Dameng (DM 8)", plsql formatter routing. PL/SQL blocks + PROCEDURE/FUNCTION/TRIGGER/PACKAGE DDL + EXP/IMP commands all dialect_unsupported Day-1. T2 fixture: manual smoke 9-case + JDBC mock unit tests; CI does not start DM server. |

ER Inspector follows this matrix: `mysql`, `postgresql` / `postgres`, `h2`,
`mariadb`, and user `sqlite` file connections use JDBC `DatabaseMetaData.getImportedKeys`;
`oracle`, `sqlserver`, `duckdb`, `clickhouse`, `apache_doris`, `starrocks`, `trino`, `presto`, `hive`, `oceanbase`, and `tidb` are explicitly unsupported and must return structured
`dialect_unsupported` guidance instead of a fake empty ER graph.

ER Designer follows this DDL matrix: `mysql`, `postgresql` / `postgres`, `h2`,
and `mariadb` generate day-1 DDL for `CREATE TABLE`, `ALTER ADD COLUMN`, `ALTER ADD FK`,
and `CREATE INDEX`; `sqlite` is CREATE-only for table/index generation and must
return `SkippedOp` for ALTER variants; `oracle`, `sqlserver`, `duckdb`, `clickhouse`, `apache_doris`, `starrocks`, `trino`, `presto`, `hive`, `oceanbase`, and `tidb` are explicitly
unsupported with `dialect_unsupported`. DROP, ALTER COLUMN type changes, and
RENAME are N/A for day-1 automated generation because they are always returned
as `SkippedOp` with `day1_unsupported`; users must write that SQL manually in
`query_editor` and execute it through the existing L2/L3 guarded SQL flow.

### Feature Compatibility Matrix

| Feature | Compatibility notes |
|---|---|
| ER Tabs (Inspector + Designer) | Inspector: mysql / postgresql / h2 / mariadb fully via JDBC `getImportedKeys`, and sqlite user file connections use the same metadata path with no extra schema selector; oracle / sqlserver / duckdb / clickhouse / apache_doris / starrocks / trino / presto / tidb `dialect_unsupported`. Designer day-1 DDL generation: mysql / postgresql / h2 / mariadb emit CREATE TABLE / ALTER ADD COLUMN / ALTER ADD FK / CREATE INDEX; sqlite is CREATE-only with all ALTER variants returning `SkippedOp`; oracle / sqlserver / duckdb / clickhouse / apache_doris / starrocks / trino / presto / tidb `dialect_unsupported`. DROP / ALTER COLUMN type / RENAME are always `SkippedOp` (`day1_unsupported`) regardless of dialect; users must write that SQL manually in the `query_editor` and run it through L2/L3 confirmation. |

### ER Designer Gate Notes

- Domain Layer: N/A for this adapter/docs slice; domain records/enums are owned
  by the backend core ER Designer worker.
- Frontend Column Type Options: ER Designer exposes dialect-aware native type
  candidates for the existing `mysql`, `postgresql`, `h2`, and `sqlite`
  dialect values. These options are UI affordances only; stored draft column
  types remain strings so synced metadata and manually entered type strings are
  preserved.
- Application Connection Layer: N/A for this adapter/docs slice; no new
  connection kind, JDBC URL shape, database/schema semantics, or target
  resolution behavior is introduced here.
- Persistence And Metadata DB: N/A; ER Designer reuses existing generic Stage
  tab persistence and does not add metadata DB columns or migrations.
- JDBC Driver And Runtime Packaging: N/A; day-1 DDL support only covers
  existing supported drivers/kinds and does not add dependencies.
- Dynamic SQL Execution Repository: N/A; generated DDL lands in `query_editor`
  and uses the existing guarded SQL execution path.
- Result Values, Analytics, Visualization, And Reports: N/A; ER Designer DDL
  generation does not change SQL result value shapes, charting, reports, or
  exports.
- SQL Statement Splitting: N/A; generated DDL is executed later by
  `query_editor` through existing splitter/guard behavior for the selected
  connection kind.
- SQL Risk Analysis And Guards: N/A for generation; execution still uses the
  existing L2/L3 guarded SQL path and no new mutation egress is introduced.
- Schema Discovery And Target Resolution: Applicable only through existing
  bound connection/database/schema fields; this slice adds REST/action hooks,
  not new target-resolution semantics.
- Adapter Actions And Ontology: Applicable; `ui_exec` exposes ER Designer
  verbs, `ui_patch` accepts `er_designer`, REST endpoints return structured
  target/dialect errors, and message bundles include user-visible descriptions.
- Diagnostics Compatibility Checklist: N/A; ER Designer does not add or change
  diagnostics providers/actions.

## Roadmap Expansion Candidates

These are roadmap candidates, not supported kinds. A candidate becomes
first-class only after a focused spec/plan completes the compatibility checklist
below and updates the support snapshot above. Do not expose these in the
frontend connection form, runtime prompt, or MCP schemas as supported until then.

Market input is directional and should be rechecked when a child plan starts.
DB-Engines' April 2026 ranking lists 431 DBMSs and keeps Oracle, MySQL,
Microsoft SQL Server, PostgreSQL, MongoDB, Snowflake, Databricks, Redis, IBM
Db2, Apache Cassandra, Elasticsearch, SQLite, MariaDB, Apache Hive, Google
BigQuery, ClickHouse, DuckDB, and Trino among visible high-ranking or
fast-moving systems. Product priority still depends on DataTalk fit, available
drivers, license/redistribution, test fixture quality, and dialect risk.

### Candidate Priority Bands

| Band | Candidate kinds | Notes |
|---|---|---|
| A — close partial/stub and common enterprise SQL | `oracle`, `sqlserver` / `mssql`, `mariadb` | Wave A completed 2026-05-07. All three kinds are now first-class. Remaining Day-2 items: Oracle PL/SQL splitter + diagnostics EXPLAIN; SQL Server GO splitter + diagnostics EXPLAIN; MariaDB standalone risk rules + diagnostics compatibility verification. |
| B — analytics / OLAP SQL engines | `apache_doris` / `doris`, `starrocks`, `clickhouse`, `hive`, `trino`, `presto`, `duckdb` | Validate JDBC behavior, catalog/schema semantics, splitter safety, and whether diagnostics can be real or must return structured unsupported. These candidates have moved into the Wave B Child Artifact Tracking table below. |
| C — domestic / enterprise compatibility | `gaussdb`, `opengauss`, `dameng` / `dm` / `dm8`, `kingbase` / `kingbasees`, `oceanbase`, `tidb` | Wave C 6/6 complete 2026-05-12: step 1 (`tidb`) → step 2 (`opengauss`) → step 3 (`oceanbase`) → step 4 (`kingbase`) → step 5 (`dameng`) → step 6 (`gaussdb`). No remaining C-band candidates. |
| D — cloud warehouses / lakehouse SQL | `snowflake`, `bigquery`, `redshift`, `databricks_sql` | Watch for non-standard authentication, warehouse/project/dataset fields, JDBC driver redistribution limits, billing-sensitive metadata scans, and result-limit semantics. |
| E — non-SQL or semi-SQL sources | `mongodb`, `elasticsearch`, `opensearch`, optionally `redis` only if product scope expands beyond SQL | These require a separate read/query contract and should not be forced through fake SQL execution. Mutation and schema semantics must be designed before implementation. |

### Wave A Child Artifact Tracking

Wave A child artifacts are documentation gates, not support declarations. A kind
stays in its current support state until its child implementation plan is
executed, verified, and the support snapshot above is updated.

| Kind | Child design | Child plan | Current outcome |
|---|---|---|---|
| `sqlite` | `docs/product-specs/2026-04-30-data-source-coverage-sqlite-design.md` | `docs/exec-plans/2026-04-30-data-source-coverage-sqlite-plan.md` | Implemented 2026-05-01 in the working tree: frontend completion, file-scoped context, `read_schema`, prompt contract, kind-conditional create/update schemas, structured unsupported diagnostics, and verification are in place; support snapshot is now first-class. |
| `oracle` | `docs/product-specs/2026-04-30-data-source-coverage-oracle-design.md` | `docs/exec-plans/2026-04-30-data-source-coverage-oracle-plan.md` | Completed 2026-05-07: first-class support with SID/service-name URL, `ojdbc11` driver, 6 risk rules, 30 system schema filters, full frontend, and AGENTS.md prompt rules. Intentionally unsupported: PL/SQL splitter, ER DDL generation, diagnostics execution. Day-2 enhancements remain open. |
| `sqlserver` | `docs/product-specs/2026-04-30-data-source-coverage-sqlserver-design.md` | `docs/exec-plans/2026-04-30-data-source-coverage-sqlserver-plan.md` | Completed 2026-05-07: first-class support with `mssql-jdbc` 12.8.1, encrypt/trust/instance options, 8 risk rules, full frontend, and AGENTS.md prompt rules. `mssql` alias normalized to `sqlserver`. Intentionally unsupported: GO batch splitter, ER DDL generation, diagnostics execution. Day-2 enhancements remain open. |
| `mariadb` | `docs/product-specs/2026-04-30-data-source-coverage-mariadb-design.md` | `docs/exec-plans/2026-04-30-data-source-coverage-mariadb-plan.md` | Completed 2026-05-07: first-class support with MySQL-ecosystem reuse (`MySqlSqlStatementSplitter`, `MySqlDiagnosticsProvider`, `MariaDbDdlGenerator`). 6 backend + 1 frontend test files. Day-2 enhancements: standalone risk rules, diagnostics compatibility verification, real-database smoke. |

### Wave B Child Artifact Tracking

Wave B child artifacts are documentation gates for analytics and OLAP SQL
engines. They are not support declarations. A kind stays unsupported until its
child implementation plan is executed, verified, and the support snapshot above
is updated.

| Kind | Child design | Child plan | Current outcome |
|---|---|---|---|
| `apache_doris` | `docs/product-specs/2026-05-01-data-source-coverage-apache-doris-design.md` | `docs/exec-plans/2026-05-01-data-source-coverage-apache-doris-plan.md` | Completed 2026-05-07: first-class OLAP SQL support via MySQL Connector/J, MySQL splitter reuse, Doris-specific risk rules, structured unsupported diagnostics and ER. Day-2: EXPLAIN diagnostics, ER DDL generation, real connection smoke. |
| `starrocks` | `docs/product-specs/2026-05-01-data-source-coverage-starrocks-design.md` | `docs/exec-plans/2026-05-01-data-source-coverage-starrocks-plan.md` | Completed 2026-05-08: first-class OLAP SQL support with native StarRocks Connector/J (`jdbc:starrocks://`, `default_catalog.database` URL composition), database context selector, generic SQL splitter, StarRocks-specific risk rules, structured unsupported diagnostics and ER. Day-2: EXPLAIN diagnostics, ER DDL generation, explicit catalog support, real connection smoke. |
| `clickhouse` | `docs/product-specs/2026-05-01-data-source-coverage-clickhouse-design.md` | `docs/exec-plans/2026-05-01-data-source-coverage-clickhouse-plan.md` | Completed 2026-05-07: first-class analytical SQL support with connection contract, metadata discovery, driver setup, generic SQL splitter, ClickHouse risk rules, type normalization, structured unsupported diagnostics and ER. Connection UI with protocol selector (HTTP/HTTPS), AGENTS.md prompt rules. Day-2: EXPLAIN diagnostics, ER DDL generation, KILL/SYSTEM/OPTIMIZE support, cluster operations. |
| `duckdb` | `docs/product-specs/2026-05-01-data-source-coverage-duckdb-design.md` | `docs/exec-plans/2026-05-01-data-source-coverage-duckdb-plan.md` | Completed 2026-05-07: first-class embedded/file support with connection UI, in-memory/file modes, read-only flag, generic splitter, DuckDB risk rules, type normalization, structured unsupported diagnostics/ER. Day-2: EXPLAIN, ER DDL, extension sandbox. |
| `trino` | `docs/product-specs/2026-05-01-data-source-coverage-trino-design.md` | `docs/exec-plans/2026-05-01-data-source-coverage-trino-plan.md` | Planned: federated Trino design with catalog/schema context and connector-capability caveats; support remains unsupported until implementation completes. |
| `presto` | `docs/product-specs/2026-05-01-data-source-coverage-presto-design.md` | `docs/exec-plans/2026-05-01-data-source-coverage-presto-plan.md` | Completed 2026-05-08: first-class federated SQL support with Presto JDBC driver (`jdbc:presto://`), catalog/schema two-level context selectors, generic SQL splitter, Presto-specific risk rules (SHOW/DESCRIBE/EXPLAIN L1, INSERT/CREATE TABLE/CREATE VIEW/UPDATE/DELETE L2, DROP/TRUNCATE/ALTER/GRANT/REVOKE/CREATE USER/ROLE/CALL/SET SESSION/RESET SESSION L3), connector caveat documented, structured unsupported diagnostics and ER. Separate from Trino. Day-2: EXPLAIN diagnostics, ER DDL generation, real connection smoke. |
| `hive` | `docs/product-specs/2026-05-01-data-source-coverage-hive-design.md` | `docs/exec-plans/2026-05-01-data-source-coverage-hive-plan.md` | Completed 2026-05-08: first-class data warehouse SQL support with connection contract, metadata discovery via SHOW DATABASES, `hive-jdbc` 4.0.1 driver, generic SQL splitter, Hive risk rules, type normalization, structured unsupported diagnostics and ER. Connection UI with default port 10000, AGENTS.md prompt rules. Day-2: EXPLAIN diagnostics, ER DDL generation, HTTP/Kerberos/SSL/ZooKeeper transport support. |

### Wave C Child Artifact Tracking

Wave C child artifacts are documentation gates for domestic and enterprise
compatibility kinds. A kind stays unsupported until its child implementation plan
is executed, verified, and the support snapshot above is updated.

| Kind | Child design | Child plan | Current outcome |
|---|---|---|---|
| `tidb` | `docs/product-specs/2026-05-08-data-source-coverage-tidb-design.md` | `docs/exec-plans/2026-05-08-data-source-coverage-tidb-plan.md` | Completed 2026-05-08: first-class OSS / self-hosted TiDB support via mysql-connector-j reuse; produced `MySqlProtocolReuseRule` 6-base kit with 6 TiDB concrete IT subclasses; 30+ TiDB-only risk rules; structured unsupported diagnostics + ER. Day-2: real diagnostics, ER, TiDB Cloud / TLS via cross-kind design. |
| `opengauss` | `docs/product-specs/2026-05-08-data-source-coverage-opengauss-design.md` | `docs/exec-plans/2026-05-08-data-source-coverage-opengauss-plan.md` | Completed 2026-05-09: first-class support with `opengauss-jdbc` driver, PG splitter/metadata reuse, 12-item system schema filter, 4 anchored L3 risk patterns, produced `PgForkReuseRule` 6-base abstract test kit. Day-2: EXPLAIN via `PostgresJsonPlanParser`, ER, real connection smoke. |
| `oceanbase` | `docs/product-specs/2026-05-08-data-source-coverage-oceanbase-design.md` | `docs/exec-plans/2026-05-08-data-source-coverage-oceanbase-plan.md` | Completed 2026-05-09: first-class MySQL-mode support via `oceanbase-client` driver, produced `MultiModeConnectionShape` v1 (kind-neutral multi-mode abstraction), MySQL splitter reuse, 7-item system schema filter, 6 anchored risk patterns. Day-2: Oracle-mode, real diagnostics, ER. |
| `kingbase` | `docs/product-specs/2026-05-08-data-source-coverage-kingbase-design.md` | `docs/exec-plans/2026-05-08-data-source-coverage-kingbase-plan.md` | Completed 2026-05-09: first-class PG-mode support via `kingbase8` driver 9.0.1, pure consumer of `PgForkReuseRule` + `MultiModeConnectionShape` v1 (zero new cross-kind abstractions). Permitted alias `kingbasees` → `kingbase` (only Wave C alias per umbrella §8 line 293). Dual-channel risk classifier: 4 L3 anchored patterns (SYS_* DDL / SYS<CRT|AUDIT>_* / SYS_KILL / FLASHBACK TABLE) + 2 dialect_unsupported (KBBACKUP/KBRESTORE / Oracle-style PL/SQL block). All 9 diagnostics hooks dialect_unsupported. 6-item system schema filter (4 PG + sys + sys_catalog). Splitter equivalence test + manual smoke 9 cases. Day-2: EXPLAIN via `PostgresJsonPlanParser`, Oracle-mode, ER, real IT. |
| `dameng` | `docs/product-specs/2026-05-08-data-source-coverage-dameng-design.md` | `docs/exec-plans/2026-05-08-data-source-coverage-dameng-plan.md` | Completed 2026-05-09: first-class support via `DmJdbcDriverX` driver, Oracle-like independent kind (no MultiMode, no PgFork consumption), GenericSqlStatementSplitter, 5-item system schema filter (SYS/SYSDBA/SYSAUDITOR/SYSSSO/CTISYS), SET SCHEMA post-connect, dual-channel risk (5 L3 + 3 dialect_unsupported), 3 kind-private equivalence tests. No aliases accepted. Day-2: EXPLAIN via DamengTabularGrammar, ER, real IT. |

### Candidate Naming Notes

- Use canonical lower-case kind strings in persisted records and API payloads.
- `apache_doris` is the canonical candidate for Apache Doris; accept `doris`
  only through an explicit normalization boundary if needed.
- `gaussdb` and `opengauss` must be evaluated separately during implementation.
  Huawei GaussDB, openGauss, and PostgreSQL-compatible modes can differ in
  driver, authentication, catalog/schema behavior, and dialect support.
- `dameng` is the canonical candidate for 达梦; `dm` / `dm8` are aliases only if
  the implementation explicitly normalizes them.
- Cloud warehouse candidates often need fields beyond host/port/database, such
  as account, region, warehouse, project, dataset, role, HTTP path, token, or
  JDBC properties. Do not overload `databaseName` with these without a plan.
- Embedded engines such as DuckDB need an additional file/network egress gate:
  SELECT-shaped table functions that read backend-local files or remote
  resources are not automatically L1. They need dialect risk tests, a sandbox or
  allowlist design, and Workbench confirmation or structured unsupported output.

## Mandatory Repository Scan

Before designing or implementing a database-type change, run targeted searches.
Do not rely on memory.

```bash
rg -n "ConnectionKind|DbType|DbConnection|ConnectionRecord|databaseName|schemaName|driverType|kind\\("
rg -n "JdbcUrlBuilder|DriverManager|getConnection|setCatalog|setSchema|getCatalogs|getSchemas|getTables|getColumns"
rg -n "mysql|postgres|postgresql|sqlite|h2|oracle|sqlserver|mssql|mariadb|clickhouse|duckdb|doris|apache_doris|starrocks|hive|trino|presto|gauss|gaussdb|opengauss|dameng|dm8|kingbase|oceanbase|tidb|snowflake|bigquery|redshift|databricks|db2|hana|teradata|mongodb|elasticsearch|opensearch|redis"
rg -n "DiagnosticsProvider|EXPLAIN|indexHints|lockInfo|pool_status|table_space"
rg -n "SqlStatementSplitters|SqlStatementGuard|CalciteSqlRiskAnalyzer|SqlExecuteService|ExecuteSqlAction|ReadSchemaAction"
rg -n "UseTargetResolver|SessionDataContextService|SessionDataContextRepository|resolve_use_target|list_connection_targets"
rg -n "datatalk_.*sql|datatalk_.*schema|datatalk_.*connection|AGENTS.md|MCP|tools/list"
rg -n "DATABASE_TYPES|DbType|formatSql|sql-dialects|parse-sql-outline|connection-form|data-source"
```

For frontend work, also inspect:

```bash
rg -n "kind|databaseName|schema|connectionId" client/src/features client/src/services client/src/types
rg -n "run_sql|set_context|query-editor-actions|use-sql-execute|services/api/sql"
```

For backend work, inspect:

```bash
rg --files server | rg "(Connection|Sql|Schema|Diagnostics|Mcp|Action|Jdbc|migration|messages)"
```

## Canonical Naming Rules

Every data source type needs a canonical kind string.

- Use lower-case strings in persisted records and API payloads.
- Keep aliases explicit. Example: `postgres` may be accepted as input, but
  `postgresql` is the canonical kind in most current code.
- Alias acceptance must have an explicit normalization boundary. If input can
  accept aliases (`postgres`), define where they are canonicalized (for
  example create/update REST or action layer) before persistence and JDBC
  routing. Do not rely on scattered `equalsIgnoreCase` branches.
- Update all enum-like schemas and UI lists together. Search for hand-written
  string lists; not every list is generated.
- If a database has separate concepts for catalog, database, schema, namespace,
  warehouse, or search path, document the mapping in this file and in the
  implementation plan.

Template for a new kind:

```text
Kind: <canonical-kind>
Aliases: <optional aliases>
Default port:
JDBC driver artifact:
JDBC driver class:
JDBC URL form:
Default database/catalog behavior:
Schema/search-path behavior:
Metadata discovery behavior:
SQL splitter:
Risk analyzer caveats:
Diagnostics support:
Frontend formatter language:
Prompt guidance:
Unsupported capabilities:
```

## Backend Compatibility Checklist

### Domain Layer

Check and update:

- `server/data-talk-domain/src/main/java/com/datatalk/entity/DbType.java`
- `server/data-talk-domain/src/main/java/com/datatalk/entity/DbConnection.java`
- Domain records/enums used by diagnostics or query result contracts.

Required decisions:

- Does the new kind need a `DbType` enum value, or should the code path move
  toward string-only `ConnectionRecord.kind()`?
- Does any sealed interface or exhaustive switch need a new branch?
- Are existing enum values misleading because they are only partial support?

Tests:

- Add or update domain tests for enum mapping or value object behavior when a
  new domain branch is introduced.

### Application Connection Layer

Check and update:

- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionContextRefreshService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRecord.java`
- `server/data-talk-application/src/main/java/com/datatalk/dto/ConnectionCreateRequest.java`
- `server/data-talk-application/src/main/java/com/datatalk/dto/ConnectionUpdateRequest.java`
- `server/data-talk-application/src/main/java/com/datatalk/dto/ConnectionDto.java`

Required decisions:

- JDBC URL shape, including default database behavior when `databaseName` is
  null.
- Driver-specific timeout parameters. MySQL uses millisecond-style parameters;
  PostgreSQL currently uses seconds. Other drivers differ.
- Whether username/password are optional, required, or replaced by token/DSN.
- Whether SSL, service name, instance name, warehouse, role, or extra JDBC
  properties are required. Do not smuggle these into unrelated fields without
  documenting it.
- Whether connection test should call `Connection.isValid(...)`, a validation
  query, or a driver-specific lightweight probe.

Tests:

- URL builder test for null and non-null database/catalog.
- Connection create/update DTO test if schema changes.
- Connection test behavior with a fake or embedded driver when possible.

### Persistence And Metadata DB

Check and update:

- `server/data-talk-infrastructure/src/main/resources/db/migration/`
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRepository.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRecord.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/repository/JdbcDbConnectionRepository.java` (legacy path; update only if touched)
- `docs/generated/db-schema.md`

Required decisions:

- Does the `connections` table need new columns for the new kind, such as
  `serviceName`, `warehouse`, `role`, `sslMode`, or `jdbcParams`?
- Can the existing `database_name` field represent the target safely, or would
  overloading it create ambiguity?
- If a migration is needed, update `docs/generated/db-schema.md`.

Tests:

- Repository round-trip tests for new persisted fields.
- Migration tests when schema changes.

### JDBC Driver And Runtime Packaging

Check and update:

- `server/data-talk-infrastructure/pom.xml`
- `server/data-talk-adapter/pom.xml` when integration tests need driver/testcontainers support.
- Tauri/desktop packaging assumptions if the driver is not already on the
  runtime classpath.

Required decisions:

- Maven artifact and version.
- Driver class name if Hikari or manual driver selection needs it.
- License and redistribution constraints.
- Native library requirements, if any.
- Testcontainers module availability for integration tests.

Do not claim support if the driver is only available in tests.

### Dynamic SQL Execution Repository

Check and update:

- `server/data-talk-infrastructure/src/main/java/com/datatalk/repository/DynamicSqlExecutionRepository.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/JdbcResultValueNormalizer.java`
- `server/data-talk-application/src/main/java/com/datatalk/service/QueryApplicationService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/controller/QueryController.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SqlExecuteController.java`

Required decisions:

- Driver class / connection pool config.
- Whether to call `setCatalog`, `setSchema`, SQL `USE`, `ALTER SESSION`, or no
  context setter.
- Whether multi-statement execution is supported.
- Whether transactions can wrap the statement batch.
- Whether consecutive DML can use JDBC `executeBatch()`.
- Whether any SQL rewrite optimization is allowed. Rewrites must be narrow,
  dialect-reviewed, and covered by tests; do not rewrite `UPDATE`, `DELETE`,
  `INSERT ... SELECT`, DDL, `CALL`, or procedural blocks by default.
- Whether generated keys, update counts, multiple result sets, and affected
  row counts behave differently.
- Whether `PreparedStatement.setMaxRows` is honored by the driver.
- Whether JDBC returns driver-specific values such as arrays, structs, JSON,
  XML, geography, intervals, timestamps with time zones, CLOB/BLOB, unsigned
  integers, decimals, or vendor-specific objects.
- How connection-level errors should be formatted in markdown diagnostics.
- Identifier quoting (table / column names emitted in CREATE TABLE / INSERT / undo
  log) **must** go through `IdentifierQuoter.quote(id, connectionKind)`. See
  [Identifier Quoting Per Dialect](#identifier-quoting-per-dialect). Direct
  `"col"` or `` `col` `` literals in `DataImportService`, `DataExportService`,
  `ScriptDataWriteService`, or `InverseSqlGenerator` are forbidden — call sites
  must thread `connectionKind` through and delegate to `IdentifierQuoter`.

Tests:

- `/api/query` or direct service test for read-only query.
- `/api/sql/execute` test for result-set and DML/DDL confirmation behavior when
  the kind supports mutations.
- `/api/sql/execute` or direct service test for DML batch behavior when the kind
  supports JDBC batch execution.
- Context application test for database/schema/catalog selection.
- Result normalization tests for the database's non-trivial JDBC value types.

Current DML execution strategy:

- `mysql`, `postgresql` / `postgres`, `h2`, and other JDBC kinds using
  `/api/sql/execute`: consecutive `INSERT` / `UPDATE` / `DELETE` statements are
  planned as JDBC `Statement.addBatch()` / `executeBatch()` units when no
  result-set statement interrupts the run.
- Consecutive same-prefix `INSERT INTO ... VALUES ...` statements are rewritten
  into a single multi-values insert only when the `VALUES` suffix is a pure
  tuple list. `INSERT ... SELECT`, `ON DUPLICATE KEY UPDATE`, `RETURNING`,
  DDL, `CALL`, procedural blocks, and unknown syntax are not rewritten.
- Risk analysis and confirmation run on the original user SQL before any
  execution optimization. Result payloads still report original statement text
  through the existing `dml_summary` contract.

### Result Values, Analytics, Visualization, And Reports

Check and update:

- `server/data-talk-application/src/main/java/com/datatalk/application/sql/JdbcResultValueNormalizer.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/RenderChartAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/SupersedeArtifactAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/PinArtifactAction.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ArtifactRepository.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/QueryResultRepository.java`
- `client/src/features/stage/components/sql-result-table.tsx`
- `client/src/features/stage/components/sql-result-display.test.tsx`
- `client/src/features/stage/utils/sql-result-export.ts`
- `client/src/features/chat/components/tools/renderers/execute-sql.tsx`
- Chart and report components under `client/src/features/stage/` and
  `client/src/features/chat/`.

Required decisions:

- Are returned date/time, decimal, binary, JSON, array, enum, and spatial values
  normalized into JSON-safe values before reaching chat, Stage, charting, or
  export?
- Does the database report column labels and types in a way that charts can infer
  dimensions/measures?
- Are large results still bounded by `pageSize`, `maxRows`, query-result handle
  storage, and export limits?
- Does SQL result export preserve values consistently across CSV, JSON, and
  clipboard paths?
- Do report/chart actions need kind-specific guidance in the runtime prompt,
  such as preferring aggregated SQL or avoiding raw-row reads?

Tests:

- Value normalization tests for driver-specific JDBC objects.
- SQL result table/export tests for representative values.
- Chart/report renderer tests if value shape or artifact payload changes.
- AI `datatalk_execute_sql` preview tests when analytics output changes.

### SQL Statement Splitting

Check and update:

- `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlStatementSplitters.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/GenericSqlStatementSplitter.java`
- Existing specialized splitters such as `MySqlSqlStatementSplitter` and
  `PostgresJdbcSqlStatementSplitter`.

Current splitter strategy:

- `mysql`: `MySqlSqlStatementSplitter`; handles quoted strings, backtick
  identifiers, `--` / `#` / `/* ... */` comments, backslash escapes, and
  client-side `DELIMITER` commands for stored-program scripts. `DELIMITER`
  lines are never sent to JDBC.
- `postgresql` / `postgres`: `PostgresJdbcSqlStatementSplitter`, backed by the
  PostgreSQL JDBC parser so dollar-quoted strings and PL/pgSQL blocks are not
  split on internal semicolons.
- `h2` and all unrecognized kinds: `GenericSqlStatementSplitter`; acceptable
  only for simple semicolon scripts with ordinary strings and comments.

Required decisions:

- Can the generic splitter safely handle strings, comments, procedural blocks,
  batch separators, dollar quotes, brackets, or dialect-specific delimiter
  commands?
- Does the database need a specialized parser or driver parser?
- Does the workbench support scripts for this kind, or only single statements?
- For every new database kind, explicitly choose one of: reuse a proven driver
  parser, add a dedicated dialect splitter, or document why the generic splitter
  is sufficient. Do not silently inherit the generic fallback for first-class
  database support.

Tests:

- Statement splitting unit tests for comments, quotes, procedural SQL, and
  multi-statement scripts.
- Regression test that `SqlExecuteService` selects the correct splitter.

### SQL Risk Analysis And Guards

Check and update:

- `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlStatementGuard.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlBearingActionInspector.java`
- `server/data-talk-domain/src/main/java/com/datatalk/domain/action/SqlExecutionRisk.java`
- Guarded DDL/DML frontend and backend tests.

Required decisions:

- Does Calcite parse the dialect syntax accurately enough?
- Are read-only commands broader than `SELECT/WITH`, such as `SHOW`,
  `DESCRIBE`, `EXPLAIN`, or database-specific metadata commands?
- Are mutating commands hidden behind dialect syntax that Calcite may parse as
  unknown?
- Are there high-risk commands unique to the database, such as `COPY`,
  `MERGE`, `CALL`, `ANALYZE`, `VACUUM`, `OPTIMIZE`, `KILL`, grants, role
  changes, external table operations, or warehouse changes?

Tests:

- L1/L2/L3 risk classification tests for the new dialect's common SQL.
- Chat-path `ExecuteSqlAction` test: L2/L3 must remain blocked in chat.
- Workbench confirmation tests for L2/L3 when execution is supported.

### Schema Discovery And Target Resolution

Check and update:

- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ListConnectionTargetsAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ResolveUseTargetAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/GetDataContextAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/SetDataContextAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SessionDataContextController.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionDataContextService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/UseTargetResolver.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/ResolvedExecutionContext.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionDataContextRecord.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/SessionDataContextRepository.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/TableContextAutoResolver.java`

Required decisions:

- Map DataTalk `database` and `schema` to the driver's `catalog` and `schema`
  concepts.
- For MySQL-compatible engines, DataTalk `database` maps to the JDBC catalog /
  MySQL schema namespace; target discovery and SQL editor controls must not
  expose a second independent `schema` level.
- Decide what `use xxx` means for the kind: connection, database, schema,
  catalog, namespace, or unsupported.
- Define system schemas to filter from target discovery.
- Define case-sensitivity and identifier quoting rules.
- Preserve large-schema guards: discovery must be bounded by `pattern`,
  `limit`, and `cursor`; describe mode must stay explicitly scoped.
- Decide whether column search is safe and bounded for this driver.

Tests:

- `read_schema` discover mode with `limit`, `cursor`, `pattern`.
- `read_schema` describe mode with explicit tables.
- Target discovery for database/schema/catalog names.
- `resolve_use_target` matched, ambiguous, and not-found cases.
- Table auto-resolver behavior for unqualified table names.

### Adapter Actions And Ontology

Check and update:

- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/CreateConnectionAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UpdateConnectionConfirmableAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/TestConnectionAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ListConnectionsAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/SelectConnectionAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiPatchAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/ErTabController.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/er/JdbcErRelationDiscoveryService.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/connection/ConnectionController.java`
- `server/data-talk-adapter/src/main/resources/messages.properties`
- `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties`

Required decisions:

- Should action input schemas enumerate allowed `kind` values? If not, how is
  invalid kind reported?
- Does `ConnectionObjectType.propertySchema()` expose the new kind?
- Does ER Inspector work from this database's foreign-key metadata? Plan A
  supports `mysql`, `postgresql` / `postgres`, `h2`, and user `sqlite`
  connections via JDBC `getImportedKeys`; `oracle` and `sqlserver` are
  unsupported with `dialect_unsupported` aiHint.
- Does connection update confirmation include all kind-specific fields in the
  preview and token?
- Do REST endpoints and MCP actions expose the same fields and validation
  behavior?
- Are all user-visible errors and labels localized in both supported languages?

Tests:

- Agent action schema contract tests.
- Ontology schema test for connection kind values.
- ER Inspector metadata test or explicit unsupported behavior.
- REST controller tests for create/update/test/list if fields or validation
  changed.
- i18n message tests or smoke checks for new labels/errors.

## Diagnostics Compatibility Checklist

Check and update:

- `server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/DiagnosticsProvider.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/DiagnosticsProviderRegistry.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/DiagnosticsService.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/*DiagnosticsProvider.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExplainQueryAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/IndexHintsAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/LockInfoAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/PoolStatusAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/TableSpaceAction.java`
- `client/src/services/api/diagnostics.ts`
- `client/src/features/stage/components/diagnostics/`
- `client/src/features/chat/components/tools/renderers/diagnostics-card.tsx`

Required decisions:

- `EXPLAIN` syntax and whether it executes the query.
- Whether the plan can be returned as JSON or must be parsed from text.
- How to normalize scan types into `FULL_SCAN`, `INDEX_RANGE`, `INDEX_SCAN`,
  `CONST`, `REF`, or `OTHER`.
- Whether index recommendations can be derived safely.
- Which capabilities are unsupported. Unsupported is valid only when returned
  as structured `unsupported`, not as an exception or fake empty success.
- Whether lock, pool, and table-space data exist and what privileges are
  required.

Tests:

- Provider `supportedDriverTypes()` includes canonical kind and aliases.
- `DiagnosticsProviderRegistry` routes the new kind correctly.
- EXPLAIN success, unsupported, and error propagation.
- Frontend renders ok/unsupported/error states.

## MCP And Runtime Agent Prompt Checklist

Check and update:

- `server/data-talk-application/src/main/java/com/datatalk/application/opencode/DataTalkMcpService.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/opencode/McpNameMapper.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/opencode/McpActionBridge.java`
- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java`
- `docs/references/opencode-protocol.md`

Required decisions:

- New MCP-visible tools must come from `ActionRegistry`; do not create a second
  registry.
- Internal id `datatalk.some_action` maps to MCP raw name `some_action` and
  OpenCode-visible name `datatalk_some_action`.
- If the new database kind changes tool usage rules, update runtime
  `AGENTS.md`. Examples: schema must be selected, broad schema reads are unsafe,
  diagnostics unsupported, DDL must go through guarded workbench flow.
- Hidden bridge fields `__dt*` must stay hidden from public tool schemas.
- Prompt rules must not claim unsupported database capabilities.

Tests:

- Prompt contract test proving runtime `AGENTS.md` references real MCP tool
  names.
- MCP `tools/list` includes/excludes expected actions.
- Tool-call tests for kind-specific schema or SQL behavior when relevant.

## UI Object And `ui_xxx` Compatibility Checklist

The `datatalk_ui_find`, `datatalk_ui_read`, `datatalk_ui_patch`, and
`datatalk_ui_exec` tools are MCP-visible CLIENT actions. They bridge OpenCode to
the frontend `UIRouter`. Database-type changes can break them indirectly through
query-editor context, Stage tab payloads, schema panel state, or workspace
connection selection.

Check and update:

- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiFindAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiReadAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiPatchAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiFindSchemas.java`
- `client/src/features/actions/ui-handlers.ts`
- `client/src/features/stage/adapters/WorkspaceAdapter.ts`
- `client/src/features/stage/adapters/QueryEditorAdapter.ts`
- `client/src/features/stage/components/stage-ui-object-registry.tsx`
- `client/src/services/ui-router/`
- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- `docs/references/ui-objects-reference.md`

Required decisions:

- Does query-editor state need new context fields beyond `connectionId`,
  `database`, and `schema`?
- Does `workspace.choose_connection` need to filter or annotate connections by
  new kind-specific capabilities?
- Do `ui_patch` paths remain sufficient for context updates?
- Does `query_editor.run_sql` pass the correct database/schema/catalog fields to
  `/api/sql/execute`?
- Does `ui_find` metadata expose enough connection information for AI to choose
  the correct tab without guessing?
- Are concurrency rules still valid: explicit target tab, `baseVersion`,
  `expectedText`, and conflict markdown?

Tests:

- `WorkspaceAdapter` and `QueryEditorAdapter` tests for context state.
- CLIENT action registration tests: internal handler ids remain
  `datatalk.ui.*`, while MCP tool names remain `datatalk_ui_*`.
- Prompt contract tests for the runtime `AGENTS.md` UI protocol section.
- `ui_find` / `ui_read` tests when tab metadata shape changes.

## Frontend Compatibility Checklist

Read `client/DESIGN.md` before changing UI requirements, specs, plans, or
implementation under `client/`.

Check and update:

- `client/src/features/settings/data-sources/connection-form-dialog.tsx`
- `client/src/features/settings/data-sources/api.ts`
- `client/src/services/api/connection.ts`
- `client/src/types/generated/api.ts`
- `client/src/features/connection/types.ts`
- `client/src/features/connection/store.ts`
- `client/src/features/session/data-source-picker/`
- `client/src/services/api/session-data-context.ts`
- `client/src/features/stage/components/sql-context-toolbar-controls.tsx`
- `client/src/features/stage/components/sql-editor-toolbar.tsx`
- `client/src/features/stage/components/activity-rail/schema-panel.tsx`
- `client/src/features/stage/utils/build-stage-resource-tree.ts`
- `client/src/features/stage/utils/format-sql.ts`
- `client/src/features/stage/utils/parse-sql-outline.ts`
- `client/src/features/stage/sql-dialects/*.json`
- `client/src/features/stage/hooks/use-sql-execute.ts`
- `client/src/features/stage/utils/query-editor-actions.ts`
- `client/src/services/api/sql.ts`
- `client/src/i18n/messages.ts`

Required decisions:

- Add the kind to connection creation/editing UI only after backend support is
  real.
- Default port and required fields.
- Whether the form needs kind-specific fields. Do not hide required connection
  properties in generic fields without explicit docs.
- SQL formatter language mapping. If `sql-formatter` does not support the
  dialect, choose a safe fallback and document the limitation.
- SQL outline keyword set and high-risk hint behavior.
- Schema/database selector visibility. MySQL-compatible and SQLite-like engines
  do not show a separate SQL editor Schema selector; PostgreSQL/H2 keep it.
- Data source picker labels and recent connection display.
- Query Editor run path contract: `connectionId`, `database`, `schema`,
  `sessionId`, `confirmed`, and `riskAck` must stay aligned between frontend
  request types and backend controller DTOs.
- Error, unsupported, and empty states must use existing semantic tokens and
  i18n messages.

Tests:

- Connection form create/edit for the new kind.
- API type checks or generated type updates.
- `format-sql` mapping test.
- SQL outline parsing for dialect-specific statements if supported.
- Stage context picker / schema panel tests.
- `npx tsc --noEmit`.

## Documentation Checklist

Check and update:

- This document.
- `CLAUDE.md` and `AGENTS.md` when a new mandatory rule or canonical doc is
  added.
- `ARCHITECTURE.md` when the support matrix or data flow changes.
- `docs/BACKEND.md` for backend extension rules.
- `docs/FRONTEND.md` for frontend extension rules.
- `docs/product-specs/index.md` if product support status changes.
- `docs/generated/db-schema.md` after metadata DB migrations.
- Runtime `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` when
  AI behavior changes.
- Relevant design docs and execution plans.

Every completed plan that changes database-type behavior must include document
housekeeping notes.

## Minimum Test Matrix For A New Data Source Type

The exact command set depends on the change, but these categories must be
covered or marked `N/A`.

Backend:

- Unit tests for `JdbcUrlBuilder`.
- Unit tests for `ConnectionKind`/kind mapping and invalid kind behavior.
- Alias normalization tests (`postgres` input path vs canonical persisted kind).
- Repository tests if metadata schema changed.
- Service tests for connection target discovery.
- `resolve_use_target` service/action tests for matched/ambiguous/not-found.
- `ReadSchemaAction` tests for discover/describe and large-schema bounds.
- `SqlExecuteService` tests for context application and statement execution.
- `ExecuteSqlAction` tests for chat-path read-only execution and L2/L3 block.
- SQL splitter tests.
- SQL risk analyzer tests for dialect-specific statements.
- Diagnostics provider tests or explicit unsupported tests.
- Agent prompt contract tests.
- `cd server && mvn compile -q`.

Frontend:

- Connection form tests.
- Data source picker/context selector tests.
- SQL formatter and outline tests.
- Diagnostics UI tests if diagnostics status changes.
- i18n tests or message coverage for new text.
- `cd client && npx tsc --noEmit`.

Integration/manual:

- Create connection.
- Test connection.
- List databases/schemas/catalogs.
- Select context via UI and `use xxx`.
- `read_schema` discovery and explicit table describe.
- Run read-only SQL from Query Editor.
- Run analytical `datatalk_execute_sql` from AI.
- Confirm L2/L3 workbench flow if mutations are supported.
- Run diagnostics or verify structured unsupported response.

## Definition Of Done

A database-type implementation is not done until all of these are true:

- The support snapshot in this document is updated.
- All affected backend branches, frontend lists, action schemas, prompt rules,
  tests, and docs are updated or explicitly marked `N/A`.
- The runtime AI prompt does not overclaim capabilities.
- Large schema and bounded result safeguards still hold.
- L2/L3 SQL cannot bypass the guarded Workbench confirmation flow.
- `CLAUDE.md` / `AGENTS.md` still point future AI agents to this gate.
- Verification commands relevant to the touched stack have been run and their
  outcomes are reported.

## Ingestion DDL Adapter (Day-1)

The external data ingestion feature (`data-ingestion` skill) uses `IngestionDdlAdapter` to generate dialect-specific DDL and INSERT statements. Day-1 supports 4 of 19 first-class connection kinds.

| Kind | Day-1 Status | Adapter Class | Notes |
|------|-------------|---------------|-------|
| mysql | Supported | `MysqlIngestionDdlAdapter` | Backtick quoting, DATETIME, JSON type |
| postgresql | Supported | `PostgresIngestionDdlAdapter` | Double-quote quoting, TIMESTAMP, JSONB |
| h2 | Supported | `H2IngestionDdlAdapter` | Double-quote quoting, CLOB for long text/JSON |
| sqlite | Supported | `SqliteIngestionDdlAdapter` | Double-quote quoting, TEXT for all strings/dates, NUMERIC |
| mariadb | Unsupported | — | Follow-up: mirror MySQL adapter with MariaDB-specific types |
| oracle | Unsupported | — | Follow-up child plan required |
| sqlserver | Unsupported | — | Follow-up: bracket quoting, DATETIME2, NVARCHAR |
| duckdb | Unsupported | — | Follow-up child plan required |
| clickhouse | Unsupported | — | Follow-up: MergeTree engine, specialized types |
| apache_doris | Unsupported | — | Follow-up child plan required |
| starrocks | Unsupported | — | Follow-up child plan required |
| trino | Unsupported | — | Follow-up child plan required |
| presto | Unsupported | — | Follow-up child plan required |
| hive | Unsupported | — | Follow-up child plan required |
| tidb | Unsupported | — | Follow-up: likely MySQL-compatible |
| oceanbase | Unsupported | — | Follow-up child plan required |
| dameng | Unsupported | — | Follow-up child plan required |
| kingbase | Unsupported | — | Follow-up child plan required |
| gaussdb | Unsupported | — | Follow-up child plan required |

## Undo Log (DML Rollback) Compatibility

The Undo Log feature captures before-state snapshots of DML operations and generates inverse SQL for one-click rollback. Compatibility depends on primary key availability and Calcite SQL parsing support.

| Kind | Undo Support | Notes |
|------|-------------|-------|
| mysql | Full | `getPrimaryKeys()` supported, Calcite parses MySQL DML correctly |
| postgresql | Full | `getPrimaryKeys()` supported, Calcite parses PG DML correctly |
| h2 | Full | `getPrimaryKeys()` supported, Calcite parses H2 DML correctly |
| sqlite | Full | `getPrimaryKeys()` supported, Calcite parses SQLite DML correctly |
| mariadb | Full | MySQL-compatible, same support level |
| oracle | Partial | `getPrimaryKeys()` supported; some Oracle-specific syntax may fail Calcite parsing → undoable=false |
| sqlserver | Partial | `getPrimaryKeys()` supported; T-SQL syntax may fail Calcite parsing → undoable=false |
| duckdb | Partial | `getPrimaryKeys()` supported; some DuckDB-specific extensions may fail parsing |
| tidb | Full | MySQL-compatible, same support level |
| clickhouse | None | MergeTree tables may lack traditional primary keys; Calcite parsing limited for ClickHouse dialect |
| hive | None | Hive tables often lack primary keys; limited DML support in Hive |
| trino | None | Trino is query-oriented, limited DML, tables may lack primary keys |
| presto | None | Same as Trino |
| apache_doris | Partial | MySQL-compatible DML, `getPrimaryKeys()` may work depending on table model |
| starrocks | Partial | MySQL-compatible DML, similar to Doris |
| oceanbase | Partial | MySQL-compatible, `getPrimaryKeys()` supported |
| dameng | Partial | `getPrimaryKeys()` supported; DM-specific syntax may fail Calcite parsing |
| kingbase | Partial | PG-compatible, `getPrimaryKeys()` supported |
| gaussdb | Partial | PG-compatible, `getPrimaryKeys()` supported |

Fallback behavior: When `getPrimaryKeys()` returns empty or Calcite parsing fails, the DML is recorded in `undo_log` with `undoable=false` (audit-only, no Undo button shown).

Unsupported kinds throw `IngestionDialectUnsupportedException` → MCP action returns `INGESTION_DIALECT_UNSUPPORTED` error code → frontend shows `ingestion.dialect_unsupported.<kind>` i18n message.

## Script Data Write Compatibility

The script data write feature enables programmatic batch data insertion into user databases via standard JDBC. All registered first-class database types are supported for data writing. The write mechanism uses standard JDBC `PreparedStatement` batch execution and does not rely on database-specific bulk-load utilities.

### Write Mechanism

- **CREATE TABLE with type inference**: Automatically creates the target table if it does not exist. Type mapping: `string` → `VARCHAR(255)`, `integer` → `BIGINT`, `float` → `DOUBLE`, `boolean` → `BOOLEAN`, `null` → `TEXT`.
- **Batch INSERT**: Uses `PreparedStatement.executeBatch()` for all databases. Rows are bound via parameterized `INSERT INTO ... VALUES (?, ?, ...)` statements.
- **Standard JDBC**: No database-specific bulk-load extensions (`LOAD DATA`, `COPY`, `BULK INSERT`, etc.) are used in the Day-1 implementation.

### Compatibility Matrix

| Kind | Write Support | Notes |
|------|--------------|-------|
| mysql | Supported | Standard JDBC batch INSERT. VARCHAR/BIGINT/DOUBLE/BOOLEAN type inference. |
| postgresql | Supported | Standard JDBC batch INSERT. Type inference compatible with PG column types. |
| h2 | Supported | Standard JDBC batch INSERT. Fully compatible with H2 type system. |
| sqlite | Supported | Standard JDBC batch INSERT. SQLite uses type affinity; VARCHAR/BIGINT/DOUBLE map to native storage. |
| mariadb | Supported | Standard JDBC batch INSERT. MySQL-compatible type inference. |
| oracle | Supported | Standard JDBC batch INSERT. VARCHAR2/NUMBER type mapping via standard JDBC types. |
| sqlserver | Supported | Standard JDBC batch INSERT. NVARCHAR/BIGINT/FLOAT/BIT type mapping. |
| duckdb | Supported | Standard JDBC batch INSERT. DuckDB column-store accepts standard batch writes. |
| clickhouse | Supported | Standard JDBC batch INSERT. MergeTree tables accept batch INSERT via JDBC driver. Performance may differ from native `INSERT FORMAT` bulk loading. |
| tidb | Supported | Standard JDBC batch INSERT. MySQL-compatible type inference and batch execution. |
| oceanbase | Supported | Standard JDBC batch INSERT. MySQL-mode compatible type inference. |
| starrocks | Supported | Standard JDBC batch INSERT. MySQL-protocol compatible via StarRocks Connector/J. |
| trino | Supported | Standard JDBC batch INSERT. Subject to connector write capabilities; some connectors may reject INSERT. |
| presto | Supported | Standard JDBC batch INSERT. Subject to connector write capabilities; some connectors may reject INSERT. |
| dameng | Supported | Standard JDBC batch INSERT. DM 8 type system compatible with standard JDBC types. |
| kingbase | Supported | Standard JDBC batch INSERT. PG-compatible type inference. |
| gaussdb | Supported | Standard JDBC batch INSERT. PG-compatible type inference. |
| apache_doris | Supported | Standard JDBC batch INSERT. MySQL-protocol compatible via MySQL Connector/J. |
| apache_hive | Supported | Standard JDBC batch INSERT. Hive supports INSERT INTO for managed tables; external tables and some storage formats may have limitations. |

### Known Limitations

- Performance for large datasets may benefit from database-specific bulk-load utilities (e.g., MySQL `LOAD DATA`, PostgreSQL `COPY`, ClickHouse `INSERT FORMAT`). These are not used in Day-1.
- Trino and Presto write support depends on the underlying connector. Connectors that do not support INSERT will propagate a connector-level error.
- Hive INSERT performance depends on the storage format and table type (managed vs external). ACID transactions are required for INSERT into transactional tables in Hive 3.x.
- Type inference uses fixed-width mappings (e.g., `VARCHAR(255)` for strings). Columns requiring longer strings, LOBs, or specialized types must be pre-created manually before running script data write.

## Data Import/Export Compatibility

The data import/export feature supports streaming file import, multi-format export, and cross-database copy via JDBC cursor. All paths are designed for bounded memory usage regardless of dataset size.

### Streaming Import

File import reads source files in streaming mode, accumulates rows in batches of 1000, and writes to the target database via `PreparedStatement.executeBatch()` with `autoCommit=false` and periodic commits.

| Format | Streaming Mechanism | Type Inference | Batch Size |
|--------|-------------------|----------------|------------|
| CSV | `BufferedReader` line-by-line, UTF-8 BOM skip, CSV RFC-compliant quoted fields | Long → `BIGINT`, Double → `DOUBLE`, else `VARCHAR(255)` | 1000 rows |
| JSON | Jackson `JsonParser` streaming, `array_of_objects` shape required | Integer/Long → `BIGINT`, Double/Float → `DOUBLE`, Boolean → `BOOLEAN`, else `VARCHAR(255)` | 1000 rows |
| XLSX | Apache POI SAX (`XSSFReader` + `SheetContentsHandler`), event-driven row processing, first sheet only | Long → `BIGINT`, Double → `DOUBLE`, Boolean → `BOOLEAN`, else `VARCHAR(255)` | 1000 rows |
| SQL | `SqlStatementSplitter` state machine (quote/comment tracking, semicolon terminator) → `SqlStreamReader` regex INSERT parsing. Supports multi-row VALUES, schema-qualified table names, cross-line statements. Single-statement size limit: 10 MB. | Syntax-token-based: unquoted integer → `BIGINT`, unquoted decimal → `DOUBLE`, quoted string → `VARCHAR(255)`, `TRUE`/`FALSE` → `BOOLEAN`, `NULL` → skip. Column type overrides via `columnTypes` take precedence. | 1000 rows |
| Cross-DB copy | JDBC cursor (`TYPE_FORWARD_ONLY`, `CONCUR_READ_ONLY`), `fetchSize=500`, `autoCommit=false` | Column types derived from source `ResultSetMetaData` | Via `writeStream` service |

Column type overrides are supported: callers may pass `columnTypes` to force specific DDL types, bypassing inference.

### Streaming Export

Export converts query results to files using streaming writers. The async threshold determines synchronous vs. asynchronous execution mode.

| Format | Streaming Mechanism | Row Limit | Notes |
|--------|-------------------|-----------|-------|
| CSV | `BufferedWriter` + UTF-8 BOM (`﻿`) prefix, RFC-compliant escaping | 1,000,000 | Excel-compatible UTF-8 BOM header |
| JSON | `BufferedWriter`, streaming array output (`[{...}, {...}]`) | 1,000,000 | Null values rendered as JSON `null` |
| XLSX | `SXSSFWorkbook` (window=100), streaming write | 1,048,576 | Hard cap at Excel specification; auto-truncated |
| SQL INSERT | `BufferedWriter`, batch INSERT statements (100 rows per `INSERT INTO ... VALUES` block) | 1,000,000 | Identifier quoting follows `IdentifierQuoter` — see [Identifier Quoting Per Dialect](#identifier-quoting-per-dialect) for the full 19-kind dispatch matrix; single-quote value escaping |

| Threshold | Behavior |
|-----------|----------|
| < 10,000 rows | Synchronous export; result returned immediately |
| >= 10,000 rows | Async export on virtual thread; SSE `export.completed` notification via `SessionBus` |

| Limit | Value |
|-------|-------|
| Default max rows | 1,000,000 |
| Export file size cap | 500 MB |
| XLSX row hard cap | 1,048,576 (Excel specification) |

### Identifier Quoting Per Dialect

Identifier quoting (table names, column names) is dispatched through
`com.datatalk.application.dialect.IdentifierQuoter` based on the source/target
`ConnectionKind`. This is the single owner of dialect-aware quoting for **import**
(`DataImportService`), **export** (`DataExportService`), **script writes**
(`ScriptDataWriteService`), and **undo log generation** (`InverseSqlGenerator`).
Direct hard-coded `"..."` or `` `...` `` literals in those services are forbidden.

| Quote Style    | Open / Close   | Escape Rule (embedded delimiter)                 | Connection Kinds                                                                                       |
|----------------|----------------|--------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| `BACKTICK`     | `` ` ` ``      | `` ` `` → `` `` ``                               | `mysql`, `mariadb`, `tidb`, `oceanbase`, `apache_doris`, `starrocks`, `clickhouse`                     |
| `DOUBLE_QUOTE` | `" "`          | `"` → `""`                                       | `postgresql` (`postgres` alias), `h2`, `sqlite`, `oracle`, `duckdb`, `kingbase`, `dameng`, `gaussdb`, `hive` (`apache_hive` alias), `trino`, `presto` |
| `BRACKET`      | `[ ]`          | `]` → `]]` (right bracket only)                  | `sqlserver` (`mssql` alias)                                                                            |

**Resolution rules:**
- Case-insensitive matching: `MySQL`, `MYSQL`, `mysql` all dispatch to `BACKTICK`.
- Unknown / `null` / blank `connectionKind` → fallback to `DOUBLE_QUOTE` + WARN log
  (preserves the historical pre-`IdentifierQuoter` ANSI behavior for legacy call sites
  that have not yet plumbed `kind` through).
- Reserved keywords (`select`, `order`, `group`, `from`, …) and identifiers containing
  spaces, dots, or quote characters are quoted/escaped identically — no opt-out.

**Why this matters:**
- BUG-0066: pre-`IdentifierQuoter` code emitted ANSI `"col"` even for MySQL targets.
  Under default `sql_mode` MySQL parses `"col"` as a string literal, not an identifier,
  causing import / export / script-write failures with cryptic syntax errors. This
  table is now the source of truth for the dispatch table — any future dialect (e.g.
  adding a 20th `ConnectionKind`) must be added here and in `IdentifierQuoter`
  simultaneously, or the contract test `IdentifierQuoterTest` will fail.

### Database Cursor Compatibility

Cross-DB copy and export both use JDBC streaming cursors to avoid loading full result sets into memory. The cursor setup pattern is:

```java
conn.setAutoCommit(false);
Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
stmt.setFetchSize(500);
```

| Kind | fetchSize Cursor | autoCommit Required | Notes |
|------|-----------------|---------------------|-------|
| mysql | `useCursorFetch=true` in JDBC URL | No | Must set `useCursorFetch=true` in JDBC URL for server-side streaming cursor; without it the driver fetches all rows into memory |
| postgresql | Default | Yes (OFF) | `autoCommit` must be `false` before `setFetchSize()` or the PG driver fetches all rows into memory |
| h2 | Default | No | Default cursor behavior sufficient for H2 |
| sqlite | Default | No | Single-file DB; cursor not tunable |
| oracle | Default | No | Oracle JDBC supports streaming cursor natively |
| sqlserver | Default | No | SQL Server JDBC supports adaptive buffering |
| mariadb | `useCursorFetch=true` in JDBC URL | No | Same MySQL-protocol requirement; inherits MySQL cursor behavior |
| duckdb | Default | No | Embedded engine; cursor handled internally |
| clickhouse | Default | No | ClickHouse JDBC streams by default |
| apache_doris | `useCursorFetch=true` in JDBC URL | No | MySQL-protocol; same cursor requirement as MySQL |
| starrocks | Default | No | Native StarRocks Connector/J handles streaming |
| tidb | `useCursorFetch=true` in JDBC URL | No | MySQL-protocol; same cursor requirement as MySQL |
| oceanbase | Default | No | OceanBase client driver handles cursor natively |
| trino | Default | No | Trino JDBC streams results by default |
| presto | Default | No | Presto JDBC streams results by default |
| hive | Default | No | Hive JDBC fetches in configurable batches |
| dameng | Default | No | DM JDBC driver supports streaming cursor natively |
| kingbase | Default | Yes (OFF) | PG-compatible; `autoCommit=false` required for fetchSize to take effect |
| gaussdb | Default | Yes (OFF) | PG-compatible; `autoCommit=false` required for fetchSize to take effect |

### Batch INSERT Compatibility

File import and cross-DB copy both write data using standard JDBC `PreparedStatement.executeBatch()`. All 19 first-class connection kinds are supported. See the **Script Data Write Compatibility** section above for the full compatibility matrix.

### Schema Search Support Matrix

`datatalk_schema_search` looks up candidate tables by keyword (Chinese / English / pinyin / synonym) using JDBC `DatabaseMetaData.getTables()` + `getColumns()`. Match locations are table name, column name, and column / table `REMARKS`. Score: `3 * table-name + 2 * column-name + 1 * comment`.

| kind | Support level | Comment support | Notes |
|------|---------------|-----------------|-------|
| mysql / mariadb / tidb / oceanbase / apache_doris / starrocks | **full** | yes | `REMARKS` exposed via JDBC driver (`useInformationSchema=true` recommended in URL). MySQL-protocol drivers also accept `INFORMATION_SCHEMA` fallback. |
| postgresql / gaussdb / kingbase | **full** | yes | PG driver exposes `pg_description` via `REMARKS`. |
| oracle / dameng | **full** | yes | Oracle / DM JDBC `remarksReporting=true` URL flag required for `REMARKS`. Without it, falls back to name-only match. |
| sqlserver | **full** | yes | SQL Server JDBC exposes `extended_properties.MS_Description` as `REMARKS`. |
| clickhouse | **full** | yes | ClickHouse JDBC exposes `comment` column on `system.tables` / `system.columns` as `REMARKS`. |
| trino / presto | **full** | yes | Driver exposes catalog comments when underlying connector supports them. |
| sqlite | **degraded** | no | SQLite has no native column-comment storage. Match falls back to table-name / column-name only; `commentSnippet = ""`. Action returns success, not an error. |
| duckdb | **degraded** | partial | DuckDB community extension support; comments may be empty depending on DuckDB version. Falls back to name-only match if `REMARKS` is null. |
| hive | **degraded** | table-only | Hive `DESCRIBE EXTENDED` is slow; first-version implementation only matches table name. Column-level `REMARKS` deferred to a future iteration. |
| (unknown kind, fails `ConnectionKind.normalize`) | **error** | n/a | Action returns `errorCode = sql.dialect_unsupported` with the kind name in the message. |

The matrix is informational; the action self-discovers via JDBC metadata and does not branch on `kind` for the happy path. Degraded kinds simply yield no `comment` matches, never a hard error.

### Known Limitations

- **MySQL cursor**: Requires `useCursorFetch=true` JDBC URL parameter for true server-side cursor. Without it, the MySQL driver fetches the entire result set into memory regardless of `fetchSize`.
- **PostgreSQL cursor**: Requires `autoCommit=false` before `setFetchSize()`. Without it, the PG driver fetches all rows into memory.
- **MySQL-protocol kinds** (mariadb, apache_doris, tidb): Inherit the `useCursorFetch=true` requirement from the MySQL wire protocol.
- **PG-protocol kinds** (kingbase, gaussdb): Inherit the `autoCommit=false` requirement from the PostgreSQL wire protocol.
- **SQLite concurrent access**: Single-file DB; concurrent write + export may encounter locking.
- **XLSX row limit**: Export capped at 1,048,576 rows (Excel specification). Rows beyond this are silently truncated.
- **Export file size cap**: 500 MB. Export stops when this limit is reached.
- **Default row limit**: 1,000,000 rows per export unless overridden by the caller.
- **Type inference**: Fixed-width mappings (e.g., `VARCHAR(255)` for strings). Columns requiring longer strings, LOBs, or specialized types must be pre-created manually before import.
- **SQL file import**: Only `INSERT INTO ... VALUES (...)` statements are parsed. DDL, UPDATE, DELETE, and statements with subqueries are skipped. Multi-target-table SQL files and table name mismatches between SQL content and the `target.tableName` parameter are rejected with clear error codes. Mixed DDL+INSERT files should be executed via the query editor's guarded flow instead.

## Change Log

| Change | Verdict | Reason |
|---|---|---|
| report-document-generation | **N/A** | Reuses existing `ExecuteSqlAction` for cross-source data fetch; introduces no new database type, no new JDBC connection logic, no new schema discovery path, no new SQL splitter / risk analyzer, no new diagnostic provider. Only adds `datatalk_promote_report` MCP action and `report` SQLite metadata table (which is DataTalk-internal, not a user data source). |
| bezel-compiler-redesign | **N/A** | Server-side deterministic HTML compilation of dashboard v3 JSON. Widget SQL execution pipeline unchanged — `query.connectionId` / `query.sql` still routed through existing `ExecuteSqlAction` at serve-time. No new database type, JDBC connection logic, schema discovery path, SQL splitter, risk analyzer, or diagnostic provider. Only adds `DashboardCompiler` pipeline, `PatternCatalog` YAML resources, and template/CSS assets (all static resources, no data-source interaction). |
