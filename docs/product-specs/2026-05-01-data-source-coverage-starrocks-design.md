# Data Source Coverage: StarRocks Design

Date: 2026-05-01
Status: Draft for review

## 1. Purpose

StarRocks is Wave B because it is a high-demand OLAP SQL engine with a native
JDBC driver and MySQL-like operational surface. This design defines explicit
StarRocks support as its own canonical data-source kind.

This artifact does not expose StarRocks as supported. StarRocks remains
unsupported until the child implementation plan is executed and verified.

## 2. Compatibility Gate Application

The mandatory gate is
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
StarRocks implementation must cover naming, connection handling, URL building,
driver packaging, metadata, target resolution, SQL execution, splitter, risk
guard, diagnostics, ER behavior, frontend UI, MCP schemas, runtime prompt, and
verification.

No gate section is N/A for first-class StarRocks support.

## 3. Support Statement

Target outcome: first-class SQL Workbench support through the native StarRocks
JDBC driver after catalog/database semantics are proven.

At completion, DataTalk must create/test/use StarRocks connections, resolve
catalog/database context, read schema safely, execute guarded SQL, classify
risk, render frontend controls, and expose real or structured unsupported
diagnostics. StarRocks remains unsupported until all child gates pass.

## 4. Kind Naming

- Canonical kind: `starrocks`.
- Accepted aliases: none for day-1.
- Persistence: store `starrocks` in backend records, API payloads, generated
  frontend types, and MCP schemas.
- Frontend label: `StarRocks`.
- Relationship to MySQL: do not persist or route StarRocks as `mysql`.

## 5. Connection And Persistence

Design inputs from StarRocks docs:

- Driver candidate: `com.starrocks:starrocks-connector-j`.
- Documented version at design time: `1.1.1`; implementation must re-check.
- URL shape: `jdbc:starrocks://<fe_host>:<fe_query_port>/<catalog>.<database>`.
- FE query port default: `9030`.
- Driver supports standard JDBC metadata APIs according to StarRocks docs.

Required implementation decisions:

- Driver class: verify from the selected artifact before implementation.
- Required fields: host, port, username, password, catalog, database.
- Optional fields: SSL, timeout, and extra JDBC properties only when persistence
  and security behavior are designed.
- Persistence: avoid overloading `databaseName` with `catalog.database` without
  an explicit mapping and migration decision.
- Connection test: use lightweight validation and localized errors.
- Day-1 empty-value rule: catalog defaults to `default_catalog`; database is
  required before URL construction and connection testing. If database is
  missing, return a localized validation error instead of guessing a database or
  building a partial URL.

## 6. Catalog, Database, Schema, And Target Resolution

StarRocks day-1 support must model catalog explicitly.

- DataTalk `database`: StarRocks database inside a catalog.
- DataTalk `schema`: null for day-1 unless metadata proves a separate need.
- Catalog: StarRocks catalog, with `default_catalog` for internal tables.
- URL composition: implementation must decide whether existing fields can hold
  catalog plus database or whether persistence needs new fields.
- `use xxx`: resolve catalog, database, and `catalog.database` forms with
  ambiguity suggestions.
- System filtering: hide internal schemas and system catalogs where appropriate.
- Case and quoting: verify identifier quoting and case sensitivity.

## 7. SQL Execution

- Context: apply selected catalog/database through URL, `SET CATALOG`, `USE`,
  or fully qualified names only after tests prove behavior.
- Multi-statement support: do not reuse MySQL splitter until StarRocks scripts
  are covered.
- Transactions: do not assume MySQL semantics; preserve L2/L3 confirmation.
- Batch DML: disabled or structured unsupported until insert/update count
  behavior is verified.
- Result values: normalize StarRocks numeric, decimal, JSON, array, bitmap,
  date/time, and binary-like values into JSON-safe output.
- Errors: include StarRocks context without leaking credentials.

## 8. SQL Splitter And Risk Guard

Splitter choices must be explicit:

- dedicated StarRocks splitter if MySQL script handling diverges;
- MySQL splitter reuse only after StarRocks coverage;
- generic splitter only if complex procedural scripts are day-1 unsupported.

Risk coverage must include:

- L1: `SELECT`, `WITH`, `SHOW`, `DESC`, `DESCRIBE`, `EXPLAIN`.
- L2: bounded `INSERT`, predicate-scoped `UPDATE`/`DELETE`, safe index or
  materialized-view operations only if semantics are proven.
- L3: `DROP`, `TRUNCATE`, broad `ALTER`, `CREATE/DROP CATALOG`, user/role
  grants, load jobs, export, compaction, backend/frontend node management, and
  system-level operations.

Chat-path SQL remains read-only.

## 9. Schema Discovery And ER Features

- Discovery must support `limit`, `cursor`, `pattern`, and explicit describe.
- Metadata must include catalog and database in returned target labels.
- Column metadata should include type, nullability, defaults, comments, keys,
  distribution/partition hints, and indexes where available.
- ER Inspector is structured unsupported until relation metadata is proven.
- ER Designer DDL is structured unsupported until StarRocks-specific DDL tests
  cover supported operations.

## 10. Diagnostics

- EXPLAIN: verify StarRocks plan output and map it to existing diagnostics.
- Index hints: structured unsupported unless real StarRocks recommendation logic
  is implemented.
- Lock info, pool status, table space: structured unsupported unless system
  tables and privileges are proven safe.
- Terminate session and optimize table: confirmable mutation design required
  before exposure.

## 11. Frontend

This design applies [client/DESIGN.md](../../client/DESIGN.md): semantic tokens,
one Chat/Workbench visual system, global Stage state, accessible controls, and
i18n.

Required frontend decisions:

- Label: `StarRocks`.
- Default port: `9030`.
- Connection form must capture catalog and database without ambiguous field
  reuse.
- Query Editor context should expose catalog/database behavior only after
  backend target discovery is complete.
- Formatter/outline may start from MySQL-like SQL only after StarRocks examples
  are tested.
- Diagnostics UI must show structured unsupported states.

## 12. AI/MCP And Runtime Prompt

Expose `starrocks` in MCP schemas only when runtime behavior is complete.

Runtime prompt rules must state that StarRocks has catalog/database context,
that `datatalk_resolve_use_target` should be used before switching context, and
that unsupported diagnostics, ER, load, catalog, and cluster-management
operations must not be claimed.

## 13. Acceptance And Verification

Automated gates:

- backend tests for kind routing, URL building, catalog/database target
  discovery, schema read, SQL execution, splitter, risk guard, diagnostics, ER,
  MCP schemas, and prompt;
- frontend tests for form, picker, context controls, formatter, outline,
  diagnostics, i18n, and accessibility;
- `cd server && mvn compile -q`;
- `cd client && npx tsc --noEmit`.

Manual or integration smoke must cover connection test, target listing,
catalog/database context selection, schema read, Query Editor `SELECT`, chat
mutation blocking, and diagnostics or structured unsupported responses.
