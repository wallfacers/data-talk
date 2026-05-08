# Data Source Coverage: OceanBase Design

Date: 2026-05-08
Status: Draft (awaiting user review)
Wave: Wave C, step 3 (per Wave C umbrella §6 recommended order)

## 1. Purpose

OceanBase is the third kind in Wave C of Task 9 Data Source Coverage Expansion.
It is selected as step 3 because it validates the **Compatibility-Mode Policy**
locked by [Wave C umbrella §7.2](./2026-05-08-data-source-coverage-wave-c-design.md):
one canonical kind, two compatibility modes (MySQL-mode / Oracle-mode), Day-1
ships only MySQL-mode first-class while Oracle-mode returns
`dialect_unsupported`. Step 3 also produces the **`MultiModeConnectionShape`**
cross-kind reuse abstraction (compatibility mode field + Flyway V18 + frontend
multi-mode form skeleton) that step 4 (`kingbase`) consumes per
[Wave C umbrella §11.4](./2026-05-08-data-source-coverage-wave-c-design.md).

This artifact does not expose OceanBase as supported. OceanBase remains
unsupported until the child implementation plan completes verification and the
Current Support Snapshot in
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)
is updated.

**Key new outputs of step 3:**

- `MultiModeConnectionShape` v1 (application-layer abstract unit, kind-neutral
  naming, consumed by `kingbase` step 4)
- Flyway migration `V18__multimode_and_oceanbase_fields.sql`
  (`compatibility_mode` shared column + `oceanbase_tenant` / `oceanbase_cluster`
  kind-private columns)
- 6 concrete `OceanBase*ReuseIT` subclasses inheriting `MySqlProtocolReuseRule`
  (closes the cross-kind reuse loop that started with `tidb`, commit `273f1e1`)
- `OceanBaseDiagnosticsProvider` Day-1 with 4 supported / 1 partial / 4
  `dialect_unsupported` hooks; the 5 dialect_unsupported / partial hooks are
  bidirectionally anchored to the Day-2 plan §Day-3 candidate matrix

## 2. Compatibility Gate Application

The mandatory gate is
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
OceanBase implementation must apply every database-type area:

- canonical naming and `ConnectionKind.normalize` boundary;
- backend connection kind, URL builder, driver packaging, connection test,
  metadata, target resolution, SQL execution, result normalization, splitter,
  risk guard, diagnostics, and localized error messages;
- frontend connection form (multi-mode skeleton + `tenant` / `cluster` fields),
  picker, default port, Query Editor context, formatter, outline,
  accessibility, semantic tokens, and i18n;
- MCP `ConnectionObjectType` enum, runtime `AGENTS.md`, prompt contract
  tests, and tool naming;
- ER Inspector and ER Designer return `dialect_unsupported` (per Wave C
  umbrella §5 Day-1 unsupported set).

No section is N/A: first-class OceanBase MySQL-mode Day-1 support touches every
database compatibility area listed above. Oracle-mode is structurally absent
from Day-1 and returns `dialect_unsupported` at every entry point — that is
itself a compatibility decision, not a gate skip.

## 3. Design Inputs

- [docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md](./2026-05-08-data-source-coverage-wave-c-design.md)
  — Wave C umbrella. Locks `MySqlProtocolReuseRule` reuse policy (§8),
  Compatibility-Mode Policy (§7.2), Reuse-With-Tests rule (§7.3), AGENTS.md
  naming policy (§7.5), Day-1 unsupported set (§5), recommended order (§6),
  and `MultiModeConnectionShape` ownership (§11.4).
- [docs/product-specs/2026-05-08-data-source-coverage-wave-c-roadmap.md](./2026-05-08-data-source-coverage-wave-c-roadmap.md)
  — sub-wave execution navigation. Locks oceanbase as step 3 (start order
  parallel with opengauss + dameng; kingbase serial after) and downstream
  consumers of `MultiModeConnectionShape` (kingbase plan kickoff hard gate).
- [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)
  — hard compatibility checklist.
- [client/DESIGN.md](../../client/DESIGN.md) — frontend connection form,
  picker, Query Editor context, and unsupported-state UI must use semantic
  tokens, accessible controls, global Stage state, and i18n keys. Multi-mode
  field rendering and disabled-mode tooltip follow control-state matrix.
- [docs/product-specs/2026-05-08-data-source-coverage-tidb-design.md](./2026-05-08-data-source-coverage-tidb-design.md)
  — `MySqlProtocolReuseRule` origin spec. OceanBase reuses every abstract
  base class produced by tidb step 1.
- [docs/product-specs/2026-05-08-data-source-coverage-opengauss-design.md](./2026-05-08-data-source-coverage-opengauss-design.md)
  — sister Wave C step 2 (PG-fork). Kind-naming, anchored risk pattern
  conventions, fixture wait strategy, and Day-2 anchor format are reused.
- [docs/exec-plans/2026-05-08-diagnostics-day2-plan.md](../exec-plans/2026-05-08-diagnostics-day2-plan.md)
  — 11 dialect diagnostics framework, `parseMySqlJsonPlan` reuse hook
  (Day-2 §Task 0.3), and §Day-3 candidate matrix. Bidirectionally anchored
  with §11 of this spec.
- OceanBase JDBC official documentation
  (<https://en.oceanbase.com/docs/community-jdbc-en>; re-checked at child
  plan kickoff): `com.oceanbase:oceanbase-client` driver, `jdbc:oceanbase://`
  URL prefix, default port 2881 (OBServer direct) / 2883 (OBProxy), username
  format `<user>@<tenant>#<cluster>`, dual MySQL-mode / Oracle-mode tenant
  model, OB-specific commands (`OUTLINE` / `TENANT` / `RESOURCE` /
  `MAJOR FREEZE` / `BACKUP` / `ALTER SYSTEM`).

