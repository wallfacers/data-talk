# Data Source Coverage Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the approved data-source coverage governance design into Wave A child design and execution-plan artifacts without implementing database support yet.

**Architecture:** This is a documentation-only decomposition plan. The parent governance spec remains the rule source, and each Wave A kind gets an independent child design plus child plan before any backend/frontend implementation begins. Shared compatibility requirements are synchronized through `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`, while each child artifact owns its own driver, URL, metadata, splitter, risk, diagnostics, frontend, MCP, and verification decisions.

**Tech Stack:** Markdown, `rg`, git, DataTalk docs workflow, `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`, `client/DESIGN.md`.

---

## Spec Mapping

Spec: [Data Source Coverage Governance Design](../product-specs/2026-04-30-data-source-coverage-governance-design.md)

This plan implements these spec sections:

| Spec section | Plan coverage |
|---|---|
| §2 Design Inputs | Task 1 applies compatibility and frontend design gates to child artifacts. |
| §5 Governance Model | Tasks 2-5 create one child design and one child execution plan per Wave A kind. |
| §6 Candidate Matrix And Waves | Tasks 2-5 follow Wave A order: `sqlite`, `oracle`, `sqlserver`, `mariadb`. |
| §7 Child Spec Required Sections | Every child spec task uses the same required section checklist. |
| §8 Acceptance Matrix | Every child plan task writes acceptance gates for connection, target discovery, schema read, SQL, diagnostics, frontend, MCP, and docs. |
| §9 Test Strategy | Every child plan task records backend, frontend, prompt, docs, and manual smoke verification. |
| §10 Documentation And Housekeeping | Tasks 1 and 6 keep gate docs and indexes synchronized. |

## Scope Guard

This plan does not add drivers, connection kinds, frontend dropdown entries, MCP enum values, or runtime prompt claims. It creates the child artifacts that will later authorize those implementation changes. A database remains unsupported until its child implementation plan is executed, verified, and documented as complete.

## Files

Create during execution:

- `docs/product-specs/2026-04-30-data-source-coverage-sqlite-design.md`
- `docs/exec-plans/2026-04-30-data-source-coverage-sqlite-plan.md`
- `docs/product-specs/2026-04-30-data-source-coverage-oracle-design.md`
- `docs/exec-plans/2026-04-30-data-source-coverage-oracle-plan.md`
- `docs/product-specs/2026-04-30-data-source-coverage-sqlserver-design.md`
- `docs/exec-plans/2026-04-30-data-source-coverage-sqlserver-plan.md`
- `docs/product-specs/2026-04-30-data-source-coverage-mariadb-design.md`
- `docs/exec-plans/2026-04-30-data-source-coverage-mariadb-plan.md`

Modify during execution:

- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
- `docs/product-specs/index.md`
- `docs/exec-plans/index.md`

## Shared Child Spec Contract

Every child design created by this plan must include these exact top-level sections:

```markdown
Date: 2026-04-30
Status: Draft for review

## 1. Purpose
## 2. Compatibility Gate Application
## 3. Support Statement
## 4. Kind Naming
## 5. Connection And Persistence
## 6. Catalog, Database, Schema, And Target Resolution
## 7. SQL Execution
## 8. SQL Splitter And Risk Guard
## 9. Schema Discovery And ER Features
## 10. Diagnostics
## 11. Frontend
## 12. AI/MCP And Runtime Prompt
## 13. Acceptance And Verification
## 14. Open Questions
```

The file title must be one of these exact headings:

```markdown
# Data Source Coverage: SQLite Design
# Data Source Coverage: Oracle Design
# Data Source Coverage: SQL Server Design
# Data Source Coverage: MariaDB Design
```

Every child execution plan must use the standard implementation-plan header required by `superpowers:writing-plans` and must explicitly state that implementation cannot begin until the child design is reviewed and approved.

## Task 1: Gate And Index Preparation

**Files:**
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
- Modify: `docs/exec-plans/index.md`

- [x] **Step 1: Re-read the approved governance spec**

Run:

```bash
sed -n '1,340p' docs/product-specs/2026-04-30-data-source-coverage-governance-design.md
```

