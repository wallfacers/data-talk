# Data Source Coverage: openGauss Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add verified first-class openGauss support as canonical kind `opengauss` (no aliases), plus produce the `PgForkReuseRule` cross-kind reuse test kit mandated by Wave C umbrella §8 (kingbase Wave C step 4 hard prerequisite).

**Architecture:** openGauss is a PostgreSQL-fork wire-protocol kind. Backend reuses `PostgresJdbcSqlStatementSplitter`, PG metadata path, PG batch DML path, and `JdbcResultValueNormalizer` PG baseline — but every reuse point is proven by an openGauss-specific concrete subclass of a `PgForkReuseRule` abstract base, so future `kingbase` PG-mode reuse has the same scaffolding. openGauss-only verbs (`DROP/CREATE/ALTER NODE [GROUP]`, 7 whitelisted `pgxc_*` cluster catalog tables) get an independent `classifyOpengaussSpecific(sql)` branch in `CalciteSqlRiskAnalyzer` with strict `^` anchor and `\b` word-boundary regex (correcting the rolled-back `ad4c1f0` "matching too wide" failure). Diagnostics and ER are structured `dialect_unsupported` Day-1.

**Tech Stack:** Java 21, Spring Boot 3.5, `org.opengauss:opengauss-jdbc:5.1.0-og` (new Maven dependency, MulanPSL2, Maven Central direct), Testcontainers (`enmotech/opengauss:6.0.0` T1 fixture), JUnit 5, AssertJ, React 19, TypeScript, Vitest.

---

## Design Inputs

