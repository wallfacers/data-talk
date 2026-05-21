# Data Source Coverage: Wave C Design

Date: 2026-05-08
Status: Draft for review (codex external review pending)

## 1. Purpose

Wave C is the "domestic / enterprise compatibility" track of Task 9 Data
Source Coverage Expansion. It covers six canonical kinds: `tidb`,
`opengauss`, `oceanbase`, `kingbase`, `dameng`, and `gaussdb`.

This design creates the documentation governance boundary for the whole wave.
It does **not** add drivers, change `ConnectionKind` constants, expose any
frontend connection option, change MCP schemas, or update `AGENTS.md` support
claims. Every Wave C kind remains unsupported until its own child design and
child plan are approved and executed, and the Current Support Snapshot in
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)
is updated.

## 2. Design Inputs

Mandatory project gates:

- [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)
  is the hard compatibility checklist for every database-related change.
- [client/DESIGN.md](../../client/DESIGN.md) applies to any future Wave C
  frontend connection form, picker, Query Editor context control, diagnostics
  state, and unsupported-state UI.
- [docs/product-specs/2026-04-30-data-source-coverage-governance-design.md](./2026-04-30-data-source-coverage-governance-design.md)
  requires one child design and one child plan per kind.
- [docs/product-specs/2026-05-01-data-source-coverage-wave-b-design.md](./2026-05-01-data-source-coverage-wave-b-design.md)
  established the "reuse-with-tests" and "structured unsupported" patterns
  Wave C builds on.

External technical references (design inputs, not implementation approvals;
each child plan must re-check driver versions, license, runtime packaging, and
connection semantics immediately before code starts):

- TiDB MySQL protocol compatibility (5.7 / 8.0), recommended JDBC driver
  `mysql:mysql-connector-j`, and TiDB-specific SQL surface (`SPLIT TABLE`,
  `ADMIN`, placement rules, `SHOW STATS_*`, optimistic/pessimistic transaction
  modes): <https://docs.pingcap.com/tidb/stable/dev-guide-choose-driver-or-orm>
- openGauss official JDBC driver `org.opengauss:opengauss-jdbc`, URL prefix
  `jdbc:opengauss://`, MulanPSL2 license, PostgreSQL protocol fork:
  <https://docs.opengauss.org/en/docs/latest/docs/DeveloperGuide/jdbc.html>
- OceanBase official JDBC driver `com.oceanbase:oceanbase-client`, URL prefix
  `jdbc:oceanbase://`, dual MySQL-mode and Oracle-mode tenant model:
  <https://en.oceanbase.com/docs/community-jdbc-en>
- KingbaseES official JDBC driver `cn.com.kingbase:kingbase8`, URL prefix
  `jdbc:kingbase8://`, PG-compatible default, optional Oracle mode, vendor
  download portal: <https://www.kingbase.com.cn/>
- Dameng (达梦) official JDBC artifacts `com.dameng:DmJdbcDriver18` and
  `com.dameng:Dm8JdbcDriver`, URL prefix `jdbc:dm://`, Oracle-like SQL surface,
  vendor download portal: <https://eco.dameng.com/>
- Huawei GaussDB JDBC driver `com.huawei.gaussdb:gaussdbjdbc` (commercial,
  Huawei Cloud distribution), product-line split among GaussDB (centralized),
  GaussDB (distributed), and GaussDB(DWS): <https://www.huaweicloud.com/product/gaussdb.html>

The references are design inputs only. A driver, URL form, or compatibility
claim is not approved until the corresponding child plan ships, tests pass, and
the support snapshot is updated.

## 3. Goals

- Create child design and execution-plan artifacts for all six Wave C kinds.
- Keep Wave B free to continue independently (Trino remains the only Wave B
  kind without first-class support as of 2026-05-08; Wave C does not block it).
- Make the four Wave-C-specific risks explicit before any implementation:
  driver distribution and license, compatibility-mode handling, product-line
  fragmentation (GaussDB three lines, Dameng multiple driver artifacts), and
  test fixture availability.
- Define a recommended implementation order without requiring all Wave C kinds
  to ship together.
