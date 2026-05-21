# Data Source Coverage: Oracle Design

Date: 2026-04-30
Status: Draft for review

## 1. Purpose

Oracle is Wave A because the repository already has `DbType.ORACLE`,
`OracleDiagnosticsProvider`, and explicit ER unsupported behavior, but the
actual connection stack is not complete. This design defines the work required
to turn the current stub state into first-class Oracle support without exposing
UI-only, prompt-only, or diagnostics-only claims.

This artifact does not expose Oracle as a supported data source. Oracle support
remains stub-only until the child implementation plan is executed and verified.

## 2. Compatibility Gate Application

The mandatory gate is
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
Oracle child implementation must apply these gate areas:

- Current support snapshot: applicable; keep `oracle` stub-only until all child
  checks pass.
- Canonical naming and alias normalization: applicable; persist `oracle`.
- Backend connection kind, URL builder, connection test, driver packaging,
  metadata, target resolution, SQL execution, result normalization, splitter,
  risk guard, diagnostics, and i18n errors: applicable.
- Frontend connection form, picker, default port, service name/SID fields,
  Query Editor context, formatter, outline, diagnostics UI, and i18n:
  applicable.
- AI/MCP action schemas, runtime `AGENTS.md`, tool naming, and prompt contract
  tests: applicable.
- ER Inspector and ER Designer: applicable; keep structured unsupported until
  Oracle ER metadata and DDL generation are explicitly implemented.

No gate section is N/A for Oracle because a first-class Oracle implementation
touches every compatibility area.

## 3. Support Statement

Target outcome: first-class Oracle support after closing the current stub gap.

At the end of implementation, DataTalk must be able to create, edit, test,
select, and delete Oracle connections; discover owners/schemas and tables;
resolve session context; execute guarded SQL; normalize Oracle JDBC values;
split supported SQL scripts safely; classify Oracle-specific risks; expose real
or structured unsupported diagnostics; and keep runtime AI guidance honest.

Oracle remains stub-only until those behaviors pass the child plan verification.

## 4. Kind Naming

- Canonical kind: `oracle`.
- Accepted aliases: none for day-1.
- Legacy aliases: none known from the current scan. If implementation discovers
  an existing persisted alias, normalize it before persistence and document it
  in the gate.
- Persistence: store `oracle` in `ConnectionRecord.kind()`, API payloads,
  generated frontend types, and MCP schemas.
- Frontend label: `Oracle`.
- MCP-visible schema value: `oracle` only after the connection, metadata, SQL,
  diagnostics, frontend, and prompt paths are verified.

## 5. Connection And Persistence

Oracle connection settings must model Oracle's service and instance concepts
directly.

- Driver artifact: implementation must choose an Oracle JDBC artifact that can
  be redistributed with DataTalk, document the license decision, and pin the
  version in the relevant Maven module.
- Driver class: implementation must verify the class expected by the chosen
  driver.
- Default port: `1521`.
- URL shapes: service-name and SID forms must be represented explicitly.
- Required fields: host, port, username, password, and exactly one of service
  name or SID unless the user supplies a complete JDBC property set allowed by
  the approved implementation.
- Optional fields: role and driver properties if the persistence model supports
  them.
- Persistence: do not overload `databaseName` with multiple Oracle concepts
  unless the implementation documents a precise mapping. If service name, SID,
  role, or properties require new metadata DB columns, add a Flyway migration
  and update `docs/generated/db-schema.md`.
- Connection test: use a driver-appropriate lightweight validation such as
  `Connection.isValid(...)` or a bounded validation query, with localized
  structured errors.

## 6. Catalog, Database, Schema, And Target Resolution

Oracle uses owner/schema semantics rather than MySQL-style databases.

- DataTalk `database`: service name or connection database identity only if
  that mapping is approved by implementation. It must not be used as an owner
  selector.
- DataTalk `schema`: Oracle owner/schema selected for metadata and unqualified
  table resolution.
- Catalog: normally null for Oracle metadata calls.
- `use xxx`: may resolve to a connection or schema/owner in the active
  connection. It must report ambiguity between connection names and schema
  names.
- Context application: implementation must choose and test `setSchema`,
  `ALTER SESSION SET CURRENT_SCHEMA`, or no context setter based on actual
  driver behavior.
- System schema filtering: hide common system owners by default, including
  Oracle-maintained schemas, while allowing explicit discovery when requested.
- Case and quoting: unquoted identifiers normalize to upper case; quoted
  identifiers preserve case and require exact matching.

## 7. SQL Execution

Oracle SQL execution must run through the existing guarded SQL workbench path.

- Context: apply selected schema through the approved context mechanism before
  execution.
- Multi-statement support: do not enable broad script execution until splitter
  behavior covers Oracle delimiters and procedural blocks.
- Transactions: preserve existing Workbench confirmation flow and transaction
  behavior for L2/L3 statements where the driver supports rollback.
- Batch DML: verify Oracle JDBC batch semantics before reusing generic DML
  batching claims.
- Result values: normalize Oracle NUMBER, DATE, TIMESTAMP, TIMESTAMP WITH TIME
  ZONE, CLOB, BLOB, RAW, ROWID, XML, and driver-specific objects into JSON-safe
  values.
- Connection errors: format ORA error codes in markdown diagnostics without
  leaking passwords or full connection strings.

## 8. SQL Splitter And Risk Guard

The generic splitter is not automatically acceptable for Oracle.

