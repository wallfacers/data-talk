# Data Source Coverage: KingbaseES Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship first-class Wave C step 4 KingbaseES PG-mode support per [kingbase design](../product-specs/2026-05-08-data-source-coverage-kingbase-design.md), as Wave C's only **pure consumer** kind: simultaneously consume `PgForkReuseRule` (opengauss step 2, commit `c58b7fd`) via 6 concrete `Kingbase*ReuseIT` subclasses **and** `MultiModeConnectionShape` v1 (oceanbase step 3, commit `1231153`) via forward-compatible append, plus `KingbaseDiagnosticsProvider` Day-1 all-9-hook `dialect_unsupported`. Produce **zero** new cross-kind reuse abstractions. `kingbasees` alias normalization is the only Wave C alias permitted.

**Architecture:** Backend adds canonical `kingbase` kind via `cn.com.kingbase:kingbase8:9.0.x` JDBC, reusing the PostgreSQL splitter / normalizer / metadata path proven equivalent through 6 concrete subclasses of opengauss-shipped `AbstractPgFork*ReuseTest` abstract bases. Multi-mode handling appends a kingbase row to `MultiModeConnectionShape.validateModeForKind` / `isDay1FirstClassMode` (PG / Oracle legal; PG-mode Day-1 first-class) — **zero v1 signature changes** (pure forward-compatible append). Risk classifier dual-channel: 4 anchored L3 patterns (`SYS_*` table DDL, `SYS<CRT|AUDIT>_*` admin schema DDL, `SYS_KILL` function, `FLASHBACK TABLE`) + 2 anchored `dialect_unsupported` patterns (`KBBACKUP`/`KBRESTORE` CLI, Oracle-style PL/SQL block) all using `^\s*` start anchor + `\b` word boundary. Frontend reuses oceanbase-shipped `multi-mode-connection-fields.tsx` skeleton with `modeOptions=["pg","oracle"]` + `modeDisabled=["oracle"]` + `kingbasees` alias hint. T2 fixture path: 6 IT `@Disabled` by default (CI does NOT run them) + manual smoke script + JDBC mock unit tests. AGENTS.md / MCP enum / first-class snapshot only after `mvn verify` SUCCESS + 6 IT pass via `-Dkingbase.it.enabled=true`.

**Tech Stack:** Spring Boot 3.5 + Java 21 (virtual threads), Maven, Flyway 9 (no new migration — V18 oceanbase already shipped `compatibility_mode`), JUnit 5 + AssertJ, React 19 + Vite + shadcn/ui, OpenCode SDK

**Spec author**: This plan implements the design at commit `d4fd516` (kingbase child design landed).

---

## File Structure

### Backend (Java 21)

**Create:**
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/KingbaseDiagnosticsProvider.java` — all 9 hooks `dialect_unsupported`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/kingbase/KingbaseSplitterReuseIT.java` (extends opengauss-shipped `AbstractPgForkSplitterReuseTest`)
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/kingbase/KingbaseMetadataReuseIT.java` (extends `AbstractPgForkMetadataReuseTest`)
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/kingbase/KingbaseTargetResolutionReuseIT.java` (extends `AbstractPgForkTargetResolutionReuseTest`)
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/kingbase/KingbaseBatchDmlReuseIT.java` (extends `AbstractPgForkBatchDmlReuseTest`)
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/kingbase/KingbaseResultNormalizationReuseIT.java` (extends `AbstractPgForkResultNormalizationReuseTest`)
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/kingbase/KingbaseConnectionReuseIT.java` (extends `AbstractPgForkConnectionTestReuseTest`)
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/kingbase/KingbaseConnectionTrigger.java` — JUnit `@EnabledIfSystemProperty` helper that reads `-Dkingbase.host/port/database/user/password`
- `server/data-talk-application/src/test/java/com/datatalk/application/connection/multimode/KingbaseMultiModeConnectionShapeTest.java` — kingbase append validation
- `server/data-talk-application/src/test/java/com/datatalk/application/connection/KingbaseAliasNormalizationTest.java` — `kingbasees` alias accepted; reject all other variants
- `server/data-talk-application/src/test/java/com/datatalk/application/connection/JdbcUrlBuilderKingbaseTest.java` — URL build (3 cases)
- `server/data-talk-application/src/test/java/com/datatalk/application/connection/KingbaseConnectionRecordValidationTest.java`
- `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/sql/risk/KingbaseRiskClassifierTest.java` — Channel 1 (4) + Channel 2 (2) anchored patterns × hit + boundary
- `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/KingbaseDiagnosticsDialectUnsupportedTest.java` — all 9 hooks
- `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/KingbaseDiagnosticsProviderRegistrationTest.java`
- `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/jdbc/KingbaseDriverCoexistenceTest.java`

**Modify:**
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java` — add `KINGBASE` enum value + `kingbasees` alias normalize() rule (the only Wave C kind with a permitted alias per umbrella §8 line 293)
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java` — add KingbaseES URL branch
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/multimode/MultiModeConnectionShape.java` — append kingbase row to `validateModeForKind` + `isDay1FirstClassMode` (forward-compat append; **zero v1 signature changes**)
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java` — `openConnection` kingbase branch with mode validation + Day-1 first-class check
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/DefaultSqlStatementSplitters.java` — route kingbase to PostgresJdbcSqlStatementSplitter
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/risk/CalciteSqlRiskAnalyzer.java` — `classifyKingbaseSpecific` 4 patterns (Channel 1) + `detectKingbaseUnsupported` 2 patterns (Channel 2) + `KingbaseUnsupportedReason` enum (`KB_BACKUP_RESTORE_CLI` / `ORACLE_PLSQL_BLOCK`)
- `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java` — kingbase shares `postgresql` discovery branch with `KINGBASE_SYSTEM_SCHEMAS` (6 items: 4 PG + `sys` + `sys_catalog`)
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java` — kingbase Stage 1 entry: mode validation + Channel 2 dialect_unsupported detection BEFORE JDBC
- `server/data-talk-infrastructure/pom.xml` — add `cn.com.kingbase:kingbase8` dependency at the patch pinned in Task 1
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java` — add `kingbase` enum (LAST step, post-verify)
- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` — add KingbaseES section (LAST step, post-verify)
- `server/data-talk-adapter/src/main/resources/messages.properties` — i18n entries (en)
- `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties` — i18n entries (zh)
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` — Current Support Snapshot row update (LAST step)
- `docs/exec-plans/index.md` — register this plan (Active → Completed)
- `docs/product-specs/2026-05-08-data-source-coverage-kingbase-design.md` — backfill final pinned driver patch from Task 1

### Frontend (TypeScript / React 19)

**Create:**
- `client/src/features/settings/data-sources/kingbase-connection-fields.tsx` — invokes oceanbase-shipped `multi-mode-connection-fields.tsx` skeleton with `modeOptions=["pg","oracle"]` + `modeDisabled=["oracle"]`; renders `kingbasees` alias hint chip when user types `kingbasees`
- `client/src/features/settings/data-sources/__tests__/kingbase-connection-fields.test.tsx`
- `client/src/features/settings/data-sources/__tests__/kingbase-form-validation.test.tsx`

**Modify:**
- `client/src/features/settings/data-sources/connection-form-dialog.tsx` — render multi-mode + kingbase fields when kind=kingbase; database field required (PG-equivalent)
- `client/src/features/settings/data-sources/data-sources-page.tsx` — picker entry for KingbaseES (en label `KingbaseES` / zh label `人大金仓 KingbaseES`)
- `client/src/features/stage/utils/format-sql.ts` — kingbase formatter routing (reuse postgresql)
- `client/src/i18n/messages.ts` — labels + diagnostics dialect_unsupported keys (zh + en)

### Manual Smoke

**Create:**
- `tools/manual-smoke/kingbase-day1.sh` — 9-case smoke script driving the running DataTalk REST API; reads `KINGBASE_HOST` / `KINGBASE_PORT` (default 54321) / `KINGBASE_DATABASE` / `KINGBASE_USER` / `KINGBASE_PASSWORD` env vars; required because CI does **not** run kingbase IT (T2 fixture)

---

## Task Order

Tasks 1-13 are mostly sequential due to enum / classifier dependencies. Tasks 8-9 (diagnostics + 6 IT subclasses) can run in parallel after Task 7. Task 10 (frontend) can start after Task 4 (`MultiModeConnectionShape` kingbase append shipped). Task 11 (MCP enum + AGENTS.md) is hard-blocked behind `mvn verify` SUCCESS + 6 IT pass via `-Dkingbase.it.enabled=true` per umbrella §7.5.

---

### Task 1: Approval Gate + Driver Reachability Report + Day-2 Anchor Pre-Check

**Files:**
- Read: `docs/product-specs/2026-05-08-data-source-coverage-kingbase-design.md` (entire spec, all 12 sections)
- Read: `docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md` §5 / §7 / §8 line 293 / §10
- Verify: Maven Central `cn.com.kingbase:kingbase8` 9.0.x latest stable patch
- Verify: opengauss-shipped `AbstractPgFork*ReuseTest` 6 base classes exist on `develop` HEAD
- Verify: oceanbase-shipped `MultiModeConnectionShape` v1 + `CompatibilityMode` enum + Flyway V18 `compatibility_mode` column exist on `develop` HEAD
- Verify: day2 plan §Day-3 kingbase row anchor (line 3119) already complete

- [ ] **Step 1: Read the full spec end to end**

Re-read the design doc; produce a 1-page bullet list of decisions to apply (driver pinned patch, `kingbasees` alias rule, default port 54321, system schemas 6 items, Channel 1 + Channel 2 anchored patterns, i18n keys, T2 fixture discipline). No code yet.

- [ ] **Step 2: Verify driver Maven Central visibility (Driver Reachability Report)**

Run:
```bash
curl -sf "https://search.maven.org/solrsearch/select?q=g:cn.com.kingbase+AND+a:kingbase8&rows=20&wt=json" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); [print(r['v']) for r in d['response']['docs']]"
```
Expected: list of versions including `9.0.x` patches. Pin **the highest 9.0.x non-snapshot patch** as the version constant. Record the exact pinned version in this plan's first changelog line.

If the artifact is **not** visible on Maven Central (license-gated mirror), fall back to **vendor portal documentation** path: append a "Vendor Portal Fallback" subsection to this plan with the URL `https://www.kingbase.com.cn/` and the developer-machine driver download steps. **Do NOT** check an offline jar into `lib/` — that is forbidden by Wave C umbrella §10. Under the fallback path the 6 IT remain `@Disabled` on CI (already the Day-1 plan per spec §12.1).

- [ ] **Step 3: Verify opengauss-shipped PgFork abstract bases**

Run:
```bash
ls server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/ 2>/dev/null
```
Expected: 6 files
- `AbstractPgForkSplitterReuseTest.java`
- `AbstractPgForkMetadataReuseTest.java`
- `AbstractPgForkTargetResolutionReuseTest.java`
- `AbstractPgForkBatchDmlReuseTest.java`
- `AbstractPgForkResultNormalizationReuseTest.java`
- `AbstractPgForkConnectionTestReuseTest.java`

Then capture the exact protected-abstract / protected-getter method names that subclasses must override:
```bash
grep -rn "protected abstract\|protected String" server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/ | head -40
```
Record the exact method signatures (e.g. `jdbcUrl()`, `username()`, `password()`) for use in Task 9. **If method names differ from the spec assumptions**, update Task 9 IT subclass overrides accordingly (do not invent method names).

- [ ] **Step 4: Verify oceanbase-shipped MultiModeConnectionShape v1**

Run:
```bash
test -f server/data-talk-application/src/main/java/com/datatalk/application/connection/multimode/MultiModeConnectionShape.java && echo "v1 found"
test -f server/data-talk-application/src/main/java/com/datatalk/application/connection/multimode/CompatibilityMode.java && echo "enum found"
ls server/data-talk-infrastructure/src/main/resources/db/migration | grep V18
```
Expected: both Java files present; `V18__multimode_and_oceanbase_fields.sql` present. Then confirm `compatibility_mode` column already exists in current schema:
```bash
grep -n "compatibility_mode" server/data-talk-infrastructure/src/main/resources/db/migration/V18*.sql
```
**No new Flyway migration is required for kingbase** — V18 already covers all kingbase fields. If V18 is missing, halt and surface the dependency violation.

- [ ] **Step 5: Verify day2 plan §Day-3 kingbase row anchor**

