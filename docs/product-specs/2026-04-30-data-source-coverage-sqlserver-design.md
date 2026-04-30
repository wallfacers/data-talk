# Data Source Coverage: SQL Server Design

Date: 2026-04-30
Status: Draft for review

## 1. Purpose

SQL Server is Wave A because `DbType.SQLSERVER` and legacy mapping traces exist,
but the main connection kind, JDBC URL, driver dependency, frontend exposure,
metadata discovery, diagnostics, and runtime prompt contracts are incomplete.
This design defines how DataTalk can add first-class SQL Server support without
treating the legacy enum as sufficient support.

This artifact does not expose SQL Server as supported. SQL Server support
remains stub/legacy until the child implementation plan is executed and
verified.

## 2. Compatibility Gate Application

The mandatory gate is
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
SQL Server child implementation must apply these gate areas:

- Current support snapshot: applicable; keep `sqlserver` stub/legacy until all
  checks pass.
- Canonical naming and alias normalization: applicable; persist `sqlserver`
  and accept `mssql` only through explicit normalization.
- Backend connection kind, JDBC URL, driver packaging, connection test,
  metadata, target resolution, SQL execution, result normalization, splitter,
  risk guard, diagnostics, and i18n errors: applicable.
- Frontend connection form, picker, default port, encryption/security fields,
  Query Editor context, formatter, outline, diagnostics UI, and i18n:
  applicable.
- AI/MCP schemas, runtime `AGENTS.md`, tool naming, and prompt contract tests:
  applicable.
- ER Inspector and ER Designer: applicable; keep structured unsupported until
  SQL Server ER metadata and DDL generation are explicitly implemented.

No gate section is N/A for SQL Server because first-class support touches every
database compatibility area.

## 3. Support Statement

Target outcome: first-class SQL Server support after closing the current
legacy/stub gap.

At the end of implementation, DataTalk must be able to create, edit, test,
select, and delete SQL Server connections; discover databases and schemas;
resolve context; execute guarded SQL; split supported scripts; classify
SQL Server-specific risk; normalize JDBC values; expose real or structured
unsupported diagnostics; and keep frontend and AI contracts honest.

SQL Server remains stub/legacy until those behaviors pass child plan
verification.

## 4. Kind Naming

- Canonical kind: `sqlserver`.
- Accepted aliases: `mssql` may be accepted only at an explicit normalization
  boundary before persistence and routing.
- Persistence: store `sqlserver` in `ConnectionRecord.kind()`, API payloads,
  generated frontend types, and MCP schemas.
- Frontend label: `SQL Server`.
- MCP-visible schema value: `sqlserver`; `mssql` must not leak as a persisted
  or prompt-preferred value.
- Invalid aliases: any unapproved values must return localized unsupported-kind
  errors.

## 5. Connection And Persistence

SQL Server connection settings must represent security and instance behavior
explicitly.

- Driver artifact: implementation must choose the Microsoft JDBC driver
  artifact, document license/runtime packaging, and pin the version in the
  relevant Maven module.
- Driver class: implementation must verify the class expected by the chosen
  driver.
- Default port: `1433`.
- URL shape: `jdbc:sqlserver://<host>:<port>;databaseName=<database>;...`
  or the driver-approved equivalent.
- Required fields: host, port, username, password, and database when the
  implementation requires a default catalog for day-1 use.
- Optional fields: instance name, encryption, trust server certificate, login
  timeout, application name, and extra JDBC properties when approved.
- Persistence: do not overload `databaseName` with instance name or security
  settings. Add metadata DB columns and update `docs/generated/db-schema.md` if
  those fields must be stored.
- Connection test: use driver-appropriate validation and preserve localized
  structured errors.

## 6. Catalog, Database, Schema, And Target Resolution

SQL Server has catalog/database plus schema semantics.

- DataTalk `database`: SQL Server database/catalog.
- DataTalk `schema`: SQL Server schema inside the selected database.
- Catalog: maps to database for JDBC metadata calls.
- `use xxx`: may resolve to a connection, database, or schema depending on the
  active connection and discovered targets; ambiguous matches must return
  suggestions.
- Context application: implementation must choose and test `setCatalog`,
  `setSchema`, SQL `USE`, or a combination based on Microsoft JDBC behavior.
- System filtering: hide system databases and schemas by default, including
  `master`, `model`, `msdb`, `tempdb`, `sys`, and `INFORMATION_SCHEMA`, while
  allowing explicit discovery when requested.
- Case and quoting: support bracket identifiers and quoted identifiers in tests.

## 7. SQL Execution

SQL Server execution must use the existing guarded Workbench path.

- Context: apply database/catalog and schema through the approved context
  mechanism before execution.
- Multi-statement support: do not support broad scripts until `GO` batch
  separator behavior is explicit and tested.
- Transactions: preserve existing L2/L3 confirmation and rollback behavior
  where the driver supports it.
- Batch DML: verify Microsoft JDBC batch behavior before reusing generic DML
  optimization claims.
- Result values: normalize SQL Server datetimeoffset, datetime2, money,
  uniqueidentifier, varbinary, XML, geography/geometry, decimal, and driver
  objects into JSON-safe values.
- Errors: format SQL Server error numbers and states in markdown diagnostics
  without leaking secrets.

## 8. SQL Splitter And Risk Guard