External references are design inputs, not implementation approvals.

## 4. Support Statement

Target outcome: first-class SQL Workbench support for OceanBase MySQL-mode
through the `com.oceanbase:oceanbase-client` JDBC path.

At the end of implementation, DataTalk must:

- create, edit, test, select, and delete OceanBase connections with
  structured `tenant` / `cluster` / `compatibility_mode` fields persisted on
  separate `ConnectionRecord` columns;
- compose the JDBC username `<user>@<tenant>[#<cluster>]` at a single
  `ConnectionService` point, never at the form, URL, or jdbcParams layer;
- discover databases and tables through the MySQL-protocol metadata path with
  OceanBase system-schema filtering and OceanBase-specific equivalence
  tests via `AbstractMySqlMetadataReuseTest`;
- resolve `database` context (no `schema` for OceanBase MySQL-mode);
- execute SELECT / DML / DDL through the existing guarded SQL path with L1 /
  L2 / L3 confirmation;
- split multi-statement scripts (including `DELIMITER`) using the existing
  `MySqlSqlStatementSplitter`, proven equivalent on OceanBase by the
  `AbstractMySqlSplitterEquivalenceTest` kit;
- classify OceanBase-specific admin / DDL commands (`OUTLINE`, `TENANT`,
  `RESOURCE POOL/UNIT`, `ALTER SYSTEM`, `MAJOR/MINOR FREEZE`,
  `BACKUP`/`RESTORE`) as L3 under independent **anchored** risk patterns;
- normalize JDBC return values (JSON, DECIMAL, BIT, `_BINARY`, ENUM, SET, YEAR)
  using the existing `JdbcResultValueNormalizer` proven equivalent on
  OceanBase via `AbstractMySqlResultNormalizationReuseTest`;
- return structured diagnostics outcomes via `OceanBaseDiagnosticsProvider`:
  4 supported hooks (`table_space`, `terminate_session`, `index_hints`,
  `pool_status` partial) + 5 `dialect_unsupported` hooks (`lock_info`,
  `optimize_table`, `explain_real`, `er_inspector`, `er_designer`) all
  anchored to Day-2 plan §Day-3 oceanbase row;
- expose the `oceanbase` kind in MCP `ConnectionObjectType` enum, AGENTS.md
  prompt rules, and prompt contract tests **only after** child plan verify
  passes, never conflate it with `mysql`;
- reject Oracle-mode connection attempts at form, ConnectionService, and
  every downstream entry point with structured `dialect_unsupported` and an
  i18n message anchoring the Day-3 oceanbase Oracle-mode upgrade path.

OceanBase remains unsupported until those checks pass.

## 5. Kind Naming

- **Canonical kind**: `oceanbase` (lower-case, single string, no aliases).
- **Aliases**: **none**. `oceanbase-ce`, `ob`, `obcluster`, `OceanBase`,
  `OCEANBASE`, `oceanbase_ce`, and similar user-typed variants are not
  normalized to `oceanbase`. The error message for unknown-kind input guides
  the user to type `oceanbase` instead.
- **Persistence**: `ConnectionRecord.kind()`, API payloads, generated
  frontend types, and MCP schema enum all store `oceanbase`.
- **Frontend label**: `OceanBase` (brand casing, not translated for zh-CN).
- **MySQL relationship**: protocol-level compatibility is a tooling reuse
  premise, not an identity-level alias. `oceanbase` connections must not be
  persisted or displayed as `mysql` in any code path. Frontend picker must
  render OceanBase as a separate row.
- **Compatibility-mode relationship**: MySQL-mode and Oracle-mode share the
  canonical kind `oceanbase`. They are distinguished by the
  `compatibility_mode` connection-level field, never by separate kinds. Per
  Wave C umbrella §7.2 a new canonical kind is **forbidden** for new modes.
- **Normalization boundary**: `ConnectionKind.normalize` (application layer
  REST and action entry points) is the single canonicalization point. Any
  scattered `equalsIgnoreCase("oceanbase")` branch violates Wave C umbrella §7.3.

## 6. Connection And Persistence

### 6.1 Driver Decision

| Item | Decision | Note |
|---|---|---|
| Driver artifact | `com.oceanbase:oceanbase-client` | License Apache 2.0; Maven Central direct distribution; coexists with `mysql-connector-j` on same classpath without conflict (`acceptsURL()` routes by scheme) |
| Driver version | `2.4.x` series latest stable patch | Child plan Step 0 re-looks up Maven Central current latest stable patch and pins it in `data-talk-infrastructure/pom.xml` and plan §Approval Gate Sanity Check |
| Driver class | `com.oceanbase.jdbc.Driver` | Service Loader auto-registered; Spring Boot fat-jar packaging must ensure `META-INF/services/java.sql.Driver` retains this entry |
| Coexistence with mysql-connector-j | Both registered. `jdbc:oceanbase://` taken by OB driver; `jdbc:mysql://` stays with mysql-connector-j; mutual non-interference via DriverManager `acceptsURL` | Verified in `OceanBaseDriverCoexistenceTest` |

### 6.2 URL Decision

URL template:

```
jdbc:oceanbase://<host>:<port>/<database>
```

- **Default port**: `2881` (direct OBServer connect / OceanBase CE default).
  User can override to `2883` (OBProxy deployment) or any custom port. Frontend
  port input is a single field; **no** "deployment shape" toggle. Driver
  internally negotiates the connection mode.
- **`database` field**: optional. When blank, URL omits the `/<database>`
  suffix and the connection is server-level. When present, URL includes
  `/<database>` and the connection initially binds to that database.
  `databaseName` semantics = MySQL `database` (not schema, not catalog).
