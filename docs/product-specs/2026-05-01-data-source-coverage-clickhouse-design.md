# Data Source Coverage: ClickHouse Design

Date: 2026-05-01
Status: Approved

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
limitations for mutations, transactions, diagnostics, and ER features.

At completion, DataTalk must create/test/use ClickHouse connections, discover
databases/tables, execute guarded analytical SQL, classify ClickHouse-specific
risk, normalize ClickHouse-specific values, and return structured unsupported
for unimplemented diagnostics or ER features.

Day-1 limitations:
- Mutations (`INSERT`, `UPDATE`, `DELETE`) use ClickHouse's async mutation
  model — results may not be immediately visible. Workbench confirmation
  required.
- Transaction rollback is limited — ClickHouse does not provide OLTP-style
  MVCC transactions. Rollback behavior must be explicitly tested and may be
  structured unsupported.
- Diagnostics (EXPLAIN) and ER features (Inspector/Designer) are
  `dialect_unsupported` unless proven safe by tests.
- Clustered/distributed ClickHouse operations (`SYSTEM`, `KILL QUERY`,
  `OPTIMIZE`, `ATTACH`, `DETACH`, cluster functions) are day-2.

## 4. Kind Naming

- Canonical kind: `clickhouse`.
- Accepted aliases: none for day-1.
- Aliases NOT accepted: `jdbc:ch` short URL prefix is a ClickHouse JDBC
  convenience but must NOT be stored as a kind. Any connection using
  `jdbc:ch://...` must be normalized to `clickhouse` kind at the
  `ConnectionKind.normalize` boundary before persistence.
- Persistence: store `clickhouse` in backend records, API payloads, generated
  frontend types, and MCP schemas.
- Frontend label: `ClickHouse`.

## 5. Connection And Persistence

### 5.1 Driver and URL

- Driver candidate: `com.clickhouse:clickhouse-jdbc` (v2, `all` classifier
  shaded artifact). Final version pinned in plan Task 1 Step 2 after
  license/runtime packaging review.
- Driver class: `com.clickhouse.jdbc.ClickHouseDriver`.
- URL syntax: `jdbc:clickhouse://<host>:<port>/<database>?<params>`.
  - Protocol: `http` or `https` (via URL scheme or `protocol` parameter).
  - Default port: `8123`.
  - Database: optional in URL; can be set via `ConnectionTargetDiscoveryService`.
  - SSL, compression, connection timeout, socket timeout: JDBC properties.
- Official ClickHouse Java client (non-JDBC) is not used for day-1; JDBC is
  chosen for consistency with the DataTalk connection pool contract.
- Connection test: `Connection.isValid(5)` with 5-second timeout; fallback
  `SELECT 1` if the driver does not honor `isValid`.

### 5.2 Metadata DB Persistence

The `connections` table stores ClickHouse connection state through existing
columns — **no new columns or Flyway migration required**:

| Column | ClickHouse mapping |
|--------|-------------------|
| `kind` | `"clickhouse"` |
| `host` | ClickHouse server hostname or IP |
| `port` | Integer, default `8123` |
| `database_name` | ClickHouse database name string (e.g. `default`, `analytics`) |
| `username` / `password` | Standard credentials |

`database_name` is sufficient because ClickHouse databases are simple string
identifiers — no file paths, no multi-part names. The Query Editor context
selector uses this value for `USE <database>` execution.

### 5.3 Connection Pool Semantics

- Hikari pool connections share the same ClickHouse server instance. ClickHouse
  is sessionless at the connection level for read operations — each query is
  independent.
- Session-scoped settings (`SET send_logs_level`, `SET max_memory_usage`) do
  not persist across pool connections. Settings must be applied per-connection
  via JDBC URL parameters or re-applied after checkout.

## 6. Catalog, Database, Schema, And Target Resolution

- DataTalk `database`: ClickHouse database name. Switched via `USE <database>`
  within the active connection. The exact syntax must be verified in plan
  Task 3 Step 1 against the pinned driver.
- DataTalk `schema`: **N/A** — ClickHouse does not have a separate schema
  layer. Tables belong directly to databases.
- Catalog: N/A for day-1.
- `USE <database>`: resolves the database context within the active connection.
- System filtering: hide the following databases by default:
  - `system` (ClickHouse internal)
  - `INFORMATION_SCHEMA` (SQL standard compatibility layer)
  - `_temporary_and_external_tables` (internal)
