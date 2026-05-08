# Data Source Coverage: openGauss Design

Date: 2026-05-08
Status: Draft (待 user 审阅 → codex external review)
Wave: Wave C, step 2 (per Wave C umbrella §6 recommended order; PG-fork 复用模式验证轮)

## 1. Purpose

openGauss is the second kind in Wave C of Task 9 Data Source Coverage Expansion. It is selected as step 2 because:

- the official driver `org.opengauss:opengauss-jdbc` is on Maven Central (no commercial distribution gate);
- the protocol is a PostgreSQL-fork wire-compatible variant, validating the **"PG-fork reuse with kind-specific tests"** engineering kit;
- a community Docker image (`enmotech/opengauss`) gives a T1 fixture per Wave C §7.4;
- step 2 also produces the **`PgForkReuseRule`** cross-kind reuse test kit mandated by [Wave C umbrella §8](./2026-05-08-data-source-coverage-wave-c-design.md), which is the hard prerequisite for `kingbase` (Wave C step 4).

This artifact does **not** expose openGauss as supported. openGauss remains unsupported until the child implementation plan completes verification and the Current Support Snapshot in [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md) is updated.

## 2. Compatibility Gate Application

The mandatory gate is [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md). openGauss implementation must apply every database-type area:

- canonical naming and `ConnectionKind.normalize` boundary;
- backend connection kind, URL builder, driver packaging, connection test, metadata, target resolution, SQL execution, result normalization, splitter, risk guard, diagnostics, and localized error messages;
- frontend connection form, picker, default port, Query Editor context, formatter, outline, accessibility, semantic tokens, and i18n;
- MCP `ConnectionObjectType` enum, runtime `AGENTS.md`, prompt contract tests, and tool naming;
- ER Inspector and ER Designer return `dialect_unsupported` (per Wave C umbrella §5 Day-1 unsupported set, consistent with `tidb`).

No section is N/A: first-class openGauss Day-1 support touches every database compatibility area listed above.

## 3. Design Inputs

- [docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md](./2026-05-08-data-source-coverage-wave-c-design.md) — Wave C umbrella. Locks `PgForkReuseRule` kit shape (§8), Reuse-With-Tests policy (§7.3), AGENTS.md naming policy (§7.5), Day-1 unsupported set (§5), and recommended order (§6).
- [docs/product-specs/2026-05-08-data-source-coverage-wave-c-roadmap.md](./2026-05-08-data-source-coverage-wave-c-roadmap.md) — Wave C sub-wave roadmap. opengauss readiness gate (§5.1), milestone M1-M4, kingbase blocking dependency.
- [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md) — hard compatibility checklist.
- [client/DESIGN.md](../../client/DESIGN.md) — frontend connection form, picker, Query Editor context, and unsupported-state UI must use semantic tokens, accessible controls, global Stage state, and i18n keys.
- [docs/product-specs/2026-05-08-data-source-coverage-tidb-design.md](./2026-05-08-data-source-coverage-tidb-design.md) — Wave C step 1 child design (PG-fork is to step 2 what MySQL-protocol was to step 1; structural symmetry).
- [docs/exec-plans/2026-05-08-diagnostics-day2-plan.md](../exec-plans/2026-05-08-diagnostics-day2-plan.md) — Diagnostics Day-2 plan §Day-3 Candidate. opengauss Day-2 EXPLAIN/INDEX_HINTS upgrade path is anchored bidirectionally to that document (grammar, fixture tier, permission error map, i18n key naming).
- openGauss official documentation (re-checked at child plan kickoff): driver `org.opengauss:opengauss-jdbc:5.1.0-og`, URL prefix `jdbc:opengauss://`, default port `5432`, MulanPSL2 license, PG 14-base protocol fork, `pgxc_*` cluster catalog, `dbe_*` system packages, column-store table syntax (`WITH (orientation = column)`), `db4ai` schema for AI/ML built-ins.

External references are design inputs, not implementation approvals.

## 4. Support Statement

Target outcome: first-class SQL Workbench support for openGauss through the verified PG-fork JDBC path.

At the end of implementation, DataTalk must:

