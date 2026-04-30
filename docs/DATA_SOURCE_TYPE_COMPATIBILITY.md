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
| `mysql` | First-class | Connection UI, JDBC URL, metadata, SQL execution, MySQL-specific SQL splitter with `DELIMITER` support, diagnostics provider, prompt rules. |
| `postgresql` / `postgres` | First-class with aliases | PostgreSQL-specific SQL splitter, schema/search-path handling, target discovery, diagnostics provider. Preserve both aliases where existing code accepts both. |
| `h2` | Development/demo support | Connection UI, JDBC URL, generic SQL splitter, schema handling, diagnostics provider. |
| `sqlite` | Partial/runtime backend support | Metadata DB uses SQLite. User DB support exists in some backend paths, but the current frontend connection form does not expose it. Treat frontend support as incomplete unless verified. |
| `oracle` | Stub only | `DbType` and `OracleDiagnosticsProvider` exist, but `ConnectionKind`, JDBC URL, UI, driver dependency, schema discovery, and SQL execution support are not complete. |
| `sqlserver` | Stub/legacy enum only | `DbType` and `QueryApplicationService` mapping exist, but the main connection kind, JDBC URL, driver, UI, schema discovery, and diagnostics are not complete. |

ER Inspector follows this matrix: `mysql`, `postgresql` / `postgres`, and `h2`
are supported through JDBC `DatabaseMetaData.getImportedKeys`; `sqlite` remains
partial because the frontend connection form is not wired for user SQLite
connections; `oracle` and `sqlserver` are explicitly unsupported and must return
structured `dialect_unsupported` guidance instead of a fake empty ER graph.

ER Designer follows this DDL matrix: `mysql`, `postgresql` / `postgres`, and
`h2` generate day-1 DDL for `CREATE TABLE`, `ALTER ADD COLUMN`, `ALTER ADD FK`,
and `CREATE INDEX`; `sqlite` is CREATE-only for table/index generation and must
return `SkippedOp` for ALTER variants; `oracle` and `sqlserver` are explicitly
unsupported with `dialect_unsupported`. DROP, ALTER COLUMN type changes, and
RENAME are N/A for day-1 automated generation because they are always returned
as `SkippedOp` with `day1_unsupported`; users must write that SQL manually in
`query_editor` and execute it through the existing L2/L3 guarded SQL flow.

### Feature Compatibility Matrix

| Feature | Compatibility notes |
|---|---|
| ER Tabs (Inspector + Designer) | Inspector: mysql / postgresql / h2 fully via JDBC `getImportedKeys`; sqlite incomplete frontend; oracle / sqlserver `dialect_unsupported`. Designer day-1 DDL generation: mysql / postgresql / h2 emit CREATE TABLE / ALTER ADD COLUMN / ALTER ADD FK / CREATE INDEX; sqlite is CREATE-only with all ALTER variants returning `SkippedOp`; oracle / sqlserver `dialect_unsupported`. DROP / ALTER COLUMN type / RENAME are always `SkippedOp` (`day1_unsupported`) regardless of dialect; users must write that SQL manually in the `query_editor` and run it through L2/L3 confirmation. |

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
| A — close partial/stub and common enterprise SQL | `sqlite`, `oracle`, `sqlserver` / `mssql`, `mariadb` | Prefer these when the goal is broad SQL Workbench coverage and familiar enterprise databases. `sqlite` needs frontend completion; `oracle` and `sqlserver` already have partial/stub traces but are not first-class. |
| B — analytics / OLAP SQL engines | `apache_doris` / `doris`, `starrocks`, `clickhouse`, `hive`, `trino`, `presto`, `duckdb` | Validate JDBC behavior, catalog/schema semantics, splitter safety, and whether diagnostics can be real or must return structured unsupported. |
| C — domestic / enterprise compatibility | `gaussdb`, `opengauss`, `dameng` / `dm` / `dm8`, `kingbase` / `kingbasees`, `oceanbase`, `tidb` | Do not assume PostgreSQL/MySQL compatibility is enough. Each kind needs explicit driver, URL, catalog/schema, SQL dialect, and risk-analysis decisions. |
| D — cloud warehouses / lakehouse SQL | `snowflake`, `bigquery`, `redshift`, `databricks_sql` | Watch for non-standard authentication, warehouse/project/dataset fields, JDBC driver redistribution limits, billing-sensitive metadata scans, and result-limit semantics. |
| E — non-SQL or semi-SQL sources | `mongodb`, `elasticsearch`, `opensearch`, optionally `redis` only if product scope expands beyond SQL | These require a separate read/query contract and should not be forced through fake SQL execution. Mutation and schema semantics must be designed before implementation. |

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
  supports `mysql`, `postgresql` / `postgres`, and `h2` via JDBC
  `getImportedKeys`; `sqlite` follows the partial frontend-support status above;
  `oracle` and `sqlserver` are unsupported with `dialect_unsupported` aiHint.
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
- Schema/database selector visibility.
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