Expected: output includes the Wave A list `sqlite`, `oracle`, `sqlserver`, and `mariadb`, plus the child spec required sections.

- [x] **Step 2: Re-read the compatibility gate**

Run:

```bash
sed -n '1,920p' docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
```

Expected: output includes the current support snapshot, mandatory repository scan commands, backend checklist, frontend checklist, MCP checklist, and Definition of Done.

- [x] **Step 3: Re-read the frontend design contract**

Run:

```bash
sed -n '1,360p' client/DESIGN.md
```

Expected: output includes semantic token rules, Chat/Workbench single-system rules, global Stage state, accessibility, and i18n constraints.

- [x] **Step 4: Run the mandatory data-source scan**

Run:

```bash
rg -n "ConnectionKind|DbType|DbConnection|ConnectionRecord|databaseName|schemaName|driverType|kind\\("
rg -n "JdbcUrlBuilder|DriverManager|getConnection|setCatalog|setSchema|getCatalogs|getSchemas|getTables|getColumns"
rg -n "mysql|postgres|postgresql|sqlite|h2|oracle|sqlserver|mssql|mariadb|clickhouse|duckdb|doris|apache_doris|starrocks|hive|trino|presto|gauss|gaussdb|opengauss|dameng|dm8|kingbase|oceanbase|tidb|snowflake|bigquery|redshift|databricks|db2|hana|teradata|mongodb|elasticsearch|opensearch|redis"
rg -n "DiagnosticsProvider|EXPLAIN|indexHints|lockInfo|pool_status|table_space"
rg -n "SqlStatementSplitters|SqlStatementGuard|CalciteSqlRiskAnalyzer|SqlExecuteService|ExecuteSqlAction|ReadSchemaAction"
rg -n "UseTargetResolver|SessionDataContextService|SessionDataContextRepository|resolve_use_target|list_connection_targets"
rg -n "datatalk_.*sql|datatalk_.*schema|datatalk_.*connection|AGENTS.md|MCP|tools/list"
rg -n "DATABASE_TYPES|DbType|formatSql|sql-dialects|parse-sql-outline|connection-form|data-source"
```

Expected: commands exit `0` and confirm database-kind logic remains spread across backend connection, SQL execution, diagnostics, frontend data-source UI, Query Editor utilities, MCP schemas, and runtime prompt docs.

- [x] **Step 5: Add Wave A tracking to the compatibility gate**

Edit `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` in the Roadmap Expansion Candidates area and add this subsection:

```markdown
### Wave A Child Artifact Tracking

Wave A child artifacts are documentation gates, not support declarations. A kind
stays in its current support state until its child implementation plan is
executed, verified, and the support snapshot above is updated.

| Kind | Child design | Child plan | Current outcome |
|---|---|---|---|
| `sqlite` | `docs/product-specs/2026-04-30-data-source-coverage-sqlite-design.md` | `docs/exec-plans/2026-04-30-data-source-coverage-sqlite-plan.md` | Planned: frontend completion and runtime verification; support remains partial until implementation completes. |
| `oracle` | `docs/product-specs/2026-04-30-data-source-coverage-oracle-design.md` | `docs/exec-plans/2026-04-30-data-source-coverage-oracle-plan.md` | Planned: first-class support design from current stub state; support remains stub-only until implementation completes. |
| `sqlserver` | `docs/product-specs/2026-04-30-data-source-coverage-sqlserver-design.md` | `docs/exec-plans/2026-04-30-data-source-coverage-sqlserver-plan.md` | Planned: first-class support design from current legacy/stub state; support remains stub/legacy until implementation completes. |
| `mariadb` | `docs/product-specs/2026-04-30-data-source-coverage-mariadb-design.md` | `docs/exec-plans/2026-04-30-data-source-coverage-mariadb-plan.md` | Planned: explicit MariaDB design; support remains unsupported until implementation completes. |
```

- [x] **Step 6: Commit gate preparation**

Run:

```bash
git diff --check -- docs/DATA_SOURCE_TYPE_COMPATIBILITY.md docs/exec-plans/index.md
git add docs/DATA_SOURCE_TYPE_COMPATIBILITY.md docs/exec-plans/index.md
git commit -m "docs(data-sources): track wave a child artifacts"
```