- [docs/product-specs/2026-05-08-data-source-coverage-opengauss-design.md](../product-specs/2026-05-08-data-source-coverage-opengauss-design.md) — spec being implemented. **Status: Draft (待 codex external review)**. Implementer flips status to `Approved` as Task 1 Step 5.
- [docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md](../product-specs/2026-05-08-data-source-coverage-wave-c-design.md) — Wave C umbrella; locks `PgForkReuseRule` kit shape (§8), Reuse-With-Tests policy (§7.3), AGENTS.md timing (§7.5), Day-1 unsupported set (§5).
- [docs/product-specs/2026-05-08-data-source-coverage-wave-c-roadmap.md](../product-specs/2026-05-08-data-source-coverage-wave-c-roadmap.md) — Wave C sub-wave roadmap §5.1 readiness gate (kingbase blocking dependency on this plan's PgForkReuseRule).
- [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md) — hard compatibility checklist.
- [client/DESIGN.md](../../client/DESIGN.md) — frontend semantic tokens, accessible controls, Stage state, i18n.
- [docs/exec-plans/2026-05-08-data-source-coverage-tidb-plan.md](./2026-05-08-data-source-coverage-tidb-plan.md) — Wave C step 1 child plan precedent. Task structure / subagent flow / cross-kind reuse kit kit shape directly mirrored.
- [docs/exec-plans/2026-05-08-diagnostics-day2-plan.md](./2026-05-08-diagnostics-day2-plan.md) §Day-3 Candidate — bidirectional anchor for §11 of opengauss design (Day-2 EXPLAIN/INDEX_HINTS upgrade path, **out of this plan's scope**).

## Files

**Backend — new:**
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/OpenGaussDiagnosticsProvider.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/AbstractPgForkSplitterEquivalenceTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/AbstractPgForkMetadataReuseTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/AbstractPgForkTargetResolutionReuseTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/AbstractPgForkBatchDmlReuseTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/AbstractPgForkResultNormalizationReuseTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/AbstractPgForkConnectionTestReuseTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/package-info.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/opengauss/OpenGaussContainerSupport.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/opengauss/OpenGaussSplitterEquivalenceIT.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/opengauss/OpenGaussMetadataReuseIT.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/opengauss/OpenGaussTargetResolutionReuseIT.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/opengauss/OpenGaussBatchDmlReuseIT.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/opengauss/OpenGaussResultNormalizationReuseIT.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/opengauss/OpenGaussConnectionTestReuseIT.java`
- `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/OpenGaussDiagnosticsProviderTest.java`

**Backend — modify:**
- `server/data-talk-infrastructure/pom.xml` (add `org.opengauss:opengauss-jdbc:5.1.0-og` runtime dependency)
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java:15,112`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java:148`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java:52`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/DiagnosticsService.java`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java:27`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java`
- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- Existing test files: `JdbcUrlBuilderTest.java`, `CalciteSqlRiskAnalyzerTest.java`, `DefaultSqlStatementSplittersTest.java`, `DiagnosticsServiceTest.java`

**Frontend — modify:**
- `client/src/features/settings/data-sources/connection-form-dialog.tsx`
- `client/src/features/chat/components/composer/data-source-picker.tsx` (if list-of-supported-kinds is enumerated)
- `client/src/features/stage/components/connection-picker.tsx` (if list-of-supported-kinds is enumerated)
- `client/src/features/stage/utils/format-sql.ts`
- `client/src/i18n/messages.ts`

**Docs:**
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` (Current Support Snapshot, ER matrix, Wave C Tracking)
- `docs/exec-plans/index.md` (Active → Completed)
- `docs/product-specs/2026-05-08-data-source-coverage-wave-c-roadmap.md` §5.1 (opengauss readiness gate ⏳ → ✅)

---

### Task 1: Approval, Gate, And Kickoff Decisions

Single-purpose: confirm the spec is approved, re-read mandatory gates, pin driver/fixture versions, and flip the spec status.

- [ ] **Step 1: Confirm spec approval status**

Run:

```bash
grep -n "^Status:" docs/product-specs/2026-05-08-data-source-coverage-opengauss-design.md
```

Expected: `Status: Draft (待 user 审阅 → codex external review)`. Implementer waits for codex external review and user sign-off before flipping to `Approved` in Step 5.

- [ ] **Step 2: Re-read mandatory gates**

Read in full:
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` — every `_TYPE_COMPATIBILITY` checklist row applies (no N/A).
- `client/DESIGN.md` — connection form / picker / formatter / i18n constraints.
- `docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md` — umbrella §5 / §7 / §8.
- `docs/product-specs/2026-05-08-data-source-coverage-opengauss-design.md` — this child design, full.
- `docs/product-specs/2026-05-08-data-source-coverage-wave-c-roadmap.md` §5.1 — opengauss readiness gate.
- `docs/bugs/index.md` — verify zero open BUGs touching `postgresql` / connection / risk analyzer / diagnostics modules. If any open BUG overlaps, declare risk in this plan's Risks section and obtain user sign-off before proceeding.

- [ ] **Step 3: Pin driver version**

Browse [Maven Central org.opengauss:opengauss-jdbc](https://central.sonatype.com/artifact/org.opengauss/opengauss-jdbc) and verify `5.1.0-og` is the latest stable 5.1.x patch. If a newer 5.1.x exists (e.g., `5.1.1-og`), pin that. Record exact version in Task 2 Step 1's pom edit and in the commit body.

License sanity check:

```bash
curl -s https://repo1.maven.org/maven2/org/opengauss/opengauss-jdbc/5.1.0-og/opengauss-jdbc-5.1.0-og.pom | grep -A 1 "<licenses>"
```

Expected: includes `<name>MulanPSL2</name>` (or `Mulan Permissive Software License v2`). Confirm OSS-compatible. If license has changed since spec write time, escalate to user before continuing.

- [ ] **Step 4: Pin openGauss Testcontainers image tag**

Run:

```bash
curl -s "https://hub.docker.com/v2/repositories/enmotech/opengauss/tags?page_size=100" | python3 -c "import sys, json; tags=[t['name'] for t in json.load(sys.stdin).get('results', [])]; print('\n'.join(t for t in tags if t.replace('.','').replace('v','').isdigit()))" | sort -V | tail -10
```

Expected: confirms `6.0.0` exists. If a newer LTS 6.x.x is available (e.g., `6.0.1`), pin that. Record chosen tag in `OpenGaussContainerSupport.java` (Task 8) as a `public static final String` constant. **`:latest` is forbidden per umbrella §7.4.**

- [ ] **Step 5: Flip spec status and commit**

Edit `docs/product-specs/2026-05-08-data-source-coverage-opengauss-design.md` line 4:

From: `Status: Draft (待 user 审阅 → codex external review)`
To: `Status: Approved (codex review passed YYYY-MM-DD)` — fill the actual codex review date.

```bash
git add docs/product-specs/2026-05-08-data-source-coverage-opengauss-design.md
git commit -m "docs(opengauss): flip spec status to Approved after codex review"
```

---

### Task 2: ConnectionKind, JdbcUrlBuilder, ConnectionService, pom Driver

**Files:**
- Modify: `server/data-talk-infrastructure/pom.xml` (add opengauss-jdbc dependency)
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java:15,112`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java:148`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/connection/JdbcUrlBuilderTest.java`

- [ ] **Step 1: Add driver dependency to pom.xml**

In `server/data-talk-infrastructure/pom.xml`, locate the `<dependencies>` block (typically after the postgresql JDBC entry). Add:

```xml
<dependency>
    <groupId>org.opengauss</groupId>
    <artifactId>opengauss-jdbc</artifactId>
    <version>5.1.0-og</version>
</dependency>
```

- [ ] **Step 2: Verify dependency resolves**

```bash
cd server && mvn -pl data-talk-infrastructure dependency:resolve -q 2>&1 | tail -10
```

Expected: 0 errors, opengauss-jdbc-5.1.0-og.jar resolved into local Maven repo. If "Could not find artifact", check the pinned version against Maven Central.

- [ ] **Step 3: Add `OPENGAUSS` constant to `ConnectionKind`**

Edit `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`. Add **after** the existing `TIDB` line (preserving alphabetical or insertion order — match the file's existing convention; if alphabetical, insert before `ORACLE`):

```java
public static final String OPENGAUSS = "opengauss";
```

No alias map entry. The string `"opengauss"` is the canonical kind.

- [ ] **Step 4: Write failing test for `JdbcUrlBuilder` opengauss URL**

Edit `server/data-talk-application/src/test/java/com/datatalk/application/connection/JdbcUrlBuilderTest.java`. Add test method (place near existing `postgresql` URL tests):

```java
@Test
void buildsOpenGaussJdbcUrl() {
    var conn = new ConnectionRecord(
        "id-1", "name", "opengauss", "host", 5432,
        "myapp", null, "user", new byte[]{0}, 5000, false, null, null, null, null, null
    );
    String url = JdbcUrlBuilder.build(conn);
    assertThat(url).isEqualTo("jdbc:opengauss://host:5432/myapp");
}

@Test
void buildsOpenGaussJdbcUrl_defaultsToPostgresWhenDbBlank() {
    var conn = new ConnectionRecord(
        "id-1", "name", "opengauss", "host", 5432,
        null, null, "user", new byte[]{0}, 5000, false, null, null, null, null, null
    );
    String url = JdbcUrlBuilder.build(conn);
    assertThat(url).isEqualTo("jdbc:opengauss://host:5432/postgres");
}
```

The exact `ConnectionRecord` constructor signature varies; mirror the existing `postgresql` test pattern in the same file.

- [ ] **Step 5: Run test to verify it fails**

```bash
cd server && mvn -pl data-talk-application test -Dtest=JdbcUrlBuilderTest#buildsOpenGaussJdbcUrl -q 2>&1 | tail -15
```

Expected: FAIL with `unsupported database kind: opengauss` from the `default` branch.

- [ ] **Step 6: Add `case ConnectionKind.OPENGAUSS` to `JdbcUrlBuilder`**

Edit `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`. Add **two cases** — once in the read-write builder switch (around line 15) and once in the read-only/test builder switch (around line 112). Both branches identical:

```java
case ConnectionKind.OPENGAUSS ->
    "jdbc:opengauss://" + c.host() + ":" + c.port() + "/" + (db != null && !db.isBlank() ? db : "postgres");
```

Place each case **adjacent to** the existing `case ConnectionKind.POSTGRESQL` for visual grouping. **Do not** fold the two kinds into a shared case — opengauss may diverge in URL parameter shape Day-2.

- [ ] **Step 7: Run test to verify it passes**

```bash
cd server && mvn -pl data-talk-application test -Dtest=JdbcUrlBuilderTest#buildsOpenGaussJdbcUrl -q 2>&1 | tail -10
```

Expected: PASS.

- [ ] **Step 8: Add `opengauss` timeout branch to `ConnectionService.testConnection`**

Edit `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java`, around line 148 (the existing `else if (kind.equals(ConnectionKind.POSTGRESQL))` branch). Add **immediately after** the POSTGRESQL branch:

```java
} else if (kind.equals(ConnectionKind.OPENGAUSS)) {
    int timeoutSeconds = c.connectTimeout() / 1000;
    url += (url.contains("?") ? "&" : "?")
        + "connectTimeout=" + timeoutSeconds
        + "&socketTimeout=" + timeoutSeconds;
}
```

(Same shape as the POSTGRESQL branch — openGauss uses PG protocol seconds, not milliseconds.)

- [ ] **Step 9: Compile**

```bash
cd server && mvn compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS. If compile fails, fix path/syntax inline before commit.

- [ ] **Step 10: Commit**

```bash
git add server/data-talk-infrastructure/pom.xml \
        server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java \
        server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java \
        server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java \
        server/data-talk-application/src/test/java/com/datatalk/application/connection/JdbcUrlBuilderTest.java
git commit -m "feat(opengauss): add ConnectionKind constant, JDBC URL, driver dependency

- pom.xml: org.opengauss:opengauss-jdbc:5.1.0-og (MulanPSL2, Maven Central)
- ConnectionKind.OPENGAUSS = \"opengauss\" (no aliases)
- JdbcUrlBuilder: jdbc:opengauss://host:port/db (defaults to postgres if blank)
- ConnectionService: opengauss timeout branch (seconds, mirrors PG)
- JdbcUrlBuilderTest: 2 new tests verify URL shape and default-db fallback"
```

---

### Task 3: Splitter Routing And SqlExecuteService Branches

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java:27`
- Modify (if applicable): `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java` (locate batch DML / bulk-rewrite / generic dialect-tagged branches)
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/sql/DefaultSqlStatementSplittersTest.java`

- [ ] **Step 1: Write failing splitter routing test**

Edit `server/data-talk-infrastructure/src/test/java/com/datatalk/sql/DefaultSqlStatementSplittersTest.java`. Add:

```java
@Test
void opengaussRoutesToPostgresSplitter() {
    var splitters = new DefaultSqlStatementSplitters(
        new MySqlSqlStatementSplitter(),
        new PostgresJdbcSqlStatementSplitter(),
        new GenericSqlStatementSplitter(),
        new OracleSqlStatementSplitter()
    );
    var statements = splitters.split("opengauss",
        "CREATE FUNCTION f() RETURNS int AS $$ BEGIN RETURN 1; END $$ LANGUAGE plpgsql;\nSELECT 1;");
    assertThat(statements).hasSize(2);
    assertThat(statements.get(0)).contains("CREATE FUNCTION");
    assertThat(statements.get(1).trim()).isEqualTo("SELECT 1;");
}
```

(Constructor argument list mirrors the existing test file's pattern; if it differs, follow the file's convention.)

- [ ] **Step 2: Run test to verify it fails**

```bash
cd server && mvn -pl data-talk-infrastructure test -Dtest=DefaultSqlStatementSplittersTest#opengaussRoutesToPostgresSplitter -q 2>&1 | tail -15
```

Expected: FAIL — without the routing change, opengauss falls through to `genericSplitter` which would split on the unguarded `;` inside the function body and produce ≠ 2 statements.

- [ ] **Step 3: Add `opengauss` to PG splitter branch**

Edit `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java`, line 27 (the existing PG branch):

From:

```java
if ("postgres".equalsIgnoreCase(connectionKind) || "postgresql".equalsIgnoreCase(connectionKind)) {
    return postgresSplitter.split(sql);
}
```

To:

```java
if ("postgres".equalsIgnoreCase(connectionKind)
    || "postgresql".equalsIgnoreCase(connectionKind)
    || "opengauss".equalsIgnoreCase(connectionKind)) {
    return postgresSplitter.split(sql);
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd server && mvn -pl data-talk-infrastructure test -Dtest=DefaultSqlStatementSplittersTest#opengaussRoutesToPostgresSplitter -q 2>&1 | tail -10
```

Expected: PASS.

- [ ] **Step 5: Inspect SqlExecuteService for kind-tagged branches**

```bash
grep -n "POSTGRESQL\|postgresql" server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java
```

For each match, decide:
- if the branch is dialect-language-tagged (e.g., generates SQL formatter language string `"postgresql"`), add an `opengauss` arm that returns the same value;
- if the branch handles batch DML or bulk rewrite that is PG-specific, add `OPENGAUSS` adjacent to `POSTGRESQL`;
- if the branch is unrelated to dialect routing, **leave it alone**.

(If `grep` returns 0 lines, this step is N/A — record `N/A: SqlExecuteService has no PG-tagged branches` in the commit body.)

- [ ] **Step 6: Compile**

```bash
cd server && mvn compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/sql/DefaultSqlStatementSplittersTest.java
# Add SqlExecuteService.java only if Step 5 made changes
git commit -m "feat(opengauss): route SQL splitter to PostgresJdbcSqlStatementSplitter

- DefaultSqlStatementSplitters: add opengauss to PG branch (proven equivalent
  by AbstractPgForkSplitterEquivalenceTest in Task 7)
- DefaultSqlStatementSplittersTest: PL/pgSQL + multi-statement assertion
- SqlExecuteService: <see commit body — N/A or list specific branches added>"
```

---

### Task 4: Target Discovery, Schema Read, System Filter

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java:52`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/session/ConnectionTargetDiscoveryServiceTest.java`

- [ ] **Step 1: Locate the PG database-discovery branch**

```bash
grep -n "POSTGRESQL.equals(connection.kind())" server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java
```

Expected: line 52 (or close). Note the line number for Step 3.

- [ ] **Step 2: Write failing test for opengauss database discovery**

Edit `ConnectionTargetDiscoveryServiceTest.java`. Add:

```java
@Test
void opengaussDiscoversDatabasesViaPgDatabaseQuery() throws Exception {
    var connection = new ConnectionRecord(
        "id-1", "name", "opengauss", "host", 5432,
        "myapp", "public", "user", new byte[]{0}, 5000, false, null, null, null, null, null
    );
    // The test mocks JDBC at the DataSource layer so the SELECT pg_database
    // path is exercised instead of getCatalogs().
    // (Mock setup pattern follows the existing PostgreSQL test in this file.)
    var result = service.discover(connection);
    assertThat(result.databases()).containsExactly("myapp", "postgres");
}
```

(If a PostgreSQL test already exists with mock setup, copy its boilerplate verbatim and change kind to `"opengauss"`.)

- [ ] **Step 3: Add `OPENGAUSS` to PG database-discovery branch**

Edit line 52 of `ConnectionTargetDiscoveryService.java`:

From:

```java
if (ConnectionKind.POSTGRESQL.equals(connection.kind())) {
```

To:

```java
if (ConnectionKind.POSTGRESQL.equals(connection.kind())
    || ConnectionKind.OPENGAUSS.equals(connection.kind())) {
```

- [ ] **Step 4: Add 12-element openGauss system schema filter**

In the same file, locate or add an `OPENGAUSS_SYSTEM_SCHEMAS` constant (use the file's existing `Set.of(...)` style for sibling kinds). Add (lower-case, used in case-insensitive comparison):

```java
private static final Set<String> OPENGAUSS_SYSTEM_SCHEMAS = Set.of(
    // PG standard (4)
    "pg_catalog", "information_schema", "pg_temp_*", "pg_toast_*"
);
// Note: "pg_temp_*" / "pg_toast_*" prefix matching is handled in the existing
// PG branch via String.startsWith("pg_temp_"). The Set carries exact-match names only.

private static final Set<String> OPENGAUSS_SYSTEM_SCHEMAS_EXACT = Set.of(
    "pg_catalog", "information_schema",
    // openGauss increments (8)
    "dbe_pldebugger", "dbe_pldeveloper", "dbe_perf", "db4ai",
    "snapshot", "sqladvisor", "gs_logical_cluster", "oracle"
);
```

Then locate or add a method `boolean isOpenGaussSystemSchema(String name)` mirroring the existing `isPostgresSystemSchema` pattern:

```java
private boolean isOpenGaussSystemSchema(String name) {
    if (name == null) return false;
    String lower = name.toLowerCase(Locale.ROOT);
    if (OPENGAUSS_SYSTEM_SCHEMAS_EXACT.contains(lower)) return true;
    return lower.startsWith("pg_temp_") || lower.startsWith("pg_toast_");
}
```

Wire it: in the `read_schema` filter logic, add `if (kind.equals(ConnectionKind.OPENGAUSS) && isOpenGaussSystemSchema(name)) continue;` next to the existing PG filter.

(Exact wiring depends on the file's structure — locate by `grep -n "isPostgresSystemSchema" ConnectionTargetDiscoveryService.java`. If no helper exists, follow the file's existing inline filtering pattern.)

- [ ] **Step 5: Write failing test for system schema filter**

Add in `ConnectionTargetDiscoveryServiceTest.java`:

```java
@Test
void opengaussFiltersSystemSchemas() {
    assertThat(service.shouldShowSchema("opengauss", "pg_catalog")).isFalse();
    assertThat(service.shouldShowSchema("opengauss", "information_schema")).isFalse();
    assertThat(service.shouldShowSchema("opengauss", "dbe_pldebugger")).isFalse();
    assertThat(service.shouldShowSchema("opengauss", "db4ai")).isFalse();
    assertThat(service.shouldShowSchema("opengauss", "oracle")).isFalse();
    assertThat(service.shouldShowSchema("opengauss", "pg_temp_3")).isFalse();
    assertThat(service.shouldShowSchema("opengauss", "pg_toast_xyz")).isFalse();
    // Non-system schemas pass through:
    assertThat(service.shouldShowSchema("opengauss", "public")).isTrue();
    assertThat(service.shouldShowSchema("opengauss", "my_app")).isTrue();
    // Case-insensitive:
    assertThat(service.shouldShowSchema("opengauss", "DBE_PLDEBUGGER")).isFalse();
}
```

If the service does not expose `shouldShowSchema` publicly, add a `@VisibleForTesting`-style package-private helper or refactor `isOpenGaussSystemSchema` to package-private and test it directly.

- [ ] **Step 6: Run tests**

```bash
cd server && mvn -pl data-talk-application test -Dtest=ConnectionTargetDiscoveryServiceTest -q 2>&1 | tail -10
```

Expected: PASS.

- [ ] **Step 7: Compile**

```bash
cd server && mvn compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java \
        server/data-talk-application/src/test/java/com/datatalk/application/session/ConnectionTargetDiscoveryServiceTest.java
git commit -m "feat(opengauss): database discovery + 12-item system schema filter

- DiscoveryService: add opengauss to pg_database SELECT branch
- 4 PG-standard + 8 openGauss-increment schemas filtered (case-insensitive)
- Lower-case Set + pg_temp_/pg_toast_ prefix match
- Tests: 9 schema filter assertions + 1 database discovery"
```

---

### Task 5: OpenGaussDiagnosticsProvider And DiagnosticsService Branches

**Files:**
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/OpenGaussDiagnosticsProvider.java`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/OpenGaussDiagnosticsProviderTest.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/DiagnosticsService.java`

- [ ] **Step 1: Write failing OpenGaussDiagnosticsProviderTest**

```java
package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenGaussDiagnosticsProviderTest {
    private final Translator translator = Mockito.mock(Translator.class);

    @Test
    void supportedDriverTypesIsOpenGaussOnly() {
        var p = new OpenGaussDiagnosticsProvider(translator);
        assertThat(p.supportedDriverTypes()).containsExactly("opengauss");
    }

    @Test
    void supportedCapabilitiesIsEmpty() {
        var p = new OpenGaussDiagnosticsProvider(translator);
        assertThat(p.supportedCapabilities()).isEmpty();
    }

    @Test
    void explainReturnsDialectUnsupported() {
        Mockito.when(translator.get("diagnostics.explain_unsupported", "opengauss"))
               .thenReturn("openGauss EXPLAIN dialect_unsupported");
        var p = new OpenGaussDiagnosticsProvider(translator);
        var conn = ConnectionRecord.empty().withKind("opengauss");
        var result = p.explain("SELECT 1", conn, "pwd", "db", "schema");
        assertThat(result.status()).isEqualTo(DiagnosticStatus.UNSUPPORTED);
        assertThat(result.message()).contains("dialect_unsupported");
    }

    // Repeat for indexHints / lockInfo / poolStatus / tableSpaceInfo /
    // terminateSessionPreview / terminateSession / optimizeTablePreview / optimizeTable
    // (9 hooks total — match every method in DiagnosticsProvider interface).
}
```

(`ConnectionRecord.empty().withKind(...)` is the existing test factory; if not present, mirror the test factory pattern from `TiDbDiagnosticsProviderTest.java`.)

- [ ] **Step 2: Run test to verify it fails**

```bash
cd server && mvn -pl data-talk-infrastructure test -Dtest=OpenGaussDiagnosticsProviderTest -q 2>&1 | tail -10
```

Expected: FAIL — `OpenGaussDiagnosticsProvider` does not exist yet.

- [ ] **Step 3: Create `OpenGaussDiagnosticsProvider`**

Mirror `TiDbDiagnosticsProvider.java` line by line; only differ in:
- class name `OpenGaussDiagnosticsProvider`
- `supportedDriverTypes()` returns `Set.of("opengauss")`
- all i18n keys use `"opengauss"` literal as the substitution argument

```java
package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class OpenGaussDiagnosticsProvider extends AbstractDiagnosticsProvider {

    public OpenGaussDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("opengauss");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of();
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn, String decryptedPassword,
                                                 String database, String schema) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.explain_unsupported", "opengauss"));
    }

    @Override
    public DiagnosticResult<List<IndexRecommendation>> indexHints(String sql, ExplainPlan plan,
                                                                    ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.index_hints_unsupported", "opengauss"));
    }

    @Override
    public DiagnosticResult<LockReport> lockInfo(ConnectionRecord conn, String decryptedPassword, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.lock_not_supported", "opengauss"));
    }

    @Override
    public DiagnosticResult<PoolReport> poolStatus(ConnectionRecord conn, String decryptedPassword) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.pool_not_supported", "opengauss"));
    }

    @Override
    public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord conn, String decryptedPassword, String database, List<String> tables) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.tablespace_not_supported", "opengauss"));
    }

    @Override
    public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate_not_supported", "opengauss"));
    }

    @Override
    public DiagnosticResult<TerminateSessionResult> terminateSession(ConnectionRecord conn, String decryptedPassword, String targetSessionId, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate_not_supported", "opengauss"));
    }

    @Override
    public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize_not_supported", "opengauss"));
    }

    @Override
    public DiagnosticResult<OptimizeTableResult> optimizeTable(ConnectionRecord conn, String decryptedPassword, String table, String schemaName, String database) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize_not_supported", "opengauss"));
    }
}
```

- [ ] **Step 4: Add `opengauss` arm to `DiagnosticsService.unsupportedReason` switches**

Edit `server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/DiagnosticsService.java`. Locate the 5-case `switch (capability)` block (around line 285 — `lock`, `pool`, `space`, `terminate`, `optimize`). Add `case "opengauss" -> translator.get("diagnostics.<cap>_not_supported", kind);` to each switch's nested per-kind switch:

```java
// In each of the 5 capability switches:
case "opengauss" -> translator.get("diagnostics.lock_not_supported", kind);   // lock
case "opengauss" -> translator.get("diagnostics.pool_not_supported", kind);   // pool
case "opengauss" -> translator.get("diagnostics.tablespace_not_supported", kind); // space
case "opengauss" -> translator.get("diagnostics.terminate_not_supported", kind); // terminate
case "opengauss" -> translator.get("diagnostics.optimize_not_supported", kind); // optimize
```

The `default` arm already covers `dialect_unsupported`, so functionally this duplicates the default — but the explicit case keeps the kind list visible for documentation, mirroring the existing tidb / clickhouse / starrocks / trino arms.

- [ ] **Step 5: Run tests**

```bash
cd server && mvn -pl data-talk-infrastructure test -Dtest=OpenGaussDiagnosticsProviderTest -q 2>&1 | tail -10
```

Expected: PASS — all 11 hooks (`supportedDriverTypes`, `supportedCapabilities`, plus 9 capability methods) return `dialect_unsupported`.

- [ ] **Step 6: Compile full server**

```bash
cd server && mvn compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/OpenGaussDiagnosticsProvider.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/OpenGaussDiagnosticsProviderTest.java \
        server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/DiagnosticsService.java