- Case and quoting: backticks and double quotes are supported; the risk
  analyzer and SQL outline must handle both.

## 7. SQL Execution

- Context: apply selected database through `USE <database>` only after tests
  prove behavior.
- Multi-statement support: use **GenericSqlStatementSplitter**. ClickHouse has
  no `DELIMITER` statement, no PL/SQL blocks, and no `GO` batch separator.
  Potential edge cases verified as safe:
  - Dollar-quoted strings: ClickHouse does not use dollar-quoted strings;
    string literals use single quotes with backslash escaping.
  - Semicolons inside single-quoted strings are handled by the generic
    splitter's quote-aware state machine.
  - `--` and `/* */` comments are correctly handled.
  - Format clauses (`FORMAT CSV`, `FORMAT JSON`) appear at statement end and
    do not affect splitting.
  - SETTINGS clauses (`SETTINGS max_threads = 4`) are key-value pairs within
    the statement and do not affect splitting.
- Transactions: ClickHouse does not provide OLTP-style MVCC transactions.
  `BEGIN`/`COMMIT`/`ROLLBACK` exist but have limited semantics (primarily for
  `INSERT` atomicity). Day-1: transactions are **structured unsupported** for
  `SELECT`; for `INSERT` the async mutation model means rollback visibility is
  not guaranteed.
- Batch DML: allowed only after JDBC update counts and insert behavior are
  tested.
- Result values: normalize ClickHouse-specific types per the contract in §7.1.
- Errors: preserve query id and server context where available without leaking
  credentials.

### 7.1 Type Normalization Contract

The following table defines how ClickHouse-specific types are normalized to
JSON shapes for `JdbcResultValueNormalizer`:

| ClickHouse Type | JDBC Type | JSON Output | Notes |
|-----------------|-----------|-------------|-------|
| `UInt8` – `UInt64` | `INTEGER` / `BIGINT` | **number** (if < 2^53) or **string** | `UInt64` may exceed JS `MAX_SAFE_INTEGER` — check magnitude before narrowing |
| `Int128` / `UInt128` | `OTHER` | **string** (decimal) | Exceeds JavaScript `Number.MAX_SAFE_INTEGER` |
| `Int256` / `UInt256` | `OTHER` | **string** (decimal) | Same as Int128 |
| `Decimal32` / `Decimal64` / `Decimal128` / `Decimal256` | `DECIMAL` | **string** (fixed-point) | Preserve precision; do not convert to float |
| `UUID` | `VARCHAR` / `OTHER` | **string** | Standard UUID hex format |
| `IPv4` | `INTEGER` / `VARCHAR` | **string** (dotted notation) | Preserve human-readable format |
| `IPv6` | `VARBINARY` / `VARCHAR` | **string** (compressed IPv6 notation) | e.g. `::1`, `2001:db8::1` |
| `Array(T)` | `ARRAY` | **JSON array** | Recursively normalize element type T |
| `Tuple(T1, T2, ...)` | `OTHER` | **JSON array** (ordered) | Positional elements; recursively normalize |
| `Map(K, V)` | `OTHER` | **JSON object** | Keys stringified; recursively normalize values |
| `Nested` | `ARRAY` of `STRUCT` | **JSON array of objects** | Each row becomes an object |
| `LowCardinality(T)` | (depends on T) | (depends on T) | Transparent wrapper — normalize the underlying type T |
| `Nullable(T)` | (depends on T) | **null** or (depends on T) | NULL maps to JSON null; non-null recursively normalized |
| `Enum8` / `Enum16` | `VARCHAR` | **string** | Enum value as string (not ordinal) |
| `Date` | `DATE` | **string** (`YYYY-MM-DD`) | ISO date format |
| `DateTime` | `TIMESTAMP` | **string** (ISO 8601 without offset) | Server timezone applies |
| `DateTime64(precision)` | `TIMESTAMP` | **string** (ISO 8601 with fractional seconds) | Includes sub-second precision |
| `DateTime('timezone')` | `TIMESTAMP WITH TIME ZONE` | **string** (ISO 8601 with offset) | Include timezone offset |
| `String` | `VARCHAR` | **string** | UTF-8 text |
| `FixedString(N)` | `VARCHAR` / `CHAR` | **string** | Trim trailing null bytes if present |
| `Bool` | `BOOLEAN` | **boolean** (`true`/`false`) | ClickHouse 21.12+ native boolean |

