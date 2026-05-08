# Data Source Coverage: Dameng Design

Date: 2026-05-08
Status: Draft (awaiting user review)
Wave: Wave C, step 5 (per Wave C umbrella §6 recommended order;
design phase parallel with opengauss step 2 and oceanbase step 3 per
roadmap §3.2; no cross-kind reuse dependency)

## 1. Purpose

Dameng (达梦, DM 8) is the fifth kind in Wave C of Task 9 Data Source
Coverage Expansion. It validates **Oracle-like proprietary protocol +
commercial driver artifact selection** under Wave C umbrella §7.1
Driver Distribution & License Gate (the highest-priority section for
this child design — without it, the child plan does not start).

Dameng is **single-mode** (no multi-mode policy applies); it does not
consume `MultiModeConnectionShape` (oceanbase/kingbase) or
`PgForkReuseRule` (opengauss/kingbase) and produces no new cross-kind
reuse abstract base. Day-1 deliverable is SELECT + L1 metadata +
risk-classified DML/DDL with all 9 diagnostics hooks (7 real-execution
+ 2 ER) returning structured `dialect_unsupported`, consistent with
Wave C umbrella §5 and the opengauss / oceanbase precedent.

This artifact does not expose Dameng as supported. Dameng remains
unsupported until the child implementation plan completes verification
and the Current Support Snapshot in
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)
is updated.

**Key new outputs of step 5:**

- Driver Decision pinned (per umbrella §7.1, the gate for the entire
  child plan): `com.dameng:DmJdbcDriverX:8.1.x` (specific patch pinned
  by child plan Step 0 against current Maven Central visibility),
  License = commercial, Distribution = Maven Central direct, Binary
  redistribution = none (CI pulls from Central; offline jar in repo
  is **forbidden** per umbrella §10 termination conditions)
- Single-mode kind handling pattern: `databaseName` field reused for
  initial schema name (Oracle-precedent; no new ConnectionRecord
  column)
- Risk classifier dual-channel: 5 anchored L3 patterns + 3 anchored
  `dialect_unsupported` patterns (PL/SQL block, PROCEDURE/FUNCTION/
  TRIGGER/PACKAGE DDL, EXP/IMP commands) all using `^\s*` start anchor
  + `\b` word boundary aligned with ad4c1f0 governance rule
- T2 fixture path: manual smoke script + JDBC mock unit tests (no
  Testcontainers; CI does not start Dameng server)
- `DamengDiagnosticsProvider` Day-1 returning structured
  `dialect_unsupported` for all 9 hooks; Day-2/Day-3 upgrade path
  bidirectionally anchored to day2 plan §Day-3 dameng row (EXPLAIN
  tabular grammar via new `DamengTabularGrammar`)

## 2. Compatibility Gate Application

The mandatory gate is
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
Dameng implementation must apply every database-type area:

- canonical naming and `ConnectionKind.normalize` boundary;
- backend connection kind, URL builder, driver packaging (Maven
  Central direct), connection test, metadata, target resolution, SQL
  execution, result normalization, splitter (reuse generic), risk
  guard (dual-channel), diagnostics, and localized error messages;
- frontend connection form (single schema field, no multi-mode picker),
  picker, default port (5236), Query Editor context, formatter,
  outline, accessibility, semantic tokens, and i18n;
- MCP `ConnectionObjectType` enum, runtime `AGENTS.md`, prompt
  contract tests, and tool naming;
- ER Inspector and ER Designer return `dialect_unsupported` (per
  Wave C umbrella §5 Day-1 unsupported set).

No section is N/A: first-class Dameng Day-1 support touches every
database compatibility area. The 8 Day-1 entry points that return
`dialect_unsupported` (PL/SQL block, PROCEDURE/FUNCTION/TRIGGER/
PACKAGE DDL, EXP/IMP commands, 7 diagnostics hooks, 2 ER hooks) are
themselves compatibility decisions, not gate skips.

## 3. Design Inputs

- [docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md](./2026-05-08-data-source-coverage-wave-c-design.md)
  — Wave C umbrella. Locks Driver Distribution & License Gate
  (§7.1, the highest-priority section for this child design),
  Reuse-With-Tests rule (§7.3), AGENTS.md naming policy (§7.5),
  Day-1 unsupported set (§5), recommended order (§6), and
  termination conditions (§10).
- [docs/product-specs/2026-05-08-data-source-coverage-wave-c-roadmap.md](./2026-05-08-data-source-coverage-wave-c-roadmap.md)
  — sub-wave execution navigation. Locks dameng as step 5 (design
  phase parallel with opengauss + oceanbase; no upstream dependency
  on either). Workload 7 day estimate.
- [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)
  — hard compatibility checklist.
- [client/DESIGN.md](../../client/DESIGN.md) — explicit constraints
  applied to every UI surface introduced by this spec (connection
  form, picker, dialect_unsupported toast, error messages):
  - **Semantic token contract** (DESIGN.md §"Semantic Tokens"):
    surface backgrounds use `bg.panel`; field borders use
    `border.default`; focus uses `interaction.focusRing`; hover
    uses `interaction.hover`; active/pressed uses
    `interaction.active`; disabled uses `interaction.disabled`
    (DESIGN.md line 282 — must not rely on color alone). **No
    component-local color values invented.**
  - **5-state control matrix** (default / hover / focus / active /
    disabled) is **fully enumerated** for every interactive control
    (kind picker row, host input, port input, schema input, save
    button, dialect_unsupported chip). Per memory record
    `feedback-design-control-states.md`: no abbreviation; each
    control × 5 state token explicitly listed in the child plan.
  - **Stage state is global, not per-session** (DESIGN.md line 310):
    connection settings dialog opens as a global modal.
  - **i18n keys** (DESIGN.md §"Internationalization"): every
    user-visible string in dameng UI routes through
    `client/src/i18n/messages.ts` with both `zh-CN` and `en-US`
    entries; key namespace per §12.4.
  - **Accessibility**: explicit `aria-label` / `aria-describedby`
    for schema field optionality and dialect_unsupported reasons.
  - **Light + dark theme parity**: every new style consumes semantic
    tokens.
