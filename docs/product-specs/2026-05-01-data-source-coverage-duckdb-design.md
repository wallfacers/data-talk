# Data Source Coverage: DuckDB Design

Date: 2026-05-01
Status: Approved

## 1. Purpose

DuckDB is Wave B because it is a popular embedded analytical SQL engine. It is
not a host/port database, so DataTalk must design file, in-memory, read-only,
extension, and local filesystem safety explicitly.

This artifact does not expose DuckDB as supported. DuckDB remains unsupported
until the child implementation plan is executed and verified.

## 2. Compatibility Gate Application

The mandatory gate is
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
DuckDB implementation must cover canonical naming, connection handling, file
path persistence, driver packaging, metadata, target resolution, SQL execution,
result normalization, splitter, risk guard, diagnostics, ER behavior, frontend
UI, MCP, runtime prompt, and tests.

No gate section is N/A for first-class DuckDB support.

## 3. Support Statement

Target outcome: first-class embedded/file analytical SQL support if file-safety
and read-only/write mode rules are approved. DuckDB support may be partial if
extensions, external file reads, cloud/object storage, or write mode are kept
structured unsupported for day-1.

DuckDB remains unsupported until all child gates pass.

## 4. Kind Naming

- Canonical kind: `duckdb`.
- Accepted aliases: none for day-1.
- Persistence: store `duckdb` in backend records, API payloads, generated
  frontend types, and MCP schemas.
- Frontend label: `DuckDB`.

## 5. Connection, Persistence, And Metadata DB Contract

### 5.1 Driver and URL

- Driver class: `org.duckdb.DuckDBDriver`.
- URL prefix: `jdbc:duckdb:`.
- `jdbc:duckdb:` creates an in-memory database.
- `jdbc:duckdb:/path/to/file` opens or creates a persistent database file.
- Read-only mode uses connection property `duckdb.read_only=true`.
- Mixing read-write and read-only connections to the same database file is
  unsupported by DuckDB docs.
- Connection test: `Connection.isValid(5)` with 5-second timeout; fallback
  `SELECT 1` if the driver does not honor `isValid`.

### 5.2 Metadata DB Persistence

The `connections` table stores DuckDB connection state through the following
columns:

| Column | DuckDB mapping |
|--------|---------------|
| `kind` | `"duckdb"` |
| `database_name` | Canonical file path for file mode; literal `:memory:` for in-memory mode |
| `read_only` | **New** `BOOLEAN NOT NULL DEFAULT FALSE` column added via V14 Flyway migration |
| `host` / `port` / `username` / `password` | NULL for DuckDB |

**Decision**: Add `read_only` boolean column rather than overloading
`database_name` with encoded prefixes. `database_name` continues the SQLite
paradigm — it carries the canonical absolute file path (or `:memory:`). The
`read_only` flag is a separate column so that:

1. Connection pool factory can read it and construct the correct JDBC URL
   property before pooling;
2. Connection form UI can render the toggle without parsing path conventions;
3. Risk analyzer and SQL execution path can check it without string parsing.

**Path canonicalization**: `database_name` must store the normalized absolute
path under the configured DuckDB data root. `..` traversal, symlinks escaping
the root, and sensitive absolute paths (`/etc`, `/proc`, home-directory
secrets, platform credential stores) are rejected at write time.

**Sandbox WAL sibling**: The sandbox policy validates the main file
(`<name>.duckdb`) and its sibling files: `<name>.duckdb.wal` (write-ahead log)
and `<name>.duckdb.tmp` (temporary files). Symlink resolution follows the real
target before root comparison.

### 5.3 In-Memory Connection Pool Semantics

**Decision**: Use named in-memory databases with session-scoped identity.

- In-memory connections use URL `jdbc:duckdb::memory:<name>` (double-colon
  syntax, full `memory` keyword). The `<name>` is `dt_mem_{sessionId}` (stable
  across queries within the same DataTalk session).
  - **URL syntax validation**: The exact URL form (`jdbc:duckdb::memory:<name>`
    vs `jdbc:duckdb:mem:<name>`) must be verified against the final pinned
    driver version in plan Task 1 Step 2. If the driver only accepts one form,
    the URL template in this design is corrected to match — this is not
    considered a design deviation.
