# Data Source Coverage: DuckDB Design

Date: 2026-05-01
Status: Draft for review

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

## 5. Connection And Persistence

Design inputs from DuckDB docs:

- Driver class: `org.duckdb.DuckDBDriver`.
- URL prefix: `jdbc:duckdb:`.
- `jdbc:duckdb:` creates an in-memory database.
- `jdbc:duckdb:/path/to/file` opens or creates a persistent database file.
- Read-only mode uses connection property `duckdb.read_only=true`.
- Mixing read-write and read-only connections to the same database file is
  unsupported by DuckDB docs.

Required implementation decisions:

- Driver artifact/version and native packaging must be reviewed for desktop
  runtime platforms.
- Required fields: connection mode (`memory` or `file`), file path when file
  mode is selected, and read-only flag.
- Sandbox contract: day-1 connections must resolve file-mode databases through
  a server-managed DuckDB data-root allowlist and persist only the normalized
  backend-local database path plus the applied sandbox policy. Users must not be
  able to choose an arbitrary sandbox root from the connection form. External
  network/object-store extensions such as `httpfs`/AWS access remain disabled or
  structured unsupported by that policy.
- Host, port, username, and password are N/A for day-1 DuckDB connections
  because DuckDB is embedded; frontend and API contracts must not force these
  fields.
- File paths are interpreted on the Spring Boot backend filesystem, not on the
  Tauri/webview filesystem. Day-1 file mode is limited to backend-local paths
  under a configured DataTalk DuckDB data root. Remote-backend file picking is
  unsupported until a separate upload/remote-path contract is designed.
- File path persistence must canonicalize paths under that data root, reject
  `..` traversal, reject symlinks that escape the root, reject sensitive
  absolute paths such as `/etc`, `/proc`, home-directory secrets, and platform
  credential stores, and redact paths in user-facing errors where needed.
- External file, object-store, and network access through DuckDB extensions are
  disabled or structured unsupported for day-1 unless explicitly added to the
  sandbox contract.

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

- Context: apply selected schema through `SET schema` or qualified names only
  after tests prove behavior.
- Multi-statement support: use DuckDB-specific or generic splitter with tests.
- Transactions: verify JDBC transaction behavior for file and in-memory modes.
- Batch DML: can be supported only after prepared statement and update-count
  behavior is tested.
- Result values: normalize decimals, huge integers, UUID, intervals, lists,
  structs, maps, dates/times, blobs, and driver-specific objects.
- File and extension access: external file reads, `COPY`, `EXPORT`, `IMPORT`,
  `ATTACH`, `INSTALL`, `LOAD`, secrets, and cloud extensions require explicit
  risk handling.
- SELECT-shaped file and network access is not L1 just because it appears in a
  `SELECT`. Calls such as `read_csv`, `read_parquet`, `read_json`, `glob`,
  `parquet_metadata`, `httpfs`, S3/http/https paths, extension-backed table
  functions, and any function that reads backend-local files or performs
  network I/O must be classified as L3 or structured unsupported until the
  sandbox contract proves them safe.

## 8. SQL Splitter And Risk Guard

Risk guard coverage must include:

- L1: `SELECT`, `WITH`, `DESCRIBE`, `EXPLAIN`, `PRAGMA` read-only forms only
  when they do not call file, extension, object-store, network, secret, or
  attached-database functions.
- L2: `INSERT`, `UPDATE` with predicate, safe `CREATE TABLE`, and `CREATE
  VIEW` only in writable mode.
- L3: `DROP`, `TRUNCATE`, broad `ALTER`, `COPY`, `EXPORT DATABASE`, `IMPORT
  DATABASE`, `ATTACH`, `DETACH`, `INSTALL`, `LOAD`, `CREATE SECRET`, external
  file/cloud access, SELECT-shaped file/network reads, and destructive
  filesystem-affecting operations.

Chat-path SQL remains read-only and must not read arbitrary local files without
an explicit Workbench confirmation path.

## 9. Schema Discovery And ER Features

- Discovery must support `limit`, `cursor`, `pattern`, explicit describe, and
  large-schema bounds.
- Metadata should include schema, table/view type, columns, primary keys,
  foreign keys, indexes, generated columns, and comments where available.
- ER Inspector can be first-class only after DuckDB metadata tests prove foreign
  key discovery.
- ER Designer DDL can be first-class only after DuckDB CREATE/ALTER support is
  tested and unsupported ALTER variants return `SkippedOp`.

## 10. Diagnostics

- EXPLAIN and EXPLAIN ANALYZE can be implemented after plan output is mapped.
- Index hints: structured unsupported unless DuckDB index advice is designed.
- Lock info and pool status: structured unsupported by default.
- Table space: possible via file size and DuckDB pragmas only after local file
  safety and permissions are designed.
- Terminate session and optimize table: structured unsupported unless a
  confirmable mutation design exists.

## 11. Frontend

This design applies [client/DESIGN.md](../../client/DESIGN.md): semantic tokens,
one Chat/Workbench system, global Stage state, accessible controls, disabled
states, and i18n.

Required frontend decisions:

- Label: `DuckDB`.
- Connection form uses mode selector (`In-memory` / `File`) and file path input
  instead of host/port fields.
- The file path label and help text must say the path is backend-local. A native
  desktop file picker may be enabled only when the backend declares local
  desktop affinity; remote backends must not show a local file picker.
- Read-only flag must be visible and accessible.
- Query Editor context exposes schema selector, not database host selectors.
- Formatter may use generic SQL until DuckDB-specific examples are validated.
- Unsupported extension/file/cloud access states must be localized and visible.

## 12. AI/MCP And Runtime Prompt

Expose `duckdb` in MCP schemas only after backend and frontend behavior is
complete.

Runtime prompt rules must state that DuckDB is embedded/local, that file and
extension operations can touch the user's filesystem or network, that
`datatalk_execute_sql` remains read-only, and that risky DuckDB commands must
go through Workbench confirmation.

## 13. Acceptance And Verification

Automated gates:

- backend tests for kind routing, URL building, file path safety, read-only
  mode, metadata, target resolution, SQL execution, type normalization,
  splitter, risk, diagnostics, ER, MCP, and prompt;
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
