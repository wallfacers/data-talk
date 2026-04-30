# Data Source Coverage: ClickHouse Design

Date: 2026-05-01
Status: Draft for review

## 1. Purpose

ClickHouse is Wave B because it is a common OLAP column-store target with an
official JDBC driver. This design defines explicit ClickHouse support while
preserving DataTalk's guarded SQL and honest diagnostics contracts.

This artifact does not expose ClickHouse as supported. ClickHouse remains
unsupported until the child implementation plan is executed and verified.

## 2. Compatibility Gate Application

The mandatory gate is
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
ClickHouse implementation must cover every database compatibility area:
canonical naming, connection kind, URL, driver, metadata, target resolution,
SQL execution, type normalization, splitter, risk guard, diagnostics, ER,
frontend UI, MCP, runtime prompt, tests, and docs.

No gate section is N/A for first-class ClickHouse support.

## 3. Support Statement

Target outcome: first-class analytical SQL support for ClickHouse with explicit
limitations for mutations, transactions, and diagnostics.

At completion, DataTalk must create/test/use ClickHouse connections, discover
databases/tables, execute guarded analytical SQL, classify ClickHouse-specific
risk, normalize ClickHouse-specific values, and return structured unsupported
for unimplemented diagnostics or ER features.

## 4. Kind Naming

- Canonical kind: `clickhouse`.
- Accepted aliases: none for day-1.
- Persistence: store `clickhouse` in backend records, API payloads, generated
  frontend types, and MCP schemas.
- Frontend label: `ClickHouse`.

## 5. Connection And Persistence

Design inputs from ClickHouse docs:

- Driver candidate: `com.clickhouse:clickhouse-jdbc`.
- Driver class: `com.clickhouse.jdbc.ClickHouseDriver`.
- URL syntax includes `jdbc:clickhouse` or `jdbc:ch`, optional protocol, host,
  port, database, parameters, and tags.
- HTTP default port is `8123`; HTTPS commonly uses explicit protocol/SSL.
- Official docs recommend the current Java client directly when
  performance/direct access is critical, but DataTalk should use JDBC for
  consistency unless a later plan changes the contract.

Required implementation decisions:

- Pin driver version and shaded classifier after license/runtime packaging
  review.
- Choose and document the ClickHouse JDBC implementation generation explicitly:
  v1 vs v2, shaded vs thin artifact, native HTTP client dependencies, SSL and
  compression property names, URL syntax, and type-mapping behavior. The
  default target should be the current v2 facade unless tests or packaging
  constraints require the v1 driver.
- Required fields: host, port, username, password, optional database.
- Optional fields: protocol `http`/`https`, SSL, compression, connection
  timeout, socket timeout, and extra JDBC properties if persistence supports
  them.
- Persistence: `databaseName` may represent the selected ClickHouse database if
  metadata and execution tests prove consistency.

## 6. Catalog, Database, Schema, And Target Resolution

- DataTalk `database`: ClickHouse database.
- DataTalk `schema`: null for day-1 unless JDBC metadata exposes a meaningful
  schema layer.
- Catalog: treat as N/A for day-1 unless the driver reports catalogs in a way
  that affects target resolution.
- `use xxx`: resolve ClickHouse database names within the active connection.
- System filtering: hide `system`, `INFORMATION_SCHEMA`, and other internal
  databases by default.
- Case and quoting: cover backticks and double quotes where supported.

## 7. SQL Execution

- Context: apply selected database through URL database, `USE`, or driver
  property only after tests prove behavior.
- Multi-statement support: use a dedicated or generic splitter; do not assume
  MySQL/PostgreSQL script rules.
- Transactions: ClickHouse transaction support is not a general MySQL/PG-style
  assumption. L2/L3 rollback behavior must be explicitly proven or disabled.
- Batch DML: allowed only after JDBC update counts and insert behavior are
  tested.