- Hikari pool connections all open the same named in-memory DB, so tables
  created by one pool connection are visible to all others in the same pool.
- Pool size for in-memory DuckDB: default Hikari size (10) is safe because all
  connections share the same in-memory database by name.
- **Cross-session isolation**: Different sessions have different `sessionId`,
  so `dt_mem_{sessionId}` is unique per session — no data leakage.
- **Pool lifecycle**: When the DataTalk session ends, the in-memory DB is
  garbage-collected by DuckDB when the last connection closes. Hikari pool
  closure handles cleanup.

### 5.4 Read-Only Mode Runtime Behavior

- Read-only is a **persisted, immutable-after-creation** property. The
  connection form does not allow toggling read-only after the connection is
  saved. To change, the user must create a new connection.
- This eliminates the need for pool destruction/rebuild on toggle and prevents
  R/W and R/O connections from coexisting in the same pool to the same file.
- If the same `.duckdb` file is held by another DuckDB CLI or DataTalk process,
  the exact concurrent access behavior depends on the pinned driver version
  per §5.1; at worst the connection test returns a locking error and displays
  a localized failure message.

### 5.5 Sandbox Data Root

- File-mode connections must resolve to a path under a configured
  `datatalk.duckdb.data-root` directory.
- **Sensible default**: `${user.home}/.datatalk/duckdb/`. On first startup, if
  the directory does not exist, the application creates it automatically. If
  creation fails (permission denied / disk full / read-only home), the
  application logs a warning at startup and the DuckDB file-mode connection
  form enters a disabled state with a prompt to configure a writable path. The
  default can be overridden via `application.yml` or the
  `DATATALK_DUCKDB_DATA_ROOT` environment variable.
- External network/object-store extensions (`httpfs`/AWS/S3) remain disabled or
  structured unsupported by that policy.

### 5.6 Backend Locality

- "Backend-local" means the filesystem of the Spring Boot process. In the
  DataTalk deployment model (Tauri sidecar), this is the user's local machine.
- The term "desktop affinity" in §11 refers to the Tauri file picker being
  enabled because the backend and frontend share the same OS filesystem. No
  separate affinity declaration endpoint is needed — the Tauri desktop build
  implicitly satisfies this condition.

## 6. Catalog, Database, Schema, And Target Resolution

- DataTalk `database`: DuckDB database file identity or `:memory:` label, not a
  server database name.
- DataTalk `schema`: DuckDB schema, normally `main`.
- Catalog: attached databases may behave like catalogs, but day-1 should not
  expose attachment as normal target switching unless `ATTACH` safety is
  designed.
- `use xxx`: resolve schema names inside the active DuckDB connection only
  unless attached database support is explicitly added.
- System filtering: hide internal schemas/tables where appropriate.
- `ATTACH` and multi-database catalog support are day-2 only and require a
  separate design for path sandboxing, catalog naming, and target switching.

## 7. SQL Execution

- Context: apply selected schema through `USE <schema>` or `SET schema = '<name>'`
  only after tests prove behavior. The exact syntax (`USE` vs `SET schema =`)
  must be verified in plan Task 3 Step 1 against the pinned driver.
- Multi-statement support: use **GenericSqlStatementSplitter**. DuckDB has no
  `DELIMITER` statement, no PL/SQL blocks, and no `GO` batch separator. The
  generic splitter handles semicolons in string literals and `--`/`/* */`
  comments correctly. Potential edge cases verified as safe:
  - Dollar-quoted strings (`$$...$$`): DuckDB does not support dollar-quoted
    strings natively — they are regular string literals containing `$$`.
  - `COPY ... FROM STDIN`: DuckDB's `COPY` uses file paths, not STDIN streams.
    `COPY` is classified as L3 (see §8).
  - String-embedded semicolons are correctly handled by the generic splitter's
    quote-aware state machine.
- Transactions: verify JDBC transaction behavior for file and in-memory modes.
- Batch DML: can be supported only after prepared statement and update-count
  behavior is tested.

### 7.1 Type Normalization Contract