- create, edit, test, select, and delete openGauss connections;
- discover databases, schemas, and tables through the PG-fork metadata path with openGauss-specific equivalence tests;
- resolve `database` + `schema` two-level context (PG-style; column-store tables surface as `TABLE` type with regular column metadata);
- execute SELECT / DML / DDL through the existing guarded SQL path with L1 / L2 / L3 confirmation;
- split multi-statement scripts (including dollar-quoted strings, PL/pgSQL blocks, `psql` `\` single-line commands stripped before splitter) using the existing `PostgresJdbcSqlStatementSplitter`, proven equivalent on openGauss by the `PgForkReuseRule` kit;
- classify openGauss-only SQL (`DROP / CREATE / ALTER NODE`, `pgxc_*` cluster catalog access on the 7-table whitelist) under independent risk rules with strict anchored regex (correcting the matching-too-wide failure of the rolled-back `ad4c1f0`);
- normalize JDBC return values through the existing PG `JdbcResultValueNormalizer` proven equivalent on openGauss;
- return structured `dialect_unsupported` for the seven diagnostics hooks (`lock_info`, `pool_status`, `table_space`, `terminate_session`, `optimize_table`, EXPLAIN-real, index hints);
- return structured `dialect_unsupported` for ER Inspector and ER Designer;
- expose the `opengauss` kind in MCP `ConnectionObjectType` enum, `AGENTS.md` prompt rules, and prompt contract tests **without** conflating it with `postgresql`.

openGauss remains unsupported until those checks pass.

## 5. Kind Naming

- **Canonical kind**: `opengauss` (lower-case, single string, no aliases).
- **Aliases**: none. `og`, `opengauss-server`, `opengauss-cluster`, and similar user-typed variants are not normalized to `opengauss`. The error message for unknown-kind input guides the user to type `opengauss` instead.
- **Persistence**: `ConnectionRecord.kind()`, API payloads, generated frontend types, and MCP schema enum all store `opengauss`.
- **Frontend label**: `openGauss` (vendor casing — lower-case `o`, capital `G`, lower-case `auss`).
- **PostgreSQL relationship**: protocol-fork compatibility is a tooling reuse premise, **not** an identity-level alias. `opengauss` connections must not be persisted or displayed as `postgresql` in any code path. Frontend picker must render openGauss as a separate row.
- **Normalization boundary**: `ConnectionKind.normalize` (application layer REST and action entry points) is the single canonicalization point. Any scattered `equalsIgnoreCase("opengauss")` branch violates Wave C umbrella §7.3.

## 6. Connection And Persistence

### 6.1 Driver Decision

| Item | Decision | Note |
|---|---|---|
| Driver artifact | **`org.opengauss:opengauss-jdbc:5.1.0-og`** | New Maven coordinate (not in current pom.xml). Child plan Step 0 re-checks the latest 5.1.x patch tag and pins it in the plan. |
| License | MulanPSL2 (Mulan Permissive Software License v2) | OSS, redistribution-friendly, compatible with the existing day2 license stack (MIT / Apache 2.0 / EPL / BSD). No commercial distribution gate. |
| Distribution | Maven Central direct | CI runner pulls without internal mirror or vendor portal. Satisfies Wave C §7.1 distribution-path requirement (path a: Maven Central). |
| Driver kernel match | openGauss 6.0 LTS-aligned | 5.1.0-og is the recommended driver for openGauss 6.0 server kernel. Protocol-layer fork from PG 14. |
| Test scope | `<scope>test</scope>` for IT-only modules; runtime classpath inclusion through `data-talk-infrastructure/pom.xml` | The driver is loaded at runtime via JDBC `DriverManager`; runtime jar is mandatory. Test scope applies only to integration-test artifacts. |

### 6.2 JDBC URL Form

```
jdbc:opengauss://<host>:<port>/<database>
```

- Default port: `5432` (openGauss official default, identical to PG).
- Database segment: required (openGauss does not support empty database in the URL; defaults to `postgres` if user leaves blank in form).
- URL parameters layered by ConnectionService timeout extension (per existing PG branch): `connectTimeout=<seconds>&socketTimeout=<seconds>` (PG protocol uses **seconds**, not milliseconds — same as `postgresql`).
- No `currentSchema` parameter Day-1 (schema selection is enforced at the metadata-discovery layer; URL-level schema pinning is OOS per §12).
- No SSL parameters Day-1 (TLS is OOS per §12, item 1; aligned with tidb child design's TLS exclusion).

**Schema field activation path (clarification for §6.3 form `schema` field):** the `schema` field value is **only** used as the default search-path filter at `read_schema` metadata discovery time. It is **not** written into the JDBC URL (no `currentSchema=`), and **not** executed as `SET search_path TO <schema>` after connection establishment. This matches the current `postgresql` path behavior verbatim. Day-2 may revisit if user reports schema-context drift.

### 6.3 Connection Form Fields

Reuse the existing PostgreSQL connection form component verbatim. `client/src/features/settings/data-sources/connection-form-dialog.tsx` registers:

```ts
DATABASE_TYPES = {
  ...,
  opengauss: { label: 'openGauss', port: 5432 },
  ...
}
```

Form fields (identical to PG):
- `host` (required)
- `port` (required, default 5432)
- `username` (required)
- `password` (required, masked, sealed via `vault.seal`)
- `databaseName` (required, default `postgres` placeholder)
- `schema` (optional, default `public`)
- `connectTimeout` (optional, ms; default from existing PG branch)

No new form components, no new i18n placeholders, no new help text overrides Day-1. Wave C §7.2 compatibilityMode field does **not** apply (openGauss is single-mode PG-fork, no Oracle-mode tenant on the openGauss side).

### 6.4 ConnectionService Timeout Branch

Add a single `else if (kind.equals(ConnectionKind.OPENGAUSS))` branch to `ConnectionService.testConnection`:

```java
} else if (kind.equals(ConnectionKind.OPENGAUSS)) {
    int timeoutSeconds = c.connectTimeout() / 1000;
    url += (url.contains("?") ? "&" : "?")
        + "connectTimeout=" + timeoutSeconds
        + "&socketTimeout=" + timeoutSeconds;
}
```

Identical shape to the existing `POSTGRESQL` branch. **Do not** fold into a shared "PG-family" branch yet; opengauss + kingbase Day-2 may diverge on timeout semantics. Three sibling branches (postgresql / opengauss / kingbase) is acceptable Day-1; consolidate when a third PG-fork kind appears (future wave).

### 6.5 ConnectionKind Constant

Add to `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`:

```java
public static final String OPENGAUSS = "opengauss";
```

Single line addition, no alias map entry.

## 7. Catalog, Database, Schema, And Target Resolution

### 7.1 Two-Level Context (database + schema)

openGauss follows PG semantics:

- **`database`**: the connection-bound database (URL segment). Switching database requires a new connection (no live `\c` equivalent in JDBC). Frontend Query Editor surfaces the database selector at connection level.
- **`schema`**: the search-path scope inside a database. Default `public`. Multi-schema query editor support is consistent with PG.

### 7.2 Database Discovery

`ConnectionTargetDiscoveryService` adds an `opengauss` clause **alongside** the existing `postgresql` clause (not folded together; reuse-with-tests policy):

```java
if (ConnectionKind.POSTGRESQL.equals(connection.kind())
    || ConnectionKind.OPENGAUSS.equals(connection.kind())) {
    try (var stmt = jdbc.createStatement();
         var rs = stmt.executeQuery(
             "SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname")) {
        // collect datnames
    }
}
```

Equivalence proof goes through `AbstractPgForkTargetResolutionReuseTest` (per §10).

### 7.3 System Schema Filter

12-element lower-case `Set<String>`:

```java
private static final Set<String> OPENGAUSS_SYSTEM_SCHEMAS = Set.of(
    // PG standard (4)
    "pg_catalog", "information_schema",
    // pg_temp_* and pg_toast_* are matched by prefix in the existing PG path
    // openGauss increments (8)
    "dbe_pldebugger", "dbe_pldeveloper", "dbe_perf", "db4ai",
    "snapshot", "sqladvisor", "gs_logical_cluster", "oracle"
);
```

Filtering rule: `read_schema` returns 0 rows for any schema in the set (case-insensitive, lowercased before comparison). Existing `pg_temp_*` / `pg_toast_*` prefix matching reused from the PG branch.

The list is stable but not exhaustive. Day-2 user reports of "missing filter" or "over-filter" feed into a future incremental update (recorded in `tech-debt-tracker.md` if persistent).

### 7.4 Column-Store Table Visibility (Day-1 decision)

openGauss column-store tables (`WITH (orientation = column)`) surface to `read_schema` identically to row-store tables — type `TABLE`, columns enumerated through JDBC `DatabaseMetaData.getColumns()`. No `COLUMNAR_TABLE` ontology Day-1.

**Day-1 does not perform orientation detection or marking.** Day-2 INDEX_HINTS upgrade (per §11.2) reads `pg_class.reloptions` to detect `orientation=column` and route column-store tables to the unsupported-with-reason path; the detection lives entirely inside `OpenGaussDiagnosticsProvider.indexHints` and does not propagate to `read_schema` ontology Day-2 either.

Future row-store / column-store distinction in the metadata browser (e.g., a "column-store" badge on the schema tree node) deferred to Day-3 if user demand surfaces.

## 8. SQL Execution, Splitter, Risk Guard, Result Normalization

### 8.1 Splitter Reuse

`DefaultSqlStatementSplitters.split(connectionKind, sql)` adds `opengauss` to the existing PG branch:

```java
if ("postgres".equalsIgnoreCase(connectionKind)
    || "postgresql".equalsIgnoreCase(connectionKind)
    || "opengauss".equalsIgnoreCase(connectionKind)) {
    return postgresSplitter.split(sql);
}
```

**Note on `equalsIgnoreCase` vs `ConnectionKind` constants** (umbrella §7.3 reconciliation): `DefaultSqlStatementSplitters.split` is a **leaf routing function** that accepts an already-normalized `connectionKind` string from upstream call sites (`ConnectionService`, `ReadSchemaAction`, action handlers — all of which apply `ConnectionKind.normalize` at their REST/action entry boundary). The literal-string comparison here mirrors the existing `postgres` / `postgresql` / `mysql` / `mariadb` / `apache_doris` / `starrocks` / `tidb` lines in the same method (commit history confirms). Umbrella §7.3 forbids **scattered alias normalization** (e.g., `equalsIgnoreCase("mssql")` synonymizing to `sqlserver`), not literal-string dispatch in leaf routers. The opengauss line is structurally identical to the existing PG line and inherits the same compliance posture; consolidation into constant-driven routing is recorded in [docs/exec-plans/tech-debt-tracker.md](../exec-plans/tech-debt-tracker.md) as a wave-c-wide follow-up, not Day-1 scope.

Equivalence is **not** asserted by inspection — it is proven by the `AbstractPgForkSplitterEquivalenceTest` (§10) running on openGauss fixtures covering:

- dollar-quoted strings (`$$body$$`, `$tag$body$tag$`);
- PL/pgSQL `DECLARE ... BEGIN ... END;` blocks;
- `psql` `\` single-line commands stripped before reaching splitter (assertion: splitter never sees them, the upstream `ParameterizedSqlExecutor` filter handles `\d`, `\du`, `\dt`);
- multi-statement scripts with mixed PG-fork increments (`pgxc_*` SELECT, column-store DDL).

Standard PG SQL DML/DDL statements split identically by `PostgresJdbcSqlStatementSplitter`; no openGauss-specific splitter exists.

### 8.2 Risk Classifier — `classifyOpengaussSpecific`

> **Spec author note** — there was an earlier exploratory commit `ad4c1f0` that attempted a placeholder `classifyOpengaussSpecific` but mismatched this spec on three counts (used `String.contains` instead of word-boundary regex for `pgxc_*` matching, missed the `ALTER NODE` rule, and used `startsWith` instead of `^` anchor). That commit was reset to `HEAD~1` (current HEAD `996b51c` no longer contains the function). **This §8.2 is therefore the from-scratch implementation specification for the child plan; there is no legacy code to maintain backward compatibility with.** Any deviation found during child plan execution must be fixed against this section, not against `ad4c1f0`'s rolled-back text.

Add to `CalciteSqlRiskAnalyzer`:

```java
if (ConnectionKind.OPENGAUSS.equalsIgnoreCase(connectionKind)) {
    return classifyOpengaussSpecific(sql);
}
```

The classifier covers four anchored patterns. Standard SQL falls through to Calcite generic.

#### 8.2.1 Cluster Node Management (3 anchored patterns)

| Pattern | Risk level | Anchor regex |
|---|---|---|
| `DROP NODE [GROUP]` | L3 high (`opengauss_node_mgmt`) | `^drop\s+node(\s+group)?\b` |
| `CREATE NODE [GROUP]` | L3 high (`opengauss_node_mgmt`) | `^create\s+node(\s+group)?\b` |
| `ALTER NODE [GROUP]` | L3 high (`opengauss_node_mgmt`) | `^alter\s+node(\s+group)?\b` |

Anchor is `^` (after `stripLeadingComments` and `lowerCase(Locale.ROOT)`). Matching only at statement start, not embedded in identifiers like `column DROP NODE_TYPE`. The `\s+` token tolerates multiple whitespace / tab / newline between keywords; this corrects the `startsWith("drop node")` failure mode where `drop  node` (double space) silently bypassed the rule.

**Risk label aggregation** — both `node` and `node group` variants share the single label `opengauss_node_mgmt`. Future granularity (split node-only L3-medium vs node-group-only L3-high, or add separate `opengauss_node_group_mgmt` label) is **deferred to incremental rules** (per §8.2.3); Day-1 single label keeps the i18n / risk-display surface minimal.

#### 8.2.2 PG-XC Cluster Catalog Access (whitelist of 7 tables)

For SELECT / CREATE / ALTER / INSERT / UPDATE / DELETE statements, scan for any of the **7 whitelisted** `pgxc_*` system tables:

```java
private static final List<String> PGXC_CLUSTER_CATALOGS = List.of(
    "pgxc_node", "pgxc_class", "pgxc_group", "pgxc_database",
    "pgxc_shard", "pgxc_namespace", "pgxc_partition"
);
```

Match rule: case-insensitive **word-boundary** anchored regex per table name, e.g. `\bpgxc_node\b`. Matching `pgxc_node` does **not** match `my_pgxc_node_log` (user table). This corrects the rolled-back `ad4c1f0` failure that matched too widely (commit message "tighten pgxc_ risk match" was the post-hoc admission).

If matched: `L3 high (opengauss_pgxc_cluster)`.

#### 8.2.3 Out of Risk Classifier Scope (Day-1)

Standard PG SQL falls through to Calcite generic classifier. The classifier does **not** cover (deferred to Day-2 / future increments):

- `DBE_*` system package CALL (e.g., `DBE_PLDEBUGGER.*`)
- `gs_*` system functions (e.g., `gs_password_complexity`, `gs_basebackup`)
- column-store DDL hints (`WITH (orientation = column)`) — currently L1 fall-through
- `EVENT TRIGGER` system-level
- `db4ai.*` AI/ML calls

Day-2 user-reported gaps drive incremental rules; YAGNI applies Day-1.

### 8.3 Result Normalization

Reuse the existing `JdbcResultValueNormalizer` PG path verbatim. Equivalence proven by `AbstractPgForkResultNormalizationReuseTest` (§10) with openGauss fixtures covering:

- `numeric` / `decimal` precision;
- `bytea` raw bytes;
- `jsonb` / `json`;
- `uuid`;
- `timestamp with time zone`;
- `text[]` / array types;
- column-store table SELECT (verifies normalization is orientation-agnostic).

### 8.4 Batch DML

Reuse PG bulk path: `INSERT INTO table VALUES (...), (...), ...`. No `COPY FROM STDIN` Day-1 (PG path does not use COPY for batch DML; openGauss inherits).

Equivalence proven by `AbstractPgForkBatchDmlReuseTest` (§10).

## 9. Frontend, MCP, And AGENTS.md

### 9.1 Frontend Picker And Form

- `DATABASE_TYPES.opengauss = { label: 'openGauss', port: 5432 }` in `connection-form-dialog.tsx`.
- Connection form reuses the `postgresql` branch verbatim (host, port, username, password, databaseName, schema fields visible).
- Picker renders `openGauss` as a separate row with no logo Day-1 (logo asset audit deferred to Day-2 per §12 OOS).
- SQL formatter language: `format-sql.ts` adds `case 'opengauss': return 'postgresql'` to the language switch (sql-formatter has no openGauss dialect; PostgreSQL is the closest).
- Outline keywords: no openGauss-specific keywords Day-1 (`pgxc_*` access is rare in user editor; full PG keyword set is sufficient).

### 9.2 MCP `ConnectionObjectType`

Add `"opengauss"` to the enum in `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java`. Single-line edit.

### 9.3 `AGENTS.md` Prompt Rule

A new section `### openGauss` is added to `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`. Content (final form):