- **Username not in URL**: passed via JDBC `Properties.user` or
  `setUsername()`.
- **`tenant` / `cluster` not in URL**: composed into the username field by
  `ConnectionService` (see §6.3). Never smuggled into URL parameters or
  jdbcParams.

### 6.3 Username Composition Rule

OceanBase JDBC requires the username form `<user>@<tenant>` or
`<user>@<tenant>#<cluster>`. Composition happens at exactly one point in the
codebase:

```java
// ConnectionService — private method, single composition site
private String composeOceanBaseUsername(ConnectionRecord c) {
  StringBuilder sb = new StringBuilder(c.username());
  sb.append('@').append(c.oceanbaseTenant());  // tenant required
  if (c.oceanbaseCluster() != null && !c.oceanbaseCluster().isBlank()) {
    sb.append('#').append(c.oceanbaseCluster());
  }
  return sb.toString();
}
```

Rules:

- `ConnectionRecord.username()` always stores the raw user (e.g. `root`),
  never the composed string. Edit-form round-trip displays three independent
  fields (`username`, `oceanbase_tenant`, `oceanbase_cluster`).
- AI prompt, MCP schema, and ER Inspector see structured fields, not the
  composed string.
- Unit test (`OceanBaseUsernameComposeTest`) covers three cases:
  tenant-only, tenant+cluster, non-oceanbase kind (method must not be invoked
  for non-oceanbase kinds).
- Composition is invoked from `ConnectionService.openConnection(...)` only;
  callers downstream of `Connection` see the composed username via JDBC
  metadata, but no other application code depends on it.

### 6.4 ConnectionRecord Fields and Flyway V18

Three new columns on `connection` table, single Flyway file:

```sql
-- V18__multimode_and_oceanbase_fields.sql
-- SQLite cannot ADD CHECK on existing tables. Rebuild required.

CREATE TABLE connection_new (
    -- existing columns preserved verbatim
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    kind TEXT NOT NULL,
    host TEXT NOT NULL,
    port INTEGER NOT NULL,
    database_name TEXT,
    username TEXT,
    password_enc BLOB,
    schema_digest TEXT,
    created_at INTEGER NOT NULL,
    connect_timeout INTEGER NOT NULL DEFAULT 10,
    last_test_status TEXT,
    last_test_at INTEGER,
    oracle_service_type TEXT,
    sqlserver_encrypt INTEGER NOT NULL DEFAULT 1,
    sqlserver_trust_server_certificate INTEGER NOT NULL DEFAULT 1,
    sqlserver_instance_name TEXT,
    duckdb_read_only INTEGER NOT NULL DEFAULT 0,

    -- new columns (Wave C step 3)
    compatibility_mode TEXT NULL,
    oceanbase_tenant TEXT NULL,
    oceanbase_cluster TEXT NULL,

    -- column-level constraints
    CHECK (compatibility_mode IS NULL
       OR compatibility_mode IN ('mysql','oracle','pg')),

    -- oceanbase row constraints
    CHECK (kind <> 'oceanbase' OR oceanbase_tenant IS NOT NULL),
    CHECK (kind <> 'oceanbase' OR compatibility_mode IS NOT NULL)
);

INSERT INTO connection_new (
    id, name, kind, host, port, database_name, username, password_enc,
    schema_digest, created_at, connect_timeout, last_test_status,
    last_test_at, oracle_service_type, sqlserver_encrypt,
    sqlserver_trust_server_certificate, sqlserver_instance_name,
    duckdb_read_only,
    compatibility_mode, oceanbase_tenant, oceanbase_cluster
)
SELECT
    id, name, kind, host, port, database_name, username, password_enc,
    schema_digest, created_at, connect_timeout, last_test_status,
    last_test_at, oracle_service_type, sqlserver_encrypt,
    sqlserver_trust_server_certificate, sqlserver_instance_name,
    duckdb_read_only,
    NULL, NULL, NULL
FROM connection;

DROP TABLE connection;
ALTER TABLE connection_new RENAME TO connection;

-- recreate indexes (verify against current schema before applying)
CREATE INDEX idx_connection_kind ON connection(kind);
CREATE INDEX idx_connection_created_at ON connection(created_at);
```

**Note**: child plan Task 2 verifies the exact DROP/CREATE INDEX list against
the live schema before submission. Migration must be idempotent under
re-run protection (Flyway version uniqueness).

`ConnectionRecord` updated record:

```java
public record ConnectionRecord(
    String id, String name, String kind, String host, int port,
    String databaseName,
    String username, byte[] passwordEnc,
    String schemaDigest, long createdAt, int connectTimeout,
    String lastTestStatus, Long lastTestAt,
    String oracleServiceType,
    int sqlserverEncrypt,
    boolean sqlserverTrustServerCertificate,
    String sqlserverInstanceName,
    boolean readOnly,
    // Wave C step 3 additions
    String compatibilityMode,    // null | "mysql" | "oracle" | "pg"
    String oceanbaseTenant,      // null unless kind='oceanbase'
    String oceanbaseCluster      // optional even for oceanbase
) {}
```

### 6.5 ConnectionKind Enum and Normalization

```java
public enum ConnectionKind {
  MYSQL, POSTGRESQL, /* ... */ OPENGAUSS, OCEANBASE;
}
```

`ConnectionKind.normalize(String input)` rules for OceanBase:

- `oceanbase` → `OCEANBASE` (lower-case canonical accepted as-is)
- `OceanBase`, `OCEANBASE` → lower-cased and accepted
- `oceanbase-ce`, `ob`, `obcluster`, `oceanbase_ce`, `oceanbase-cluster` →
  **rejected** with structured "unknown kind" error guiding user to type
  `oceanbase`