git commit -m "feat(opengauss): structured unsupported diagnostics provider

- OpenGaussDiagnosticsProvider extends AbstractDiagnosticsProvider, all 9
  capability hooks return DiagnosticResult.unsupported via i18n keys
- supportedDriverTypes = {opengauss}, supportedCapabilities = {}
- DiagnosticsService 5 unsupportedReason switches gain explicit opengauss arm
  (matches existing tidb/clickhouse/starrocks/trino documentation pattern)
- 11 unit tests confirm dialect_unsupported on every capability"
```

---

### Task 6: CalciteSqlRiskAnalyzer `classifyOpengaussSpecific`

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzerTest.java`

> **Note**: per opengauss design §8.2 Spec Author Note, the rolled-back `ad4c1f0` commit's exploratory `classifyOpengaussSpecific` is **not present** in HEAD. This task implements the function from scratch against the spec; there is no legacy code to maintain.

- [ ] **Step 1: Write failing tests for 4 anchored patterns**

Edit `CalciteSqlRiskAnalyzerTest.java`. Add a nested `@Nested class OpengaussRules` (mirroring tidb's nested test class):

```java
@Nested
class OpengaussRules {
    private final SqlStatementSplitters splitters = new DefaultSqlStatementSplitters(
        new MySqlSqlStatementSplitter(),
        new PostgresJdbcSqlStatementSplitter(),
        new GenericSqlStatementSplitter(),
        new OracleSqlStatementSplitter()
    );
    private final CalciteSqlRiskAnalyzer analyzer = new CalciteSqlRiskAnalyzer(splitters);

    @Test
    void dropNodeIsHigh() {
        var r = analyzer.analyze("opengauss", "DROP NODE pgxc_dn1");
        assertThat(r.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(r.code()).isEqualTo("opengauss_node_mgmt");
    }

    @Test
    void dropNodeWithDoubleSpaceIsHigh() {
        // \s+ tolerance, not startsWith
        var r = analyzer.analyze("opengauss", "DROP  NODE  pgxc_dn1");
        assertThat(r.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(r.code()).isEqualTo("opengauss_node_mgmt");
    }

    @Test
    void dropNodeGroupIsHigh() {
        var r = analyzer.analyze("opengauss", "DROP NODE GROUP foo_group");
        assertThat(r.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(r.code()).isEqualTo("opengauss_node_mgmt");
    }

    @Test
    void createNodeIsHigh() {
        var r = analyzer.analyze("opengauss", "CREATE NODE n1 WITH (type='datanode')");
        assertThat(r.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(r.code()).isEqualTo("opengauss_node_mgmt");
    }

    @Test
    void alterNodeIsHigh() {
        var r = analyzer.analyze("opengauss", "ALTER NODE n1 WITH (port=5430)");
        assertThat(r.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(r.code()).isEqualTo("opengauss_node_mgmt");
    }

    @Test
    void selectFromPgxcNodeIsHigh() {
        var r = analyzer.analyze("opengauss", "SELECT * FROM pgxc_node");
        assertThat(r.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(r.code()).isEqualTo("opengauss_pgxc_cluster");
    }

    @Test
    void selectFromPgxcClassIsHigh() {
        var r = analyzer.analyze("opengauss", "SELECT oid, relname FROM pgxc_class WHERE pcrelid = 1");
        assertThat(r.level()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void selectFromMyPgxcNodeLogIsNotHigh_anchorTest() {
        // Word-boundary anchor must NOT match user table named like my_pgxc_node_log
        var r = analyzer.analyze("opengauss", "SELECT * FROM my_pgxc_node_log");
        assertThat(r).isNotNull();
        assertThat(r.code()).isNotEqualTo("opengauss_pgxc_cluster");
    }

    @Test
    void plainSelectFallsThroughToCalciteGeneric() {
        var r = analyzer.analyze("opengauss", "SELECT 1");
        assertThat(r.level()).isEqualTo(RiskLevel.LOW);  // or whatever Calcite generic returns for SELECT
    }

    @Test
    void columnNamedDropNodeTypeNotMatched() {
        // ^ anchor must NOT match the keyword embedded mid-statement
        var r = analyzer.analyze("opengauss",
            "SELECT column_name FROM information_schema.columns WHERE column_name = 'drop_node_type'");
        assertThat(r.code()).isNotEqualTo("opengauss_node_mgmt");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd server && mvn -pl data-talk-application test -Dtest=CalciteSqlRiskAnalyzerTest\$OpengaussRules -q 2>&1 | tail -15
```

Expected: FAIL — without the dispatcher and `classifyOpengaussSpecific`, opengauss analysis falls through to Calcite generic, which doesn't tag `opengauss_node_mgmt` / `opengauss_pgxc_cluster`.

- [ ] **Step 3: Add dispatcher entry**

Edit `CalciteSqlRiskAnalyzer.java`. In the existing kind-dispatch chain (locate by `grep -n "TIDB.equalsIgnoreCase" CalciteSqlRiskAnalyzer.java`), add **after** the tidb dispatch:

```java
if (ConnectionKind.OPENGAUSS.equalsIgnoreCase(connectionKind)) {
    return classifyOpengaussSpecific(sql);
}
```

- [ ] **Step 4: Add `PGXC_CLUSTER_CATALOGS` constant and compiled regex**

Near the top of `CalciteSqlRiskAnalyzer.java`, alongside other class-level constants:

```java
private static final List<String> PGXC_CLUSTER_CATALOGS = List.of(
    "pgxc_node", "pgxc_class", "pgxc_group", "pgxc_database",
    "pgxc_shard", "pgxc_namespace", "pgxc_partition"
);

private static final Pattern PGXC_CATALOG_PATTERN = Pattern.compile(
    "\\b(" + String.join("|", PGXC_CLUSTER_CATALOGS) + ")\\b",
    Pattern.CASE_INSENSITIVE
);

private static final Pattern OPENGAUSS_DROP_NODE = Pattern.compile(
    "^drop\\s+node(\\s+group)?\\b", Pattern.CASE_INSENSITIVE);
private static final Pattern OPENGAUSS_CREATE_NODE = Pattern.compile(
    "^create\\s+node(\\s+group)?\\b", Pattern.CASE_INSENSITIVE);
private static final Pattern OPENGAUSS_ALTER_NODE = Pattern.compile(
    "^alter\\s+node(\\s+group)?\\b", Pattern.CASE_INSENSITIVE);
```

- [ ] **Step 5: Implement `classifyOpengaussSpecific`**

Add to `CalciteSqlRiskAnalyzer.java`:

```java
private SqlRiskAnalysis classifyOpengaussSpecific(String sql) {
    String normalized = stripLeadingComments(sql);
    if (normalized == null || normalized.isEmpty()) return null;

    // 3 anchored NODE management patterns (^ anchor, \s+ tolerance, \b word-boundary)
    if (OPENGAUSS_DROP_NODE.matcher(normalized).find()
        || OPENGAUSS_CREATE_NODE.matcher(normalized).find()
        || OPENGAUSS_ALTER_NODE.matcher(normalized).find()) {
        return SqlRiskAnalysis.high("opengauss_node_mgmt");
    }

    // 7 whitelisted pgxc_* cluster catalog tables (\b word-boundary, no contains)
    // Only check for SELECT/INSERT/UPDATE/DELETE/CREATE/ALTER reaching catalog tables.
    String lowerSql = normalized.toLowerCase(Locale.ROOT);
    if (lowerSql.startsWith("select") || lowerSql.startsWith("insert")
        || lowerSql.startsWith("update") || lowerSql.startsWith("delete")
        || lowerSql.startsWith("create") || lowerSql.startsWith("alter")) {
        if (PGXC_CATALOG_PATTERN.matcher(normalized).find()) {
            return SqlRiskAnalysis.high("opengauss_pgxc_cluster");
        }
    }

    // Standard PG SQL falls through to Calcite generic classifier.
    return null;
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
cd server && mvn -pl data-talk-application test -Dtest=CalciteSqlRiskAnalyzerTest\$OpengaussRules -q 2>&1 | tail -15
```

Expected: PASS — all 10 OpengaussRules tests green. If `selectFromMyPgxcNodeLogIsNotHigh_anchorTest` fails, you used `contains` instead of `\b` word-boundary regex; revisit `PGXC_CATALOG_PATTERN`.

- [ ] **Step 7: Run full risk analyzer tests to verify no regression**

```bash
cd server && mvn -pl data-talk-application test -Dtest=CalciteSqlRiskAnalyzerTest -q 2>&1 | tail -15
```

Expected: ALL PASS (existing mysql / tidb / postgresql / sqlite / etc. tests unaffected).

- [ ] **Step 8: Compile**

```bash
cd server && mvn compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java \
        server/data-talk-application/src/test/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzerTest.java
git commit -m "feat(opengauss): classifyOpengaussSpecific risk classifier

4 anchored patterns (^ + \\s+ + \\b word-boundary) per spec §8.2:
- DROP NODE [GROUP]    → L3 high (opengauss_node_mgmt)
- CREATE NODE [GROUP]  → L3 high (opengauss_node_mgmt)
- ALTER NODE [GROUP]   → L3 high (opengauss_node_mgmt)
- SELECT/INSERT/UPDATE/DELETE/CREATE/ALTER touching one of 7 whitelisted
  pgxc_* cluster catalog tables → L3 high (opengauss_pgxc_cluster)

Rules use word-boundary regex (\\bpgxc_node\\b) to avoid false positives
on user tables like my_pgxc_node_log. Replaces the rolled-back ad4c1f0
exploratory implementation that used contains-based matching.

10 nested tests: 5 NODE management variants (incl. \\s+ double-space),
2 pgxc_* whitelist hits, 1 pgxc_* anchor false-positive guard, 1 fall-
through, 1 ^-anchor false-positive guard."
```

---

### Task 7: `PgForkReuseRule` Abstract Base Test Kit

**Files:**
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/AbstractPgForkSplitterEquivalenceTest.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/AbstractPgForkMetadataReuseTest.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/AbstractPgForkTargetResolutionReuseTest.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/AbstractPgForkBatchDmlReuseTest.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/AbstractPgForkResultNormalizationReuseTest.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/AbstractPgForkConnectionTestReuseTest.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/package-info.java`

This is the kit mandated by Wave C umbrella §8 — neutral name, abstract-base shape symmetric to `MySqlProtocolReuseRule` from tidb. Future kingbase PG-mode (Wave C step 4) consumes this kit verbatim.

- [ ] **Step 1: Write `package-info.java`**

```java
/**
 * PgForkReuseRule abstract base test kit (Wave C umbrella §8).
 *
 * Each abstract base class proves a single behavioral equivalence point
 * between a PG-fork kind (opengauss, kingbase PG-mode, future kinds) and
 * the reference PostgreSQL JDBC stack. Subclasses fill exactly two things:
 * fixture source (DataSource) and kindUnderTest() identifier. All assertions
 * live in the abstract base.
 *
 * Naming neutrality: classes named Abstract*PgFork*ReuseTest. Forbidden:
 * OpenGaussSplitterTest, KingbaseMetadataTest, or any kind-anchored name
 * inside this package. Kind-anchored names live in
 * com.datatalk.application.coverage.opengauss / kingbase / etc., which
 * extend these bases.
 *
 * Step-2 producer: opengauss (this child plan).
 * Step-4 consumer: kingbase PG-mode.
 * Retroactive subscriber: postgresql (recorded as Wave C follow-up tech debt).
 */
package com.datatalk.application.coverage.pgfork;
```

- [ ] **Step 2: Write `AbstractPgForkSplitterEquivalenceTest`**

```java
package com.datatalk.application.coverage.pgfork;

import com.datatalk.infra.sql.PostgresJdbcSqlStatementSplitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Equivalence: PostgresJdbcSqlStatementSplitter behaves identically on the
 * PG-fork kind. Subclasses provide a real DataSource so the test can also
 * pre-validate that the PG-fork server accepts the syntax (sanity, not main
 * assertion).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractPgForkSplitterEquivalenceTest {

    private final PostgresJdbcSqlStatementSplitter splitter = new PostgresJdbcSqlStatementSplitter();

    protected abstract String kindUnderTest();
    protected abstract DataSource dataSourceFor();

    @Test
    void splitsTwoStandardStatements() {
        List<String> stmts = splitter.split("SELECT 1; SELECT 2;");
        assertThat(stmts).hasSize(2);
        assertThat(stmts.get(0).trim()).isEqualTo("SELECT 1;");
        assertThat(stmts.get(1).trim()).isEqualTo("SELECT 2;");
    }

    @Test
    void preservesDollarQuoted() {
        String sql = "CREATE FUNCTION f() RETURNS int AS $$ BEGIN RETURN 1; END $$ LANGUAGE plpgsql;\nSELECT 1;";
        List<String> stmts = splitter.split(sql);
        assertThat(stmts).hasSize(2);
        assertThat(stmts.get(0)).contains("$$ BEGIN RETURN 1; END $$");
    }

    @Test
    void preservesTaggedDollarQuoted() {
        String sql = "CREATE FUNCTION f() RETURNS int AS $body$ BEGIN RETURN 1; END $body$ LANGUAGE plpgsql;\nSELECT 1;";
        List<String> stmts = splitter.split(sql);
        assertThat(stmts).hasSize(2);
        assertThat(stmts.get(0)).contains("$body$ BEGIN RETURN 1; END $body$");
    }

    @Test
    void preservesPlPgSqlBlock() {
        String sql = "DO $$ DECLARE x int; BEGIN x := 1; RAISE NOTICE 'x=%', x; END $$;";
        List<String> stmts = splitter.split(sql);
        assertThat(stmts).hasSize(1);
        assertThat(stmts.get(0)).contains("RAISE NOTICE");
    }

    @Test
    void preservesMultiStatementWithComment() {
        String sql = "-- comment\nSELECT 1; /* block */ SELECT 2;";
        List<String> stmts = splitter.split(sql);
        assertThat(stmts).hasSize(2);
    }
}
```

- [ ] **Step 3: Write `AbstractPgForkMetadataReuseTest`**

```java
package com.datatalk.application.coverage.pgfork;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Equivalence: pg_catalog and information_schema discoverability via JDBC
 * DatabaseMetaData behaves identically on the PG-fork kind.
 */
public abstract class AbstractPgForkMetadataReuseTest {

    protected abstract String kindUnderTest();
    protected abstract DataSource dataSourceFor();

    @Test
    void canQueryPgCatalog() throws Exception {
        try (var conn = dataSourceFor().getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT count(*) FROM pg_catalog.pg_class")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isPositive();
        }
    }

    @Test
    void canQueryInformationSchema() throws Exception {
        try (var conn = dataSourceFor().getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT count(*) FROM information_schema.tables")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isPositive();
        }
    }

    @Test
    void getTablesReturnsUserSchemaTables() throws Exception {
        try (var conn = dataSourceFor().getConnection()) {
            try (var s = conn.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS pgfork_test_tbl (id int)");
            }
            var meta = conn.getMetaData();
            try (var rs = meta.getTables(null, "public", "pgfork_test_tbl", new String[]{"TABLE"})) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("TABLE_NAME")).isEqualTo("pgfork_test_tbl");
            }
        }
    }

    @Test
    void getColumnsReturnsTypeInfo() throws Exception {
        try (var conn = dataSourceFor().getConnection()) {
            try (var s = conn.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS pgfork_columns_tbl (id int PRIMARY KEY, name text NOT NULL)");
            }
            var meta = conn.getMetaData();
            try (var rs = meta.getColumns(null, "public", "pgfork_columns_tbl", "%")) {
                int rows = 0;
                while (rs.next()) rows++;
                assertThat(rows).isGreaterThanOrEqualTo(2);
            }
        }
    }
}
```

- [ ] **Step 4: Write `AbstractPgForkTargetResolutionReuseTest`**

```java
package com.datatalk.application.coverage.pgfork;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Equivalence: pg_database SELECT enumerates user databases, system schema
 * filter applies identically on the PG-fork kind.
 */