- Keep unsupported capabilities structured and honest. Fake-empty results,
  prompt-only descriptions without code, and silent fallback to PostgreSQL or
  MySQL paths are not allowed.

## 4. Non-Goals

- No Wave C driver dependencies are added in this work.
- No Wave C frontend connection type, picker option, formatter language, or
  schema-context control is exposed.
- No backend `ConnectionKind` constant, `JdbcUrlBuilder` branch, SQL splitter,
  diagnostics provider, MCP schema value, runtime prompt rule, or
  `ConnectionRecord` field is changed.
- No Wave C database becomes first-class by virtue of these documents.
- No domestic database is treated as a PostgreSQL or MySQL alias just because
  it speaks PG or MySQL protocol on the wire. Reuse must be proven by
  kind-specific tests at every reuse point.
- This design does not decide whether `oceanbase`, `kingbase`, or `dameng`
  ever ship Day-2 support for their non-default compatibility mode. That
  belongs in each child design or a Wave C follow-up.
- Wave C does not include non-SQL sources (Band E in the governance roadmap),
  cloud warehouses (Band D), or any kind outside the six canonical names
  listed in section 1.

## 5. Wave C Candidate Matrix

Each row gives the Day-1 support target, protocol family, driver candidate
(with distribution / license shape), and primary design risk. URL forms,
specific risk rules, and connection-form fields are decided in each kind's
child design, not here.

| Kind | Day-1 support target | Protocol family | Driver candidate / distribution | Primary design risk |
|---|---|---|---|---|
| `tidb` | First-class SQL Workbench; MySQL protocol reuse proven by kind-specific tests; TiDB-enhanced SQL gets independent risk rules. | MySQL 5.7 / 8.0 protocol | `mysql:mysql-connector-j` (already in `data-talk-infrastructure/pom.xml`, zero new dependency); GPL-2.0 with Universal FOSS Exception; Maven Central. | Protocol compatibility ≠ behavioral compatibility. Placement rules, `SPLIT TABLE`, `ADMIN`, `SHOW STATS_*`, optimistic/pessimistic transaction model, CTE limitations, and TiFlash hints need independent risk rules. PD/TiKV topology awareness is not Day-1. |
| `opengauss` | First-class SQL Workbench; PG-fork protocol reuse proven by kind-specific tests; openGauss-enhanced SQL gets independent risk rules. | PostgreSQL protocol fork | `org.opengauss:opengauss-jdbc`; MulanPSL2; Maven Central. | PG protocol-compatible but with SQL-layer divergences: `COPY` behavior, row-store vs column-store table syntax, extended `pg_catalog` views, missing `REINDEX CONCURRENTLY`, sequence permission model. The PostgreSQL splitter is reusable only after dollar-quoted / PL/pgSQL equivalence tests. |
| `oceanbase` | Day-1 MySQL-mode first-class only; Oracle-mode marked `dialect_unsupported` until Day-2. | MySQL protocol (mode-MySQL) / Oracle protocol (mode-Oracle) | `com.oceanbase:oceanbase-client`; Apache 2.0; Maven Central. | One canonical kind, two compatibility modes. Connection form needs `tenant` and `compatibilityMode` fields. Catalog/schema semantics shift with mode. Splitter / risk / diagnostics / ER must branch by mode. MySQL-mode is **not** an alias of `mysql`. **Tenant is not a namespace**: in OceanBase a tenant is a resource-isolated, near-instance-level boundary (independent users, independent SYS schema, often routed through OBProxy). It is **not** comparable to PostgreSQL `schema` or MySQL `database`. Connection user format `user@tenant#cluster` and authentication implications are decided in the child design. |
| `kingbase` | Day-1 PG-mode first-class only; Oracle-mode marked `dialect_unsupported` until Day-2. | PostgreSQL protocol (default) / Oracle protocol (optional) | `cn.com.kingbase:kingbase8`; commercial; partial Maven Central visibility, **vendor download portal preferred for reproducibility**. | Dual mode track. Alias `kingbasees` normalized at the `ConnectionKind` boundary. Driver version is bound to server version. `SYS_*` views vs `pg_*` views. PG splitter reuse must be proven by dollar-quoted / PL/SQL equivalence tests. |
| `dameng` | Day-1 first-class for SELECT + L1 metadata; DDL/DML risk rules and structured unsupported diagnostics + ER. | Oracle-like proprietary protocol | `com.dameng:DmJdbcDriver18` or `com.dameng:Dm8JdbcDriver`; commercial; some historical versions on Maven Central, **vendor download portal preferred**; artifact selection decided in this wave's child design. | Driver artifact naming is fragmented (DM7 / DM8 / public). CHAR / VARCHAR2 / NUMBER type normalization. Splitter selection for procedural blocks (`DECLARE...BEGIN...END;/`). DBA system schema filter list (`SYS`, `SYSDBA`, `CTISYS`, etc.). |
| `gaussdb` | **Day-1 deliverable is product-line split + driver decision + license review only**; no first-class implementation in this wave. | GaussDB (centralized / distributed) ≈ openGauss line; GaussDB(DWS) diverges further. | `com.huawei.gaussdb:gaussdbjdbc` (Huawei Cloud); commercial; **not on Maven Central — vendor mirror only**; license and redistribution clauses must be reviewed in this wave. | Three product lines with materially different behavior. Driver redistribution restrictions may block CI. Whether `gaussdb` and `opengauss` share a child plan is decided inside the `gaussdb` child design, not here. |