- Chinese names (`蚂蚁 OceanBase` / `沃趣 OceanBase`) → rejected at this
  boundary; AI prompt may recognize them and rewrite to `oceanbase` upstream

## 7. Metadata Discovery

### 7.1 Reuse Path

`ConnectionTargetDiscoveryService` adds `oceanbase` branch routing to the
existing `mysql` discovery path:

```java
// pseudo
case "mysql", "oceanbase" -> mysqlLikeDiscovery(c);
```

Reuse points:

- `SHOW DATABASES` enumerates catalog dimension
- `INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ?` enumerates tables
- `INFORMATION_SCHEMA.COLUMNS` enumerates columns
- `INFORMATION_SCHEMA.STATISTICS` enumerates indexes

### 7.2 System Schema Filter

OceanBase MySQL-mode system schemas (baseline + OB increment):

```java
private static final Set<String> OCEANBASE_SYSTEM_SCHEMAS = Set.of(
    // MySQL baseline (4 items)
    "mysql", "information_schema", "performance_schema", "sys",
    // OceanBase increment (verified at child plan Task 4)
    "oceanbase",       // OB internal metadata
    "LBACSYS",         // OB Label Security
    "SYS"              // Oracle-mode SYS namespace; defensive filter
);
```

**Note**: the exact OB increment list (potential additions like `__public__`,
`gvs_*`, OB 4.x internal views) is verified at child plan Task 4 by running
`SHOW DATABASES` against the T1 fixture (`oceanbase/oceanbase-ce:4.2.1-lts`)
and reconciling against OceanBase official docs. The 7 items listed above are
the minimum baseline.

### 7.3 Schema Field Semantics

- `databaseName` = OceanBase MySQL-mode `database` (equivalent to MySQL
  `database`).
- No "schema" concept introduced into OceanBase MySQL-mode discovery.
- Oracle-mode upgrade (Day-3) splits the schema dimension; not in this spec.

### 7.4 Reuse Inventory

| Reuse point | Source | Test obligation (Wave C §7.3 Reuse-With-Tests) |
|---|---|---|
| `mysqlLikeDiscovery()` body | existing mysql branch | `OceanBaseMetadataReuseIT extends AbstractMySqlMetadataReuseTest` |
| `ConnectionTargetDiscoveryService` switch dispatch | same | covers catalog list / table list / columns / indexes (4 cases) |
| System-schema filter pattern | mysql branch equivalent | OceanBase-specific filter list has its own unit test |

### 7.5 Independent Inventory

| Independent point | Reason |
|---|---|
| OceanBase system-schema filter list | mysql baseline does not cover OB increments |
| `compatibility_mode` entry validation | Discovery entry must reject `oceanbase` row with mode ≠ `mysql` and return `dialect_unsupported`; not present in mysql branch |
| `oceanbase_tenant` / `oceanbase_cluster` reachability check | ConnectionService composes username; metadata discovery indirectly depends on tenant routing; verified via fixture |

## 8. SQL Execution / Splitter / Risk Classifier

### 8.1 SQL Execution Path

`SqlExecuteService` adds `oceanbase` branch fully reusing `mysql` branch:

- Same `JdbcResultValueNormalizer` (verified by `AbstractMySqlResultNormalizationReuseTest`)
- Same connection-level guard / L1-L2-L3 confirmation flow
- Same parameterized statement / `SET autocommit` behavior
- `compatibility_mode` entry validation: ≠`mysql` returns
  `dialect_unsupported` with i18n key `connection.kind.oceanbase.mode_oracle_unsupported_day1`
  and message anchor "OceanBase Oracle-mode is not supported in Day-1; see
  Wave C Day-3 candidate (oceanbase Oracle-mode plan)"

### 8.2 Splitter

```java
case "oceanbase" -> mySqlSplitter;  // single instance shared with mysql / tidb
```

Verified equivalent on OceanBase by
`OceanBaseSplitterReuseIT extends AbstractMySqlSplitterEquivalenceTest`,
covering 6 cases: single statement, `;`-delimited multi-statement,
`DELIMITER` rewrite, string-literal `;` escape, comment-block `;` ignore,
empty-statement filtering.

### 8.3 Risk Classifier — `classifyOceanBaseSpecific()`

`CalciteSqlRiskAnalyzer` adds oceanbase branch: invokes `classifyMySqlBase(sql)`
(shared with mysql/tidb), then invokes `classifyOceanBaseSpecific(sql)`. The
latter's non-null L3 result takes precedence.

**Spec Author Note (per Wave C §10 governance)**: all new patterns **MUST**
use `^\s*` start anchor + `\b` word boundary; `contains()` is **forbidden**.
This rule is verbatim aligned with the ad4c1f0 governance reset incident —
`classifyOceanBaseSpecific` is a from-scratch authoritative implementation.
Any reviewer encountering `contains()` in this method body must reject the
PR before code review.

**6 anchored Pattern constants** (ordered as they will appear in the file):

```java
// CalciteSqlRiskAnalyzer.java — new private static final fields

private static final Pattern OCEANBASE_OUTLINE_DDL = Pattern.compile(
    "^\\s*(ALTER|CREATE|DROP)\\s+OUTLINE\\b",
    Pattern.CASE_INSENSITIVE);

private static final Pattern OCEANBASE_TENANT_DDL = Pattern.compile(
    "^\\s*(ALTER|CREATE|DROP)\\s+TENANT\\b",
    Pattern.CASE_INSENSITIVE);

private static final Pattern OCEANBASE_RESOURCE_DDL = Pattern.compile(
    "^\\s*(ALTER|CREATE|DROP)\\s+RESOURCE\\s+(POOL|UNIT)\\b",
    Pattern.CASE_INSENSITIVE);

private static final Pattern OCEANBASE_ALTER_SYSTEM = Pattern.compile(
    "^\\s*ALTER\\s+SYSTEM\\b",
    Pattern.CASE_INSENSITIVE);

private static final Pattern OCEANBASE_FREEZE = Pattern.compile(
    "^\\s*(MAJOR|MINOR)\\s+FREEZE\\b",
    Pattern.CASE_INSENSITIVE);

private static final Pattern OCEANBASE_BACKUP_RESTORE = Pattern.compile(
    "^\\s*(BACKUP|RESTORE)\\b",
    Pattern.CASE_INSENSITIVE);
```