The following table defines how DuckDB-specific types are normalized to JSON
shapes for `JdbcResultValueNormalizer`:

| DuckDB Type | JDBC Type | JSON Output | Notes |
|-------------|-----------|-------------|-------|
| `HUGEINT` (128-bit) | `BIGINT` / `OTHER` | **string** (decimal) | Exceeds JavaScript `Number.MAX_SAFE_INTEGER`; emit as string to avoid precision loss |
| `UHUGEINT` | `BIGINT` / `OTHER` | **string** (decimal) | Same as HUGEINT, unsigned |
| `UBIGINT` | `BIGINT` | **number** (if < 2^53) or **string** | Unsigned 64-bit — check magnitude before narrowing |
| `UTINYINT`, `USMALLINT`, `UINTEGER` | `INTEGER` | **number** | Safe in JavaScript number range |
| `UUID` | `VARCHAR` / `OTHER` | **string** | Standard UUID hex format |
| `INTERVAL` | `VARCHAR` / `OTHER` | **string** (ISO 8601 duration) | DuckDB interval format differs from PostgreSQL; emit as ISO 8601 `P...` string |
| `LIST<T>` | `ARRAY` | **JSON array** | Recursively normalize element type T |
| `STRUCT` | `STRUCT` / `OTHER` | **JSON object** | Field names become object keys; recursively normalize values |
| `MAP<K,V>` | `OTHER` | **JSON object** | Keys become object keys (stringified); recursively normalize values |
| `DATE` | `DATE` | **string** (`YYYY-MM-DD`) | ISO date format |
| `TIME` | `TIME` | **string** (`HH:MM:SS[.fff]`) | ISO time format |
| `TIMESTAMP` | `TIMESTAMP` | **string** (`YYYY-MM-DDTHH:MM:SS.fff`) | ISO 8601 datetime |
| `TIMESTAMP WITH TIME ZONE` | `TIMESTAMP WITH TIME ZONE` | **string** (ISO 8601 with offset) | Include timezone offset |
| `BLOB` | `BLOB` / `VARBINARY` | **string** (base64) | Base64-encoded binary data |
| `ENUM` | `VARCHAR` | **string** | Enum value as string |
| `BIT` | `BIT` / `OTHER` | **string** (binary literal) | e.g. `"01010101"` |
| `UNION` | `OTHER` | **JSON object** | Single key-value pair `{tag: value}` |

The normalizer must handle `java.sql.ResultSet.getObject()` returning
`org.duckdb.DuckDBStruct`, `DuckDBList`, `DuckDBMap` and convert them to the
above JSON shapes without throwing `ClassCastException`.

## 8. SQL Splitter And Risk Guard

### 8.1 Splitter

**Decision**: `GenericSqlStatementSplitter` for day-1. No DuckDB-specific
splitter required. See §7 for the rationale (no DELIMITER, no PL/SQL, no GO).

### 8.2 Risk Levels

- L1: `SELECT`, `WITH`, `DESCRIBE`, `EXPLAIN`, and the following PRAGMA forms
  **only**:
  - `PRAGMA database_list`
  - `PRAGMA database_size`
  - `PRAGMA show_databases`
  - `PRAGMA table_info('<name>')`
  - `PRAGMA storage_info('<name>')`
  - Any `PRAGMA` that does not modify configuration or filesystem state.
- L2: `INSERT`, `UPDATE` with `WHERE` predicate, safe `CREATE TABLE`,
  `CREATE VIEW` only in writable mode.
- L3: `DROP`, `TRUNCATE`, broad `ALTER`, `EXPORT DATABASE`, `IMPORT DATABASE`,
  `INSTALL`, `LOAD`, `CREATE SECRET`, external file/cloud access, and
  destructive filesystem-affecting operations.

### 8.3 Hard Reject (Day-1 Structured Unsupported)

The following are **not L3** — they are rejected immediately with a structured
unsupported response:

- `ATTACH` / `DETACH` — day-2 feature per §6.
- `COPY` — filesystem I/O, not implementable as a confirmed mutation.
- `EXPORT DATABASE` / `IMPORT DATABASE` — full filesystem read/write.
- Any PRAGMA that sets a configuration value (e.g.
  `PRAGMA temp_directory='/path'`, `PRAGMA memory_limit='...'`) — these bypass
  the sandbox.

