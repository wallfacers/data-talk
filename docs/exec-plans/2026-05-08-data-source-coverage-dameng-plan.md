# Data Source Coverage: Dameng (DM 8) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship first-class Wave C step 5 Dameng (DM 8) Day-1 support per [dameng design](../product-specs/2026-05-08-data-source-coverage-dameng-design.md), producing kind-private equivalence tests + dual-channel risk classifier (5 anchored L3 patterns + 3 anchored `dialect_unsupported` patterns) + `DamengDiagnosticsProvider` Day-1 all-9-hook `dialect_unsupported`. **No** new cross-kind reuse abstractions, **no** Flyway migration (V18 oceanbase already covers field reuse), **no** new ConnectionRecord columns, **no** Testcontainers IT (T2 fixture: manual smoke + JDBC mock unit tests only).

**Architecture:** Backend adds canonical `dameng` kind via `com.dameng:DmJdbcDriverX:8.1.x` JDBC, reusing Oracle splitter (`GenericSqlStatementSplitter`) / normalizer (`JdbcResultValueNormalizer` Oracle baseline) / metadata path (`oracleDiscovery` with `ALL_TABLES` / `ALL_TAB_COLUMNS` / `ALL_IND_COLUMNS`) proven equivalent through three kind-private equivalence tests (`DamengSplitterEquivalenceTest` / `DamengMetadataEquivalenceTest` / `DamengResultNormalizationEquivalenceTest`). Single-mode kind (no multi-mode); `databaseName` field reused as initial schema name (Oracle-precedent). Frontend ships single-schema-field connection form with 5-state token contract; no multi-mode picker. AGENTS.md / MCP enum / first-class snapshot only after `mvn verify` SUCCESS + manual smoke 9-case pass.

**Tech Stack:** Spring Boot 3.5 + Java 21 (virtual threads), Maven, JUnit 5 + AssertJ, Mockito (JDBC mock), React 19 + Vite + shadcn/ui, OpenCode SDK. **No Testcontainers / no Flyway migration / no offline jar.**

**Spec author**: This plan implements the design at commit `d337e29` (final spec ship).

---

## File Structure

### Backend (Java 21)

**Create:**
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/DamengDiagnosticsProvider.java` — all 9 hooks `dialect_unsupported`
- `server/data-talk-application/src/test/java/com/datatalk/application/connection/DamengAliasNormalizationTest.java` — accept `dameng` (case-insensitive); reject `dm` / `dm8` / `DM` / `DM8` / `DM7` / `dameng7` / `dameng8` / `武汉达梦` / `达梦`
- `server/data-talk-application/src/test/java/com/datatalk/application/connection/DamengUrlBuilderTest.java` — URL with / without schema; no `/<database>` suffix
- `server/data-talk-application/src/test/java/com/datatalk/application/connection/DamengConnectionRecordValidationTest.java` — `compatibility_mode` must be null; no oceanbase tenant/cluster
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/dameng/DamengSplitterEquivalenceTest.java` — 5 splitter cases vs Oracle baseline (kind-private; no shared abstract base)
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/dameng/DamengMetadataEquivalenceTest.java` — 4 discovery cases via mock JDBC Connection
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/dameng/DamengResultNormalizationEquivalenceTest.java` — 5 type round-trip cases via mock ResultSet
- `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/sql/risk/DamengRiskClassifierTest.java` — Channel 1 (5 anchored L3 patterns) + Channel 2 (3 anchored `dialect_unsupported` patterns) × hit + boundary
- `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/DamengDiagnosticsDialectUnsupportedTest.java` — all 9 hooks
- `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/DamengDiagnosticsProviderRegistrationTest.java`
- `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/jdbc/DamengDriverCoexistenceTest.java`

**Modify:**
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java` — add `DAMENG` enum value + normalize() lower-case branch (no aliases)
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java` — add Dameng URL branch (`jdbc:dm://<host>:<port>`; no db suffix)
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java` — add Dameng post-connect `SET SCHEMA` injection (or `?schema=` URL parameter; finalized in Task 9)
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/DefaultSqlStatementSplitters.java` — route dameng to `genericSplitter` (same instance as Oracle)
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/risk/CalciteSqlRiskAnalyzer.java` — `classifyDamengSpecific` 5 patterns + `detectDamengUnsupported` 3 patterns + main classify wiring
- `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java` — dameng branch routes through Oracle discovery + 5-item `DAMENG_SYSTEM_SCHEMAS` filter
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java` — Dameng Stage 1 `dialect_unsupported` entry validation
- `server/data-talk-infrastructure/pom.xml` — add `com.dameng:DmJdbcDriverX:8.1.x` dependency
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java` — add `DAMENG("dameng")` enum (LAST step, post-verify)
- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` — add Dameng section (LAST step, post-verify)
- `server/data-talk-adapter/src/main/resources/messages.properties` — i18n entries (en)
- `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties` — i18n entries (zh)
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` — Current Support Snapshot row update (LAST step)
- `docs/exec-plans/index.md` — register this plan (Active → Completed)
- `docs/product-specs/2026-05-08-data-source-coverage-dameng-design.md` — backfill final pinned driver patch + `?schema=` vs `SET SCHEMA` decision

### Frontend (TypeScript / React 19)

**Create:**
- `client/src/features/settings/data-sources/dameng-connection-fields.tsx` — single-mode form: host / port (default 5236) / username / password / schema (optional)
- `client/src/features/settings/data-sources/__tests__/dameng-connection-fields.test.tsx`

**Modify:**
- `client/src/features/settings/data-sources/connection-form-dialog.tsx` — render dameng fields when kind=dameng (NO multi-mode component)
- `client/src/features/settings/data-sources/data-sources-page.tsx` — picker entry for Dameng (DM 8)
- `client/src/features/stage/utils/format-sql.ts` — dameng formatter routing (reuse Oracle / generic SQL formatter)
- `client/src/i18n/messages.ts` — labels + diagnostics dialect_unsupported keys (zh + en)

### Manual Smoke

**Create:**
- `tools/manual-smoke/dameng-day1.sh` — 9-case smoke script (T2 fixture; CI does not run; required for QA acceptance and `?schema=` vs `SET SCHEMA` decision lock)

---

## Task Order

Tasks 1-13 are mostly sequential due to enum / driver / discovery / risk-classifier dependencies. Task 11 (MCP enum + AGENTS.md) is hard-gated to run **only after** Tasks 2-10 all pass `mvn verify` + manual smoke (umbrella §7.5 timing rule). Tasks 7 (kind-private equivalence tests) and 8 (DiagnosticsProvider) can run in parallel after Task 6 lands the dual-channel risk classifier. Task 10 (frontend) can start after Task 4 lands ConnectionRecord field reuse.

---

### Task 1: Approval Gate + Driver Reachability Report + Day-2 Anchor Sanity Check + `?schema=` Decision Entry

**Files:**
- Read: `docs/product-specs/2026-05-08-data-source-coverage-dameng-design.md` (entire spec, 839 lines)
- Read: `docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md` §5 / §7.1 / §7.3 / §7.5 / §10
- Read: `docs/exec-plans/2026-05-08-diagnostics-day2-plan.md` line ~3121 (dameng row in §Day-3 candidate matrix)
- Verify: Maven Central `com.dameng:DmJdbcDriverX` 8.1.x latest stable patch
- Verify: ConnectionKind / ConnectionRecord / MultiModeConnectionShape default branch already shipped via oceanbase V18

- [ ] **Step 1: Read the full spec end-to-end**

Re-read `docs/product-specs/2026-05-08-data-source-coverage-dameng-design.md` (12 sections, 839 lines). Produce a 1-page bullet list of decisions to apply (driver patch, default port, system schemas, anchored patterns, i18n keys, no-Flyway-migration, no-new-columns, no-multi-mode). No code yet.

- [ ] **Step 2: Verify Dameng driver Maven Central visibility and pin patch**

Run:
```bash
curl -sf "https://search.maven.org/solrsearch/select?q=g:com.dameng+AND+a:DmJdbcDriverX&rows=20&wt=json" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); [print(r['v']) for r in d['response']['docs']]"
```
Expected: list of versions including `8.1.3.140` or later 8.1.x patch. Pin **the highest 8.1.x non-snapshot patch** (likely `8.1.3.140` per spec §6.1 estimate; verify against current Central response). Record the exact pinned version in this plan's Driver Reachability Report (Step 5).

If Maven Central visibility regressed (no 8.1.x artifact), **STOP**: per spec §6.1, fallback path (b) = internal Maven mirror requires user re-approval, and (c) = offline jar is **forbidden** by umbrella §10. Do not proceed silently.

- [ ] **Step 3: Verify ConnectionKind / ConnectionRecord / MultiModeConnectionShape default branch already shipped**

Run:
```bash
cd /home/wallfacers/project/data-talk && grep -n "validateModeForKind" server/data-talk-application/src/main/java/com/datatalk/application/connection/multimode/MultiModeConnectionShape.java
```
Expected: file exists with a `default -> { if (mode != null) throw ... }` branch (shipped by oceanbase plan Task 3). Dameng rows will store `compatibility_mode = NULL`, validated by this default branch. **No new Flyway migration or new ConnectionRecord column needed** (spec §6.3).

Run:
```bash
cd /home/wallfacers/project/data-talk && grep -n "oceanbaseTenant\|oceanbaseCluster\|compatibilityMode" server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRecord.java | head -3
```
Expected: 3 fields present (shipped by oceanbase plan Task 5). For dameng records: `compatibilityMode = null`, `oceanbaseTenant = null`, `oceanbaseCluster = null`.

- [ ] **Step 4: Verify Day-2 plan §Day-3 dameng row anchor**

Run:
```bash
cd /home/wallfacers/project/data-talk && grep -n "dameng" docs/exec-plans/2026-05-08-diagnostics-day2-plan.md | head -10
```
Expected: dameng row in the §Day-3 candidate matrix already mentions `EXPLAIN / 新建 DamengTabularGrammar` and explicitly defers `INDEX_HINTS` (per spec §11.2). **No backfill** to day2 plan needed at this child plan ship time.

- [ ] **Step 5: Record `?schema=` vs `SET SCHEMA` decision deferral entry**

Per spec §6.2, the choice between `jdbc:dm://h:p?schema=<name>` URL parameter vs post-connect `executeUpdate("SET SCHEMA <name>")` depends on actual DM 8 driver behavior on the manual smoke fixture (Task 9). Record in this plan a placeholder line: **"`?schema=` vs `SET SCHEMA` lock — TO FILL after Task 9 fixture verification; default fallback in Task 4 implementation = post-connect `SET SCHEMA`."** Task 9 Step 9 finalizes this and Task 13 backfills the spec §6.2.

- [ ] **Step 6: Append Driver Reachability Report and commit Approval Gate**

Append to this plan (just under the Self-Review section):

```
## Driver Reachability Report (Task 1 result)

- Dameng driver: `com.dameng:DmJdbcDriverX:<pinned-patch>` (Maven Central direct; commercial license; no offline jar)
- Driver class: `dm.jdbc.driver.DmDriver`
- Default port: 5236
- ConnectionRecord schema: V18 (oceanbase) sufficient — no new migration
- `?schema=` vs `SET SCHEMA` decision: deferred to Task 9 fixture verification
- Day-2 anchor: day2 plan §Day-3 dameng row already complete (EXPLAIN + DamengTabularGrammar + INDEX_HINTS deferred)
```

Then:
```bash
git add docs/exec-plans/2026-05-08-data-source-coverage-dameng-plan.md
git commit -m "chore(dameng): record approval gate (driver pin / day2 anchor)"
```

---

### Task 2: ConnectionKind.DAMENG + Reject All Aliases at `normalize()`

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/connection/DamengAliasNormalizationTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.datatalk.application.connection;

