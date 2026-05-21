# Data Source Coverage: Trino Design

Date: 2026-05-01
Status: Draft for review

## 1. Purpose

Trino is Wave B because it is a common federated analytical SQL engine with an
official JDBC driver. This design defines Trino as its own data-source kind with
explicit catalog/schema handling and connector-capability caveats.

This artifact does not expose Trino as supported. Trino remains unsupported
until the child implementation plan is executed and verified.

## 2. Compatibility Gate Application

The mandatory gate is
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
Trino implementation must cover canonical naming, connection handling, driver
packaging, catalog/schema target resolution, SQL execution, result
normalization, splitter, risk guard, diagnostics, ER, frontend, MCP, runtime
prompt, and tests.

No gate section is N/A for first-class Trino support.

## 3. Support Statement

Target outcome: first-class federated SQL support for Trino where DataTalk can
connect, discover catalogs/schemas/tables, execute guarded SQL, and return
structured unsupported for connector-specific diagnostics or mutations that
cannot be proven generically.

Trino remains unsupported until all child gates pass.

## 4. Kind Naming

- Canonical kind: `trino`.
- Accepted aliases: none for day-1.
- Persistence: store `trino` in backend records, API payloads, generated
  frontend types, and MCP schemas.
- Frontend label: `Trino`.
- Presto relationship: Trino is not a Presto alias.

## 5. Connection And Persistence

Design inputs from Trino docs:

- Driver artifact: `io.trino:trino-jdbc`.
- Driver class: `io.trino.jdbc.TrinoDriver`.
- URL forms: `jdbc:trino://host:port`,
  `jdbc:trino://host:port/catalog`,
  `jdbc:trino://host:port/catalog/schema`.
- HTTP default port can be `80`; HTTPS default can be `443` with `SSL=true`;
  typical deployments often use `8080`.
- Users need access to the `system.jdbc` schema for metadata.
- Driver version should match or be newer than the Trino cluster.

Required implementation decisions:

- Pin driver version after cluster compatibility and license review.
- Required fields: host, port, username, optional password or token,
  optional catalog and schema.
- Optional fields: SSL, SSL verification, access token, Kerberos, roles,
  session properties, extra credentials, source/client tags only if persisted
  securely.
- Persistence: model catalog and schema explicitly; do not collapse both into
  `databaseName` without a documented mapping.

## 6. Catalog, Database, Schema, And Target Resolution

- DataTalk `database`: Trino catalog.
- DataTalk `schema`: Trino schema inside the selected catalog.
- Catalog: first-class target level.
- `use xxx`: resolve catalog, schema, and `catalog.schema` forms with ambiguity
  suggestions.
- System filtering: hide system catalogs/schemas by default while preserving
  access needed for metadata queries.
- Connector caveat: catalog capabilities vary by connector; target discovery
  must not imply every catalog supports writes, transactions, or diagnostics.
- Trino implementation must place catalog/schema URL parsing, target-resolution
  helpers, and connector-capability descriptors in neutral shared names when
  they are likely to be reused by Presto. Do not hard-code those abstractions
  behind Trino-only names.

## 7. SQL Execution

- Context: apply selected catalog/schema through URL or `USE catalog.schema`
  after tests prove behavior.
- Multi-statement support: use a Trino-specific or conservative generic
  splitter; do not assume PostgreSQL/MySQL procedural syntax.
- Transactions: support only where Trino and connector behavior is verified.
- Batch DML: structured unsupported unless connector capabilities and update
  counts are proven.
- Result values: normalize decimals, timestamps with/without time zone, arrays,
  maps, rows, JSON, UUID, IP address, and driver-specific objects.
- Errors: preserve query id and Trino error name/type where available.

## 8. SQL Splitter And Risk Guard

Risk guard coverage must include:

- L1: `SELECT`, `WITH`, `SHOW`, `DESCRIBE`, `EXPLAIN`, `VALUES`.
- L2: connector-supported `INSERT`, `CREATE TABLE AS`, and scoped DDL only after
  capability checks.
- L3: `DROP`, `TRUNCATE`, broad `ALTER`, `CALL`, grants, roles, session/system
  changes, and connector procedures with external side effects.

Chat-path SQL remains read-only.

## 9. Schema Discovery And ER Features

- Discovery must use bounded metadata queries and `system.jdbc` access where
  required.
- Metadata must return catalog/schema/table labels and connector-aware table
  type information.
- ER Inspector is structured unsupported unless relation metadata is proven for
  a catalog.
- ER Designer DDL is structured unsupported by default because DDL support is
  connector-dependent.

## 10. Diagnostics

- EXPLAIN: implement after Trino plan text or JSON is mapped.
- Index hints: structured unsupported by default because connectors vary.
- Lock info, pool status, table space: structured unsupported unless reliable
  system/runtime tables and privileges are proven.
- Terminate session: possible through Trino query cancellation APIs only after a
  separate confirmable mutation design.
- Optimize table: structured unsupported by default because connector
  procedures vary.

## 11. Frontend

This design applies [client/DESIGN.md](../../client/DESIGN.md): semantic tokens,
one Chat/Workbench system, global Stage state, accessible controls, and i18n.

Required frontend decisions:

- Label: `Trino`.
- Default port: `8080` unless product chooses blank/default-by-SSL behavior.
- Connection form must expose catalog and schema context separately.
- Query Editor context must show catalog then schema selectors.
- Formatter may use generic SQL until Trino-specific examples are validated.
- Diagnostics UI must show connector-specific unsupported states.

## 12. AI/MCP And Runtime Prompt

Expose `trino` in MCP schemas only after backend and frontend behavior is
complete.

Runtime prompt rules must state that Trino is federated, connector capabilities
vary, writes and diagnostics may be unsupported per catalog, and
`datatalk_resolve_use_target` is required before changing context.

## 13. Acceptance And Verification

Automated gates:

- backend tests for kind routing, URL building, auth properties, catalog/schema
  target discovery, schema read, SQL execution, type normalization, splitter,
  risk, diagnostics, ER structured unsupported, MCP, and prompt;
- frontend tests for connection form, picker, catalog/schema context,
  formatter, outline, diagnostics, i18n, and accessibility;
- `cd server && mvn compile -q`;
- `cd client && npx tsc --noEmit`.

Manual or integration smoke must cover connection test, catalog/schema listing,
schema describe, Query Editor `SELECT`, mutation blocking, and diagnostics or
structured unsupported output.