Day-1 unsupported set across all six kinds (consistent with Wave B): ER
Inspector, ER Designer DDL generation, and diagnostics real execution
(EXPLAIN, index hints, lock info, pool status, table space) all return
structured `dialect_unsupported`.

## 6. Recommended Implementation Order

The order is sorted by driver availability → protocol reuse degree →
product-line clarity → test fixture reachability. It is a recommendation, not
a hard constraint. Any kind may be pulled forward when there is strong user
demand, but the gating remains unchanged: driver reachable + fixture reachable
+ child design approved before opening a child plan.

1. **`tidb`** — lowest risk; validates the Wave C governance flow.
   Zero new driver dependency (reuses the existing `mysql-connector-j`),
   official Docker `pingcap/tidb` available, and the same MySQL-protocol reuse
   shape as the already-shipped Wave B `apache_doris`. Use `tidb` to lock down
   the "MySQL-protocol reuse with kind-specific tests" engineering kit.

2. **`opengauss`** — validates the PG-fork reuse pattern.
   Open-source driver on Maven Central, official Docker `enmotech/opengauss`
   available. Maximum overlap with PostgreSQL stack: splitter reuse candidate
   is `PostgresJdbcSqlStatementSplitter`, risk rules baseline from the existing
   PG rules plus openGauss increments. Output the "PG-fork reuse" engineering
   kit.

3. **`oceanbase`** — validates the multi-mode policy.
   Open-source driver on Maven Central, official Docker
   `oceanbase/oceanbase-ce` available. **Day-1 only MySQL-mode first-class.**
   Lock down `compatibilityMode` field, `tenant` field, connection form,
   target resolution multi-mode skeleton; Oracle-mode stays Day-2.

4. **`kingbase`** — validates commercial PG-fork plus multi-mode.
   Commercial driver; vendor download portal vs internal Maven mirror policy
   decided first. Fixture may require trial license. By this point this step
   becomes a composition of the kits from steps 2 and 3.

5. **`dameng`** — validates Oracle-like dialect plus commercial driver
   artifact selection. Commercial driver; artifact naming (DM7 / DM8 /
   public) must be pinned in the child design. Day-1 does **not** alias the
   Wave A `oracle` stack: the Wave A Oracle splitter does not support PL/SQL,
   and Dameng equally does not require procedural-block support Day-1, but
   reuse goes through an explicit splitter decision and tests, not through an
   `equalsIgnoreCase("oracle")` shortcut.

6. **`gaussdb`** — highest risk, scheduled last.
   Day-1 deliverable is product-line split (centralized / distributed / DWS) +
   driver decision + license review. A legitimate outcome of this step is
   "Wave C does not implement `gaussdb`; defer to Wave C follow-up or Wave D."
   Whether `gaussdb` shares a child plan with `opengauss` is decided inside
   the `gaussdb` child design.