All 6 patterns hit ⇒ L3. Risk label aggregation: every OceanBase-specific L3
hit is labeled `oceanbase_admin_command` (no sub-label fan-out, avoiding
risk-label explosion). The matched sub-command name is included in the error
message body, not in the structured label.

### 8.4 Risk Boundary Tests (mandatory per pattern)

Each pattern has a paired test set:

**Hit cases** (must classify as L3):
- `ALTER OUTLINE my_outline ON SELECT * FROM t;`
- `CREATE TENANT t1 RESOURCE_POOL_LIST = ('pool1');`
- `DROP RESOURCE POOL pool1;`
- `ALTER SYSTEM SET enable_rebalance = false;`
- `MAJOR FREEZE;`
- `BACKUP DATABASE TO 'oss://...';`

**Boundary cases** (must NOT classify as oceanbase L3 — caught by missing
anchor):
- `UPDATE my_outline_table SET ...` ← does not match `OCEANBASE_OUTLINE_DDL`
- `SELECT * FROM tenant_resource_view` ← does not match `OCEANBASE_TENANT_DDL`
- `INSERT INTO backup_log ...` ← does not match `OCEANBASE_BACKUP_RESTORE`
- `SELECT * FROM major_freeze_history` ← does not match `OCEANBASE_FREEZE`
- `   ALTER OUTLINE foo ...` (leading whitespace) ← matches due to `\s*` anchor
- `EXPLAIN ALTER SYSTEM ...` ← caught by base classifier `EXPLAIN` unwrap
  preprocessing; not a oceanbase-specific concern

### 8.5 Reuse Inventory

| Reuse point | Source | Test obligation |
|---|---|---|
| `MySqlSqlStatementSplitter` | tidb step 1 (commit `273f1e1`) | `OceanBaseSplitterReuseIT extends AbstractMySqlSplitterEquivalenceTest` |
| `JdbcResultValueNormalizer` | tidb step 1 | `OceanBaseNormalizerReuseIT extends AbstractMySqlResultNormalizationReuseTest` |
| `classifyMySqlBase()` | mysql / tidb shared | OceanBase-specific classifier base behavior verified inside `OceanBaseRiskClassifierTest` (oceanbase-private; no shared abstract base — the kit has no risk-base reuse class) |
| L1-L2-L3 confirmation flow | framework | covered by integration tests |

### 8.6 Independent Inventory

| Independent point | Reason |
|---|---|
| `classifyOceanBaseSpecific()` | OceanBase-specific 6 admin / DDL patterns; mysql has no such concept |
| `oceanbase_admin_command` risk label | OceanBase aggregation label |
| `compatibility_mode` entry validation | multi-mode kind specific |

## 9. Diagnostics Provider — 9 hooks Matrix

### 9.1 Provider Class

`OceanBaseDiagnosticsProvider extends AbstractDiagnosticsProvider`, consistent
with the 11-dialect framework landed by Day-2 plan. Path:

```
server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/OceanBaseDiagnosticsProvider.java
```

### 9.2 9-Hook Decision Matrix

| Hook | Day-1 Decision | Implementation Path |
|---|---|---|
| `pool_status` | **partial** | Reuse mysql `INFORMATION_SCHEMA.PROCESSLIST` query; OB increment fields (`gv$ob_processlist.tenant_id`, `user_client_ip`) not returned. i18n key `diagnostics.partial.oceanbase.pool_status`. |
| `lock_info` | **dialect_unsupported** | OB uses `gv$ob_locks` / `gv$ob_transaction`; behavior diverges from mysql baseline; Day-3 upgrade. |
| `table_space` | **supported** | Reuse mysql `INFORMATION_SCHEMA.TABLES.DATA_LENGTH` query; OB 4.x field semantic equivalent. |
| `terminate_session` | **supported** | Reuse mysql `KILL <session_id>`. |
| `optimize_table` | **dialect_unsupported** | OB `ALTER TABLE ... OPTIMIZE PARTITION` syntax 4.x+ only; behavior divergence too large; Day-3 upgrade. |
| `explain_real` | **dialect_unsupported** | OB EXPLAIN JSON field mapping diverges from mysql; Day-3 reuses Day-2 `parseMySqlJsonPlan` framework + `OceanBasePlanFieldAdapter`. |
| `index_hints` | **supported** | Reuse mysql `USE INDEX` / `FORCE INDEX` recommendation path; OB `/*+ INDEX(t idx) */` hint syntax Day-3 increment. |
| `er_inspector` | **dialect_unsupported** | Wave C umbrella §5 Day-1 ER unsupported. |
| `er_designer` | **dialect_unsupported** | Same as above. |

### 9.3 dialect_unsupported Anchor Format

The 5 `dialect_unsupported` hook error messages share a structured anchor:

```
"OceanBase MySQL-mode does not support <hook> in Day-1.
 See Wave C Day-3 candidate (oceanbase mysql-mode upgrade matrix)
 in docs/exec-plans/2026-05-08-diagnostics-day2-plan.md §Day-3 Candidates."
```

i18n keys (consistent with Day-2 plan naming):

