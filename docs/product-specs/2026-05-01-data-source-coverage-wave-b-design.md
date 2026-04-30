# Data Source Coverage: Wave B Design

Date: 2026-05-01
Status: Draft for review

## 1. Purpose

Wave B covers analytics and OLAP SQL engines after Wave A has already started:
`apache_doris`, `starrocks`, `clickhouse`, `hive`, `trino`, `presto`, and
`duckdb`.

This design creates the documentation boundary for the whole wave. It does not
add drivers, frontend options, MCP schema values, runtime prompt claims, or
database support. Every Wave B kind remains unsupported until its own child
implementation plan is executed, verified, and the support snapshot in
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)
is updated.

## 2. Design Inputs

Mandatory project gates:

- [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)
  is the hard compatibility checklist.
- [client/DESIGN.md](../../client/DESIGN.md) applies to any future frontend
  connection form, picker, Query Editor context control, diagnostics state, and
  unsupported-state UI.
- [docs/product-specs/2026-04-30-data-source-coverage-governance-design.md](./2026-04-30-data-source-coverage-governance-design.md)
  requires one child design and one child plan per kind.

External technical references checked on 2026-05-01:

- Apache Doris documents MySQL protocol connectivity, MySQL JDBC Connector use,
  and FE query port default `9030`:
  <https://doris.apache.org/docs/3.x/db-connect/database-connect/>
- StarRocks documents its native JDBC driver
  `com.starrocks:starrocks-connector-j`, URL form
  `jdbc:starrocks://<fe_host>:<fe_query_port>/<catalog>.<database>`, default
  FE query port `9030`, and standard JDBC metadata APIs:
  <https://docs.starrocks.io/docs/integrations/JDBC_driver/>
- ClickHouse documents official `clickhouse-jdbc`, driver class
  `com.clickhouse.jdbc.ClickHouseDriver`, URL syntax, default HTTP port `8123`,
  and type mappings:
  <https://clickhouse.com/docs/integrations/language-clients/java/jdbc>
- Apache HiveServer2 documents `jdbc:hive2://...`, driver class
  `org.apache.hive.jdbc.HiveDriver`, default binary port `10000`, HTTP mode,
  SSL, Kerberos, and limited JDBC metadata concerns:
  <https://cwiki.apache.org/confluence/display/Hive/HiveServer2+Clients>
- Trino documents `io.trino:trino-jdbc`, driver class
  `io.trino.jdbc.TrinoDriver`, URL forms with optional catalog/schema, HTTP(S)
  coordinator access, and required access to `system.jdbc`:
  <https://trino.io/docs/current/client/jdbc.html>
- PrestoDB documents `com.facebook.presto:presto-jdbc`, URL forms with optional
  catalog/schema, `system.jdbc` access, and current release downloads:
  <https://prestodb.io/docs/0.285/installation/jdbc.html> and
  <https://prestodb.io/getting-started/>
- DuckDB documents `org.duckdb.DuckDBDriver`, URL prefix `jdbc:duckdb:`,
  in-memory and file-backed modes, read-only properties, and JDBC support:
  <https://duckdb.org/docs/lts/clients/java>

The references are design inputs, not implementation approvals. Each child plan
must re-check driver versions, licensing, runtime packaging, and connection
semantics immediately before code starts.

## 3. Goals

- Create Wave B child design and execution-plan artifacts for all seven kinds.
- Keep Wave A free to continue independently.
- Make OLAP-specific risks explicit before implementation: large metadata
  scans, catalog/schema ambiguity, distributed query cancellation, engine-owned
  file/object-store credentials, weak JDBC metadata, partial transaction
  semantics, and diagnostics privileges.
- Define a recommended implementation order without requiring all Wave B kinds
  to ship together.
- Keep unsupported capabilities structured and honest.

## 4. Non-Goals

- No Wave B driver dependencies are added in this work.
- No frontend connection type is exposed.
- No backend enum, URL builder, SQL splitter, diagnostics provider, MCP schema,
  or runtime prompt is changed.
- No Wave B database becomes first-class by virtue of these documents.
- No OLAP engine is treated as MySQL, PostgreSQL, or generic JDBC support
  without kind-specific tests.

## 5. Wave B Candidate Matrix

| Kind | Day-1 support target | Primary design risk |
|---|---|---|
| `apache_doris` | First-class SQL Workbench support through MySQL-protocol JDBC after proof. | MySQL compatibility is protocol-level, not a permission to reuse MySQL metadata, splitter, risk, diagnostics, or ER behavior blindly. |
| `starrocks` | First-class SQL Workbench support through native StarRocks JDBC after proof. | URL uses `catalog.database`; DataTalk must map catalog/database/schema without overloading one field. |
| `clickhouse` | First-class analytical SQL support, with mutation/transaction caveats explicit. | Driver type mappings, HTTP(S) protocol behavior, non-standard DDL/DML, and weak transaction assumptions. |
| `hive` | Partial or first-class support only if HiveServer2 metadata and auth decisions are verified. | Kerberos/HTTP/SSL configuration and limited JDBC metadata make UI and diagnostics easy to overclaim. |
| `trino` | First-class federated SQL support with catalog/schema context. | It is a query engine over catalogs, so metadata scans can be expensive and connector capabilities vary. |
| `presto` | First-class or partial federated SQL support, separate from Trino. | PrestoDB and Trino have diverged; reuse must be explicit, not assumed. |
| `duckdb` | First-class embedded/file analytical SQL support if file-safety rules are approved. | It is local/embedded, not host/port server JDBC; connection form, file paths, read-only mode, extensions, and mutation safety differ from server databases. |

