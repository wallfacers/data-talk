# Data Source Coverage: Apache Doris Design

Date: 2026-05-01
Status: Draft for review

## 1. Purpose

Apache Doris is Wave B because it is a user-requested OLAP engine and exposes a
MySQL-compatible network protocol. This design defines explicit Doris support
under its own canonical kind instead of hiding Doris behind the existing MySQL
kind.

This artifact does not expose Doris as supported. Doris remains unsupported
until the child implementation plan is executed and verified.

## 2. Compatibility Gate Application

The mandatory gate is
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
Doris implementation must apply every database-type area:

- canonical naming and alias normalization;
- backend connection kind, URL builder, driver packaging, connection test,
  metadata, target resolution, SQL execution, result normalization, splitter,
  risk guard, diagnostics, and localized errors;
- frontend connection form, picker, default port, Query Editor context,
  formatter, outline, diagnostics UI, accessibility, and i18n;
- MCP schemas, runtime `AGENTS.md`, prompt contract tests, and tool naming;
- ER Inspector and ER Designer only after Doris-specific metadata and DDL tests.

No gate section is N/A because first-class Doris support touches every database
compatibility area.

## 3. Support Statement

Target outcome: first-class SQL Workbench support for Apache Doris through a
verified MySQL-protocol JDBC path.

At the end of implementation, DataTalk must create, edit, test, select, and
delete Doris connections; discover databases and tables; resolve context;
execute guarded SQL; split supported Doris scripts; classify Doris-specific
risk; normalize returned OLAP types; expose real or structured unsupported
diagnostics; update frontend UI honestly; and align MCP/runtime prompt claims.

Doris remains unsupported until those checks pass.

## 4. Kind Naming

- Canonical kind: `apache_doris`.
- Accepted alias: `doris`, normalized to `apache_doris` before persistence.
- Persistence: store `apache_doris` in `ConnectionRecord.kind()`, API payloads,
  generated frontend types, and MCP schemas.
- Frontend label: `Apache Doris`.
- MySQL relationship: Doris may use MySQL protocol tooling, but it must not be
  persisted or displayed as `mysql`.

## 5. Connection And Persistence

Design inputs from Apache Doris docs:

- Doris uses the MySQL network protocol.
- FE query port maps to the MySQL protocol service and defaults to `9030`.
- Doris docs show MySQL Connector/J style URL examples such as
  `jdbc:mysql://FE_IP:FE_PORT/demo?...`.

Required implementation decisions:

- Driver artifact: use MySQL Connector/J only after license/runtime packaging
  is accepted for DataTalk; MariaDB Connector/J must be evaluated as an
  alternative but is not assumed.
- Driver decision: implementation must produce an explicit decision record
  comparing MySQL Connector/J and MariaDB Connector/J for license, packaging,
  Doris protocol behavior, timeout properties, and known quirks before adding a
  Maven dependency.
- Driver class: expected `com.mysql.cj.jdbc.Driver` if MySQL Connector/J is
  chosen.
- URL shape: `jdbc:mysql://<feHost>:<feQueryPort>/<database>` with Doris-safe
  connection properties.
- Default port: `9030`.
- Required fields: host, port, username, password, optional default database.
- Optional fields: SSL mode, connection timeout, socket timeout, and extra JDBC
  properties only if existing persistence can represent them safely.
- Persistence: existing `databaseName` can represent the selected Doris
  database only after target discovery and execution tests prove consistency.

## 6. Catalog, Database, Schema, And Target Resolution

- DataTalk `database`: Doris database.
- DataTalk `schema`: null for day-1 unless Doris metadata proves a separate
  schema concept relevant to JDBC clients.
- Catalog: MySQL JDBC catalog behavior may map to Doris database, but this must
  be verified with `DatabaseMetaData` and `Connection#setCatalog`.
- `use xxx`: resolves Doris database names within the active connection.
- System filtering: hide internal/system databases and schemas that Doris
  reports for metadata or information schema queries.
- Case and quoting: cover backtick identifiers and Doris-specific keyword
  conflicts.

## 7. SQL Execution

Doris SQL execution must use the existing guarded Workbench path.

- Context: apply selected database through URL database, `USE`, or catalog only
  after tests prove the chosen mechanism.
- Multi-statement support: may reuse MySQL splitting only after Doris scripts,
  comments, strings, and `DELIMITER` behavior are tested.
- Transactions: do not assume MySQL transaction behavior; Doris OLAP mutations
  and DDL must use existing L2/L3 confirmation and rollback only where JDBC
  transaction semantics are proven.