### 8.4 SELECT-Shaped File/Network Access — Hard Reject

The following are **hard reject** (structured unsupported), not L3. Day-1
sandbox path policy cannot penetrate function parameters (e.g.
`read_csv('/etc/passwd')`), so confirming them via Workbench is unsafe.

- `read_csv`, `read_parquet`, `read_json`, `read_json_auto`
- `glob`, `parquet_metadata`, `parquet_scan`
- `httpfs` extension functions
- S3/http/https paths in any table function (`s3://`, `http://`, `https://`)
- `curl`, `http_get`, `http_post` (community extensions; listed for
  completeness — they cannot be invoked on day-1 because `INSTALL`/`LOAD` are
  hard-rejected per §8.3)
- Any table function that reads backend-local files or performs network I/O

These can only be elevated to L3 when the sandbox path policy is able to
validate function arguments (i.e. the `<path>` parameter of `read_csv(<path>)`
is forced through the data-root allowlist).

Chat-path SQL remains read-only and must not read arbitrary local files without
an explicit Workbench confirmation path.

## 9. Schema Discovery And ER Features

- Discovery must support `limit`, `cursor`, `pattern`, explicit describe, and
  large-schema bounds.
- Metadata should include schema, table/view type, columns, primary keys,
  foreign keys, indexes, generated columns, and comments where available.
- ER Inspector can be first-class only after DuckDB metadata tests prove foreign
  key discovery. **Day-1 fallback: `dialect_unsupported`** when
  `getImportedKeys` returns empty or throws — symmetric with ER Designer.
- ER Designer: **day-1 fallback is `dialect_unsupported`** (structured
  unsupported response for both EXPLAIN DDL and design serialization).
  `CREATE TABLE` DDL generation may be implemented as a stretch goal after the
  DuckDB-specific type-to-DDL mapping passes tests, but this is not a day-1
  requirement.

## 10. Diagnostics

- EXPLAIN and EXPLAIN ANALYZE can be implemented after plan output is mapped to
  the DataTalk diagnostics response contract.
- Index hints: structured unsupported (DuckDB has no explicit index advice).
- Lock info and pool status: structured unsupported (embedded engine, no
  connection pool visible at the SQL layer).
- Table space: possible via file size and DuckDB pragmas (`PRAGMA database_size`)
  after local file safety and permissions are designed.
- Terminate session and optimize table: structured unsupported (no equivalent
  in DuckDB).

## 11. Frontend

This design applies [client/DESIGN.md](../../client/DESIGN.md): semantic tokens,
one Chat/Workbench system, global Stage state, accessible controls, disabled
states, and i18n.

### 11.1 Connection Form

All control states below use `client/DESIGN.md` semantic tokens. Actual CSS
color values are mapped by the DESIGN.md token system (see the `semantic` block
in DESIGN.md for light/dark theme mappings).

- Label: `DuckDB`.
- Mode selector: segmented control (`In-memory` / `File`).
  - **Five states**:
    - idle → `bg.panel`, border `border.subtle`
    - hover → `interaction.hover` overlay on `bg.panel`
    - focus → `interaction.focusRing` (2px outer ring)
    - selected/active → `interaction.selected` background, text `text.strong`
    - disabled → `interaction.disabled` (opacity + non-pointer cursor, not color alone)
- File path input (visible only in `File` mode):
  - **Five states**:
    - idle → `bg.panel`, border `border.default`
    - hover → border `border.strong`
    - focus → `interaction.focusRing`
    - error → border `status.danger`, error helper text uses `status.danger` as text color
    - disabled → `interaction.disabled`
  - Helper text: "File path is backend-local (Spring Boot process filesystem)".
  - Validation: path must be under configured data root. If the user picks a
    path outside the data root (e.g. `~/Downloads/foo.duckdb`), the input shows
    the error state with a localized message: *"This file is outside the
    allowed DuckDB data directory. Please move the file to `{dataRoot}` or
    contact your administrator to change the data root setting."* No copy-to-root
    action is provided on day-1 — the user must manually relocate the file or
    change the application configuration.