SQL Server cannot silently inherit the generic splitter for first-class script
support.

Required splitter decisions:

- single-statement support if no SQL Server splitter is implemented;
- semicolons inside strings and comments;
- bracket identifiers;
- `GO` batch separators with optional repeat counts if day-1 scripts support
  them;
- stored procedure and trigger bodies if day-1 scripts support them.

Risk guard coverage must include:

- L1: `SELECT`, `WITH`, bounded metadata queries, and approved `SHOWPLAN`
  reads.
- L2: `INSERT`, `UPDATE` with predicate, `DELETE` with predicate, `MERGE`,
  `CREATE INDEX`, and scoped `ALTER INDEX`.
- L3: `DROP`, `TRUNCATE`, broad `ALTER`, `BACKUP`, `RESTORE`, `EXEC` with side
  effects, permission changes, login/user management, `DBCC`, `KILL`, and
  database-level commands.

Chat-path `datatalk_execute_sql` remains read-only and must not bypass the
Workbench L2/L3 confirmation flow.

## 9. Schema Discovery And ER Features

SQL Server `datatalk_read_schema` must support bounded discovery and explicit
describe.

- Discover mode: query databases/schemas/tables with `limit`, `cursor`, and
  `pattern`; default to the selected database and schema when present.
- Describe mode: require explicit table names and schema context.
- Column metadata: include data type, length, precision, scale, nullable,
  default, identity, computed-column, primary-key, and foreign-key information
  when supported.
- ER Inspector: keep `dialect_unsupported` until SQL Server foreign-key
  discovery is implemented and verified through the normal connection stack.
- ER Designer: keep `dialect_unsupported` until SQL Server DDL generation has a
  separate approved design.

## 10. Diagnostics

Diagnostics must be real or structured unsupported.

- EXPLAIN/plan: decide whether to use `SET SHOWPLAN_XML`, `SET SHOWPLAN_TEXT`,
  or another safe plan path, including permission and session-setting cleanup.
- Index hints: unsupported unless plan output can be normalized and a tested
  recommendation strategy exists.
- Lock info: structured unsupported unless privilege-aware DMV queries are
  implemented.
- Pool status: structured unsupported unless the existing pool status is
  meaningful for SQL Server.
- Table space: structured unsupported unless database/file/table size queries,
  permissions, and units are implemented.
- Terminate session and optimize table: structured unsupported unless a later
  plan designs safe confirmable SQL Server mutations.

Unsupported diagnostics must return structured unsupported responses, not fake
empty success.

## 11. Frontend

This design applies [client/DESIGN.md](../../client/DESIGN.md):

- use semantic tokens for form, warning, diagnostics, and unsupported states;
- keep Chat and Workbench visually consistent;
- keep Stage state global;
- preserve keyboard access, focus rings, disabled states, and accessible names;
- route all user-visible text through i18n.

Required frontend decisions:

- Add SQL Server to the connection form and picker only after backend support is
  real.
- Show host, port, database, username, password, encryption, trust certificate,
  and optional instance/JDBC properties according to approved backend fields.
- Query Editor context must support database plus schema selection.
- Formatter mapping may use a safe generic SQL language unless a tested SQL
  Server formatter mapping is approved.
- SQL outline must recognize SQL Server keywords, `GO`, `EXEC`, `MERGE`, and
  high-risk database commands.
- Diagnostics UI must render unsupported, permission denied, and real plan
  states honestly.

## 12. AI/MCP And Runtime Prompt

MCP schemas may expose `sqlserver` only when backend and frontend behavior is
complete.

Runtime `AGENTS.md` must state:

- SQL Server context has database/catalog and schema.
- `mssql` is an input alias only; prefer `sqlserver`.
- Use `datatalk_resolve_use_target` before switching context.
- Do not claim ER or diagnostics support beyond implemented capabilities.
- SQL Server DDL/DML must go through Workbench confirmation.

Prompt contract tests must prove SQL Server capability claims map to real
action behavior or structured unsupported output.

## 13. Acceptance And Verification

Automated gates:

- Backend tests for alias normalization, connection kind, URL builder,
  connection test, persistence, target discovery, `resolve_use_target`,
  `datatalk_read_schema`, `SqlExecuteService`, `ExecuteSqlAction`, splitter,
  risk analyzer, result normalization, diagnostics provider or structured
  unsupported behavior, and ER unsupported behavior.
- Frontend tests for connection form, picker, Query Editor context, formatter,
  outline, diagnostics states, accessibility-relevant controls, and i18n.
- Prompt and MCP contract tests for schemas and runtime `AGENTS.md`.
- `cd server && mvn compile -q`.
- `cd client && npx tsc --noEmit`.

Manual or integration smoke:

- create SQL Server connection with encryption settings;
- create `mssql` alias input and verify persisted kind is `sqlserver`;
- test connection;
- list databases and schemas;
- resolve matched, ambiguous, and missing targets;
- read schema discovery and describe;
- run Query Editor `SELECT`;
- run analytical `datatalk_execute_sql`;
- exercise L2/L3 confirmation;
- run diagnostics or verify structured unsupported.

## 14. Open Questions

No question blocks this child artifact. Implementation approval must settle the
driver version/license, encryption defaults, instance-name persistence, and
whether day-1 script execution includes `GO` batches or only single statements.
