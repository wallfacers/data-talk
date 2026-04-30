# Data Source Coverage: MariaDB Design

Date: 2026-04-30
Status: Draft for review

## 1. Purpose

MariaDB is Wave A because DataTalk already has first-class MySQL support, but
MariaDB must not appear by accident as a hidden MySQL alias. This design defines
explicit MariaDB support as its own data-source kind unless the approved
implementation intentionally chooses and documents a different normalization
model.

This artifact does not expose MariaDB as supported. MariaDB remains unsupported
until the child implementation plan is executed and verified.

## 2. Compatibility Gate Application

The mandatory gate is
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
MariaDB child implementation must apply these gate areas:

- Current support snapshot: applicable; MariaDB stays a roadmap candidate until
  all child checks pass.
- Canonical naming and alias normalization: applicable; persist `mariadb`
  unless an approved design explicitly normalizes to another canonical kind.
- Backend connection kind, URL builder, driver packaging, connection test,
  metadata, target resolution, SQL execution, result normalization, splitter,
  risk guard, diagnostics, and i18n errors: applicable.
- Frontend connection form, picker, default port, SSL fields, Query Editor
  context, formatter, outline, diagnostics UI, and i18n: applicable.
- AI/MCP schemas, runtime `AGENTS.md`, tool naming, and prompt contract tests:
  applicable.
- ER Inspector and ER Designer: applicable; do not inherit MySQL ER claims
  without MariaDB verification.

No gate section is N/A for MariaDB because first-class support touches every
database compatibility area.

## 3. Support Statement

Target outcome: explicit MariaDB support, not accidental MySQL compatibility.

At the end of implementation, DataTalk must be able to create, edit, test,
select, and delete MariaDB connections; discover databases and tables; resolve
context; execute guarded SQL; split supported scripts; classify MariaDB-specific
risk; normalize returned values; expose real or structured unsupported
diagnostics; update frontend UI honestly; and keep MCP/runtime prompt claims
aligned with implementation.

MariaDB remains unsupported until those behaviors pass the child plan
verification.

## 4. Kind Naming

- Canonical kind: `mariadb`.
- Accepted aliases: none for day-1 unless implementation approval explicitly
  adds aliases.
- MySQL relationship: do not persist MariaDB as `mysql` unless a later approved
  design intentionally chooses that model and documents user-visible
  consequences.
- Persistence: store `mariadb` in `ConnectionRecord.kind()`, API payloads,
  generated frontend types, and MCP schemas.
- Frontend label: `MariaDB`.
- MCP-visible schema value: `mariadb` only after backend, frontend, and prompt
  paths are verified.

## 5. Connection And Persistence

MariaDB connection settings are close to MySQL but must be verified.

- Driver artifact: implementation must choose MariaDB Connector/J or MySQL
  Connector/J compatibility, document license/runtime packaging, and pin the
  version in the relevant Maven module.
- Driver class: implementation must verify the driver class for the chosen
  artifact.
- Default port: `3306`.
- URL shape: `jdbc:mariadb://<host>:<port>/<database>` if MariaDB Connector/J is
  chosen, or a documented alternative if MySQL Connector/J is intentionally used.
- Required fields: host, port, username, password, and optional default
  database according to approved day-1 behavior.
- Optional fields: SSL mode, trust store, socket/Unix socket only if supported
  by approved persistence, connection timeout, and extra JDBC properties.
- Persistence: the existing `databaseName` can represent the default database
  if tests prove MariaDB metadata and execution use it consistently. Add
  migrations and `docs/generated/db-schema.md` updates if new fields are added.
- Connection test: use a driver-appropriate lightweight validation and localized
  structured errors.

## 6. Catalog, Database, Schema, And Target Resolution

MariaDB follows MySQL-like database/catalog semantics, but the implementation
must prove reuse is valid.

- DataTalk `database`: MariaDB database/catalog.
- DataTalk `schema`: normally null for day-1 unless a future plan introduces a
  schema-like concept.
- Catalog: JDBC catalog maps to database.
- `use xxx`: may resolve to a connection or database in the active connection;
  ambiguous matches must return suggestions.
- Context application: likely `setCatalog` or URL database selection, but the
  implementation must test the chosen behavior.
- System filtering: hide system databases such as `mysql`, `information_schema`,
  `performance_schema`, and `sys` by default.
- Case and quoting: cover backtick identifiers and case-sensitive filesystem
  behavior in tests where relevant.

## 7. SQL Execution

MariaDB execution must use the existing guarded Workbench path.

- Context: apply selected database/catalog through the approved mechanism.
- Multi-statement support: may reuse MySQL behavior only after MariaDB-specific
  splitter tests pass.
- Transactions: preserve existing L2/L3 confirmation and rollback behavior.
- Batch DML: reuse MySQL DML batching only after MariaDB tests prove correct
  behavior for generated keys, update counts, and multi-value rewrite limits.
- Result values: normalize MariaDB date/time, decimal, JSON, enum/set, unsigned
  integers, bit, binary, and driver-specific objects into JSON-safe values.
- Errors: format MariaDB driver messages in markdown diagnostics without
  leaking secrets.

## 8. SQL Splitter And Risk Guard

MariaDB may reuse `MySqlSqlStatementSplitter` only after explicit coverage.