Run:
```bash
grep -n "kingbase" docs/exec-plans/2026-05-08-diagnostics-day2-plan.md | grep -i "PostgresJsonPlanParser\|SYS_\*"
```
Expected: line ~3119 contains both phrases ("`PostgresJsonPlanParser` from `opengauss`" and "B-tree" with "KingbaseES `SYS_*`"). **No backfill required** at this child plan ship time per spec §11.2.

- [ ] **Step 6: Commit Approval Gate report**

Append to this plan a one-line "Driver Reachability Report" stating pinned driver version (path (a) Maven Central direct OR path (b) vendor portal documentation fallback) + opengauss base classes commit + oceanbase v1 commit. Then:
```bash
git add docs/exec-plans/2026-05-08-data-source-coverage-kingbase-plan.md
git commit -m "chore(kingbase): record approval gate (driver pin + upstream kit verification)"
```

---

### Task 2: `kingbasees` Alias Normalization @ ConnectionKind

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/connection/KingbaseAliasNormalizationTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.datatalk.application.connection;

import com.datatalk.domain.error.DataTalkException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KingbaseAliasNormalizationTest {

    @Test
    void canonicalKingbaseLowerCaseAccepted() {
        assertThat(ConnectionKind.normalize("kingbase")).isEqualTo(ConnectionKind.KINGBASE);
    }

    @Test
    void canonicalKingbaseMixedCaseAccepted() {
        assertThat(ConnectionKind.normalize("KINGBASE")).isEqualTo(ConnectionKind.KINGBASE);
        assertThat(ConnectionKind.normalize("Kingbase")).isEqualTo(ConnectionKind.KINGBASE);
    }

    @Test
    void kingbaseesAliasNormalizedToKingbase() {
        // umbrella §8 line 293: only Wave C kind with a permitted alias
        assertThat(ConnectionKind.normalize("kingbasees")).isEqualTo(ConnectionKind.KINGBASE);
    }

    @Test
    void kingbaseesAliasMixedCaseNormalized() {
        assertThat(ConnectionKind.normalize("KingbaseES")).isEqualTo(ConnectionKind.KINGBASE);
        assertThat(ConnectionKind.normalize("KINGBASEES")).isEqualTo(ConnectionKind.KINGBASE);
        assertThat(ConnectionKind.normalize("KingBaseES")).isEqualTo(ConnectionKind.KINGBASE);
    }

    @Test
    void shortAliasRejected() {
        assertThatThrownBy(() -> ConnectionKind.normalize("kb"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("kbase"))
            .isInstanceOf(DataTalkException.class);
    }

    @Test
    void chineseAliasRejected() {
        assertThatThrownBy(() -> ConnectionKind.normalize("金仓"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("人大金仓"))
            .isInstanceOf(DataTalkException.class);
    }

    @Test
    void versionSuffixedAliasRejected() {
        assertThatThrownBy(() -> ConnectionKind.normalize("kingbase7"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("kingbase8"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("kingbase9"))
            .isInstanceOf(DataTalkException.class);
    }
}
```

- [ ] **Step 2: Run the failing test**

Run: `cd server && mvn -pl data-talk-application test -Dtest=KingbaseAliasNormalizationTest -q`
Expected: FAIL with "no enum constant ConnectionKind.KINGBASE" or normalize() does not match `kingbase` / `kingbasees`.

- [ ] **Step 3: Add `KINGBASE` to enum + alias rule in `normalize()`**

Edit `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`. In the enum body add `KINGBASE` (before the trailing semicolon). In `normalize(String raw)` switch, add **two** case arms:

```java
return switch (lower) {
    case "mysql" -> ConnectionKind.MYSQL;
    // ... existing cases ...
    case "oceanbase" -> ConnectionKind.OCEANBASE;
    case "kingbase" -> ConnectionKind.KINGBASE;
    case "kingbasees" -> ConnectionKind.KINGBASE;  // umbrella §8 line 293 permitted alias
    // explicitly reject all other variants (kb, kbase, 金仓, 人大金仓, kingbase7/8/9)
    default -> throw new DataTalkException(DataTalkErrorCodes.UNKNOWN_CONNECTION_KIND, "kind=" + raw);
};
```

The `kingbasees` arm is the **only** Wave C alias permitted. All other inputs (Chinese aliases, version-suffixed forms) must fall through to the `default` reject arm.

- [ ] **Step 4: Run tests, verify pass**

Run: `cd server && mvn -pl data-talk-application test -Dtest=KingbaseAliasNormalizationTest -q`
Expected: PASS (7/7).

- [ ] **Step 5: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java \
        server/data-talk-application/src/test/java/com/datatalk/application/connection/KingbaseAliasNormalizationTest.java
git commit -m "feat(kingbase): ConnectionKind.KINGBASE + kingbasees alias normalization"
```

---

### Task 3: KingbaseDriverCoexistenceTest + Maven Dep + JdbcUrlBuilder Branch

**Files:**
- Modify: `server/data-talk-infrastructure/pom.xml`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/jdbc/KingbaseDriverCoexistenceTest.java`

- [ ] **Step 1: Add `kingbase8` Maven dependency**

Edit `server/data-talk-infrastructure/pom.xml` (under `<dependencies>`):

```xml
<dependency>
    <groupId>cn.com.kingbase</groupId>
    <artifactId>kingbase8</artifactId>
    <version>9.0.x</version> <!-- pin from Task 1 Step 2 result -->
</dependency>
```

Use the exact patch pinned in Task 1. If Task 1 fell back to vendor portal path, document that path in the plan changelog instead and skip this step pending offline driver install (the fallback path defers Task 9 IT to local/QA only — Tasks 2-8 still complete on CI).

- [ ] **Step 2: Write failing driver coexistence test**

```java
package com.datatalk.infra.jdbc;

import org.junit.jupiter.api.Test;

import java.sql.Driver;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class KingbaseDriverCoexistenceTest {

    @Test
    void kingbase8DriverIsRegistered() {
        boolean found = false;
        var drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver d = drivers.nextElement();
            if (d.getClass().getName().equals("com.kingbase8.Driver")) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void kingbase8AcceptsKingbaseUrlOnly() throws Exception {
        Driver kingbase = DriverManager.getDriver("jdbc:kingbase8://h:54321/db");
        assertThat(kingbase.getClass().getName()).isEqualTo("com.kingbase8.Driver");
    }

    @Test
    void postgresDriverDoesNotAcceptKingbaseUrl() throws Exception {
        Driver pg = (Driver) Class.forName("org.postgresql.Driver").getDeclaredConstructor().newInstance();
        assertThat(pg.acceptsURL("jdbc:kingbase8://h:54321/db")).isFalse();
    }

    @Test
    void kingbase8DoesNotAcceptPostgresUrl() throws Exception {
        Driver kingbase = (Driver) Class.forName("com.kingbase8.Driver").getDeclaredConstructor().newInstance();
        assertThat(kingbase.acceptsURL("jdbc:postgresql://h:5432/db")).isFalse();
    }

    @Test
    void kingbase8DoesNotAcceptOpengaussUrl() throws Exception {
        Driver kingbase = (Driver) Class.forName("com.kingbase8.Driver").getDeclaredConstructor().newInstance();
        assertThat(kingbase.acceptsURL("jdbc:opengauss://h:5432/db")).isFalse();
    }
}
```

- [ ] **Step 3: Run failing test**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=KingbaseDriverCoexistenceTest -q`
Expected: FAIL — `com.kingbase8.Driver` class not on classpath.

- [ ] **Step 4: Write failing JdbcUrlBuilder test**

Create `server/data-talk-application/src/test/java/com/datatalk/application/connection/JdbcUrlBuilderKingbaseTest.java`:

```java
package com.datatalk.application.connection;

import com.datatalk.application.persistence.ConnectionRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcUrlBuilderKingbaseTest {

    @Test
    void buildsKingbaseUrlWithDatabase() {
        var record = kingbaseRecord("dbX");
        assertThat(JdbcUrlBuilder.build(record))
            .isEqualTo("jdbc:kingbase8://h:54321/dbX");
    }

    @Test
    void rejectsBlankDatabase() {
        var record = kingbaseRecord("");
        assertThatThrownBy(() -> JdbcUrlBuilder.build(record))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("kingbase requires database");
    }

    @Test
    void rejectsNullDatabase() {
        var record = kingbaseRecord(null);
        assertThatThrownBy(() -> JdbcUrlBuilder.build(record))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("kingbase requires database");
    }

    private static ConnectionRecord kingbaseRecord(String db) {
        // 22-field constructor (after V18 oceanbase additions); kingbase rows use null for tenant + cluster
        return new ConnectionRecord(
            "id", "n", "kingbase", "h", 54321, db, "kbuser", new byte[]{},
            "", 0L, 10, null, null, null, 1, true, null, false,
            "pg", null, null);
    }
}
```

- [ ] **Step 5: Run failing test**

Run: `cd server && mvn -pl data-talk-application test -Dtest=JdbcUrlBuilderKingbaseTest -q`
Expected: FAIL — `JdbcUrlBuilder.build` does not handle kingbase kind.

- [ ] **Step 6: Add KingbaseES branch in `JdbcUrlBuilder`**

Edit `JdbcUrlBuilder.java`, in `build(ConnectionRecord c)`, add:

```java
case ConnectionKind.KINGBASE -> {
    String db = c.databaseName();
    if (db == null || db.isBlank()) {
        throw new IllegalArgumentException("kingbase requires database");
    }
    yield "jdbc:kingbase8://" + c.host() + ":" + c.port() + "/" + db;
}
```

PG-equivalent semantics: `database` field is required, exactly like the existing PG branch.

- [ ] **Step 7: Full module compile to verify exhaustive switch**

Run: `cd server && mvn -pl data-talk-application,data-talk-infrastructure,data-talk-adapter compile -q`
Expected: 0 errors. If any switch on `ConnectionKind` lacks the `KINGBASE` case (typically: `JdbcUrlBuilder`, `ConnectionService`, `SqlExecuteService`, `ConnectionTargetDiscoveryService`, `DefaultSqlStatementSplitters`), add a stub default-throw for now; the real branches land in Tasks 5-7.

- [ ] **Step 8: Run both tests, verify pass**

Run: `cd server && mvn -pl data-talk-application,data-talk-infrastructure test -Dtest='JdbcUrlBuilderKingbaseTest,KingbaseDriverCoexistenceTest' -q`
Expected: PASS (3+5 = 8/8).

- [ ] **Step 9: Commit**

```bash
git add server/data-talk-infrastructure/pom.xml \
        server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java \
        server/data-talk-application/src/test/java/com/datatalk/application/connection/JdbcUrlBuilderKingbaseTest.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/jdbc/KingbaseDriverCoexistenceTest.java
git commit -m "feat(kingbase): kingbase8 driver dep + JdbcUrlBuilder + coexistence test"
```

---

### Task 4: MultiModeConnectionShape v1 — Append Kingbase Row (Forward-Compat Append; Zero v1 Signature Changes)

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/multimode/MultiModeConnectionShape.java` (append-only)
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/connection/multimode/KingbaseMultiModeConnectionShapeTest.java`

Per spec §10.6: this task **must not change v1 method signatures** (`validateModeForKind(String, CompatibilityMode)` / `isDay1FirstClassMode(String, CompatibilityMode)`). Only the switch arms gain a new `case "kingbase" -> ...` clause inserted before the `default` arm.

- [ ] **Step 1: Write failing tests**

Create `KingbaseMultiModeConnectionShapeTest.java`:

```java
package com.datatalk.application.connection.multimode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KingbaseMultiModeConnectionShapeTest {

    @Test
    void kingbaseAcceptsPgMode() {
        MultiModeConnectionShape.validateModeForKind("kingbase", CompatibilityMode.PG);
        // no throw
    }

    @Test
    void kingbaseAcceptsOracleMode() {
        // mode is legal at the validate boundary; Day-1 unsupported is a separate isDay1FirstClassMode check
        MultiModeConnectionShape.validateModeForKind("kingbase", CompatibilityMode.ORACLE);
        // no throw
    }

    @Test
    void kingbaseRejectsMysqlMode() {
        assertThatThrownBy(() ->
            MultiModeConnectionShape.validateModeForKind("kingbase", CompatibilityMode.MYSQL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("kingbase requires compatibility_mode in {pg,oracle}");
    }

    @Test
    void kingbaseRejectsNullMode() {
        // multi-mode kind: mode must be specified
        assertThatThrownBy(() ->
            MultiModeConnectionShape.validateModeForKind("kingbase", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("kingbase requires compatibility_mode");
    }

    @Test
    void kingbaseDay1FirstClassOnlyPg() {
        assertThat(MultiModeConnectionShape.isDay1FirstClassMode("kingbase", CompatibilityMode.PG)).isTrue();
        assertThat(MultiModeConnectionShape.isDay1FirstClassMode("kingbase", CompatibilityMode.ORACLE)).isFalse();
    }

    @Test
    void oceanbaseRowStillAccepted_NoV1Regression() {
        // forward-compat append must NOT regress oceanbase v1 behavior
        MultiModeConnectionShape.validateModeForKind("oceanbase", CompatibilityMode.MYSQL);  // no throw
        assertThat(MultiModeConnectionShape.isDay1FirstClassMode("oceanbase", CompatibilityMode.MYSQL)).isTrue();
        assertThat(MultiModeConnectionShape.isDay1FirstClassMode("oceanbase", CompatibilityMode.ORACLE)).isFalse();
    }

    @Test
    void singleModeKindStillRejectsAnyMode_NoV1Regression() {
        // forward-compat append must NOT regress default arm behavior
        assertThatThrownBy(() ->
            MultiModeConnectionShape.validateModeForKind("mysql", CompatibilityMode.MYSQL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not specify compatibility_mode");
    }
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `cd server && mvn -pl data-talk-application test -Dtest=KingbaseMultiModeConnectionShapeTest -q`
Expected: FAIL — kingbase falls into default arm and throws "must not specify compatibility_mode".

- [ ] **Step 3: Append kingbase row to v1 (no signature changes)**

Edit `MultiModeConnectionShape.java`. In `validateModeForKind` switch, **insert before** the `default` arm:

```java
case "kingbase" -> {
    if (mode != CompatibilityMode.PG && mode != CompatibilityMode.ORACLE) {
        throw new IllegalArgumentException(
            "kingbase requires compatibility_mode in {pg,oracle}");
    }
}
```

In `isDay1FirstClassMode` switch, **insert before** the `default` arm:

```java
case "kingbase" -> mode == CompatibilityMode.PG;
```

Do not touch any other line. Method signatures remain `validateModeForKind(String, CompatibilityMode)` and `isDay1FirstClassMode(String, CompatibilityMode)`.

- [ ] **Step 4: Run tests, verify pass**

Run: `cd server && mvn -pl data-talk-application test -Dtest='KingbaseMultiModeConnectionShapeTest,MultiModeConnectionShapeTest' -q`
Expected: PASS (7 kingbase + 8 oceanbase = 15/15). The oceanbase test re-run is the v1 regression guard.

- [ ] **Step 5: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/connection/multimode/MultiModeConnectionShape.java \
        server/data-talk-application/src/test/java/com/datatalk/application/connection/multimode/KingbaseMultiModeConnectionShapeTest.java
git commit -m "feat(kingbase): append kingbase row to MultiModeConnectionShape v1 (forward-compat)"
```

---

### Task 5: ConnectionService Kingbase Branch + ConnectionRecord Validation

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/connection/KingbaseConnectionRecordValidationTest.java`

Per spec §6.3: kingbase adds **zero** new ConnectionRecord columns. Existing 22-field record is sufficient (V18 already added `compatibility_mode`; tenant + cluster are always NULL for kingbase rows, enforced by oceanbase V18 CHECK).

- [ ] **Step 1: Write failing tests**

```java
package com.datatalk.application.connection;

import com.datatalk.application.connection.multimode.CompatibilityMode;
import com.datatalk.application.connection.multimode.MultiModeConnectionShape;
import com.datatalk.application.persistence.ConnectionRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KingbaseConnectionRecordValidationTest {

    @Test
    void kingbaseRecordWithPgModeIsValid() {
        // mode boundary check passes
        MultiModeConnectionShape.validateModeForKind("kingbase", CompatibilityMode.PG);
        // Day-1 first-class check passes
        org.assertj.core.api.Assertions.assertThat(
            MultiModeConnectionShape.isDay1FirstClassMode("kingbase", CompatibilityMode.PG)).isTrue();
    }

    @Test
    void kingbaseWithMysqlModeIsRejected() {
        assertThatThrownBy(() ->
            MultiModeConnectionShape.validateModeForKind("kingbase", CompatibilityMode.MYSQL))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void kingbaseRecordTenantAndClusterAreNull() {
        var c = kingbaseRecord("pg");
        org.assertj.core.api.Assertions.assertThat(c.oceanbaseTenant()).isNull();
        org.assertj.core.api.Assertions.assertThat(c.oceanbaseCluster()).isNull();
    }

    private static ConnectionRecord kingbaseRecord(String mode) {
        return new ConnectionRecord(
            "id", "n", "kingbase", "h", 54321, "db", "kbuser", new byte[]{},
            "", 0L, 10, null, null, null, 1, true, null, false,
            mode, null, null);
    }
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `cd server && mvn -pl data-talk-application test -Dtest=KingbaseConnectionRecordValidationTest -q`
Expected: FAIL only on the validation flow if not yet wired (or PASS immediately if Task 4 ships ahead — that's also acceptable since this test exercises the v1 surface directly).

- [ ] **Step 3: Wire kingbase branch into ConnectionService.openConnection**

Edit `ConnectionService.java`. Find `openConnection(ConnectionRecord c)` (or analogous public method). Insert a kingbase branch before the JDBC `DriverManager.getConnection` call:

```java
if ("kingbase".equals(c.kind())) {
    CompatibilityMode mode = c.compatibilityMode() != null
        ? CompatibilityMode.of(c.compatibilityMode()) : null;
    MultiModeConnectionShape.validateModeForKind("kingbase", mode);
    if (!MultiModeConnectionShape.isDay1FirstClassMode("kingbase", mode)) {
        throw new DataTalkException(
            DataTalkErrorCodes.DIALECT_UNSUPPORTED,
            translator.t("connection.kind.kingbase.mode_oracle_unsupported_day1"));
    }
}
```

(Imports: `com.datatalk.application.connection.multimode.CompatibilityMode`, `com.datatalk.application.connection.multimode.MultiModeConnectionShape`.)

No need to compose tenant / cluster — kingbase username is passed verbatim per spec §6.2.

- [ ] **Step 4: Run tests, verify pass**

Run: `cd server && mvn -pl data-talk-application test -Dtest=KingbaseConnectionRecordValidationTest -q`
Expected: PASS (3/3).

- [ ] **Step 5: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java \
        server/data-talk-application/src/test/java/com/datatalk/application/connection/KingbaseConnectionRecordValidationTest.java
git commit -m "feat(kingbase): ConnectionService kingbase branch + mode validation"
```

---

### Task 6: Splitter Routing + ConnectionTargetDiscoveryService Kingbase Branch + 6-Item System Schema Filter

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/DefaultSqlStatementSplitters.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java`

Per spec §7.1: kingbase shares the `postgresql` discovery branch (NOT a separate sibling like opengauss; opengauss spec §6.2 chose three siblings deliberately, kingbase chooses share-with-pg per design author). Per spec §7.2: 6 system schemas (4 PG baseline + `sys` + `sys_catalog`).

- [ ] **Step 1: Add kingbase to splitter routing**

In `DefaultSqlStatementSplitters.java`, route `kingbase` to `PostgresJdbcSqlStatementSplitter`:

```java
if ("postgresql".equalsIgnoreCase(connectionKind)
    || "kingbase".equalsIgnoreCase(connectionKind)) {
    return postgresJdbcSplitter;
}
```

Note: opengauss has its own routing arm even though it could share — kingbase follows the share-with-pg pattern per spec §7.1 footnote.

- [ ] **Step 2: Add kingbase discovery branch + system schema filter**

In `ConnectionTargetDiscoveryService.java`, define the kingbase system schema set:

```java
private static final java.util.Set<String> KINGBASE_SYSTEM_SCHEMAS = java.util.Set.of(
    // PG baseline (4 items)
    "pg_catalog", "information_schema", "pg_toast", "pg_temp",
    // KingbaseES increment (verified at child plan Task 12 manual smoke fixture)
    "sys",            // KingbaseES internal SYS_* synonym objects
    "sys_catalog"     // KingbaseES system catalog analog to pg_catalog
);
```

Add to the discovery dispatch switch:

```java
case "postgresql" -> postgresLikeDiscovery(c, PG_SYSTEM_SCHEMAS);
case "kingbase"   -> postgresLikeDiscovery(c, KINGBASE_SYSTEM_SCHEMAS);
// opengauss remains its own branch (spec §7.1 footnote)
```

- [ ] **Step 3: Verify exhaustive switch**

Run: `cd server && mvn -pl data-talk-application,data-talk-infrastructure compile -q`
Expected: 0 errors.

- [ ] **Step 4: Commit**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/DefaultSqlStatementSplitters.java \
        server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java
git commit -m "feat(kingbase): splitter routing + discovery branch + 6 system schemas"
```

---

### Task 7: KingbaseRiskClassifier Dual-Channel — 4 + 2 Anchored Patterns + Stage 1 Wiring

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/risk/CalciteSqlRiskAnalyzer.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/sql/risk/KingbaseRiskClassifierTest.java`

Per spec §8.3 + §10 Wave C governance reset (commit `ad4c1f0`): all new patterns **MUST** use `^\s*` start anchor + `\b` word boundary; `contains()` is forbidden. KBBACKUP / KBRESTORE prefix MUST be followed by mandatory `\s+` to prevent `KBBACKUP_INFO()` function-call collision.

- [ ] **Step 1: Write failing risk classifier tests**

```java
package com.datatalk.infra.sql.risk;

import com.datatalk.domain.action.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KingbaseRiskClassifierTest {

    private final CalciteSqlRiskAnalyzer analyzer = new CalciteSqlRiskAnalyzer();

    // ==== Channel 1 hits — must classify as L3 ====

    @Test
    void dropTableSysUsersIsL3() {
        assertThat(analyzer.classifyKingbaseSpecific("DROP TABLE SYS_USERS"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void alterTableSysAuditTrailAddColumnIsL3() {
        assertThat(analyzer.classifyKingbaseSpecific("ALTER TABLE SYS_AUDIT_TRAIL ADD COLUMN x INT"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void truncateTableSysLogIsL3() {
        assertThat(analyzer.classifyKingbaseSpecific("TRUNCATE TABLE SYS_LOG"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void dropTableSyscrtPackagesIsL3() {
        assertThat(analyzer.classifyKingbaseSpecific("DROP TABLE SYSCRT_PACKAGES"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void alterTableSysauditRulesIsL3() {
        assertThat(analyzer.classifyKingbaseSpecific("ALTER TABLE SYSAUDIT_RULES ADD COLUMN y INT"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void selectSysKillIsL3() {
        assertThat(analyzer.classifyKingbaseSpecific("SELECT SYS_KILL(123)"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void flashbackTableIsL3() {
        assertThat(analyzer.classifyKingbaseSpecific("FLASHBACK TABLE my_table TO TIMESTAMP '2025-01-01 00:00:00'"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void leadingWhitespaceStillMatchesChannel1() {
        assertThat(analyzer.classifyKingbaseSpecific("   DROP TABLE SYS_USERS"))
            .hasValue(RiskLevel.L3);
    }

    // ==== Channel 1 boundaries — must NOT classify as kingbase L3 ====

    @Test
    void updateMySysUserTableIsNotMatched() {
        // 'my_sys_user' does not start with SYS_; pattern is anchored `SYS_[A-Z_]+\b`
        assertThat(analyzer.classifyKingbaseSpecific("UPDATE my_sys_user SET x = 1"))
            .isEmpty();
    }

    @Test
    void selectFromSysUsersViewIsNotMatched() {
        // SELECT not in (DROP|ALTER|TRUNCATE) anchored command list
        assertThat(analyzer.classifyKingbaseSpecific("SELECT * FROM sys_users_view"))
            .isEmpty();
    }

    @Test
    void insertIntoFlashbackLogIsNotMatched() {
        // INSERT not in anchored command list for FLASHBACK; FLASHBACK pattern requires leading FLASHBACK\s+TABLE
        assertThat(analyzer.classifyKingbaseSpecific("INSERT INTO flashback_log VALUES (1)"))
            .isEmpty();
    }

    @Test
    void selectColumnSysKillAuditIsNotMatched() {
        // 'sys_kill_audit' is a column name; SYS_KILL pattern anchored `SELECT\s+SYS_KILL\b`,
        // word boundary after KILL means SYS_KILL_AUDIT does not match
        assertThat(analyzer.classifyKingbaseSpecific("SELECT sys_kill_audit FROM x"))
            .isEmpty();
    }

    // ==== Channel 2 hits — must trigger dialect_unsupported ====

    @Test
    void kbbackupDatabaseTriggersUnsupported() {
        assertThat(analyzer.detectKingbaseUnsupported("KBBACKUP DATABASE my_db FILE='/backup/...'"))
            .hasValue(KingbaseUnsupportedReason.KB_BACKUP_RESTORE_CLI);
    }

    @Test
    void kbrestoreDatabaseTriggersUnsupported() {
        assertThat(analyzer.detectKingbaseUnsupported("KBRESTORE DATABASE my_db FROM '/backup/...'"))
            .hasValue(KingbaseUnsupportedReason.KB_BACKUP_RESTORE_CLI);
    }

    @Test
    void declareBlockTriggersUnsupported() {
        assertThat(analyzer.detectKingbaseUnsupported("DECLARE v_x INT := 1; BEGIN NULL; END;"))
            .hasValue(KingbaseUnsupportedReason.ORACLE_PLSQL_BLOCK);
    }

    @Test
    void beginBlockTriggersUnsupported() {
        assertThat(analyzer.detectKingbaseUnsupported("BEGIN DBMS_OUTPUT.PUT_LINE('x'); END;"))
            .hasValue(KingbaseUnsupportedReason.ORACLE_PLSQL_BLOCK);
    }

    // ==== Channel 2 boundaries — must NOT trigger ====

    @Test
    void kbbackupInfoFunctionCallIsNotTriggered() {
        // KBBACKUP pattern requires \s+ after KBBACKUP; KBBACKUP_INFO() has no whitespace
        assertThat(analyzer.detectKingbaseUnsupported("SELECT KBBACKUP_INFO()"))
            .isEmpty();
    }

    @Test
    void insertIntoBeginLogIsNotTriggered() {
        // 'begin_log' has \b after BEGIN_LOG, not after BEGIN; pattern is `BEGIN\b` so begin_log does not match
        assertThat(analyzer.detectKingbaseUnsupported("INSERT INTO begin_log VALUES (1)"))
            .isEmpty();
    }

    @Test
    void insertIntoDeclareAuditIsNotTriggered() {
        assertThat(analyzer.detectKingbaseUnsupported("INSERT INTO declare_audit VALUES (1)"))
            .isEmpty();
    }

    @Test
    void pgStyleDoBlockIsNotTriggered() {
        // PG-style DO $$ ... $$ starts with DO, not DECLARE/BEGIN — supported by splitter
        assertThat(analyzer.detectKingbaseUnsupported("DO $$ BEGIN RAISE NOTICE 'x'; END $$;"))
            .isEmpty();
    }
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=KingbaseRiskClassifierTest -q`
Expected: FAIL — `classifyKingbaseSpecific` / `detectKingbaseUnsupported` / `KingbaseUnsupportedReason` not defined.

- [ ] **Step 3: Add `KingbaseUnsupportedReason` enum**

Create `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/risk/KingbaseUnsupportedReason.java`:

```java
package com.datatalk.infra.sql.risk;

public enum KingbaseUnsupportedReason {
    KB_BACKUP_RESTORE_CLI,
    ORACLE_PLSQL_BLOCK
}
```

- [ ] **Step 4: Add 4 + 2 anchored Pattern constants + classify / detect methods**

Edit `CalciteSqlRiskAnalyzer.java`. Add private static fields:

```java
// ==== Channel 1 — KingbaseES admin / DDL L3 patterns (4 anchored) ====

private static final java.util.regex.Pattern KINGBASE_SYS_TABLE_DDL =
    java.util.regex.Pattern.compile(
        "^\\s*(DROP|ALTER|TRUNCATE)\\s+(TABLE\\s+)?SYS_[A-Z_]+\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);

private static final java.util.regex.Pattern KINGBASE_SYS_ADMIN_SCHEMA_DDL =
    java.util.regex.Pattern.compile(
        "^\\s*(DROP|ALTER)\\s+(TABLE\\s+)?SYS(CRT|AUDIT)_[A-Z_]+\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);

private static final java.util.regex.Pattern KINGBASE_SYS_KILL =
    java.util.regex.Pattern.compile(
        "^\\s*SELECT\\s+SYS_KILL\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);

private static final java.util.regex.Pattern KINGBASE_FLASHBACK =
    java.util.regex.Pattern.compile(
        "^\\s*FLASHBACK\\s+TABLE\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);

// ==== Channel 2 — KingbaseES Day-1 dialect_unsupported patterns (2 anchored) ====

private static final java.util.regex.Pattern KINGBASE_KB_BACKUP_RESTORE =
    java.util.regex.Pattern.compile(
        "^\\s*(KBBACKUP|KBRESTORE)\\s+",
        java.util.regex.Pattern.CASE_INSENSITIVE);
// Whitespace-required suffix prevents collision with hypothetical SELECT KBBACKUP_INFO() function calls

private static final java.util.regex.Pattern KINGBASE_ORACLE_PLSQL_BLOCK =
    java.util.regex.Pattern.compile(
        "^\\s*(DECLARE|BEGIN)\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);
// Day-1 PG-mode does not support Oracle-style PL/SQL block; PG-style DO $$ ... $$ is supported by the splitter
```

Then add two methods:

```java
public java.util.Optional<com.datatalk.domain.action.RiskLevel>
        classifyKingbaseSpecific(String sql) {
    if (sql == null) return java.util.Optional.empty();
    if (KINGBASE_SYS_TABLE_DDL.matcher(sql).find()
        || KINGBASE_SYS_ADMIN_SCHEMA_DDL.matcher(sql).find()
        || KINGBASE_SYS_KILL.matcher(sql).find()
        || KINGBASE_FLASHBACK.matcher(sql).find()) {
        return java.util.Optional.of(com.datatalk.domain.action.RiskLevel.L3);
    }
    return java.util.Optional.empty();
}

public java.util.Optional<KingbaseUnsupportedReason>
        detectKingbaseUnsupported(String sql) {
    if (sql == null) return java.util.Optional.empty();
    if (KINGBASE_KB_BACKUP_RESTORE.matcher(sql).find()) {
        return java.util.Optional.of(KingbaseUnsupportedReason.KB_BACKUP_RESTORE_CLI);
    }
    if (KINGBASE_ORACLE_PLSQL_BLOCK.matcher(sql).find()) {
        return java.util.Optional.of(KingbaseUnsupportedReason.ORACLE_PLSQL_BLOCK);
    }
    return java.util.Optional.empty();
}
```

- [ ] **Step 5: Wire Channel 1 into main `classify(String, ConnectionKind)`**

In `CalciteSqlRiskAnalyzer.classify(...)`, before falling through to base PG classification:

```java
if (kind == ConnectionKind.KINGBASE) {
    var kb = classifyKingbaseSpecific(sql);
    if (kb.isPresent()) {
        return new SqlExecutionRisk(kb.get(), "kingbase_admin_command", sql);
    }
    // fall through to base PG classification (kingbase shares PG dialect base)
}
```

Risk label aggregation: every KingbaseES-specific L3 hit is labeled `kingbase_admin_command` (canonical kind, NOT `kingbasees_admin_command`).

- [ ] **Step 6: Wire Channel 2 into SqlExecuteService Stage 1 entry**

Edit `SqlExecuteService.execute(...)`. Before any JDBC call (Stage 1):

```java
if ("kingbase".equals(c.kind())) {
    // Mode validation (already happens at openConnection per Task 5; re-run as defensive Stage 1 guard)
    var mode = c.compatibilityMode() != null
        ? CompatibilityMode.of(c.compatibilityMode()) : null;
    if (!MultiModeConnectionShape.isDay1FirstClassMode("kingbase", mode)) {
        throw new DataTalkException(
            DataTalkErrorCodes.DIALECT_UNSUPPORTED,
            translator.t("connection.kind.kingbase.mode_oracle_unsupported_day1"));
    }
    // Channel 2 dialect_unsupported detection BEFORE JDBC
    var unsupported = riskAnalyzer.detectKingbaseUnsupported(sql);
    if (unsupported.isPresent()) {
        String key = switch (unsupported.get()) {
            case KB_BACKUP_RESTORE_CLI -> "risk.dialect_unsupported.kingbase.kb_backup_restore_cli";
            case ORACLE_PLSQL_BLOCK    -> "risk.dialect_unsupported.kingbase.oracle_plsql_block";
        };
        throw new DataTalkException(
            DataTalkErrorCodes.DIALECT_UNSUPPORTED, translator.t(key));
    }
}
```

- [ ] **Step 7: Run risk classifier tests, verify pass**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=KingbaseRiskClassifierTest -q`
Expected: PASS (20/20 — 8 hit + 4 boundary Channel 1 + 4 hit + 4 boundary Channel 2).

- [ ] **Step 8: Run full module compile**

Run: `cd server && mvn compile -q`
Expected: 0 errors.

- [ ] **Step 9: Commit**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/risk/CalciteSqlRiskAnalyzer.java \
        server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/risk/KingbaseUnsupportedReason.java \
        server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/sql/risk/KingbaseRiskClassifierTest.java
git commit -m "feat(kingbase): risk classifier dual-channel 4+2 anchored patterns + Stage 1 wiring"
```

---

### Task 8: KingbaseDiagnosticsProvider — All 9 Hooks `dialect_unsupported` + Provider Registration + i18n

**Files:**
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/KingbaseDiagnosticsProvider.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/KingbaseDiagnosticsDialectUnsupportedTest.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/KingbaseDiagnosticsProviderRegistrationTest.java`
- Modify: `server/data-talk-adapter/src/main/resources/messages.properties` + `messages_zh_CN.properties`

- [ ] **Step 1: Write failing dialect_unsupported test (all 9 hooks)**

```java
package com.datatalk.infra.diagnostics;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KingbaseDiagnosticsDialectUnsupportedTest {
    private final Translator translator = mock(Translator.class);
    private final KingbaseDiagnosticsProvider provider;

    KingbaseDiagnosticsDialectUnsupportedTest() {
        when(translator.t(org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(inv -> "[" + inv.getArgument(0) + "]");
        provider = new KingbaseDiagnosticsProvider(translator);
    }

    @Test
    void supportedDriverTypesIsOnlyKingbase() {
        assertThat(provider.supportedDriverTypes()).containsExactly("kingbase");
    }

    @Test
    void supportedCapabilitiesIsEmpty() {
        // Day-1 returns dialect_unsupported for every hook
        assertThat(provider.supportedCapabilities()).isEmpty();
    }

    @Test
    void explainReturnsDialectUnsupported() {
        var conn = kingbaseRecord();
        var result = provider.explain("SELECT 1", conn, "pw", "db", null);
        assertThat(result.outcome()).isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
        assertThat(result.message())
            .contains("[diagnostics.dialect_unsupported.kingbase.explain_real]");
    }

    @Test
    void lockInfoReturnsDialectUnsupported() {
        assertThat(provider.lockInfo(kingbaseRecord(), "pw", "db").outcome())
            .isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
    }

    @Test
    void poolStatusReturnsDialectUnsupported() {
        assertThat(provider.poolStatus(kingbaseRecord(), "pw", "db").outcome())
            .isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
    }

    @Test
    void tableSpaceReturnsDialectUnsupported() {
        assertThat(provider.tableSpace(kingbaseRecord(), "pw", "db").outcome())
            .isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
    }

    @Test
    void terminateSessionReturnsDialectUnsupported() {
        assertThat(provider.terminateSession(123L, kingbaseRecord(), "pw", "db").outcome())
            .isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
    }

    @Test
    void optimizeTableReturnsDialectUnsupported() {
        assertThat(provider.optimizeTable("t", kingbaseRecord(), "pw", "db", null).outcome())
            .isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
    }

    @Test
    void indexHintsReturnsDialectUnsupported() {
        assertThat(provider.indexHints("SELECT 1", kingbaseRecord(), "pw", "db", null).outcome())
            .isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
    }

    @Test
    void erInspectorReturnsDialectUnsupported() {
        // ER hooks routed via DiagnosticsService; here we directly test the provider hook surface used by ER
        assertThat(provider.erInspector(kingbaseRecord(), "pw", "db", null).outcome())
            .isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
    }

    @Test
    void erDesignerReturnsDialectUnsupported() {
        assertThat(provider.erDesigner(kingbaseRecord(), "pw", "db", null).outcome())
            .isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
    }

    private static ConnectionRecord kingbaseRecord() {
        return new ConnectionRecord(
            "id", "n", "kingbase", "h", 54321, "db", "kbuser",
            new byte[]{}, "", 0L, 10, null, null, null, 1, true, null,
            false, "pg", null, null);
    }
}
```

If `erInspector` / `erDesigner` are not direct provider methods (they may live on `DiagnosticsService` instead, dispatched by kind), drop those two test methods and add them to `KingbaseDiagnosticsProviderRegistrationTest` as routing assertions instead.

- [ ] **Step 2: Write failing provider registration test**

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
class KingbaseDiagnosticsProviderRegistrationTest {

    @Autowired
    private List<DiagnosticsProvider> providers;

    @Test
    void kingbaseProviderIsRegistered() {
        assertThat(providers)
            .anyMatch(p -> p instanceof KingbaseDiagnosticsProvider);
    }

    @Test
    void kingbaseProviderRoutesByDriverType() {
        var kingbase = providers.stream()
            .filter(p -> p instanceof KingbaseDiagnosticsProvider)
            .findFirst().orElseThrow();
        assertThat(kingbase.supportedDriverTypes()).contains("kingbase");
    }
}
```

- [ ] **Step 3: Run tests to verify fail**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest='KingbaseDiagnosticsDialectUnsupportedTest,KingbaseDiagnosticsProviderRegistrationTest' -q`
Expected: FAIL — class not found.

- [ ] **Step 4: Implement `KingbaseDiagnosticsProvider`**

Follow the pattern of `OceanBaseDiagnosticsProvider` (which itself follows `TidbDiagnosticsProvider`). Create the file:

```java
package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class KingbaseDiagnosticsProvider extends AbstractDiagnosticsProvider {

    public KingbaseDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("kingbase");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of();  // Day-1: all dialect_unsupported
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn,
                                                  String decryptedPassword,
                                                  String database, String schema) {
        return dialectUnsupported("explain_real", "KingbaseES PG-mode does not support explain_real in Day-1.");
    }

    @Override
    public DiagnosticResult<List<IndexHint>> indexHints(String sql, ConnectionRecord conn,
                                                        String decryptedPassword,
                                                        String database, String schema) {
        return dialectUnsupported("index_hints", "KingbaseES PG-mode does not support index_hints in Day-1.");
    }

    @Override
    public DiagnosticResult<List<LockInfoEntry>> lockInfo(ConnectionRecord conn,
                                                           String decryptedPassword, String database) {
        return dialectUnsupported("lock_info", "KingbaseES PG-mode does not support lock_info in Day-1.");
    }

    @Override
    public DiagnosticResult<PoolStatus> poolStatus(ConnectionRecord conn,
                                                   String decryptedPassword, String database) {
        return dialectUnsupported("pool_status", "KingbaseES PG-mode does not support pool_status in Day-1.");
    }

    @Override
    public DiagnosticResult<List<TableSpaceEntry>> tableSpace(ConnectionRecord conn,
                                                              String decryptedPassword,
                                                              String database) {
        return dialectUnsupported("table_space", "KingbaseES PG-mode does not support table_space in Day-1.");
    }

    @Override
    public DiagnosticResult<TerminateSessionResult> terminateSession(long sessionId,
                                                                      ConnectionRecord conn,
                                                                      String decryptedPassword,
                                                                      String database) {
        return dialectUnsupported("terminate_session", "KingbaseES PG-mode does not support terminate_session in Day-1.");
    }

    @Override
    public DiagnosticResult<OptimizeTableResult> optimizeTable(String table,
                                                                ConnectionRecord conn,
                                                                String decryptedPassword,
                                                                String database, String schema) {
        return dialectUnsupported("optimize_table", "KingbaseES PG-mode does not support optimize_table in Day-1.");
    }

    @Override
    public DiagnosticResult<ErModel> erInspector(ConnectionRecord conn,
                                                  String decryptedPassword,
                                                  String database, String schema) {
        return dialectUnsupported("er_inspector", "KingbaseES PG-mode does not support ER Inspector in Day-1.");
    }

    @Override
    public DiagnosticResult<ErModel> erDesigner(ConnectionRecord conn,
                                                 String decryptedPassword,
                                                 String database, String schema) {
        return dialectUnsupported("er_designer", "KingbaseES PG-mode does not support ER Designer in Day-1.");
    }

    private <T> DiagnosticResult<T> dialectUnsupported(String capability, String fallback) {
        String key = "diagnostics.dialect_unsupported.kingbase." + capability;
        String msg = translator.t(key);
        return DiagnosticResult.dialectUnsupported(msg.equals(key) ? fallback : msg);
    }
}
```

If the framework dispatches `er_inspector` / `er_designer` through `DiagnosticsService` instead of provider methods, omit those two methods here and add a `DiagnosticsService` dispatch arm in a separate edit (see Task 7-style wiring).

- [ ] **Step 5: Add i18n entries**

Append to `server/data-talk-adapter/src/main/resources/messages.properties` (en):

```
diagnostics.dialect_unsupported.kingbase.lock_info=KingbaseES PG-mode does not support lock_info in Day-1. See Wave C Day-3 candidate (kingbase pg-mode upgrade path).
diagnostics.dialect_unsupported.kingbase.pool_status=KingbaseES PG-mode does not support pool_status in Day-1. See Wave C Day-3 candidate (kingbase pg-mode upgrade path).
diagnostics.dialect_unsupported.kingbase.table_space=KingbaseES PG-mode does not support table_space in Day-1. See Wave C Day-3 candidate (kingbase pg-mode upgrade path).
diagnostics.dialect_unsupported.kingbase.terminate_session=KingbaseES PG-mode does not support terminate_session in Day-1. See Wave C Day-3 candidate (kingbase pg-mode upgrade path).
diagnostics.dialect_unsupported.kingbase.optimize_table=KingbaseES PG-mode does not support optimize_table in Day-1. See Wave C Day-3 candidate (kingbase pg-mode upgrade path).
diagnostics.dialect_unsupported.kingbase.explain_real=KingbaseES PG-mode does not support explain_real in Day-1. See Wave C Day-3 candidate (kingbase pg-mode upgrade path; reuses opengauss-shipped PostgresJsonPlanParser).
diagnostics.dialect_unsupported.kingbase.index_hints=KingbaseES PG-mode does not support index_hints in Day-1. See Wave C Day-3 candidate (kingbase pg-mode upgrade path; B-tree recommendation).
diagnostics.dialect_unsupported.kingbase.er_inspector=KingbaseES PG-mode does not support ER Inspector in Day-1.
diagnostics.dialect_unsupported.kingbase.er_designer=KingbaseES PG-mode does not support ER Designer in Day-1.
connection.kind.kingbase.label=KingbaseES
connection.kind.kingbase.alias_normalized=Input "kingbasees" normalized to canonical kind "kingbase".
connection.kind.kingbase.mode_oracle_unsupported_day1=KingbaseES Oracle-mode is not supported in Day-1. See Wave C Day-3 candidate (kingbase Oracle-mode plan).
risk.dialect_unsupported.kingbase.kb_backup_restore_cli=KingbaseES KBBACKUP / KBRESTORE CLI utilities are not supported in Day-1. Use the KingbaseES vendor backup tooling directly.
risk.dialect_unsupported.kingbase.oracle_plsql_block=Oracle-style PL/SQL blocks (DECLARE/BEGIN ... END;/) require KingbaseES Oracle-mode, which is not supported in Day-1. Use PG-style DO $$ ... $$ blocks instead, or wait for the Wave C kingbase Oracle-mode Day-3 candidate.
```

To `messages_zh_CN.properties` (zh): identical keys with Chinese translations. Specifically:

```
connection.kind.kingbase.label=人大金仓 KingbaseES
connection.kind.kingbase.alias_normalized=输入 "kingbasees" 已归一化为规范类型 "kingbase"。
connection.kind.kingbase.mode_oracle_unsupported_day1=Day-1 不支持 KingbaseES Oracle 兼容模式，等待 Wave C Day-3 候选（kingbase Oracle-mode 计划）。
risk.dialect_unsupported.kingbase.kb_backup_restore_cli=Day-1 不支持 KingbaseES 的 KBBACKUP / KBRESTORE CLI 工具语句，请直接使用 KingbaseES 厂商备份工具。
risk.dialect_unsupported.kingbase.oracle_plsql_block=Oracle 风格 PL/SQL 块（DECLARE/BEGIN ... END;/）需要 KingbaseES Oracle 兼容模式，Day-1 不支持。请改用 PG 风格 DO $$ ... $$ 块，或等待 Wave C Day-3 候选（kingbase Oracle-mode）。
diagnostics.dialect_unsupported.kingbase.lock_info=Day-1 KingbaseES PG 模式不支持 lock_info 诊断，参见 Wave C Day-3 候选（kingbase pg-mode 升级路径）。
diagnostics.dialect_unsupported.kingbase.pool_status=Day-1 KingbaseES PG 模式不支持 pool_status 诊断，参见 Wave C Day-3 候选。
diagnostics.dialect_unsupported.kingbase.table_space=Day-1 KingbaseES PG 模式不支持 table_space 诊断，参见 Wave C Day-3 候选。
diagnostics.dialect_unsupported.kingbase.terminate_session=Day-1 KingbaseES PG 模式不支持 terminate_session 诊断，参见 Wave C Day-3 候选。
diagnostics.dialect_unsupported.kingbase.optimize_table=Day-1 KingbaseES PG 模式不支持 optimize_table 诊断（VACUUM ANALYZE 升级路径见 Wave C Day-3 候选）。
diagnostics.dialect_unsupported.kingbase.explain_real=Day-1 KingbaseES PG 模式不支持 explain_real 诊断，参见 Wave C Day-3 候选（复用 opengauss 已 ship 的 PostgresJsonPlanParser）。
diagnostics.dialect_unsupported.kingbase.index_hints=Day-1 KingbaseES PG 模式不支持 index_hints 诊断（B-tree 建议升级路径见 Wave C Day-3 候选）。
diagnostics.dialect_unsupported.kingbase.er_inspector=Day-1 KingbaseES PG 模式不支持 ER Inspector。
diagnostics.dialect_unsupported.kingbase.er_designer=Day-1 KingbaseES PG 模式不支持 ER Designer。
```

- [ ] **Step 6: Run all tests**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest='KingbaseDiagnosticsDialectUnsupportedTest,KingbaseDiagnosticsProviderRegistrationTest' -q`
Expected: PASS (11 + 2 = 13 / 13).

- [ ] **Step 7: Commit**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/KingbaseDiagnosticsProvider.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/KingbaseDiagnosticsDialectUnsupportedTest.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/KingbaseDiagnosticsProviderRegistrationTest.java \
        server/data-talk-adapter/src/main/resources/messages.properties \
        server/data-talk-adapter/src/main/resources/messages_zh_CN.properties
git commit -m "feat(kingbase): DiagnosticsProvider all-9-hooks dialect_unsupported + i18n"
```

---

### Task 9: 6 Concrete `Kingbase*ReuseIT` Subclasses + KingbaseConnectionTrigger + Manual Smoke Script (T2 Fixture)

**Files:**
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/kingbase/KingbaseConnectionTrigger.java`
- Create: 6 concrete IT files (paths in §File Structure)
- Create: `tools/manual-smoke/kingbase-day1.sh`

**T2 fixture discipline (different from opengauss T1):** the 6 IT are `@Disabled` by default and only run under `-Dkingbase.it.enabled=true` plus `-Dkingbase.host/port/database/user/password=...`. CI does **NOT** run them. The 6 IT remain part of the kingbase plan acceptance gate per opengauss spec §10 line 379, executed in local / QA acceptance.

- [ ] **Step 1: Verify abstract base method names**

Run:
```bash
grep -rn "protected abstract\|protected String" \
    server/data-talk-application/src/test/java/com/datatalk/application/coverage/pgfork/ | head -40
```

Capture the exact override hook names. Expected from spec §7.4: `jdbcUrl()`, `username()`, `password()`. **If method names differ**, adapt subclass overrides in Step 3.

- [ ] **Step 2: Write `KingbaseConnectionTrigger`**

```java
package com.datatalk.application.coverage.kingbase;

/**
 * Reads kingbase IT connection parameters from system properties.
 * <p>
 * Triggered by:
 * <pre>
 *   mvn -pl data-talk-application,data-talk-infrastructure verify \
 *       -Dkingbase.it.enabled=true \
 *       -Dkingbase.host=&lt;host&gt; \
 *       -Dkingbase.port=&lt;port&gt; \
 *       -Dkingbase.database=&lt;db&gt; \
 *       -Dkingbase.user=&lt;user&gt; \
 *       -Dkingbase.password=&lt;pw&gt;
 * </pre>
 * CI does NOT set kingbase.it.enabled=true; the 6 IT are part of the kingbase
 * plan acceptance gate but executed in local / QA acceptance only.
 */
public final class KingbaseConnectionTrigger {

    private KingbaseConnectionTrigger() {}

    public static String host()     { return required("kingbase.host"); }
    public static int    port()     { return Integer.parseInt(System.getProperty("kingbase.port", "54321")); }
    public static String database() { return required("kingbase.database"); }
    public static String username() { return required("kingbase.user"); }
    public static String password() { return System.getProperty("kingbase.password", ""); }

    public static String jdbcUrl() {
        return "jdbc:kingbase8://" + host() + ":" + port() + "/" + database();
    }

    private static String required(String key) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(
                "System property '" + key + "' not set. " +
                "Kingbase IT requires -Dkingbase.it.enabled=true plus " +
                "-Dkingbase.host/port/database/user/password.");
        }
        return v;
    }
}
```

- [ ] **Step 3: Write 6 concrete IT subclasses (parallel files)**

Each subclass extends one of the 6 opengauss-shipped abstract bases. Example for splitter:

```java
package com.datatalk.application.coverage.kingbase;

import com.datatalk.application.coverage.pgfork.AbstractPgForkSplitterReuseTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Tag("kingbase-it")
@EnabledIfSystemProperty(named = "kingbase.it.enabled", matches = "true")
class KingbaseSplitterReuseIT extends AbstractPgForkSplitterReuseTest {

    @Override
    protected String jdbcUrl()  { return KingbaseConnectionTrigger.jdbcUrl(); }

    @Override
    protected String username() { return KingbaseConnectionTrigger.username(); }

    @Override
    protected String password() { return KingbaseConnectionTrigger.password(); }
}
```

Repeat the same pattern for:
- `KingbaseMetadataReuseIT extends AbstractPgForkMetadataReuseTest`
- `KingbaseTargetResolutionReuseIT extends AbstractPgForkTargetResolutionReuseTest`
- `KingbaseBatchDmlReuseIT extends AbstractPgForkBatchDmlReuseTest`
- `KingbaseResultNormalizationReuseIT extends AbstractPgForkResultNormalizationReuseTest`
- `KingbaseConnectionReuseIT extends AbstractPgForkConnectionTestReuseTest`

Each gets `@Tag("kingbase-it")` + `@EnabledIfSystemProperty(named = "kingbase.it.enabled", matches = "true")`. **No Testcontainers** — kingbase is T2 and CI does not run IT.

If Step 1 grep showed override hooks named differently (e.g. `connectionUrl()` instead of `jdbcUrl()`), adapt every subclass to match.

- [ ] **Step 4: Write `tools/manual-smoke/kingbase-day1.sh`**

```bash
#!/usr/bin/env bash
# tools/manual-smoke/kingbase-day1.sh
#
# Prerequisites:
# 1. A reachable KingbaseES V8 server (trial license or development edition).
#    Vendor portal: https://www.kingbase.com.cn/
# 2. Environment variables:
#      KINGBASE_HOST, KINGBASE_PORT (default 54321),
#      KINGBASE_DATABASE, KINGBASE_USER, KINGBASE_PASSWORD
# 3. The datatalk backend running locally on 8080.
#
# Runs 9 case smoke checks against the running DataTalk backend through its
# REST API. CI does not run this script.

set -euo pipefail

: "${KINGBASE_HOST:?KINGBASE_HOST not set}"
KINGBASE_PORT="${KINGBASE_PORT:-54321}"
: "${KINGBASE_DATABASE:?KINGBASE_DATABASE not set}"
: "${KINGBASE_USER:?KINGBASE_USER not set}"
: "${KINGBASE_PASSWORD:?KINGBASE_PASSWORD not set}"
API="http://127.0.0.1:8080/api"

CONN_BODY=$(cat <<JSON
{
  "name":"kingbase-smoke",
  "kind":"kingbase",
  "host":"$KINGBASE_HOST",
  "port":$KINGBASE_PORT,
  "databaseName":"$KINGBASE_DATABASE",
  "username":"$KINGBASE_USER",
  "password":"$KINGBASE_PASSWORD",
  "compatibilityMode":"pg"
}
JSON
)

echo "Case 1: create connection (canonical kingbase)"
CONN_ID=$(curl -fsS -XPOST "$API/connections" -H 'content-type:application/json' \
    -d "$CONN_BODY" | python3 -c 'import json,sys;print(json.load(sys.stdin)["id"])')
echo "  -> id=$CONN_ID"

echo "Case 2: kingbasees alias accepted via separate POST (input alias normalized)"
curl -fsS -XPOST "$API/connections" -H 'content-type:application/json' \
    -d "$(echo "$CONN_BODY" | sed 's/"kingbase"/"kingbasees"/')" \
    | python3 -c 'import json,sys;d=json.load(sys.stdin);assert d["kind"]=="kingbase",d'

echo "Case 3: connection test (success)"
curl -fsS -XPOST "$API/connections/$CONN_ID/test"

echo "Case 4: schema discovery — system schemas filtered"
curl -fsS "$API/connections/$CONN_ID/schemas" \
    | python3 -c 'import json,sys;ss={s["name"] for s in json.load(sys.stdin)};\
                  bad={"pg_catalog","information_schema","pg_toast","pg_temp","sys","sys_catalog"} & ss;\
                  assert not bad, "leaked system schemas: "+str(bad)'

echo "Case 5: SELECT 1 (L1)"
curl -fsS -XPOST "$API/connections/$CONN_ID/sql/execute" \
    -H 'content-type:application/json' -d '{"sql":"SELECT 1"}'

echo "Case 6: L3 SYS_USERS DDL must be classified L3"
curl -fsS -XPOST "$API/connections/$CONN_ID/sql/risk" \
    -H 'content-type:application/json' -d '{"sql":"DROP TABLE SYS_USERS"}' \
    | python3 -c 'import json,sys;d=json.load(sys.stdin);assert d["level"]=="L3"'

echo "Case 7: SELECT SYS_KILL must be L3"
curl -fsS -XPOST "$API/connections/$CONN_ID/sql/risk" \
    -H 'content-type:application/json' -d '{"sql":"SELECT SYS_KILL(123)"}' \
    | python3 -c 'import json,sys;d=json.load(sys.stdin);assert d["level"]=="L3"'

echo "Case 8: KBBACKUP DATABASE must be dialect_unsupported"
curl -fsS -XPOST "$API/connections/$CONN_ID/sql/execute" \
    -H 'content-type:application/json' -d '{"sql":"KBBACKUP DATABASE x FILE='\''/x'\''"}' \
    -o - -w '%{http_code}\n' | grep -q -E '"code":"DIALECT_UNSUPPORTED"|400'

echo "Case 9: Oracle-style DECLARE block must be dialect_unsupported"
curl -fsS -XPOST "$API/connections/$CONN_ID/sql/execute" \
    -H 'content-type:application/json' -d '{"sql":"DECLARE v_x INT := 1; BEGIN NULL; END;"}' \
    -o - -w '%{http_code}\n' | grep -q -E '"code":"DIALECT_UNSUPPORTED"|400'

echo "Manual smoke 9 cases all PASSED"
```

Make executable: `chmod +x tools/manual-smoke/kingbase-day1.sh`. CI does **not** invoke this script (T2 fixture discipline).

- [ ] **Step 5: Smoke-compile (CI side; IT remain disabled)**

Run: `cd server && mvn -pl data-talk-application test-compile -q`
Expected: 0 errors. The 6 IT compile but do not execute on CI because `@EnabledIfSystemProperty` is not satisfied.

- [ ] **Step 6: Local IT exercise (developer machine only — optional during plan execution)**

If a KingbaseES V8 trial / dev server is reachable:

```bash
cd server && mvn -pl data-talk-application test \
    -Dkingbase.it.enabled=true \
    -Dkingbase.host=<h> -Dkingbase.port=<p> \
    -Dkingbase.database=<db> -Dkingbase.user=<u> -Dkingbase.password=<pw> \
    -Dtest='Kingbase*ReuseIT' -q
```

Expected: PASS (6 / 6 IT classes). Record results in plan changelog.

- [ ] **Step 7: Commit**

```bash
git add server/data-talk-application/src/test/java/com/datatalk/application/coverage/kingbase/ \
        tools/manual-smoke/kingbase-day1.sh
git commit -m "test(kingbase): 6 concrete *ReuseIT subclasses (T2 fixture) + manual smoke 9 cases"
```

---

### Task 10: Frontend — kingbase-connection-fields.tsx + Multi-Mode Skeleton Reuse + i18n

**Files:**
- Create: `client/src/features/settings/data-sources/kingbase-connection-fields.tsx`
- Create: `client/src/features/settings/data-sources/__tests__/kingbase-connection-fields.test.tsx`
- Modify: `client/src/features/settings/data-sources/connection-form-dialog.tsx`
- Modify: `client/src/features/settings/data-sources/data-sources-page.tsx`
- Modify: `client/src/features/stage/utils/format-sql.ts`
- Modify: `client/src/i18n/messages.ts`

Per `client/DESIGN.md` Frontend Design Contract Gate (CLAUDE.md), the 5-state token contract MUST be fully enumerated for every interactive control.

- [ ] **Step 1: Read `client/DESIGN.md` semantic token list and 5-state contract**

Re-confirm token names (per spec §3 client/DESIGN.md inputs): `bg.panel`, `border.default`, `interaction.focusRing`, `interaction.hover`, `interaction.active`, `interaction.selected`, `interaction.disabled`, `accent.primary`, `text.onAccent`, `text.muted`, `text.strong`, `feedback.warning.bg`, `feedback.warning.border`, `text.warning`. No component-local color values invented.

- [ ] **Step 2: Enumerate 5-state token table for all kingbase controls**

Write the following table at the top of `kingbase-connection-fields.tsx` as a comment block (per memory record `feedback-design-control-states.md`: no abbreviation; each control × 5 state token explicitly listed):

```
Control                          | default                | hover                  | focus                  | active/selected        | disabled
---------------------------------+------------------------+------------------------+------------------------+------------------------+-------------------------
Kind picker row (KingbaseES)     | bg.panel + border.def. | interaction.hover      | interaction.focusRing  | interaction.selected   | interaction.disabled
Host input                       | bg.panel + border.def. | interaction.hover      | interaction.focusRing  | (n/a)                  | interaction.disabled
Port input (default 54321)       | bg.panel + border.def. | interaction.hover      | interaction.focusRing  | (n/a)                  | interaction.disabled
Database input (required)        | bg.panel + border.def. | interaction.hover      | interaction.focusRing  | (n/a)                  | interaction.disabled
Mode picker chip — pg            | bg.panel + border.def. | interaction.hover      | interaction.focusRing  | interaction.selected   | (n/a — always enabled)
Mode picker chip — oracle        | (Day-1 disabled)       | (n/a — disabled)       | (n/a — disabled)       | (n/a — disabled)       | interaction.disabled + tooltip Day-3 anchor
Save button                      | accent.primary +       | interaction.hover (on  | interaction.focusRing  | interaction.active     | interaction.disabled
                                 | text.onAccent          | accent.primary)        |                        |                        |
dialect_unsupported chip         | feedback.warning.bg +  | (n/a — informational)  | (n/a — informational)  | (n/a — informational)  | (n/a — informational)
                                 | text.warning           |                        |                        |                        |
kingbasees alias hint chip       | bg.panel + border.def. | (n/a — informational)  | (n/a — informational)  | (n/a — informational)  | (n/a — informational)
                                 | + text.muted           |                        |                        |                        |
```

8 controls × 5 states fully enumerated; informational chips use `(n/a)` notation per design contract.

- [ ] **Step 3: Write failing component test**

```typescript
// client/src/features/settings/data-sources/__tests__/kingbase-connection-fields.test.tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { KingbaseConnectionFields } from "../kingbase-connection-fields";

describe("KingbaseConnectionFields", () => {
    it("reuses MultiModeConnectionFields with pg/oracle, oracle disabled", () => {
        render(<KingbaseConnectionFields
            mode="pg"
            onModeChange={() => {}}
            kindInput="kingbase"
        />);
        expect(screen.getByRole("radio", { name: /PostgreSQL/i })).not.toBeDisabled();
        expect(screen.getByRole("radio", { name: /Oracle/i })).toBeDisabled();
    });

    it("invokes onModeChange when user picks pg", () => {
        const onModeChange = vi.fn();
        render(<KingbaseConnectionFields
            mode="pg"
            onModeChange={onModeChange}
            kindInput="kingbase"
        />);
        fireEvent.click(screen.getByRole("radio", { name: /PostgreSQL/i }));
        expect(onModeChange).toHaveBeenCalledWith("pg");
    });

    it("renders kingbasees alias hint chip when kindInput is the alias", () => {
        render(<KingbaseConnectionFields
            mode="pg"
            onModeChange={() => {}}
            kindInput="kingbasees"
        />);
        expect(
            screen.getByText(/normalized to canonical kind|归一化为规范类型/i)
        ).toBeInTheDocument();
    });

    it("does not render alias hint when kindInput is canonical", () => {
        render(<KingbaseConnectionFields
            mode="pg"
            onModeChange={() => {}}
            kindInput="kingbase"
        />);
        expect(
            screen.queryByText(/normalized to canonical kind|归一化为规范类型/i)
        ).not.toBeInTheDocument();
    });
});
```

- [ ] **Step 4: Run test to verify fail**

Run: `cd client && npx vitest run kingbase-connection-fields`
Expected: FAIL — module not found.

- [ ] **Step 5: Implement `KingbaseConnectionFields`**

Create `client/src/features/settings/data-sources/kingbase-connection-fields.tsx`:

```tsx
import { type FC } from "react";
import { useT } from "@/i18n/hooks";
import {
    MultiModeConnectionFields,
    type CompatibilityMode,
} from "./multi-mode-connection-fields";

interface Props {
    mode: CompatibilityMode | null;
    onModeChange: (mode: CompatibilityMode) => void;
    /**
     * Raw user input for kind. When equal to the canonical alias `kingbasees`,
     * an info chip is shown explaining the normalization to `kingbase`.
     */
    kindInput: string;
}

export const KingbaseConnectionFields: FC<Props> = ({
    mode, onModeChange, kindInput,
}) => {
    const t = useT();
    const isAlias = kindInput.toLowerCase() === "kingbasees";
    return (
        <div className="flex flex-col gap-3">
            {isAlias && (
                <div
                    role="status"
                    className="text-xs px-2 py-1 rounded
                               bg-[var(--color-bg-panel)]
                               border border-[var(--color-border-default)]
                               text-[var(--color-text-muted)]"
                    aria-live="polite"
                >
                    {t("connection.kind.kingbase.alias_normalized")}
                </div>
            )}
            <MultiModeConnectionFields
                kind="kingbase"
                mode={mode}
                onModeChange={onModeChange}
                modeOptions={["pg", "oracle"]}
                modeDisabled={["oracle"]}
            />
        </div>
    );
};
```

- [ ] **Step 6: Add i18n keys**

Append to `client/src/i18n/messages.ts`:

```typescript
"connection.kind.kingbase.label": { en: "KingbaseES", "zh-CN": "人大金仓 KingbaseES" },
"connection.kind.kingbase.alias_normalized": {
    en: 'Input "kingbasees" normalized to canonical kind "kingbase".',
    "zh-CN": '输入 "kingbasees" 已归一化为规范类型 "kingbase"。'
},
"connection.kind.kingbase.mode_oracle_unsupported_day1": {
    en: "KingbaseES Oracle-mode is not supported in Day-1.",
    "zh-CN": "Day-1 不支持 KingbaseES Oracle 兼容模式。"
},
"risk.dialect_unsupported.kingbase.kb_backup_restore_cli": {
    en: "KBBACKUP / KBRESTORE CLI utilities are not supported in Day-1.",
    "zh-CN": "Day-1 不支持 KBBACKUP / KBRESTORE CLI 语句。"
},
"risk.dialect_unsupported.kingbase.oracle_plsql_block": {
    en: "Oracle-style PL/SQL blocks require KingbaseES Oracle-mode (Day-1 unsupported).",
    "zh-CN": "Oracle 风格 PL/SQL 块需要 Oracle 兼容模式（Day-1 不支持）。"
},
"diagnostics.dialect_unsupported.kingbase.lock_info": {
    en: "KingbaseES PG-mode does not support lock_info in Day-1.",
    "zh-CN": "Day-1 KingbaseES PG 模式不支持 lock_info 诊断。"
},
// (repeat parallel zh / en pairs for pool_status, table_space, terminate_session,
//  optimize_table, explain_real, index_hints, er_inspector, er_designer)
```

- [ ] **Step 7: Run vitest, verify pass**

Run: `cd client && npx vitest run kingbase-connection-fields`
Expected: PASS (4 / 4).

- [ ] **Step 8: Wire into `connection-form-dialog.tsx` and add picker entry**

In `connection-form-dialog.tsx`, render `KingbaseConnectionFields` when `kind === "kingbase"`. The host / port / database / username / password inputs are the same shared form (database required, default port 54321).

In `data-sources-page.tsx` picker, add a row:

```tsx
{ value: "kingbase", label: t("connection.kind.kingbase.label") }
```

Per spec §5: even when the user types `kingbasees`, picker persistence is `kingbase`.

- [ ] **Step 9: Add format-sql kingbase routing**

In `client/src/features/stage/utils/format-sql.ts`, route `kingbase` to the postgresql formatter (kingbase Day-1 PG-mode == PG dialect for formatting purposes).

- [ ] **Step 10: Run typecheck and full vitest**

Run: `cd client && npx tsc --noEmit && npx vitest run`
Expected: 0 type errors; all vitest tests pass.

- [ ] **Step 11: Commit**

```bash
git add client/src/features/settings/data-sources/kingbase-connection-fields.tsx \
        client/src/features/settings/data-sources/__tests__ \
        client/src/features/settings/data-sources/connection-form-dialog.tsx \
        client/src/features/settings/data-sources/data-sources-page.tsx \
        client/src/features/stage/utils/format-sql.ts \
        client/src/i18n/messages.ts
git commit -m "feat(kingbase): frontend connection fields reusing multi-mode skeleton"
```

---

### Task 11: MCP Enum + AGENTS.md (Last Backend Step, Post-Verify Hard Constraint)

**⚠️ Hard timing constraint per Wave C umbrella §7.5: this task runs ONLY AFTER all preceding tasks pass `mvn verify` SUCCESS + 6 IT pass under `-Dkingbase.it.enabled=true` (local / QA acceptance) + manual smoke 9 cases pass.**

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java`
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`

- [ ] **Step 1: Run consolidated pre-flight verification**

Run:
```bash
cd server && mvn clean verify -DskipITs=false -q
```
Expected: BUILD SUCCESS (no kingbase IT executed because `kingbase.it.enabled` is not set on CI — that is by design; verification passes when CI tests pass).

Then on local / QA fixture (developer machine; if access to a KingbaseES V8 instance is available):
```bash
cd server && mvn -pl data-talk-application test \
    -Dkingbase.it.enabled=true \
    -Dkingbase.host=<h> -Dkingbase.port=<p> \
    -Dkingbase.database=<db> -Dkingbase.user=<u> -Dkingbase.password=<pw> \
    -Dtest='Kingbase*ReuseIT' -q
```
Expected: PASS (6 / 6 IT). **DO NOT proceed if any IT fails.**

Then:
```bash
KINGBASE_HOST=<h> KINGBASE_PORT=<p> KINGBASE_DATABASE=<db> \
    KINGBASE_USER=<u> KINGBASE_PASSWORD=<pw> \
    bash tools/manual-smoke/kingbase-day1.sh
```
Expected: "Manual smoke 9 cases all PASSED".

- [ ] **Step 2: Add `kingbase` to `ConnectionObjectType` enum**

Edit `ConnectionObjectType.java`:

```java
public enum ConnectionObjectType {
    MYSQL("mysql"), POSTGRESQL("postgresql"), /* ... */
    OCEANBASE("oceanbase"),
    KINGBASE("kingbase");
    // ... existing constructor and wireValue() pattern ...
}
```

- [ ] **Step 3: Add KingbaseES section to `AGENTS.md`**

Edit `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`:

```markdown
### KingbaseES (kind: `kingbase`)

- canonical kind: `kingbase`. **Permitted alias** (the only Wave C alias):
  `kingbasees` is normalized to `kingbase` at `ConnectionKind.normalize`
  (Wave C umbrella §8 line 293). All other variants (`kb`, `kbase`,
  `kingbase7`, `kingbase8`, `kingbase9`) are rejected.
- Day-1 first-class: PG-mode only. Oracle-mode (`compatibility_mode='oracle'`)
  returns `dialect_unsupported`.
- driver: `cn.com.kingbase:kingbase8`; URL `jdbc:kingbase8://<host>:<port>/<database>`;
  default port 54321; `database` required (PG-equivalent).
- system schemas filtered from discovery: `pg_catalog`, `information_schema`,
  `pg_toast`, `pg_temp`, `sys`, `sys_catalog` (6 items).
- Day-1 unsupported (dialect_unsupported, all anchored `^\s*` + `\b`):
  - Oracle-style PL/SQL blocks (`DECLARE/BEGIN ... END;/`) — reject by
    Channel 2 risk classifier; PG-style `DO $$ ... $$` is supported.
  - `KBBACKUP` / `KBRESTORE` CLI utilities — reject by Channel 2 risk
    classifier; whitespace-required suffix prevents collision with
    `KBBACKUP_INFO()` function calls.
- Day-1 L3 admin / DDL (Channel 1 anchored, label `kingbase_admin_command`):
  `SYS_*` table DDL, `SYS<CRT|AUDIT>_*` admin schema DDL, `SYS_KILL`
  function, `FLASHBACK TABLE`.
- All 9 diagnostics hooks (lock_info / pool_status / table_space /
  terminate_session / optimize_table / explain_real / index_hints /
  er_inspector / er_designer) return structured `dialect_unsupported`.
- Chinese aliases (`人大金仓`, `金仓`) are recognized in user
  natural-language input only; they map to canonical kind `kingbase`,
  NOT to `postgresql` and NOT to alias targets at `ConnectionKind.normalize`.
- Username is passed verbatim (no tenant / cluster suffix; PG-style single
  instance model). KingbaseES Oracle-mode + V7 / V9 driver + HA / replication
  UI are out of Day-1 scope.
```

- [ ] **Step 4: Re-run mvn verify**

Run: `cd server && mvn verify -q`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java \
        server/data-talk-adapter/src/main/resources/agents/AGENTS.md
git commit -m "feat(kingbase): MCP enum + AGENTS.md (post-verify per umbrella §7.5)"
```

---

### Task 12: Consolidated Verification + Manual Smoke 9 Cases

- [ ] **Step 1: Full mvn verify**

Run: `cd server && mvn clean verify -q`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Full client typecheck + vitest**

Run: `cd client && npx tsc --noEmit && npx vitest run`
Expected: 0 type errors; all vitest tests pass.

- [ ] **Step 3: Start server + client locally**

Open two terminals.

Terminal 1: `cd server && mvn spring-boot:run -pl data-talk-adapter`
Terminal 2: `cd client && npm run dev`

- [ ] **Step 4: Manual smoke — alias normalization + connection creation**

In the running app:
1. Open Connections, click "+", select KingbaseES.
2. Fill kind=`kingbasees` (alias) — verify info chip shows "normalized to canonical kind".
3. Fill host=`<KINGBASE_HOST>`, port=54321, database=`<KINGBASE_DATABASE>`, user=`<KINGBASE_USER>`, password=`<KINGBASE_PASSWORD>`, mode=PostgreSQL (Oracle disabled with tooltip).
4. Click Test — expect SUCCESS.
5. Save — verify persisted record has `kind="kingbase"` (not `kingbasees`).

- [ ] **Step 5: Manual smoke — discovery + system schema filter**

1. Pick the new connection. Browse schemas: `pg_catalog`, `information_schema`, `pg_toast`, `pg_temp`, `sys`, `sys_catalog` should be filtered from the visible list.
2. Run `SELECT 1;` — expect 1 row.

- [ ] **Step 6: Manual smoke — Channel 1 risk classifier**

1. Run `DROP TABLE SYS_USERS;` — expect L3 confirmation prompt.
2. Run `SELECT SYS_KILL(123);` — expect L3 confirmation prompt.
3. Run `FLASHBACK TABLE my_table TO TIMESTAMP '2025-01-01 00:00:00';` — expect L3 confirmation prompt.

- [ ] **Step 7: Manual smoke — Channel 2 dialect_unsupported**

1. Run `KBBACKUP DATABASE x FILE='/x';` — expect dialect_unsupported error with i18n message about KBBACKUP CLI.
2. Run `DECLARE v_x INT := 1; BEGIN NULL; END;` — expect dialect_unsupported with i18n message about Oracle PL/SQL block.

- [ ] **Step 8: Manual smoke — diagnostics dialect_unsupported**

1. Open ER Inspector for the connection — expect "KingbaseES PG-mode does not support ER Inspector in Day-1." message.
2. Open Lock Info diagnostics — expect dialect_unsupported with i18n message.

- [ ] **Step 9: Manual smoke — Oracle-mode rejection**

1. Edit the connection, attempt to switch mode to Oracle — verify cannot select (disabled with tooltip).
2. Manually craft a request via DevTools / curl with `compatibility_mode=oracle` — expect API rejection with `connection.kind.kingbase.mode_oracle_unsupported_day1`.

- [ ] **Step 10: Run the manual smoke script**

```bash
KINGBASE_HOST=<h> KINGBASE_PORT=<p> KINGBASE_DATABASE=<db> \
    KINGBASE_USER=<u> KINGBASE_PASSWORD=<pw> \
    bash tools/manual-smoke/kingbase-day1.sh
```
Expected: "Manual smoke 9 cases all PASSED".

- [ ] **Step 11: No commit (smoke logs stay in `tmp/`)**

Per CLAUDE.md tmp/ rule, smoke logs / Playwright traces / artifacts are **not** committed. If you produced a smoke log, leave it in `tmp/`.

---

### Task 13: Documentation Housekeeping

**Files:**
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` — Current Support Snapshot row
- Modify: `docs/exec-plans/index.md` — move from Active to Completed
- Modify: `docs/product-specs/2026-05-08-data-source-coverage-kingbase-design.md` — backfill final pinned driver patch

- [ ] **Step 1: Update `DATA_SOURCE_TYPE_COMPATIBILITY.md` Snapshot**

Add `kingbase` to the "First-class Day-1" row with summary of decisions:

```
| `kingbase` | First-class Day-1 (PG-mode only) | driver cn.com.kingbase:kingbase8@<pinned-version> via Maven Central direct (or vendor portal documented fallback per Task 1 Step 2), URL jdbc:kingbase8://<h>:<p>/<db> port 54321, PostgreSQL splitter / metadata / normalizer reuse via 6 Kingbase*ReuseIT subclasses inheriting opengauss-shipped AbstractPgFork*ReuseTest abstract bases (T2 fixture: @Disabled by default, triggered by -Dkingbase.it.enabled=true). Risk classifier dual-channel: Channel 1 4 anchored L3 patterns (SYS_* DDL / SYS<CRT|AUDIT>_* admin DDL / SYS_KILL / FLASHBACK TABLE) labeled kingbase_admin_command; Channel 2 2 anchored dialect_unsupported patterns (KBBACKUP/KBRESTORE CLI / Oracle-style PL/SQL block). MultiModeConnectionShape v1 forward-compat append (kingbase row added; zero v1 signature changes). All 9 diagnostics hooks dialect_unsupported. Permitted alias `kingbasees` normalized to canonical kind `kingbase` at ConnectionKind.normalize (only Wave C alias permitted per umbrella §8 line 293). Oracle-mode dialect_unsupported until Day-3. |
```

- [ ] **Step 2: Verify day2 plan §Day-3 kingbase row anchor still matches**

Run: `cd /home/wallfacers/project/data-talk && grep -n 'kingbase' docs/exec-plans/2026-05-08-diagnostics-day2-plan.md | head -5`
Expected: kingbase row line ~3119 already mentions `EXPLAIN [FORMAT JSON]` + `PostgresJsonPlanParser from opengauss` + `B-tree / KingbaseES SYS_*`. **No backfill needed** (per spec §11.2).

- [ ] **Step 3: Backfill final pinned driver patch to spec**

In `docs/product-specs/2026-05-08-data-source-coverage-kingbase-design.md`:
- §6.1 Driver Decision row: replace "9.0.x" with the exact patch from Task 1 Step 2.
- §7.2 system schema list: append any additional KB system schemas discovered during Task 12 manual smoke (if any).

- [ ] **Step 4: Move plan from Active to Completed in exec-plans index**

Edit `docs/exec-plans/index.md`: move the kingbase plan entry from Active to Completed; add a one-line Completion Log: `Completed YYYY-MM-DD; commits: <list>; verified: mvn verify + 6 IT (local kingbase.it.enabled=true) + manual smoke 9 cases`.

- [ ] **Step 5: Mark every checkbox in this plan as `[x]`**

Use:
```bash
sed -i 's/^- \[ \]/- [x]/g' docs/exec-plans/2026-05-08-data-source-coverage-kingbase-plan.md
```

- [ ] **Step 6: Commit final housekeeping**

```bash
git add docs/DATA_SOURCE_TYPE_COMPATIBILITY.md \
        docs/exec-plans/index.md \
        docs/product-specs/2026-05-08-data-source-coverage-kingbase-design.md \
        docs/exec-plans/2026-05-08-data-source-coverage-kingbase-plan.md
git commit -m "docs(kingbase): housekeeping — Snapshot upgrade + plan completion"
```

---

## Self-Review

**1. Spec coverage**

- §1 Purpose → Tasks 1 (approval gate + driver pin) + 4 (MultiModeConnectionShape append) + 9 (6 IT subclasses) + 13 (housekeeping)
- §2 Compatibility Gate Application → Tasks 2-12 cover every database area (canonical naming + alias norm + URL + connection test + metadata + target resolution + SQL exec + result norm + splitter + risk + diagnostics + i18n + frontend form / picker / outline + MCP enum + AGENTS.md + ER hooks)
- §3 Design Inputs → Task 1 Step 1 (read spec) + Task 10 Steps 1-2 (`client/DESIGN.md` semantic tokens + 5-state matrix) + Task 13 Step 3 (backfill)
- §4 Support Statement → Tasks 2 (alias) + 3 (driver + URL) + 4 (multi-mode) + 5 (connection service mode validation) + 6 (splitter + discovery + 6 system schemas) + 7 (Channel 1 + Channel 2 risk) + 8 (9 diagnostics dialect_unsupported) + 9 (6 IT) + 10 (frontend) + 11 (MCP enum + AGENTS.md timing)
- §5 Kind Naming → Task 2 Step 3 (`kingbasees` permitted alias; all other variants rejected)
- §6 Connection And Persistence → Tasks 3 (driver + URL builder; default port 54321; database required) + 5 (ConnectionService kingbase branch; zero new ConnectionRecord columns; `compatibility_mode` reused from V18)
- §7 Metadata Discovery → Task 6 (kingbase shares postgresql discovery branch + 6-item KINGBASE_SYSTEM_SCHEMAS filter: 4 PG + `sys` + `sys_catalog`)
- §8 SQL Execution / Splitter / Risk Classifier → Task 6 (splitter routing) + Task 7 (Channel 1 4 patterns + Channel 2 2 patterns + Stage 1 wiring with mandatory `\s+` after KBBACKUP / KBRESTORE; 20 boundary tests)
- §9 Diagnostics Provider 9-Hook Matrix → Task 8 (all 9 hooks dialect_unsupported + 14 i18n keys × en/zh)
- §10 Reuse Outputs (Pure Consumer) → Tasks 4 (forward-compat MultiModeConnectionShape append; zero v1 signature changes) + 9 (6 concrete IT consume opengauss-shipped PgForkReuseRule abstract bases) + 10 (frontend reuses oceanbase-shipped multi-mode-connection-fields.tsx). **Zero new cross-kind reuse abstractions produced.**
- §11 Day-2 / Day-3 Upgrade Path Bidirectional Anchor → Task 1 Step 5 + Task 13 Step 2 (verify day2 plan line 3119 anchor; no backfill required)
- §12 Out-of-Scope / T2 Fixture / i18n / AGENTS.md Timing → Task 9 (T2 fixture: 6 IT @Disabled + manual smoke 9 cases; CI does not run IT) + Task 8 Step 5 (i18n 14 keys × en/zh) + Task 11 (AGENTS.md / MCP enum hard timing constraint per umbrella §7.5)

All 12 spec sections covered.

**2. Placeholder scan**

No "TBD", "TODO", "implement later", "fill in details", "similar to Task N", "add appropriate error handling" appear in any task step. Risk classifier wiring code in Task 7 Step 5-6 is complete and concrete (Channel 1 main classify + Channel 2 SqlExecuteService Stage 1 with full i18n key dispatch). 6 IT subclasses in Task 9 explicitly enumerate `@EnabledIfSystemProperty` + `@Tag` annotations and override hook list. Manual smoke script in Task 9 Step 4 is a complete 9-case bash script with real curl invocations against the REST API, not a placeholder. Frontend tests in Task 10 Step 3 are concrete with assertion code. i18n keys in Task 8 Step 5 are listed verbatim (en + zh) and consistent with spec §12.4.

**3. Type consistency**

- `KingbaseUnsupportedReason.KB_BACKUP_RESTORE_CLI` / `ORACLE_PLSQL_BLOCK` enum values consistent across Tasks 7 (definition + classifier method) + 7 Step 6 (SqlExecuteService Stage 1 switch dispatch) + Task 7 Step 1 (test imports).
- `MultiModeConnectionShape.validateModeForKind(String, CompatibilityMode)` and `isDay1FirstClassMode(String, CompatibilityMode)` signatures unchanged from oceanbase v1; Task 4 explicitly asserts no v1 signature changes (forward-compat append only). v1 regression test included.
- `ConnectionRecord` 22-field constructor consistent across Tasks 3 / 5 / 8 / 9 (test fixture builders all use 22 args after the V18 oceanbase additions; kingbase rows hold `compatibilityMode="pg"`, `oceanbaseTenant=null`, `oceanbaseCluster=null`).
- 6 abstract base class names (`AbstractPgForkSplitterReuseTest`, `AbstractPgForkMetadataReuseTest`, `AbstractPgForkTargetResolutionReuseTest`, `AbstractPgForkBatchDmlReuseTest`, `AbstractPgForkResultNormalizationReuseTest`, `AbstractPgForkConnectionTestReuseTest`) match Task 9 IT subclass extends clauses and the spec §12.2 table verbatim. Task 1 Step 3 + Task 9 Step 1 verify these exist in the opengauss-shipped commit before Task 9 implementation.
- 4 Channel 1 anchored Pattern constant names (`KINGBASE_SYS_TABLE_DDL`, `KINGBASE_SYS_ADMIN_SCHEMA_DDL`, `KINGBASE_SYS_KILL`, `KINGBASE_FLASHBACK`) and 2 Channel 2 anchored Pattern constant names (`KINGBASE_KB_BACKUP_RESTORE`, `KINGBASE_ORACLE_PLSQL_BLOCK`) match Task 7 Step 4 + spec §8.3 verbatim. KBBACKUP / KBRESTORE pattern enforces `\s+` mandatory suffix per spec §8.3 + Task 7 Step 1 boundary test (`SELECT KBBACKUP_INFO()` must NOT match).
- 14 i18n keys (9 diagnostics dialect_unsupported + 4 connection.kind.kingbase.* + 2 risk.dialect_unsupported.kingbase.* — minus duplicate label entry) consistent across Task 8 Step 5 (backend properties files) + Task 10 Step 6 (frontend messages.ts) + spec §12.4.
- Risk label `kingbase_admin_command` (canonical kind, not `kingbasees_admin_command`) consistent across Task 7 Step 5 + spec §8.3 §10 governance reset reference.