Parallelism: Steps 1 (`tidb`) and 2 (`opengauss`) may run in parallel — their
drivers and fixtures do not block each other and their reuse directions
(MySQL-protocol vs PG-fork) cross-validate. Steps 3 (`oceanbase`), 4
(`kingbase`), and 5 (`dameng`) are independent of each other and may run in
parallel **after** steps 1 and 2 land their reuse kits (`MySqlProtocolReuseRule`
and `PgForkReuseRule`). Step 6 (`gaussdb`) should not start until steps 1
through 5 have at least their child designs approved.

## 7. Domestic-DB Specific Policies

These five policies are the substantive Wave C delta over Wave B. Each is a
hard constraint on every child design and child plan.

### 7.1 Driver Distribution & License Gate

Each child design **MUST** answer the following before approval (in writing,
not "to be decided later"):

- **Driver artifact**: `groupId:artifactId:version` pinned exactly.
- **License**: MIT / Apache 2.0 / MulanPSL2 / commercial / etc. with the
  primary-source URL. For commercial drivers, list this repository's binary
  redistribution rights explicitly. The typical answer is "no
  redistribution; CI pulls from internal Maven mirror or offline jar."
- **Distribution path**: choose exactly one of (a) Maven Central direct, (b)
  internal company Maven mirror with the repository URL and mirror rule, or
  (c) vendor download portal offline jar. Where the offline jar lives in the
  repo (for example `server/data-talk-infrastructure/lib/` or another agreed
  location) must be decided in this wave. "Install on developer machines
  manually" is **not** acceptable.
- **CI reproducibility**: the CI runner must be able to pull the driver. If
  the driver is offline-distributed, the child plan must include a "driver
  bootstrap" task.
- For `gaussdb`, `kingbase`, and `dameng`, this section is the highest
  priority of the corresponding child design. If it does not pass, the child
  plan does not start.

### 7.2 Compatibility-Mode Policy

Domestic databases commonly support Oracle-mode / MySQL-mode / PG-mode.
Wave C policy:

- **Each compatibility mode does not get a new canonical kind.** `oceanbase` /
  `kingbase` stay as single canonical kinds. Mode is connection-level
  metadata.
- A new field `compatibilityMode` is added to `ConnectionRecord` /
  `ConnectionCreateRequest` / `ConnectionUpdateRequest` to carry the mode. It
  is **not** smuggled into `databaseName`, URL parameters, or
  `jdbcParams`. The exact field shape and Flyway migration are designed in
  the first child design that needs it (`oceanbase`).
- **`tenant` field is kind-specific, not part of `MultiModeConnectionShape`.**
  Tenant exists in OceanBase (resource-isolated near-instance boundary) but
  not in `kingbase` or `dameng`. The `oceanbase` child design decides whether
  `tenant` becomes a dedicated `ConnectionRecord` column (recommended for
  type-safe target resolution and migration safety) or is composed into the
  username at the application boundary. Either way, `tenant` is required at
  connection time for OceanBase MySQL-mode and is **not** smuggled into
  `databaseName` or `jdbcParams`. Other multi-mode kinds adding new
  kind-specific fields follow the same rule: fields go on
  `ConnectionRecord` with their own Flyway migration; they do not extend
  `MultiModeConnectionShape`.
- Each mode in each child design gets its own splitter selection, risk rule
  set, metadata discovery path, target resolution, prompt section, and
  unsupported matrix.
- Day-1 ships a single mode first-class per kind. Each child design names
  the chosen mode (`oceanbase` ships MySQL-mode, `kingbase` ships PG-mode per
  section 5). All other modes return structured `dialect_unsupported`.
- Mode is a connection-level configuration. Runtime / `USE` / session-level
  mode switching is not allowed Day-1.
- Frontend connection form renders the mode selector with `client/DESIGN.md`
  semantic tokens; user-visible strings go through `client/src/i18n/messages.ts`.

