# Data Source Coverage: Wave B Artifact Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the Wave B data-source documentation matrix without implementing or exposing any Wave B database support.

**Architecture:** This is a documentation-only decomposition plan. The Wave B overview design owns shared OLAP rules and ordering, while each child design/plan owns its own driver, connection, metadata, SQL, splitter, risk, diagnostics, frontend, MCP, testing, and housekeeping decisions.

**Tech Stack:** Markdown documentation, DataTalk compatibility gate, product spec index, execution plan index.

---

Implementation support for Wave B must not start from this overview plan alone.
Each kind must use its own child plan after its child design is reviewed and
approved.

## Files

- Create: `docs/product-specs/2026-05-01-data-source-coverage-wave-b-design.md`
- Create: `docs/product-specs/2026-05-01-data-source-coverage-apache-doris-design.md`
- Create: `docs/product-specs/2026-05-01-data-source-coverage-starrocks-design.md`
- Create: `docs/product-specs/2026-05-01-data-source-coverage-clickhouse-design.md`
- Create: `docs/product-specs/2026-05-01-data-source-coverage-hive-design.md`
- Create: `docs/product-specs/2026-05-01-data-source-coverage-trino-design.md`
- Create: `docs/product-specs/2026-05-01-data-source-coverage-presto-design.md`
- Create: `docs/product-specs/2026-05-01-data-source-coverage-duckdb-design.md`
- Create: `docs/exec-plans/2026-05-01-data-source-coverage-apache-doris-plan.md`
- Create: `docs/exec-plans/2026-05-01-data-source-coverage-starrocks-plan.md`
- Create: `docs/exec-plans/2026-05-01-data-source-coverage-clickhouse-plan.md`
- Create: `docs/exec-plans/2026-05-01-data-source-coverage-hive-plan.md`
- Create: `docs/exec-plans/2026-05-01-data-source-coverage-trino-plan.md`
- Create: `docs/exec-plans/2026-05-01-data-source-coverage-presto-plan.md`
- Create: `docs/exec-plans/2026-05-01-data-source-coverage-duckdb-plan.md`
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
- Modify: `docs/product-specs/index.md`
- Modify: `docs/exec-plans/index.md`

### Task 1: Create Wave B Overview

- [x] **Step 1: Write the overview design**

Create `docs/product-specs/2026-05-01-data-source-coverage-wave-b-design.md`
with the Wave B scope, design inputs, candidate matrix, implementation order,
shared OLAP requirements, artifact set, and documentation acceptance criteria.

- [x] **Step 2: Write this overview plan**

Create `docs/exec-plans/2026-05-01-data-source-coverage-wave-b-plan.md` to
track the documentation slice. The plan must state that it does not implement
database support.

### Task 2: Create Per-Kind Child Artifacts

- [x] **Step 1: Create Apache Doris child artifacts**

Created the Apache Doris child design and plan. The design uses canonical
kind `apache_doris`, alias `doris`, MySQL protocol as a hypothesis to prove,
FE query port `9030`, MySQL Connector/J candidate routing, and explicit
structured unsupported behavior for unverified diagnostics and ER features.

- [x] **Step 2: Create StarRocks child artifacts**

Created the StarRocks child design and plan. The design uses canonical kind
`starrocks`, native JDBC driver candidate `com.starrocks:starrocks-connector-j`,
URL shape `catalog.database`, FE query port `9030`, and explicit catalog
target-resolution tests.

- [x] **Step 3: Create ClickHouse child artifacts**

Created the ClickHouse child design and plan. The design uses canonical kind
`clickhouse`, official JDBC driver candidate `com.clickhouse:clickhouse-jdbc`,
driver class `com.clickhouse.jdbc.ClickHouseDriver`, HTTP(S) URL semantics,
type-normalization tests, and mutation/transaction caveats.

- [x] **Step 4: Create Hive child artifacts**

Created the Hive child design and plan. The design uses canonical kind
`hive`, HiveServer2 URL shape, driver class `org.apache.hive.jdbc.HiveDriver`,
binary/HTTP/SSL/Kerberos choices, and conservative support until metadata and
auth behavior are verified.

- [x] **Step 5: Create Trino child artifacts**

Created the Trino child design and plan. The design uses canonical kind
`trino`, driver candidate `io.trino:trino-jdbc`, catalog/schema target
resolution, `system.jdbc` access requirements, and connector-capability caveats.

- [x] **Step 6: Create Presto child artifacts**

Created the Presto child design and plan. The design uses canonical kind
`presto`, driver candidate `com.facebook.presto:presto-jdbc`, catalog/schema
target resolution, and a separate compatibility path from Trino.

- [x] **Step 7: Create DuckDB child artifacts**

Created the DuckDB child design and plan. The design uses canonical kind
`duckdb`, driver class `org.duckdb.DuckDBDriver`, `jdbc:duckdb:` URL behavior,
file/in-memory/read-only connection fields, extension safety, and embedded
runtime caveats.

### Task 3: Register And Verify Documentation

- [x] **Step 1: Update compatibility tracking**

Add a Wave B child artifact tracking table to
`docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` without changing any current support
status.

- [x] **Step 2: Update indexes**

Register all Wave B child designs and the overview design in
`docs/product-specs/index.md`. Register all child plans and this overview plan
in `docs/exec-plans/index.md`, with child implementation plans Active and this
documentation-only overview plan moved to Completed during housekeeping.

- [x] **Step 3: Run documentation verification**

Run:

```bash
rg -n "data-source-coverage-(wave-b|apache-doris|starrocks|clickhouse|hive|trino|presto|duckdb)-(design|plan)" docs/product-specs/index.md docs/exec-plans/index.md docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
rg -n "supported[[:space:]]+now|first-class[[:space:]]+now|is[[:space:]]+now[[:space:]]+supported|is[[:space:]]+now[[:space:]]+first-class" docs/product-specs/2026-05-01-data-source-coverage-*-design.md docs/exec-plans/2026-05-01-data-source-coverage-{apache-doris,starrocks,clickhouse,hive,trino,presto,duckdb}-plan.md docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
rg -n "T[B]D|T[O]DO|fill[[:space:]]+in" docs/product-specs/2026-05-01-data-source-coverage-*-design.md docs/exec-plans/2026-05-01-data-source-coverage-{apache-doris,starrocks,clickhouse,hive,trino,presto,duckdb}-plan.md
git diff --check -- docs/product-specs docs/exec-plans docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
```

Expected:

- first command finds the new Wave B registrations;
- second and third commands print no unsupported support-claim or placeholder
  matches;
- `git diff --check` exits successfully.

### Task 4: Review Gate Before Implementation

- [x] **Step 1: Review child designs**

Each child design must be reviewed before its implementation plan starts. A
kind can move independently; Wave B does not require a single large release.

- [x] **Step 2: Keep runtime support unchanged**

Confirm no code, MCP schema, frontend option, runtime prompt, or support
snapshot text claims Wave B support after this documentation slice.

## Completion Notes

Status: Completed as a documentation-only slice. The seven child implementation
plans remain Active and do not declare runtime support. No backend, frontend,
MCP schema, or prompt implementation was changed by this plan.