- Read-only toggle: checkbox.
  - **States**:
    - unchecked (default) → standard checkbox, accent `accent.primary`
    - checked → `accent.primary` checkmark
    - disabled → `interaction.disabled` (immutable after connection save;
      label remains readable via `text.muted`)
  - Label: "Open in read-only mode".
  - Note: read-only is immutable after connection creation.
- External access disabled banner:
  - **Visible state**: `Alert` component with `Info` icon, surface
    `status.infoSurface`, border `status.info`, text `text.muted`. Content:
    "External file, object storage, and network access are not supported."
    Static informational banner — no disabled/loading states.
- File picker trigger button:
  - **Enabled state** (Tauri desktop): standard button, `bg.panel`, border
    `border.default`, hover → `interaction.hover`, focus →
    `interaction.focusRing`, active → `interaction.active`, disabled →
    `interaction.disabled`.
  - **Disabled state** (web/remote): button rendered with `interaction.disabled`,
    tooltip on hover: "File picker is only available in the desktop app".
- Host/port/username/password fields: **hidden** for DuckDB. The connection
  form must not render these fields.

### 11.2 Query Editor

- Schema selector: visible for DuckDB (normally `main`).
- Database selector: hidden (DuckDB has no host-level database concept; the
  file identity serves as the database context).
- Formatter: generic SQL until DuckDB-specific examples are validated.
- Outline: generic SQL parsing for tables/columns.

### 11.3 Diagnostics and Unsupported States

- EXPLAIN: rendered as plan tree if implemented; otherwise structured
  unsupported message.
- Index hints / lock info / terminate / optimize: structured unsupported badge
  with localized text.
- External file/extension risk handling: risk card shows L3 or unsupported
  message with localized explanation.

## 12. AI/MCP And Runtime Prompt

Expose `duckdb` in MCP schemas only after backend and frontend behavior is
complete.

### 12.1 Runtime Prompt Segment

The following segment is inserted into `AGENTS.md` under the `duckdb` kind
section:

```
## DuckDB Notes

- DuckDB is an embedded analytical database. Connections use file paths or
  in-memory mode, not host/port.
- `datatalk_execute_sql` remains read-only in the chat path.
- File operations (`COPY`, `EXPORT`, `IMPORT`, `INSTALL`, `LOAD`) and external
  file/network access (`read_csv`, `read_parquet`, `httpfs`, S3) are not
  supported. Use the SQL workbench for confirmed mutations.
- `ATTACH` and `DETACH` are not supported.
- DuckDB file paths are backend-local only.
- Read-only connections cannot execute mutations.
```

## 13. Acceptance And Verification

Automated gates:

- backend tests for kind routing, URL building, file path safety, read-only
  mode, V14 Flyway migration round-trip (add `read_only` column, rollback),
  metadata, target resolution, SQL execution, type normalization, splitter,
  risk, diagnostics, ER, MCP, and prompt;
- frontend tests for mode selector, file path input, read-only flag, picker,
  Query Editor schema context, formatter, outline, diagnostics, i18n, and
  accessibility;
- platform smoke for supported desktop OS targets if native driver packaging is
  affected;
- `cd server && mvn compile -q`;
- `cd client && npx tsc --noEmit`.

Manual or integration smoke must cover in-memory connection, file connection,
read-only open, schema discovery, Query Editor `SELECT`, mutation blocking,
external file/extension risk handling, and diagnostics or structured
unsupported output.

**Housekeeping**: Upon completion, also satisfy parent Wave B design §10
housekeeping — update `DATA_SOURCE_TYPE_COMPATIBILITY.md` support snapshot row
and Wave B child artifact tracking table.

> Reviewed by wallfacers, 2026-05-07. P0 4 items (L3/sandbox conflict, splitter
> undecided, metadata DB persistence, in-memory pool semantics) resolved. P1 5
> items (semantic tokens, in-memory URL validation, data-root UX fallback,
> sensible default, ER Inspector fallback) resolved. P2 5 items (WAL naming,
> community extensions, SET syntax, concurrent access wording, V14 migration test)
> resolved.