### 7.3 Reuse-With-Tests Rule

PG-fork (`opengauss`, `kingbase` PG-mode) and MySQL-protocol (`tidb`,
`oceanbase` MySQL-mode) **may** reuse PostgreSQL/MySQL splitter, risk,
diagnostics, and metadata discovery, but every reuse point must:

1. Carry kind-specific test coverage: at minimum splitter equivalence tests
   and risk-rule boundary tests.
2. Be explicitly listed in the child design's "Reuse Inventory" and
   "Independent Inventory."
3. Route through a single `ConnectionKind.normalize(String)` boundary at
   application-layer REST and action entry points. Scattered
   `equalsIgnoreCase("postgresql")` or `equalsIgnoreCase("mysql")` branches
   are **forbidden**, consistent with Wave B.

### 7.4 Test Fixture Tiering

Each child design assigns the kind to one of three fixture tiers:

- **T1 — automated fixture available**: official Docker, Testcontainers, or
  embedded engine reachable in CI. Today: `tidb` (`pingcap/tidb`),
  `opengauss` (`enmotech/opengauss`), `oceanbase` (`oceanbase/oceanbase-ce`).
  Child plan **must** include integration tests.
- **T2 — restricted fixture**: trial license or restricted image required but
  reproducible. Today's likely placement: `kingbase` (trial license),
  `dameng` (trial license). Child plan includes manual smoke scripts plus
  unit tests plus documented prerequisites.
- **T3 — fixture not obtainable**: no reproducible fixture path. Today's
  likely placement: parts of `gaussdb`. Day-1 first-class is **forbidden**;
  the child plan must document a "code complete + user self-validation"
  boundary explicitly.

The Current Support Snapshot in
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)
upgrades a kind to first-class only when its fixture tier is T1 or T2. T3
upgrades require an explicit follow-up plan.

### 7.5 AGENTS.md / Prompt Contract Rule

- Until a Wave C kind's child plan passes verification,
  `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` and MCP
  schemas **must not** mention the kind name.
- Chinese product names (达梦, 人大金仓 / 金仓, 华为高斯, 蚂蚁 OceanBase /
  沃趣 OceanBase) are allowed inside prompt rules **only** to help the runtime
  recognize user-typed requests. They are **not** valid canonical kinds, **not** valid schema
  enum values, and **not** valid alias targets.
- Each child design includes a prompt-rules section structurally identical to
  the Wave A Oracle / MariaDB child designs, and the prompt update ships in
  the same PR as the code.

## 8. Shared Wave C Requirements

Every Wave C child design and child plan **MUST** apply:

### Shared with Wave B

- Canonical kind strings are lower-case and persisted as their own kind.
- Aliases (such as `kingbasees`, `dm`, `dm8`) are normalized at one application-layer
  `ConnectionKind.normalize(String)` boundary before persistence and routing.
  Scattered `equalsIgnoreCase` branches are not allowed.
- `database` / `schema` / `catalog` / namespace / tenant mappings are
  documented in the child design and tested in target discovery.
- Metadata discovery preserves `limit`, `cursor`, `pattern`, explicit
  describe, and large-schema bounds.
- Query Editor L2 / L3 confirmation is the only mutation path. Chat-path
  `datatalk_execute_sql` remains read-only for unsupported or mutating SQL.
- Splitter selection is explicit: proven reuse, dedicated splitter, or
  conservative generic splitter with tests.
- Diagnostics are real or structured unsupported. Fake empty success is not
  allowed.
- ER Inspector and ER Designer are not inherited from another dialect without
  fixture-backed metadata and DDL tests.
- Frontend uses `client/DESIGN.md` semantic tokens, accessible controls,
  global Stage state, and i18n keys.
- Runtime `AGENTS.md` does not declare a Wave C capability before its code
  and tests prove it.

### Wave C delta (from section 7)

- Driver distribution and license decisions land in a dedicated "Driver
  Decision" section of each child design.
- Multi-mode kinds introduce the `compatibilityMode` field; persistence and
  `ConnectionRecord` field changes ship with a Flyway migration.
- Day-1 single-mode first-class; other modes return structured
  `dialect_unsupported`.
