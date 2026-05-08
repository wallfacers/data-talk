# Data Source Coverage: KingbaseES Design

Date: 2026-05-08
Status: Draft (awaiting user review)
Wave: Wave C, step 4 (per Wave C umbrella §6 recommended order;
last of the 4 wave-c kinds; design phase serial-blocked on opengauss
step 2 + oceanbase step 3 design drafts being visible per roadmap §3.3)

## 1. Purpose

KingbaseES (人大金仓 KingbaseES) is the fourth kind in Wave C of Task 9
Data Source Coverage Expansion. It validates **commercial PG-fork plus
multi-mode policy** by simultaneously consuming two cross-kind reuse
abstractions produced by sister wave-c kinds:

- `PgForkReuseRule` 6 abstract base classes (produced by opengauss
  step 2, commit `c58b7fd`)
- `MultiModeConnectionShape` v1 (produced by oceanbase step 3, commit
  `1231153`, Flyway V18)

KingbaseES is the **only Wave C kind that consumes both reuse kits**.
This child design is therefore intentionally focused on consumption
(not production) and produces **zero new cross-kind reuse
abstractions**. Day-1 ships **PG-mode first-class only**; Oracle-mode
is structured `dialect_unsupported` until an independent Day-3 child
plan.

This artifact does not expose KingbaseES as supported. KingbaseES
remains unsupported until the child implementation plan completes
verification and the Current Support Snapshot in
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)
is updated.

**Key new outputs of step 4:**

- Driver Decision pinned (umbrella §7.1 highest-priority gate):
  `cn.com.kingbase:kingbase8:9.0.x` (specific patch pinned by child
  plan Step 0 against current Maven Central visibility), License =
  commercial, Distribution = Maven Central direct with vendor portal
  offline jar fallback (offline jar in repo is **forbidden** per
  umbrella §10 termination conditions; fallback path means a
  documented mirror URL or vendor download instructions, not a
  bundled jar)
- 6 concrete `Kingbase*ReuseIT` subclasses inheriting opengauss-shipped
  `AbstractPgFork*ReuseTest` abstract bases (closes the cross-kind
  reuse loop `opengauss` → `kingbase`)
- Multi-mode consumption: kingbase row appended to
  `MultiModeConnectionShape.validateModeForKind` /
  `isDay1FirstClassMode` (PG / Oracle legal; PG-mode Day-1
  first-class); zero v1 signature changes; reuse oceanbase-shipped
  `multi-mode-connection-fields.tsx` frontend skeleton
- Risk classifier dual-channel: 4 anchored L3 patterns (`SYS_*` table
  DDL, `SYS<CRT|AUDIT>_*` admin schema DDL, `SYS_KILL` function,
  `FLASHBACK TABLE`) + 2 anchored `dialect_unsupported` patterns
  (`KBBACKUP/KBRESTORE` CLI, Oracle-style PL/SQL block) all using
  `^\s*` start anchor + `\b` word boundary
- T2 fixture path: manual smoke script + JDBC mock unit tests; the
  6 PgFork concrete IT subclasses are `@Disabled` by default and
  triggered via `-Dkingbase.it.enabled=true` profile (different from
  opengauss 6 IT which run on CI under T1)
- `KingbaseDiagnosticsProvider` Day-1 returning structured
  `dialect_unsupported` for all 9 hooks; Day-2/Day-3 upgrade
  bidirectionally anchored to day2 plan §Day-3 kingbase row (line
  3119: reuses opengauss-shipped `PostgresJsonPlanParser`)
- **Alias normalization**: `kingbase` is the **only Wave C kind with a
  permitted alias** — `kingbasees` is normalized to `kingbase` at the
  `ConnectionKind.normalize` boundary (umbrella §8 line 293
  exception). All other variants rejected with structured error.

## 2. Compatibility Gate Application

The mandatory gate is
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
KingbaseES implementation must apply every database-type area:

- canonical naming, `kingbasees` alias normalization, and
  `ConnectionKind.normalize` boundary;
- backend connection kind, URL builder, driver packaging (Maven
  Central direct + vendor portal fallback documented), connection
  test, metadata, target resolution, SQL execution, result
  normalization, splitter (reuse PG via PgForkReuseRule), risk guard
  (dual-channel), diagnostics, and localized error messages;
- frontend connection form (multi-mode skeleton reused from
  oceanbase; PG-mode Day-1 first-class with Oracle-mode disabled
  with tooltip), picker, default port (54321), Query Editor context,
  formatter, outline, accessibility, semantic tokens, and i18n;
- MCP `ConnectionObjectType` enum, runtime `AGENTS.md`, prompt
  contract tests, and tool naming;
- ER Inspector and ER Designer return `dialect_unsupported` (per
  Wave C umbrella §5 Day-1 unsupported set).

No section is N/A: first-class KingbaseES PG-mode Day-1 support
touches every database compatibility area. Oracle-mode is
structurally absent from Day-1 and returns `dialect_unsupported` at
every entry point — that is itself a compatibility decision, not a
gate skip.

## 3. Design Inputs

- [docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md](./2026-05-08-data-source-coverage-wave-c-design.md)
  — Wave C umbrella. Locks Driver Distribution & License Gate
  (§7.1 highest-priority), Compatibility-Mode Policy (§7.2),
  Reuse-With-Tests rule (§7.3), AGENTS.md naming policy (§7.5),
  Day-1 unsupported set (§5), recommended order (§6), `kingbasees`
  alias exception (§8 line 293), and termination conditions (§10).
- [docs/product-specs/2026-05-08-data-source-coverage-wave-c-roadmap.md](./2026-05-08-data-source-coverage-wave-c-roadmap.md)
  — sub-wave execution navigation. Locks kingbase as step 4 (last
  of 4; design phase serial-blocked on opengauss + oceanbase design
  drafts being visible per §3.3; child plan kickoff hard-blocked on
  both upstream kits being shipped per §4 lines 115-118). Workload
  5.5 day estimate (lowest in wave-c due to upstream reuse).
- [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)
  — hard compatibility checklist.
