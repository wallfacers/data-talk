# Data Source Coverage: SQLite Design

Date: 2026-04-30
Status: Implemented (2026-05-01)

## 1. Purpose

SQLite was Wave A because the repository already contained backend and runtime
traces for `sqlite`, while the user-facing connection UI and compatibility
claims were incomplete. This design defined how SQLite could become first-class
for user database files through a file-scoped model.

The child implementation plan executed on 2026-05-01. SQLite is now exposed in
the frontend, verified as a first-class file-scoped user data source, and the
current support snapshot lives in
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).

## 2. Compatibility Gate Application

The mandatory gate is
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
SQLite child implementation must apply these gate areas:

- Current support snapshot: applicable; the child plan completed on 2026-05-01
  and upgraded `sqlite` to first-class file-scoped support.
- Canonical naming and alias normalization: applicable; persist `sqlite`.
- Backend connection, JDBC URL, driver, connection test, metadata, SQL
  execution, result normalization, splitter, risk guard, diagnostics:
  applicable.
- Frontend connection form, default fields, picker, Query Editor context,
  formatter, outline, diagnostics UI, and i18n: applicable.
- AI/MCP action schemas, runtime `AGENTS.md`, tool naming, and prompt contract
  tests: applicable.
- ER Inspector and ER Designer: applicable to the existing matrix; Inspector
  must be verified through user SQLite connections before being claimed,
  Designer remains CREATE-only for generated DDL and returns `SkippedOp` for
  unsupported ALTER variants.

No gate section is N/A for SQLite because the kind already touches backend,
frontend, SQL workbench, diagnostics, ER, and runtime prompt behavior.

## 3. Support Statement

Target outcome: first-class user SQLite database-file support.

At the end of implementation, DataTalk must be able to create, edit, test,
select, and delete a SQLite connection; list bounded metadata for the selected
file; run guarded Query Editor SQL; run analytical `datatalk_execute_sql` on
read-only statements; return structured unsupported diagnostics for unavailable
capabilities; and keep ER Designer CREATE-only behavior honest.

SQLite remains partial until those behaviors pass the child plan verification.

## 4. Kind Naming

- Canonical kind: `sqlite`.
- Accepted aliases: none for new input.
- Legacy aliases: none known from the current scan; if implementation discovers
  a persisted legacy value, normalize it before routing and document it in the
  gate.
- Persistence: store `sqlite` in `ConnectionRecord.kind()`, API payloads, and
  generated frontend types.
- Frontend label: `SQLite`.
- MCP-visible schema value: `sqlite` only after backend and frontend support is
  real.

## 5. Connection And Persistence

SQLite connection configuration must model a database file, not a network
server.

- Driver artifact: keep using the existing SQLite JDBC dependency already used
  for metadata persistence unless implementation proves a separate runtime
  dependency is required.
- Driver class: `org.sqlite.JDBC`.
- URL shape: `jdbc:sqlite:<databaseName>`.
- `databaseName` meaning: SQLite file path or the exact memory token
  `:memory:`. It must not be presented as a server database name. In the
  current backend model, `:memory:` is ephemeral per JDBC connection, so the
  UI and runtime prompt must present it as a temporary test target rather than
  a durable working database.
- Host and port: not meaningful for SQLite. The backend may keep existing DTO
  fields for compatibility, but frontend labels and validation must not imply a
  remote host requirement.
- Username/password: not required for normal SQLite.
- Metadata DB migration: not expected for day-1 SQLite completion because
  `database_name` can represent the file path. If per-kind extra fields are
  added later, update migrations and `docs/generated/db-schema.md`.

## 6. Catalog, Database, Schema, And Target Resolution

SQLite has a file-scoped database model.

- DataTalk `database`: the active SQLite file path or `:memory:` token.
- DataTalk `schema`: normally null for user SQLite.
- Catalog: none for normal user flows.
- `use xxx`: may match a saved connection name or SQLite file target, but it
  must not claim server-side database switching inside an open SQLite
  connection.
- Target discovery: list the selected connection and file-scoped target only;
  do not synthesize PostgreSQL-style schemas.
- System objects: filter `sqlite_%` internal objects from table discovery unless
  the request explicitly asks for system metadata.
- Case sensitivity and quoting: keep SQLite identifier behavior documented in
  tests; quoted identifiers preserve names and unquoted matching is
  case-insensitive for ordinary ASCII identifiers.

## 7. SQL Execution

Execution uses the existing guarded SQL path.

- Connection context: file-scoped; no `setCatalog` and no schema search path.
- Multi-statement execution: allowed only to the extent covered by splitter and
  risk tests.
- Transactions: reuse existing `SqlExecuteService` transaction behavior for
  multi-statement workbench execution, with SQLite locking errors surfaced in
  markdown diagnostics.
- DML batching: verify whether current JDBC batch behavior is correct for
  SQLite before enabling DML optimization claims.
- Max row behavior: verify `Statement.setMaxRows` or enforce result truncation
  through existing result bounds.
- Result normalization: cover SQLite dynamic typing for integer, real, text,
  blob, null, numeric-like text, and timestamp-like text values.

## 8. SQL Splitter And Risk Guard

SQLite may use the generic splitter only after explicit tests prove the day-1
script shape is safe.

Required splitter coverage:

- semicolons in single and double quoted strings;
- `--` and `/* ... */` comments;
- SQLite pragmas;
- common `CREATE TABLE`, `CREATE INDEX`, `INSERT`, `UPDATE`, `DELETE`, and
  `SELECT` scripts;
- behavior for trigger bodies if day-1 implementation supports trigger scripts.

Risk guard coverage must include:

- L1: `SELECT`, `WITH`, `EXPLAIN QUERY PLAN`, and read-only metadata pragmas
  such as `PRAGMA table_info`, `database_list`, `index_list`, `index_info`,
  `index_xinfo`, `foreign_key_list`, `table_list`, `table_xinfo`,
  `compile_options`, `quick_check`, `integrity_check`, `page_count`,
  `freelist_count`, `application_id`, `user_version`, and `schema_version`.
- L2: `INSERT`, `UPDATE` with predicate, `DELETE` with predicate,
  `CREATE TABLE`, `CREATE INDEX`.
- L3: `DROP`, `ALTER TABLE`, `VACUUM`, `ATTACH`, `DETACH`, `REINDEX`,
  unqualified destructive statements, and broad `PRAGMA` writes.

Chat-path `datatalk_execute_sql` remains read-only and must not bypass L2/L3
Workbench confirmation.

## 9. Schema Discovery And ER Features

`datatalk_read_schema` must support bounded discovery and explicit describe for
SQLite user files.

- Discover mode: use bounded `DatabaseMetaData` or SQLite catalog queries,
  apply `limit`, `cursor`, and `pattern`, and filter internal `sqlite_%`
  objects by default.
- Describe mode: require explicit table names and preserve large-schema bounds.
- Column metadata: include names, declared types, nullable information when
  available, and primary-key hints when available.
- ER Inspector: can be claimed only after a user SQLite connection can discover
  foreign-key metadata through the normal connection stack.
- ER Designer: keep existing CREATE-only DDL behavior; ALTER variants remain
  structured skipped operations.

## 10. Diagnostics

SQLite diagnostics must be honest.

- EXPLAIN: `EXPLAIN QUERY PLAN` can be considered only after provider tests
  normalize the returned rows into the existing diagnostic model.
- Index hints: unsupported unless a tested recommendation strategy exists.
- Lock info: structured unsupported for day-1 unless a safe file-lock
  diagnostic is designed.
- Pool status: structured unsupported if the existing Hikari/pool state cannot
  provide SQLite-specific value.
- Table space: structured unsupported unless file size and page metrics are
  implemented with clear privilege and path-safety bounds.
- Terminate session and optimize table: structured unsupported unless a later
  plan defines safe SQLite-specific behavior.

Unsupported diagnostics must return the existing structured unsupported shape,
not fake empty success.

## 11. Frontend

This design applies [client/DESIGN.md](../../client/DESIGN.md):

- use semantic tokens in connection form, picker, Query Editor context controls,
  diagnostics states, and unsupported states;
- keep Chat and Workbench in one visual system;
- treat Stage state as global workbench state;
- preserve visible focus, keyboard navigation, disabled states, and accessible
  names;
- add every user-visible label, helper, error, and unsupported message through
  i18n.

Required frontend decisions:

- Add `sqlite` to generated or hand-written connection types only after backend
  verification.
- Add SQLite to the settings connection form and data-source picker with label
  `SQLite`.
- Replace host/port-first form assumptions for SQLite with a file path or
  `:memory:` field mapped to `databaseName`.
- Keep username/password optional and hidden or clearly secondary for normal
  SQLite.
- Query Editor context should show a file-scoped database label and hide schema
  controls by default.
- Formatter language may use the generic `sql` formatter unless a SQLite
  dialect mapping is introduced and tested.
- SQL outline should include SQLite keywords and avoid false high-risk hints for
  read-only pragmas.

## 12. AI/MCP And Runtime Prompt

Action schemas may expose `sqlite` only when the backend can create, test,
select, read schema, and execute guarded SQL for user SQLite connections.

For connection-management tools, `datatalk_create_connection` and
`datatalk_update_connection_confirmable` must require only `name` / `kind`
plus `databaseName` for `kind=sqlite`; `host`, `port`, `username`, and
`password` remain required only for non-SQLite kinds.

Runtime `AGENTS.md` must state:

- SQLite context is file-scoped.
- Do not ask the model to switch SQLite schemas or cross-database catalogs.
- Use `datatalk_list_connection_targets` and `datatalk_resolve_use_target` for
  saved connection matching, not for server catalog discovery.
- Diagnostics may be structured unsupported.
- DDL/DML still goes through the Workbench L2/L3 flow.

Prompt contract tests must prove every SQLite capability mentioned in
`AGENTS.md` is backed by a real action schema or documented unsupported path.

## 13. Acceptance And Verification

Automated gates:

- Backend tests for `JdbcUrlBuilder`, `ConnectionKind`, connection test,
  target discovery, `resolve_use_target`, `datatalk_read_schema`,
  `SqlExecuteService`, `ExecuteSqlAction`, splitter, risk analyzer, result
  normalization, diagnostics unsupported or implementation, and ER behavior.
- Frontend tests for connection form, data-source picker, Query Editor context,
  formatter mapping, SQL outline, diagnostics unsupported rendering, and i18n.
- Prompt and MCP contract tests for action schemas and runtime `AGENTS.md`.
- `cd server && mvn compile -q`.
- `cd client && npx tsc --noEmit`.

Manual or integration smoke:

- create SQLite connection for a file path;
- create SQLite `:memory:` connection if day-1 support includes memory mode,
  but treat it as a temporary test target and do not assume state survives
  separate JDBC connections;
- test connection;
- list targets;
- read schema discovery and explicit table describe;
- run Query Editor `SELECT`;
- run analytical `datatalk_execute_sql`;
- verify L2/L3 Workbench confirmation for mutation;
- run diagnostics or verify structured unsupported;
- verify ER Designer CREATE-only behavior remains documented.

## 14. Open Questions

No question blocks this child artifact. The implementation review must decide
whether day-1 support includes trigger scripts; if it does, the splitter must
cover trigger bodies before SQLite leaves partial status.