Required splitter decisions:

- plain SQL statements separated by semicolons;
- semicolons in strings and comments;
- PL/SQL `BEGIN ... END;` blocks;
- `CREATE OR REPLACE PROCEDURE/FUNCTION/PACKAGE/TRIGGER` blocks;
- slash terminator handling when scripts use SQL*Plus-style `/` lines.

Risk guard coverage must include:

- L1: `SELECT`, `WITH`, bounded metadata queries, `EXPLAIN PLAN FOR` only if
  diagnostics execution is reviewed.
- L2: `INSERT`, `UPDATE` with predicate, `DELETE` with predicate, `MERGE`,
  `CREATE INDEX`, ordinary `ALTER SESSION` context changes.
- L3: `DROP`, `TRUNCATE`, broad `ALTER`, `GRANT`, `REVOKE`, `CALL`, anonymous
  `BEGIN` blocks with side effects, user/session management, tablespace
  changes, and broad maintenance commands.

Chat-path `datatalk_execute_sql` remains read-only and must not accept Oracle
L2/L3 execution.

## 9. Schema Discovery And ER Features

Oracle `datatalk_read_schema` must support bounded discovery and explicit
describe.

- Discover mode: query metadata with `limit`, `cursor`, and `pattern`; default
  to the selected schema/owner; filter system schemas by default.
- Describe mode: require explicit tables and owner/schema context.
- Column metadata: include Oracle data type, precision, scale, nullable,
  default, comments when safe, and primary/foreign key metadata when supported.
- ER Inspector: keep `dialect_unsupported` until Oracle foreign-key discovery
  is implemented and verified through the normal connection stack.
- ER Designer: keep `dialect_unsupported` until Oracle DDL generation has a
  separate approved design.

## 10. Diagnostics

Diagnostics must distinguish existing stubs from real Oracle support.

- EXPLAIN: decide whether to implement `EXPLAIN PLAN FOR` plus
  `DBMS_XPLAN.DISPLAY`, including cleanup of plan-table state and privilege
  requirements.
- Index hints: unsupported unless a tested recommendation strategy can read and
  normalize plan output.
- Lock info: structured unsupported unless a privilege-aware query is designed.
- Pool status: structured unsupported unless the existing pool data can be
  reported honestly for Oracle connections.
- Table space: structured unsupported unless tablespace queries, privileges,
  units, and recommendations are implemented.
- Terminate session and optimize table: structured unsupported unless a later
  plan designs safe confirmable Oracle mutations.

Unsupported diagnostics must return the existing structured unsupported shape.

## 11. Frontend

This design applies [client/DESIGN.md](../../client/DESIGN.md):

- use semantic tokens for connection forms, warnings, diagnostics, and
  unsupported states;
- keep Chat and Workbench as one visual system;
- keep Stage state global;
- preserve visible focus, keyboard access, disabled states, and accessible
  names;
- route all user-visible strings through i18n.

Required frontend decisions:

- Add Oracle to the connection form and picker only after backend support is
  real.
- Show host, port, username, password, and a segmented or equivalent selection
  between service name and SID.
- Do not hide service name, SID, role, or JDBC properties in unrelated generic
  fields.
- Query Editor context should present schema/owner selection, not a MySQL-style
  database dropdown.
- Formatter mapping may use a safe generic SQL language until a tested Oracle
  formatter exists.
- SQL outline must recognize Oracle-specific statements and risk hints.
- Diagnostics UI must render unsupported, permission denied, and real EXPLAIN
  states without fake empty success.

## 12. AI/MCP And Runtime Prompt

MCP schemas may expose `oracle` only when runtime behavior is complete.

Runtime `AGENTS.md` must state:

- Oracle schema means owner/current schema, not a server database.
- Use `datatalk_resolve_use_target` before switching context.
- Do not claim ER support until Oracle ER metadata support ships.
- Do not claim diagnostics beyond the provider's real capabilities.
- Oracle DDL/DML must go through the Workbench L2/L3 confirmation flow.

Prompt contract tests must prove every Oracle capability mentioned in
`AGENTS.md` is backed by real action behavior or structured unsupported output.

## 13. Acceptance And Verification

Automated gates:

- Backend tests for kind normalization, URL builder, driver loading, connection
  test, connection persistence, target discovery, `resolve_use_target`,
  `datatalk_read_schema`, `SqlExecuteService`, `ExecuteSqlAction`, splitter,
  risk analyzer, result normalization, diagnostics provider or unsupported
  behavior, and ER unsupported behavior.
- Frontend tests for connection form, data-source picker, Query Editor context,
  formatter, outline, diagnostics states, accessibility-relevant controls, and
  i18n.
- Prompt and MCP contract tests for schemas and runtime `AGENTS.md`.
- `cd server && mvn compile -q`.
- `cd client && npx tsc --noEmit`.

Manual or integration smoke:

- create Oracle connection with service name;
- create Oracle connection with SID if day-1 includes SID;
- test connection;
- list schemas/owners;
- resolve matched, ambiguous, and missing `use` targets;
- read schema discovery and describe;
- run Query Editor `SELECT`;
- run analytical `datatalk_execute_sql`;
- exercise L2/L3 Workbench confirmation;
- run diagnostics or verify structured unsupported.

## 14. Open Questions

No question blocks this child artifact. Implementation approval must settle the
driver redistribution decision and the persistence shape for service name, SID,
role, and optional JDBC properties before code changes begin.