Required splitter coverage:

- quoted strings, backtick identifiers, comments, and escaped characters;
- `DELIMITER` stored-program scripts if day-1 supports them;
- MariaDB-specific procedural syntax when included in day-1 scripts;
- behavior for statements where MariaDB differs from MySQL.

Risk guard coverage must include:

- L1: `SELECT`, `WITH`, `SHOW`, `DESCRIBE`, `EXPLAIN`.
- L2: `INSERT`, `UPDATE` with predicate, `DELETE` with predicate,
  `CREATE INDEX`, scoped `ALTER TABLE`, and safe maintenance reads.
- L3: `DROP`, `TRUNCATE`, broad `ALTER`, `GRANT`, `REVOKE`, `KILL`,
  `OPTIMIZE TABLE`, `ANALYZE TABLE`, `LOAD DATA`, replication commands, and
  file or plugin commands.

Chat-path `datatalk_execute_sql` remains read-only and must not bypass the
Workbench confirmation flow.

## 9. Schema Discovery And ER Features

MariaDB `datatalk_read_schema` must support bounded discovery and explicit
describe.

- Discover mode: query databases/tables with `limit`, `cursor`, and `pattern`;
  filter system databases by default.
- Describe mode: require explicit tables and use selected database context.
- Column metadata: include type, nullable, default, primary/foreign key, index
  hints, generated columns, and comments when available.
- ER Inspector: can reuse the MySQL relation discovery path only after MariaDB
  tests prove `DatabaseMetaData.getImportedKeys` behavior is correct.
- ER Designer: can reuse MySQL DDL generation only after MariaDB-specific DDL
  tests cover CREATE TABLE, ALTER ADD COLUMN, ALTER ADD FK, and CREATE INDEX.

## 10. Diagnostics

Diagnostics must prove MySQL reuse or provide a MariaDB-specific provider.

- EXPLAIN: verify whether MySQL provider SQL works against MariaDB and whether
  plan output maps cleanly to the existing diagnostic model.
- Index hints: verify recommendations against MariaDB plan output.
- Lock info: structured unsupported unless MySQL lock queries work and are
  privilege-safe for MariaDB.
- Pool status: structured unsupported unless pool state is meaningful.
- Table space: verify information schema queries and units before reusing MySQL
  behavior.
- Terminate session and optimize table: confirmable mutations require
  MariaDB-specific preview SQL, token handling, and permission tests.

Unsupported diagnostics must return structured unsupported responses.

## 11. Frontend

This design applies [client/DESIGN.md](../../client/DESIGN.md):

- use semantic tokens for forms, warnings, diagnostics, and unsupported states;
- keep Chat and Workbench as one visual system;
- keep Stage state global;
- preserve keyboard access, focus rings, disabled states, and accessible names;
- route all user-visible text through i18n.

Required frontend decisions:

- Add MariaDB to the connection form and picker only after backend support is
  real.
- Show label `MariaDB`, default port `3306`, and any approved SSL/property
  fields.
- Query Editor context may mirror MySQL database-only behavior only after
  backend target discovery tests pass.
- Formatter mapping may use `mysql` only after SQL formatting tests cover
  MariaDB examples; otherwise use safe generic `sql`.
- SQL outline may reuse MySQL keywords only with MariaDB-specific additions and
  risk hint coverage.
- Diagnostics UI must distinguish MariaDB from MySQL when capabilities differ.

## 12. AI/MCP And Runtime Prompt

MCP schemas may expose `mariadb` only when runtime behavior is complete.

Runtime `AGENTS.md` must state:

- MariaDB is a separate kind when exposed.
- Do not tell users to create a MySQL connection for MariaDB unless the product
  intentionally chooses that compatibility model.
- Use `datatalk_resolve_use_target` before switching databases.
- Do not claim diagnostics or ER support beyond implemented capabilities.
- DDL/DML must go through Workbench confirmation.

Prompt contract tests must prove MariaDB capability claims map to real action
behavior or structured unsupported output.

## 13. Acceptance And Verification

Automated gates:

- Backend tests for kind normalization, URL builder, driver loading, connection
  test, target discovery, `resolve_use_target`, `datatalk_read_schema`,
  `SqlExecuteService`, `ExecuteSqlAction`, splitter, risk analyzer, result
  normalization, diagnostics provider or structured unsupported behavior, and
  ER behavior if reused.
- Frontend tests for connection form, picker, Query Editor context, formatter,
  outline, diagnostics states, accessibility-relevant controls, and i18n.
- Prompt and MCP contract tests for schemas and runtime `AGENTS.md`.
- `cd server && mvn compile -q`.
- `cd client && npx tsc --noEmit`.

Manual or integration smoke:

- create MariaDB connection;
- test connection;
- list databases;
- resolve matched, ambiguous, and missing targets;
- read schema discovery and describe;
- run Query Editor `SELECT`;
- run analytical `datatalk_execute_sql`;
- exercise L2/L3 confirmation;
- run diagnostics or verify structured unsupported;
- verify ER behavior if MySQL ER paths are reused.

## 14. Open Questions

No question blocks this child artifact. Implementation approval must settle the
driver choice, license/runtime packaging, whether any MySQL Connector/J
compatibility is allowed, and which MySQL paths can be reused only after
MariaDB-specific tests pass.