import com.datatalk.domain.error.DataTalkException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DamengAliasNormalizationTest {

    @Test
    void canonicalLowerCaseAccepted() {
        assertThat(ConnectionKind.normalize("dameng")).isEqualTo(ConnectionKind.DAMENG);
    }

    @Test
    void mixedCaseAccepted() {
        assertThat(ConnectionKind.normalize("Dameng")).isEqualTo(ConnectionKind.DAMENG);
        assertThat(ConnectionKind.normalize("DAMENG")).isEqualTo(ConnectionKind.DAMENG);
    }

    @Test
    void shortAliasDmIsRejected() {
        assertThatThrownBy(() -> ConnectionKind.normalize("dm"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("DM"))
            .isInstanceOf(DataTalkException.class);
    }

    @Test
    void versionSuffixedAliasIsRejected() {
        assertThatThrownBy(() -> ConnectionKind.normalize("dm8"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("DM8"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("DM7"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("dameng7"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("dameng8"))
            .isInstanceOf(DataTalkException.class);
    }

    @Test
    void chineseAliasIsRejected() {
        assertThatThrownBy(() -> ConnectionKind.normalize("达梦"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("武汉达梦"))
            .isInstanceOf(DataTalkException.class);
    }
}
```

- [ ] **Step 2: Run the failing test**

Run: `cd server && mvn -pl data-talk-application test -Dtest=DamengAliasNormalizationTest -q`
Expected: FAIL — `ConnectionKind.DAMENG` symbol not found.

- [ ] **Step 3: Add `DAMENG` enum value and `normalize()` branch**

Edit `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`:

```java
public enum ConnectionKind {
    MYSQL, POSTGRESQL, /* ... existing kinds ... */ OCEANBASE, DAMENG;

    public static ConnectionKind normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DataTalkException(DataTalkErrorCodes.UNKNOWN_CONNECTION_KIND, "kind=null");
        }
        String lower = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (lower) {
            case "mysql" -> MYSQL;
            // ... existing cases ...
            case "oceanbase" -> OCEANBASE;
            case "dameng" -> DAMENG;
            // explicit reject for all dameng aliases — guides the user to type "dameng"
            default -> throw new DataTalkException(
                DataTalkErrorCodes.UNKNOWN_CONNECTION_KIND, "kind=" + raw);
        };
    }
}
```

The `case "dameng"` branch only accepts the canonical lower-case input post-normalization; mixed-case `Dameng` / `DAMENG` flow through `toLowerCase(ROOT)` and hit the same case. Aliases (`dm`, `dm8`, `dameng8`, `达梦`, etc.) fall through `default` and throw, satisfying spec §5 + §6.4 requirement.

- [ ] **Step 4: Run test, verify pass**

Run: `cd server && mvn -pl data-talk-application test -Dtest=DamengAliasNormalizationTest -q`
Expected: PASS (5/5).

- [ ] **Step 5: Run full module compile to verify no exhaustive-switch breaks**

Run: `cd server && mvn -pl data-talk-application,data-talk-infrastructure,data-talk-adapter compile -q`
Expected: 0 errors. If any pre-existing switch on `ConnectionKind` lacks `DAMENG`, fix in Tasks 4-6 (typically: `JdbcUrlBuilder`, `ConnectionService`, `SqlExecuteService`, `ConnectionTargetDiscoveryService`, `DefaultSqlStatementSplitters`).

- [ ] **Step 6: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java \
        server/data-talk-application/src/test/java/com/datatalk/application/connection/DamengAliasNormalizationTest.java
git commit -m "feat(dameng): add DAMENG kind with strict normalize (no aliases)"
```

---

### Task 3: DamengDriverCoexistenceTest + Maven Dep + JdbcUrlBuilder Branch

**Files:**
- Modify: `server/data-talk-infrastructure/pom.xml`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/jdbc/DamengDriverCoexistenceTest.java`

- [ ] **Step 1: Add `DmJdbcDriverX` Maven dependency**

Edit `server/data-talk-infrastructure/pom.xml` (under `<dependencies>`):

```xml
<dependency>
    <groupId>com.dameng</groupId>
    <artifactId>DmJdbcDriverX</artifactId>
    <version>8.1.3.140</version> <!-- pin from Task 1 Step 2 result -->
</dependency>
```

Use the exact version pinned in Task 1 Step 2.

- [ ] **Step 2: Write failing driver coexistence test**

```java
package com.datatalk.infra.jdbc;

import org.junit.jupiter.api.Test;

import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DamengDriverCoexistenceTest {

    @Test
    void dmDriverAcceptsDmUrl() throws Exception {
        Driver dm = DriverManager.getDriver("jdbc:dm://localhost:5236");
        assertThat(dm.getClass().getName()).isEqualTo("dm.jdbc.driver.DmDriver");
    }

    @Test
    void dmDriverRejectsOracleUrl() throws Exception {
        Driver dm = (Driver) Class.forName("dm.jdbc.driver.DmDriver")
            .getDeclaredConstructor().newInstance();
        assertThat(dm.acceptsURL("jdbc:oracle:thin:@//host:1521/SVC")).isFalse();
    }

    @Test
    void oracleDriverRejectsDmUrl() throws Exception {
        // Oracle driver classpath presence is an existing-Oracle-Day-1 invariant
        Driver oracle = (Driver) Class.forName("oracle.jdbc.OracleDriver")
            .getDeclaredConstructor().newInstance();
        assertThat(oracle.acceptsURL("jdbc:dm://host:5236")).isFalse();
    }

    @Test
    void registeredDriversIncludeBoth() {
        List<Driver> drivers = Collections.list(DriverManager.getDrivers());
        boolean hasDm = drivers.stream().anyMatch(d -> d.getClass().getName().equals("dm.jdbc.driver.DmDriver"));
        boolean hasOracle = drivers.stream().anyMatch(d -> d.getClass().getName().equals("oracle.jdbc.OracleDriver"));
        assertThat(hasDm).as("dm driver registered").isTrue();
        assertThat(hasOracle).as("oracle driver registered").isTrue();
    }
}
```

- [ ] **Step 3: Run failing test**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=DamengDriverCoexistenceTest -q`
Expected: FAIL — DM driver class not on classpath OR JdbcUrlBuilder DAMENG branch missing (driver registration depends only on classpath; if pom.xml dep is in place, this test should already partially pass after Step 1).

- [ ] **Step 4: Add Dameng URL branch in `JdbcUrlBuilder`**

Edit `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`. Add the `DAMENG` case to the existing kind-switch (location: alongside the OCEANBASE branch shipped by oceanbase plan Task 5):

```java
case DAMENG -> {
    // Dameng URL is server-level, no /<database> suffix per spec §6.2.
    // Initial schema (databaseName) is injected post-connect via SET SCHEMA
    // OR via ?schema=<name> URL parameter — finalized in Task 9 fixture verification.
    yield "jdbc:dm://" + c.host() + ":" + c.port();
}
```

(If Task 9 fixture verification confirms `?schema=` URL parameter works on DM 8 driver, Task 13 backfills this branch and the spec §6.2 to embed `?schema=<dbName>` instead of using post-connect `SET SCHEMA`. Default Day-1 implementation = no URL parameter; schema injection lives in `ConnectionService.openConnection`.)

- [ ] **Step 5: Run full compile, then re-run coexistence test**

Run: `cd server && mvn -pl data-talk-application,data-talk-infrastructure compile -q`
Expected: 0 errors.

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=DamengDriverCoexistenceTest -q`
Expected: PASS (4/4). DM driver auto-registered via `META-INF/services/java.sql.Driver` in the artifact.

- [ ] **Step 6: Commit**

```bash
git add server/data-talk-infrastructure/pom.xml \
        server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/jdbc/DamengDriverCoexistenceTest.java
git commit -m "feat(dameng): add DmJdbcDriverX dep + jdbc:dm:// URL branch"
```

---

### Task 4: DamengUrlBuilderTest + ConnectionRecord Field Reuse + ConnectionService Schema Injection

**Files:**
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/connection/DamengUrlBuilderTest.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/connection/DamengConnectionRecordValidationTest.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java`

- [ ] **Step 1: Write failing JdbcUrlBuilder test**

```java
package com.datatalk.application.connection;

import com.datatalk.application.persistence.ConnectionRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DamengUrlBuilderTest {

    @Test
    void buildsServerLevelUrlIgnoringDatabaseName() {
        // dameng URL has NO /<db> suffix even when databaseName is set
        var record = damengRecord("MY_SCHEMA");
        assertThat(JdbcUrlBuilder.build(record))
            .isEqualTo("jdbc:dm://h:5236");
    }

    @Test
    void buildsServerLevelUrlWhenDatabaseNameNull() {
        var record = damengRecord(null);
        assertThat(JdbcUrlBuilder.build(record))
            .isEqualTo("jdbc:dm://h:5236");
    }

    @Test
    void buildsServerLevelUrlWhenDatabaseNameBlank() {
        var record = damengRecord("");
        assertThat(JdbcUrlBuilder.build(record))
            .isEqualTo("jdbc:dm://h:5236");
    }

    private static ConnectionRecord damengRecord(String schema) {
        // 22-arg ConnectionRecord constructor (post-oceanbase V18):
        // compatibilityMode=null, oceanbaseTenant=null, oceanbaseCluster=null for dameng rows
        return new ConnectionRecord(
            "id", "n", "dameng", "h", 5236, schema, "SYSDBA", new byte[]{},
            "", 0L, 10, null, null, null, 1, true, null, false,
            null, null, null);
    }
}
```

- [ ] **Step 2: Run failing test**

Run: `cd server && mvn -pl data-talk-application test -Dtest=DamengUrlBuilderTest -q`
Expected: PASS already if Task 3 Step 4 implementation is correct. (If FAIL, fix the JdbcUrlBuilder `DAMENG` branch from Task 3.)

- [ ] **Step 3: Write ConnectionRecord validation test**

```java
package com.datatalk.application.connection;

import com.datatalk.application.connection.multimode.MultiModeConnectionShape;
import com.datatalk.application.persistence.ConnectionRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DamengConnectionRecordValidationTest {

    @Test
    void damengAcceptsNullCompatibilityMode() {
        // Dameng is single-mode; default branch in MultiModeConnectionShape
        // requires mode == null. No throw.
        MultiModeConnectionShape.validateModeForKind("dameng", null);
    }

    @Test
    void damengRejectsAnyCompatibilityMode() {
        // Setting compatibility_mode for dameng must be rejected
        // (mode-aware kinds are only oceanbase / kingbase).
        assertThatThrownBy(() -> MultiModeConnectionShape.validateModeForKind(
            "dameng",
            com.datatalk.application.connection.multimode.CompatibilityMode.MYSQL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not specify compatibility_mode");
    }

    @Test
    void damengRecordWithAllOceanBaseFieldsNullIsLegal() {
        // Construct the canonical dameng row shape:
        // compatibility_mode = NULL, oceanbase_tenant = NULL, oceanbase_cluster = NULL
        var record = new ConnectionRecord(
            "id", "dameng-prod", "dameng", "10.0.0.1", 5236, "SCOTT",
            "SYSDBA", new byte[]{1, 2, 3},
            "digest", System.currentTimeMillis(), 10,
            null, null, null, 1, true, null, false,
            null, null, null);
        // Validate every dameng-specific field invariant
        org.assertj.core.api.Assertions.assertThat(record.kind()).isEqualTo("dameng");
        org.assertj.core.api.Assertions.assertThat(record.compatibilityMode()).isNull();
        org.assertj.core.api.Assertions.assertThat(record.oceanbaseTenant()).isNull();
        org.assertj.core.api.Assertions.assertThat(record.oceanbaseCluster()).isNull();
        org.assertj.core.api.Assertions.assertThat(record.databaseName()).isEqualTo("SCOTT");
    }
}
```

- [ ] **Step 4: Run validation test**

Run: `cd server && mvn -pl data-talk-application test -Dtest=DamengConnectionRecordValidationTest -q`
Expected: PASS (3/3). Reuses existing `MultiModeConnectionShape` default branch (shipped by oceanbase plan Task 3).

- [ ] **Step 5: Implement `ConnectionService` Dameng schema injection**

Per spec §6.2 + §7.3, `databaseName` field carries the **initial schema** for dameng connections. Default Day-1 implementation = post-connect `SET SCHEMA`. Edit `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java`:

```java
// inside openConnection(ConnectionRecord c) — add after the JDBC connect line
if ("dameng".equals(c.kind())) {
    String schema = c.databaseName();
    if (schema != null && !schema.isBlank()) {
        // Use sqlIdentifier escaping (existing helper) to defend against injection;
        // schema is user-input but should be a single SQL identifier.
        String quoted = sqlIdentifier(schema);
        try (var st = connection.createStatement()) {
            st.executeUpdate("SET SCHEMA " + quoted);
        }
    }
    // null/blank → connect with default-user schema; discovery layer falls back
    // to SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM DUAL (per spec §7.3)
}
```

If `sqlIdentifier(...)` helper does not exist as a `ConnectionService` method, reuse the existing identifier-quoting utility from the SQL layer (e.g., `JdbcResultValueNormalizer` or a new private static method in `ConnectionService` matching Oracle escaping rules). Add a unit test in this task if a new helper is created.

- [ ] **Step 6: Run full compile**

Run: `cd server && mvn compile -q`
Expected: 0 errors.

- [ ] **Step 7: Commit**

```bash
git add server/data-talk-application/src/test/java/com/datatalk/application/connection/DamengUrlBuilderTest.java \
        server/data-talk-application/src/test/java/com/datatalk/application/connection/DamengConnectionRecordValidationTest.java \
        server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java
git commit -m "feat(dameng): URL builder tests + initial schema injection via SET SCHEMA"
```

---

### Task 5: Splitter Routing + Discovery Branch + System Schema Filter + Stage 1 Entry Validation

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/DefaultSqlStatementSplitters.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`

- [ ] **Step 1: Add splitter routing**

Edit `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/DefaultSqlStatementSplitters.java`. Route dameng to the same `genericSplitter` instance currently used by Oracle (per spec §8.2):

```java
// inside the existing kind-switch
case "oracle", "dameng" -> genericSplitter;
```

(Single shared `GenericSqlStatementSplitter` instance; no new bean. Equivalence proven by `DamengSplitterEquivalenceTest` in Task 7.)

- [ ] **Step 2: Add discovery branch with 5-item Dameng system schema filter**

Edit `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java`. Add the constant and branch (per spec §7.1 + §7.2):

```java
private static final java.util.Set<String> DAMENG_SYSTEM_SCHEMAS = java.util.Set.of(
    "SYS",         // system objects
    "SYSDBA",      // DBA user
    "SYSAUDITOR",  // audit
    "SYSSSO",      // security
    "CTISYS"       // full-text indexing
);

// inside the existing kind-switch routing discovery to oracleDiscovery
case "oracle" -> oracleDiscovery(c, ORACLE_SYSTEM_SCHEMAS);
case "dameng" -> oracleDiscovery(c, DAMENG_SYSTEM_SCHEMAS);
```

`oracleDiscovery(c, systemSchemaFilter)` is the existing Oracle metadata path (`ALL_TABLES` / `ALL_TAB_COLUMNS` / `ALL_IND_COLUMNS`). If the current Oracle discovery method doesn't accept a `Set<String>` filter parameter, refactor it in this step to take the filter as an argument (preserving the existing Oracle call site).

- [ ] **Step 3: Add SqlExecuteService Stage 1 entry validation**

Edit `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`. Add Dameng-specific Stage 1 validation BEFORE JDBC dispatch (per spec §8.1):

```java
// inside execute(...) — before the JDBC routing
if ("dameng".equals(c.kind())) {
    var unsupportedReason = riskAnalyzer.detectDamengUnsupported(sql);
    if (unsupportedReason.isPresent()) {
        // dialect_unsupported reasons map to specific i18n keys; see spec §8.3.
        String key = switch (unsupportedReason.get()) {
            case PLSQL_BLOCK -> "risk.dialect_unsupported.dameng.plsql_block";
            case PROCEDURE_DDL -> "risk.dialect_unsupported.dameng.procedure_ddl";
            case EXP_IMP_COMMAND -> "risk.dialect_unsupported.dameng.exp_imp_command";
        };
        throw new DataTalkException(
            DataTalkErrorCodes.DIALECT_UNSUPPORTED, translator.t(key));
    }
}
```

`detectDamengUnsupported` and `DamengUnsupportedReason` are introduced in Task 6.

- [ ] **Step 4: Verify full compile (placeholder for Task 6 risk classifier symbols)**

Run: `cd server && mvn compile -q`
Expected: COMPILE FAILURE — `riskAnalyzer.detectDamengUnsupported` and `DamengUnsupportedReason` enum do not exist yet. This is acceptable; Task 6 introduces them and re-validates the full module compile.

**Do not commit yet.** Wait for Task 6 to land the risk-classifier symbols, then commit Tasks 5+6 together (since the Stage 1 wiring depends on Task 6 symbols).

---

### Task 6: DamengRiskClassifier Dual-Channel — 5+3 Anchored Patterns

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/risk/CalciteSqlRiskAnalyzer.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/sql/risk/DamengRiskClassifierTest.java`

- [ ] **Step 1: Write failing risk classifier test (Channel 1 — 5 anchored L3 patterns)**

```java
package com.datatalk.infra.sql.risk;

import com.datatalk.domain.action.RiskLevel;
import com.datatalk.infra.sql.risk.CalciteSqlRiskAnalyzer.DamengUnsupportedReason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DamengRiskClassifierTest {

    private final CalciteSqlRiskAnalyzer analyzer = new CalciteSqlRiskAnalyzer();

    // ====== Channel 1 (dameng_admin_command — L3) ======

    // ---- TABLESPACE DDL ----
    @Test
    void createTablespaceIsL3() {
        assertThat(analyzer.classifyDamengSpecific(
            "CREATE TABLESPACE ts1 DATAFILE 'ts1.dbf' SIZE 100M"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void alterTablespaceIsL3() {
        assertThat(analyzer.classifyDamengSpecific("ALTER TABLESPACE ts1 ADD DATAFILE 'ts2.dbf'"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void selectTablespaceHistoryIsNotMatched() {
        // boundary: SELECT against table containing "tablespace" substring
        assertThat(analyzer.classifyDamengSpecific("SELECT * FROM tablespace_history"))
            .isEmpty();
    }

    // ---- USER DDL ----
    @Test
    void alterUserIsL3() {
        assertThat(analyzer.classifyDamengSpecific("ALTER USER scott IDENTIFIED BY new_pw"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void updateUserTableIsNotMatched() {
        // boundary: UPDATE against table beginning with "user" substring
        assertThat(analyzer.classifyDamengSpecific("UPDATE my_user_table SET x = 1"))
            .isEmpty();
    }

    // ---- ROLE DDL ----
    @Test
    void dropRoleIsL3() {
        assertThat(analyzer.classifyDamengSpecific("DROP ROLE admin_role"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void selectAllRolesIsNotMatched() {
        assertThat(analyzer.classifyDamengSpecific("SELECT role_name FROM all_roles"))
            .isEmpty();
    }

    // ---- GRANT / REVOKE ----
    @Test
    void grantSelectIsL3() {
        assertThat(analyzer.classifyDamengSpecific("GRANT SELECT ON t1 TO scott"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void revokeAllIsL3() {
        assertThat(analyzer.classifyDamengSpecific("REVOKE ALL PRIVILEGES ON t1 FROM scott"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void insertGrantLogIsNotMatched() {
        assertThat(analyzer.classifyDamengSpecific("INSERT INTO grant_log VALUES (1)"))
            .isEmpty();
    }

    // ---- DROP OBJECT (TABLE / VIEW / INDEX / SEQUENCE / SYNONYM) ----
    @Test
    void dropTableIsL3() {
        assertThat(analyzer.classifyDamengSpecific("DROP TABLE t1"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void dropViewIsL3() {
        assertThat(analyzer.classifyDamengSpecific("DROP VIEW v1"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void dropIndexIsL3() {
        assertThat(analyzer.classifyDamengSpecific("DROP INDEX idx1"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void dropSequenceIsL3() {
        assertThat(analyzer.classifyDamengSpecific("DROP SEQUENCE s1"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void dropSynonymIsL3() {
        assertThat(analyzer.classifyDamengSpecific("DROP SYNONYM syn1"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void insertDropAuditIsNotMatched() {
        assertThat(analyzer.classifyDamengSpecific("INSERT INTO drop_audit VALUES (1)"))
            .isEmpty();
    }

    // ---- whitespace anchor ----
    @Test
    void leadingWhitespaceStillMatchesChannel1() {
        assertThat(analyzer.classifyDamengSpecific("   CREATE TABLESPACE ts1 DATAFILE 'x.dbf' SIZE 100M"))
            .hasValue(RiskLevel.L3);
    }

    // ====== Channel 2 (dialect_unsupported) ======

    // ---- PL/SQL BLOCK ----
    @Test
    void declareBlockIsUnsupported() {
        assertThat(analyzer.detectDamengUnsupported("DECLARE v_x INT; BEGIN v_x := 1; END;"))
            .hasValue(DamengUnsupportedReason.PLSQL_BLOCK);
    }

    @Test
    void beginBlockIsUnsupported() {
        assertThat(analyzer.detectDamengUnsupported("BEGIN DBMS_OUTPUT.PUT_LINE('x'); END;"))
            .hasValue(DamengUnsupportedReason.PLSQL_BLOCK);
    }

    @Test
    void leadingWhitespacePlsqlStillMatches() {
        assertThat(analyzer.detectDamengUnsupported("   BEGIN NULL; END;"))
            .hasValue(DamengUnsupportedReason.PLSQL_BLOCK);
    }

    @Test
    void insertBeginLogIsNotPlsql() {
        // boundary: \b after BEGIN word should distinguish identifier
        assertThat(analyzer.detectDamengUnsupported("INSERT INTO begin_log VALUES (1)"))
            .isEmpty();
    }

    // ---- PROCEDURE / FUNCTION / TRIGGER / PACKAGE DDL ----
    @Test
    void createProcedureIsUnsupported() {
        assertThat(analyzer.detectDamengUnsupported("CREATE PROCEDURE p1 AS BEGIN NULL; END;"))
            .hasValue(DamengUnsupportedReason.PROCEDURE_DDL);
    }

    @Test
    void createOrReplaceFunctionIsUnsupported() {
        assertThat(analyzer.detectDamengUnsupported(
            "CREATE OR REPLACE FUNCTION f1 RETURN INT AS BEGIN RETURN 1; END;"))
            .hasValue(DamengUnsupportedReason.PROCEDURE_DDL);
    }

    @Test
    void dropTriggerIsUnsupported() {
        assertThat(analyzer.detectDamengUnsupported("DROP TRIGGER t1"))
            .hasValue(DamengUnsupportedReason.PROCEDURE_DDL);
    }

    @Test
    void alterPackageBodyIsUnsupported() {
        assertThat(analyzer.detectDamengUnsupported("ALTER PACKAGE BODY pkg COMPILE"))
            .hasValue(DamengUnsupportedReason.PROCEDURE_DDL);
    }

    // ---- EXP / IMP ----
    @Test
    void expCommandIsUnsupported() {
        assertThat(analyzer.detectDamengUnsupported("EXP scott/tiger@dm FILE=demo.dmp"))
            .hasValue(DamengUnsupportedReason.EXP_IMP_COMMAND);
    }

    @Test
    void impCommandIsUnsupported() {
        assertThat(analyzer.detectDamengUnsupported("IMP scott/tiger@dm FILE=demo.dmp"))
            .hasValue(DamengUnsupportedReason.EXP_IMP_COMMAND);
    }

    @Test
    void selectExpFunctionIsNotMatched() {
        // boundary: EXP() function call must NOT match (no whitespace after EXP)
        assertThat(analyzer.detectDamengUnsupported("SELECT EXP(2) FROM DUAL"))
            .isEmpty();
    }

    @Test
    void selectImpToDateIsNotMatched() {
        // boundary: IMP_TO_DATE function name must NOT match (\b boundary; no whitespace)
        assertThat(analyzer.detectDamengUnsupported("SELECT IMP_TO_DATE('2026-01-01') FROM DUAL"))
            .isEmpty();
    }
}
```

- [ ] **Step 2: Run failing test**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=DamengRiskClassifierTest -q`
Expected: FAIL — `classifyDamengSpecific`, `detectDamengUnsupported`, and `DamengUnsupportedReason` enum do not exist.

- [ ] **Step 3: Implement Channel 1 + Channel 2 patterns + nested enum + classify methods**

Edit `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/risk/CalciteSqlRiskAnalyzer.java`:

```java
// ====== Channel 1 — 5 anchored L3 patterns (dameng_admin_command) ======
// All patterns: ^\s* start anchor + \b word boundary per spec §8.3 + ad4c1f0 governance.

private static final java.util.regex.Pattern DAMENG_TABLESPACE_DDL =
    java.util.regex.Pattern.compile(
        "^\\s*(CREATE|ALTER|DROP)\\s+TABLESPACE\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);

private static final java.util.regex.Pattern DAMENG_USER_DDL =
    java.util.regex.Pattern.compile(
        "^\\s*(CREATE|ALTER|DROP)\\s+USER\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);

private static final java.util.regex.Pattern DAMENG_ROLE_DDL =
    java.util.regex.Pattern.compile(
        "^\\s*(CREATE|ALTER|DROP)\\s+ROLE\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);

private static final java.util.regex.Pattern DAMENG_GRANT_REVOKE =
    java.util.regex.Pattern.compile(
        "^\\s*(GRANT|REVOKE)\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);

private static final java.util.regex.Pattern DAMENG_DROP_OBJECT =
    java.util.regex.Pattern.compile(
        "^\\s*DROP\\s+(TABLE|VIEW|INDEX|SEQUENCE|SYNONYM)\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);

// ====== Channel 2 — 3 anchored dialect_unsupported patterns ======

private static final java.util.regex.Pattern DAMENG_PLSQL_BLOCK =
    java.util.regex.Pattern.compile(
        "^\\s*(DECLARE|BEGIN)\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);

private static final java.util.regex.Pattern DAMENG_PROCEDURE_DDL =
    java.util.regex.Pattern.compile(
        "^\\s*(CREATE|ALTER|DROP)(\\s+OR\\s+REPLACE)?\\s+(PROCEDURE|FUNCTION|TRIGGER|PACKAGE(\\s+BODY)?)\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);

private static final java.util.regex.Pattern DAMENG_EXP_IMP =
    java.util.regex.Pattern.compile(
        "^\\s*(EXP|IMP)\\s+",
        java.util.regex.Pattern.CASE_INSENSITIVE);
// EXP/IMP must be followed by whitespace; prevents SELECT EXP(2) misclassification

// ====== Nested enum for Stage 1 routing ======

public enum DamengUnsupportedReason {
    PLSQL_BLOCK,
    PROCEDURE_DDL,
    EXP_IMP_COMMAND
}

// ====== Channel 1 classify ======

public java.util.Optional<com.datatalk.domain.action.RiskLevel>
        classifyDamengSpecific(String sql) {
    if (sql == null) return java.util.Optional.empty();
    if (DAMENG_TABLESPACE_DDL.matcher(sql).find()
        || DAMENG_USER_DDL.matcher(sql).find()
        || DAMENG_ROLE_DDL.matcher(sql).find()
        || DAMENG_GRANT_REVOKE.matcher(sql).find()
        || DAMENG_DROP_OBJECT.matcher(sql).find()) {
        return java.util.Optional.of(com.datatalk.domain.action.RiskLevel.L3);
    }
    return java.util.Optional.empty();
}

// ====== Channel 2 detect ======

public java.util.Optional<DamengUnsupportedReason> detectDamengUnsupported(String sql) {
    if (sql == null) return java.util.Optional.empty();
    if (DAMENG_PLSQL_BLOCK.matcher(sql).find()) {
        return java.util.Optional.of(DamengUnsupportedReason.PLSQL_BLOCK);
    }
    if (DAMENG_PROCEDURE_DDL.matcher(sql).find()) {
        return java.util.Optional.of(DamengUnsupportedReason.PROCEDURE_DDL);
    }
    if (DAMENG_EXP_IMP.matcher(sql).find()) {
        return java.util.Optional.of(DamengUnsupportedReason.EXP_IMP_COMMAND);
    }
    return java.util.Optional.empty();
}
```

Wire Channel 1 into the main `classify(String sql, ConnectionKind kind)` method (or analogous entry):

```java
if (kind == ConnectionKind.DAMENG) {
    var dmRisk = classifyDamengSpecific(sql);
    if (dmRisk.isPresent()) {
        return new SqlExecutionRisk(dmRisk.get(), "dameng_admin_command", sql);
    }
}
// fall through to base classification (catches TRUNCATE / ALTER TABLE etc. via the existing classifier)
```

- [ ] **Step 4: Run risk classifier test, verify pass**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=DamengRiskClassifierTest -q`
Expected: PASS (29/29 — 5 Channel 1 hits + 5 Channel 1 boundaries + 1 leading-whitespace + 4 Channel 2 PLSQL + 4 Channel 2 PROCEDURE + 4 Channel 2 EXP/IMP + 6 boundary; verify exact count against the test file).

- [ ] **Step 5: Run full compile (Task 5 wiring now satisfied)**

Run: `cd server && mvn compile -q`
Expected: 0 errors. Task 5 SqlExecuteService Stage 1 entry now compiles because `detectDamengUnsupported` and `DamengUnsupportedReason` exist.

- [ ] **Step 6: Commit Task 5 + Task 6 together**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/DefaultSqlStatementSplitters.java \
        server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java \
        server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java \
        server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/risk/CalciteSqlRiskAnalyzer.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/sql/risk/DamengRiskClassifierTest.java
git commit -m "feat(dameng): splitter + discovery + dual-channel risk classifier (5+3 anchored)"
```

---

### Task 7: Three Kind-Private Equivalence Tests (Splitter / Metadata / Result Normalization)

**Files:**
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/dameng/DamengSplitterEquivalenceTest.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/dameng/DamengMetadataEquivalenceTest.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/dameng/DamengResultNormalizationEquivalenceTest.java`

These tests are **kind-private** (no shared abstract base; spec §10 line 619: single-consumer abstractions are over-engineering). All three run on CI without a real DM server using JDBC mocks / hard-coded fixtures.

- [ ] **Step 1: Write `DamengSplitterEquivalenceTest` (5 cases)**

```java
package com.datatalk.application.coverage.dameng;

import com.datatalk.infra.sql.GenericSqlStatementSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kind-private equivalence test: dameng uses the same GenericSqlStatementSplitter
 * instance as Oracle Day-1. This test validates parity by enumerating the
 * 5-case canonical splitter contract on representative dameng statements.
 * Per spec §10, no shared abstract base is extracted because dameng is the
 * only Wave C Oracle-like kind.
 */
class DamengSplitterEquivalenceTest {

    private final GenericSqlStatementSplitter splitter = new GenericSqlStatementSplitter();

    @Test
    void singleStatement() {
        List<String> parts = splitter.split("SELECT * FROM ALL_TABLES");
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).trim()).isEqualTo("SELECT * FROM ALL_TABLES");
    }

    @Test
    void semicolonDelimitedMultiStatement() {
        List<String> parts = splitter.split("SELECT 1 FROM DUAL; SELECT 2 FROM DUAL;");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).trim()).isEqualTo("SELECT 1 FROM DUAL");
        assertThat(parts.get(1).trim()).isEqualTo("SELECT 2 FROM DUAL");
    }

    @Test
    void stringLiteralSemicolonEscaped() {
        // semicolon inside string literal must NOT split
        List<String> parts = splitter.split("INSERT INTO t VALUES ('a;b'); SELECT 1");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).trim()).isEqualTo("INSERT INTO t VALUES ('a;b')");
    }

    @Test
    void commentBlockSemicolonIgnored() {
        // semicolon inside /* */ comment must NOT split
        List<String> parts = splitter.split("SELECT 1 /* a;b */ FROM DUAL; SELECT 2");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).trim()).startsWith("SELECT 1");
    }

    @Test
    void emptyStatementsAreFiltered() {
        // ;;;; collapses to no statements
        List<String> parts = splitter.split(";;SELECT 1 FROM DUAL;;;");
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).trim()).isEqualTo("SELECT 1 FROM DUAL");
    }
}
```

- [ ] **Step 2: Write `DamengMetadataEquivalenceTest` (4 cases via mock JDBC)**

```java
package com.datatalk.application.coverage.dameng;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.session.ConnectionTargetDiscoveryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kind-private equivalence test: dameng routes through the existing Oracle
 * discovery path. Verifies that the SQL queries issued for dameng connections
 * are Oracle-compatible (ALL_TABLES / ALL_TAB_COLUMNS / ALL_IND_COLUMNS).
 */
class DamengMetadataEquivalenceTest {

    @Test
    void schemaListUsesAllUsers() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        var service = new ConnectionTargetDiscoveryService(/* deps */);
        // Call the discovery entrypoint with a dameng record
        service.listSchemas(damengRecord(), conn);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(conn).prepareStatement(sqlCaptor.capture());
        // Oracle-compatible schema enumeration: ALL_USERS or SYS.SYSOBJECTS
        assertThat(sqlCaptor.getValue().toUpperCase())
            .containsAnyOf("ALL_USERS", "SYS.SYSOBJECTS");
    }

    @Test
    void tableListUsesAllTables() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        var service = new ConnectionTargetDiscoveryService(/* deps */);
        service.listTables(damengRecord(), conn, "SCOTT");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(conn).prepareStatement(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue().toUpperCase()).contains("ALL_TABLES");
    }

    @Test
    void columnListUsesAllTabColumns() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        var service = new ConnectionTargetDiscoveryService(/* deps */);
        service.listColumns(damengRecord(), conn, "SCOTT", "T1");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(conn).prepareStatement(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue().toUpperCase()).contains("ALL_TAB_COLUMNS");
    }

    @Test
    void indexListUsesAllIndColumns() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        var service = new ConnectionTargetDiscoveryService(/* deps */);
        service.listIndexes(damengRecord(), conn, "SCOTT", "T1");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(conn).prepareStatement(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue().toUpperCase()).contains("ALL_IND_COLUMNS");
    }

    private static ConnectionRecord damengRecord() {
        return new ConnectionRecord(
            "id", "n", "dameng", "h", 5236, "SCOTT", "SYSDBA",
            new byte[]{}, "", 0L, 10, null, null, null, 1, true, null,
            false, null, null, null);
    }
}
```

If `ConnectionTargetDiscoveryService` constructor takes injected dependencies, instantiate test-doubles or fakes accordingly. The exact constructor signature is environment-bound; adapt only to the actual signature. Do not invent fields.

- [ ] **Step 3: Write `DamengResultNormalizationEquivalenceTest` (5 type round-trip cases)**

```java
package com.datatalk.application.coverage.dameng;

import com.datatalk.infra.jdbc.JdbcResultValueNormalizer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Kind-private equivalence test: dameng reuses the existing Oracle baseline
 * of JdbcResultValueNormalizer. Verifies CHAR trailing-space preservation,
 * VARCHAR2, NUMBER → BigDecimal, DATE with hh:mm:ss, TIMESTAMP, CLOB preview.
 */
class DamengResultNormalizationEquivalenceTest {

    private final JdbcResultValueNormalizer normalizer = new JdbcResultValueNormalizer();

    @Test
    void charTrailingSpacePreserved() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData md = mock(ResultSetMetaData.class);
        when(md.getColumnType(1)).thenReturn(Types.CHAR);
        when(rs.getMetaData()).thenReturn(md);
        when(rs.getString(1)).thenReturn("ABC   ");

        Object value = normalizer.normalize(rs, 1, "dameng");
        // dameng (Oracle baseline) preserves trailing spaces on fixed-width CHAR
        assertThat(value).isEqualTo("ABC   ");
    }

    @Test
    void varchar2PassesThrough() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData md = mock(ResultSetMetaData.class);
        when(md.getColumnType(1)).thenReturn(Types.VARCHAR);
        when(md.getColumnTypeName(1)).thenReturn("VARCHAR2");
        when(rs.getMetaData()).thenReturn(md);
        when(rs.getString(1)).thenReturn("hello");

        Object value = normalizer.normalize(rs, 1, "dameng");
        assertThat(value).isEqualTo("hello");
    }

    @Test
    void numberAsBigDecimal() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData md = mock(ResultSetMetaData.class);
        when(md.getColumnType(1)).thenReturn(Types.NUMERIC);
        when(rs.getMetaData()).thenReturn(md);
        when(rs.getBigDecimal(1)).thenReturn(new BigDecimal("123.456"));

        Object value = normalizer.normalize(rs, 1, "dameng");
        assertThat(value).isInstanceOf(BigDecimal.class);
        assertThat(value).isEqualTo(new BigDecimal("123.456"));
    }

    @Test
    void dateIncludesHhMmSs() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData md = mock(ResultSetMetaData.class);
        when(md.getColumnType(1)).thenReturn(Types.DATE);
        when(rs.getMetaData()).thenReturn(md);
        // DM 8 DATE includes time portion (Oracle-compatible); use Timestamp
        java.sql.Timestamp ts = java.sql.Timestamp.valueOf("2026-05-09 14:30:45");
        when(rs.getTimestamp(1)).thenReturn(ts);

        Object value = normalizer.normalize(rs, 1, "dameng");
        // Oracle baseline returns the timestamp form for DATE values; assert
        // string formatting includes the time portion
        assertThat(value.toString()).contains("14:30:45");
    }

    @Test
    void clobPreview() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData md = mock(ResultSetMetaData.class);
        when(md.getColumnType(1)).thenReturn(Types.CLOB);
        when(rs.getMetaData()).thenReturn(md);
        java.sql.Clob clob = mock(java.sql.Clob.class);
        when(clob.length()).thenReturn(100L);
        when(clob.getSubString(1, 64)).thenReturn("This is a CLOB preview...");
        when(rs.getClob(1)).thenReturn(clob);

        Object value = normalizer.normalize(rs, 1, "dameng");
        assertThat(value.toString()).contains("CLOB preview");
    }
}
```

The exact `JdbcResultValueNormalizer.normalize(...)` signature depends on the existing infrastructure; if the actual signature uses a different parameter list (e.g., (ResultSet, int, ConnectionKind) instead of (ResultSet, int, String)), adapt the test accordingly. Do not change the production normalizer signature solely for the test.

- [ ] **Step 4: Run all three equivalence tests**

Run: `cd server && mvn -pl data-talk-application,data-talk-infrastructure test -Dtest='Dameng*EquivalenceTest' -q`
Expected: PASS (5 splitter + 4 metadata + 5 normalization = 14 tests).

- [ ] **Step 5: Commit**

```bash
git add server/data-talk-application/src/test/java/com/datatalk/application/coverage/dameng/
git commit -m "test(dameng): kind-private equivalence (splitter + metadata + normalization)"
```

---

### Task 8: DamengDiagnosticsProvider (All 9 Hooks `dialect_unsupported`) + i18n

**Files:**
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/DamengDiagnosticsProvider.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/DamengDiagnosticsDialectUnsupportedTest.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/DamengDiagnosticsProviderRegistrationTest.java`
- Modify: `server/data-talk-adapter/src/main/resources/messages.properties` + `messages_zh_CN.properties`

- [ ] **Step 1: Write failing diagnostics test (all 9 hooks)**

```java
package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DamengDiagnosticsDialectUnsupportedTest {

    private final Translator translator = mock(Translator.class);
    private final DamengDiagnosticsProvider provider;

    DamengDiagnosticsDialectUnsupportedTest() {
        when(translator.t(anyString()))
            .thenAnswer(inv -> "[" + inv.getArgument(0) + "]");
        provider = new DamengDiagnosticsProvider(translator);
    }

    @Test
    void supportedDriverTypesIsOnlyDameng() {
        assertThat(provider.supportedDriverTypes()).containsExactly("dameng");
    }

    @Test
    void supportedCapabilitiesIsEmpty() {
        // Day-1: every hook returns dialect_unsupported per spec §9.2
        assertThat(provider.supportedCapabilities()).isEmpty();
    }

    @Test
    void explainReturnsDialectUnsupported() {
        var result = provider.explain("SELECT 1", damengRecord(), "pw", "db", null);
        assertThat(result.outcome()).isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
        assertThat(result.message())
            .contains("[diagnostics.dialect_unsupported.dameng.explain_real]");
    }

    @Test
    void indexHintsReturnsDialectUnsupported() {
        var result = provider.indexHints("SELECT 1", damengRecord(), "pw", "db", null);
        assertThat(result.outcome()).isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
        assertThat(result.message())
            .contains("[diagnostics.dialect_unsupported.dameng.index_hints]");
    }

    @Test
    void lockInfoReturnsDialectUnsupported() {
        var result = provider.lockInfo(damengRecord(), "pw", "db");
        assertThat(result.outcome()).isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
        assertThat(result.message())
            .contains("[diagnostics.dialect_unsupported.dameng.lock_info]");
    }

    @Test
    void poolStatusReturnsDialectUnsupported() {
        var result = provider.poolStatus(damengRecord(), "pw", "db");
        assertThat(result.outcome()).isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
    }

    @Test
    void tableSpaceReturnsDialectUnsupported() {
        var result = provider.tableSpace(damengRecord(), "pw", "db");
        assertThat(result.outcome()).isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
    }

    @Test
    void terminateSessionReturnsDialectUnsupported() {
        var result = provider.terminateSession(42L, damengRecord(), "pw", "db");
        assertThat(result.outcome()).isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
    }

    @Test
    void optimizeTableReturnsDialectUnsupported() {
        var result = provider.optimizeTable("T1", damengRecord(), "pw", "db", "SCOTT");
        assertThat(result.outcome()).isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
    }

    // ER hooks (er_inspector / er_designer) tested via the integration site;
    // if the provider exposes direct methods for ER, add the two parallel tests here.

    private static ConnectionRecord damengRecord() {
        return new ConnectionRecord(
            "id", "n", "dameng", "h", 5236, "SCOTT", "SYSDBA",
            new byte[]{}, "", 0L, 10, null, null, null, 1, true, null,
            false, null, null, null);
    }
}
```

- [ ] **Step 2: Write registration test**

```java
package com.datatalk.infra.diagnostics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DamengDiagnosticsProviderRegistrationTest {

    @Autowired
    private List<DiagnosticsProvider> providers;

    @Test
    void damengProviderIsRegistered() {
        boolean registered = providers.stream()
            .anyMatch(p -> p.supportedDriverTypes().contains("dameng"));
        assertThat(registered).as("DamengDiagnosticsProvider must be Spring-registered").isTrue();
    }
}
```

- [ ] **Step 3: Run failing tests**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=DamengDiagnostics* -q`
Expected: FAIL — `DamengDiagnosticsProvider` class not found.

- [ ] **Step 4: Implement `DamengDiagnosticsProvider`**

Create `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/DamengDiagnosticsProvider.java`:

```java
package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class DamengDiagnosticsProvider extends AbstractDiagnosticsProvider {

    public DamengDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("dameng");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of();  // Day-1: every hook returns dialect_unsupported
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn,
                                                  String decryptedPassword,
                                                  String database, String schema) {
        return dialectUnsupported("explain_real",
            "Dameng does not support explain_real in Day-1.");
    }

    @Override
    public DiagnosticResult<List<IndexHint>> indexHints(String sql, ConnectionRecord conn,
                                                        String decryptedPassword,
                                                        String database, String schema) {
        return dialectUnsupported("index_hints",
            "Dameng does not support index_hints in Day-1.");
    }

    @Override
    public DiagnosticResult<List<LockInfoEntry>> lockInfo(ConnectionRecord conn,
                                                           String decryptedPassword,
                                                           String database) {
        return dialectUnsupported("lock_info",
            "Dameng does not support lock_info in Day-1.");
    }

    @Override
    public DiagnosticResult<PoolStatus> poolStatus(ConnectionRecord conn,
                                                   String decryptedPassword,
                                                   String database) {
        return dialectUnsupported("pool_status",
            "Dameng does not support pool_status in Day-1.");
    }

    @Override
    public DiagnosticResult<List<TableSpaceEntry>> tableSpace(ConnectionRecord conn,
                                                              String decryptedPassword,
                                                              String database) {
        return dialectUnsupported("table_space",
            "Dameng does not support table_space in Day-1.");
    }

    @Override
    public DiagnosticResult<TerminateSessionResult> terminateSession(long sessionId,
                                                                      ConnectionRecord conn,
                                                                      String decryptedPassword,
                                                                      String database) {
        return dialectUnsupported("terminate_session",
            "Dameng does not support terminate_session in Day-1.");
    }

    @Override
    public DiagnosticResult<OptimizeTableResult> optimizeTable(String table,
                                                                ConnectionRecord conn,
                                                                String decryptedPassword,
                                                                String database, String schema) {
        return dialectUnsupported("optimize_table",
            "Dameng does not support optimize_table in Day-1.");
    }

    private <T> DiagnosticResult<T> dialectUnsupported(String capability, String fallback) {
        String key = "diagnostics.dialect_unsupported.dameng." + capability;
        String msg = translator.t(key);
        return DiagnosticResult.dialectUnsupported(msg.equals(key) ? fallback : msg);
    }
}
```

The `AbstractDiagnosticsProvider` parent class already exists in the diagnostics package (Day-2 plan Task 0.1/0.2 shipped). If the abstract base method signatures differ from the above (particularly for ER hooks `er_inspector` / `er_designer`), match the actual signatures verbatim — do not invent additional methods.

- [ ] **Step 5: Add i18n entries**

Append to `server/data-talk-adapter/src/main/resources/messages.properties` (en):

```
diagnostics.dialect_unsupported.dameng.lock_info=Dameng does not support lock_info in Day-1. See Wave C Day-3 candidate (dameng upgrade path).
diagnostics.dialect_unsupported.dameng.pool_status=Dameng does not support pool_status in Day-1. See Wave C Day-3 candidate (dameng upgrade path).
diagnostics.dialect_unsupported.dameng.table_space=Dameng does not support table_space in Day-1. See Wave C Day-3 candidate (dameng upgrade path).
diagnostics.dialect_unsupported.dameng.terminate_session=Dameng does not support terminate_session in Day-1. See Wave C Day-3 candidate (dameng upgrade path).
diagnostics.dialect_unsupported.dameng.optimize_table=Dameng does not support optimize_table in Day-1. See Wave C Day-3 candidate (dameng upgrade path).
diagnostics.dialect_unsupported.dameng.explain_real=Dameng does not support explain_real in Day-1. See Wave C Day-3 candidate (dameng upgrade path; new DamengTabularGrammar).
diagnostics.dialect_unsupported.dameng.index_hints=Dameng does not support index_hints in Day-1. See Wave C Day-3 candidate (deferred until type normalization lands).
diagnostics.dialect_unsupported.dameng.er_inspector=Dameng does not support ER Inspector in Day-1.
diagnostics.dialect_unsupported.dameng.er_designer=Dameng does not support ER Designer in Day-1.
risk.dialect_unsupported.dameng.plsql_block=Dameng PL/SQL blocks (DECLARE/BEGIN ... END;) are not supported in Day-1. Use a separate statement instead.
risk.dialect_unsupported.dameng.procedure_ddl=Dameng PROCEDURE / FUNCTION / TRIGGER / PACKAGE DDL is not supported in Day-1. See Wave C dameng Day-3 candidate.
risk.dialect_unsupported.dameng.exp_imp_command=Dameng EXP/IMP utility commands are not supported in Day-1. Use the Dameng SQL Workbench directly for data import/export.
connection.kind.dameng.label=Dameng (DM 8)
connection.kind.dameng.schema_placeholder=Initial schema name (optional)
connection.kind.dameng.schema_help=If left blank, connects with the default schema for the user.
```

Append to `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties` (zh) — identical keys with Chinese translations:

```
diagnostics.dialect_unsupported.dameng.lock_info=达梦 Day-1 不支持 lock_info。详见 Wave C Day-3 候选（达梦升级路径）。
diagnostics.dialect_unsupported.dameng.pool_status=达梦 Day-1 不支持 pool_status。详见 Wave C Day-3 候选（达梦升级路径）。
diagnostics.dialect_unsupported.dameng.table_space=达梦 Day-1 不支持 table_space。详见 Wave C Day-3 候选（达梦升级路径）。
diagnostics.dialect_unsupported.dameng.terminate_session=达梦 Day-1 不支持 terminate_session。详见 Wave C Day-3 候选（达梦升级路径）。
diagnostics.dialect_unsupported.dameng.optimize_table=达梦 Day-1 不支持 optimize_table。详见 Wave C Day-3 候选（达梦升级路径）。
diagnostics.dialect_unsupported.dameng.explain_real=达梦 Day-1 不支持 explain_real。详见 Wave C Day-3 候选（新建 DamengTabularGrammar）。
diagnostics.dialect_unsupported.dameng.index_hints=达梦 Day-1 不支持 index_hints。详见 Wave C Day-3 候选（待类型规范化落地后启用）。
diagnostics.dialect_unsupported.dameng.er_inspector=达梦 Day-1 不支持 ER 检查器。
diagnostics.dialect_unsupported.dameng.er_designer=达梦 Day-1 不支持 ER 设计器。
risk.dialect_unsupported.dameng.plsql_block=达梦 Day-1 不支持 PL/SQL 块（DECLARE/BEGIN ... END;）。请改用独立语句执行。
risk.dialect_unsupported.dameng.procedure_ddl=达梦 Day-1 不支持 PROCEDURE / FUNCTION / TRIGGER / PACKAGE DDL。详见 Wave C 达梦 Day-3 候选。
risk.dialect_unsupported.dameng.exp_imp_command=达梦 Day-1 不支持 EXP/IMP 工具命令。请直接使用达梦 SQL Workbench 进行数据导入导出。
connection.kind.dameng.label=达梦 (DM 8)
connection.kind.dameng.schema_placeholder=初始模式名（可选）
connection.kind.dameng.schema_help=留空时使用当前用户的默认模式。
```

- [ ] **Step 6: Run all tests**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest='DamengDiagnostics*' -q`
Expected: PASS (9 hooks unsupported test + registration test).

- [ ] **Step 7: Commit**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/DamengDiagnosticsProvider.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/DamengDiagnosticsDialectUnsupportedTest.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/DamengDiagnosticsProviderRegistrationTest.java \
        server/data-talk-adapter/src/main/resources/messages.properties \
        server/data-talk-adapter/src/main/resources/messages_zh_CN.properties
git commit -m "feat(dameng): DiagnosticsProvider all-9-hooks dialect_unsupported + i18n"
```

---

### Task 9: Manual Smoke 9-Case Script + `?schema=` vs `SET SCHEMA` Decision Lock

**Files:**
- Create: `tools/manual-smoke/dameng-day1.sh`

This task **finalizes** the deferred decision from Task 1 Step 5 (`?schema=` URL parameter vs post-connect `SET SCHEMA`). The choice is made empirically against an actual DM 8 fixture; do not guess.

- [ ] **Step 1: Write the manual smoke script header and prerequisites**

Create `tools/manual-smoke/dameng-day1.sh`:

```bash
#!/usr/bin/env bash
# tools/manual-smoke/dameng-day1.sh
#
# T2 fixture manual smoke for Dameng Day-1.
# CI does NOT run this script. Local QA acceptance only.
#
# Prerequisites:
# 1. Reachable DM 8 server (trial license or development edition).
#    Vendor portal: https://eco.dameng.com/
# 2. Environment variables:
#       export DAMENG_HOST=127.0.0.1
#       export DAMENG_PORT=5236
#       export DAMENG_USER=SYSDBA
#       export DAMENG_PASSWORD=...
#       export DAMENG_SCHEMA=SCOTT          # optional initial schema
#       export DATATALK_API=http://localhost:8080
# 3. DataTalk backend running locally on port 8080.
# 4. DataTalk frontend running locally on port 5173 (for cases 8-9 UI verification).
#
# Common connection-failure causes:
#   - case-sensitive username (DM IDENTITY_CASE_SENSITIVE=1)
#   - port firewall (5236 default; verify reachability)
#   - schema permission (initial schema requires SELECT on user objects)

set -euo pipefail

: "${DAMENG_HOST:?required}"
: "${DAMENG_PORT:=5236}"
: "${DAMENG_USER:?required}"
: "${DAMENG_PASSWORD:?required}"
: "${DAMENG_SCHEMA:=}"
: "${DATATALK_API:=http://localhost:8080}"

LOG_DIR=/home/wallfacers/project/data-talk/tmp/dameng-smoke
mkdir -p "$LOG_DIR"
LOG="$LOG_DIR/run-$(date +%s).log"
exec > >(tee -a "$LOG") 2>&1

echo "==> Dameng Day-1 manual smoke (run log: $LOG)"
```

- [ ] **Step 2: Add Case 1 — connection success + failure**

```bash
echo "[Case 1] Create dameng connection — success path"
CONN_ID=$(curl -sf -X POST "$DATATALK_API/api/connections" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"dameng-smoke\",\"kind\":\"dameng\",\"host\":\"$DAMENG_HOST\",\"port\":$DAMENG_PORT,\"username\":\"$DAMENG_USER\",\"password\":\"$DAMENG_PASSWORD\",\"databaseName\":\"$DAMENG_SCHEMA\"}" \
    | jq -r '.id')
echo "    created connectionId=$CONN_ID"

echo "[Case 1] Test connection — expect ok"
curl -sf -X POST "$DATATALK_API/api/connections/$CONN_ID/test" | jq '.'

echo "[Case 1] Connection failure — wrong password"
curl -s -X POST "$DATATALK_API/api/connections" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"dameng-bad\",\"kind\":\"dameng\",\"host\":\"$DAMENG_HOST\",\"port\":$DAMENG_PORT,\"username\":\"$DAMENG_USER\",\"password\":\"WRONG\"}" \
    | jq '.code'  # expect non-success code or error envelope
```

- [ ] **Step 3: Add Case 2-4 — schema/table/column discovery**

```bash
echo "[Case 2] Schema discovery — expect SYS/SYSDBA/SYSAUDITOR/SYSSSO/CTISYS filtered out"
curl -sf "$DATATALK_API/api/connections/$CONN_ID/schemas" | jq '.[] | .name'

echo "[Case 3] Table discovery — pick first schema"
SCHEMA=$(curl -sf "$DATATALK_API/api/connections/$CONN_ID/schemas" | jq -r '.[0].name')
curl -sf "$DATATALK_API/api/connections/$CONN_ID/schemas/$SCHEMA/tables" | jq '.[].name' | head -5

echo "[Case 4] Column discovery — pick first table"
TABLE=$(curl -sf "$DATATALK_API/api/connections/$CONN_ID/schemas/$SCHEMA/tables" | jq -r '.[0].name')
curl -sf "$DATATALK_API/api/connections/$CONN_ID/schemas/$SCHEMA/tables/$TABLE/columns" | jq '.[].name'
```

- [ ] **Step 4: Add Case 5 — L1 SELECT execution**

```bash
echo "[Case 5] L1 SELECT execution"
curl -sf -X POST "$DATATALK_API/api/sql/execute" \
    -H "Content-Type: application/json" \
    -d "{\"connectionId\":\"$CONN_ID\",\"sql\":\"SELECT 1 FROM DUAL\"}" \
    | jq '.rows'
```

- [ ] **Step 5: Add Case 6 — L2 INSERT/UPDATE**

```bash
echo "[Case 6] L2 INSERT/UPDATE — expect L2 confirmation envelope"
curl -sf -X POST "$DATATALK_API/api/sql/execute" \
    -H "Content-Type: application/json" \
    -d "{\"connectionId\":\"$CONN_ID\",\"sql\":\"UPDATE $SCHEMA.$TABLE SET col1 = col1\"}" \
    | jq '.risk'  # expect L2 in production response shape
```

- [ ] **Step 6: Add Case 7 — L3 DELETE / DROP**

```bash
echo "[Case 7] L3 DROP TABLE — expect L3 confirmation envelope"
curl -sf -X POST "$DATATALK_API/api/sql/execute" \
    -H "Content-Type: application/json" \
    -d "{\"connectionId\":\"$CONN_ID\",\"sql\":\"DROP TABLE smoke_temp_table\"}" \
    | jq '.risk'  # expect L3
echo "[Case 7] L3 DROP USER — expect dameng_admin_command label"
curl -sf -X POST "$DATATALK_API/api/sql/execute" \
    -H "Content-Type: application/json" \
    -d "{\"connectionId\":\"$CONN_ID\",\"sql\":\"DROP USER fake_user CASCADE\"}" \
    | jq '.riskLabel'  # expect dameng_admin_command
```

- [ ] **Step 7: Add Case 8 — `dialect_unsupported` rejection**

```bash
echo "[Case 8] PL/SQL block — expect dialect_unsupported"
curl -s -X POST "$DATATALK_API/api/sql/execute" \
    -H "Content-Type: application/json" \
    -d "{\"connectionId\":\"$CONN_ID\",\"sql\":\"DECLARE v INT; BEGIN v := 1; END;\"}" \
    | jq '.code, .message'  # expect DIALECT_UNSUPPORTED + i18n message

echo "[Case 8] CREATE PROCEDURE — expect dialect_unsupported"
curl -s -X POST "$DATATALK_API/api/sql/execute" \
    -H "Content-Type: application/json" \
    -d "{\"connectionId\":\"$CONN_ID\",\"sql\":\"CREATE PROCEDURE p1 AS BEGIN NULL; END;\"}" \
    | jq '.code, .message'

echo "[Case 8] EXP utility — expect dialect_unsupported"
curl -s -X POST "$DATATALK_API/api/sql/execute" \
    -H "Content-Type: application/json" \
    -d "{\"connectionId\":\"$CONN_ID\",\"sql\":\"EXP scott/tiger@dm FILE=demo.dmp\"}" \
    | jq '.code, .message'
```

- [ ] **Step 8: Add Case 9 — i18n key resolution**

```bash
echo "[Case 9] i18n key resolution — verify en + zh"
for LANG in en-US zh-CN; do
    echo "  language=$LANG"
    curl -sf -X POST "$DATATALK_API/api/sql/execute" \
        -H "Content-Type: application/json" \
        -H "Accept-Language: $LANG" \
        -d "{\"connectionId\":\"$CONN_ID\",\"sql\":\"BEGIN NULL; END;\"}" \
        | jq '.message'
done

echo "==> Dameng Day-1 smoke COMPLETE"
```

- [ ] **Step 9: Run the manual smoke against a local DM 8 fixture; LOCK `?schema=` decision**

Bring up a DM 8 fixture (Docker image from vendor mirror per spec §12.1, or local install). Run:

```bash
chmod +x tools/manual-smoke/dameng-day1.sh
DAMENG_HOST=127.0.0.1 DAMENG_USER=SYSDBA DAMENG_PASSWORD=Sysdba_001 \
    DAMENG_SCHEMA=SCOTT tools/manual-smoke/dameng-day1.sh
```

Expected: All 9 cases pass; log saved under `tmp/dameng-smoke/`.

**Lock the `?schema=` vs `SET SCHEMA` decision** by manually probing the DM 8 driver:

```bash
# Quick java probe (one-off; do not commit)
cat <<'EOF' > /tmp/dameng-schema-probe.java
import java.sql.*;
public class probe {
    public static void main(String[] a) throws Exception {
        Class.forName("dm.jdbc.driver.DmDriver");
        // Try URL parameter
        try (Connection c = DriverManager.getConnection(
                "jdbc:dm://" + a[0] + ":" + a[1] + "?schema=" + a[4],
                a[2], a[3])) {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM DUAL")) {
                rs.next();
                System.out.println("URL ?schema= result: " + rs.getString(1));
            }
        } catch (Exception e) {
            System.out.println("URL ?schema= REJECTED: " + e.getMessage());
        }
    }
}
EOF
# Compile + run with classpath including DmJdbcDriverX
```

If the URL parameter is accepted by DM 8 and the resulting `CURRENT_SCHEMA` matches the requested schema → **switch** the Task 4 implementation: change `JdbcUrlBuilder.DAMENG` branch to embed `?schema=<dbName>` and remove the `executeUpdate("SET SCHEMA ...")` call from `ConnectionService.openConnection`. If rejected → keep the post-connect `SET SCHEMA` fallback (current Day-1 default).

Update the spec at the end of Task 13 (housekeeping) with the locked decision.

- [ ] **Step 10: Do NOT commit smoke logs**

Per CLAUDE.md tmp/ rule: do not `git add` `tmp/dameng-smoke/`. Only commit the `tools/manual-smoke/dameng-day1.sh` script itself.

```bash
git add tools/manual-smoke/dameng-day1.sh
git commit -m "test(dameng): manual smoke 9-case script + ?schema= decision lock"
```

---

### Task 10: Frontend — Dameng Connection Fields + Picker + Formatter Routing

**Files:**
- Create: `client/src/features/settings/data-sources/dameng-connection-fields.tsx`
- Create: `client/src/features/settings/data-sources/__tests__/dameng-connection-fields.test.tsx`
- Modify: `client/src/features/settings/data-sources/connection-form-dialog.tsx`
- Modify: `client/src/features/settings/data-sources/data-sources-page.tsx`
- Modify: `client/src/features/stage/utils/format-sql.ts`
- Modify: `client/src/i18n/messages.ts`

**Design Inputs (mandatory per CLAUDE.md Frontend Design Contract Gate; references `client/DESIGN.md`):**

The 5-state semantic-token matrix for every interactive control introduced by this task is enumerated below. **No abbreviation per memory record `feedback-design-control-states.md`.**

| Control | default | hover | focus | active (selected/pressed) | disabled |
|---|---|---|---|---|---|
| Kind picker row "Dameng (DM 8)" | `bg.panel`, `border.default`, `text.strong` | `interaction.hover` | `interaction.focusRing` | `interaction.selected`, `accent.primary` text | `interaction.disabled`, `text.muted` |
| Host input | `bg.panel`, `border.default` | `interaction.hover` | `interaction.focusRing` | `interaction.active` | `interaction.disabled`, `text.muted` |
| Port input (default 5236) | `bg.panel`, `border.default` | `interaction.hover` | `interaction.focusRing` | `interaction.active` | `interaction.disabled`, `text.muted` |
| Schema input (`databaseName` reused; placeholder "Initial schema name (optional)") | `bg.panel`, `border.default`, `text.muted` for placeholder | `interaction.hover` | `interaction.focusRing` | `interaction.active` | `interaction.disabled`, `text.muted` |
| Save button | `bg.panel` filled with `accent.primary`, `text.onAccent` | `interaction.hover` | `interaction.focusRing` | `interaction.active` | `interaction.disabled`, `text.muted` |
| `dialect_unsupported` chip (rendered when SQL editor receives a Channel-2 hit) | `feedback.warning.bg`, `feedback.warning.border`, `text.warning` | `interaction.hover` | `interaction.focusRing` | n/a (non-clickable) | `interaction.disabled` if dialog closed |

Light + dark theme parity is automatic via semantic tokens (per `client/DESIGN.md` §"Semantic Tokens"). Stage state is global (not per-session). i18n keys are listed in spec §12.4 and Task 8 Step 5.

- [ ] **Step 1: Re-read `client/DESIGN.md` semantic-token list**

Re-confirm token names referenced above (`bg.panel`, `border.default`, `interaction.focusRing`, `interaction.hover`, `interaction.active`, `interaction.selected`, `interaction.disabled`, `accent.primary`, `text.onAccent`, `text.muted`, `text.strong`, `feedback.warning.bg`, `feedback.warning.border`, `text.warning`) exist in the live token contract. If any token name has shifted, adjust the matrix above and the implementation accordingly before writing code.

- [ ] **Step 2: Write failing component test**

```typescript
// client/src/features/settings/data-sources/__tests__/dameng-connection-fields.test.tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { DamengConnectionFields } from "../dameng-connection-fields";

describe("DamengConnectionFields", () => {
    it("renders default port 5236 and optional schema placeholder", () => {
        render(<DamengConnectionFields
            value={{ host: "", port: 5236, username: "", password: "", databaseName: "" }}
            onChange={() => {}}
        />);
        const portInput = screen.getByLabelText(/Port/i) as HTMLInputElement;
        expect(portInput.value).toBe("5236");
        const schemaInput = screen.getByLabelText(/Schema/i) as HTMLInputElement;
        expect(schemaInput.placeholder).toMatch(/optional/i);
    });

    it("schema field has aria-describedby pointing at the help text", () => {
        render(<DamengConnectionFields
            value={{ host: "", port: 5236, username: "", password: "", databaseName: "" }}
            onChange={() => {}}
        />);
        const schemaInput = screen.getByLabelText(/Schema/i);
        const describedBy = schemaInput.getAttribute("aria-describedby");
        expect(describedBy).toBeTruthy();
        expect(document.getElementById(describedBy!)).toBeInTheDocument();
    });

    it("invokes onChange when user types into host field", () => {
        const onChange = vi.fn();
        render(<DamengConnectionFields
            value={{ host: "", port: 5236, username: "", password: "", databaseName: "" }}
            onChange={onChange}
        />);
        const hostInput = screen.getByLabelText(/Host/i);
        fireEvent.change(hostInput, { target: { value: "10.0.0.1" } });
        expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ host: "10.0.0.1" }));
    });
});
```

- [ ] **Step 3: Run failing test**

Run: `cd client && npx vitest run dameng-connection-fields`
Expected: FAIL — module `dameng-connection-fields` not found.

- [ ] **Step 4: Implement `DamengConnectionFields`**

Create `client/src/features/settings/data-sources/dameng-connection-fields.tsx`:

```tsx
import { type FC } from "react";
import { useT } from "@/i18n/hooks";

export interface DamengConnectionValue {
    host: string;
    port: number;
    username: string;
    password: string;
    databaseName: string;  // initial schema name (reused per spec §6.3 + §7.3)
}

interface Props {
    value: DamengConnectionValue;
    onChange: (next: DamengConnectionValue) => void;
}

export const DamengConnectionFields: FC<Props> = ({ value, onChange }) => {
    const t = useT();
    const helpId = "dameng-schema-help";
    return (
        <div className="flex flex-col gap-3">
            <label className="flex flex-col gap-1">
                <span className="text-sm text-[var(--color-text-strong)]">
                    {t("connection.field.host")}
                </span>
                <input
                    type="text"
                    aria-label={t("connection.field.host")}
                    value={value.host}
                    onChange={(e) => onChange({ ...value, host: e.target.value })}
                    className="px-3 py-2 rounded
                              bg-[var(--color-bg-panel)]
                              border border-[var(--color-border-default)]
                              hover:bg-[var(--color-interaction-hover)]
                              focus:outline-none focus:ring-2 focus:ring-[var(--color-interaction-focus-ring)]
                              disabled:opacity-50 disabled:cursor-not-allowed
                              disabled:bg-[var(--color-interaction-disabled)]
                              disabled:text-[var(--color-text-muted)]"
                />
            </label>

            <label className="flex flex-col gap-1">
                <span className="text-sm text-[var(--color-text-strong)]">
                    {t("connection.field.port")}
                </span>
                <input
                    type="number"
                    aria-label={t("connection.field.port")}
                    value={value.port}
                    onChange={(e) => onChange({ ...value, port: Number(e.target.value) })}
                    className="px-3 py-2 rounded
                              bg-[var(--color-bg-panel)]
                              border border-[var(--color-border-default)]
                              hover:bg-[var(--color-interaction-hover)]
                              focus:outline-none focus:ring-2 focus:ring-[var(--color-interaction-focus-ring)]
                              disabled:opacity-50 disabled:cursor-not-allowed"
                />
            </label>

            <label className="flex flex-col gap-1">
                <span className="text-sm text-[var(--color-text-strong)]">
                    {t("connection.field.username")}
                </span>
                <input
                    type="text"
                    aria-label={t("connection.field.username")}
                    value={value.username}
                    onChange={(e) => onChange({ ...value, username: e.target.value })}
                    className="px-3 py-2 rounded
                              bg-[var(--color-bg-panel)]
                              border border-[var(--color-border-default)]
                              hover:bg-[var(--color-interaction-hover)]
                              focus:outline-none focus:ring-2 focus:ring-[var(--color-interaction-focus-ring)]"
                />
            </label>

            <label className="flex flex-col gap-1">
                <span className="text-sm text-[var(--color-text-strong)]">
                    {t("connection.field.password")}
                </span>
                <input
                    type="password"
                    aria-label={t("connection.field.password")}
                    value={value.password}
                    onChange={(e) => onChange({ ...value, password: e.target.value })}
                    className="px-3 py-2 rounded
                              bg-[var(--color-bg-panel)]
                              border border-[var(--color-border-default)]
                              hover:bg-[var(--color-interaction-hover)]
                              focus:outline-none focus:ring-2 focus:ring-[var(--color-interaction-focus-ring)]"
                />
            </label>

            <label className="flex flex-col gap-1">
                <span className="text-sm text-[var(--color-text-strong)]">
                    {t("connection.field.schema")}
                </span>
                <input
                    type="text"
                    aria-label={t("connection.field.schema")}
                    aria-describedby={helpId}
                    placeholder={t("connection.kind.dameng.schema_placeholder")}
                    value={value.databaseName}
                    onChange={(e) => onChange({ ...value, databaseName: e.target.value })}
                    className="px-3 py-2 rounded
                              bg-[var(--color-bg-panel)]
                              border border-[var(--color-border-default)]
                              placeholder:text-[var(--color-text-muted)]
                              hover:bg-[var(--color-interaction-hover)]
                              focus:outline-none focus:ring-2 focus:ring-[var(--color-interaction-focus-ring)]"
                />
                <span id={helpId} className="text-xs text-[var(--color-text-muted)]">
                    {t("connection.kind.dameng.schema_help")}
                </span>
            </label>
        </div>
    );
};
```

- [ ] **Step 5: Add i18n keys**

Append to `client/src/i18n/messages.ts`:

```typescript
"connection.kind.dameng.label": { en: "Dameng (DM 8)", "zh-CN": "达梦 (DM 8)" },
"connection.kind.dameng.schema_placeholder": {
    en: "Initial schema name (optional)",
    "zh-CN": "初始模式名（可选）"
},
"connection.kind.dameng.schema_help": {
    en: "If left blank, connects with the default schema for the user.",
    "zh-CN": "留空时使用当前用户的默认模式。"
},
"risk.dialect_unsupported.dameng.plsql_block": {
    en: "Dameng PL/SQL blocks (DECLARE/BEGIN ... END;) are not supported in Day-1.",
    "zh-CN": "达梦 Day-1 不支持 PL/SQL 块（DECLARE/BEGIN ... END;）。"
},
"risk.dialect_unsupported.dameng.procedure_ddl": {
    en: "Dameng PROCEDURE / FUNCTION / TRIGGER / PACKAGE DDL is not supported in Day-1.",
    "zh-CN": "达梦 Day-1 不支持 PROCEDURE / FUNCTION / TRIGGER / PACKAGE DDL。"
},
"risk.dialect_unsupported.dameng.exp_imp_command": {
    en: "Dameng EXP/IMP utility commands are not supported in Day-1.",
    "zh-CN": "达梦 Day-1 不支持 EXP/IMP 工具命令。"
},
```

(Plus the 9 `diagnostics.dialect_unsupported.dameng.*` keys per Task 8 Step 5 — frontend mirrors the backend i18n bundle.)

- [ ] **Step 6: Run vitest, verify pass**

Run: `cd client && npx vitest run dameng-connection-fields`
Expected: PASS (3/3).

- [ ] **Step 7: Wire into `connection-form-dialog.tsx`**

Edit `client/src/features/settings/data-sources/connection-form-dialog.tsx`:

```tsx
// inside the kind switch
{kind === "dameng" && (
    <DamengConnectionFields value={damengValue} onChange={setDamengValue} />
)}
```

The `databaseName` field is **optional** for dameng (unlike PostgreSQL / kingbase where it is required). Form submission must not block on empty `databaseName`.

- [ ] **Step 8: Add Dameng entry to picker in `data-sources-page.tsx`**

Add a new picker row labeled `Dameng (DM 8)` (en) / `达梦 (DM 8)` (zh) routed by i18n key `connection.kind.dameng.label`. The picker row is **independent** from the Oracle row (per spec §5: dameng MUST NOT be displayed as oracle).

- [ ] **Step 9: Add formatter routing in `format-sql.ts`**

Edit `client/src/features/stage/utils/format-sql.ts`:

```typescript
// inside the kind switch in format-sql.ts
case "dameng":
    // Reuse Oracle / generic SQL formatter (95%+ Oracle-compatible syntax)
    return formatOracleSql(input);
```

If the existing Oracle formatter is generic, route through the same path. No dameng-specific formatter is introduced Day-1.

- [ ] **Step 10: Run typecheck**

Run: `cd client && npx tsc --noEmit`
Expected: 0 errors.

- [ ] **Step 11: Commit**

```bash
git add client/src/features/settings/data-sources/dameng-connection-fields.tsx \
        client/src/features/settings/data-sources/__tests__/dameng-connection-fields.test.tsx \
        client/src/features/settings/data-sources/connection-form-dialog.tsx \
        client/src/features/settings/data-sources/data-sources-page.tsx \
        client/src/features/stage/utils/format-sql.ts \
        client/src/i18n/messages.ts
git commit -m "feat(dameng): frontend connection fields + picker + formatter routing"
```

---

### Task 11: MCP Enum + AGENTS.md (Last Backend Step, Post-Verify)

**⚠️ Hard timing constraint per umbrella §7.5: this task runs ONLY AFTER Tasks 2-10 all pass `mvn verify` SUCCESS + manual smoke 9-case SUCCESS.**

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java`
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`

- [ ] **Step 1: Run consolidated pre-flight verification**

Run:
```bash
cd server && mvn clean verify -q
```
Expected: BUILD SUCCESS.

Run:
```bash
cd client && npx tsc --noEmit && npx vitest run
```
Expected: 0 type errors; all vitest tests pass.

Confirm Task 9 manual smoke 9-case SUCCESS log exists under `tmp/dameng-smoke/`.

**DO NOT proceed if any of the above fails.** AGENTS.md / MCP enum landing before verify-success silently exposes an unsupported kind to the AI runtime, violating umbrella §7.5.

- [ ] **Step 2: Add `DAMENG` to `ConnectionObjectType` enum**

Edit `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java`:

```java
public enum ConnectionObjectType {
    MYSQL("mysql"),
    POSTGRESQL("postgresql"),
    /* ... existing kinds ... */
    OCEANBASE("oceanbase"),
    DAMENG("dameng");

    // ... existing constructor and wireValue() pattern preserved ...
}
```

- [ ] **Step 3: Add Dameng section to `AGENTS.md`**

Edit `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`. Append:

```markdown
### Dameng (kind: `dameng`)

- canonical kind: `dameng` (lower-case). **No aliases**: `dm`, `dm8`, `DM`,
  `DM8`, `DM7`, `dameng7`, `dameng8`, `武汉达梦`, `达梦` are all rejected at
  `ConnectionKind.normalize`.
- driver: `com.dameng:DmJdbcDriverX:8.1.x` (Maven Central direct; commercial
  license; offline jar in repo is **forbidden** by Wave C umbrella §10).
- driver class: `dm.jdbc.driver.DmDriver`.
- URL: `jdbc:dm://<host>:<port>` (server-level; **no `/<database>` suffix**;
  default port 5236).
- `databaseName` field is reused as the **initial schema name** (Oracle-precedent;
  not a separate database). Schema is injected post-connect via
  `SET SCHEMA <name>` (or `?schema=<name>` URL parameter, finalized by child
  plan Task 9 fixture verification — see plan changelog).
- Day-1 unsupported (returns structured `dialect_unsupported`):
  - PL/SQL blocks (`DECLARE...BEGIN...END;`)
  - PROCEDURE / FUNCTION / TRIGGER / PACKAGE DDL
  - EXP / IMP CLI utility commands
- Day-1 L3 admin commands (under risk label `dameng_admin_command`):
  TABLESPACE / USER / ROLE DDL, GRANT / REVOKE, DROP TABLE/VIEW/INDEX/SEQUENCE/SYNONYM.
- All 9 diagnostics + ER hooks (`lock_info`, `pool_status`, `table_space`,
  `terminate_session`, `optimize_table`, `explain_real`, `index_hints`,
  `er_inspector`, `er_designer`) return structured `dialect_unsupported`
  Day-1.
- Chinese aliases (`达梦`, `武汉达梦`) are recognized in user natural-language
  input only; they map to canonical kind `dameng`, NOT alias normalization at
  the `ConnectionKind.normalize` boundary (umbrella §7.5).
- Dameng is **not** an alias of Oracle. AI must not persist or display dameng
  connections as `oracle` in any code path.
```

- [ ] **Step 4: Re-run mvn verify**

Run: `cd server && mvn verify -q`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java \
        server/data-talk-adapter/src/main/resources/agents/AGENTS.md
git commit -m "feat(dameng): MCP enum + AGENTS.md (post-verify per umbrella §7.5)"
```

---

### Task 12: Consolidated Verification + Manual Smoke Re-Run

- [ ] **Step 1: Full mvn verify**

Run: `cd server && mvn clean verify -q`
Expected: BUILD SUCCESS. All Dameng-specific unit tests + Oracle / MySQL existing tests pass.

- [ ] **Step 2: Full client typecheck + vitest**

Run: `cd client && npx tsc --noEmit && npx vitest run`
Expected: 0 type errors; all vitest tests pass.

- [ ] **Step 3: Start server + client locally**

Open two terminals.
Terminal 1: `cd server && mvn spring-boot:run -pl data-talk-adapter`
Terminal 2: `cd client && npm run dev`

- [ ] **Step 4: Manual smoke — create Dameng connection, alias rejection**

In the running app:
1. Open Connections, click "+", select Dameng (DM 8). Verify the picker row label is `Dameng (DM 8)` (en) / `达梦 (DM 8)` (zh).
2. Try entering kind=`dm` or `dm8` via API curl (DevTools network tab):
   ```bash
   curl -X POST http://localhost:8080/api/connections \
       -H "Content-Type: application/json" \
       -d '{"name":"x","kind":"dm","host":"h","port":5236,"username":"u","password":"p"}'
   ```
   Expected: error envelope `code=UNKNOWN_CONNECTION_KIND`.
3. Fill the form: host=127.0.0.1, port=5236, user=SYSDBA, password=..., schema=SCOTT (optional).
4. Click Test — expect SUCCESS.
5. Save.

- [ ] **Step 5: Manual smoke — discovery filters 5 system schemas**

1. Pick the new connection. Browse schemas list.
2. Verify SYS / SYSDBA / SYSAUDITOR / SYSSSO / CTISYS are NOT shown in the visible list.

- [ ] **Step 6: Manual smoke — risk classifier L3 + dameng_admin_command label**

1. Run `DROP USER fake_user CASCADE;` — expect L3 confirmation prompt with risk label `dameng_admin_command`.
2. Run `CREATE TABLESPACE ts1 DATAFILE 'ts1.dbf' SIZE 100M;` — expect L3.
3. Run `GRANT SELECT ON t1 TO scott;` — expect L3.

- [ ] **Step 7: Manual smoke — Channel 2 dialect_unsupported**

1. Run `DECLARE v INT; BEGIN v := 1; END;` — expect dialect_unsupported response with i18n key `risk.dialect_unsupported.dameng.plsql_block` resolved.
2. Run `CREATE PROCEDURE p1 AS BEGIN NULL; END;` — expect dialect_unsupported.
3. Run `EXP scott/tiger@dm FILE=demo.dmp` — expect dialect_unsupported.

- [ ] **Step 8: Manual smoke — diagnostics dialect_unsupported + ER hooks**

1. Open ER Inspector for the Dameng connection — expect "Dameng does not support ER Inspector in Day-1." message.
2. Open Lock Info diagnostics — expect dialect_unsupported with i18n message.

- [ ] **Step 9: Do not commit smoke logs**

Per CLAUDE.md tmp/ rule: smoke run logs in `tmp/dameng-smoke/` are NOT committed. If everything passes, no commit needed in this step.

---

### Task 13: Documentation Housekeeping

**Files:**
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` — Current Support Snapshot row
- Modify: `docs/exec-plans/index.md` — move from Active to Completed
- Modify: `docs/product-specs/2026-05-08-data-source-coverage-dameng-design.md` — backfill final pinned driver patch + `?schema=` decision
- Modify: `docs/exec-plans/2026-05-08-diagnostics-day2-plan.md` — verify §Day-3 dameng row anchor (no backfill expected)

- [ ] **Step 1: Update `DATA_SOURCE_TYPE_COMPATIBILITY.md` Snapshot**

Add `dameng` to the "First-class Day-1" row with summary of decisions:

```
| `dameng` | First-class Day-1 (DM 8) | driver com.dameng:DmJdbcDriverX@<pinned-patch> (Maven Central direct; commercial license; no offline jar), URL jdbc:dm://<host>:<port> port 5236 (no /<db> suffix), `databaseName` field reused as initial schema name (Oracle-precedent), Oracle splitter / metadata / normalizer reuse via 3 kind-private equivalence tests (DamengSplitterEquivalenceTest / DamengMetadataEquivalenceTest / DamengResultNormalizationEquivalenceTest), Risk classifier dual-channel (5 anchored L3 patterns + 3 anchored dialect_unsupported patterns), all 9 diagnostics hooks dialect_unsupported, no Flyway migration, no new ConnectionRecord columns, no multi-mode. T2 fixture: manual smoke 9-case + JDBC mock unit tests; CI does not start DM server. PL/SQL blocks + PROCEDURE/FUNCTION/TRIGGER/PACKAGE DDL + EXP/IMP commands all dialect_unsupported Day-1. |
```

- [ ] **Step 2: Verify day2 plan §Day-3 dameng row anchor still matches**

Run:
```bash
cd /home/wallfacers/project/data-talk && grep -n 'dameng' docs/exec-plans/2026-05-08-diagnostics-day2-plan.md | head -10
```
Expected: dameng row near line 3121 already mentions `EXPLAIN / 新建 DamengTabularGrammar` and explicitly defers `INDEX_HINTS` (per spec §11.2). **No backfill needed.** If somehow the row was modified upstream and no longer contains the expected text, file a follow-up issue and STOP this step (do not silently rewrite day2 plan).

- [ ] **Step 3: Backfill final pinned driver patch + `?schema=` decision to spec**

Edit `docs/product-specs/2026-05-08-data-source-coverage-dameng-design.md`:
- §6.1 row "Driver artifact": replace `8.1.x` placeholder with the exact patch pinned by Task 1 Step 2.
- §6.2 "Optional URL parameters": replace the deferred decision text with the locked outcome from Task 9 Step 9 — either "DM 8 driver accepts `?schema=<name>` URL parameter; URL embeds `?schema=` directly" or "DM 8 driver rejects `?schema=` URL parameter; post-connect `executeUpdate(\"SET SCHEMA ...\")` is the canonical injection path".
- §7.2 system schema list: append any additional Dameng system schemas discovered during Task 9 fixture run (vendor-edition extensions, if any).

- [ ] **Step 4: Move plan from Active to Completed in exec-plans index**

Edit `docs/exec-plans/index.md`: move the dameng plan entry from Active to Completed; add a one-line Completion Log: `Completed YYYY-MM-DD; commits: <list>; verified: mvn verify + manual smoke 9-case + frontend tsc + vitest`.

- [ ] **Step 5: Mark every checkbox in this plan as `[x]`**

Run:
```bash
sed -i 's/^- \[ \]/- [x]/g' docs/exec-plans/2026-05-08-data-source-coverage-dameng-plan.md
```

- [ ] **Step 6: Commit final housekeeping**

```bash
git add docs/DATA_SOURCE_TYPE_COMPATIBILITY.md \
        docs/exec-plans/index.md \
        docs/product-specs/2026-05-08-data-source-coverage-dameng-design.md \
        docs/exec-plans/2026-05-08-data-source-coverage-dameng-plan.md
git commit -m "docs(dameng): housekeeping — Snapshot upgrade + plan completion"
```

---

## Self-Review

**1. Spec coverage**

- §1 Purpose → Tasks 1 (approval gate / driver pin) + 6 (dual-channel risk classifier) + 8 (DiagnosticsProvider 9 hooks) + 13 (housekeeping)
- §2 Compatibility Gate Application → Tasks 2-12 cover every database area listed (kind / URL / driver / connection test / metadata / target resolution / SQL execution / result normalization / splitter / risk guard / diagnostics / localized errors / frontend form / picker / formatter / outline / accessibility / semantic tokens / i18n / MCP enum / AGENTS.md / ER hooks)
- §3 Design Inputs → Task 1 Step 1 (read full spec) + Task 13 Step 3 (backfill)
- §4 Support Statement → Tasks 2-10 implement the 12-bullet support list verbatim (CRUD connection / discovery 4 ops / schema fallback / SELECT-DML-DDL guarded / generic splitter equivalence / 5 anchored L3 patterns / Oracle baseline normalizer equivalence / 9 diagnostics dialect_unsupported / MCP+AGENTS.md post-verify / alias rejection)
- §5 Kind Naming → Task 2 (normalize() rules + alias rejection test) + Task 11 Step 3 (AGENTS.md canonicalization clause)
- §6 Connection And Persistence → Tasks 1 (driver gate / no Flyway needed) + 3 (Maven dep + URL builder) + 4 (ConnectionRecord field reuse + ConnectionService schema injection) + 11 (AGENTS.md no-aliases recap)
- §7 Metadata Discovery → Task 5 Step 2 (oracleDiscovery routing + 5-item DAMENG_SYSTEM_SCHEMAS filter) + Task 7 Step 2 (DamengMetadataEquivalenceTest 4 cases)
- §8 SQL Execution / Splitter / Risk Classifier → Task 5 (splitter routing + Stage 1 entry) + Task 6 (dual-channel: 5 anchored L3 patterns Channel 1 + 3 anchored dialect_unsupported Channel 2) + Task 7 Step 1 (DamengSplitterEquivalenceTest 5 cases) + boundary tests in Task 6 Step 1
- §9 Diagnostics Provider — 9 hooks Matrix → Task 8 (provider class + all-9-hooks dialect_unsupported test + i18n keys verbatim from §9.3)
- §10 Reuse Outputs (None) → embedded in plan structure: no MultiModeConnectionShape consumption, no shared abstract base, no concrete IT subclasses, no Flyway migration; only kind-private equivalence tests in Task 7
- §11 Day-2 / Day-3 Upgrade Path — Bidirectional Anchor → Task 1 Step 4 (verify day2 plan dameng row already complete) + Task 13 Step 2 (final anchor verification)
- §12 Out-of-Scope / T2 Fixture / i18n / AGENTS.md Timing → Task 8 Step 5 (i18n verbatim from §12.4) + Task 9 (T2 manual smoke 9-case fixture; no Testcontainers) + Task 11 (AGENTS.md timing post-verify per §12.5) + Task 12 Step 9 (no smoke log commit per CLAUDE.md tmp/ rule)

All 12 spec sections covered.

**2. Placeholder scan**

No "TBD", "TODO", "implement later", "fill in details" appear in steps. Risk classifier wiring code in Task 6 Step 3 is concrete (5+3 anchored patterns + nested enum + classify methods + main classify wiring). Discovery Task 5 Step 2 includes the full 5-item system schema filter set verbatim from spec §7.2. Frontend tests in Task 10 Step 2 are concrete with assertion code, not stubs. Manual smoke 9 cases in Task 9 are concrete bash + curl + jq, not placeholders. The `?schema=` vs `SET SCHEMA` decision is explicitly deferred to Task 9 fixture verification with a default fallback (`SET SCHEMA`) implemented in Task 4 — this is a documented decision-lock pattern, not a placeholder.

**3. Type consistency**

- `ConnectionKind.DAMENG` enum value consistent across Tasks 2 / 3 / 4 / 5 / 6 / 11.
- `ConnectionRecord` 22-field constructor consistent across Tasks 4 / 7 / 8 (test fixture builders use 22 args; for dameng rows: `compatibilityMode = null`, `oceanbaseTenant = null`, `oceanbaseCluster = null`).
- `DamengUnsupportedReason` nested enum (`PLSQL_BLOCK`, `PROCEDURE_DDL`, `EXP_IMP_COMMAND`) consistent across Task 5 (Stage 1 entry switch) + Task 6 (definition + detect method) + i18n key suffixes in Task 8 Step 5 (`plsql_block`, `procedure_ddl`, `exp_imp_command`).
- 5 Channel 1 Pattern constant names (`DAMENG_TABLESPACE_DDL`, `DAMENG_USER_DDL`, `DAMENG_ROLE_DDL`, `DAMENG_GRANT_REVOKE`, `DAMENG_DROP_OBJECT`) match Task 6 Step 3 + spec §8.3 verbatim.
- 3 Channel 2 Pattern constant names (`DAMENG_PLSQL_BLOCK`, `DAMENG_PROCEDURE_DDL`, `DAMENG_EXP_IMP`) match Task 6 Step 3 + spec §8.3 verbatim.
- 5-item `DAMENG_SYSTEM_SCHEMAS` filter set (`SYS`, `SYSDBA`, `SYSAUDITOR`, `SYSSSO`, `CTISYS`) matches Task 5 Step 2 + spec §7.2 verbatim.
- 9 i18n keys in Task 8 Step 5 match spec §12.4 verbatim (7 diagnostics + 2 ER + 3 risk + 3 connection-form).
- 3 kind-private equivalence test class names (`DamengSplitterEquivalenceTest`, `DamengMetadataEquivalenceTest`, `DamengResultNormalizationEquivalenceTest`) match Task 7 + spec §7.4 / §8.5 / §10 verbatim.
- Risk label aggregation `dameng_admin_command` consistent across Task 6 Step 3 (wiring) + spec §8.3.
- 5-state semantic-token matrix in Task 10 Design Inputs explicitly enumerates 6 controls × 5 states (no abbreviation per memory record `feedback-design-control-states.md`).