- Batch DML: disabled or structured unsupported until Doris-specific insert and
  update-count behavior is verified.
- Result values: normalize large integers, decimals, JSON-like strings,
  date/time, binary values, and driver-specific objects into JSON-safe output.
- Errors: preserve Doris driver messages in markdown diagnostics without
  leaking credentials.

## 8. SQL Splitter And Risk Guard

Splitter choices:

- Preferred: reuse MySQL splitter only after Doris-specific tests pass.
- Fallback: dedicated Doris splitter if MySQL delimiter/procedural behavior is
  wrong.
- Conservative path: generic splitter for day-1 if stored-program scripts are
  explicitly unsupported.

Risk guard coverage must include:

- L1: `SELECT`, `WITH`, `SHOW`, `DESC`, `DESCRIBE`, `EXPLAIN`.
- L2: bounded `INSERT`, `UPDATE` with predicate, `DELETE` with predicate,
  `CREATE INDEX`, `ANALYZE`, and safe table maintenance only if Doris semantics
  are proven.
- L3: `DROP`, `TRUNCATE`, broad `ALTER`, user/role grants, load jobs, routine
  load, export, compaction, backend/frontend node changes, and cluster/system
  management SQL.

Chat-path `datatalk_execute_sql` remains read-only.

## 9. Schema Discovery And ER Features

- `datatalk_read_schema` must support bounded discover mode and explicit
  describe mode.
- Discovery must filter internal databases by default and support `limit`,
  `cursor`, and `pattern`.
- Describe mode must include columns, nullable/default metadata, key model,
  partition/distribution hints when available, comments, and indexes.
- ER Inspector is structured unsupported until Doris JDBC metadata proves
  imported-key behavior or another relation source is implemented.
- ER Designer DDL is structured unsupported until Doris-specific CREATE TABLE,
  ALTER ADD COLUMN, ALTER ADD FK, and CREATE INDEX generation tests exist.

## 10. Diagnostics

Diagnostics must be Doris-specific or structured unsupported:

- EXPLAIN: verify Doris `EXPLAIN` output and map it to DataTalk's diagnostic
  model.
- Index hints: structured unsupported unless Doris has actionable index or rollup
  guidance that can be computed safely.
- Lock info, pool status, table space: structured unsupported unless reliable
  Doris system tables and privileges are verified.
- Terminate session and optimize table: confirmable mutations only after Doris
  SQL and permission behavior are explicitly designed.

## 11. Frontend

This design applies [client/DESIGN.md](../../client/DESIGN.md):

- use semantic tokens for form states, warnings, diagnostics, and unsupported
  states;
- keep Chat and Workbench as one visual system;
- keep Stage state global;
- preserve keyboard access, focus rings, disabled states, and accessible names;
- route all user-visible text through i18n.

Required frontend decisions:

- Label: `Apache Doris`.
- Default port: `9030`.
- Context controls: database selector only for day-1 unless schema is proven.
- Formatter: use MySQL formatter only after Doris SQL examples format safely;
  otherwise use generic SQL.
- SQL outline: start from MySQL-like keywords but add Doris-specific management
  and load statements for risk hints.
- Diagnostics UI must render structured unsupported states explicitly.

## 12. AI/MCP And Runtime Prompt

MCP schemas may expose `apache_doris` only after backend and frontend support is
complete.

Runtime prompt rules must state:

- use `datatalk_resolve_use_target` before switching Doris databases;
- do not call Doris a MySQL database;
- do not claim ER, diagnostics, load-job, or cluster-management support unless
  implemented;
- keep DDL/DML in the Workbench confirmation flow.

Prompt contract tests must prove capability claims map to real behavior or
structured unsupported responses.

## 13. Acceptance And Verification

Automated gates:

- backend tests for alias normalization, URL building, driver loading,
  connection test, target discovery, schema read, SQL execution, splitter,
  risk analyzer, result normalization, diagnostics, ER structured unsupported,
  MCP schema, and runtime prompt;
- frontend tests for form fields, picker, Query Editor context, formatter,
  outline, diagnostics state, accessibility-relevant controls, and i18n;
- `cd server && mvn compile -q`;
- `cd client && npx tsc --noEmit`.

Manual or integration smoke:

- create and test a Doris connection;
- list databases and resolve matched, ambiguous, and missing targets;
- read schema discovery and explicit describe;
- run Query Editor `SELECT`;
- verify chat-path mutation blocking;
- verify diagnostics output or structured unsupported responses.