- [docs/product-specs/2026-05-08-data-source-coverage-tidb-design.md](./2026-05-08-data-source-coverage-tidb-design.md)
  — sister Wave C step 1 precedent (kind-naming pattern, anchored
  risk pattern conventions, fixture tier discipline reused).
- [docs/product-specs/2026-05-08-data-source-coverage-oceanbase-design.md](./2026-05-08-data-source-coverage-oceanbase-design.md)
  — sister Wave C step 3 precedent (anchored risk pattern conventions
  reused; diagnostics dialect_unsupported policy reused; client/
  DESIGN.md constraint enumeration pattern reused).
- [docs/product-specs/2026-05-08-data-source-coverage-opengauss-design.md](./2026-05-08-data-source-coverage-opengauss-design.md)
  — sister Wave C step 2 precedent (Day-2 EXPLAIN bidirectional
  anchor pattern with day2 plan §Day-3 reused).
- [docs/exec-plans/2026-05-08-diagnostics-day2-plan.md](../exec-plans/2026-05-08-diagnostics-day2-plan.md)
  — 11 dialect diagnostics framework, `TabularLayout` /
  `TextPlanGrammar` records (Day-2 plan Task 0.1/0.2 already shipped),
  and §Day-3 candidate matrix dameng row (line 3121:
  "新建 `DamengTabularGrammar`"). Bidirectionally anchored with
  §11 of this spec.
- Existing Oracle infrastructure code paths (sister Oracle-like
  precedent for reuse): `JdbcUrlBuilder` Oracle branch lines 32-48,
  `ConnectionTargetDiscoveryService` Oracle branch (`ALL_TABLES` /
  `ALL_TAB_COLUMNS` / `ALL_INDEXES`), `JdbcResultValueNormalizer`
  Oracle baseline (CHAR / VARCHAR2 / NUMBER / DATE / TIMESTAMP / CLOB
  / BLOB), `DefaultSqlStatementSplitters` Oracle branch (currently
  uses `GenericSqlStatementSplitter`).