Expected: `git diff --check` prints no output and the commit succeeds.

## Task 2: SQLite Child Spec And Plan

**Files:**
- Create: `docs/product-specs/2026-04-30-data-source-coverage-sqlite-design.md`
- Create: `docs/exec-plans/2026-04-30-data-source-coverage-sqlite-plan.md`
- Modify: `docs/product-specs/index.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`

- [x] **Step 1: Run SQLite-targeted scan**

Run:

```bash
rg -n "sqlite|SQLITE|org.sqlite|jdbc:sqlite|DATABASE_TYPES|formatSql|parse-sql-outline|ConnectionKind|JdbcUrlBuilder|DynamicSqlExecutionRepository|ReadSchemaAction|SqlExecuteService" server client/src docs/DATA_SOURCE_TYPE_COMPATIBILITY.md docs/product-specs docs/exec-plans
```

Expected: output shows SQLite support in backend URL/driver/runtime paths and missing or incomplete frontend connection form exposure.

- [x] **Step 2: Write SQLite child design**

Create `docs/product-specs/2026-04-30-data-source-coverage-sqlite-design.md` with the shared child spec contract. Required design decisions:

- support statement: target is first-class user SQLite by completing frontend exposure and verifying existing backend/runtime paths;
- canonical kind: `sqlite`;
- alias policy: no aliases unless implementation discovers a persisted legacy value;
- connection model: file path or `:memory:` semantics must be explicit; username/password are not required for normal SQLite;
- target model: SQLite has no server catalog and no schema search path comparable to PostgreSQL;
- execution model: use existing guarded SQL path and define context behavior as database-file scoped;
- splitter: generic splitter is acceptable only if tests cover SQLite comments, strings, pragmas, and multi-statement scripts targeted by day-1 support;
- diagnostics: define which capabilities are real and which return structured unsupported;
- frontend: add SQLite to connection form/picker only after backend verification; map fields so `databaseName` clearly represents the SQLite file path or memory name;
- MCP/prompt: do not claim cross-database schema switching for SQLite.

- [x] **Step 3: Write SQLite child execution plan**

Create `docs/exec-plans/2026-04-30-data-source-coverage-sqlite-plan.md` with task groups for:

- backend URL and connection-test verification;
- repository and metadata behavior checks;
- `read_schema`, target discovery, `resolve_use_target`, SQL execution, splitter, and risk tests;
- frontend connection form, picker, Query Editor context, formatter, outline, and i18n;
- diagnostics real/unsupported behavior;
- MCP schema and runtime prompt contract tests;
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` support snapshot update after implementation.

The plan must state that SQLite remains partial until the implementation tasks pass verification.

- [x] **Step 4: Register SQLite child artifacts**

Modify:

- `docs/product-specs/index.md`: add the SQLite design under section 8.
- `docs/exec-plans/index.md`: add the SQLite plan under Active.
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`: ensure the Wave A tracking row links are accurate and do not mark SQLite first-class.

- [x] **Step 5: Review SQLite artifacts**

Run:

```bash
rg -n "T[B]D|T[O]DO|fill[[:space:]]+in|supported[[:space:]]+now|first-class[[:space:]]+now" docs/product-specs/2026-04-30-data-source-coverage-sqlite-design.md docs/exec-plans/2026-04-30-data-source-coverage-sqlite-plan.md
git diff --check -- docs/product-specs/2026-04-30-data-source-coverage-sqlite-design.md docs/exec-plans/2026-04-30-data-source-coverage-sqlite-plan.md docs/product-specs/index.md docs/exec-plans/index.md docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
```

Expected: the `rg` command prints no lines; `git diff --check` prints no output.

- [x] **Step 6: Commit SQLite child artifacts**

Run:

```bash
git add docs/product-specs/2026-04-30-data-source-coverage-sqlite-design.md \
  docs/exec-plans/2026-04-30-data-source-coverage-sqlite-plan.md \
  docs/product-specs/index.md \
  docs/exec-plans/index.md \
  docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
git commit -m "docs(data-sources): plan sqlite coverage"
```

Expected: commit succeeds.

## Task 3: Oracle Child Spec And Plan