- [client/DESIGN.md](../../client/DESIGN.md) — explicit constraints
  applied to every UI surface introduced by this spec (multi-mode
  form reused from oceanbase, picker, dialect_unsupported toast):
  - **Semantic token contract** (DESIGN.md §"Semantic Tokens"):
    surface backgrounds use `bg.panel`; field borders use
    `border.default`; focus uses `interaction.focusRing`; hover
    uses `interaction.hover`; active/pressed uses
    `interaction.active`; disabled uses `interaction.disabled`
    (DESIGN.md line 282 — must not rely on color alone). **No
    component-local color values invented.**
  - **5-state control matrix** (default / hover / focus / active /
    disabled) is **fully enumerated** for every interactive control
    (kind picker row, host input, port input, database input, mode
    picker chips, save button, dialect_unsupported chip). Per memory
    record `feedback-design-control-states.md`: no abbreviation;
    each control × 5 state token explicitly listed in the child
    plan.
  - **Stage state is global, not per-session** (DESIGN.md line 310):
    connection settings dialog opens as a global modal.
  - **i18n keys** (DESIGN.md §"Internationalization"): every
    user-visible string in kingbase UI routes through
    `client/src/i18n/messages.ts` with both `zh-CN` and `en-US`
    entries; key namespace per §12.4.
  - **Accessibility**: explicit `aria-label` /
    `aria-describedby` for `kingbasees` alias guidance and Oracle-mode
    disabled reason.
  - **Light + dark theme parity**: every new style consumes semantic
    tokens.
- [docs/product-specs/2026-05-08-data-source-coverage-opengauss-design.md](./2026-05-08-data-source-coverage-opengauss-design.md)
  — sister Wave C step 2; producer of `PgForkReuseRule` 6 abstract
  bases consumed by this spec. Anchored risk pattern conventions,
  `PostgresJsonPlanParser` Day-2 design, fixture wait strategy, and
  Day-2 anchor format are reused.
- [docs/product-specs/2026-05-08-data-source-coverage-oceanbase-design.md](./2026-05-08-data-source-coverage-oceanbase-design.md)
  — sister Wave C step 3; producer of `MultiModeConnectionShape` v1
  consumed by this spec. Anchored risk pattern conventions,
  diagnostics dialect_unsupported policy, and client/DESIGN.md
  constraint enumeration pattern are reused.
- [docs/product-specs/2026-05-08-data-source-coverage-dameng-design.md](./2026-05-08-data-source-coverage-dameng-design.md)
  — sister Wave C step 5; T2 fixture path (manual smoke script +
  JDBC mock unit tests) reused identically; Channel 2
  dialect_unsupported pattern naming convention reused.