```
### openGauss

- Canonical kind: `opengauss`. No aliases.
- Default port: 5432.
- Protocol: PostgreSQL wire-fork. SQL splitting and metadata reuse PG path,
  proven equivalent by PgForkReuseRule.
- openGauss-specific L3: `DROP NODE`, `CREATE NODE`, `ALTER NODE` (cluster
  node management); SELECT/CREATE/ALTER on the 7 pgxc_* cluster catalog
  tables (pgxc_node, pgxc_class, pgxc_group, pgxc_database, pgxc_shard,
  pgxc_namespace, pgxc_partition).
- Schema context uses PG-style (database + schema two-level). Column-store
  tables (WITH orientation=column) surface as TABLE type.
- Diagnostics are dialect_unsupported on openGauss Day-1.
- ER Inspector and Designer are dialect_unsupported on openGauss Day-1.
- User may type "openGauss", "opengauss". Map to canonical `opengauss` only.
```

**Note on AI fuzzy mapping vs `ConnectionKind.normalize` strict behavior**: the last bullet ("User may type ... Map to canonical `opengauss` only") is a **prompt-layer instruction** to the runtime AI agent for case-folding `openGauss` ↔ `opengauss` when interpreting natural-language user requests. It does **not** modify `ConnectionKind.normalize(String)`'s strict behavior — strings like `og`, `opengauss-server`, `opengauss-cluster` are **not** normalized to `opengauss` at the application boundary (per §5 "no aliases" hard rule). The AI prompt and the application normalize layer are two distinct surfaces with distinct responsibilities; this section's content lives only in the prompt.