**Files:**
- Create: `docs/product-specs/2026-04-30-data-source-coverage-oracle-design.md`
- Create: `docs/exec-plans/2026-04-30-data-source-coverage-oracle-plan.md`
- Modify: `docs/product-specs/index.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`

- [x] **Step 1: Run Oracle-targeted scan**

Run:

```bash
rg -n "oracle|ORACLE|OracleDiagnosticsProvider|ojdbc|serviceName|SID|ConnectionKind|JdbcUrlBuilder|DbType|ReadSchemaAction|SqlExecuteService|DiagnosticsProvider" server client/src docs/DATA_SOURCE_TYPE_COMPATIBILITY.md docs/product-specs docs/exec-plans
```

Expected: output shows Oracle stubs in domain/diagnostics and missing connection kind, JDBC URL, driver dependency, frontend exposure, schema discovery, and SQL execution support.

- [x] **Step 2: Write Oracle child design**

Create `docs/product-specs/2026-04-30-data-source-coverage-oracle-design.md` with the shared child spec contract. Required design decisions:

- support statement: target is first-class Oracle only after closing the current stub gap;
- canonical kind: `oracle`;
- alias policy: no accepted aliases unless the child design explicitly defines them;
- connection model: distinguish service name, SID, host, port, username, password, optional role, and JDBC properties without overloading unrelated fields;
- target model: define catalog/schema behavior using Oracle owner/schema semantics;
- execution model: define `setSchema`, `ALTER SESSION`, or no context setter based on driver behavior;
- splitter: generic splitter is not automatically acceptable because PL/SQL blocks and slash terminators need explicit treatment;
- risk guard: include Oracle-specific commands such as `MERGE`, `CALL`, `BEGIN`, `GRANT`, `ALTER`, `TRUNCATE`, and `DROP`;
- diagnostics: decide `EXPLAIN PLAN` and `DBMS_XPLAN.DISPLAY` support separately from lock/pool/space capabilities;
- frontend: define kind-specific fields and i18n labels before exposing Oracle in the picker;
- MCP/prompt: prompt must not claim Oracle diagnostics or ER support until implemented.

- [x] **Step 3: Write Oracle child execution plan**

Create `docs/exec-plans/2026-04-30-data-source-coverage-oracle-plan.md` with task groups for:

- driver artifact and licensing decision;
- connection DTO/persistence changes for service name or JDBC properties;
- `JdbcUrlBuilder`, connection test, target discovery, schema read, SQL execution, splitter, and risk tests;
- diagnostics provider implementation or structured unsupported;
- frontend connection UI and Query Editor context;
- MCP schema, prompt, and contract tests;
- support snapshot update after verification.

The plan must state that Oracle remains stub-only until the implementation tasks pass verification.

- [x] **Step 4: Register Oracle child artifacts**

Modify:

- `docs/product-specs/index.md`: add the Oracle design under section 8.
- `docs/exec-plans/index.md`: add the Oracle plan under Active.
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`: keep the current support snapshot as stub-only.

- [x] **Step 5: Review Oracle artifacts**

Run:

```bash
rg -n "T[B]D|T[O]DO|fill[[:space:]]+in|supported[[:space:]]+now|first-class[[:space:]]+now" docs/product-specs/2026-04-30-data-source-coverage-oracle-design.md docs/exec-plans/2026-04-30-data-source-coverage-oracle-plan.md
git diff --check -- docs/product-specs/2026-04-30-data-source-coverage-oracle-design.md docs/exec-plans/2026-04-30-data-source-coverage-oracle-plan.md docs/product-specs/index.md docs/exec-plans/index.md docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
```

Expected: the `rg` command prints no lines; `git diff --check` prints no output.

- [x] **Step 6: Commit Oracle child artifacts**

Run:

```bash
git add docs/product-specs/2026-04-30-data-source-coverage-oracle-design.md \
  docs/exec-plans/2026-04-30-data-source-coverage-oracle-plan.md \
  docs/product-specs/index.md \
  docs/exec-plans/index.md \
  docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
