# Data Source Coverage: Hive Design

Date: 2026-05-01
Status: Draft for review

## 1. Purpose

Apache Hive is Wave B because it is a common analytical SQL target, but it has
older JDBC metadata limitations and a wide authentication/transport matrix.
This design defines a conservative HiveServer2 support path.

This artifact does not expose Hive as supported. Hive remains unsupported until
the child implementation plan is executed and verified.

## 2. Compatibility Gate Application

The mandatory gate is
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
Hive implementation must cover canonical naming, connection transport/auth,
driver packaging, metadata, target resolution, SQL execution, splitter, risk
guard, diagnostics, ER behavior, frontend UI, MCP, runtime prompt, and tests.

No gate section is N/A if Hive is exposed as a database kind.

## 3. Support Statement

Target outcome: conservative HiveServer2 SQL support. Day-1 minimum support is
binary transport on port `10000`, a selected Hive database, and simple
username/password or username-only authentication. HTTP transport, SSL,
Kerberos, ZooKeeper discovery, custom headers/cookies, and Knox-style
deployments are day-2 capabilities and must remain structured unsupported
unless a reviewed design revision explicitly adds fields and tests.

Hive remains unsupported until the selected support subset passes all child
verification.

## 4. Kind Naming

- Canonical kind: `hive`.
- Accepted aliases: none for day-1.
- Persistence: store `hive` in backend records, API payloads, generated
  frontend types, and MCP schemas.
- Frontend label: `Apache Hive`.

## 5. Connection And Persistence

Design inputs from Apache HiveServer2 docs:

- Driver class: `org.apache.hive.jdbc.HiveDriver`.
- URL prefix: `jdbc:hive2://`.
- Default binary port: `10000`.
- HTTP transport uses `transportMode=http;httpPath=<path>` and commonly port
  `10001`.
- SSL, Kerberos principal, two-way SSL, custom headers/cookies, and ZooKeeper
  service discovery require explicit URL properties.

Required implementation decisions:

- Driver artifact/version and transitive dependency packaging must be reviewed
  because Hive JDBC pulls Hadoop/Hive libraries.
- Required day-1 fields: host, port, database, username, password behavior, and
  transport mode.
- Day-1 transport/auth fields: binary transport only, default port `10000`,
  database, username, and optional password according to cluster mode.
- Optional fields for HTTP path, SSL truststore, Kerberos
  principal/keytab/cache, ZooKeeper namespace, and custom headers/cookies are
  excluded from day-1 unless this design is revised and re-approved.
- Persistence: do not encode complex auth material into `databaseName`.

## 6. Catalog, Database, Schema, And Target Resolution

- DataTalk `database`: Hive database.
- DataTalk `schema`: null for day-1; Hive database is the namespace normally
  exposed to users.
- Catalog: N/A for day-1 unless a Hive deployment exposes catalog-like
  behavior through a metastore-compatible layer.
- `use xxx`: resolve Hive database names.
- System filtering: hide internal databases if reported by the metastore.
- Metadata caveat: older Hive docs state limited JDBC metadata support; child
  implementation must verify `DatabaseMetaData` behavior against the selected
  HiveServer2 version or use bounded SQL fallbacks.

## 7. SQL Execution

- Context: apply selected database through URL database or `USE`.
- Multi-statement support: use a Hive-specific or conservative generic splitter.
- Transactions: do not assume rollback semantics for DDL/DML; keep L2/L3 guard
  flow and mark rollback unavailable where not proven.
- Batch DML: structured unsupported unless update counts and insert behavior
  are verified.
- Result values: normalize primitive, decimal, timestamp/date, binary, array,
  map, struct, and JSON-stringified complex values into JSON-safe output.
- Cancellation: query cancellation support must be tested; do not claim cancel
  reliability until proven.

## 8. SQL Splitter And Risk Guard

Splitter coverage must include Hive comments, strings, quoted identifiers,
`SET`, `ADD JAR`, `TRANSFORM`, `LOAD DATA`, and semicolon behavior.

Risk guard coverage must include:

- L1: `SELECT`, `WITH`, `SHOW`, `DESCRIBE`, `EXPLAIN`.
- L2: `INSERT`, `CREATE TABLE`, `CREATE VIEW`, and partition maintenance only
  after semantics are verified.
- L3: `DROP`, `TRUNCATE`, broad `ALTER`, `LOAD DATA`, `ADD JAR`, `CREATE
  FUNCTION`, `MSCK REPAIR`, grants, compaction, and filesystem-affecting
  operations.

Chat-path SQL remains read-only.

## 9. Schema Discovery And ER Features

- Discovery must support `limit`, `cursor`, `pattern`, and explicit describe.
- Metadata should include table type, partition columns, storage format,
  location only if safe to expose, comments, and column types.
- ER Inspector is structured unsupported by default because Hive commonly lacks
  enforced foreign-key metadata.
- ER Designer DDL is structured unsupported until Hive-specific CREATE/ALTER
  generation, partitioning, storage format, and external table semantics are
  designed.

## 10. Diagnostics

- EXPLAIN: possible after Hive plan text is mapped.
- Index hints: structured unsupported by default.
- Lock info: structured unsupported unless Hive lock commands/system tables are
  verified for the selected transaction manager.
- Pool status and table space: structured unsupported unless reliable metastore
  or filesystem-safe queries are implemented.
- Terminate session and optimize table: structured unsupported until a separate
  confirmable mutation design exists.

## 11. Frontend

This design applies [client/DESIGN.md](../../client/DESIGN.md): semantic tokens,
one Chat/Workbench system, global Stage state, accessible controls, disabled
states, and i18n.

Required frontend decisions:

- Label: `Apache Hive`.
- Default port: `10000`.
- Connection form must not expose Kerberos/HTTP/SSL fields until backend
  persists and tests them.
- Query Editor context exposes database selector only for day-1.
- Formatter may use generic SQL until Hive-specific examples are validated.
- Unsupported auth/diagnostics states must be visible and localized.

## 12. AI/MCP And Runtime Prompt

Expose `hive` in MCP schemas only after the implemented support subset is
complete.

Runtime prompt rules must state the selected Hive transport/auth subset, require
`datatalk_resolve_use_target` before context changes, avoid claims about
Kerberos/HTTP/SSL unless implemented, and keep DDL/DML in Workbench
confirmation.

## 13. Acceptance And Verification

Automated gates:

- backend tests for kind routing, URL building, selected auth/transport fields,
  metadata fallback, schema read, SQL execution, result normalization, splitter,
  risk, diagnostics, ER structured unsupported, MCP, and prompt;
- frontend tests for form, picker, Query Editor context, unsupported auth
  states, formatter, outline, diagnostics, i18n, and accessibility;
- `cd server && mvn compile -q`;
- `cd client && npx tsc --noEmit`.

Manual or integration smoke must use a real HiveServer2 fixture for connection
test, database listing, schema describe, Query Editor `SELECT`, mutation
blocking, selected auth mode, and diagnostics or structured unsupported output.