**Wave C §7.5 timing constraint**: this `AGENTS.md` section is **part of the child plan PR**, not this design's PR. The runtime prompt must not advertise openGauss support until the child plan verification matrix passes. Spec writes the rule content; child plan ships the file edit.

### 9.4 Prompt Contract Tests

`AgentPromptContractTest` adds openGauss assertions:

- canonical kind appears once;
- alias-claim guard (no `og`, `opengauss-server` etc.);
- 4 risk-rule examples covered (3 NODE patterns + pgxc_ access);
- diagnostics dialect_unsupported language asserted;
- ER dialect_unsupported language asserted.

## 10. `PgForkReuseRule` Cross-Kind Reuse Kit

Wave C umbrella §8 mandates the step-2 kind (`opengauss`) produce the `PgForkReuseRule` kit. The kit lands physically in the opengauss child plan and is consumed by `kingbase` PG-mode (Wave C step 4) and any future PG-fork kind.

### 10.1 Physical Shape

Location: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/` (test code; **not** runtime classes).

Naming: `PgForkReuseRule` is a policy label, not a single class name. Physically it is a group of subject-organized abstract test base classes:

```text
pgfork/
  AbstractPgForkSplitterEquivalenceTest.java       // splitter behavior equivalence
  AbstractPgForkMetadataReuseTest.java             // pg_catalog / information_schema equivalence
  AbstractPgForkTargetResolutionReuseTest.java     // pg_database / search_path / system schema filter equivalence
  AbstractPgForkBatchDmlReuseTest.java             // batch DML behavior equivalence
  AbstractPgForkResultNormalizationReuseTest.java  // JdbcResultValueNormalizer equivalence
  AbstractPgForkConnectionTestReuseTest.java       // isValid + SELECT 1 equivalence
  package-info.java                                // policy statement