git commit -m "docs(data-sources): plan oracle coverage"
```

Expected: commit succeeds.

## Task 4: SQL Server Child Spec And Plan

**Files:**
- Create: `docs/product-specs/2026-04-30-data-source-coverage-sqlserver-design.md`
- Create: `docs/exec-plans/2026-04-30-data-source-coverage-sqlserver-plan.md`
- Modify: `docs/product-specs/index.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`

- [x] **Step 1: Run SQL Server-targeted scan**

Run:

```bash
rg -n "sqlserver|mssql|SQLSERVER|Microsoft|jdbc:sqlserver|ConnectionKind|JdbcUrlBuilder|DbType|ReadSchemaAction|SqlExecuteService|DiagnosticsProvider" server client/src docs/DATA_SOURCE_TYPE_COMPATIBILITY.md docs/product-specs docs/exec-plans
```

Expected: output shows legacy enum or mapping traces and missing first-class connection kind, JDBC URL, driver, frontend exposure, metadata discovery, and diagnostics support.

- [x] **Step 2: Write SQL Server child design**

Create `docs/product-specs/2026-04-30-data-source-coverage-sqlserver-design.md` with the shared child spec contract. Required design decisions:

- support statement: target is first-class SQL Server after closing the current legacy/stub gap;
- canonical kind: `sqlserver`;
- alias policy: `mssql` can be accepted only through explicit normalization to `sqlserver`;
- connection model: define host, port, database, instance name, encryption/trust-server-certificate, username/password, and JDBC properties;
- target model: define database/catalog and schema behavior using SQL Server catalog plus schema semantics;
- execution model: define whether `setCatalog`, `setSchema`, or `USE` is used;
- splitter: handle `GO` batch separator explicitly or document that scripts are single-statement only until a splitter is implemented;
- risk guard: include `MERGE`, `EXEC`, `BACKUP`, `RESTORE`, `ALTER`, `DROP`, `TRUNCATE`, permissions, and database-level commands;
- diagnostics: define `SHOWPLAN`/execution-plan support and structured unsupported for capabilities that require privileges;
- frontend: define kind-specific security fields before exposing SQL Server in the picker;
- MCP/prompt: do not claim SQL Server ER or diagnostics support until implemented and tested.

- [x] **Step 3: Write SQL Server child execution plan**

Create `docs/exec-plans/2026-04-30-data-source-coverage-sqlserver-plan.md` with task groups for:

- driver artifact and URL builder;
- alias normalization and persistence behavior;
- connection test and target discovery;
- schema read, SQL execution, splitter, risk guard, diagnostics, frontend, and MCP tests;
- support snapshot update after verification.

The plan must state that SQL Server remains stub/legacy until the implementation tasks pass verification.

- [x] **Step 4: Register SQL Server child artifacts**

Modify:

- `docs/product-specs/index.md`: add the SQL Server design under section 8.
- `docs/exec-plans/index.md`: add the SQL Server plan under Active.
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`: keep the current support snapshot as stub/legacy.

- [x] **Step 5: Review SQL Server artifacts**

Run:

```bash
rg -n "T[B]D|T[O]DO|fill[[:space:]]+in|supported[[:space:]]+now|first-class[[:space:]]+now" docs/product-specs/2026-04-30-data-source-coverage-sqlserver-design.md docs/exec-plans/2026-04-30-data-source-coverage-sqlserver-plan.md
git diff --check -- docs/product-specs/2026-04-30-data-source-coverage-sqlserver-design.md docs/exec-plans/2026-04-30-data-source-coverage-sqlserver-plan.md docs/product-specs/index.md docs/exec-plans/index.md docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
```

Expected: the `rg` command prints no lines; `git diff --check` prints no output.

- [x] **Step 6: Commit SQL Server child artifacts**

Run:

```bash
git add docs/product-specs/2026-04-30-data-source-coverage-sqlserver-design.md \
  docs/exec-plans/2026-04-30-data-source-coverage-sqlserver-plan.md \
  docs/product-specs/index.md \
  docs/exec-plans/index.md \
  docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
git commit -m "docs(data-sources): plan sqlserver coverage"
```

Expected: commit succeeds.

## Task 5: MariaDB Child Spec And Plan