- `diagnostics.dialect_unsupported.oceanbase.lock_info`
- `diagnostics.dialect_unsupported.oceanbase.optimize_table`
- `diagnostics.dialect_unsupported.oceanbase.explain_real`
- `diagnostics.dialect_unsupported.oceanbase.er_inspector`
- `diagnostics.dialect_unsupported.oceanbase.er_designer`
- `diagnostics.partial.oceanbase.pool_status`

### 9.4 Reuse Surface

The tidb-shipped `MySqlProtocolReuseRule` kit (commit `273f1e1`) has 6
abstract base classes:

- `AbstractMySqlSplitterEquivalenceTest`
- `AbstractMySqlMetadataReuseTest`
- `AbstractMySqlTargetResolutionReuseTest`
- `AbstractMySqlBatchDmlReuseTest`
- `AbstractMySqlResultNormalizationReuseTest`
- `AbstractMySqlConnectionTestReuseTest`

The kit has **no** `DiagnosticsReuse` abstract base; diagnostics behavior
is kind-specific by design (each kind decides its own supported / partial /
dialect_unsupported matrix). OceanBase diagnostics tests are kind-private:

- 4 supported hooks (`pool_status` partial, `table_space`,
  `terminate_session`, `index_hints`) verified by `OceanBaseDiagnosticsSupportedTest`
- 5 dialect_unsupported hooks verified by `OceanBaseDiagnosticsDialectUnsupportedTest`
  (returns structured outcome with correct i18n key + Day-3 anchor message)

### 9.5 OceanBase-Specific Diagnostics Tests

Beyond reuse:

- `OceanBaseDiagnosticsDialectUnsupportedTest` — verifies 5 dialect_unsupported
  hooks return structured outcome with correct i18n key and anchor message.
- `OceanBaseDiagnosticsPartialTest` — `pool_status` returns mysql-baseline
  fields and i18n partial key.

## 10. Reuse Kit Outputs — `MultiModeConnectionShape`

### 10.1 Abstract Unit and Naming

Per Wave C umbrella §7.2 + §11.4: oceanbase is the first kind producing
`MultiModeConnectionShape`. Naming is **intentionally kind-neutral** —
contains no `OceanBase` token — to enable kingbase reuse.

Path:

```
server/data-talk-application/src/main/java/com/datatalk/application/connection/multimode/MultiModeConnectionShape.java
```

### 10.2 Abstract Unit Sketch (kind-neutral surface)

```java
// MultiModeConnectionShape.java — application layer
public final class MultiModeConnectionShape {

  public enum CompatibilityMode {
    MYSQL("mysql"),
    ORACLE("oracle"),
    PG("pg");

    private final String wireValue;
    CompatibilityMode(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }

    public static CompatibilityMode of(String wire) {
      for (CompatibilityMode m : values()) {
        if (m.wireValue.equals(wire)) return m;
      }
      throw new IllegalArgumentException(
          "Unknown compatibility mode: " + wire);
    }
  }

  /** Validate that (kind, mode) is a legal combination. */
  public static void validateModeForKind(
      String kind, CompatibilityMode mode) {
    switch (kind) {
      case "oceanbase" -> {
        if (mode != CompatibilityMode.MYSQL
            && mode != CompatibilityMode.ORACLE) {
          throw new IllegalArgumentException(
              "oceanbase requires compatibility_mode in {mysql,oracle}");
        }
      }
      // kingbase row added by kingbase child plan
      default -> {
        if (mode != null) {
          throw new IllegalArgumentException(
              "kind '" + kind + "' must not specify compatibility_mode");
        }
      }
    }
  }

  /**
   * Day-1 first-class mode predicate.
   * Returns false ⇒ caller must return dialect_unsupported.
   */
  public static boolean isDay1FirstClassMode(
      String kind, CompatibilityMode mode) {
    return switch (kind) {
      case "oceanbase" -> mode == CompatibilityMode.MYSQL;
      // kingbase row added by kingbase child plan
      default -> mode == null;
    };
  }

  private MultiModeConnectionShape() {}
}
```

### 10.3 Flyway V18 Shared Column

`compatibility_mode` column is landed by oceanbase (§6.4) but visible to
**all kinds**. CHECK constraint guarantees:

- single-mode kinds (mysql/postgresql/oracle/sqlserver/h2/duckdb/...) row
  `compatibility_mode IS NULL`
- oceanbase row `compatibility_mode IN ('mysql','oracle')`
- kingbase row (after kingbase child plan lands) `compatibility_mode IN ('pg','oracle')`

The `('mysql','oracle','pg')` check at column level is the broad outer bound;
`MultiModeConnectionShape.validateModeForKind` enforces the per-kind narrower
bound at application layer.

### 10.4 Frontend Multi-Mode Form Skeleton

Path:

```
client/src/features/settings/data-sources/multi-mode-connection-fields.tsx
```

```tsx
export type CompatibilityMode = "mysql" | "oracle" | "pg";

interface MultiModeFieldsProps {
  kind: ConnectionKind;
  mode: CompatibilityMode | null;
  onModeChange: (mode: CompatibilityMode) => void;
  modeOptions: CompatibilityMode[];   // determined by kind
  modeDisabled: CompatibilityMode[];  // dialect_unsupported modes:
                                       // disabled + tooltip "Day-3 candidate"
}
```

oceanbase invocation in `connection-form-dialog.tsx`:

```tsx
<MultiModeFields
  kind="oceanbase"
  mode={record.compatibilityMode}
  onModeChange={...}
  modeOptions={["mysql", "oracle"]}
  modeDisabled={["oracle"]}   // Day-1 grayed out + tooltip
/>
```

`tenant` and `cluster` fields are **kind-private** to oceanbase and rendered
separately (not part of `MultiModeFields`). They ship in
`oceanbase-connection-fields.tsx`.

### 10.5 ConnectionRecord Field Ownership Boundary