The normalizer must handle `java.sql.ResultSet.getObject()` returning
ClickHouse-specific wrapper objects and convert them to the above JSON shapes
without throwing `ClassCastException`.

## 8. SQL Splitter And Risk Guard

### 8.1 Splitter

**Decision**: `GenericSqlStatementSplitter` for day-1. No ClickHouse-specific
splitter required. See §7 for the rationale (no DELIMITER, no PL/SQL, no GO;
format/SETTINGS clauses are statement-internal).

### 8.2 Risk Levels

- L1: `SELECT`, `WITH`, `SHOW`, `DESCRIBE`, `EXPLAIN`.
- L2: bounded `INSERT` (single table, no subquery source), safe `CREATE TABLE`.
- L3: `DROP`, `TRUNCATE`, broad `ALTER`, `RENAME`, grants, users,
  dictionaries, and cluster operations.

### 8.3 Hard Reject (Day-1 Structured Unsupported)

The following are **not L2/L3** — they are rejected immediately with a
structured unsupported response:

- `KILL QUERY`, `SYSTEM`, `OPTIMIZE` — server-level operations.
- `ATTACH`, `DETACH` — ClickHouse-level database/table attachment, not DataTalk
  attachment.
- `CREATE USER`, `CREATE ROLE`, `GRANT`, `REVOKE` — privilege management.

### 8.4 SELECT-Shaped File/Network Access — Hard Reject

The following are **hard reject** (structured unsupported), not L2/L3. Day-1
sandbox path policy cannot penetrate function parameters (e.g.
`file('path/to/data.csv')`), so confirming them via Workbench is unsafe. This
decision is aligned with the DuckDB child design §8.4 — both databases treat
server-side file/network access functions as day-1 hard reject for the same
reason.

- `remote`, `remoteSecure` — distributed cluster access.
- `url` — arbitrary HTTP/HTTPS endpoint access.
- `s3`, `s3Cluster` — S3/object storage access.
- `file` — local filesystem read.
- `hdfs` — HDFS access.
- `postgresql`, `mysql`, `mongodb`, `odbc`, `jdbc` — external database
  bridging.
- `cluster`, `clusterAllReplicas` — cluster-wide query execution.
- `mysql`, `postgresql` table functions — cross-database federation.

Chat-path SQL remains read-only and must not read arbitrary server-side files
or trigger network I/O without an explicit Workbench confirmation path.

## 9. Schema Discovery And ER Features

- Discovery must support `limit`, `cursor`, `pattern`, explicit describe, and
  large-schema bounds.
- Metadata should include engine, order key, partition key, primary key,
  sampling key, comments, and materialized/view distinctions when available.
- ER Inspector: **day-1 fallback is `dialect_unsupported`** — ClickHouse does
  not have relational foreign key constraints in the OLTP sense;
  `getImportedKeys` will return empty.
- ER Designer: **day-1 fallback is `dialect_unsupported`** — ClickHouse table
  engine decisions (MergeTree, ReplacingMergeTree, etc.) are not mappable to
  DataTalk's ER DDL generation contract.

## 10. Diagnostics

- EXPLAIN: implement only after ClickHouse `EXPLAIN` output (AST, pipeline,
  actions) is mapped to the DataTalk diagnostics response contract.
  Day-1 fallback: `dialect_unsupported` if mapping is too complex.
- Index hints: structured unsupported — ClickHouse uses primary/order keys
  and partition pruning, not B-tree index advice. A ClickHouse-specific
  diagnostic for skipping indexes or projections could be designed as day-2.
- Lock info: structured unsupported (ClickHouse is not OLTP locking).
- Pool status: structured unsupported.
- Table space: possible through `system.tables` or `system.parts` queries
  only after privilege-safe verification.
- Terminate session (`KILL QUERY`) and optimize table (`OPTIMIZE TABLE`):
  structured unsupported until a dedicated mutation design exists.

## 11. Frontend

This design applies [client/DESIGN.md](../../client/DESIGN.md): semantic tokens,
one Chat/Workbench system, global Stage state, accessible controls, disabled
states, and i18n.

### 11.1 Connection Form

All control states below use `client/DESIGN.md` semantic tokens. Actual CSS
color values are mapped by the DESIGN.md token system.

- Label: `ClickHouse`.
- Host input: standard text field.
  - **Five states**: idle → `bg.panel`, border `border.default`; hover →
    border `border.strong`; focus → `interaction.focusRing`; error → border
    `status.danger`, error helper text uses `status.danger` as text color;
    disabled → `interaction.disabled`.