**Files:**
- Create: `docs/product-specs/2026-04-30-data-source-coverage-mariadb-design.md`
- Create: `docs/exec-plans/2026-04-30-data-source-coverage-mariadb-plan.md`
- Modify: `docs/product-specs/index.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`

- [x] **Step 1: Run MariaDB-targeted scan**

Run:

```bash
rg -n "mariadb|MariaDB|mysql|MySqlSqlStatementSplitter|ConnectionKind|JdbcUrlBuilder|DbType|ReadSchemaAction|SqlExecuteService|DiagnosticsProvider|DATABASE_TYPES|formatSql" server client/src docs/DATA_SOURCE_TYPE_COMPATIBILITY.md docs/product-specs docs/exec-plans
```

Expected: output shows MySQL first-class support and no separate MariaDB first-class support.

- [x] **Step 2: Write MariaDB child design**

Create `docs/product-specs/2026-04-30-data-source-coverage-mariadb-design.md` with the shared child spec contract. Required design decisions:

- support statement: target is explicit MariaDB support, not an accidental MySQL alias;
- canonical kind: `mariadb`;
- alias policy: no alias to `mysql` unless the design explicitly chooses canonicalization and documents user-visible consequences;
- connection model: decide MariaDB JDBC driver versus MySQL Connector/J compatibility, URL form, default port, timeout parameters, SSL fields, and license/runtime packaging;
- target model: define database/catalog and schema behavior and whether it matches existing MySQL handling;
- execution model: state whether MySQL DML batching and context setting are reused;
- splitter: decide whether `MySqlSqlStatementSplitter` is reused and add MariaDB-specific coverage for delimiter scripts if reused;
- risk guard: include MariaDB-specific syntax that differs from MySQL;
- diagnostics: decide whether MySQL provider SQL works against MariaDB or whether MariaDB gets a separate provider;
- frontend: add MariaDB label/default port only after backend support is real;
- MCP/prompt: mention MariaDB only when action schemas and runtime guidance match implementation.

- [x] **Step 3: Write MariaDB child execution plan**

Create `docs/exec-plans/2026-04-30-data-source-coverage-mariadb-plan.md` with task groups for:

- driver and URL decision;
- kind normalization and support snapshot;
- connection test, target discovery, schema read, SQL execution, splitter, risk guard, diagnostics, frontend, and MCP tests;
- support snapshot update after verification.

The plan must state that MariaDB remains unsupported until the implementation tasks pass verification.

- [x] **Step 4: Register MariaDB child artifacts**

Modify:

- `docs/product-specs/index.md`: add the MariaDB design under section 8.
- `docs/exec-plans/index.md`: add the MariaDB plan under Active.
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`: keep MariaDB in roadmap candidate status.

- [x] **Step 5: Review MariaDB artifacts**

Run:

```bash
rg -n "T[B]D|T[O]DO|fill[[:space:]]+in|supported[[:space:]]+now|first-class[[:space:]]+now" docs/product-specs/2026-04-30-data-source-coverage-mariadb-design.md docs/exec-plans/2026-04-30-data-source-coverage-mariadb-plan.md
git diff --check -- docs/product-specs/2026-04-30-data-source-coverage-mariadb-design.md docs/exec-plans/2026-04-30-data-source-coverage-mariadb-plan.md docs/product-specs/index.md docs/exec-plans/index.md docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
```

Expected: the `rg` command prints no lines; `git diff --check` prints no output.

- [x] **Step 6: Commit MariaDB child artifacts**

Run:

```bash
git add docs/product-specs/2026-04-30-data-source-coverage-mariadb-design.md \
  docs/exec-plans/2026-04-30-data-source-coverage-mariadb-plan.md \
  docs/product-specs/index.md \
  docs/exec-plans/index.md \
  docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