## 6. Recommended Implementation Order

1. `apache_doris`: user-requested and close enough to existing MySQL paths to
   expose reuse decisions early.
2. `starrocks`: similar FE/query-port OLAP shape, but with a native JDBC driver
   and explicit `catalog.database` URL semantics.
3. `clickhouse`: common OLAP target with official JDBC support and important
   type/transaction caveats.
4. `duckdb`: highly testable local engine, but it needs a separate connection
   UX because it is file/in-memory based.
5. `trino`: catalog/schema federated engine with clear official JDBC docs.
6. `presto`: similar user model to Trino but separate driver, release stream,
   and compatibility surface.
7. `hive`: keep last unless customer demand pulls it forward, because
   Kerberos/HTTP/SSL and metadata limitations increase implementation risk.

The order is a recommendation. A child kind can be pulled forward when there is
strong user demand, a reliable test fixture, and a clear driver/license path.

## 7. Shared Wave B Requirements

Every child implementation must apply these requirements:

- Canonical kind strings are lower-case and persisted as their own kind.
- Aliases are accepted only at a single normalization boundary before
  persistence and routing. The preferred implementation anchor is a single
  application-layer `ConnectionKind.normalize(String)` boundary used by REST and
  action entry points before persistence, URL construction, metadata routing,
  MCP schemas, and frontend type generation. Scattered `equalsIgnoreCase`
  branches are not allowed.
- `database`, `schema`, `catalog`, and namespace mappings are documented in
  the child design and tested in target discovery.
- Metadata discovery must keep `limit`, `cursor`, `pattern`, explicit describe,
  and large-schema bounds.
- Query Editor L2/L3 confirmation remains the only mutation path. Chat-path
  `datatalk_execute_sql` remains read-only for unsupported or mutating SQL.
- Splitter selection must be explicit: proven reuse, a dedicated splitter, or a
  conservative generic splitter with tests.
- Diagnostics must be real or structured unsupported. Fake empty success is not
  allowed.
- ER Inspector and ER Designer are not inherited from another dialect without
  fixture-backed metadata and DDL tests.
- Frontend work must use semantic tokens, accessible controls, global Stage
  state, and i18n keys from `client/DESIGN.md`.
- Runtime `AGENTS.md` must not claim a Wave B capability before code and tests
  prove it.
- Trino and Presto implementations must prepare shared catalog/schema URL
  parsing, target-resolution, and connector-capability helpers in neutral
  package names before either engine ships. Trino-only names must not become the
  foundation that Presto later has to reverse-refactor.

## 8. Approval Process

Each child design starts as `Status: Draft for review`. Implementation may not
begin until the file is changed to exactly `Status: Approved`. The approval
change must add a short reviewer/date note near the end of the child design.
Child plans must check approval with `rg -n "^Status: Approved$" <design-file>`
and must stop if the design still says draft.

## 9. Child Artifact Set

This wave creates the following child artifacts:

| Kind | Child design | Child plan |
|---|---|---|
| `apache_doris` | `docs/product-specs/2026-05-01-data-source-coverage-apache-doris-design.md` | `docs/exec-plans/2026-05-01-data-source-coverage-apache-doris-plan.md` |
| `starrocks` | `docs/product-specs/2026-05-01-data-source-coverage-starrocks-design.md` | `docs/exec-plans/2026-05-01-data-source-coverage-starrocks-plan.md` |
| `clickhouse` | `docs/product-specs/2026-05-01-data-source-coverage-clickhouse-design.md` | `docs/exec-plans/2026-05-01-data-source-coverage-clickhouse-plan.md` |
| `duckdb` | `docs/product-specs/2026-05-01-data-source-coverage-duckdb-design.md` | `docs/exec-plans/2026-05-01-data-source-coverage-duckdb-plan.md` |
| `trino` | `docs/product-specs/2026-05-01-data-source-coverage-trino-design.md` | `docs/exec-plans/2026-05-01-data-source-coverage-trino-plan.md` |
| `presto` | `docs/product-specs/2026-05-01-data-source-coverage-presto-design.md` | `docs/exec-plans/2026-05-01-data-source-coverage-presto-plan.md` |
| `hive` | `docs/product-specs/2026-05-01-data-source-coverage-hive-design.md` | `docs/exec-plans/2026-05-01-data-source-coverage-hive-plan.md` |

## 10. Acceptance And Housekeeping

This documentation slice is complete only when:

- every Wave B child design and child plan file exists;
- `docs/product-specs/index.md` lists every child design and this overview;
- `docs/exec-plans/index.md` lists every child plan and this overview plan
  under Active;
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` has Wave B child artifact tracking;
- no wording marks a Wave B kind as currently supported;
- `git diff --check` and targeted placeholder scans pass.