public abstract class AbstractPgForkTargetResolutionReuseTest {

    protected abstract String kindUnderTest();
    protected abstract DataSource dataSourceFor();

    @Test
    void enumeratesDatabasesViaPgDatabase() throws Exception {
        try (var conn = dataSourceFor().getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                 "SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname")) {
            List<String> names = new ArrayList<>();
            while (rs.next()) names.add(rs.getString(1));
            assertThat(names).isNotEmpty();
            assertThat(names).contains("postgres");
        }
    }

    @Test
    void searchPathDefaultsToPublic() throws Exception {
        try (var conn = dataSourceFor().getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SHOW search_path")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).contains("public");
        }
    }
}
```

- [ ] **Step 5: Write `AbstractPgForkBatchDmlReuseTest`**

```java
package com.datatalk.application.coverage.pgfork;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Equivalence: PG bulk-INSERT batch DML path executes identically on the
 * PG-fork kind.
 */
public abstract class AbstractPgForkBatchDmlReuseTest {

    protected abstract String kindUnderTest();
    protected abstract DataSource dataSourceFor();

    @BeforeEach
    void setUp() throws Exception {
        try (var conn = dataSourceFor().getConnection(); var s = conn.createStatement()) {
            s.execute("DROP TABLE IF EXISTS pgfork_batch_tbl");
            s.execute("CREATE TABLE pgfork_batch_tbl (id int, val text)");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (var conn = dataSourceFor().getConnection(); var s = conn.createStatement()) {
            s.execute("DROP TABLE IF EXISTS pgfork_batch_tbl");
        }
    }

    @Test
    void bulkInsertValuesInserts3Rows() throws Exception {
        try (var conn = dataSourceFor().getConnection(); var s = conn.createStatement()) {
            s.execute("INSERT INTO pgfork_batch_tbl (id, val) VALUES (1, 'a'), (2, 'b'), (3, 'c')");
            try (var rs = s.executeQuery("SELECT count(*) FROM pgfork_batch_tbl")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(3);
            }
        }
    }
}
```

- [ ] **Step 6: Write `AbstractPgForkResultNormalizationReuseTest`**

```java
package com.datatalk.application.coverage.pgfork;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Equivalence: JdbcResultValueNormalizer produces identical normalized
 * shapes on the PG-fork kind for: numeric, jsonb, uuid, bytea, timestamptz,
 * text[].
 */
public abstract class AbstractPgForkResultNormalizationReuseTest {