git commit -m "docs(data-sources): plan mariadb coverage"
```

Expected: commit succeeds.

## Task 6: Cross-Child Consistency Review

Status: Completed. This plan produced Wave A child design and plan artifacts only. No database support implementation was exposed.

**Files:**
- Modify: `docs/exec-plans/2026-04-30-data-source-coverage-governance-plan.md`
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
- Modify: `docs/product-specs/index.md`
- Modify: `docs/exec-plans/index.md`

- [x] **Step 1: Verify child artifact files exist**

Run:

```bash
ls docs/product-specs/2026-04-30-data-source-coverage-*-design.md
ls docs/exec-plans/2026-04-30-data-source-coverage-*-plan.md
```

Expected: output includes governance plus the four Wave A child design files and governance plus the four Wave A child plan files.

- [x] **Step 2: Verify all child artifacts are indexed**

Run:

```bash
rg -n "data-source-coverage-(sqlite|oracle|sqlserver|mariadb)-design" docs/product-specs/index.md
rg -n "data-source-coverage-(sqlite|oracle|sqlserver|mariadb)-plan" docs/exec-plans/index.md
```

Expected: each command prints four matching index entries.

- [x] **Step 3: Verify no child artifact marks support complete**

Run:

```bash
rg -n "support remains|remains unsupported|remains partial|remains stub|remains stub/legacy" docs/product-specs/2026-04-30-data-source-coverage-{sqlite,oracle,sqlserver,mariadb}-design.md docs/exec-plans/2026-04-30-data-source-coverage-{sqlite,oracle,sqlserver,mariadb}-plan.md
rg -n "supported[[:space:]]+now|first-class[[:space:]]+now|is now supported|is now first-class" docs/product-specs/2026-04-30-data-source-coverage-{sqlite,oracle,sqlserver,mariadb}-design.md docs/exec-plans/2026-04-30-data-source-coverage-{sqlite,oracle,sqlserver,mariadb}-plan.md
```

Expected: the first command prints explicit non-complete support statements for each child artifact. The second command prints no lines.

- [x] **Step 4: Verify prompt/UI support was not exposed**

Run:

```bash
rg -n "oracle|sqlserver|mariadb" client/src/features/settings/data-sources/connection-form-dialog.tsx server/data-talk-adapter/src/main/resources/agents/AGENTS.md server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java
```

Expected: output does not show newly exposed Oracle, SQL Server, or MariaDB support from this documentation-only plan.

- [x] **Step 5: Mark this governance plan complete**

All checklist items in this plan are now marked `- [x]`. Add a short status note under this task:

```markdown
Status: Completed. This plan produced Wave A child design and plan artifacts only. No database support implementation was exposed.
```

- [x] **Step 6: Move this plan to Completed in the index**

Edit `docs/exec-plans/index.md`:

- remove `Data Source Coverage Governance` from Active;
- add it to Completed with the completion date `2026-04-30`;
- keep all four child implementation plans in Active.

- [x] **Step 7: Final doc verification**

Run:

```bash
rg -n "T[B]D|T[O]DO|fill[[:space:]]+in|supported[[:space:]]+now|first-class[[:space:]]+now" docs/product-specs/2026-04-30-data-source-coverage-{sqlite,oracle,sqlserver,mariadb}-design.md docs/exec-plans/2026-04-30-data-source-coverage-{sqlite,oracle,sqlserver,mariadb}-plan.md docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
git diff --check -- docs/product-specs docs/exec-plans docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
git status --short
```

Expected: the first command prints no lines; `git diff --check` prints no output; `git status --short` shows only documentation files intentionally changed for this plan.

- [x] **Step 8: Commit governance completion**

Run:

```bash
git add docs/product-specs docs/exec-plans docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
git commit -m "docs(data-sources): complete wave a planning artifacts"
```

Expected: commit succeeds.

## Verification Summary For This Plan

Because this plan is documentation-only, execution does not require `mvn compile` or `npx tsc --noEmit`. Those commands belong in the child implementation plans once backend or frontend code changes begin. The required verification for this plan is:

```bash
rg -n "T[B]D|T[O]DO|fill[[:space:]]+in|supported[[:space:]]+now|first-class[[:space:]]+now" docs/product-specs/2026-04-30-data-source-coverage-{sqlite,oracle,sqlserver,mariadb}-design.md docs/exec-plans/2026-04-30-data-source-coverage-{sqlite,oracle,sqlserver,mariadb}-plan.md docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
git diff --check -- docs/product-specs docs/exec-plans docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
git status --short
```

Expected: no unresolved template or false-support lines, no whitespace errors, and only intentional documentation changes before commit.