- Test fixtures are tiered T1 / T2 / T3; T3 may not claim Day-1 first-class.
- Chinese product names live only inside prompt content, never inside
  canonical / schema enum values.

### Cross-kind shared engineering kits

Each Wave C step produces a reusable kit that the next step consumes. Naming
follows neutral domain semantics so later kinds reuse without rewrite:

- **`MySqlProtocolReuseRule`** (produced by step 1, `tidb`) — equivalence
  tests covering the `MySqlSqlStatementSplitter`, MySQL diagnostics provider,
  and MySQL metadata reuse decisions. Also consumed by `oceanbase` MySQL-mode
  in step 3.
- **`PgForkReuseRule`** (produced by step 2, `opengauss`) — equivalence tests
  covering `PostgresJdbcSqlStatementSplitter` and PostgreSQL risk rule reuse,
  plus the openGauss-specific increments. Also consumed by `kingbase` PG-mode
  in step 4.
- **`MultiModeConnectionShape`** (produced by step 3, `oceanbase`) — the
  `compatibilityMode` field, conditional connection form rendering, and
  target-resolution multi-mode skeleton. Also consumed by `kingbase` in step
  4. Per §7.2, kind-specific fields such as OceanBase `tenant` live on
  `ConnectionRecord` (with their own Flyway migration), **not** inside this
  shared shape — the shape carries only mode-shaped concerns common to all
  multi-mode kinds.

Naming is intentionally neutral. `OceanBaseConnectionShape` or
`OpenGaussSplitterRule` are **not** acceptable names; they create a
later-refactor debt when `kingbase` reuses the same code.

### Documentation sync

Every child plan ships its verification PR with these updates:

- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` — Current Support Snapshot row,
  ER matrix row, and Wave C Tracking outcome.
- `docs/exec-plans/index.md` — move the corresponding plan from Active to
  Completed.
- `docs/product-specs/index.md` — keep the child design's row in sync.
- `docs/generated/db-schema.md` — only when a Flyway migration is shipped.

## 9. Wave C Child Artifact Tracking

Wave C child artifacts are documentation gates, not support declarations. A
kind stays unsupported until its child implementation plan is executed,
verified, and the support snapshot is updated. The table below is mirrored
into [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)
when this design is approved. Outcomes are written back as each child plan
completes.

| Kind | Child design | Child plan | Current outcome |
|---|---|---|---|
| `tidb` | `docs/product-specs/2026-05-08-data-source-coverage-tidb-design.md` | `docs/exec-plans/2026-05-08-data-source-coverage-tidb-plan.md` | Planned: MySQL-protocol reuse with kind-specific tests; TiDB enhancements (placement rules, `SPLIT TABLE`, `ADMIN`, `SHOW STATS_*`, transaction model hints) get independent risk rules; structured unsupported diagnostics and ER. |
| `opengauss` | `docs/product-specs/2026-05-08-data-source-coverage-opengauss-design.md` | `docs/exec-plans/2026-05-08-data-source-coverage-opengauss-plan.md` | Planned: PG-fork reuse with kind-specific tests; openGauss row/column store and `pg_catalog` extensions get independent risk; structured unsupported diagnostics and ER. |
| `oceanbase` | `docs/product-specs/2026-05-08-data-source-coverage-oceanbase-design.md` | `docs/exec-plans/2026-05-08-data-source-coverage-oceanbase-plan.md` | Planned: Day-1 MySQL-mode first-class only; `tenant` and `compatibilityMode` fields introduced (Flyway migration); Oracle-mode `dialect_unsupported`; structured unsupported diagnostics and ER. |
| `kingbase` | `docs/product-specs/2026-05-08-data-source-coverage-kingbase-design.md` | `docs/exec-plans/2026-05-08-data-source-coverage-kingbase-plan.md` | Planned: Day-1 PG-mode first-class only via `PgForkReuseRule`; commercial driver distribution path decided in child design; Oracle-mode `dialect_unsupported`; `kingbasees` alias normalized at the `ConnectionKind` boundary. |
| `dameng` | `docs/product-specs/2026-05-08-data-source-coverage-dameng-design.md` | `docs/exec-plans/2026-05-08-data-source-coverage-dameng-plan.md` | Planned: Day-1 SELECT + L1 metadata + DML/DDL risk rules; commercial driver artifact selection (`Dm8JdbcDriver` vs `DmJdbcDriver18`) pinned in child design; structured unsupported diagnostics and ER; PL/SQL block splitter explicitly out of scope Day-1. |
| `gaussdb` | `docs/product-specs/2026-05-08-data-source-coverage-gaussdb-design.md` | _Created only if child design concludes feasibility (per §10.3); otherwise the kind is closed with `Blocked: <reason>` outcome and no child plan is written._ | Planned: child design Day-1 deliverable is product-line split (centralized / distributed / DWS) + driver decision + license review only; first-class implementation deferred until driver redistribution and fixture path are unblocked. |

## 10. Approval Process

### Document hierarchy

- This umbrella is a **governance document**, not an implementation backlog.
  It does not have a corresponding `docs/exec-plans/` umbrella plan, matching
  the Wave B precedent (`2026-05-01-data-source-coverage-wave-b-design.md`
  has no `wave-b-plan.md`). Approving this umbrella does **not** trigger
  `superpowers:writing-plans` for itself.
- Each Wave C kind goes through its **own complete lifecycle**:
  ① brainstorming (`superpowers:brainstorming`) → child design at
  `docs/product-specs/2026-05-08-data-source-coverage-<kind>-design.md`,
  ② writing-plans (`superpowers:writing-plans`) → child plan at
  `docs/exec-plans/2026-05-08-data-source-coverage-<kind>-plan.md`,
  ③ implementation. Six child cycles will run, sequenced per §6.
- Child designs are **standalone documents**, not embedded in child plans.
  Each child design is approved separately before its child plan is written.
  This matches Wave A (sqlite / oracle / sqlserver / mariadb) and Wave B
  (apache_doris / starrocks / clickhouse / hive / trino / presto / duckdb)
  precedent.

### Umbrella approval (this design)

1. User reviews the spec file and accepts or requests revisions.
2. **Codex external review.** Feedback is written back into this spec.
3. Spec is committed and registered in
   [docs/product-specs/index.md](./index.md) §8.
4. Six Wave C child design brainstorming sessions may begin **only** after
   the umbrella is approved. The first-launched session is `tidb` per §6.

### Per-kind approval (each child design and child plan)

1. **Child design** is produced by an independent `superpowers:brainstorming`
   session and written to `docs/product-specs/`.
2. The "Driver Decision" section (policy 7.1) is signed off by the user or
   commercial decision-owner before the child design is approved.
3. Child design goes through the same user-review + codex-review flow as the
   umbrella.
4. **Child plan** is produced by an independent `superpowers:writing-plans`
   invocation against the approved child design, written to
   `docs/exec-plans/`, and registered as Active in
   [docs/exec-plans/index.md](../exec-plans/index.md).
5. Implementation completes with `cd server && mvn clean verify` BUILD
   SUCCESS, `cd client && npx tsc --noEmit` clean, and the appropriate test
   layer for the fixture tier (T1: full integration tests; T2: manual smoke
   scripts plus unit tests).
6. The verification PR updates `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
   Current Support Snapshot, ER matrix, and Wave C Tracking outcome; moves
   the plan in `docs/exec-plans/index.md` to Completed.

### Termination conditions

A child plan is **not** opened, and the Wave C Tracking outcome is written as
`Blocked: <reason>`, when any of the following hold:

- Driver Decision concludes "no redistribution rights and no CI-reachable
  mirror."
- Test fixture is T3 with no documented user-self-validation boundary.
- Driver artifact pinning cannot be agreed.

In those cases the kind is also recorded in
[docs/exec-plans/tech-debt-tracker.md](../exec-plans/tech-debt-tracker.md) (when
the blocker is resolvable internally) or in
[docs/bugs/](../bugs/) (when the blocker reflects a runtime regression).
`gaussdb` is the kind most likely to enter this branch.