- [docs/exec-plans/2026-05-08-diagnostics-day2-plan.md](../exec-plans/2026-05-08-diagnostics-day2-plan.md)
  — 11 dialect diagnostics framework, `parseMySqlJsonPlan` /
  upcoming `PostgresJsonPlanParser` reuse hook, and §Day-3 candidate
  matrix kingbase row (line 3119: "EXPLAIN [FORMAT JSON] / 复用
  PostgresJsonPlanParser from opengauss / B-tree / T2 (trial license)
  / diagnostics.explain.unsupported.kingbase_permission").
  Bidirectionally anchored with §11 of this spec.
- KingbaseES official documentation (re-checked at child plan
  kickoff): driver artifact `cn.com.kingbase:kingbase8:9.0.x` on
  Maven Central (vendor portal: <https://www.kingbase.com.cn/>),
  driver class `com.kingbase8.Driver`, URL prefix
  `jdbc:kingbase8://<host>:<port>/<database>`, default port 54321,
  PG-protocol-compatible default + Oracle-protocol-compatible mode,
  `SYS_*` Oracle-compat synonym views coexist with `pg_*` native
  views, `KBBACKUP` / `KBRESTORE` CLI utilities, dollar-quoted
  `DO $$ ... $$` PG-style block + Oracle-style
  `DECLARE/BEGIN ... END;/` block syntax distinction.

External references are design inputs, not implementation approvals.

## 4. Support Statement

Target outcome: first-class Day-1 SQL Workbench support for KingbaseES
V8 PG-mode through the official `cn.com.kingbase:kingbase8:9.0.x`
JDBC path.

At the end of implementation, DataTalk must:

- create, edit, test, select, and delete KingbaseES connections;
  `kingbasees` user-typed alias is normalized to `kingbase` at
  `ConnectionKind.normalize` (the only Wave C kind with a permitted
  alias);
- store `compatibility_mode = 'pg'` Day-1 (Oracle-mode rejected at
  form via `MultiModeConnectionShape.isDay1FirstClassMode` returning
  false; structured `dialect_unsupported` with i18n key
  `connection.kind.kingbase.mode_oracle_unsupported_day1`);
- discover schemas, tables, columns, and indexes through the
  existing PostgreSQL metadata path
  (`pg_namespace` / `pg_class` / `pg_attribute` / `pg_index`);
  KingbaseES-specific equivalence verified by
  `KingbaseMetadataReuseIT extends AbstractPgForkMetadataReuseTest`;
  Day-1 does not consult `SYS_*` Oracle-compat views (deferred to
  Oracle-mode Day-3);
- resolve `database` context (PG-style; `databaseName` semantics =
  PG `database`);
- execute SELECT / DML / DDL through the existing guarded SQL path
  with L1 / L2 / L3 confirmation, plus `dialect_unsupported`
  rejection at the SqlExecuteService kingbase branch entry for two
  classes (`KBBACKUP` / `KBRESTORE` CLI commands, Oracle-style
  PL/SQL block) detected by the new `detectKingbaseUnsupported(sql)`
  method on `CalciteSqlRiskAnalyzer`;
- split multi-statement scripts using the existing
  `PostgresJdbcSqlStatementSplitter`, proven equivalent on KingbaseES
  PG-mode by `KingbaseSplitterReuseIT extends AbstractPgForkSplitterReuseTest`,
  including PG-style dollar-quoted `DO $$ ... $$` blocks; Oracle-style
  `DECLARE/BEGIN ... END;/` blocks rejected by Channel 2 risk classifier
  before reaching the splitter;
- classify KingbaseES-specific admin / DDL commands (`SYS_*` table
  DDL, `SYS<CRT|AUDIT>_*` admin schema DDL, `SYS_KILL` function,
  `FLASHBACK TABLE`) as L3 under independent **anchored** risk
  patterns aggregated by the `kingbase_admin_command` risk label;
- normalize JDBC return values using the existing
  `JdbcResultValueNormalizer` PostgreSQL baseline proven equivalent
  on KingbaseES PG-mode via
  `KingbaseResultNormalizationReuseIT extends AbstractPgForkResultNormalizationReuseTest`;
- return structured `dialect_unsupported` for the seven diagnostics
  hooks (`lock_info`, `pool_status`, `table_space`,
  `terminate_session`, `optimize_table`, `explain_real`,
  `index_hints`) and the two ER hooks (`er_inspector`,
  `er_designer`) — consistent with Wave C umbrella §5 and the
  opengauss / oceanbase / dameng precedents;
- expose the `kingbase` kind in MCP `ConnectionObjectType` enum,
  AGENTS.md prompt rules, and prompt contract tests **only after**
  child plan verify passes (manual smoke SUCCESS + 6 IT pass via
  `-Dkingbase.it.enabled=true` + unit tests SUCCESS), never conflate
  it with `postgresql` or `opengauss`.

KingbaseES remains unsupported until those checks pass.

## 5. Kind Naming

- **Canonical kind**: `kingbase` (lower-case, single string).
- **Aliases**: **`kingbasees` permitted** (the only Wave C kind with
  a permitted alias, per umbrella §8 line 293). Normalization rules
  at `ConnectionKind.normalize`:

| User input | Normalized | Note |
|---|---|---|
| `kingbase` | `kingbase` | canonical |
| `kingbasees` | `kingbase` | umbrella §8 permitted alias |
| `KingbaseES`, `KINGBASEES`, `KingBaseES`, etc. | `kingbase` | lower-case + alias normalization |
| `KINGBASE`, `Kingbase` | `kingbase` | lower-case + canonical |
| `kb`, `kbase`, `金仓`, `人大金仓` | **rejected** | not normalized; structured "unknown kind" error guides user to type `kingbase` |
| `kingbase7`, `kingbase8`, `kingbase9` | **rejected** | version suffix not part of canonical |

- **Persistence**: `ConnectionRecord.kind()`, API payloads, generated
  frontend types, and MCP schema enum all store `kingbase` (never
  `kingbasees`). Even when the user types `kingbasees`, persistence
  is `kingbase`.
- **Frontend label**: `KingbaseES` (en) / `人大金仓 KingbaseES` (zh).
  Brand casing preserved.
- **PostgreSQL relationship**: protocol-level PG-fork is a tooling
  reuse premise (splitter + metadata + normalizer + diagnostics),
  not an identity-level alias. `kingbase` connections must not be
  persisted or displayed as `postgresql` in any code path. Frontend
  picker must render KingbaseES as a separate row.
- **opengauss relationship**: both PG-fork; both consume
  `PgForkReuseRule`. They are **not** aliases of each other; they
  remain separate canonical kinds.
- **Multi-mode**: PG-mode and Oracle-mode share the canonical kind
  `kingbase`. They are distinguished by the `compatibility_mode`
  connection-level field (oceanbase Flyway V18 shared column),
  never by separate kinds.
- **Normalization boundary**: `ConnectionKind.normalize` (application
  layer REST and action entry points) is the single canonicalization
  point. Any scattered `equalsIgnoreCase("kingbase")` or
  `equalsIgnoreCase("kingbasees")` branch violates Wave C umbrella
  §7.3.

## 6. Connection And Persistence

### 6.1 Driver Decision (Umbrella §7.1 Gate — Highest Priority)

| Item | Decision | Note |
|---|---|---|
| Driver artifact | `cn.com.kingbase:kingbase8:9.0.x` | Specific patch pinned by child plan Step 0 against current Maven Central visibility. KingbaseES driver `kingbase8` series serves KingbaseES V8 servers; V7 / V9 series drivers are out of scope for Day-1. |
| Driver class | `com.kingbase8.Driver` | PG-protocol-compatible registration. Service Loader auto-registered via `META-INF/services/java.sql.Driver`. Spring Boot fat-jar packaging must preserve this entry. |
| License | Commercial (Beijing People's University Jincang Information Technology Co., Ltd.) | Maven Central distribution is allowed by the license for download and integration use. **Binary redistribution rights**: none. This repository must NOT bundle the jar into a fat-jar artifact distributed externally. |
| Distribution path | **Maven Central direct + vendor portal fallback** | Child plan Step 0 produces a "Driver Reachability Report": (a) if `kingbase8` is visible on Maven Central with the desired patch, use direct dependency; (b) if not visible, fall back to documented vendor portal download instructions in the child plan README. **Forbidden alternative** per umbrella §10: offline jar checked into `lib/` directory. The fallback is documentation-driven, not jar-bundling. |
| CI reproducibility | Path (a) is reproducible without bootstrap task. Path (b) requires developer-machine setup; CI cannot run kingbase IT in path (b) without a private Maven mirror | Child plan Step 0 verifies CI runner can resolve the artifact under path (a); if path (b) is required, kingbase IT remains `@Disabled` on CI (already the Day-1 plan per §12.1). |
| Coexistence with PostgreSQL driver | Both registered | `jdbc:postgresql://...` taken by `org.postgresql.Driver`; `jdbc:kingbase8://...` taken by `com.kingbase8.Driver`; mutual non-interference via DriverManager `acceptsURL` routing. Verified by `KingbaseDriverCoexistenceTest`. |

### 6.2 URL Decision

URL template:

```
jdbc:kingbase8://<host>:<port>/<database>
```

- **Default port**: `54321` (KingbaseES V8 default; differs from PG
  5432 to make user setup match server out-of-box).
- **`/<database>` required**: same as PG; cannot be omitted.
  `databaseName` field in `ConnectionRecord` is required for
  kingbase rows (enforced at form layer; existing PG validation
  pattern reused).
- **Username**: passed via JDBC `Properties.user`. No tenant /
  cluster suffix (single-PG-instance model).
- **Day-1 no URL custom parameters**: `?currentSchema=...`,
  `?ApplicationName=...`, `?ssl=...` are not exposed in the form.
  Day-1 keeps form simple; if schema-context switch is needed at
  runtime, `ConnectionTargetDiscoveryService` issues
  `SET search_path TO ...` after connection opens (PG-equivalent
  pattern).
- **TLS**: not in scope Day-1 (consistent with opengauss spec
  §12.5 out-of-scope item 1 — wave-c TLS is a future cross-kind
  follow-up plan, not per-kind Day-1).

### 6.3 ConnectionRecord Field Reuse (No New Columns)

KingbaseES adds **zero** new columns to `ConnectionRecord`. Reuse
strategy:

| Field | KingbaseES semantics | Note |
|---|---|---|
| `kind` | `"kingbase"` | per §5 canonical; even when user types `kingbasees`, persistence is `kingbase` |
| `host` | KingbaseES server hostname or IP | required |
| `port` | KingbaseES server port | default 54321 |
| `databaseName` | PG `database` name | required (PG-required pattern reused) |
| `username` | KingbaseES database user | required; passed verbatim |
| `passwordEnc` | encrypted KingbaseES password | required |
| `compatibilityMode` | `"pg"` Day-1 first-class; `"oracle"` `dialect_unsupported` | Validated by `MultiModeConnectionShape.validateModeForKind` per §10 |
| `oceanbaseTenant` / `oceanbaseCluster` | always `NULL` for kingbase rows | enforced by oceanbase Flyway V18 CHECK |

No Flyway migration is needed for kingbase. The existing schema (after
oceanbase V18) already covers all kingbase fields including
`compatibility_mode`.

### 6.4 ConnectionKind Enum and Normalization

```java
public enum ConnectionKind {
  MYSQL, POSTGRESQL, /* ... */ DAMENG, KINGBASE;
}
```

`ConnectionKind.normalize(String input)` rules for KingbaseES (per §5
table):

- Input lower-cased first, then matched
- `kingbase` → `KINGBASE` accepted as canonical
- `kingbasees` → `KINGBASE` accepted as alias (the only wave-c
  alias permitted by umbrella §8)
- All other variants rejected with structured "unknown kind" error
- AI prompt rules (post-verify) may include Chinese aliases `人大金仓`
  / `金仓` for user-typed-request recognition only; they are **not**
  alias targets at `ConnectionKind.normalize` (umbrella §7.5).

## 7. Metadata Discovery

### 7.1 Reuse Path (PG Branch Equivalent)

`ConnectionTargetDiscoveryService` adds `kingbase` branch routing
through the existing PostgreSQL discovery path:

```java
// pseudo
case "postgresql", "kingbase" -> postgresLikeDiscovery(c);
// (opengauss is its own branch even though it could share — opengauss spec §6.2 keeps three sibling branches)
```

PG-mode KingbaseES data dictionary views are PG-compatible (the core
vendor-claimed compatibility):

- `SELECT nspname FROM pg_namespace` — schema enumeration
- `SELECT tablename FROM pg_tables WHERE schemaname = ?` — table
  enumeration
- `SELECT column_name, data_type FROM information_schema.columns WHERE table_schema = ? AND table_name = ?` — column enumeration
- `SELECT indexname FROM pg_indexes WHERE schemaname = ? AND tablename = ?` — index enumeration

### 7.2 System Schema Filter

KingbaseES PG-mode default system schemas (baseline; child plan Task
4 fixture verification 补全 if additions found):

```java
private static final Set<String> KINGBASE_SYSTEM_SCHEMAS = Set.of(
    // PG baseline (4 items)
    "pg_catalog", "information_schema", "pg_toast", "pg_temp",
    // KingbaseES increment (verified at child plan Task 4)
    "sys",            // KingbaseES internal SYS_* synonym objects
    "sys_catalog"     // KingbaseES system catalog analog to pg_catalog
);
```

The 6 baseline items represent KingbaseES standard deployment system
schemas. Child plan Task 4 runs `\dn` (or
`SELECT nspname FROM pg_namespace`) on the manual smoke fixture and
reconciles against vendor docs to fill in any
vendor-extension schemas (e.g., `sysmac` for security-related schemas,
`syscrt` for Oracle-compat package schemas if not already covered by
SYS_* admin pattern).

### 7.3 Schema Field Semantics

- `databaseName` field carries the PG `database` (PG-precedent;
  consistent with PG / opengauss).
- `schema` is **not** a separate `ConnectionRecord` column; it is a
  per-query namespace selected via `SET search_path TO ...` at
  metadata discovery time.
- No `SYS_*` Oracle-compat view consultation in Day-1 discovery
  (deferred to Oracle-mode Day-3).

### 7.4 Reuse Inventory

| Reuse point | Source | Test obligation (Wave C §7.3 Reuse-With-Tests + opengauss §10 line 379 acceptance gate) |
|---|---|---|
| `postgresLikeDiscovery()` body | existing PG branch | `KingbaseMetadataReuseIT extends AbstractPgForkMetadataReuseTest` |
| `ConnectionTargetDiscoveryService` switch dispatch | same | covers schema list / table list / columns / indexes (4 cases) |
| System-schema filter pattern | PG branch equivalent | KingbaseES-specific filter list has its own unit test |
| `JdbcResultValueNormalizer` PG baseline | existing PG branch | `KingbaseResultNormalizationReuseIT extends AbstractPgForkResultNormalizationReuseTest` |
| `PostgresJdbcSqlStatementSplitter` | existing PG splitter | `KingbaseSplitterReuseIT extends AbstractPgForkSplitterReuseTest` |
| Target resolution PG-style | existing | `KingbaseTargetResolutionReuseIT extends AbstractPgForkTargetResolutionReuseTest` |
| Batch DML PG-style | existing | `KingbaseBatchDmlReuseIT extends AbstractPgForkBatchDmlReuseTest` |
| Connection test PG-style | existing | `KingbaseConnectionReuseIT extends AbstractPgForkConnectionTestReuseTest` |

The 6 concrete IT subclasses (one per `PgForkReuseRule` abstract base)
are the **kingbase plan acceptance gate** (per opengauss spec §10
line 379).

### 7.5 Independent Inventory

| Independent point | Reason |
|---|---|
| KingbaseES system-schema filter list | PG baseline does not cover KingbaseES-specific items (`sys`, `sys_catalog`) |
| `compatibility_mode` entry validation | Discovery entry must reject `kingbase` row with mode ≠ `pg` and return `dialect_unsupported`; not present in PG branch |
| `kingbasees` alias normalization | Only Wave C kind with this concern |

## 8. SQL Execution / Splitter / Risk Classifier

### 8.1 SQL Execution Path

`SqlExecuteService` adds `kingbase` branch with two-stage entry
(same pattern as dameng and oceanbase):

1. **Stage 1 — `dialect_unsupported` detection** (BEFORE JDBC):
   invokes `CalciteSqlRiskAnalyzer.detectKingbaseUnsupported(sql)`;
   non-empty `Optional<KingbaseUnsupportedReason>` result returns
   structured outcome immediately, never reaching JDBC.
2. **Stage 2 — normal guarded execution**: if Stage 1 passes, route
   through the same path as PostgreSQL (L1-L2-L3 confirmation,
   `JdbcResultValueNormalizer` PG baseline, parameterized statements,
   `SET autocommit` behavior).

`compatibility_mode` entry validation: ≠`pg` returns
`dialect_unsupported` with i18n key
`connection.kind.kingbase.mode_oracle_unsupported_day1` and message
anchor "KingbaseES Oracle-mode is not supported in Day-1; see Wave C
Day-3 candidate (kingbase Oracle-mode plan)".

### 8.2 Splitter

```java
case "kingbase" -> postgresJdbcSplitter;  // single instance shared with PG / opengauss
```

Verified equivalent on KingbaseES PG-mode by
`KingbaseSplitterReuseIT extends AbstractPgForkSplitterReuseTest`,
covering PG 6 cases + KingbaseES increment 2 cases:

PG 6 cases (inherited from abstract base):
- Single statement
- `;`-delimited multi-statement
- String-literal `;` escape
- Comment-block `;` ignore
- Empty-statement filtering
- PG-style dollar-quoted `DO $$ ... $$` block round-trip

KingbaseES 2 increment cases (kingbase-private):
- PG-style dollar-quoted block with named tag (`DO $tag$ ... $tag$;`)
  — confirms KingbaseES PG-mode splitter handles named tags
  identically to PG
- Oracle-style PL/SQL block (`DECLARE...BEGIN ... END;/`) is
  **rejected upstream** by Channel 2 risk classifier before reaching
  the splitter; this case verifies the splitter never sees such
  input in Day-1

### 8.3 Risk Classifier — Dual Channel

`CalciteSqlRiskAnalyzer` adds kingbase branch with two methods:

**Channel 1 — `classifyKingbaseSpecific(sql)`**: returns
`Optional<RiskLevel>`; non-empty L3 result takes precedence over base
classification.

**Channel 2 — `detectKingbaseUnsupported(sql)`**: returns
`Optional<KingbaseUnsupportedReason>`; non-empty result triggers
Stage 1 rejection in `SqlExecuteService`.

**Spec Author Note (per Wave C §10 governance)**: all new patterns
**MUST** use `^\s*` start anchor + `\b` word boundary; `contains()`
is **forbidden**. This rule is verbatim aligned with the ad4c1f0
governance reset incident and matches the conventions established by
opengauss / oceanbase / dameng.

#### Channel 1 — 4 anchored L3 Pattern constants

```java
// CalciteSqlRiskAnalyzer.java — new private static final fields

private static final Pattern KINGBASE_SYS_TABLE_DDL = Pattern.compile(
    "^\\s*(DROP|ALTER|TRUNCATE)\\s+(TABLE\\s+)?SYS_[A-Z_]+\\b",
    Pattern.CASE_INSENSITIVE);

private static final Pattern KINGBASE_SYS_ADMIN_SCHEMA_DDL = Pattern.compile(
    "^\\s*(DROP|ALTER)\\s+(TABLE\\s+)?SYS(CRT|AUDIT)_[A-Z_]+\\b",
    Pattern.CASE_INSENSITIVE);

private static final Pattern KINGBASE_SYS_KILL = Pattern.compile(
    "^\\s*SELECT\\s+SYS_KILL\\b",
    Pattern.CASE_INSENSITIVE);

private static final Pattern KINGBASE_FLASHBACK = Pattern.compile(
    "^\\s*FLASHBACK\\s+TABLE\\b",
    Pattern.CASE_INSENSITIVE);
```

All 4 patterns hit ⇒ L3. Risk label aggregation: every
KingbaseES-specific L3 hit is labeled `kingbase_admin_command`. The
matched sub-command name is included in the error message body, not
in the structured label.

#### Channel 2 — 2 anchored `dialect_unsupported` Pattern constants

```java
private static final Pattern KINGBASE_KB_BACKUP_RESTORE = Pattern.compile(
    "^\\s*(KBBACKUP|KBRESTORE)\\s+",
    Pattern.CASE_INSENSITIVE);
// Whitespace-required suffix prevents collision with hypothetical
// SELECT KBBACKUP_INFO() function calls

private static final Pattern KINGBASE_ORACLE_PLSQL_BLOCK = Pattern.compile(
    "^\\s*(DECLARE|BEGIN)\\b",
    Pattern.CASE_INSENSITIVE);
// Day-1 PG-mode does not support Oracle-style PL/SQL block;
// PG-style DO $$ ... $$ is supported by the splitter
```

Each pattern maps to a specific i18n key:

| Pattern | i18n key | English message |
|---|---|---|
| `KINGBASE_KB_BACKUP_RESTORE` | `risk.dialect_unsupported.kingbase.kb_backup_restore_cli` | "KingbaseES KBBACKUP / KBRESTORE CLI utilities are not supported in Day-1. Use the KingbaseES vendor backup tooling directly." |
| `KINGBASE_ORACLE_PLSQL_BLOCK` | `risk.dialect_unsupported.kingbase.oracle_plsql_block` | "Oracle-style PL/SQL blocks (DECLARE/BEGIN ... END;/) require KingbaseES Oracle-mode, which is not supported in Day-1. Use PG-style DO $$ ... $$ blocks instead, or wait for the Wave C kingbase Oracle-mode Day-3 candidate." |

### 8.4 Risk Boundary Tests (Mandatory Per Pattern)

**Channel 1 hit cases** (must classify as L3):
- `DROP TABLE SYS_USERS;` → SYS_TABLE_DDL
- `ALTER TABLE SYS_AUDIT_TRAIL ADD COLUMN x INT;` → SYS_TABLE_DDL
- `TRUNCATE TABLE SYS_LOG;` → SYS_TABLE_DDL
- `DROP TABLE SYSCRT_PACKAGES;` → SYS_ADMIN_SCHEMA_DDL
- `ALTER TABLE SYSAUDIT_RULES ...;` → SYS_ADMIN_SCHEMA_DDL
- `SELECT SYS_KILL(123);` → SYS_KILL
- `FLASHBACK TABLE my_table TO TIMESTAMP '...';` → FLASHBACK

**Channel 1 boundary cases** (must NOT classify as kingbase L3):
- `UPDATE my_sys_user SET ...` ← does not match `KINGBASE_SYS_TABLE_DDL`
  (`my_sys_user` does not start with `SYS_`)
- `SELECT * FROM sys_users_view` ← does not match (DROP/ALTER/TRUNCATE
  anchored, this is SELECT)
- `INSERT INTO flashback_log` ← does not match `KINGBASE_FLASHBACK`
  (INSERT not in anchored command list)
- `SELECT sys_kill_audit FROM ...` ← does not match `KINGBASE_SYS_KILL`
  (column name, not `SELECT SYS_KILL` function call)
- `   DROP TABLE SYS_USERS` (leading whitespace) ← matches due to
  `\s*` anchor

**Channel 2 hit cases** (must trigger `dialect_unsupported`):
- `KBBACKUP DATABASE my_db FILE='/backup/...';` → KB_BACKUP_RESTORE
- `KBRESTORE DATABASE my_db FROM '/backup/...';` → KB_BACKUP_RESTORE
- `DECLARE v_x INT := 1; BEGIN NULL; END;` → ORACLE_PLSQL_BLOCK
- `BEGIN DBMS_OUTPUT.PUT_LINE('x'); END;` → ORACLE_PLSQL_BLOCK

**Channel 2 boundary cases** (must NOT trigger):
- `SELECT KBBACKUP_INFO()` ← does not match `KINGBASE_KB_BACKUP_RESTORE`
  (no whitespace after KBBACKUP — required `\s+`)
- `INSERT INTO begin_log` ← does not match `KINGBASE_ORACLE_PLSQL_BLOCK`
  (`\b` after BEGIN; `begin_log` does not match)
- `INSERT INTO declare_audit` ← does not match (same reason)
- `DO $$ BEGIN RAISE NOTICE 'x'; END $$;` ← does **not** match
  because it starts with `DO`, not `DECLARE` or `BEGIN`; this is
  PG-style and goes through normal splitter handling

### 8.5 Reuse Inventory

| Reuse point | Source | Test obligation |
|---|---|---|
| `PostgresJdbcSqlStatementSplitter` | existing PG splitter | `KingbaseSplitterReuseIT extends AbstractPgForkSplitterReuseTest` |
| `JdbcResultValueNormalizer` PG baseline | existing PG branch | `KingbaseResultNormalizationReuseIT extends AbstractPgForkResultNormalizationReuseTest` |
| `postgresLikeDiscovery()` metadata path | existing PG branch (§7) | `KingbaseMetadataReuseIT extends AbstractPgForkMetadataReuseTest` |
| `classifyMySqlBase` analog — base PG classifier | mysql / PG / opengauss shared base | KingbaseES base classifier behavior verified by classifier-base unit tests (no shared abstract base — PG classifier reuse pattern matches dameng's approach) |
| L1-L2-L3 confirmation flow | framework | covered by integration tests |
| `MultiModeConnectionShape` v1 | oceanbase step 3 (commit `1231153`) | `KingbaseMultiModeConnectionShapeTest` (validates kingbase row in `validateModeForKind` + `isDay1FirstClassMode`) |
| Frontend `multi-mode-connection-fields.tsx` | oceanbase step 3 | `kingbase-connection-form.tsx` invocation: `modeOptions=["pg","oracle"]`, `modeDisabled=["oracle"]` |

### 8.6 Independent Inventory

| Independent point | Reason |
|---|---|
| `classifyKingbaseSpecific()` (Channel 1, 4 patterns) | KingbaseES-specific admin / DDL patterns; PG classifier does not exist as a shared base |
| `detectKingbaseUnsupported()` (Channel 2, 2 patterns) | Day-1 unsupported-entry detection; routed by SqlExecuteService Stage 1 |
| `kingbase_admin_command` risk label | KingbaseES aggregation label |
| Stage 1 / Stage 2 SqlExecuteService entry split | Same pattern as dameng / oceanbase |
| `compatibility_mode` entry validation | Multi-mode kind specific |
| `kingbasees` alias normalization | Only Wave C kind with this concern |

## 9. Diagnostics Provider — 9 hooks Matrix

### 9.1 Provider Class

`KingbaseDiagnosticsProvider extends AbstractDiagnosticsProvider`,
consistent with the 11-dialect framework landed by Day-2 plan. Path:

```
server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/KingbaseDiagnosticsProvider.java
```

### 9.2 9-Hook Decision Matrix — All `dialect_unsupported` Day-1

Per Wave C umbrella §5 Day-1 unsupported set and the opengauss /
oceanbase / dameng precedents, all 9 diagnostics hooks return
structured `dialect_unsupported` Day-1.

| Hook | Day-1 Decision | Day-2/Day-3 Upgrade Owner |
|---|---|---|
| `lock_info` | `dialect_unsupported` | Independent Day-3 spec |
| `pool_status` | `dialect_unsupported` | Independent Day-3 spec |
| `table_space` | `dialect_unsupported` | Independent Day-3 spec |
| `terminate_session` | `dialect_unsupported` | Independent Day-3 spec |
| `optimize_table` | `dialect_unsupported` | Independent Day-3 spec |
| `explain_real` | `dialect_unsupported` | day2 plan §Day-3 kingbase row (`EXPLAIN [FORMAT JSON] <sql>` reusing **opengauss-shipped** `PostgresJsonPlanParser`; SYS_* views unified with pg_* as system objects) |
| `index_hints` | `dialect_unsupported` | day2 plan §Day-3 kingbase row (B-tree recommendation; KingbaseES `SYS_*` views Day-2 unified handling per line 3119) |
| `er_inspector` | `dialect_unsupported` | Wave C overall ER upgrade (umbrella §5) |
| `er_designer` | `dialect_unsupported` | Same |

### 9.3 dialect_unsupported Anchor Format

All 9 hook error messages share a structured anchor consistent with
the opengauss / oceanbase / dameng pattern:

```
"KingbaseES PG-mode does not support <hook> in Day-1.
 See Wave C Day-3 candidate (kingbase pg-mode upgrade path) in
 this spec §11."
```

i18n keys:

- `diagnostics.dialect_unsupported.kingbase.lock_info`
- `diagnostics.dialect_unsupported.kingbase.pool_status`
- `diagnostics.dialect_unsupported.kingbase.table_space`
- `diagnostics.dialect_unsupported.kingbase.terminate_session`
- `diagnostics.dialect_unsupported.kingbase.optimize_table`
- `diagnostics.dialect_unsupported.kingbase.explain_real`
- `diagnostics.dialect_unsupported.kingbase.index_hints`
- `diagnostics.dialect_unsupported.kingbase.er_inspector`
- `diagnostics.dialect_unsupported.kingbase.er_designer`

### 9.4 Reuse Surface

KingbaseES diagnostics Day-1 has no reuse surface to exercise (all 9
hooks return `dialect_unsupported` per umbrella §5). Day-2/Day-3
upgrades reuse opengauss-shipped `PostgresJsonPlanParser` for
EXPLAIN; that reuse decision is made in the Day-3 spec, not here.

Day-1 tests are minimal and kind-private:

- `KingbaseDiagnosticsDialectUnsupportedTest` — verifies all 9 hooks
  return structured outcome with the correct i18n key (per §9.3) and
  consistent Day-1 anchor message
- `KingbaseDiagnosticsProviderRegistrationTest` — verifies the
  provider is registered and routed when `kind = kingbase`

## 10. Reuse Outputs (None — Pure Consumer)

KingbaseES produces **zero new cross-kind reuse abstractions**. It is
Wave C's only kind that simultaneously consumes both upstream kits:

- **Consumes `PgForkReuseRule`** (produced by opengauss step 2,
  commit `c58b7fd`):
  - 6 concrete `Kingbase*ReuseIT` subclasses inheriting
    `AbstractPgFork*ReuseTest` (the abstract bases ship in opengauss
    PR; this spec produces the concrete subclasses as the kingbase
    plan acceptance gate per opengauss spec §10 line 379).
- **Consumes `MultiModeConnectionShape` v1** (produced by oceanbase
  step 3, commit `1231153`, Flyway V18):
  - Appends kingbase row to `validateModeForKind`: PG / Oracle legal
    combinations.
  - Appends kingbase row to `isDay1FirstClassMode`: PG-mode Day-1
    first-class.
  - **Zero modifications to v1 function signatures** — pure
    forward-compatible append (per oceanbase spec §10.6).
  - Reuses oceanbase-shipped frontend `multi-mode-connection-fields.tsx`
    skeleton; kingbase invocation uses `modeOptions=["pg","oracle"]`,
    `modeDisabled=["oracle"]`.
- **Does NOT produce a hypothetical `OracleProtocolReuseRule`**:
  Oracle-mode is `dialect_unsupported` Day-1; even if Day-3 ships
  Oracle-mode, dameng is the only other Oracle-like Wave C kind and
  YAGNI applies (single consumer ≠ shared abstraction).

The kingbase consumption pattern proves the cross-kind reuse model
end-to-end:
- `tidb` → produces `MySqlProtocolReuseRule` → `oceanbase` consumes
- `opengauss` → produces `PgForkReuseRule` → `kingbase` consumes
- `oceanbase` → produces `MultiModeConnectionShape` → `kingbase`
  consumes

## 11. Day-2 / Day-3 Upgrade Path — Bidirectional Anchor

### 11.1 Upgrade Matrix Scope

day2 plan (`docs/exec-plans/2026-05-08-diagnostics-day2-plan.md`)
§Day-3 candidate matrix is bound to **EXPLAIN-real and INDEX_HINTS
upgrade only** (per day2 plan §Day-3 line 3110: "Wave-C 4 kind
Day-2 EXPLAIN/INDEX_HINTS 真实化"). The other 5 capabilities are
explicitly out of day2 plan scope and live in independent Day-3 specs.

### 11.2 Day-2 Plan §Day-3 Anchored Items (kingbase row)

Bidirectionally anchored: day2 plan §Day-3 candidate matrix kingbase
row already lists both items (line 3119).

| Upgrade Item | Day-1 Status | Day-2 Path | day2 plan §Day-3 kingbase row |
|---|---|---|---|
| `explain_real` | `dialect_unsupported` | `EXPLAIN [FORMAT JSON] <sql>` reusing **opengauss-shipped** `PostgresJsonPlanParser` (no new parser; symmetric to `parseMySqlJsonPlan` shared by mysql / tidb / oceanbase Day-2 path) | ✅ already listed: "EXPLAIN [FORMAT JSON] / 复用 PostgresJsonPlanParser from opengauss" |
| `index_hints` | `dialect_unsupported` | B-tree index recommendation; SYS_* views unified with pg_* as system objects (no separate SYS_* discovery branch needed Day-2) | ✅ already listed: "B-tree / KingbaseES SYS_* 视图与 pg_* 同源处理" |

The child plan documentation-housekeeping task verifies the
bidirectional anchor (no new day2 plan backfill required at this
child plan ship time — day2 plan §Day-3 kingbase row is already
complete).

### 11.3 Independent Day-3 Specs (Out Of day2 Plan Scope)

| Upgrade Item | Day-1 Status | Independent Day-3 Path |
|---|---|---|
| `lock_info` | `dialect_unsupported` | New spec uses PG `pg_locks` / KingbaseES equivalents |
| `pool_status` | `dialect_unsupported` | New spec uses PG `pg_stat_activity` filtering |
| `table_space` | `dialect_unsupported` | New spec uses `pg_tablespace` aggregation |
| `terminate_session` | `dialect_unsupported` | New spec uses `pg_terminate_backend(pid)` |
| `optimize_table` | `dialect_unsupported` | New spec uses `VACUUM ANALYZE` (PG-equivalent; KingbaseES PG-mode supports same) |

### 11.4 Oracle-Mode — Independent Day-3 Spec

Day-1 explicitly rejects:
- Oracle-style PL/SQL block (`DECLARE/BEGIN ... END;/`)
- Connections with `compatibility_mode = 'oracle'`

Day-3 Oracle-mode first-class enablement requires:

- A dedicated kingbase Oracle-mode child design + plan
- `MultiModeConnectionShape.isDay1FirstClassMode` updated to allow
  `oracle` for kingbase
- Frontend `multi-mode-connection-fields.tsx` `modeDisabled` updated
  to remove `oracle` from disabled list (or replace with empty list)
- Splitter / risk / discovery / diagnostics all Oracle-flavor for
  kingbase Oracle-mode (separate from PG-mode code paths)
- Independent Day-3 child design + plan; not in day2 plan scope; not
  in this Day-1 spec scope

### 11.5 Other Out-Of-Scope Day-3 Items

| Item | Owner |
|---|---|
| Runtime / `USE` / session-level mode switching | Forbidden forever (umbrella §7.2); never enabled |
| KingbaseES V7 / V9 driver support | Day-3 evaluation; independent plan |
| KingbaseES HA (KB_HA / Repmgr) / read-write splitting / replication UI | Not on roadmap |
| KingbaseES enterprise-only features (data masking / TDE / multi-tenant) | Not on roadmap |
| KingbaseES Cloud / managed service | Not on roadmap |

## 12. Out-of-Scope / T2 Fixture / i18n / AGENTS.md Timing

### 12.1 T2 Fixture — Manual Smoke Path + 6 IT `@Disabled`

Wave C umbrella §7.4 T2 definition: "restricted fixture: trial
license or restricted image required but reproducible. Child plan
includes manual smoke scripts plus unit tests plus documented
prerequisites." KingbaseES follows this discipline; sister kind:
`dameng`.

**Manual smoke script**: `tools/manual-smoke/kingbase-day1.sh`

Structure (9 cases, identical pattern to dameng):

```bash
#!/usr/bin/env bash
# tools/manual-smoke/kingbase-day1.sh
#
# Prerequisites:
# 1. A reachable KingbaseES V8 server (trial license or development
#    edition). Vendor portal: https://www.kingbase.com.cn/
# 2. Environment variables: KINGBASE_HOST, KINGBASE_PORT (default 54321),
#    KINGBASE_DATABASE, KINGBASE_USER, KINGBASE_PASSWORD
# 3. The datatalk backend running locally on 8080
#
# This script runs 9 case smoke checks against the running DataTalk
# backend through its REST API. CI does not run this script.

# 9 cases:
# 1. Connection success + connection failure (wrong password)
# 2. Schema list discovery (\dn equivalent)
# 3. Table list discovery (one demo table)
# 4. Column list discovery (one demo table)
# 5. L1 SELECT execution
# 6. L2 INSERT / UPDATE
# 7. L3 DELETE / DROP (with confirmation simulation)
# 8. dialect_unsupported Oracle-style PL/SQL block + KBBACKUP CLI
#    rejection
# 9. Error message i18n key resolution (kingbasees alias normalization)
```

**6 concrete `Kingbase*ReuseIT` subclasses — `@Disabled` by
default**: Different from opengauss 6 IT (T1, runs on CI under
`enmotech/opengauss` Testcontainers). KingbaseES 6 IT are guarded
by JUnit 5 `@EnabledIfSystemProperty(named = "kingbase.it.enabled", matches = "true")`
or equivalent profile, triggered by:

```bash
mvn -pl data-talk-application,data-talk-infrastructure verify \
    -Dkingbase.it.enabled=true \
    -Dkingbase.host=<host> \
    -Dkingbase.port=<port> \
    -Dkingbase.database=<db> \
    -Dkingbase.user=<user> \
    -Dkingbase.password=<pw>
```

CI does **not** set `kingbase.it.enabled=true`; the 6 IT are part of
the kingbase plan acceptance gate (per opengauss spec §10 line 379)
but executed in local / QA acceptance, not on CI.

**Unit tests** (CI runs without a real KingbaseES server, JDBC
mocks):

- `KingbaseUrlBuilderTest` — URL construction
- `KingbaseConnectionRecordValidationTest` — application-layer
  ConnectionService validation
- `KingbaseAliasNormalizationTest` — `kingbasees` alias
  normalization at `ConnectionKind.normalize`
- `KingbaseRiskClassifierTest` — 4 + 2 = 6 anchored patterns × hit /
  boundary cases (per §8.4)
- `KingbaseDiagnosticsDialectUnsupportedTest` — 9 hooks
- `KingbaseDiagnosticsProviderRegistrationTest` — provider routing
- `KingbaseDriverCoexistenceTest` — DriverManager `acceptsURL`
  routing between PG / opengauss / KingbaseES drivers
- `KingbaseMultiModeConnectionShapeTest` — `validateModeForKind` +
  `isDay1FirstClassMode` covering kingbase rows + non-kingbase guard

**Documentation prerequisites** (in
`docs/exec-plans/2026-05-08-data-source-coverage-kingbase-plan.md`):

- KingbaseES vendor portal URL: <https://www.kingbase.com.cn/>
- Trial license retrieval steps
- KingbaseES V8 server install steps
- JDBC driver Maven Central reachability vs vendor portal fallback
  decision (Driver Reachability Report from Step 0)

### 12.2 6 Concrete IT Subclasses (Cross-Kind Reuse Loop Closure)

These 6 subclasses inherit from the opengauss-shipped
`PgForkReuseRule` abstract bases (commit `c58b7fd`), closing the
cross-kind reuse loop `opengauss` → `kingbase`:

| Subclass | Abstract Base (shipped by opengauss step 2) | Validates |
|---|---|---|
| `KingbaseSplitterReuseIT` | `AbstractPgForkSplitterReuseTest` | PG 6 cases + KingbaseES 2 increment cases |
| `KingbaseMetadataReuseIT` | `AbstractPgForkMetadataReuseTest` | schema / table / column / index discovery |
| `KingbaseTargetResolutionReuseIT` | `AbstractPgForkTargetResolutionReuseTest` | database context resolution + `SET search_path` |
| `KingbaseBatchDmlReuseIT` | `AbstractPgForkBatchDmlReuseTest` | batch INSERT / UPDATE / DELETE behavior |
| `KingbaseResultNormalizationReuseIT` | `AbstractPgForkResultNormalizationReuseTest` | PG type round-trip (numeric / text / timestamp / json / array) |
| `KingbaseConnectionReuseIT` | `AbstractPgForkConnectionTestReuseTest` | connection open/close, autocommit, isolation level |

### 12.3 KingbaseES-Specific Tests (Kind-Private)

See §12.1 unit test list. All run on CI without a real KingbaseES
server.

### 12.4 i18n Labels and Keys

- Frontend picker label: `KingbaseES` (en) / `人大金仓 KingbaseES`
  (zh — both English brand and Chinese company name shown for
  recognition)
- i18n keys:
  - `connection.kind.kingbase.label` = `KingbaseES`
  - `connection.kind.kingbase.label.zh` = `人大金仓 KingbaseES`
  - `connection.kind.kingbase.alias_normalized` (form hint when user
    types `kingbasees` and it normalizes to `kingbase`)
  - `connection.kind.kingbase.mode_oracle_unsupported_day1`
  - `risk.dialect_unsupported.kingbase.kb_backup_restore_cli`
  - `risk.dialect_unsupported.kingbase.oracle_plsql_block`
  - `diagnostics.dialect_unsupported.kingbase.lock_info`
  - `diagnostics.dialect_unsupported.kingbase.pool_status`
  - `diagnostics.dialect_unsupported.kingbase.table_space`
  - `diagnostics.dialect_unsupported.kingbase.terminate_session`
  - `diagnostics.dialect_unsupported.kingbase.optimize_table`
  - `diagnostics.dialect_unsupported.kingbase.explain_real`
  - `diagnostics.dialect_unsupported.kingbase.index_hints`
  - `diagnostics.dialect_unsupported.kingbase.er_inspector`
  - `diagnostics.dialect_unsupported.kingbase.er_designer`
- AI prompt rules (post-verify) may include Chinese aliases
  `人大金仓` / `金仓` for user-typed-request recognition only; they
  are **not** alias targets at `ConnectionKind.normalize` (umbrella
  §7.5).

### 12.5 AGENTS.md / MCP Timing (Wave C umbrella §7.5)

| Time Point | AGENTS.md Status | MCP `ConnectionObjectType` Enum |
|---|---|---|
| Design approval (this spec lands) | ❌ no kingbase | ❌ no kingbase |
| Child plan draft | ❌ | ❌ |
| Child plan implementation in progress | ❌ | ❌ |
| Child plan verify passes (manual smoke SUCCESS + 6 IT pass via `-Dkingbase.it.enabled=true` + unit tests SUCCESS) | ✅ same PR adds kingbase section (with `kingbasees` alias normalization documentation) | ✅ same PR adds enum |
| Snapshot upgrade to first-class | ✅ live | ✅ live |

### 12.6 Out-of-Scope (11 items explicitly NOT done in this spec)

1. KingbaseES Oracle-mode first-class — `dialect_unsupported`
   Day-1; independent Day-3 child design + plan
2. Oracle-style PL/SQL block (`DECLARE/BEGIN ... END;/`) —
   `dialect_unsupported` Day-1
3. KBBACKUP / KBRESTORE CLI utilities — `dialect_unsupported` Day-1
4. All 9 diagnostics + ER hooks return `dialect_unsupported`
   (consistent with Wave C umbrella §5)
5. KingbaseES V7 / V9 series driver — Day-3 evaluation only; Day-1
   supports V8 only (`cn.com.kingbase:kingbase8:9.0.x`)
6. KingbaseES HA (KB_HA / Repmgr) / read-write splitting /
   replication UI — never in scope
7. KingbaseES enterprise-edition-only features (data masking / TDE
   / multi-tenant) — never in scope
8. Offline jar in repo — **forbidden** by umbrella §10 termination
   conditions; Maven Central direct + vendor portal documentation
   fallback only
9. `SYS_*` Oracle-compat view consultation in Day-1 metadata
   discovery — Day-1 only consults `pg_*` views; Oracle-mode Day-3
   may add SYS_* path
10. CI starting KingbaseES container — explicitly out of CI scope
    Day-1; 6 IT triggered only via `-Dkingbase.it.enabled=true`
    profile in local / QA acceptance
11. KingbaseES Cloud / managed service connection-form variant —
    Day-1 supports self-hosted / on-premise only