Per umbrella §7.2:

| Field | Ownership | Note |
|---|---|---|
| `compatibilityMode` | `MultiModeConnectionShape` shared | oceanbase + kingbase share; backed by V18 column |
| `oceanbaseTenant` | oceanbase private | **Not** part of `MultiModeConnectionShape`; kind-private column |
| `oceanbaseCluster` | oceanbase private | Same as above |

Future multi-mode kinds adding their private fields (e.g. kingbase potentially
adds `kingbase_compatibility_minor_mode`) follow the same rule: own
`<kind>_*` columns, **never** extend `MultiModeConnectionShape`.

### 10.6 Cross-Kind Reuse Consumption Timing

```
oceanbase ships MultiModeConnectionShape v1
   ├── compatibilityMode field + validateModeForKind + isDay1FirstClassMode
   ├── Flyway V18 lands
   ├── Frontend multi-mode-connection-fields.tsx skeleton
   └── Unit tests cover oceanbase row of validateModeForKind
            │
            ▼  (kingbase plan kickoff hard gate per roadmap §3.2)
kingbase consumes MultiModeConnectionShape v1
   ├── Adds kingbase row to validateModeForKind: pg / oracle legal
   ├── Adds kingbase row to isDay1FirstClassMode: PG-mode first-class
   ├── Reuses frontend multi-mode-connection-fields.tsx
   └── Does NOT modify v1 enum or function signatures (forward-compatible)
```

## 11. Day-2 / Day-3 Upgrade Path — Bidirectional Anchor

### 11.1 Upgrade Matrix

| Upgrade Item | Day-1 Status | Day-2/Day-3 Path | Day-2 Plan §Day-3 Anchor |
|---|---|---|---|
| `lock_info` | `dialect_unsupported` | Implement `OceanBaseDiagnosticsProvider.fetchLockInfo()` via `gv$ob_locks` + `gv$ob_transaction`; columns mapped to Day-2 lockInfo schema (`holder_session` / `blocker_session` / `wait_started_at` / `lock_type`) | `lock_info upgrade` row added |
| `optimize_table` | `dialect_unsupported` | Generate `ALTER TABLE ... OPTIMIZE PARTITION` clause; OB 4.x+ detection enables, lower versions remain unsupported | `optimize_table upgrade (4.x+ only)` row added |
| `explain_real` | `dialect_unsupported` | Reuse Day-2 `parseMySqlJsonPlan` framework + new `OceanBasePlanFieldAdapter` mapping OB EXPLAIN JSON fields (`OPERATOR` / `EST.ROWS` / `EST.TIME(us)` / `OUTPUT & FILTERS`) to Day-2 standard plan fields (`operator` / `rows` / `cost` / `actualRows`) | `explain_real adapter` row added |
| `pool_status` partial → full | partial | Increment returns `gv$ob_processlist.tenant_id` / `user_client_ip` and other OB-specific fields | `pool_status enrich` row added |
| `index_hints` baseline → full | supported (baseline) | Increment recommends OB `/*+ INDEX(t idx) */` hint syntax | `index_hints OB syntax` row added |
| Oracle-mode | dialect_unsupported (whole) | Independent Day-3 child plan: `compatibility_mode='oracle'` first-class; splitter / risk / discovery / diagnostics all Oracle-flavor rewritten; frontend `multi-mode-connection-fields.tsx` unlocks `oracle` | Independent Day-3 plan; not in Day-2 §Day-3 candidate matrix; reserved by wave-c roadmap §3.2 |
| ER Inspector / Designer | `dialect_unsupported` | Bundled with wave-c overall ER upgrade; not in this spec | wave-c umbrella §5 |

### 11.2 Bidirectional Anchor Requirement

- This spec §11 explicitly enumerates 5 Day-2/Day-3 candidate items + 1
  independent Day-3 (Oracle-mode) + 1 wave-c overall (ER).
- `docs/exec-plans/2026-05-08-diagnostics-day2-plan.md` §Day-3 candidate
  section **MUST** backfill the oceanbase row with these 5 items at the time
  the oceanbase child plan ships:
  - `lock_info upgrade`
  - `optimize_table upgrade (4.x+ only)`
  - `explain_real adapter`
  - `pool_status enrich`
  - `index_hints OB syntax`
- The child plan documentation-housekeeping task verifies the bidirectional
  anchor before Definition of Done.

### 11.3 Out of Day-2/Day-3 Scope

- Runtime / `USE` / session-level mode switching (umbrella §7.2 forbidden)
- Oracle-mode (independent Day-3 plan, not in Day-2 §Day-3 upgrade range)
- Multi-tenant cross-tenant metadata / SQL routing
- OBProxy configuration management UI

## 12. Out-of-Scope / Test Fixture / i18n / AGENTS.md Timing

### 12.1 Test Fixture (T1 — Testcontainers)

- Image: `oceanbase/oceanbase-ce:4.2.1-lts` (child plan Step 1 re-checks
  Docker Hub current LTS tag)
- Wait Strategy:
  ```java
  Wait.forLogMessage(".*observer\\s+is\\s+ready.*", 1)
      .withStartupTimeout(Duration.ofSeconds(180))
  ```
- Startup overhead: ~120s cold start; **single-container shared mode**
  (`OceanBaseContainerSupport`) across the 6 IT subclasses; never spin up 6 ×
  120s.
- Default connection params: `compatibilityMode='mysql'`,
  `oceanbase_tenant='sys'`, `oceanbase_cluster=null`, `username='root'`,
  port `2881`.
- Exposed ports: `2881` (MySQL-mode).

### 12.2 6 Concrete IT Subclasses (Cross-Kind Reuse Loop Closure)