- Dameng official documentation (re-checked at child plan kickoff):
  driver artifact `com.dameng:DmJdbcDriverX:8.x` on Maven Central
  (vendor portal: <https://eco.dameng.com/>), URL prefix
  `jdbc:dm://<host>:<port>`, default port 5236, schema-per-user
  Oracle-like model, type system Oracle-compatible 95%+, PL/SQL
  block syntax `DECLARE...BEGIN...END;/`, EXP/IMP utility commands.

External references are design inputs, not implementation approvals.

## 4. Support Statement

Target outcome: first-class Day-1 SQL Workbench support for Dameng
DM 8 SELECT + L1 metadata + risk-classified DML/DDL, through the
official `com.dameng:DmJdbcDriverX:8.1.x` JDBC path.

At the end of implementation, DataTalk must:

- create, edit, test, select, and delete Dameng connections with
  `databaseName` field reused as initial schema name (Oracle-precedent;
  no new ConnectionRecord column);
- discover schemas, tables, columns, and indexes through the existing
  Oracle metadata path (`ALL_TABLES` / `ALL_TAB_COLUMNS` /
  `ALL_INDEXES`); the dameng kind-private equivalence test
  `DamengMetadataEquivalenceTest` validates parity with Oracle
  baseline behavior on a real DM 8 fixture;
- resolve `schema` context (`databaseName` field carries the schema
  name; null fallback to current-user schema via DM driver default);
- execute SELECT / DML / DDL through the existing guarded SQL path
  with L1 / L2 / L3 confirmation, plus `dialect_unsupported`
  rejection at the SqlExecuteService dameng branch entry for
  three classes (PL/SQL block, PROCEDURE/FUNCTION/TRIGGER/PACKAGE
  DDL, EXP/IMP commands) detected by the new
  `detectDamengUnsupported(sql)` method on `CalciteSqlRiskAnalyzer`;
- split multi-statement scripts using the existing
  `GenericSqlStatementSplitter` (same as Oracle Day-1), proven
  equivalent on Dameng by `DamengSplitterEquivalenceTest`;
- classify Dameng-specific admin / DDL commands (TABLESPACE,
  USER, ROLE, GRANT/REVOKE, DROP TABLE/VIEW/INDEX/SEQUENCE/SYNONYM)
  as L3 under independent **anchored** risk patterns aggregated by
  the `dameng_admin_command` risk label;
- normalize JDBC return values (CHAR with trailing-space preservation,
  VARCHAR2, NUMBER as BigDecimal, DATE with hh:mm:ss, TIMESTAMP,
  CLOB / BLOB preview) using the existing `JdbcResultValueNormalizer`
  Oracle baseline proven equivalent on Dameng via
  `DamengResultNormalizationEquivalenceTest`;
- return structured `dialect_unsupported` for the seven diagnostics
  hooks (`lock_info`, `pool_status`, `table_space`,
  `terminate_session`, `optimize_table`, `explain_real`,
  `index_hints`) and the two ER hooks (`er_inspector`,
  `er_designer`) — consistent with Wave C umbrella §5 and the
  opengauss / oceanbase precedent;
- expose the `dameng` kind in MCP `ConnectionObjectType` enum,
  AGENTS.md prompt rules, and prompt contract tests **only after**
  child plan verify passes (manual smoke script SUCCESS + unit
  tests SUCCESS), never conflate it with `oracle`;
- reject unknown-kind input matching `dm`, `dm8`, `DM`, `DM8`, `达梦`
  at `ConnectionKind.normalize` with structured error guiding the
  user to type `dameng`.

Dameng remains unsupported until those checks pass.

## 5. Kind Naming

- **Canonical kind**: `dameng` (lower-case, single string, no
  aliases).
- **Aliases**: **none**. `dm`, `dm8`, `DM`, `DM8`, `Dameng`,
  `DAMENG`, `DM7`, `dameng7`, `dameng8`, `武汉达梦`, and Chinese
  `达梦` are not normalized to `dameng`. The error message for
  unknown-kind input guides the user to type `dameng` instead.
- **Persistence**: `ConnectionRecord.kind()`, API payloads, generated
  frontend types, and MCP schema enum all store `dameng`.
- **Frontend label**: `Dameng (DM 8)` (en) / `达梦 (DM 8)` (zh).
  Brand casing preserved; "DM 8" suffix indicates the major version
  band supported Day-1; DM 9 is out of scope (§12.6 item 5).
- **Oracle relationship**: protocol-level Oracle-likeness is a
  tooling reuse premise (metadata + normalizer + splitter), not an
  identity-level alias. `dameng` connections must not be persisted
  or displayed as `oracle` in any code path. Frontend picker must
  render Dameng as a separate row.
- **Single-mode**: Dameng has no compatibility mode field. The
  `compatibility_mode` column added by oceanbase Flyway V18 stays
  `NULL` for dameng rows, enforced by
  `MultiModeConnectionShape.validateModeForKind` (which currently
  routes any non-multi-mode kind through the `default` branch
  requiring `mode == null`).
- **Normalization boundary**: `ConnectionKind.normalize` (application
  layer REST and action entry points) is the single canonicalization
  point. Any scattered `equalsIgnoreCase("dameng")` or
  `equalsIgnoreCase("dm")` branch violates Wave C umbrella §7.3.

## 6. Connection And Persistence

### 6.1 Driver Decision (Umbrella §7.1 Gate — Highest Priority)

| Item | Decision | Note |
|---|---|---|
| Driver artifact | `com.dameng:DmJdbcDriverX:8.1.x` | Specific patch pinned by child plan Step 0 against current Maven Central visibility (likely `8.1.3.140` or later — verify at kickoff). DM 9 driver artifacts are out of scope for Day-1. |
| Driver class | `dm.jdbc.driver.DmDriver` | Service Loader auto-registered via `META-INF/services/java.sql.Driver`. Spring Boot fat-jar packaging must preserve this entry. |
| License | Commercial (Dameng Database Co., Ltd.) | Maven Central distribution is allowed by the license for download and integration use. **Binary redistribution rights**: none. This repository must NOT bundle the jar into a fat-jar artifact distributed externally. CI builds and developer machines pull from Maven Central per build. |
| Distribution path | **Maven Central direct** (umbrella §7.1 option a) | `<dependency><groupId>com.dameng</groupId><artifactId>DmJdbcDriverX</artifactId><version>8.1.x</version></dependency>` in `data-talk-infrastructure/pom.xml`. **Forbidden alternatives** per umbrella §10 termination conditions: (b) internal Maven mirror requiring company-specific URL, (c) offline jar checked into `lib/` directory. If child plan Step 0 finds Maven Central visibility regressed, the path falls back to (b) and **MUST** be re-approved by the user before continuing. |
| CI reproducibility | Maven Central pull is reproducible without bootstrap task | No "driver bootstrap" task needed — the `mvn` invocation handles it. The child plan Step 0 verifies CI runner can resolve the artifact. |
| Coexistence with Oracle driver | Both registered | `jdbc:oracle:thin:@...` taken by Oracle driver; `jdbc:dm://...` taken by DM driver; mutual non-interference via DriverManager `acceptsURL` routing. Verified by `DamengDriverCoexistenceTest`. |

### 6.2 URL Decision

URL template:

```
jdbc:dm://<host>:<port>
```

- **No `/<database>` suffix**: connection is server-level. Dameng has
  no `database` concept in URL (Oracle-like; one DM instance = one
  database conceptually).
- **Default port**: `5236` (DM 8 default).
- **Optional URL parameters** for schema initialization:
  `?schema=<schemaName>`. Child plan Step 0 verifies on the manual
  smoke fixture whether the DM 8 driver supports `?schema=` parameter
  natively. If not, fallback path is `executeUpdate("SET SCHEMA " +
  c.databaseName())` invoked by `ConnectionService` immediately after
  the JDBC connection opens. The child plan finalizes which path is
  used and updates this spec accordingly.
- **Username**: passed via JDBC `Properties.user`. No tenant /
  cluster suffix.
- **Username case-sensitivity**: passed verbatim to driver. Dameng's
  `IDENTITY_CASE_SENSITIVE=1` server config makes usernames
  case-sensitive; this spec does not normalize. Documentation in
  the child plan notes "verify case sensitivity matches your DM
  server config" as a common connection-failure cause.

### 6.3 ConnectionRecord Field Reuse (No New Columns)

Dameng adds **zero** new columns to `ConnectionRecord`. Reuse strategy:

| Field | Dameng semantics | Note |
|---|---|---|
| `kind` | `"dameng"` | per §5 canonical |
| `host` | DM server hostname or IP | required |
| `port` | DM server port | default 5236 |
| `databaseName` | **Initial schema name** (optional) | empty/null → connect with default-user schema; non-empty → DM driver `?schema=` parameter or post-connect `SET SCHEMA` (see §6.2) |
| `username` | DM database user | required; passed verbatim |
| `passwordEnc` | encrypted DM password | required |
| `compatibilityMode` | always `NULL` for dameng rows | enforced by `MultiModeConnectionShape.validateModeForKind` default branch |
| `oceanbaseTenant` / `oceanbaseCluster` | always `NULL` for dameng rows | enforced by oceanbase Flyway V18 CHECK (kind <> 'oceanbase' OR ...) |

No Flyway migration is needed for dameng. The existing schema (after
oceanbase V18) already covers all dameng fields.

### 6.4 ConnectionKind Enum and Normalization

```java
public enum ConnectionKind {
  MYSQL, POSTGRESQL, /* ... */ OCEANBASE, DAMENG;
}
```

`ConnectionKind.normalize(String input)` rules for Dameng:

- `dameng` → `DAMENG` (lower-case canonical accepted)
- `Dameng`, `DAMENG` → lower-cased and accepted
- `dm`, `dm8`, `DM`, `DM8`, `DM7`, `dameng7`, `dameng8`,
  `武汉达梦`, `达梦` → **rejected** with structured "unknown kind"
  error guiding user to type `dameng`
- AI prompt rules (post-verify) may include Chinese alias `达梦` /
  `武汉达梦` for user-typed-request recognition only; they are **not**
  alias targets at the `ConnectionKind.normalize` boundary
  (umbrella §7.5).

## 7. Metadata Discovery

### 7.1 Reuse Path (Oracle Branch Equivalent)

`ConnectionTargetDiscoveryService` adds `dameng` branch routing
through the existing Oracle discovery path:

```java
// pseudo
case "oracle", "dameng" -> oracleDiscovery(c);
```

DM 8 data dictionary views are Oracle-compatible (95%+, the core
vendor-claimed compatibility). Reused queries:

- `SELECT NAME FROM SYS.SYSOBJECTS WHERE TYPE$='SCH' AND SUBTYPE$='USER'` (alternative: `ALL_USERS`) — schema enumeration
- `SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = ?` — table enumeration
- `SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, NULLABLE FROM ALL_TAB_COLUMNS WHERE OWNER = ? AND TABLE_NAME = ?` — column enumeration
- `SELECT INDEX_NAME, COLUMN_NAME FROM ALL_IND_COLUMNS WHERE TABLE_OWNER = ? AND TABLE_NAME = ?` — index enumeration

### 7.2 System Schema Filter

Dameng default system schemas (baseline; child plan Task 4 fixture
verification补全 if additions found):

```java
private static final Set<String> DAMENG_SYSTEM_SCHEMAS = Set.of(
    "SYS",         // system objects
    "SYSDBA",      // DBA user
    "SYSAUDITOR",  // audit
    "SYSSSO",      // security
    "CTISYS"       // full-text indexing
);
```

The 5 baseline items represent Dameng's standard deployment system
schemas. Child plan Task 4 runs `SELECT USERNAME FROM ALL_USERS` on
the manual smoke fixture and reconciles against vendor docs to fill
in any vendor-extension schemas (e.g., backup-related schemas only
in enterprise edition).

### 7.3 Schema Field Semantics

- `databaseName` field carries the **initial schema** for the
  connection (Oracle-precedent — `databaseName` is reused per kind:
  oracle uses it for SID or service name, dameng uses it for
  schema). Documentation in `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
  is updated to note this dameng semantic.
- Discovery layer queries `databaseName` value as the `OWNER`
  filter; if `databaseName` is null/blank, discovery defaults to
  the connection's current-user schema returned by
  `SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM DUAL`.
- No "catalog" concept exposed in Dameng UI (DM 8 has one
  conceptual catalog per server; not user-facing).

### 7.4 Reuse Inventory

| Reuse point | Source | Test obligation (Wave C §7.3 Reuse-With-Tests) |
|---|---|---|
| `oracleDiscovery()` body | existing Oracle branch | `DamengMetadataEquivalenceTest` (kind-private; no shared abstract base — Dameng is the only Oracle-like wave-c kind) |
| `ConnectionTargetDiscoveryService` switch dispatch | same | covers schema list / table list / columns / indexes (4 cases) |
| System-schema filter pattern | Oracle branch equivalent | Dameng-specific filter list has its own unit test |

### 7.5 Independent Inventory

| Independent point | Reason |
|---|---|
| Dameng system-schema filter list | Oracle baseline does not cover Dameng-specific items (CTISYS, SYSSSO, SYSAUDITOR) |
| `databaseName` semantics = "initial schema" | Documented in `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` as kind-specific reuse |
| `SET SCHEMA` post-connect fallback | If DM 8 driver does not support `?schema=` URL parameter |

## 8. SQL Execution / Splitter / Risk Classifier

### 8.1 SQL Execution Path

`SqlExecuteService` adds `dameng` branch with two-stage entry:

1. **Stage 1 — `dialect_unsupported` detection** (BEFORE JDBC):
   invokes `CalciteSqlRiskAnalyzer.detectDamengUnsupported(sql)`;
   non-empty `Optional<DamengUnsupportedReason>` result returns
   structured outcome immediately, **never** reaching JDBC. This
   is single-source-of-truth: the detection logic lives in the
   risk classifier alongside the L3 patterns.
2. **Stage 2 — normal guarded execution**: if Stage 1 passes,
   route through the same path as Oracle (L1-L2-L3 confirmation,
   `JdbcResultValueNormalizer` Oracle baseline, parameterized
   statements, `SET autocommit` behavior).

### 8.2 Splitter

```java
case "oracle", "dameng" -> genericSplitter;  // single instance
```

Same as Oracle Day-1 (which uses `GenericSqlStatementSplitter`
per `DefaultSqlStatementSplitters.java:33`). Verified equivalent on
Dameng by `DamengSplitterEquivalenceTest`, covering 5 cases:

- Single statement
- `;`-delimited multi-statement
- String-literal `;` escape
- Comment-block `;` ignore
- Empty-statement filtering

PL/SQL block (`DECLARE...BEGIN...END;`) handling is deliberately
**out of splitter scope**: such input is rejected upstream at
SqlExecuteService Stage 1 by `detectDamengUnsupported` before
reaching the splitter.

### 8.3 Risk Classifier — Dual Channel

`CalciteSqlRiskAnalyzer` adds dameng branch with two methods:

**Channel 1 — `classifyDamengSpecific(sql)`**: returns
`Optional<RiskLevel>`; non-empty L3 result takes precedence over
base classification.

**Channel 2 — `detectDamengUnsupported(sql)`**: returns
`Optional<DamengUnsupportedReason>`; non-empty result triggers
Stage 1 rejection in `SqlExecuteService`.

**Spec Author Note (per Wave C §10 governance)**: all new patterns
**MUST** use `^\s*` start anchor + `\b` word boundary; `contains()`
is **forbidden**. This rule is verbatim aligned with the ad4c1f0
governance reset incident.

#### Channel 1 — 5 anchored L3 Pattern constants

```java
// CalciteSqlRiskAnalyzer.java — new private static final fields

private static final Pattern DAMENG_TABLESPACE_DDL = Pattern.compile(
    "^\\s*(CREATE|ALTER|DROP)\\s+TABLESPACE\\b",
    Pattern.CASE_INSENSITIVE);

private static final Pattern DAMENG_USER_DDL = Pattern.compile(
    "^\\s*(CREATE|ALTER|DROP)\\s+USER\\b",
    Pattern.CASE_INSENSITIVE);

private static final Pattern DAMENG_ROLE_DDL = Pattern.compile(
    "^\\s*(CREATE|ALTER|DROP)\\s+ROLE\\b",
    Pattern.CASE_INSENSITIVE);

private static final Pattern DAMENG_GRANT_REVOKE = Pattern.compile(
    "^\\s*(GRANT|REVOKE)\\b",
    Pattern.CASE_INSENSITIVE);

private static final Pattern DAMENG_DROP_OBJECT = Pattern.compile(
    "^\\s*DROP\\s+(TABLE|VIEW|INDEX|SEQUENCE|SYNONYM)\\b",
    Pattern.CASE_INSENSITIVE);
```

All 5 patterns hit ⇒ L3. Risk label aggregation: every Dameng-specific
L3 hit is labeled `dameng_admin_command`. The matched sub-command
name is included in the error message body, not in the structured
label.

`TRUNCATE` and `ALTER TABLE` are recognized as L3 by the existing
base classifier — no additional dameng-specific pattern needed for
these.

#### Channel 2 — 3 anchored `dialect_unsupported` Pattern constants

```java
private static final Pattern DAMENG_PLSQL_BLOCK = Pattern.compile(
    "^\\s*(DECLARE|BEGIN)\\b",
    Pattern.CASE_INSENSITIVE);

private static final Pattern DAMENG_PROCEDURE_DDL = Pattern.compile(
    "^\\s*(CREATE|ALTER|DROP)(\\s+OR\\s+REPLACE)?\\s+(PROCEDURE|FUNCTION|TRIGGER|PACKAGE(\\s+BODY)?)\\b",
    Pattern.CASE_INSENSITIVE);

private static final Pattern DAMENG_EXP_IMP = Pattern.compile(
    "^\\s*(EXP|IMP)\\s+",
    Pattern.CASE_INSENSITIVE);
// EXP/IMP must be followed by whitespace; prevents SELECT EXP(...) misclassification
```

Each pattern maps to a specific i18n key:

| Pattern | i18n key | English message |
|---|---|---|
| `DAMENG_PLSQL_BLOCK` | `risk.dialect_unsupported.dameng.plsql_block` | "Dameng PL/SQL blocks (DECLARE/BEGIN ... END;) are not supported in Day-1. Use a separate statement instead." |
| `DAMENG_PROCEDURE_DDL` | `risk.dialect_unsupported.dameng.procedure_ddl` | "Dameng PROCEDURE / FUNCTION / TRIGGER / PACKAGE DDL is not supported in Day-1. See Wave C dameng Day-3 candidate." |
| `DAMENG_EXP_IMP` | `risk.dialect_unsupported.dameng.exp_imp_command` | "Dameng EXP/IMP utility commands are not supported in Day-1. Use the Dameng SQL Workbench directly for data import/export." |

### 8.4 Risk Boundary Tests (Mandatory Per Pattern)

Each pattern has a paired test set:

**Channel 1 hit cases** (must classify as L3):
- `CREATE TABLESPACE ts1 DATAFILE 'ts1.dbf' SIZE 100M;`
- `ALTER USER scott IDENTIFIED BY new_pw;`
- `DROP ROLE admin_role;`
- `GRANT SELECT ON t1 TO scott;`
- `DROP TABLE t1;` / `DROP VIEW v1;` / `DROP INDEX idx1;`

**Channel 1 boundary cases** (must NOT match dameng L3):
- `UPDATE my_user_table SET ...` ← does not match `DAMENG_USER_DDL`
- `SELECT * FROM tablespace_history` ← does not match `DAMENG_TABLESPACE_DDL`
- `INSERT INTO grant_log ...` ← does not match `DAMENG_GRANT_REVOKE`
- `SELECT role_name FROM all_roles` ← does not match `DAMENG_ROLE_DDL`
- `INSERT INTO drop_audit ...` ← does not match `DAMENG_DROP_OBJECT`

**Channel 2 hit cases** (must trigger `dialect_unsupported`):
- `DECLARE v_x INT; BEGIN v_x := 1; END;` → PLSQL_BLOCK
- `BEGIN DBMS_OUTPUT.PUT_LINE('x'); END;` → PLSQL_BLOCK
- `CREATE PROCEDURE p1 AS BEGIN NULL; END;` → PROCEDURE_DDL
- `CREATE OR REPLACE FUNCTION f1 RETURN INT AS BEGIN RETURN 1; END;` → PROCEDURE_DDL
- `DROP TRIGGER t1;` → PROCEDURE_DDL
- `EXP scott/tiger@dm FILE=demo.dmp` → EXP_IMP

**Channel 2 boundary cases** (must NOT trigger):
- `SELECT EXP(2) FROM DUAL` ← does not match `DAMENG_EXP_IMP` (no whitespace after EXP)
- `SELECT IMP_TO_DATE('2026-01-01') FROM DUAL` ← does not match (no whitespace boundary)
- `   BEGIN ... END;` (leading whitespace) ← matches PLSQL_BLOCK due to `\s*` anchor
- `INSERT INTO begin_log` ← does not match PLSQL_BLOCK (`\b` after BEGIN)

### 8.5 Reuse Inventory

| Reuse point | Source | Test obligation |
|---|---|---|
| `GenericSqlStatementSplitter` | existing (Oracle Day-1 also uses) | `DamengSplitterEquivalenceTest` |
| `JdbcResultValueNormalizer` Oracle baseline | existing Oracle branch | `DamengResultNormalizationEquivalenceTest` |
| `oracleDiscovery()` metadata path | existing Oracle branch (§7) | `DamengMetadataEquivalenceTest` |
| L1-L2-L3 confirmation flow | framework | covered by integration tests (manual smoke) |

### 8.6 Independent Inventory

| Independent point | Reason |
|---|---|
| `classifyDamengSpecific()` (Channel 1, 5 patterns) | Dameng-specific admin / DDL patterns; Oracle classifier does not exist as a shared base |
| `detectDamengUnsupported()` (Channel 2, 3 patterns) | Day-1 unsupported-entry detection; routed by SqlExecuteService Stage 1 |
| `dameng_admin_command` risk label | Dameng aggregation label |
| Stage 1 / Stage 2 SqlExecuteService entry split | Single-source-of-truth pattern; Oracle does not need this (no Day-1 dialect_unsupported set) |

## 9. Diagnostics Provider — 9 hooks Matrix

### 9.1 Provider Class

`DamengDiagnosticsProvider extends AbstractDiagnosticsProvider`,
consistent with the 11-dialect framework landed by Day-2 plan. Path:

```
server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/DamengDiagnosticsProvider.java
```

### 9.2 9-Hook Decision Matrix — All `dialect_unsupported` Day-1

Per Wave C umbrella §5 Day-1 unsupported set and the opengauss /
oceanbase precedent, all 9 diagnostics hooks return structured
`dialect_unsupported` Day-1.

| Hook | Day-1 Decision | Day-2/Day-3 Upgrade Owner |
|---|---|---|
| `lock_info` | `dialect_unsupported` | Independent Day-3 spec |
| `pool_status` | `dialect_unsupported` | Independent Day-3 spec |
| `table_space` | `dialect_unsupported` | Independent Day-3 spec |
| `terminate_session` | `dialect_unsupported` | Independent Day-3 spec |
| `optimize_table` | `dialect_unsupported` | Independent Day-3 spec |
| `explain_real` | `dialect_unsupported` | day2 plan §Day-3 dameng row (`EXPLAIN <sql>` tabular output via new `DamengTabularGrammar` reusing day2 `TabularLayout` / `TextPlanGrammar` records) |
| `index_hints` | `dialect_unsupported` | day2 plan §Day-3 explicitly defers ("Day-3 暂不推荐" — type normalization prerequisites must land first); independent Day-3+ spec when ready |
| `er_inspector` | `dialect_unsupported` | Wave C overall ER upgrade (umbrella §5) |
| `er_designer` | `dialect_unsupported` | Same |

### 9.3 dialect_unsupported Anchor Format

All 9 hook error messages share a structured anchor consistent with
the opengauss / oceanbase pattern:

```
"Dameng does not support <hook> in Day-1.
 See Wave C Day-3 candidate (dameng upgrade path) in this spec §11."
```

i18n keys (consistent with Day-2 plan naming and oceanbase
precedent):

- `diagnostics.dialect_unsupported.dameng.lock_info`
- `diagnostics.dialect_unsupported.dameng.pool_status`
- `diagnostics.dialect_unsupported.dameng.table_space`
- `diagnostics.dialect_unsupported.dameng.terminate_session`
- `diagnostics.dialect_unsupported.dameng.optimize_table`
- `diagnostics.dialect_unsupported.dameng.explain_real`
- `diagnostics.dialect_unsupported.dameng.index_hints`
- `diagnostics.dialect_unsupported.dameng.er_inspector`
- `diagnostics.dialect_unsupported.dameng.er_designer`

### 9.4 Reuse Surface

Dameng diagnostics Day-1 has no reuse surface to exercise (all 9
hooks return `dialect_unsupported` per Wave C umbrella §5). The
existing `OracleDiagnosticsProvider` does implement EXPLAIN and
INDEX_HINTS for Oracle, and DM 8's Oracle-compatible plan output
makes future reuse plausible. However, those reuse decisions are
made in the respective Day-2/Day-3 specs (per §11), not Day-1.
Day-1 tests are minimal and kind-private:

- `DamengDiagnosticsDialectUnsupportedTest` — verifies all 9 hooks
  return structured outcome with the correct i18n key (per §9.3) and
  consistent Day-1 anchor message
- `DamengDiagnosticsProviderRegistrationTest` — verifies the provider
  is registered and routed when `kind = dameng`

## 10. Reuse Outputs (None)

Dameng produces **zero new cross-kind reuse abstractions**. It is
the only Wave C Oracle-like kind; there is no sister kind to
consume any potential `OracleProtocolReuseRule` or similar abstract
base. Per Wave C umbrella §7.3 Reuse-With-Tests rule and YAGNI:

- No `OracleProtocolReuseRule` introduced (single-consumer
  abstractions are over-engineering)
- All Dameng reuse of existing Oracle code paths is verified by
  kind-private equivalence tests:
  `DamengSplitterEquivalenceTest`,
  `DamengMetadataEquivalenceTest`,
  `DamengResultNormalizationEquivalenceTest`
- Dameng does **not** consume `MultiModeConnectionShape` or
  `PgForkReuseRule` (single-mode + non-PG-fork)
- Future Oracle-like additions (e.g., a hypothetical
  `tibero` / `DM 9`) trigger a re-evaluation of whether to extract
  a shared base; the trigger is two consumers, not one

## 11. Day-2 / Day-3 Upgrade Path — Bidirectional Anchor

### 11.1 Upgrade Matrix Scope

day2 plan (`docs/exec-plans/2026-05-08-diagnostics-day2-plan.md`)
§Day-3 candidate matrix is bound to **EXPLAIN-real and INDEX_HINTS
upgrade only** (per day2 plan §Day-3 line 3110: "Wave-C 4 kind
Day-2 EXPLAIN/INDEX_HINTS 真实化"). The other 5 capabilities
(`lock_info`, `pool_status`, `table_space`, `terminate_session`,
`optimize_table`) are explicitly out of day2 plan scope and live in
independent Day-3 specs.

### 11.2 Day-2 Plan §Day-3 Anchored Items (dameng row)

This is bidirectionally anchored: day2 plan §Day-3 candidate matrix
dameng row already lists the EXPLAIN item (line 3121: "新建
`DamengTabularGrammar`") and explicitly defers INDEX_HINTS.

| Upgrade Item | Day-1 Status | Day-2 Path | day2 plan §Day-3 dameng row |
|---|---|---|---|
| `explain_real` | `dialect_unsupported` | `EXPLAIN <sql>` tabular output; new `DamengTabularGrammar` reusing day2 `TabularLayout` + `TextPlanGrammar` records (Day-2 plan Task 0.1/0.2 already shipped); per-kind grammar lives in `DamengDiagnosticsProvider` | ✅ already listed: "EXPLAIN / 新建 DamengTabularGrammar" |
| `index_hints` | `dialect_unsupported` | Deferred: Oracle-style B-tree + bitmap recommendation requires CHAR/VARCHAR2/NUMBER type normalization to land first (per day2 plan §Day-3 line 3121: "Day-3 暂不推荐") | ✅ already listed as deferred |

The child plan documentation-housekeeping task verifies the
bidirectional anchor (no new day2 plan backfill required at this
child plan ship time — day2 plan §Day-3 dameng row is already
complete).

### 11.3 Independent Day-3 Specs (Out Of day2 Plan Scope)

| Upgrade Item | Day-1 Status | Independent Day-3 Path |
|---|---|---|
| `lock_info` | `dialect_unsupported` | New spec uses Dameng `V$LOCK` / `V$SESSION` views (Oracle-compatible names) |
| `pool_status` | `dialect_unsupported` | New spec uses `V$SESSION` filtering |
| `table_space` | `dialect_unsupported` | New spec uses `DBA_SEGMENTS` / `USER_SEGMENTS` aggregation |
| `terminate_session` | `dialect_unsupported` | New spec uses `ALTER SYSTEM KILL SESSION '<sid>,<serial#>'` (Oracle-compatible) |
| `optimize_table` | `dialect_unsupported` | New spec uses `DBMS_STATS` / Dameng-specific `SP_TABLEDEF_REBUILD` (vendor docs to verify) |

### 11.4 PL/SQL / Procedure DDL — Independent Day-3 Spec

Day-1 explicitly rejects the following via `detectDamengUnsupported`:

- PL/SQL block (`DECLARE...BEGIN...END;`)
- PROCEDURE / FUNCTION / TRIGGER / PACKAGE DDL
- EXP / IMP utility commands

Day-3 first-class enablement requires:

- A dedicated Dameng-specific splitter (`DamengSqlStatementSplitter`)
  handling `/` PL/SQL terminator
- Extended risk classifier supporting PL/SQL block as L3 (not
  unsupported)
- Front-end SQL editor PL/SQL block detection and confirmation flow
- Independent Day-3 child design + plan; not in day2 plan scope

### 11.5 Other Out-Of-Scope Day-3 Items

| Item | Owner |
|---|---|
| DM 9 driver support | Day-3 evaluation; independent plan |
| Dameng DataWatch / DSC cluster / read-write splitting / replication UI | Not on roadmap |
| Dameng enterprise-only features (geospatial / full-text / XML) | Not on roadmap |
| Offline jar in repo | **Forbidden** by umbrella §10; never enabled |
| Auto schema switching | Not on roadmap (user must explicitly fill or leave blank for default) |
| CI Testcontainers for Dameng | Reserved for T1 upgrade evaluation; not Day-1 |
| Dameng Cloud / managed service | Not on roadmap |

## 12. Out-of-Scope / T2 Fixture / i18n / AGENTS.md Timing

### 12.1 T2 Fixture — Manual Smoke Path

Wave C umbrella §7.4 T2 definition: "restricted fixture: trial
license or restricted image required but reproducible. Child plan
includes manual smoke scripts plus unit tests plus documented
prerequisites." Dameng follows this discipline; sister kind:
`kingbase`.

**Manual smoke script**: `tools/manual-smoke/dameng-day1.sh`

Structure:

```bash
#!/usr/bin/env bash
# tools/manual-smoke/dameng-day1.sh
#
# Prerequisites:
# 1. A reachable Dameng DM 8 server (trial license or development
#    edition). Vendor portal: https://eco.dameng.com/
# 2. Environment variables: DAMENG_HOST, DAMENG_PORT (default 5236),
#    DAMENG_USER, DAMENG_PASSWORD, DAMENG_SCHEMA (optional)
# 3. The datatalk backend running locally on 8080
#
# This script runs 9 case smoke checks against the running DataTalk
# backend through its REST API. CI does not run this script.

# 9 cases:
# 1. Connection success + connection failure (wrong password)
# 2. Schema list discovery
# 3. Table list discovery (one demo table)
# 4. Column list discovery (one demo table)
# 5. L1 SELECT execution
# 6. L2 INSERT / UPDATE
# 7. L3 DELETE / DROP (with confirmation simulation)
# 8. dialect_unsupported PL/SQL block rejection
# 9. Error message i18n key resolution
```

**Unit tests**: All Dameng unit tests run on CI without a real DM
server (use JDBC mocks or hard-coded fixtures):

- `DamengUrlBuilderTest` — URL construction with / without schema
- `DamengConnectionRecordValidationTest` — application-layer
  ConnectionService validation
- `DamengSplitterEquivalenceTest` — 5 splitter cases
- `DamengRiskClassifierTest` — 5 + 3 = 8 anchored patterns × hit /
  boundary cases (per §8.4)
- `DamengResultNormalizationEquivalenceTest` — 5 type round-trip
  cases via mock ResultSet
- `DamengMetadataEquivalenceTest` — 4 discovery cases via mock
  Connection
- `DamengDiagnosticsDialectUnsupportedTest` — 9 hooks
- `DamengDiagnosticsProviderRegistrationTest` — provider routing
- `DamengDriverCoexistenceTest` — DriverManager `acceptsURL`
  routing between Oracle / DM drivers

**CI does not run**: `dameng-day1.sh` manual smoke; CI pipeline
explicitly skips dameng integration tests.

**Documentation prerequisites** (in `docs/exec-plans/2026-05-08-data-source-coverage-dameng-plan.md`):

- Dameng eco portal URL: <https://eco.dameng.com/>
- Trial license retrieval steps
- DM 8 Docker image availability check (vendor mirror, not Docker
  Hub mainline)
- Common connection-failure causes (case-sensitive username, port,
  schema permission)

### 12.2 No Concrete IT Subclasses

Unlike oceanbase (which produces 6 concrete `OceanBase*ReuseIT`
inheriting `MySqlProtocolReuseRule`), Dameng produces **zero**
concrete cross-kind IT subclasses. Reuse is verified by
kind-private equivalence tests (§8.5 + §7.4) and by the manual
smoke script.

### 12.3 Dameng-Specific Tests (Kind-Private)

See §12.1 unit test list. All run on CI without a real DM server.

### 12.4 i18n Labels and Keys

- Frontend picker label: `Dameng (DM 8)` (en) / `达梦 (DM 8)` (zh)
- i18n keys:
  - `connection.kind.dameng.label` = `Dameng (DM 8)`
  - `connection.kind.dameng.label.zh` = `达梦 (DM 8)`
  - `connection.kind.dameng.schema_placeholder` (form hint)
  - `connection.kind.dameng.schema_help` (form aria-describedby)
  - `risk.dialect_unsupported.dameng.plsql_block`
  - `risk.dialect_unsupported.dameng.procedure_ddl`
  - `risk.dialect_unsupported.dameng.exp_imp_command`
  - `diagnostics.dialect_unsupported.dameng.lock_info`
  - `diagnostics.dialect_unsupported.dameng.pool_status`
  - `diagnostics.dialect_unsupported.dameng.table_space`
  - `diagnostics.dialect_unsupported.dameng.terminate_session`
  - `diagnostics.dialect_unsupported.dameng.optimize_table`
  - `diagnostics.dialect_unsupported.dameng.explain_real`
  - `diagnostics.dialect_unsupported.dameng.index_hints`
  - `diagnostics.dialect_unsupported.dameng.er_inspector`
  - `diagnostics.dialect_unsupported.dameng.er_designer`
- AI prompt rules (post-verify) may include Chinese aliases `达梦` /
  `武汉达梦` for user-typed-request recognition only; they are
  **not** alias targets at `ConnectionKind.normalize` (umbrella §7.5).

### 12.5 AGENTS.md / MCP Timing (Wave C umbrella §7.5)

| Time Point | AGENTS.md Status | MCP `ConnectionObjectType` Enum |
|---|---|---|
| Design approval (this spec lands) | ❌ no dameng | ❌ no dameng |
| Child plan draft | ❌ | ❌ |
| Child plan implementation in progress | ❌ | ❌ |
| Child plan verify passes (manual smoke SUCCESS + unit tests SUCCESS) | ✅ same PR adds dameng section | ✅ same PR adds enum |
| Snapshot upgrade to first-class | ✅ live | ✅ live |

### 12.6 Out-of-Scope (11 items explicitly NOT done in this spec)

1. PL/SQL block execution (`DECLARE` / `BEGIN ... END;`) —
   `dialect_unsupported` Day-1; Day-3 independent spec
2. PROCEDURE / FUNCTION / TRIGGER / PACKAGE DDL —
   `dialect_unsupported` Day-1; Day-3 independent spec
3. EXP / IMP utility commands — `dialect_unsupported` Day-1
4. All 9 diagnostics + ER hooks return `dialect_unsupported`
   (consistent with Wave C umbrella §5)
5. DM 9 series driver — Day-3 evaluation only; Day-1 supports
   DM 8 only (`com.dameng:DmJdbcDriverX:8.1.x`)
6. Dameng DataWatch / DSC cluster / read-write splitting /
   replication UI — never in scope
7. Dameng enterprise-edition-only features (geospatial /
   full-text / XML / advanced security) — never in scope
8. Offline jar in repo — **forbidden** by umbrella §10
   termination conditions
9. Auto schema switching — user must explicitly fill or leave
   blank for default-user schema
10. CI starting Dameng container — explicitly out of CI scope
    Day-1; T1 upgrade is a separate Day-3 evaluation
11. Dameng Cloud / managed service connection-form variant —
    Day-1 supports self-hosted / on-premise only