    protected abstract String kindUnderTest();
    protected abstract DataSource dataSourceFor();

    @Test
    void numericPrecisionPreserved() throws Exception {
        try (var conn = dataSourceFor().getConnection(); var s = conn.createStatement();
             var rs = s.executeQuery("SELECT 12345.6789::numeric(10, 4) AS v")) {
            rs.next();
            assertThat(rs.getBigDecimal(1)).isEqualByComparingTo(new BigDecimal("12345.6789"));
        }
    }

    @Test
    void uuidParseable() throws Exception {
        try (var conn = dataSourceFor().getConnection(); var s = conn.createStatement();
             var rs = s.executeQuery("SELECT '00000000-0000-0000-0000-000000000001'::uuid AS v")) {
            rs.next();
            assertThat(rs.getString(1)).isEqualTo("00000000-0000-0000-0000-000000000001");
        }
    }

    @Test
    void jsonbReadsAsString() throws Exception {
        try (var conn = dataSourceFor().getConnection(); var s = conn.createStatement();
             var rs = s.executeQuery("SELECT '{\"a\": 1}'::jsonb AS v")) {
            rs.next();
            assertThat(rs.getString(1)).contains("\"a\"").contains("1");
        }
    }

    @Test
    void byteaRoundTrip() throws Exception {
        try (var conn = dataSourceFor().getConnection(); var s = conn.createStatement();
             var rs = s.executeQuery("SELECT '\\x48656c6c6f'::bytea AS v")) {
            rs.next();
            byte[] bytes = rs.getBytes(1);
            assertThat(new String(bytes)).isEqualTo("Hello");
        }
    }

    @Test
    void textArrayReadsAsArray() throws Exception {
        try (var conn = dataSourceFor().getConnection(); var s = conn.createStatement();
             var rs = s.executeQuery("SELECT ARRAY['a', 'b', 'c']::text[] AS v")) {
            rs.next();
            var arr = rs.getArray(1);
            assertThat((Object[]) arr.getArray()).containsExactly("a", "b", "c");
        }
    }
}
```

- [ ] **Step 7: Write `AbstractPgForkConnectionTestReuseTest`**

```java
package com.datatalk.application.coverage.pgfork;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Equivalence: Connection.isValid + SELECT 1 work identically on the PG-fork
 * kind. The ConnectionService.testConnection path's smoke probe.
 */
public abstract class AbstractPgForkConnectionTestReuseTest {

    protected abstract String kindUnderTest();
    protected abstract DataSource dataSourceFor();

    @Test
    void isValidReturnsTrueForOpenConnection() throws Exception {
        try (var conn = dataSourceFor().getConnection()) {
            assertThat(conn.isValid(2)).isTrue();
        }
    }

    @Test
    void selectOneReturnsOne() throws Exception {
        try (var conn = dataSourceFor().getConnection();
             var s = conn.createStatement();
             var rs = s.executeQuery("SELECT 1")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }
}
```

- [ ] **Step 8: Compile**

```bash
cd server && mvn -pl data-talk-application test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS. The 6 abstract bases compile (no concrete subclasses yet — Task 8).

- [ ] **Step 9: Commit**

```bash
git add server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/
git commit -m "test(pgfork): PgForkReuseRule abstract base test kit (Wave C §8)

6 abstract base test classes proving PG-fork behavioral equivalence:
- AbstractPgForkSplitterEquivalenceTest (5 tests, dollar-quoted, PL/pgSQL)
- AbstractPgForkMetadataReuseTest (4 tests, pg_catalog/info_schema/getTables/getColumns)
- AbstractPgForkTargetResolutionReuseTest (2 tests, pg_database, search_path)
- AbstractPgForkBatchDmlReuseTest (1 test, bulk INSERT VALUES)
- AbstractPgForkResultNormalizationReuseTest (5 tests, numeric/uuid/jsonb/bytea/text[])
- AbstractPgForkConnectionTestReuseTest (2 tests, isValid + SELECT 1)

Symmetric to MySqlProtocolReuseRule from tidb (Wave C step 1). Naming
neutral: opengauss / kingbase / future PG-fork kinds extend these bases.
Concrete subclasses for opengauss land in Task 8."
```

---

### Task 8: openGauss Testcontainers + 6 Concrete `OpenGauss*ReuseIT` Subclasses

**Files:**
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/opengauss/OpenGaussContainerSupport.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/opengauss/OpenGaussSplitterEquivalenceIT.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/opengauss/OpenGaussMetadataReuseIT.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/opengauss/OpenGaussTargetResolutionReuseIT.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/opengauss/OpenGaussBatchDmlReuseIT.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/opengauss/OpenGaussResultNormalizationReuseIT.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/opengauss/OpenGaussConnectionTestReuseIT.java`

- [ ] **Step 1: Write `OpenGaussContainerSupport`**

Mirror tidb's `TiDbContainerSupport` shape. Use log-message-based health check per spec §12.2.

```java
package com.datatalk.application.coverage.opengauss;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * Shared openGauss Testcontainers fixture for the OpenGauss*ReuseIT family.
 *
 * Image: enmotech/opengauss:6.0.0 (LTS, MulanPSL2 driver kernel-aligned).
 * Health check: log-based, ~30s startup. Raw socket connect causes flaky CI.
 */
public final class OpenGaussContainerSupport {

    public static final String IMAGE_TAG = "enmotech/opengauss:6.0.0";
    public static final String DB = "datatalktest";
    public static final String USER = "gaussdb";
    public static final String PASSWORD = "Gaussdb@123";  // openGauss password complexity
    public static final int PORT = 5432;

    private OpenGaussContainerSupport() {}