These 6 subclasses inherit from the tidb-shipped `MySqlProtocolReuseRule`
abstract bases (commit `273f1e1`), closing the cross-kind reuse loop
`tidb` → `oceanbase`:

| Subclass | Abstract Base (shipped by tidb step 1) | Validates |
|---|---|---|
| `OceanBaseSplitterReuseIT` | `AbstractMySqlSplitterEquivalenceTest` | DELIMITER / multi-statement / comments / string-literal `;` |
| `OceanBaseMetadataReuseIT` | `AbstractMySqlMetadataReuseTest` | catalog/database list / table list / columns / indexes |
| `OceanBaseTargetResolutionReuseIT` | `AbstractMySqlTargetResolutionReuseTest` | database context resolution |
| `OceanBaseBatchDmlReuseIT` | `AbstractMySqlBatchDmlReuseTest` | batch INSERT / UPDATE / DELETE behavior |
| `OceanBaseNormalizerReuseIT` | `AbstractMySqlResultNormalizationReuseTest` | JSON / DECIMAL / BIT / ENUM / SET / YEAR |
| `OceanBaseConnectionReuseIT` | `AbstractMySqlConnectionTestReuseTest` | connection open/close, autocommit, isolation level |

### 12.3 OceanBase-Specific Tests (Not Reused)

- `OceanBaseRiskClassifierTest` — 6 anchored patterns (hit + boundary cases
  per §8.4) and `classifyMySqlBase` integration
- `OceanBaseDiagnosticsSupportedTest` — 4 supported hooks (`pool_status`
  partial, `table_space`, `terminate_session`, `index_hints`)
- `OceanBaseDiagnosticsDialectUnsupportedTest` — 5 dialect_unsupported hooks
  return structured outcome with correct i18n key and Day-3 anchor
- `OceanBaseUsernameComposeTest` — `composeOceanBaseUsername` 3 cases
  (tenant-only / tenant+cluster / non-oceanbase guard)
- `OceanBaseFlywayMigrationTest` — V18 migration + CHECK constraints
  (oceanbase-row tenant required, kind ≠ oceanbase row tenant null,
  compatibility_mode enum range)
- `OceanBaseConnectionRecordValidationTest` — application-layer
  `ConnectionService.create/update` validation
- `OceanBaseMultiModeConnectionShapeTest` — `validateModeForKind` /
  `isDay1FirstClassMode` covering oceanbase rows + non-oceanbase guard
- `OceanBaseDriverCoexistenceTest` — verifies `acceptsURL("jdbc:mysql://...")`
  goes to mysql-connector-j and `acceptsURL("jdbc:oceanbase://...")` goes to
  OceanBase driver, no conflict

### 12.4 i18n Labels and Keys

- Frontend picker label: `OceanBase` (en) / `OceanBase` (zh — brand casing
  retained, not translated)
- i18n keys:
  - `connection.kind.oceanbase.label` = `OceanBase`
  - `connection.kind.oceanbase.tenant_required` (form validation)
  - `connection.kind.oceanbase.tenant_placeholder` (form hint)
  - `connection.kind.oceanbase.cluster_placeholder` (form hint, optional)
  - `connection.kind.oceanbase.mode_oracle_unsupported_day1`
  - `diagnostics.dialect_unsupported.oceanbase.lock_info`
  - `diagnostics.dialect_unsupported.oceanbase.optimize_table`
  - `diagnostics.dialect_unsupported.oceanbase.explain_real`
  - `diagnostics.dialect_unsupported.oceanbase.er_inspector`
  - `diagnostics.dialect_unsupported.oceanbase.er_designer`
  - `diagnostics.partial.oceanbase.pool_status`
- AI prompt rules (post-verify) may include Chinese aliases (`蚂蚁 OceanBase`,
  `沃趣 OceanBase`) for user-typed-request recognition only; they are **not**
  alias targets for `ConnectionKind.normalize` (umbrella §7.5).

### 12.5 AGENTS.md / MCP Timing (Wave C umbrella §7.5)

| Time Point | AGENTS.md Status | MCP `ConnectionObjectType` Enum |
|---|---|---|
| Design approval (this spec lands) | ❌ no oceanbase | ❌ no oceanbase |
| Child plan draft | ❌ | ❌ |
| Child plan implementation in progress | ❌ | ❌ |
| Child plan verify passes (`mvn verify` SUCCESS + 6 IT pass) | ✅ same PR adds oceanbase section | ✅ same PR adds enum |
| Snapshot upgrade to first-class | ✅ live | ✅ live |

### 12.6 Out-of-Scope (12 items explicitly NOT done in this spec)

1. Oracle-mode first-class enablement (independent Day-3 child plan)
2. Multi-tenant cross-tenant metadata / SQL routing
3. OBProxy configuration management UI
4. Tenant / cluster auto-discovery (user must fill)
5. Runtime / `USE` / session-level mode switching (forbidden forever per
   umbrella §7.2)
6. ER Inspector / ER Designer (wave-c umbrella §5 Day-1 unsupported)
7. OB primary-standby replication / backup-restore management UI (admin
   commands still classified L3 by risk classifier; no dedicated UI)
8. OB cluster deploy / scale-out SQL automation (`ALTER SYSTEM ADD ZONE` etc.;
   recognized as L3 only; no dedicated wizard)
9. OB Outline optimizer manual binding UI (risk-recognized but no dedicated UI)
10. `kingbasees` / `gaussdb` / `dameng` and other Wave C kinds' multi-mode
    determination (this spec only locks oceanbase; kingbase has its own spec)
11. OceanBase JDBC driver dynamic version upgrade path (driver pinned;
    upgrades go through independent plan)
12. OceanBase Cloud (OB Cloud / Aliyun managed) connection-form variant —
    Day-1 supports self-hosted / CE only