```

Each abstract base exposes `protected abstract String kindUnderTest()` and `protected abstract DataSource dataSourceFor(...)`, providing shared fixtures, shared assertions, and shared corpora.

### 10.2 Subclass Contract

`opengauss` provides six concrete subclasses under `server/data-talk-application/src/test/java/com/datatalk/application/coverage/opengauss/`:

- `OpenGaussSplitterEquivalenceIT extends AbstractPgForkSplitterEquivalenceTest`
- `OpenGaussMetadataReuseIT extends AbstractPgForkMetadataReuseTest`
- `OpenGaussTargetResolutionReuseIT extends AbstractPgForkTargetResolutionReuseTest`
- `OpenGaussBatchDmlReuseIT extends AbstractPgForkBatchDmlReuseTest`
- `OpenGaussResultNormalizationReuseIT extends AbstractPgForkResultNormalizationReuseTest`
- `OpenGaussConnectionTestReuseIT extends AbstractPgForkConnectionTestReuseTest`

Subclasses fill exactly two things: fixture source and `kindUnderTest()` identifier. All real assertions live in the abstract base.

`kingbase` PG-mode (Wave C step 4) **must** provide the same six concrete subclasses as a child-plan acceptance gate. `postgresql` retroactively providing the same six subclasses is recorded as Wave C follow-up technical debt and is **not** part of this opengauss spec scope.

### 10.3 Fixture Tier

`opengauss`: T1 (`enmotech/opengauss:6.0.0` Docker via Testcontainers). The child plan runs all six concrete subclasses on CI.

### 10.4 Relationship To Existing Tests

- `PostgresJdbcSqlStatementSplitterTest` remains the postgresql self-unit test and is **not** moved into the abstract base.
- The abstract bases are additive. From postgresql's perspective they add supplementary tests; from opengauss's perspective they prove equivalence.

### 10.5 Naming Neutrality

- **Forbidden**: `OpenGaussSplitterTest`, `KingbaseMetadataTest`, or any kind-anchored name inside the abstract-base package.
- Abstract-base names take the form `AbstractPgFork*ReuseTest` so future consumers (`kingbase`, `postgresql` retroactively) reuse without semantic debt.
- Symmetric to step 1: `MySqlProtocolReuseRule` ↔ `PgForkReuseRule`. Future Wave C kinds invent neither new policy labels nor new abstract-base prefixes.

## 11. Day-2 EXPLAIN / INDEX_HINTS Upgrade Path

This section is the bidirectional anchor with [docs/exec-plans/2026-05-08-diagnostics-day2-plan.md](../exec-plans/2026-05-08-diagnostics-day2-plan.md) §Day-3 Candidate. Day-1 ships dialect_unsupported (per §4); Day-2 upgrade specs the path.

### 11.1 EXPLAIN Real Implementation Plan (Day-2)

| Item | Decision |
|---|---|
| EXPLAIN SQL | `EXPLAIN (FORMAT JSON) <user_sql>` (does not execute, no `ANALYZE`) |
| Output format | JSON tree |
| Grammar | **Newly-built `PostgresJsonPlanParser`** in `AbstractDiagnosticsProvider` (or sibling helper) — deserializes PG-fork JSON plan to `ExplainNode` tree |
| Reuse for kingbase | `PostgresJsonPlanParser` is **shared**, not opengauss-specific. kingbase Day-2 consumes the same parser. Symmetric to `parseMySqlJsonPlan` (already shipped Day-2 base helper). |
| ScanType mapping | `Seq Scan` → `FULL_SCAN`; `Index Scan` / `Bitmap Heap Scan` → `INDEX_SCAN`; `Index Only Scan` → `INDEX_RANGE`; `Hash Join` / `Nested Loop` / `Merge Join` → `OTHER` |
| Permission error map | PG-style "permission denied for relation X" → `mapPermissionOrDriverError` recognizes `42501` SQLState; i18n key `diagnostics.explain.unsupported.opengauss_permission` |
| Fixture | T1 (`enmotech/opengauss:6.0.0`) — same as Day-1 |

### 11.2 INDEX_HINTS Real Implementation Plan (Day-2)

| Table type | Day-2 behavior | i18n key |
|---|---|---|
| Row-store table (default `WITH (orientation = row)`) | Recommend `BTREE` index for `FULL_SCAN` columns extracted by `SqlColumnExtractor` (reuse from sqlite/sqlserver/mariadb/tidb Day-2) | (existing) `diagnostics.index_hints.btree_recommendation` |
| Column-store table (`WITH (orientation = column)`) | Unsupported with reason: "openGauss column-store tables use zone map optimization and do not benefit from B-tree secondary indexes; check zone map hits in EXPLAIN PLAN" | New `diagnostics.index_hints.unsupported.opengauss_columnstore` |

Detection of orientation: read `pg_class.reloptions` for `orientation=column`. If column-store, return unsupported result; otherwise proceed to BTREE recommendation.

### 11.3 Other Diagnostics Capabilities (Day-2 / Day-3)

LOCK_INFO, POOL_STATUS, TABLE_SPACE, TERMINATE_SESSION, OPTIMIZE_TABLE remain `dialect_unsupported` Day-1 and Day-2. Day-3 may add real implementation following the same `mapPermissionOrDriverError` + per-kind reason pattern.

### 11.4 Day-2 Upgrade Trigger Conditions

Per day2 plan §Day-3 Candidate "升级触发条件":

1. opengauss child plan verified (this design's child plan ships first).
2. fixture reachable in CI (T1 already verified).
3. driver reachable in CI (Maven Central direct).

When all three are met, a separate `diagnostics-day2.5-opengauss-plan.md` (or `diagnostics-day3-wave-c-plan.md` covering all four wave-c kinds together) opens. **This opengauss design does not implement Day-2 EXPLAIN/INDEX_HINTS code** — it specifies the path so kingbase's Day-2 plan can build on it.

## 12. Verification, Documentation Sync, And Out-Of-Scope

### 12.1 Verification Matrix

| Layer | Command / path | Pass criterion |
|---|---|---|
| Backend compile | `cd server && mvn compile -q` | 0 compile errors |
| Backend full test suite | `cd server && mvn clean verify` | BUILD SUCCESS; six new `OpenGauss*ReuseIT` classes green |
| Frontend type check | `cd client && npx tsc --noEmit` | 0 type errors |
| Frontend unit | `cd client && npm test` | 0 failures; new connection-form and picker tests cover `opengauss` |
| MCP prompt contract test | adapter module `*PromptContractTest` classes | openGauss cases included: create db → create schema → create table → SELECT → DROP NODE triggers L3 → SELECT FROM pgxc_node triggers L3 → SELECT FROM my_pgxc_log does **not** trigger L3 (anchor verification) |
| Integration fixture | `enmotech/opengauss:6.0.0` via Testcontainers (**T1** per umbrella §7.4) | Six `OpenGauss*ReuseIT` classes pass |
| Manual smoke (recommended, not gating) | Backend + Tauri client; connect local `enmotech/opengauss:6.0.0` Docker | Create connection → SHOW DATABASES → select db → create schema → create row-store table → INSERT → SELECT → create column-store table (`WITH (orientation = column)`) → SELECT → `DROP NODE pgxc_dn1` surfaces L3 confirmation card → `SELECT * FROM pgxc_node` surfaces L3 confirmation card |

### 12.2 Test Fixture (T1)

- Docker image: `enmotech/opengauss:6.0.0`. The child plan re-checks the exact tag at kickoff and pins it in the plan; `latest` is forbidden per umbrella §7.4.
- Testcontainers module: `org.testcontainers:testcontainers` already on the classpath (used by tidb / postgresql / mysql IT). openGauss uses `GenericContainer` with port 5432 exposed.
- **Health check (mandatory; openGauss container startup is ~30s, longer than mysql/postgres)**: `Wait.forLogMessage(".*database system is ready to accept connections.*", 1).withStartupTimeout(Duration.ofSeconds(120))` as primary; fallback `Wait.forSuccessfulCommand("gs_isready -h 127.0.0.1 -p 5432 -U gaussdb")` if the binary is in the image PATH (the child plan verifies which one works against `enmotech/opengauss:6.0.0` at kickoff and pins exactly one). **Do not rely on raw socket connect** — openGauss accepts TCP before login is ready, causing flaky CI.
- Username / password / initial database created via Testcontainers env vars (`GS_USERNAME`, `GS_PASSWORD`, `GS_DB`); the child plan documents the exact env-var contract at kickoff.
- CI must pull the image. Existing testcontainers infra runs on the CI runner; openGauss shares the same.

### 12.3 Documentation Sync (child plan completion PR)

1. `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`:
   - Current Support Snapshot: append an `opengauss` row marked first-class.
   - ER matrix and Feature Compatibility matrix: include `opengauss` in the `dialect_unsupported` lists.
   - Wave C Child Artifact Tracking: change the `opengauss` outcome to `Completed YYYY-MM-DD: <one-line summary>`.
2. `docs/exec-plans/index.md`: move `2026-05-08-data-source-coverage-opengauss-plan.md` from Active to Completed.
3. `docs/product-specs/index.md`: this spec is registered at write time; no further change.
4. `docs/generated/db-schema.md`: N/A — no Flyway migration in this spec.
5. `client/DESIGN.md`: N/A — no new design tokens or interaction patterns (PG form reuse only).
6. `docs/product-specs/2026-05-08-data-source-coverage-wave-c-roadmap.md` §5.1: update opengauss readiness gate states from `⏳ 待启动` to `✅` row by row as each gate passes.

### 12.4 Approval Process

1. User reviews this spec file.
2. **Codex external review.** Feedback is written back into this spec.
3. Spec is committed and registered in [docs/product-specs/index.md](./index.md) §8 (already done at write time).
4. Transition to `superpowers:writing-plans` to produce the child plan at `docs/exec-plans/2026-05-08-data-source-coverage-opengauss-plan.md`, registered as Active in [docs/exec-plans/index.md](../exec-plans/index.md).
5. Child plan executes → verification matrix passes → housekeeping → Tracking outcome written back.

### 12.5 Out Of Scope

1. **TLS / SSL fields** (cross-kind unified support, future independent design; aligned with tidb child design item 1).
2. **openGauss DCS / CM cluster topology awareness** (DataTalk does not introspect cluster routing; user-visible nodes are connection-level only).
3. **Column-store specific DDL/DML optimization** — `INSERT OVERWRITE`, `ALTER TABLE ... ADD COLUMN ... USING column`, column-store-specific compression options (Day-2 / Day-3).
4. **`db4ai.*` AI/ML built-in active optimization** — user can execute calls but DataTalk does not optimize, recommend, or surface dedicated UI.
5. **Physical backup / disaster recovery / streaming replication** operations (`gs_basebackup`, `gs_restore`, replication slot manipulation; Day-3 if scoped at all).
6. **Oracle compatibility package `oracle.*` proactive surfacing** (already in §7.3 system schema filter — schema is hidden from `read_schema`; user can SELECT FROM `oracle.dual` etc. but it's filtered from metadata browser).
7. **Huawei Cloud GaussDB for openGauss managed-service integration** — different product line, owned by `gaussdb` child cycle (Wave C step 6) per umbrella §6.
8. **Online elastic scaling (DCC / OM operations)** — operational tooling, out of SQL Workbench scope.
9. **Column-store query zone map / materialized view smart selection** — Day-3 query plan annotation, depends on Day-2 EXPLAIN landing first.
10. **openGauss-only outline keywords** (`pgxc_*`, `dbe_*`, `gs_*` in editor outline) — Day-2; the full PG keyword set covers ~95% of user editor surface Day-1.
11. **openGauss brand icon** (avoids logo asset audit — Day-2; aligned with tidb).
12. **Refactoring `OpenGaussDiagnosticsProvider` and `TiDbDiagnosticsProvider` and `DorisDiagnosticsProvider` (any fully-unsupported provider) into a shared base** — real duplication concern, but Day-1 explicitly prefers structural symmetry over premature abstraction; consolidation is recorded in [docs/exec-plans/tech-debt-tracker.md](../exec-plans/tech-debt-tracker.md) by the child plan PR for future refactor work.