    public static GenericContainer<?> create() {
        return new GenericContainer<>(DockerImageName.parse(IMAGE_TAG))
            .withEnv("GS_USERNAME", USER)
            .withEnv("GS_PASSWORD", PASSWORD)
            .withEnv("GS_DB", DB)
            .withEnv("GS_NODENAME", "datatalk_dn1")
            .withExposedPorts(PORT)
            .waitingFor(Wait.forLogMessage(
                ".*database system is ready to accept connections.*", 1)
                .withStartupTimeout(Duration.ofSeconds(120)));
    }

    public static DataSource dataSource(GenericContainer<?> container) {
        var ds = new org.opengauss.ds.PGSimpleDataSource();
        ds.setServerName(container.getHost());
        ds.setPortNumber(container.getMappedPort(PORT));
        ds.setDatabaseName(DB);
        ds.setUser(USER);
        ds.setPassword(PASSWORD);
        return ds;
    }
}
```

(`org.opengauss.ds.PGSimpleDataSource` is the class shipped by `opengauss-jdbc:5.1.0-og`. If the actual class name differs after pinning the driver, adjust at Step 1; the import is verified at Step 9 compile.)

- [ ] **Step 2: Write `OpenGaussSplitterEquivalenceIT`**

```java
package com.datatalk.application.coverage.opengauss;

import com.datatalk.application.coverage.pgfork.AbstractPgForkSplitterEquivalenceTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;

import javax.sql.DataSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OpenGaussSplitterEquivalenceIT extends AbstractPgForkSplitterEquivalenceTest {

    private static GenericContainer<?> container;
    private static DataSource ds;

    @BeforeAll
    static void start() {
        container = OpenGaussContainerSupport.create();
        container.start();
        ds = OpenGaussContainerSupport.dataSource(container);
    }

    @AfterAll
    static void stop() {
        if (container != null) container.stop();
    }

    @Override
    protected String kindUnderTest() { return "opengauss"; }

    @Override
    protected DataSource dataSourceFor() { return ds; }
}
```

- [ ] **Steps 3–7: Write the remaining 5 concrete subclasses**

Each is an exact copy of the Step 2 template, differing only in:
- class name (`OpenGaussMetadataReuseIT`, `OpenGaussTargetResolutionReuseIT`, `OpenGaussBatchDmlReuseIT`, `OpenGaussResultNormalizationReuseIT`, `OpenGaussConnectionTestReuseIT`)
- `extends` target (`AbstractPgForkMetadataReuseTest`, etc.)

The `@BeforeAll` / `@AfterAll` / `kindUnderTest` / `dataSourceFor` body is identical. Each subclass should run independently — the container lifecycle is per-class.

Optional optimization: introduce a JUnit 5 `@TestInstance(PER_CLASS)` shared `@RegisterExtension` so the container starts once per IT class but the DataSource handle is reused. **Not required Day-1**; the per-class start/stop overhead (~30s × 6 = 3 min) is acceptable.

- [ ] **Step 8: Run a single IT to verify wiring**

```bash
cd server && mvn -pl data-talk-application test \
    -Dtest=OpenGaussConnectionTestReuseIT -q 2>&1 | tail -20
```

Expected: PASS. The container starts (~30s), `isValid` and `SELECT 1` both pass.

If `Cannot find driver` error: verify the pom dependency from Task 2 Step 1 propagates to `data-talk-application` test classpath. May need to add `<scope>test</scope>` exclusion or `<dependency>... <type>jar</type></dependency>` shadow in `data-talk-application/pom.xml`.

- [ ] **Step 9: Run all 6 ITs**

```bash
cd server && mvn -pl data-talk-application test \
    -Dtest='OpenGauss*ReuseIT' -q 2>&1 | tail -30
```

Expected: 6 IT classes × multiple tests each, all PASS. Total runtime ~3-5 minutes (container per class).

- [ ] **Step 10: Compile full test suite**

```bash
cd server && mvn test-compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS.

- [ ] **Step 11: Commit**

```bash
git add server/data-talk-application/src/test/java/com/datatalk/application/coverage/opengauss/
git commit -m "test(opengauss): 6 concrete OpenGauss*ReuseIT testcontainers subclasses

Consume PgForkReuseRule abstract bases via enmotech/opengauss:6.0.0
testcontainers fixture (T1 per umbrella §7.4):
- OpenGaussSplitterEquivalenceIT
- OpenGaussMetadataReuseIT
- OpenGaussTargetResolutionReuseIT
- OpenGaussBatchDmlReuseIT
- OpenGaussResultNormalizationReuseIT
- OpenGaussConnectionTestReuseIT

OpenGaussContainerSupport: shared image tag, env vars, log-message health
check (.*database system is ready to accept connections.*, 120s timeout).
Image kernel + driver 5.1.0-og aligned.

Resolves Wave C umbrella §8 PgForkReuseRule producer obligation; kingbase
(step 4) consumer-ready."
```

---

### Task 9: MCP Adapter Enum + AGENTS.md

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java`
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java`

> **Wave C §7.5 timing constraint**: this task ships the AGENTS.md change **as part of the child plan PR** — the runtime prompt advertises openGauss only after this child plan's verification matrix passes (Task 12). The earlier rolled-back `ad4c1f0` violated this by claiming opengauss support before plan verify.

- [ ] **Step 1: Write failing AgentPromptContractTest cases**

Add to `AgentPromptContractTest.java` (mirroring tidb test cases):

```java
@Test
void agentsMdMentionsOpengaussCanonicalKindOnce() {
    String md = readAgentsMd();
    long count = countOccurrences(md, "Canonical kind: `opengauss`");
    assertThat(count).isEqualTo(1L);
}

@Test
void agentsMdHasOpengaussSection() {
    String md = readAgentsMd();
    assertThat(md).contains("### openGauss");
}

@Test
void agentsMdLists4OpengaussRiskRules() {
    String md = readAgentsMd();
    assertThat(md).contains("DROP NODE");
    assertThat(md).contains("CREATE NODE");
    assertThat(md).contains("ALTER NODE");
    assertThat(md).contains("pgxc_node");
    assertThat(md).contains("pgxc_class");
    assertThat(md).contains("pgxc_group");
    assertThat(md).contains("pgxc_database");
    assertThat(md).contains("pgxc_shard");
    assertThat(md).contains("pgxc_namespace");
    assertThat(md).contains("pgxc_partition");
}

@Test
void connectionObjectTypeEnumIncludesOpengauss() {
    var type = new ConnectionObjectType();
    var schema = type.objectSchema();
    var properties = (Map<String, Object>) schema.get("properties");
    var kind = (Map<String, Object>) properties.get("kind");
    var enumList = (List<String>) kind.get("enum");
    assertThat(enumList).contains("opengauss");
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd server && mvn -pl data-talk-adapter test -Dtest=AgentPromptContractTest -q 2>&1 | tail -10
```

Expected: FAIL — `AGENTS.md` and `ConnectionObjectType` enum do not yet contain `opengauss`.

- [ ] **Step 3: Add `opengauss` to `ConnectionObjectType` enum**

Edit `ConnectionObjectType.java`. Locate the existing `enum` list under `kind` (around line 39):

From:

```java
Map.entry("kind",         Map.of("type", "string", "enum", List.of("mysql", "postgresql", "sqlite", "h2", "mariadb", "tidb", "oracle", "sqlserver", "duckdb", "clickhouse", "apache_doris", "starrocks", "trino", "presto", "hive"))),
```

To:

```java
Map.entry("kind",         Map.of("type", "string", "enum", List.of("mysql", "postgresql", "sqlite", "h2", "mariadb", "tidb", "oracle", "sqlserver", "duckdb", "clickhouse", "apache_doris", "starrocks", "trino", "presto", "hive", "opengauss"))),
```

- [ ] **Step 4: Add `### openGauss` section to AGENTS.md**

Edit `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`. Locate the `### TiDB` section (the most-recent Wave C entry). Insert **after** the `### TiDB` section's closing line (before the next `###` heading):

```markdown
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

- [ ] **Step 5: Run tests to verify they pass**

```bash
cd server && mvn -pl data-talk-adapter test -Dtest=AgentPromptContractTest -q 2>&1 | tail -10
```

Expected: PASS — 4 new contract tests + existing tests all green.

- [ ] **Step 6: Compile**

```bash
cd server && mvn compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java \
        server/data-talk-adapter/src/main/resources/agents/AGENTS.md \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java
git commit -m "feat(opengauss): MCP enum + AGENTS.md prompt rules (post-verify)

- ConnectionObjectType.kind.enum gains \"opengauss\"
- AGENTS.md ### openGauss section: 4 risk rules listed (3 NODE + 7 pgxc_*),
  PG-fork protocol notes, dialect_unsupported diagnostics + ER, kind-name
  fuzzy mapping (prompt-layer only, does not modify ConnectionKind.normalize
  per design §9.3)
- AgentPromptContractTest: 4 new assertions

Wave C §7.5 timing: this section ships in the same PR as code/tests, after
the child plan's verification matrix passes (Task 12). Earlier rolled-back
ad4c1f0 violated this by claiming opengauss support before verify."
```

---

### Task 10: Frontend — Form, Picker, Formatter, i18n

**Files:**
- Modify: `client/src/features/settings/data-sources/connection-form-dialog.tsx`
- Modify: `client/src/features/stage/utils/format-sql.ts`
- Modify: `client/src/i18n/messages.ts`
- Optional (only if list-of-supported-kinds is enumerated): `client/src/features/chat/components/composer/data-source-picker.tsx`, `client/src/features/stage/components/connection-picker.tsx`

> Per opengauss design §9.1 / Q4 selected approach **A**: connection form **reuses the PostgreSQL form** verbatim. Day-1 only registers DATABASE_TYPES entry and i18n label. No new form component, no new help text, no schema-field-form-validation increment.

- [ ] **Step 1: Register `opengauss` in `DATABASE_TYPES`**

Edit `client/src/features/settings/data-sources/connection-form-dialog.tsx`. Locate the `DATABASE_TYPES` const. Insert after the `tidb` entry:

```ts
opengauss: { label: 'openGauss', port: 5432 },
```

- [ ] **Step 2: Verify the form renders for opengauss kind**

Inspect the form's render branches. The PG branch should already conditionally render `databaseName` + `schema` fields when `kind === 'postgresql'`. Add `opengauss` to the same condition:

```ts
// Before:
const isPgFamily = kind === 'postgresql' || kind === 'postgres';
// After:
const isPgFamily = kind === 'postgresql' || kind === 'postgres' || kind === 'opengauss';
```

(Exact variable name varies — locate by `grep -n "postgresql" client/src/features/settings/data-sources/connection-form-dialog.tsx`. Mirror the existing PG predicate pattern.)

- [ ] **Step 3: Add SQL formatter language mapping**

Edit `client/src/features/stage/utils/format-sql.ts`. Locate the language switch:

```ts
case 'postgres':
case 'postgresql':
    return 'postgresql'
```

Add `opengauss` to the same group:

```ts
case 'postgres':
case 'postgresql':
case 'opengauss':
    return 'postgresql'
```

- [ ] **Step 4: i18n: add openGauss kind label and help text (if any kind-conditional copy exists)**

Edit `client/src/i18n/messages.ts`. Locate the `connections.kind.*` key family (added in `a2b131f` for tidb / mariadb etc.). The opengauss design §9.1 says no kind-specific placeholder/help override Day-1 — but the kind label key is still required for the picker.

Add to both `zh-CN` and `en-US` blocks:

```ts
'connections.kind.opengauss': 'openGauss',
```

(label is intentionally identical in both locales — vendor brand casing.)

If the existing connection-form-dialog uses `DATABASE_TYPES.opengauss.label` directly, the i18n key may not be strictly necessary. Verify by:

```bash
grep -rn "connections.kind." client/src/ | head -5
```

If 0 matches, skip the i18n key add and document `N/A` in commit body.

- [ ] **Step 5: Type-check**

```bash
cd client && npx tsc --noEmit 2>&1 | tail -10
```

Expected: 0 type errors.

- [ ] **Step 6: Smoke-render the form (manual, optional)**

If the implementer has the dev server up (`cd client && npm run dev`), open the data-sources settings page → "Add data source" → check that "openGauss" appears in the kind dropdown and selecting it renders the PG form with default port 5432.

(If not running dev server, this step is skipped; Task 11 unit tests cover the rendering contract.)

- [ ] **Step 7: Commit**

```bash
git add client/src/features/settings/data-sources/connection-form-dialog.tsx \
        client/src/features/stage/utils/format-sql.ts \
        client/src/i18n/messages.ts
git commit -m "feat(opengauss): frontend form, picker, formatter, i18n

- DATABASE_TYPES.opengauss = { label: 'openGauss', port: 5432 }
- connection-form-dialog: opengauss reuses PG form predicate (host/port/
  user/pass/db/schema/timeout fields rendered identically to postgresql)
- format-sql: opengauss maps to sql-formatter 'postgresql' language
- i18n: connections.kind.opengauss = 'openGauss' (zh-CN + en-US, brand
  casing preserved in both)
- No new form component (per design §9.1 Q4 approach A reuse)
- No kind-specific placeholder/help text Day-1 (deferred to Day-2 user feedback)"
```

---

### Task 11: Frontend Unit Tests

**Files:**
- Create: `client/src/features/settings/data-sources/__tests__/opengauss-connection-form.test.tsx`
- Create: `client/src/features/settings/data-sources/__tests__/opengauss-connection-kind.test.ts`
- Create: `client/src/features/stage/utils/__tests__/format-sql-opengauss.test.ts`

- [ ] **Step 1: Write `opengauss-connection-kind.test.ts`**

```ts
import { describe, expect, it } from 'vitest'
import { DATABASE_TYPES } from '../connection-form-dialog'

describe('opengauss connection kind registration', () => {
  it('registers opengauss with brand label and port 5432', () => {
    expect(DATABASE_TYPES.opengauss).toEqual({
      label: 'openGauss',
      port: 5432,
    })
  })

  it('keeps postgresql registration intact', () => {
    expect(DATABASE_TYPES.postgresql).toBeDefined()
    expect(DATABASE_TYPES.postgresql.port).toBe(5432)
  })

  it('does not introduce alias keys', () => {
    // No 'og', 'opengauss-server', 'opengauss-cluster' aliases.
    expect(DATABASE_TYPES['og' as never]).toBeUndefined()
    expect(DATABASE_TYPES['opengauss-server' as never]).toBeUndefined()
  })
})
```

- [ ] **Step 2: Write `opengauss-connection-form.test.tsx`**

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ConnectionFormDialog } from '../connection-form-dialog'

describe('ConnectionFormDialog renders opengauss form', () => {
  it('shows host/port/user/password/db/schema fields for opengauss', () => {
    render(
      <ConnectionFormDialog
        open={true}
        kind="opengauss"
        onSubmit={() => {}}
        onCancel={() => {}}
      />
    )

    // Host + port + user + password + db (PG family).
    expect(screen.getByLabelText(/host/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/port/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/database/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/schema/i)).toBeInTheDocument()

    // Default port 5432.
    expect((screen.getByLabelText(/port/i) as HTMLInputElement).value).toBe('5432')
  })
})
```

(Exact `ConnectionFormDialog` props vary per current implementation; mirror the pattern in `tidb-connection-form.test.tsx` shipped by `273f1e1`.)

- [ ] **Step 3: Write `format-sql-opengauss.test.ts`**

```ts
import { describe, expect, it } from 'vitest'
import { resolveSqlFormatterLanguage } from '../format-sql'

describe('opengauss formatter language', () => {
  it('maps opengauss to postgresql', () => {
    expect(resolveSqlFormatterLanguage('opengauss')).toBe('postgresql')
  })

  it('case-insensitive', () => {
    expect(resolveSqlFormatterLanguage('OpenGauss')).toBe('postgresql')
    expect(resolveSqlFormatterLanguage('OPENGAUSS')).toBe('postgresql')
  })

  it('does not affect postgresql', () => {
    expect(resolveSqlFormatterLanguage('postgresql')).toBe('postgresql')
  })

  it('does not affect mysql', () => {
    expect(resolveSqlFormatterLanguage('mysql')).toBe('mysql')
  })
})
```

- [ ] **Step 4: Run frontend tests**

```bash
cd client && npm test -- --run 2>&1 | tail -20
```

Expected: all passing, including 3 new opengauss test files.

- [ ] **Step 5: Type-check again**

```bash
cd client && npx tsc --noEmit 2>&1 | tail -5
```

Expected: 0 type errors.

- [ ] **Step 6: Commit**

```bash
git add client/src/features/settings/data-sources/__tests__/opengauss-connection-form.test.tsx \
        client/src/features/settings/data-sources/__tests__/opengauss-connection-kind.test.ts \
        client/src/features/stage/utils/__tests__/format-sql-opengauss.test.ts
git commit -m "test(opengauss): frontend unit tests for form/kind/formatter

- opengauss-connection-kind: DATABASE_TYPES registration + no-alias guard
- opengauss-connection-form: PG family field rendering + default port 5432
- format-sql-opengauss: case-insensitive opengauss → postgresql mapping"
```

---

### Task 12: Consolidated Verification

End-to-end sanity check before housekeeping.

- [ ] **Step 1: Backend full test suite**

```bash
cd server && mvn clean verify 2>&1 | tee /tmp/opengauss-verify.log | tail -30
```

Expected: BUILD SUCCESS. Specifically:
- `JdbcUrlBuilderTest` 2 new opengauss tests PASS
- `DefaultSqlStatementSplittersTest` 1 new opengauss test PASS
- `ConnectionTargetDiscoveryServiceTest` 9 new opengauss tests PASS
- `OpenGaussDiagnosticsProviderTest` 11 tests PASS
- `CalciteSqlRiskAnalyzerTest$OpengaussRules` 10 tests PASS
- `AgentPromptContractTest` 4 new opengauss assertions PASS
- All 6 `OpenGauss*ReuseIT` IT classes green (~3-5 min container time)
- All existing tests (mysql / tidb / postgresql / etc.) still green

If any IT fails on container startup, check Docker daemon state and the pinned image tag from Task 1 Step 4.

- [ ] **Step 2: Frontend type-check**

```bash
cd client && npx tsc --noEmit 2>&1 | tail -5
```

Expected: 0 type errors.

- [ ] **Step 3: Frontend unit tests**

```bash
cd client && npm test -- --run 2>&1 | tail -10
```

Expected: all PASS.

- [ ] **Step 4: Manual smoke (recommended, not gating)**

```bash
# In one terminal:
docker run --rm --name og-smoke -d \
    -e GS_USERNAME=gaussdb -e GS_PASSWORD=Gaussdb@123 -e GS_DB=smoke \
    -p 5432:5432 enmotech/opengauss:6.0.0
# Wait ~30s for the container to be ready

# Then start backend + Tauri client:
cd server && mvn spring-boot:run -pl data-talk-adapter &
cd client && npm run tauri dev
```

In the Tauri client:
1. Settings → Data Sources → Add → Type: openGauss → fill form (host=localhost, port=5432, user=gaussdb, password=Gaussdb@123, db=smoke).
2. Test connection → expect green check.
3. Save → in chat, ask the AI to "show me the databases" → expect `smoke` and `postgres` in the result.
4. Open Query Editor → run `CREATE TABLE foo (id int); INSERT INTO foo VALUES (1); SELECT * FROM foo;` → expect L2 confirmation card → confirm → row returned.
5. Run `DROP NODE pgxc_dn1` → expect L3 confirmation card with `opengauss_node_mgmt` reason.
6. Run `SELECT * FROM pgxc_node` → expect L3 confirmation card with `opengauss_pgxc_cluster` reason.
7. Run `SELECT * FROM information_schema.tables WHERE table_name LIKE '%pgxc_node%'` → expect **L3 confirmation card** (the SQL contains `pgxc_node` which is whitelisted; this is correct strict behavior).
8. Run `SELECT * FROM my_pgxc_log` (user table named with pgxc_ prefix) → expect **L1 / pass-through** (word-boundary anchor must NOT match `my_pgxc_log` since `\bpgxc_log\b` matches but `pgxc_log` is **not** in the 7-table whitelist).

```bash
docker stop og-smoke
```

Document any deviation in the verification log under `tmp/`.

- [ ] **Step 5: Verification record**

Create `tmp/opengauss-plan-verify-record.md` (gitignored, per CLAUDE.md `tmp/` rule):

```markdown
# openGauss Plan Verification Record

Date: YYYY-MM-DD
Implementer: <name>

- [x] Backend mvn verify: BUILD SUCCESS (NN tests, M skipped)
- [x] Frontend tsc --noEmit: 0 errors
- [x] Frontend npm test: NN passing
- [x] Manual smoke: 8 scenarios pass
- [ ] BUGs registered: <none / list>
- [ ] Open follow-ups for Day-2 / tech-debt: <list>
```

(No git commit for this step — `tmp/` is gitignored. The record is archival evidence, not part of repo.)

---

### Task 13: Documentation Housekeeping

After verification passes, update canonical docs and move plan from Active to Completed.

**Files:**
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/product-specs/2026-05-08-data-source-coverage-wave-c-roadmap.md` §5.1
- Modify: `docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md` §9 Wave C Tracking

- [ ] **Step 1: Update DATA_SOURCE_TYPE_COMPATIBILITY.md Current Support Snapshot**

Locate the snapshot table. Append a new row for `opengauss` between `tidb` and the next entry:

```markdown
| `opengauss` | First-class | Connection UI (host/port/username/password/database/schema, default port 5432), JDBC URL (`jdbc:opengauss://`) via `org.opengauss:opengauss-jdbc:5.1.0-og` (MulanPSL2), database + schema two-level context, `PostgresJdbcSqlStatementSplitter` reuse proven by 6 `OpenGauss*ReuseIT` concrete subclasses of `PgForkReuseRule` abstract base kit, batch DML PG bulk-INSERT path, `JdbcResultValueNormalizer` PG baseline reuse, independent `classifyOpengaussSpecific` risk rules covering 4 anchored patterns (DROP/CREATE/ALTER NODE [GROUP] L3 + 7 whitelisted pgxc_* cluster catalog tables L3, all using `^` anchor + `\b` word-boundary regex), 12-item system schema filter (4 PG standard + 8 openGauss increments dbe_pldebugger/dbe_pldeveloper/dbe_perf/db4ai/snapshot/sqladvisor/gs_logical_cluster/oracle), column-store tables (WITH orientation=column) surface as TABLE type, `OpenGaussDiagnosticsProvider` returning structured `dialect_unsupported` for all 9 hooks, structured unsupported ER. TLS / SSL / DCS-CM cluster topology / column-store specific DDL / db4ai active optimization / physical backup / Huawei Cloud GaussDB managed service / online elastic scaling are out of scope for Day-1. Day-2: real EXPLAIN via `EXPLAIN (FORMAT JSON)` + new `PostgresJsonPlanParser` (shared with kingbase Day-2), real INDEX_HINTS for row-store via `SqlColumnExtractor` (column-store unsupported with reason). Minimum supported server version: openGauss 6.0. |
```

- [ ] **Step 2: Update DATA_SOURCE_TYPE_COMPATIBILITY.md ER matrix**

Locate the ER Inspector / ER Designer matrix. Add `opengauss` to the `dialect_unsupported` group (alongside tidb).

- [ ] **Step 3: Update DATA_SOURCE_TYPE_COMPATIBILITY.md Wave C Tracking**

Locate the Wave C Tracking section. Update the `opengauss` outcome:

From: `Planned: PG-fork reuse with kind-specific tests; ...`
To: `Completed YYYY-MM-DD: PG-fork reuse first-class; PgForkReuseRule kit produced (kingbase step 4 consumer-ready); 12-item system schema filter; classifyOpengaussSpecific 4-anchor risk rules.`

- [ ] **Step 4: Move plan from Active to Completed in `docs/exec-plans/index.md`**

Cut the row matching `2026-05-08-data-source-coverage-opengauss-plan.md` from `## 活跃计划` and paste under `## 已完成计划` (alongside `tidb-plan` and `diagnostics-day2-plan`). Update completion-date column. Match the existing format (no extra columns).

(If the plan was never registered in Active because Task 1 Step 5 didn't add it, register it directly under Completed.)

- [ ] **Step 5: Update wave-c-roadmap §5.1 opengauss readiness gate states**

Edit `docs/product-specs/2026-05-08-data-source-coverage-wave-c-roadmap.md` §5.1 opengauss table. Change every `⏳ 待启动` / `⏳ 待` cell to `✅` for all 6 gates (brainstorming → child design complete → child design approved → child plan started → child plan ship → first-class).

- [ ] **Step 6: Update wave-c-design §9 Wave C Child Artifact Tracking**

Edit `docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md` §9 table. Update the `opengauss` row's `Current outcome` cell:

From: `Planned: PG-fork reuse with kind-specific tests; ...`
To: `Completed YYYY-MM-DD: first-class shipped; PgForkReuseRule kit (6 abstract bases + 6 concrete OpenGauss*ReuseIT subclasses) produced; kingbase consumer-ready.`

- [ ] **Step 7: Verify no other doc has stale references**

```bash
grep -rn "opengauss.*待\|opengauss.*未\|opengauss.*planned\|opengauss.*Planned" docs/
```

Expected: 0 matches (all `Planned` / `待` / `未` states updated to `Completed` or `✅`).

- [ ] **Step 8: Commit**

```bash
git add docs/DATA_SOURCE_TYPE_COMPATIBILITY.md \
        docs/exec-plans/index.md \
        docs/product-specs/2026-05-08-data-source-coverage-wave-c-roadmap.md \
        docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md
git commit -m "docs(opengauss): housekeeping after verification

- DATA_SOURCE_TYPE_COMPATIBILITY.md: opengauss row added to Current Support
  Snapshot (first-class), ER matrix lists opengauss as dialect_unsupported,
  Wave C Tracking outcome flipped to Completed
- exec-plans/index.md: opengauss plan moved Active → Completed
- wave-c-roadmap §5.1: 6 readiness gates ⏳ → ✅
- wave-c-design §9: opengauss outcome Completed YYYY-MM-DD"
```

- [ ] **Step 9: Update plan checkbox progress in this file**

Mark every task's checkboxes in this plan from `[ ]` to `[x]`. Append at the end:

```markdown
---

# Completion Log

| Field | Value |
|---|---|
| Completed | YYYY-MM-DD |
| Implementer | <name> |
| Backend mvn verify | BUILD SUCCESS |
| Frontend tsc + npm test | clean |
| Manual smoke scenarios | 8/8 pass |
| BUGs registered | <list or none> |
| Tech-debt items | <list or none> |
| Day-2 successor | docs/exec-plans/<future>-diagnostics-day3-wave-c-plan.md (TBD) |
```

- [ ] **Step 10: Final commit**

```bash
git add docs/exec-plans/2026-05-08-data-source-coverage-opengauss-plan.md
git commit -m "docs(opengauss): mark plan complete + completion log"
```

---

# Risks

1. **enmotech/opengauss:6.0.0 image availability**: dockerhub may rate-limit or remove the tag. **Mitigation**: Task 1 Step 4 verifies tag presence at kickoff; if unavailable, fall back to `enmotech/opengauss:5.0.0` and re-run driver compatibility check (`5.1.0-og` driver may emit protocol-version warnings but should function).

2. **opengauss-jdbc Maven Central pull**: rare network hiccups can cause CI timeout on first pull. **Mitigation**: existing CI infra already pulls multi-MB drivers (mysql / postgres); no new infra needed.

3. **Container startup ~30s flaky on slow CI runners**: 120s timeout is generous; if still flaky, switch from log-based wait to `Wait.forSuccessfulCommand` with `gs_isready` per spec §12.2 fallback.

4. **`org.opengauss.ds.PGSimpleDataSource` class name**: the driver may use `org.opengauss.ds.PGConnectionPoolDataSource` or `org.opengauss.jdbc.PGSimpleDataSource` depending on version. **Mitigation**: Task 8 Step 1 + Step 8 single-IT-run validates the class path; if NCDFE, grep the driver jar (`unzip -l ~/.m2/.../opengauss-jdbc-5.1.0-og.jar | grep -i datasource`) and adjust import.

5. **Subclass `@BeforeAll` static container × 6 = 3 min startup overhead**: acceptable Day-1 (per Task 8 Step 7 Optional optimization note). If team finds this unbearable, refactor to a JUnit 5 shared `@RegisterExtension` once Day-2 starts.

6. **Word-boundary regex tested only on the lower-case copy**: `PGXC_CATALOG_PATTERN` uses `Pattern.CASE_INSENSITIVE`. Test step `selectFromMyPgxcNodeLogIsNotHigh_anchorTest` verifies the false-positive guard. If a future fixture surfaces an edge case (e.g., `pg.xc_node` with a dot), file as a BUG and tighten the regex.

# Known Issues

None at plan-write time. `docs/bugs/index.md` Open BUGs sweep is part of Task 1 Step 2.

---

## Self-Review

**Spec coverage** — every section in `2026-05-08-data-source-coverage-opengauss-design.md` mapped to a task:
- §1-§4 (Purpose / Gate / Inputs / Support Statement) → Task 1 (gate re-read)
- §5 (Kind Naming) → Task 2 (`ConnectionKind.OPENGAUSS` constant, no alias)
- §6 (Connection / Persistence) → Task 2 (driver pom + URL builder + ConnectionService timeout)
- §7 (Catalog / Database / Schema / Target Resolution) → Task 4 (PG database discovery + 12-schema filter + column-store TABLE visibility)
- §8.1 (Splitter Reuse) → Task 3 + Task 7 + Task 8 (routing + abstract base + concrete IT)
- §8.2 (Risk Classifier) → Task 6 (`classifyOpengaussSpecific` from scratch, ad4c1f0 mismatch corrected)
- §8.3 / §8.4 (Result Normalization / Batch DML) → Task 7 + Task 8 (`AbstractPgFork{ResultNormalization,BatchDml}ReuseTest` + concrete IT)
- §9 (Frontend / MCP / AGENTS.md) → Task 9 (MCP enum + AGENTS.md timing) + Task 10 + Task 11 (frontend)
- §10 (`PgForkReuseRule` Cross-Kind Reuse Kit) → Task 7 (abstract base) + Task 8 (concrete IT subclasses)
- §11 (Day-2 Upgrade Path) → **out of this plan's scope** by spec design (Day-2 successor plan; this plan ends at Day-1 first-class + dialect_unsupported diagnostics)
- §12 (Verification / Documentation Sync / OOS) → Task 12 (verify) + Task 13 (housekeeping)

**Placeholder scan** — no `TBD` / `TODO` / "implement later" / vague error handling references. Every code step has actual code. The "Day-2 successor" cell in Task 13 Step 9 Completion Log is intentionally `TBD` (no Day-2 plan exists yet); this is a forward pointer, not a Day-1 placeholder.

**Type consistency** — class names (`OpenGaussDiagnosticsProvider`, `OpenGaussContainerSupport`, `AbstractPgForkSplitterEquivalenceTest`, `OpenGaussSplitterEquivalenceIT`), method names (`classifyOpengaussSpecific`, `kindUnderTest`, `dataSourceFor`), and constants (`ConnectionKind.OPENGAUSS`, `PGXC_CLUSTER_CATALOGS`, `OPENGAUSS_SYSTEM_SCHEMAS_EXACT`) match across all tasks. The `i18n` keys (`diagnostics.explain_unsupported`, `diagnostics.lock_not_supported`, etc.) reuse the existing day2-shipped key set; no new key family introduced Day-1.

Plan is complete.
