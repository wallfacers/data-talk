# Data Source Coverage: Presto Design

Date: 2026-05-01
Status: Draft for review

## 1. Purpose

PrestoDB is Wave B because it is a common federated analytical SQL engine.
Although it resembles Trino, PrestoDB has its own driver, release stream, and
runtime behavior. This design defines Presto as a separate canonical kind.

This artifact does not expose Presto as supported. Presto remains unsupported
until the child implementation plan is executed and verified.

## 2. Compatibility Gate Application

The mandatory gate is
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
Presto implementation must cover canonical naming, connection handling, driver
packaging, catalog/schema target resolution, SQL execution, result
normalization, splitter, risk guard, diagnostics, ER, frontend, MCP, runtime
prompt, and tests.

No gate section is N/A for first-class Presto support.

## 3. Support Statement

Target outcome: first-class or partial federated SQL support for PrestoDB,
depending on verified driver and connector behavior. Presto must not inherit
Trino support declarations without Presto-specific tests.

Presto remains unsupported until all child gates pass.

## 4. Kind Naming

- Canonical kind: `presto`.
- Accepted aliases: none for day-1.
- Persistence: store `presto` in backend records, API payloads, generated
  frontend types, and MCP schemas.
- Frontend label: `Presto`.
- Trino relationship: Presto is not a Trino alias.

## 5. Connection And Persistence

Design inputs from PrestoDB docs:

- Driver artifact candidate: `com.facebook.presto:presto-jdbc`.
- Expected driver class: `com.facebook.presto.jdbc.PrestoDriver`; verify from
  the selected artifact before implementation.
- URL forms: `jdbc:presto://host:port`,
  `jdbc:presto://host:port/catalog`,
  `jdbc:presto://host:port/catalog/schema`.
- Users need access to `system.jdbc` for metadata.
- Official release pages must be checked before pinning a driver version.

Required implementation decisions:

- Pin driver version after compatibility and license review.
- Required fields: host, port, username, optional password/token, optional
  catalog and schema.
- Optional fields: SSL, Kerberos, access token, custom headers, extra
  credentials, timezone, and source/application properties only if persisted
  securely.
- Persistence: model catalog and schema explicitly.

## 6. Catalog, Database, Schema, And Target Resolution

- DataTalk `database`: Presto catalog.
- DataTalk `schema`: Presto schema.
- Catalog: first-class target level.
- `use xxx`: resolve catalog, schema, and `catalog.schema` forms with ambiguity
  suggestions.
- Connector caveat: catalog capabilities vary and must not be overclaimed.
- System filtering: hide system catalogs/schemas by default while preserving
  metadata access.

## 7. SQL Execution

- Context: apply selected catalog/schema through URL or `USE catalog.schema`
  after tests prove behavior.
- Multi-statement support: use Presto-specific or conservative generic
  splitting.
- Transactions and DML: connector-dependent; do not claim generic write support
  without capability tests.
- Result values: normalize decimals, timestamps, arrays, maps, rows, JSON,
  UUID/IP-like values, and driver-specific objects.
- Errors: preserve Presto error names/types and query ids where available.

## 8. SQL Splitter And Risk Guard

Risk guard coverage must include:

- L1: `SELECT`, `WITH`, `SHOW`, `DESCRIBE`, `EXPLAIN`, `VALUES`.
- L2: connector-supported `INSERT`, `CREATE TABLE AS`, and scoped DDL only after
  capability checks.
- L3: `DROP`, `TRUNCATE`, broad `ALTER`, `CALL`, grants, roles, session/system
  changes, and connector procedures with external side effects.

Chat-path SQL remains read-only.

## 9. Schema Discovery And ER Features

- Discovery must use bounded metadata queries and handle missing `system.jdbc`
  privileges clearly.
- Metadata must return catalog/schema/table labels and connector-aware table
  types.
- ER Inspector is structured unsupported unless relation metadata is proven.
- ER Designer DDL is structured unsupported by default because DDL support is
  connector-dependent.

## 10. Diagnostics

- EXPLAIN: implement after Presto output is mapped.
- Index hints: structured unsupported by default.
- Lock info, pool status, table space: structured unsupported unless reliable
  system/runtime tables and privileges are verified.
- Terminate session and optimize table: structured unsupported until separate
  confirmable mutation designs exist.

## 11. Frontend

This design applies [client/DESIGN.md](../../client/DESIGN.md): semantic tokens,
one Chat/Workbench system, global Stage state, accessible controls, and i18n.

Required frontend decisions:

- Label: `Presto`.
- Default port: `8080` unless implementation chooses blank/default behavior.
- Connection form must expose catalog and schema context separately.
- Query Editor context must show catalog then schema selectors.
- Formatter may use generic SQL until Presto-specific examples are validated.
- Diagnostics UI must show connector-specific unsupported states.

## 12. AI/MCP And Runtime Prompt

Expose `presto` in MCP schemas only after behavior is complete.

Runtime prompt rules must state that Presto is separate from Trino, connector
capabilities vary, write support may be unavailable, diagnostics may be
structured unsupported, and `datatalk_resolve_use_target` is required before
context changes.

## 13. Acceptance And Verification

Automated gates:

- backend tests for kind routing, URL building, auth properties, catalog/schema
  target discovery, schema read, SQL execution, type normalization, splitter,
  risk, diagnostics, ER structured unsupported, MCP, and prompt;
- frontend tests for form, picker, catalog/schema context, formatter, outline,
  diagnostics, i18n, and accessibility;
- `cd server && mvn compile -q`;
- `cd client && npx tsc --noEmit`.

Manual or integration smoke must cover connection test, catalog/schema listing,
schema describe, Query Editor `SELECT`, mutation blocking, and diagnostics or
structured unsupported output.