- Result values: normalize unsigned integers, large decimals, UUID, IPv4/IPv6,
  enum, low-cardinality, nullable, array, tuple, map, nested, date/time, and
  binary/string values into JSON-safe output.
- Errors: preserve query id and server context where available without leaking
  credentials.

## 8. SQL Splitter And Risk Guard

Splitter coverage must include comments, strings, quoted identifiers,
ClickHouse settings, format clauses, and semicolon behavior.

Risk guard coverage must include:

- L1: `SELECT`, `WITH`, `SHOW`, `DESCRIBE`, `EXPLAIN`.
- L2: bounded `INSERT`, safe `CREATE TABLE`, and limited maintenance operations
  only after semantics are proven.
- L2/L3: `SELECT` statements that call ClickHouse table functions capable of
  server-side file or network access, such as `remote`, `url`, `s3`, `file`,
  `hdfs`, `postgresql`, `mongodb`, `odbc`, `jdbc`, `cluster`, or
  `clusterAllReplicas`, must be blocked in chat and require Workbench
  confirmation or structured unsupported output.
- L3: `DROP`, `TRUNCATE`, broad `ALTER`, `KILL QUERY`, `SYSTEM`, `OPTIMIZE`,
  `ATTACH`, `DETACH`, `RENAME`, grants, users, dictionaries, and cluster
  operations.

Chat-path SQL remains read-only.

## 9. Schema Discovery And ER Features

- Discovery must support `limit`, `cursor`, `pattern`, explicit describe, and
  large-schema bounds.
- Metadata should include engine, order key, partition key, primary key,
  sampling key, comments, and materialized/view distinctions when available.
- ER Inspector is structured unsupported unless relations can be discovered
  reliably from metadata.
- ER Designer DDL is structured unsupported until ClickHouse-specific table
  engine and key settings are designed.

## 10. Diagnostics

- EXPLAIN: implement only after ClickHouse `EXPLAIN` output is mapped.
- Index hints: structured unsupported unless a ClickHouse-specific diagnostic
  explains primary/order key, partition pruning, projections, or skipping
  indexes.
- Lock info: structured unsupported by default.
- Pool status: structured unsupported unless DataTalk has meaningful pool data.
- Table space: possible through system tables only after privilege-safe queries
  are verified.
- Terminate session and optimize table: confirmable mutations require a
  dedicated design.

## 11. Frontend

This design applies [client/DESIGN.md](../../client/DESIGN.md): semantic tokens,
one Chat/Workbench system, global Stage state, accessible controls, disabled
states, and i18n.

Required frontend decisions:

- Label: `ClickHouse`.
- Default port: `8123`.
- Connection form must expose protocol/SSL choices without raw color styling or
  hidden defaults.
- Query Editor context exposes database selector only for day-1.
- Formatter may use generic SQL until ClickHouse-specific formatting examples
  are validated.
- SQL outline must include ClickHouse-specific statements that affect risk.

## 12. AI/MCP And Runtime Prompt

Expose `clickhouse` in MCP schemas only after backend and frontend behavior is
complete.

Runtime prompt rules must warn that ClickHouse mutation and transaction
semantics differ from OLTP databases, require `datatalk_resolve_use_target`
before context changes, and avoid diagnostics/ER claims beyond implemented
behavior.

## 13. Acceptance And Verification

Automated gates:

- backend tests for kind routing, URL building, driver loading, target
  discovery, schema read, SQL execution, type normalization, splitter, risk,
  diagnostics, ER, MCP, and prompt;
- frontend tests for connection form, picker, Query Editor context, formatter,
  outline, diagnostics, accessibility, and i18n;
- `cd server && mvn compile -q`;
- `cd client && npx tsc --noEmit`.

Manual or integration smoke must cover connection test, database discovery,
schema describe, Query Editor `SELECT`, mutation blocking, ClickHouse type
normalization, and diagnostics or structured unsupported responses.