- Port input: numeric field, default `8123`.
  - **Five states**: same as host input.
- Protocol selector: radio group or segmented control (`HTTP` / `HTTPS`).
  - **Five states**: idle → `bg.panel`, border `border.subtle`; hover →
    `interaction.hover` overlay; focus → `interaction.focusRing`;
    selected/active → `interaction.selected` background, text `text.strong`;
    disabled → `interaction.disabled`.
- Database input: optional text field.
  - **Five states**: same as host input.
- Username/password: standard credential fields.
  - Password field: masked, with optional visibility toggle.
  - **Five states**: same as host input.
- Connection test button: standard primary button.
  - **States**: idle → `accent.primary` background, `text.inverse` (note:
    `text.inverse` from DESIGN.md theme semantics); hover →
    `accent.primaryHover`; focus → `interaction.focusRing`; active →
    `interaction.active`; loading → `interaction.disabled` with spinner;
    disabled → `interaction.disabled`.
- Success/error feedback:
  - Success → `status.successSurface` background, `status.success` icon.
  - Error → `status.dangerSurface` background, `status.danger` border,
    error text uses `status.danger`.

### 11.2 Query Editor

- Database selector: visible for ClickHouse (replaces schema selector).
- Formatter: generic SQL until ClickHouse-specific formatting examples are
  validated.
- Outline: generic SQL parsing; must recognize ClickHouse-specific keywords
  (`MATERIALIZE`, `FINAL`, `SAMPLE`, `SETTINGS`, `FORMAT`).

### 11.3 Diagnostics and Unsupported States

- EXPLAIN: rendered if implemented; otherwise structured unsupported badge
  with localized text.
- Index hints / lock info / terminate / optimize: structured unsupported.
- Risk card: L2/L3 confirmation for mutations; hard-reject banner for
  file/network access functions.

## 12. AI/MCP And Runtime Prompt

Expose `clickhouse` in MCP schemas only after backend and frontend behavior is
complete.

### 12.1 Runtime Prompt Segment

The following segment is inserted into `AGENTS.md` under the `clickhouse` kind
section:

```
## ClickHouse Notes

- ClickHouse is an analytical column-store database. It uses HTTP protocol
  (default port 8123) and does not support OLTP-style transactions.
- `datatalk_execute_sql` remains read-only in the chat path.
- Mutations (`INSERT`, `ALTER`, `DELETE`) are async and require the SQL
  workbench with confirmation. Results may not be immediately visible.
- File and network access functions (`file`, `s3`, `url`, `remote`, `hdfs`,
  `odbc`, `jdbc`, `mysql`, `postgresql`) are not supported.
- Cluster and system operations (`SYSTEM`, `KILL QUERY`, `OPTIMIZE`,
  `ATTACH`, `DETACH`) are not supported.
- ER diagrams and index hints are not available for ClickHouse.
```

## 13. Acceptance And Verification

Automated gates:

- backend tests for kind routing, URL building, driver loading, connection
  test, target discovery, schema read, SQL execution, type normalization,
  splitter, risk, diagnostics, ER, MCP, and prompt;
- frontend tests for connection form (host/port/protocol/database), picker,
  Query Editor database context, formatter, outline, diagnostics,
  accessibility, and i18n;
- `cd server && mvn compile -q`;
- `cd client && npx tsc --noEmit`.

Manual or integration smoke must cover connection test, database discovery,
schema describe, Query Editor `SELECT`, mutation blocking, ClickHouse type
normalization (especially UInt64/Int128/IPv6/Map/Tuple), and diagnostics or
structured unsupported responses.

**Housekeeping**: Upon completion, also satisfy parent Wave B design §10
housekeeping — update `DATA_SOURCE_TYPE_COMPATIBILITY.md` support snapshot row
and Wave B child artifact tracking table.

> Reviewed by wallfacers, 2026-05-07. P0 3 items (L2/L3 vs DuckDB hard-reject
> alignment for SELECT-shaped file/network access, metadata DB persistence
> decision, splitter explicit selection) resolved. P1 7 items (semantic tokens,
> type JSON contract table, connection test mechanism, jdbc:ch alias normalization,
> support statement clarity, USE syntax, system filtering list) resolved. P2 items
> (transaction semantics clarification, LowCardinality transparent handling,
> cluster function coverage) resolved.
