# Data Source Coverage: TiDB Design

Date: 2026-05-08
Status: Approved (codex review passed 2026-05-08)
Wave: Wave C, step 1 (per Wave C umbrella §6 recommended order)

## 1. Purpose

TiDB is the first kind in Wave C of Task 9 Data Source Coverage Expansion.
It is selected as step 1 because the driver is zero-new-dependency
(`mysql:mysql-connector-j` already in `data-talk-infrastructure/pom.xml`),
the protocol is MySQL-compatible, an official Docker image
(`pingcap/tidb`) gives a T1 fixture, and it shares its reuse shape with the
already-shipped Wave B `apache_doris`. Step 1 also produces the
`MySqlProtocolReuseRule` cross-kind reuse test kit mandated by
[Wave C umbrella §8](./2026-05-08-data-source-coverage-wave-c-design.md).

This artifact does not expose TiDB as supported. TiDB remains unsupported
until the child implementation plan completes verification and the
Current Support Snapshot in
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)
is updated.

## 2. Compatibility Gate Application

The mandatory gate is
[docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
TiDB implementation must apply every database-type area:

- canonical naming and `ConnectionKind.normalize` boundary;
- backend connection kind, URL builder, driver packaging, connection test,
  metadata, target resolution, SQL execution, result normalization, splitter,
  risk guard, diagnostics, and localized error messages;
- frontend connection form, picker, default port, Query Editor context,
  formatter, outline, accessibility, semantic tokens, and i18n;
- MCP `ConnectionObjectType` enum, runtime `AGENTS.md`, prompt contract
  tests, and tool naming;
- ER Inspector and ER Designer return `dialect_unsupported` (per Wave C
  umbrella §5 Day-1 unsupported set, consistent with Wave B).

No section is N/A: first-class TiDB Day-1 support touches every database
compatibility area listed above.

## 3. Design Inputs

- [docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md](./2026-05-08-data-source-coverage-wave-c-design.md)
  — Wave C umbrella. Locks `MySqlProtocolReuseRule` kit shape, Reuse-With-Tests
  policy (§7.3), AGENTS.md naming policy (§7.5), Day-1 unsupported set (§5),
  and recommended order (§6).
- [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)
  — hard compatibility checklist.
- [client/DESIGN.md](../../client/DESIGN.md) — frontend connection form,
  picker, Query Editor context, and unsupported-state UI must use semantic
  tokens, accessible controls, global Stage state, and i18n keys.
- [docs/product-specs/2026-05-01-data-source-coverage-apache-doris-design.md](./2026-05-01-data-source-coverage-apache-doris-design.md)
  — MySQL-protocol reuse precedent. Connection persistence shape directly
  reused.
- TiDB official documentation (re-checked at child plan kickoff): MySQL
  5.7 / 8.0 wire-protocol compatibility, recommended driver
  `mysql-connector-j`, default port `4000`, `SPLIT TABLE` / `ADMIN` /
  placement rule / `SHOW STATS_*` / optimistic-pessimistic transaction model
  / TiFlash hint syntax / AUTO_RANDOM column type.

External references are design inputs, not implementation approvals.

## 4. Support Statement

Target outcome: first-class SQL Workbench support for TiDB through the
verified MySQL-protocol JDBC path.

At the end of implementation, DataTalk must:

- create, edit, test, select, and delete TiDB connections;
- discover databases and tables through the MySQL-protocol metadata path with
  TiDB-specific equivalence tests;
- resolve `database` context (no `schema` for TiDB);
- execute SELECT / DML / DDL through the existing guarded SQL path with L1 /
  L2 / L3 confirmation;
- split multi-statement scripts (including `DELIMITER`) using the existing
  `MySqlSqlStatementSplitter`, proven equivalent on TiDB by the
  `MySqlProtocolReuseRule` kit;
- classify TiDB-only SQL (`ADMIN`, `SPLIT TABLE`, `BACKUP / RESTORE`, `IMPORT
  INTO`, `LOAD DATA INFILE`, `FLASHBACK`, `RECOVER TABLE`, placement policies,
  `KILL TIDB`, `SHOW STATS_*`) under independent risk rules;
- normalize JDBC return values (AUTO_RANDOM as `BIGINT UNSIGNED`, JSON,
  DECIMAL, BIT, `_BINARY`, ENUM, SET, YEAR) using the existing
  `JdbcResultValueNormalizer` proven equivalent on TiDB;
- return structured `dialect_unsupported` for the seven diagnostics hooks
  (`lock_info`, `pool_status`, `table_space`, `terminate_session`,
  `optimize_table`, EXPLAIN-real, index hints);
- return structured `dialect_unsupported` for ER Inspector and ER Designer;
- expose the `tidb` kind in MCP `ConnectionObjectType` enum, AGENTS.md prompt
  rules, and prompt contract tests without conflating it with `mysql`.

TiDB remains unsupported until those checks pass.

## 5. Kind Naming

- **Canonical kind**: `tidb` (lower-case, single string, no aliases).
- **Aliases**: none. `pingcap-tidb`, `tidbserver`, `tidb-cluster`, and similar
  user-typed variants are not normalized to `tidb`. The error message for
  unknown-kind input guides the user to type `tidb` instead.
- **Persistence**: `ConnectionRecord.kind()`, API payloads, generated
  frontend types, and MCP schema enum all store `tidb`.
- **Frontend label**: `TiDB` (PingCAP brand casing).
- **MySQL relationship**: protocol-level compatibility is a tooling reuse
  premise, not an identity-level alias. `tidb` connections must not be
  persisted or displayed as `mysql` in any code path. Frontend picker must
  render TiDB as a separate row.
- **Normalization boundary**: `ConnectionKind.normalize` (application layer
  REST and action entry points) is the single canonicalization point. Any
  scattered `equalsIgnoreCase("tidb")` branch violates Wave C umbrella §7.3.

## 6. Connection And Persistence

### 6.1 Driver Decision

| Item | Decision | Note |
|---|---|---|
| Driver artifact | **`com.mysql:mysql-connector-j`** — reuse the existing dependency in `data-talk-infrastructure/pom.xml:49-50`, **zero new Maven coordinate** | Version follows the current pom version. The child plan Step 0 re-checks the exact version at implementation start and pins it in the plan. |
| Driver class | `com.mysql.cj.jdbc.Driver` | Identical to the current MySQL path in `DynamicSqlExecutionRepository.java:76`. |
| License | **GPL-2.0 with Universal FOSS Exception**. Note: Wave C umbrella §5 currently lists this driver license as "MIT" — that is a factual error. The correction is shipped as a **separate mini commit against the umbrella spec**, not bundled into the TiDB child plan PR (governance: a child design must not absorb fixes for parent-document factual errors). | Existing project usage is already compliant; zero new license risk. |
| Distribution path | Maven Central direct (already in effect). | T1 fixture; CI requires zero additional driver bootstrap. |
| MariaDB Connector/J alternative evaluation | **Not evaluated**, departing from the Apache Doris design §5 stance. | TiDB official documentation explicitly recommends mysql-connector-j; an extra evaluation creates documentation debt. The child plan §6 records this trade-off in one line. |
| **Minimum server version** | **TiDB 6.5 LTS** — the oldest TiDB release whose `INFORMATION_SCHEMA.KEY_COLUMN_USAGE` and `REFERENTIAL_CONSTRAINTS` are reliably populated, whose `placement` SQL surface is stable, whose `ADMIN SHOW DDL` shape is GA, and whose `mysql-connector-j` 8.x compatibility is documented. The child plan Step 0 re-pins the exact tested image tag (typically `pingcap/tidb:v7.5.x` or later LTS). Older TiDB (≤ 5.x) is **explicitly unsupported** Day-1; connection test does not need to refuse such servers, but functional support and risk-rule coverage assume 6.5+. | Pinning a minimum version protects metadata reuse, FK metadata visibility, and TiDB-only verb correctness. |

### 6.2 Connection Persistence

- **Required fields**: `host`, `port`, `username`, `password`.
- **Optional field**: `database` — `null` means connection-level unbound;
  `USE` and target resolution still work.
- **Default port**: `4000` (TiDB universal default; also the default for
  TiDB Cloud).
- **Persistence**: `ConnectionRecord.databaseName` carries the TiDB database
  name directly (mirror of `mysql`). No new persisted columns. No
  `compatibilityMode`, `tenant`, `txnMode`, `tls*`, or other kind-specific
  fields.
- **DB migration**: **none**. `ConnectionRecord` schema unchanged; only the
  `kind` string accepts a new value.
- **Connection test probe**: reuse the MySQL path
  (`Connection.isValid(2)` + `SELECT 1`), identical to `apache_doris`.

### 6.3 JDBC URL

```text
jdbc:mysql://<host>:<port>/<database>?useSSL=false&allowPublicKeyRetrieval=true
```

When `databaseName == null`:

```text
jdbc:mysql://<host>:<port>/?useSSL=false&allowPublicKeyRetrieval=true
```

- `JdbcUrlBuilder.build(ConnectionRecord)` adds a `case ConnectionKind.TIDB ->`
  branch immediately after the existing `MYSQL` case. URL construction reuses
  the same string-building pattern (no extracted helper — three lines of code,
  matching the apache_doris precedent).
- No `connectTimeout`, `socketTimeout`, `cachePrepStmts`, or other
  mysql-connector-j tuning parameters Day-1. Pool-level configuration
  inherits the global Hikari settings, identical to `mysql` and
  `apache_doris`.
- `useSSL=false` and `allowPublicKeyRetrieval=true` are developer-friendly
  defaults for OSS / self-hosted TiDB **only**. TLS toggling is deliberately
  out of scope (see §11) and will be addressed by a future cross-kind TLS
  design. **Safety boundary**: the connection form must label this kind as
  "TiDB (OSS / 自部署)" so users do not enter TiDB Cloud Serverless or
  Dedicated endpoints (which require TLS) into this URL shape and silently
  fail or transmit credentials in cleartext. The future cross-kind TLS
  design supersedes this boundary by introducing per-connection TLS toggles;
  until then, TiDB Cloud users must wait for that design.

### 6.4 ConnectionKind / JdbcUrlBuilder / ConnectionService

- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`
  adds `public static final String TIDB = "tidb";`.
- `JdbcUrlBuilder.build(ConnectionRecord)` adds the `TIDB` branch after `MYSQL`.
- `ConnectionService.java:139-141` is a **MySQL-protocol generic action**:
  it appends `connectTimeout=<ms>&socketTimeout=<ms>` to the JDBC URL using
  millisecond units, which is the mysql-connector-j parameter convention
  shared by all MySQL-wire-protocol clients (verified against mariadb at
  line 142–144 using a slightly different parameter set, which is why
  `MYSQL` and `MARIADB` are kept as separate `else if` branches today).
  TiDB uses mysql-connector-j and accepts identical parameter names with
  identical semantics, so the child plan **extends the line-139 predicate
  to `kind.equals(ConnectionKind.MYSQL) || kind.equals(ConnectionKind.TIDB)`
  unconditionally** — no judgement call required.
- `ConnectionKind.normalize` does not auto-alias `tidb` (no aliases exist),
  but its presence still anchors the single normalization point per umbrella
  §7.3.

## 7. Catalog, Database, Schema, And Target Resolution

### 7.1 Namespace Mapping

| DataTalk concept | TiDB reality | Decision |
|---|---|---|
| `database` | TiDB database (≈ MySQL database) | Direct mapping; `ConnectionRecord.databaseName` persistence. |
| `schema` | **null** — TiDB has no schema layer separate from database | Day-1 schema is null; frontend Query Editor does not render a schema selector. |
| `catalog` | JDBC `getCatalogs()` returns the database list (mirrors mysql) | Switching via `Connection#setCatalog`; shares target-resolution path with mysql. |
| `tenant` | N/A | Not introduced. |

### 7.2 Target Resolution

- `ConnectionTargetDiscoveryService` line 185 currently checks
  `MYSQL.equals(normalized)`. The child plan extends this to
  `MYSQL.equals(normalized) || TIDB.equals(normalized)`. TiDB MySQL-protocol
  metadata API behavior is identical, but the equivalence is **proven by
  `MySqlProtocolReuseRule` tests**, not assumed.
- `use xxx` resolution: reuse the mysql path. JDBC `USE \`<db>\`` is sent
  directly. `setCatalog` is **not** used (matches mysql to avoid
  mysql-connector-j `setCatalog` implementation drift across versions).
- `list_connection_targets`: `SHOW DATABASES` plus a system-database filter
  list. TiDB system databases to filter:
  `INFORMATION_SCHEMA`, `mysql`, `PERFORMANCE_SCHEMA`, `METRICS_SCHEMA`, `sys`.
  The difference from the mysql filter list (TiDB adds `METRICS_SCHEMA`) is
  recorded in tests.

### 7.3 Schema Discovery

- `ReadSchemaAction.java:280` currently shares a branch among
  `mysql / mariadb / apache_doris / starrocks / hive / trino / presto`. The
  child plan extends this to include `tidb`, routing through the
  MySQL-protocol metadata path.
- `INFORMATION_SCHEMA.KEY_COLUMN_USAGE` and `REFERENTIAL_CONSTRAINTS` are
  fully readable in TiDB (verified for TiDB 6.x and later). TiDB foreign-key
  constraints are syntactic by default unless `foreign_key_checks` and
  `tidb_enable_foreign_key` are enabled — metadata is readable but semantics
  are weak. ER Inspector and Designer still return `dialect_unsupported`
  Day-1 per Wave C umbrella §5.
- Large-schema bounds (`limit`, `cursor`, `pattern`, explicit describe) are
  inherited from the mysql path unchanged.

### 7.4 Identifier Quoting & Case

- Backtick identifiers (`` `db`.`tbl` ``) match mysql; formatter and outline
  reuse the mysql path.
- Case sensitivity: TiDB defaults to `lower_case_table_names=2` (matching
  mysql on macOS / Windows), versus mysql Linux default `0`. This is a
  known divergence and is **not** specialized Day-1; unit tests do not cover
  it. Day-2 may add it if user reports surface.

## 8. SQL Execution, Splitter, Risk Guard, Result Normalization

### 8.1 SQL Execution

- Path: full reuse of the mysql guarded Workbench path —
  `/api/sql/execute`, L2 / L3 confirmation, `SqlBearingActionInspector`,
  `SqlStatementGuard`.
- `SqlExecuteService.java:490` currently shares a batch-DML branch among
  `mysql / mariadb / apache_doris / starrocks / trino`. The child plan
  appends `tidb`. TiDB JDBC-level batch behavior matches mysql-connector-j;
  equivalence is proven by `MySqlProtocolReuseRule` IT.
- `SqlExecuteService.java:572` extension: same shape (mysql-protocol generic
  SQL behavior).
- Context application: `USE \`<db>\``; shares the mysql path.
- Transactions: standard JDBC commit / rollback. TiDB defaults to pessimistic
  transactions since 4.0, surfaced equivalently to mysql InnoDB at the JDBC
  layer. **No** `txn_mode` connection or session-level field exposed; users
  control it through explicit `SET TRANSACTION` SQL.
- Generated keys, multiple result sets, and `setMaxRows` behavior match
  mysql.

### 8.2 Splitter

- **Reuse `MySqlSqlStatementSplitter`** — TiDB fully supports `DELIMITER` for
  stored programs / views / triggers and matches mysql 5.7 / 8.0 splitter
  semantics.
- `DefaultSqlStatementSplitters.java:30` currently routes
  `mysql / mariadb / apache_doris / starrocks` to `mysqlSplitter` via a
  single `if` predicate (constructor injection of `mysqlSplitter` is at
  lines 12 and 17). The child plan extends the line-30 predicate to
  `"mysql".equalsIgnoreCase(...) || "mariadb".equalsIgnoreCase(...) ||
  "apache_doris".equalsIgnoreCase(...) || "starrocks".equalsIgnoreCase(...) ||
  "tidb".equalsIgnoreCase(...)` so `tidb` resolves to the same
  `mysqlSplitter` instance.
- **Reuse-With-Tests** (per umbrella §7.3): a new `TiDbSplitterEquivalenceIT`
  proves the existing splitter behaves correctly on TiDB-flavored input. It
  is a test class only; **no** new splitter class is introduced. Minimum
  language coverage:
  - basic SELECT scripts;
  - `DELIMITER //` custom delimiter;
  - `--`, `#`, and `/* */` comments;
  - single-quoted, double-quoted, and backtick-quoted strings;
  - escape sequences;
  - `/*+ TIDB_INLJ(t1, t2) */` and similar TiDB hint syntax;
  - `SPLIT TABLE ... BETWEEN ... AND ...` (TiDB-only multi-line statement).

### 8.3 Risk Rules

#### 8.3.1 Branch placement

`CalciteSqlRiskAnalyzer` currently has dialect-specific branches for
`sqlite` / `oracle` / `sqlserver` / `duckdb` / `clickhouse` /
`apache_doris` / `starrocks` / `trino` / `presto` / `hive` (lines 166–193,
verified at write time). **`mysql` and `mariadb` have no
dialect-specific branch** — they fall through to the generic Calcite
classification path.

TiDB cannot follow the mysql fallthrough model because TiDB has TiDB-only
verbs (`ADMIN`, `SPLIT TABLE`, `BACKUP`, `FLASHBACK`, `PLACEMENT`, `KILL
TIDB`, `IMPORT INTO`, `RECOVER TABLE`, etc.) that the generic Calcite
classifier does not recognize correctly and that need explicit risk
levels.

**Decision: TiDB takes a dialect-specific branch (`case "tidb"`) modeled
after the StarRocks branch (lines 542–637).** Inside the branch:

1. **Match TiDB-only patterns first** with explicit string-prefix checks
   in the canonical pattern order from §8.3.2 below. These return their
   declared `RiskLevel` directly.
2. **For non-TiDB-only SQL** (SELECT / DML / standard DDL / GRANT / etc.),
   delegate to the same generic Calcite classification path that
   mysql / mariadb use today, by calling the shared `classify(sql, category)`
   helper (or an equivalent — child plan finds the exact API surface).
3. **Unrecognized statements** return `SqlRiskAnalysis.high("tidb_unrecognized")`
   matching the StarRocks pattern (line 637).

This keeps TiDB-only verbs honest while reusing the verified generic
classifier for shared SQL syntax. **Mirroring mysql by fallthrough is
not acceptable** because mysql has no branch to fall through to and
generic Calcite does not recognize TiDB-only verbs.

#### 8.3.2 Rule table

Day-1 rules (TiDB-only patterns enumerated; shared SQL delegates per
8.3.1):

| Statement pattern | RiskLevel | Note |
|---|---|---|
| `SELECT` / CTE / `SHOW` / `DESC` / `DESCRIBE` / `EXPLAIN` / `EXPLAIN ANALYZE` | **L1** | Mirrors mysql. EXPLAIN ANALYZE actually runs the query but is read-only-equivalent, same as mysql. |
| `INSERT` / `UPDATE` / `DELETE` / `REPLACE` | **L2** | Mirrors mysql. `UPDATE` / `DELETE` flow through `SqlBearingActionInspector` for the unified WHERE check. |
| `CREATE TABLE` / `CREATE INDEX` / `CREATE VIEW` / `ANALYZE TABLE` | **L2** | Mirrors mysql. `ANALYZE TABLE` triggers TiDB statistics collection and may be IO-heavy; documented but still L2. |
| `DROP` / `TRUNCATE` / `ALTER` / `RENAME` | **L3** | Mirrors mysql. |
| `GRANT` / `REVOKE` / `CREATE USER` / `DROP USER` / `CREATE ROLE` / `DROP ROLE` | **L3** | Mirrors mysql. |
| `ADMIN CANCEL DDL JOBS` / `ADMIN PAUSE DDL JOBS` / `ADMIN RESUME DDL JOBS` | **TiDB-only L3** | Destructive operations on the DDL job queue. |
| `ADMIN CHECK TABLE` / `ADMIN CHECK INDEX` | **TiDB-only L2** | Read-only consistency check, but heavy IO on large tables. |
| `ADMIN SHOW DDL` / `ADMIN SHOW DDL JOBS` / `ADMIN SHOW DDL JOB QUERIES` | **TiDB-only L1** | Read-only DDL-state introspection; safe to suggest to users via AGENTS.md prompt rules. **Must be matched before** the generic `admin ` catch-all so it does not get classified L3. |
| Any other `ADMIN ...` verb (e.g. `ADMIN RECOVER INDEX`, `ADMIN FLUSH BINDINGS`, `ADMIN RELOAD OPT_RULE_BLACKLIST`, `ADMIN SET BDR ROLE`) | **TiDB-only L3 (conservative catch-all)** | TiDB uses `ADMIN` for cluster-maintenance verbs; default to L3 for any unrecognized `ADMIN` to avoid silently allowing destructive ops. |
| `SPLIT TABLE ... BY/BETWEEN/REGIONS ...` | **TiDB-only L2** | Active region split, cluster-affecting but does not delete data. |
| `BACKUP DATABASE` / `RESTORE DATABASE` / `SHOW BACKUPS` / `SHOW RESTORES` | **TiDB-only L3** | BR-integrated SQL path, external side effects, force L3. |
| `IMPORT INTO ... FROM ...` / `LOAD DATA ... INFILE` | **TiDB-only L3** | File IO plus bulk write, mirroring ClickHouse `LOAD` risk. |
| `FLASHBACK CLUSTER TO TIMESTAMP ...` / `FLASHBACK DATABASE` / `FLASHBACK TABLE` | **TiDB-only L3** | Time-travel restore, cluster-wide effect. |
| `RECOVER TABLE` | **TiDB-only L2** | Recover from GC; bounded write impact. |
| `ALTER PLACEMENT POLICY` / `CREATE PLACEMENT POLICY` / `DROP PLACEMENT POLICY` | **TiDB-only L3** | Cluster-level data-placement policy, scheduling-affecting. |
| `SHOW PLACEMENT` / `SHOW PLACEMENT FOR <object>` / `SHOW PLACEMENT LABELS` | **TiDB-only L1** | Read-only placement introspection; **must be matched before** the L3 `*PLACEMENT POLICY` patterns to avoid misclassification. |
| `KILL TIDB <connectionID>` / `KILL TIDB QUERY <connectionID>` | **TiDB-only L3** | Terminates other sessions; governance-destructive. |
| `SHOW STATS_HEALTHY` / `SHOW STATS_HISTOGRAMS` / `SHOW STATS_META` / `SHOW STATS_BUCKETS` | **L1** | Read-only statistics queries. |
| `SHOW TABLE <name> REGIONS` / `SHOW TABLE REGIONS` / `SHOW SPLIT REGIONS` | **TiDB-only L1** | Read-only region introspection; **must be matched before** the L2 `SPLIT TABLE` pattern to avoid misclassification. |
| `SET GLOBAL <variable> = ...` | **TiDB-only L3** | Cluster-global variable mutation; persistence semantics differ from session-level `SET`. Distinct rule needed because TiDB's set of GLOBAL variables (e.g. `tidb_enable_*`, `tidb_gc_life_time`, `tidb_isolation_read_engines`) directly governs cluster behavior. Session-level `SET <var> = ...` (without `GLOBAL`) remains classified by the generic Calcite path. |
| `BATCH ON <expr> LIMIT <n> INSERT ...` / `BATCH ON ... LIMIT ... UPDATE ...` / `BATCH ON ... LIMIT ... DELETE ...` (TiDB v7.0+) | **TiDB-only L3** | Non-transactional bulk DML. Affected row count is unpredictable and partial failure leaves rows partially mutated; conservative L3 even though session-level `BATCH ON ...` is bounded by `LIMIT`. |
| `ALTER TABLE <t> COMPACT [PARTITION <p>] [TIFLASH REPLICA]` | **TiDB-only L2** | Triggers TiFlash background compaction. IO-heavy but does not delete data; aligns with `ANALYZE TABLE` L2 reasoning. |
| `/*+ READ_FROM_STORAGE(tiflash[t]) */` / `/*+ TIDB_INLJ(...) */` / `/*+ HASH_JOIN(...) */` etc. hints | **No effect on RiskLevel** | Hints are optimizer suggestions; the host statement's RiskLevel applies. |

**Pattern-match ordering**: TiDB-only L1 read patterns (`SHOW PLACEMENT`,
`SHOW TABLE REGIONS`, `SHOW SPLIT REGIONS`, `SHOW STATS_*`) must be matched
**before** the higher-risk patterns that share keyword prefixes (`*PLACEMENT
POLICY`, `SPLIT TABLE`). The child plan implements the branch as a single
ordered cascade of `if (sqlUpper.startsWith(...)) { ... }` checks following
the StarRocks branch (lines 542–637) shape, with L1 reads at the top.

### 8.4 Result Normalization

- Reuse `JdbcResultValueNormalizer` mysql path as the baseline.
- TiDB-only types require explicit equivalence tests:
  - **AUTO_RANDOM column**: JDBC returns `BIGINT UNSIGNED`, identical to
    mysql `BIGINT UNSIGNED` — test only, no code change.
  - **JSON column**: mysql-connector-j returns `String`, identical to mysql
    JSON behavior — test only, no code change.
  - **`DECIMAL` / `BIT` / `_BINARY`**: identical to mysql — test only, no
    code change.
  - **`ENUM` / `SET`**: identical to mysql — test only, no code change.
  - **`YEAR(4)`**: mysql-connector-j returns `Date` or `Short` depending on
    version, consistent with mysql — test only, no code change.
- `JdbcResultValueNormalizerTest` adds a TiDB equivalence test class as part
  of the `MySqlProtocolReuseRule` kit deliverable (see §10).

### 8.5 Diagnostics

- TiDB Day-1 has all seven diagnostics hooks return `dialect_unsupported`
  (Wave C umbrella §5).
- Implementation: a new `TiDbDiagnosticsProvider extends
  AbstractDiagnosticsProvider`, each hook returning
  `DiagnosticResult.unsupported(translator.get("diagnostics.<verb>_unsupported", "tidb"))`.
  Structurally identical to `DorisDiagnosticsProvider`.
- `DiagnosticsService.java:286-325` currently has `case "apache_doris"`
  branches for `lock_not_supported`, `pool_not_supported`,
  `tablespace_not_supported`, `terminate_not_supported`,
  `optimize_not_supported`. Add matching `case "tidb"` branches with the
  same i18n keys.
- Seven existing i18n keys (`diagnostics.lock_not_supported`,
  `diagnostics.pool_not_supported`, `diagnostics.tablespace_not_supported`,
  `diagnostics.terminate_not_supported`, `diagnostics.optimize_not_supported`,
  `diagnostics.explain_unsupported`, `diagnostics.index_hints_unsupported`)
  are reused; **no** new i18n keys are added.
- Day-2 (out of scope): TiDB `EXPLAIN ANALYZE`, `ADMIN SHOW DDL`, and
  Statement Summary tables are high-quality entry points for partial real
  diagnostics.

## 9. Frontend, MCP, And AGENTS.md

### 9.1 Frontend

**Connection form** (`client/src/features/settings/data-sources/connection-form-dialog.tsx`):

- Append `tidb` to the kind dropdown (label `TiDB`, i18n key
  `connections.kind.tidb`).
- Field set: `host`, `port` (default 4000), `username`, `password`,
  `database` (optional). Identical row shape to `mysql` and `apache_doris`.
- **No** TLS / SSL field rendered. TLS is handled by the future cross-kind
  TLS design (out of scope, see §11).
- Default placeholders: host `127.0.0.1`, port `4000`, database empty with
  the help text "(optional, leave empty to use server default)".
- `client/DESIGN.md` semantic tokens: input border `border.default`, focus
  `accent.primary`, error `feedback.error.bg+text`; label `text.primary`;
  password field uses the shared password input. No `tidb`-specific styling.

**Connection picker** (`client/src/features/chat/components/composer/data-source-picker.tsx`
and `client/src/features/stage/components/connection-picker.tsx`):

- Render TiDB as a separate row. **No** merging with mysql.
- Icon: shared generic db icon. **No** dedicated TiDB logo Day-1 (avoids
  brand-asset audit and license review). Day-2 may add a logo.
- **Sort order principle**: protocol-family clustering. The MySQL-protocol
  cluster (mysql / mariadb / apache_doris / starrocks) is the existing
  cluster; TiDB joins this cluster. Within the cluster, the order is:
  reference DB first (`mysql`), then forks/derivatives (`mariadb`, `tidb`),
  then OLAP-on-MySQL-protocol (`apache_doris`, `starrocks`). The PG-protocol
  cluster (`postgresql`, future `opengauss` / `kingbase`) and other
  clusters retain their current relative ordering.
- **Concrete result**: `mysql` → `mariadb` → `tidb` → `apache_doris` → `starrocks` → ...
  (TiDB inserted at position 3, between `mariadb` and `apache_doris`).
- Future Wave C kinds joining the same cluster (`oceanbase` MySQL-mode,
  `opengauss` PG-protocol) follow the same principle and document their
  position in their respective child designs.

**Query Editor toolbar context** (`client/src/features/stage/components/query-editor-toolbar.tsx`):

- Database selector only. **No** schema selector. Existing
  `kind in ['mysql', 'mariadb', 'apache_doris', 'starrocks']` schemaless
  branch extends to include `tidb`.

**Formatter and outline**:

- `client/src/features/stage/utils/format-sql.ts` routes `tidb` to
  `language: 'mysql'`.
- `client/src/features/stage/utils/parse-sql-outline.ts` uses the mysql
  keyword dictionary for `tidb`. TiDB-only keywords (`SPLIT`, `ADMIN`,
  `PLACEMENT`, `FLASHBACK`, `IMPORT`, `BACKUP`, `RESTORE`) are **not** added
  to outline Day-1; Day-2 may add them based on user feedback.

**i18n** (`client/src/i18n/messages.ts`):

- Add zh-CN and en-US entries: `connections.kind.tidb`,
  `connections.tidb.kindLabel`, `connections.tidb.placeholder.host`,
  `connections.tidb.placeholder.port`,
  `connections.tidb.placeholder.databaseOptional`,
  `connections.tidb.help.databaseOptional` (pointing at "leave empty for
  connection-level unbound").
- Diagnostics unsupported strings reuse the existing
  `diagnostics.<verb>_unsupported` keys; **no** new keys added.

**Generated API types**: re-run backend OpenAPI generation. The generated
`client/src/services/api/generated/` types automatically include the `tidb`
literal. The child plan verifies the generation step runs cleanly.

### 9.2 MCP / Adapter

**`ConnectionObjectType.java:39`**:

- Append `"tidb"` to the enum: `List.of("mysql", "postgresql", "sqlite",
  "h2", "mariadb", "oracle", "sqlserver", "duckdb", "clickhouse",
  "apache_doris", "starrocks", "trino", "presto", "hive", "tidb")`.
- Order mirrors the frontend kind list and follows the mysql-protocol
  siblings.

**MCP create / update connection schemas**:

- Field set identical to the mysql kind (host / port / user / password /
  database). **No** kind-conditional required fields are added.
- Connection test action follows the mysql path.

**`datatalk_execute_sql` / `datatalk_read_schema` / `datatalk_resolve_use_target` / `datatalk_list_connection_targets`**:

- Behavior reuses the mysql path. Per §7 / §8, schema resolves to null,
  target resolution shares the mysql path, and SQL execution flows through
  the existing guarded SQL service.
- Prompt contract tests add TiDB cases (minimum set: create database →
  create table → SELECT → DELIMITER multi-statement → ADMIN SHOW DDL
  triggers L3 confirmation → SPLIT TABLE triggers L2).

### 9.3 AGENTS.md

`server/data-talk-adapter/src/main/resources/agents/AGENTS.md` adds a TiDB
section. The section ships in the same PR as the verified implementation,
not before (per Wave C umbrella §7.5):

```md
### TiDB

- Canonical kind: `tidb`. Reject any user attempt to map TiDB to `mysql`.
- Default port: 4000.
- Protocol: MySQL 5.7/8.0 wire-compatible. SQL splitting, formatting, and
  most DML/DDL behave like MySQL, but several statements are TiDB-only and
  require guarded execution.
- TiDB-only L3 (must surface confirmation): `ADMIN CANCEL/PAUSE/RESUME DDL
  JOBS`, `BACKUP DATABASE`, `RESTORE DATABASE`, `IMPORT INTO`, `LOAD DATA
  INFILE`, `FLASHBACK CLUSTER/DATABASE/TABLE`, `ALTER/CREATE/DROP PLACEMENT
  POLICY`, `KILL TIDB`, `SET GLOBAL`, `BATCH ON ... INSERT/UPDATE/DELETE`.
- TiDB-only L2: `SPLIT TABLE ... BETWEEN ... AND ...`, `RECOVER TABLE`,
  `ANALYZE TABLE`, `ALTER TABLE ... COMPACT`.
- TiDB-only L1 read-only introspection (safe to suggest to the user):
  `SHOW PLACEMENT`, `SHOW PLACEMENT FOR ...`, `SHOW PLACEMENT LABELS`,
  `SHOW TABLE <t> REGIONS`, `SHOW SPLIT REGIONS`, `SHOW STATS_HEALTHY`,
  `SHOW STATS_HISTOGRAMS`, `SHOW STATS_META`, `SHOW STATS_BUCKETS`,
  `ADMIN SHOW DDL`. Use these when the user asks about cluster topology,
  region distribution, statistics health, or DDL job state.
- Diagnostics (lock/pool/table_space/EXPLAIN-real/index-hints/terminate/
  optimize) are dialect_unsupported on TiDB Day-1. Suggest the user run
  `EXPLAIN ANALYZE` or the L1 introspection statements above manually in the
  Query Editor when execution plans, region distribution, or statistics are
  needed.
- ER Inspector and Designer are dialect_unsupported on TiDB Day-1.
- User may type "TiDB", "tidb", "PingCAP TiDB". Map to canonical `tidb`
  only. Do not invent aliases.
```

Chinese product names: per Wave C umbrella §7.5, the prompt may mention
`TiDB` as a user-recognition word. It is **not** a schema enum value and
**not** an alias.

## 10. `MySqlProtocolReuseRule` Cross-Kind Reuse Kit

Wave C umbrella §8 mandates the step-1 kind (`tidb`) produce the
`MySqlProtocolReuseRule` kit. The kit lands physically in the tidb child
plan and is consumed by `oceanbase` MySQL-mode (Wave C step 3) and any
future MySQL-protocol kind.

### 10.1 Physical Shape

Location: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/mysqlprotocol/`
(test code; **not** runtime classes).

Naming: `MySqlProtocolReuseRule` is a policy label, not a single class
name. Physically it is a group of subject-organized abstract test base
classes:

```text
mysqlprotocol/
  AbstractMySqlSplitterEquivalenceTest.java       // splitter behavior equivalence
  AbstractMySqlMetadataReuseTest.java             // INFORMATION_SCHEMA equivalence
  AbstractMySqlTargetResolutionReuseTest.java     // USE / SHOW DATABASES / system filter equivalence
  AbstractMySqlBatchDmlReuseTest.java             // batch DML behavior equivalence
  AbstractMySqlResultNormalizationReuseTest.java  // JdbcResultValueNormalizer equivalence
  AbstractMySqlConnectionTestReuseTest.java       // isValid + SELECT 1 equivalence
  package-info.java                               // policy statement
```

Each abstract base exposes
`protected abstract String kindUnderTest()` and
`protected abstract DataSource dataSourceFor(...)`, providing shared
fixtures, shared assertions, and shared corpora.

### 10.2 Subclass Contract

`tidb` provides six concrete subclasses under
`server/data-talk-application/src/test/java/com/datatalk/application/coverage/tidb/`:

- `TiDbSplitterEquivalenceIT extends AbstractMySqlSplitterEquivalenceTest`
- `TiDbMetadataReuseIT extends AbstractMySqlMetadataReuseTest`
- `TiDbTargetResolutionReuseIT extends AbstractMySqlTargetResolutionReuseTest`
- `TiDbBatchDmlReuseIT extends AbstractMySqlBatchDmlReuseTest`
- `TiDbResultNormalizationReuseIT extends AbstractMySqlResultNormalizationReuseTest`
- `TiDbConnectionTestReuseIT extends AbstractMySqlConnectionTestReuseTest`

Subclasses fill exactly two things: fixture source and `kindUnderTest()`
identifier. All real assertions live in the abstract base.

`oceanbase` MySQL-mode (Wave C step 3) **must** provide the same six
concrete subclasses as a child-plan acceptance gate.
`apache_doris` / `starrocks` / `mariadb` should retroactively provide the
same six subclasses; if they do not, the gap is recorded as Wave C
follow-up technical debt and is **not** part of this tidb spec scope.

### 10.3 Fixture Tier

`tidb`: T1 (`pingcap/tidb` Docker via Testcontainers). The child plan runs
all six concrete subclasses on CI.

### 10.4 Relationship To Existing Tests

- `MySqlSqlStatementSplitterTest` remains the mysql self-unit test and is
  **not** moved into the abstract base.
- The abstract bases are additive. From mysql's perspective they add
  supplementary tests; from tidb's perspective they prove equivalence.

### 10.5 Naming Neutrality

- **Forbidden**: `TiDbSplitterTest`, `OceanBaseMetadataTest`, or any
  kind-anchored name inside the abstract-base package.
- Abstract-base names take the form `AbstractMySql*ReuseTest` so future
  consumers (`apache_doris`, `oceanbase`, `starrocks`, `mariadb`) reuse
  without semantic debt.

## 11. Verification, Documentation Sync, And Out-Of-Scope

### 11.1 Verification Matrix

| Layer | Command / path | Pass criterion |
|---|---|---|
| Backend compile | `cd server && mvn compile -q` | 0 compile errors |
| Backend full test suite | `cd server && mvn clean verify` | BUILD SUCCESS; six new `TiDb*ReuseIT` classes green |
| Frontend type check | `cd client && npx tsc --noEmit` | 0 type errors |
| Frontend unit | `cd client && npm test` | 0 failures; new connection-form and picker tests cover `tidb` |
| MCP prompt contract test | adapter module `*PromptContractTest` classes | TiDB cases included: create db → create table → SELECT → DELIMITER multi-statement → ADMIN SHOW DDL triggers L3 → SPLIT TABLE triggers L2 |
| Integration fixture | `pingcap/tidb` via Testcontainers (**T1** per umbrella §7.4) | Six `TiDb*ReuseIT` classes pass |
| Manual smoke (recommended, not gating) | Backend + Tauri client; connect local `pingcap/tidb` Docker | Create connection → SHOW DATABASES → select db → create table → INSERT → SELECT → ADMIN SHOW DDL surfaces confirmation card → SPLIT TABLE surfaces confirmation card |

### 11.2 Test Fixture (T1)

- Docker image: `pingcap/tidb:latest`. The child plan re-checks the current
  LTS tag at kickoff and pins the exact tag in the plan.
- Testcontainers module: `org.testcontainers:testcontainers` already on the
  classpath. TiDB uses `GenericContainer` with port 4000 exposed and a
  health check.
- CI must pull the image. Existing mysql / postgres testcontainers already
  run on the CI runner; TiDB shares the same infrastructure.

### 11.3 Documentation Sync (child plan completion PR)

1. `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`:
   - Current Support Snapshot: append a `tidb` row marked first-class.
   - ER matrix and Feature Compatibility matrix: include `tidb` in the
     `dialect_unsupported` lists.
   - Wave C Child Artifact Tracking: change the `tidb` outcome to
     `Completed YYYY-MM-DD: <one-line summary>`.
2. `docs/exec-plans/index.md`: move
   `2026-05-08-data-source-coverage-tidb-plan.md` from Active to Completed.
3. `docs/product-specs/index.md`: this spec is already registered; no
   further change.
4. `docs/generated/db-schema.md`: N/A — no Flyway migration in this spec.
5. `client/DESIGN.md`: N/A — no new design tokens or interaction patterns.

### 11.4 Approval Process

1. User reviews this spec file.
2. **Codex external review.** Feedback is written back into this spec.
3. Spec is committed and registered in
   [docs/product-specs/index.md](./index.md) §8 (already done at write
   time).
4. Transition to `superpowers:writing-plans` to produce the child plan at
   `docs/exec-plans/2026-05-08-data-source-coverage-tidb-plan.md`,
   registered as Active in
   [docs/exec-plans/index.md](../exec-plans/index.md).
5. Child plan executes → verification matrix passes → housekeeping →
   Tracking outcome written back.

### 11.5 Out Of Scope

- TLS / SSL fields (user-set policy: cross-kind unified support, future
  independent design).
- TiDB Cloud (Serverless / Dedicated) connection form.
- Real diagnostics execution (EXPLAIN ANALYZE, Statement Summary, ADMIN SHOW
  DDL real — Day-2).
- ER Inspector / Designer for TiDB (Day-2; metadata is readable but Wave C
  umbrella §5 locks unsupported Day-1).
- TiDB-only outline keywords (`SPLIT`, `ADMIN`, `PLACEMENT`, `FLASHBACK`,
  `IMPORT`, `BACKUP`, `RESTORE` — Day-2).
- TiDB brand icon (avoids logo asset audit — Day-2).
- Case-sensitivity divergence handling (`lower_case_table_names=2` versus
  mysql defaults — added when user reports surface).
- `txn_mode=optimistic|pessimistic` connection-level field (users use
  `SET TRANSACTION` themselves).
- Backfilling six `MySqlProtocolReuseRule` subclasses for `apache_doris` /
  `starrocks` / `mariadb` (Wave C follow-up technical debt; each handled by
  its own future child plan).
- Refactoring `TiDbDiagnosticsProvider` and `DorisDiagnosticsProvider` (and
  any future fully-unsupported provider) into a shared
  `FullyUnsupportedDiagnosticsProvider` base or factory. This is a real
  duplication concern but Day-1 explicitly prefers structural symmetry
  with the shipped `DorisDiagnosticsProvider` over premature abstraction;
  the consolidation is recorded in
  [docs/exec-plans/tech-debt-tracker.md](../exec-plans/tech-debt-tracker.md)
  by the child plan PR for future refactor work.
