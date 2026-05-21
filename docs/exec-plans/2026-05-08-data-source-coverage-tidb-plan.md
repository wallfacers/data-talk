# Data Source Coverage: TiDB Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add verified first-class TiDB support as canonical kind `tidb` (no aliases) for OSS / self-hosted clusters, plus produce the `MySqlProtocolReuseRule` cross-kind reuse test kit mandated by Wave C umbrella §8.

**Architecture:** TiDB is MySQL wire-protocol compatible. Backend reuses `mysql-connector-j` (zero new dependency), `MySqlSqlStatementSplitter`, MySQL metadata path, MySQL batch DML path, and `JdbcResultValueNormalizer` mysql baseline — but every reuse point is proven by a TiDB-specific concrete subclass of a `MySqlProtocolReuseRule` abstract base, so future `oceanbase` MySQL-mode reuse has the same scaffolding. TiDB-only verbs (`ADMIN`, `SPLIT TABLE`, `BACKUP`, `FLASHBACK`, `PLACEMENT`, `BATCH ON`, `SET GLOBAL`, `KILL TIDB`, `IMPORT INTO`, `RECOVER TABLE`) get an independent `classifyTidbSpecific(sql)` branch in `CalciteSqlRiskAnalyzer` modeled after the StarRocks branch (lines 542–637). Diagnostics and ER are structured `dialect_unsupported` Day-1.

**Tech Stack:** Java 21, Spring Boot 3.5, mysql-connector-j (existing), Testcontainers (`pingcap/tidb` T1 fixture), JUnit 5, AssertJ, React 19, TypeScript, Vitest.

---

## Design Inputs

- [docs/product-specs/2026-05-08-data-source-coverage-tidb-design.md](../product-specs/2026-05-08-data-source-coverage-tidb-design.md) — spec being implemented. **Status: codex-approved 2026-05-08.**
- [docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md](../product-specs/2026-05-08-data-source-coverage-wave-c-design.md) — Wave C umbrella; locks `MySqlProtocolReuseRule` kit shape (§8), Reuse-With-Tests policy (§7.3), Day-1 unsupported set (§5).
- [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md) — hard compatibility checklist.
- [client/DESIGN.md](../../client/DESIGN.md) — frontend semantic tokens, accessible controls, Stage state, i18n.
- [docs/product-specs/2026-05-01-data-source-coverage-apache-doris-design.md](../product-specs/2026-05-01-data-source-coverage-apache-doris-design.md) and [docs/exec-plans/2026-05-01-data-source-coverage-apache-doris-plan.md](./2026-05-01-data-source-coverage-apache-doris-plan.md) — MySQL-protocol reuse precedent.

## Files

**Backend — new:**
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/TiDbDiagnosticsProvider.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/mysqlprotocol/AbstractMySqlSplitterEquivalenceTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/mysqlprotocol/AbstractMySqlMetadataReuseTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/mysqlprotocol/AbstractMySqlTargetResolutionReuseTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/mysqlprotocol/AbstractMySqlBatchDmlReuseTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/mysqlprotocol/AbstractMySqlResultNormalizationReuseTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/mysqlprotocol/AbstractMySqlConnectionTestReuseTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/mysqlprotocol/package-info.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/tidb/TiDbContainerSupport.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/tidb/TiDbSplitterEquivalenceIT.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/tidb/TiDbMetadataReuseIT.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/tidb/TiDbTargetResolutionReuseIT.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/tidb/TiDbBatchDmlReuseIT.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/tidb/TiDbResultNormalizationReuseIT.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/tidb/TiDbConnectionTestReuseIT.java`
- `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/TiDbDiagnosticsProviderTest.java`

**Backend — modify:**
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java:139`
- `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java:185`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java:490,572`
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java:165-197`
- `server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/DiagnosticsService.java:286-325`
- `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java:30`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java:280`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java:39`
- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- Existing tests under `server/data-talk-application/src/test/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzerTest.java` and `server/data-talk-application/src/test/java/com/datatalk/application/connection/JdbcUrlBuilderTest.java`

**Frontend — modify:**
- `client/src/features/settings/data-sources/connection-form-dialog.tsx`
- `client/src/features/chat/components/composer/data-source-picker.tsx`
- `client/src/features/stage/components/connection-picker.tsx`
- `client/src/features/stage/components/query-editor-toolbar.tsx`
- `client/src/features/stage/utils/format-sql.ts`
- `client/src/features/stage/utils/parse-sql-outline.ts`
- `client/src/i18n/messages.ts`
- Generated types under `client/src/services/api/generated/`

**Docs:**
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` (Current Support Snapshot, ER matrix, Wave C Tracking)
- `docs/exec-plans/index.md` (Active → Completed)
- `docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md` (umbrella §5 license fix — independent mini commit per Task 14)

---

### Task 1: Approval, Gate, And Kickoff Decisions

- [x] **Step 1: Confirm spec approval status**

Run:

```bash
grep -n "^Status:" docs/product-specs/2026-05-08-data-source-coverage-tidb-design.md
```

Expected: `Status: Draft for review (codex external review pending)` — but the user has confirmed codex review passed on 2026-05-08. The implementer flips the status to `Status: Approved` as the first commit.

- [x] **Step 2: Re-read mandatory gates**

Read:
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` (full)
- `client/DESIGN.md` (full)
- `docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md` (umbrella, §5 / §7 / §8)
- `docs/product-specs/2026-05-08-data-source-coverage-tidb-design.md` (this child design, full)
- `docs/bugs/index.md` — verify zero open BUGs touching `mysql` / `apache_doris` / connection / risk analyzer modules

- [x] **Step 3: Pin driver version**

Run:

```bash
grep -A 2 "<artifactId>mysql-connector-j" server/data-talk-infrastructure/pom.xml
```

Record the exact version in the commit body of Task 2 Step 5.

- [x] **Step 4: Pin TiDB Testcontainers image tag**

Pick the latest `pingcap/tidb` LTS image with explicit version (do **not** use `:latest` in CI). At spec time, target `pingcap/tidb:v7.5.5` or newer LTS. Record the chosen tag in `TiDbContainerSupport.java` (Task 8) as a `public static final String` constant.

- [x] **Step 5: Flip spec status and commit**

Edit `docs/product-specs/2026-05-08-data-source-coverage-tidb-design.md` line 4 from `Status: Draft for review (codex external review pending)` to `Status: Approved (codex review passed 2026-05-08)`.

```bash
git add docs/product-specs/2026-05-08-data-source-coverage-tidb-design.md
git commit -m "docs(tidb): flip spec status to Approved after codex review"
```

---

### Task 2: ConnectionKind, JdbcUrlBuilder, ConnectionService

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java:139`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/connection/JdbcUrlBuilderTest.java`

- [x] **Step 1: Write failing JdbcUrlBuilder tests**

Add three test methods to `JdbcUrlBuilderTest`:

```java
@Test
void buildTidbUrlWithDatabase() {
    ConnectionRecord c = recordBuilder()
        .kind("tidb").host("127.0.0.1").port(4000).databaseName("analytics").build();
    assertThat(JdbcUrlBuilder.build(c))
        .isEqualTo("jdbc:mysql://127.0.0.1:4000/analytics?useSSL=false&allowPublicKeyRetrieval=true");
}

@Test
void buildTidbUrlWithoutDatabase() {
    ConnectionRecord c = recordBuilder()
        .kind("tidb").host("127.0.0.1").port(4000).databaseName(null).build();
    assertThat(JdbcUrlBuilder.build(c))
        .isEqualTo("jdbc:mysql://127.0.0.1:4000/?useSSL=false&allowPublicKeyRetrieval=true");
}

@Test
void buildTidbUrlEmptyDatabaseTreatedAsNull() {
    ConnectionRecord c = recordBuilder()
        .kind("tidb").host("h").port(4000).databaseName("").build();
    assertThat(JdbcUrlBuilder.build(c))
        .isEqualTo("jdbc:mysql://h:4000/?useSSL=false&allowPublicKeyRetrieval=true");
}
```

If `recordBuilder()` does not exist, follow the pattern from existing `buildMysqlUrl*` tests in the same file.

- [x] **Step 2: Run failing tests**

Run:

```bash
cd server && mvn -q -pl data-talk-application test -Dtest=JdbcUrlBuilderTest
```

Expected: 3 new tests fail with "unsupported database kind: tidb".

- [x] **Step 3: Add `TIDB` constant**

Edit `ConnectionKind.java`. Add after `HIVE` constant:

```java
    public static final String TIDB = "tidb";
```

- [x] **Step 4: Add `TIDB` branch to `JdbcUrlBuilder.build(ConnectionRecord)`**

Insert after the `MYSQL` case (around the existing `case ConnectionKind.MYSQL ->` block):

```java
            case ConnectionKind.TIDB ->
                db != null && !db.isBlank()
                    ? "jdbc:mysql://" + c.host() + ":" + c.port() + "/" + db + "?useSSL=false&allowPublicKeyRetrieval=true"
                    : "jdbc:mysql://" + c.host() + ":" + c.port() + "/?useSSL=false&allowPublicKeyRetrieval=true";
```

Note: the existing `MYSQL` branch does **not** append `?useSSL=...` — the TiDB branch adds them as TiDB-OSS-development-friendly defaults per design §6.3. Existing MYSQL behavior unchanged.

- [x] **Step 5: Verify tests pass**

Run:

```bash
cd server && mvn -q -pl data-talk-application test -Dtest=JdbcUrlBuilderTest
```

Expected: all `JdbcUrlBuilderTest` tests pass (existing + 3 new).

- [x] **Step 6: Extend `ConnectionService.java:139` predicate**

Per spec §6.4, the line-139 branch is a MySQL-protocol-generic timeout action (`connectTimeout=<ms>&socketTimeout=<ms>`). Change:

```java
        if (kind.equals(ConnectionKind.MYSQL)) {
```

to

```java
        if (kind.equals(ConnectionKind.MYSQL) || kind.equals(ConnectionKind.TIDB)) {
```

- [x] **Step 7: Add ConnectionService timeout extension test**

In `server/data-talk-application/src/test/java/com/datatalk/application/connection/ConnectionServiceTest.java`, add:

```java
@Test
void tidbConnectionAppendsMillisecondTimeoutParams() {
    ArgumentCaptor<ConnectionRecord> captor = ArgumentCaptor.forClass(ConnectionRecord.class);
    when(repo.create(captor.capture())).thenReturn(captor.getValue());
    svc.create("TiDB Local", "tidb", "127.0.0.1", 4000, "analytics", "root", "", 5000, null, null, null, null, null);
    ConnectionRecord saved = captor.getValue();
    assertThat(JdbcUrlBuilder.build(saved))
        .contains("connectTimeout=5000")
        .contains("socketTimeout=5000");
}
```

The signature mirrors the existing `apache_doris` test in the same file.

- [x] **Step 8: Run + commit**

```bash
cd server && mvn -q -pl data-talk-application test \
  -Dtest=JdbcUrlBuilderTest,ConnectionServiceTest
```

Expected: PASS.

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java \
        server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java \
        server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java \
        server/data-talk-application/src/test/java/com/datatalk/application/connection/JdbcUrlBuilderTest.java \
        server/data-talk-application/src/test/java/com/datatalk/application/connection/ConnectionServiceTest.java
git commit -m "feat(tidb): add ConnectionKind.TIDB + JdbcUrlBuilder branch + timeout"
```

---

### Task 3: Splitter Routing And SqlExecuteService Branches

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java:30`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java:490,572`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/sql/DefaultSqlStatementSplittersTest.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/sql/SqlExecuteServiceTest.java` (only if tidb kind already covered indirectly; otherwise see Task 8 IT)

- [x] **Step 1: Add failing splitter routing test**

In `DefaultSqlStatementSplittersTest.java`, add:

```java
@Test
void tidbRoutesToMysqlSplitter() {
    String sql = "DELIMITER //\nCREATE PROCEDURE p() BEGIN SELECT 1; END //\nDELIMITER ;";
    List<String> parts = splitters.split("tidb", sql);
    assertThat(parts).hasSize(1);
    assertThat(parts.get(0)).contains("CREATE PROCEDURE");
}
```

- [x] **Step 2: Run failing test**

```bash
cd server && mvn -q -pl data-talk-infrastructure test -Dtest=DefaultSqlStatementSplittersTest
```

Expected: FAIL — current `tidb` falls to generic splitter, splits on `;` and produces multiple parts.

- [x] **Step 3: Extend the splitter routing predicate**

Edit `DefaultSqlStatementSplitters.java:30` from:

```java
        if ("mysql".equalsIgnoreCase(connectionKind) || "mariadb".equalsIgnoreCase(connectionKind) || "apache_doris".equalsIgnoreCase(connectionKind) || "starrocks".equalsIgnoreCase(connectionKind)) {
            return mysqlSplitter.split(sql);
        }
```

to:

```java
        if ("mysql".equalsIgnoreCase(connectionKind) || "mariadb".equalsIgnoreCase(connectionKind) || "apache_doris".equalsIgnoreCase(connectionKind) || "starrocks".equalsIgnoreCase(connectionKind) || "tidb".equalsIgnoreCase(connectionKind)) {
            return mysqlSplitter.split(sql);
        }
```

- [x] **Step 4: Verify splitter test passes**

```bash
cd server && mvn -q -pl data-talk-infrastructure test -Dtest=DefaultSqlStatementSplittersTest
```

Expected: PASS.

- [x] **Step 5: Extend SqlExecuteService.java:490 batch DML predicate**

Locate the predicate currently reading:

```java
        if ("mysql".equalsIgnoreCase(connection.kind()) || "mariadb".equalsIgnoreCase(connection.kind()) || "apache_doris".equalsIgnoreCase(connection.kind()) || "starrocks".equalsIgnoreCase(connection.kind()) || "trino".equalsIgnoreCase(connection.kind())) {
```

Append `|| "tidb".equalsIgnoreCase(connection.kind())`.

- [x] **Step 6: Extend SqlExecuteService.java:572 same shape**

Locate the predicate currently reading:

```java
            || "apache_doris".equalsIgnoreCase(context.connection().kind()))
```

Adjacent lines form a multi-kind disjunction; identify all `apache_doris` matches in the same `if` block at line 572 and append `|| "tidb".equalsIgnoreCase(context.connection().kind())` consistently.

- [x] **Step 7: Run full sql-related tests**

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure test \
  -Dtest=DefaultSqlStatementSplittersTest,SqlExecuteServiceTest
```

Expected: PASS.

- [x] **Step 8: Commit**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/sql/DefaultSqlStatementSplittersTest.java \
        server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java
git commit -m "feat(tidb): route tidb to MySQL splitter and batch DML path"
```

---

### Task 4: Target Discovery, Schema Read, System Filter

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java:185`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java:280`

- [x] **Step 1: Add failing target discovery test**

In `server/data-talk-application/src/test/java/com/datatalk/application/session/ConnectionTargetDiscoveryServiceTest.java` (find the existing `apache_doris` test and copy):

```java
@Test
void tidbHasNoIndependentSchemaNamespace() {
    assertThat(svc.hasIndependentSchemaNamespace("tidb")).isFalse();
}

@Test
void tidbFiltersTidbSystemDatabases() {
    List<String> dbs = List.of("analytics", "INFORMATION_SCHEMA", "mysql",
        "PERFORMANCE_SCHEMA", "METRICS_SCHEMA", "sys", "myapp");
    assertThat(svc.filterSystemDatabases("tidb", dbs))
        .containsExactly("analytics", "myapp");
}
```

The second test asserts that **`METRICS_SCHEMA` is also filtered for tidb** — this is the documented divergence from the mysql filter list (spec §7.2).

- [x] **Step 2: Run failing tests**

```bash
cd server && mvn -q -pl data-talk-application test -Dtest=ConnectionTargetDiscoveryServiceTest
```

Expected: FAIL.

- [x] **Step 3: Extend `hasIndependentSchemaNamespace` (line 185)**

The current line 185 reads `return !ConnectionKind.MYSQL.equals(normalized) ...`. Locate the chain that lists mysql-protocol kinds (mysql / mariadb / apache_doris / starrocks) and append `&& !ConnectionKind.TIDB.equals(normalized)` so TiDB returns `false` for "has independent schema namespace".

- [x] **Step 4: Extend system database filter for TiDB**

Locate the system-database filter list. If the filter is kind-conditional (per spec §7.2 the tidb filter must include `METRICS_SCHEMA` in addition to mysql's list), add a kind-conditional branch that returns `Set.of("INFORMATION_SCHEMA", "mysql", "PERFORMANCE_SCHEMA", "METRICS_SCHEMA", "sys")` for `tidb`. If the filter is currently a single shared set, refactor to a kind-conditional method `systemDatabasesFor(String kind)` and route mysql/mariadb/apache_doris/starrocks to the existing list and `tidb` to the extended list. Add a unit test asserting the difference.

- [x] **Step 5: Extend `ReadSchemaAction.java:280`**

Current line 280 chains seven kinds. Append `|| "tidb".equalsIgnoreCase(kind)` to the disjunction.

- [x] **Step 6: Run + commit**

```bash
cd server && mvn -q -pl data-talk-application,data-talk-adapter test \
  -Dtest=ConnectionTargetDiscoveryServiceTest,ReadSchemaActionIT
```

Expected: PASS.

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java \
        server/data-talk-application/src/test/java/com/datatalk/application/session/ConnectionTargetDiscoveryServiceTest.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java
git commit -m "feat(tidb): wire target discovery and schema read for tidb"
```

---

### Task 5: TiDbDiagnosticsProvider And DiagnosticsService Branches

**Files:**
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/TiDbDiagnosticsProvider.java`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/TiDbDiagnosticsProviderTest.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/DiagnosticsService.java:286-325`

- [x] **Step 1: Create `TiDbDiagnosticsProvider`**

Write `TiDbDiagnosticsProvider.java` mirroring `DorisDiagnosticsProvider`:

```java
package com.datatalk.infra.diagnostics;

import com.datatalk.application.diagnostics.AbstractDiagnosticsProvider;
import com.datatalk.application.diagnostics.DiagnosticResult;
import com.datatalk.i18n.Translator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Set;

@Component
public class TiDbDiagnosticsProvider extends AbstractDiagnosticsProvider {

    public TiDbDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedKinds() {
        return Set.of("tidb");
    }

    @Override
    public DiagnosticResult explain(DataSource ds, String sql) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.explain_unsupported", "tidb"));
    }

    @Override
    public DiagnosticResult indexHints(DataSource ds, String sql) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.index_hints_unsupported", "tidb"));
    }

    @Override
    public DiagnosticResult lockInfo(DataSource ds) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.lock_not_supported", "tidb"));
    }

    @Override
    public DiagnosticResult poolStatus(DataSource ds) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.pool_not_supported", "tidb"));
    }

    @Override
    public DiagnosticResult tableSpace(DataSource ds, String table) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.tablespace_not_supported", "tidb"));
    }

    @Override
    public DiagnosticResult terminateSession(DataSource ds, String sessionId) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.terminate_not_supported", "tidb"));
    }

    @Override
    public DiagnosticResult optimizeTable(DataSource ds, String table) {
        return DiagnosticResult.unsupported(translator.get("diagnostics.optimize_not_supported", "tidb"));
    }
}
```

If `AbstractDiagnosticsProvider` exposes a different method shape (e.g. `Set<String> supportedCapabilities()` instead of `supportedKinds()`), match the exact shape used by `DorisDiagnosticsProvider`. Read that file first.

- [x] **Step 2: Create `TiDbDiagnosticsProviderTest`**

Mirror `DorisDiagnosticsProviderTest`, with seven hooks asserting `DiagnosticResult.isUnsupported()` and the message contains `"tidb"`.

- [x] **Step 3: Extend `DiagnosticsService.java:286-325` switches**

For each of the five `case "apache_doris" -> translator.get("diagnostics.<verb>_not_supported", kind);` lines (lock_info, pool_status, table_space, terminate_session, optimize_table), add a sibling `case "tidb" ->` line with the same translator key. Concretely, where the current code reads:

```java
                case "apache_doris" -> translator.get("diagnostics.lock_not_supported", kind);
```

add immediately above or below:

```java
                case "tidb" -> translator.get("diagnostics.lock_not_supported", kind);
```

Repeat for `pool`, `tablespace`, `terminate`, `optimize`.

- [x] **Step 4: Run + commit**

```bash
cd server && mvn -q -pl data-talk-application,data-talk-infrastructure test \
  -Dtest=TiDbDiagnosticsProviderTest,DiagnosticsServiceTest,DiagnosticsClosedLoopIT
```

Expected: PASS.

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/TiDbDiagnosticsProvider.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/TiDbDiagnosticsProviderTest.java \
        server/data-talk-application/src/main/java/com/datatalk/application/diagnostics/DiagnosticsService.java
git commit -m "feat(tidb): add TiDbDiagnosticsProvider with structured unsupported"
```

---

### Task 6: CalciteSqlRiskAnalyzer `classifyTidbSpecific`

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java:165-197` (dispatcher) and add new method `classifyTidbSpecific`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzerTest.java`

This is the largest single Java task. Break into substeps.

- [x] **Step 1: Add failing risk-rule tests (cascade order)**

In `CalciteSqlRiskAnalyzerTest.java`, add test methods following the existing `apache_doris` and `starrocks` patterns. Cover all rules from spec §8.3.2, with **explicit ordering tests** that prove specific patterns are matched before the generic `show` / `admin` catch-alls:

```java
// L1: read-only introspection (must beat generic show / admin)
@Test
void tidb_show_placement_is_l1() {
    assertThat(analyzer.analyze("SHOW PLACEMENT FOR DATABASE analytics", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L1);
}
@Test
void tidb_show_table_regions_is_l1() {
    assertThat(analyzer.analyze("SHOW TABLE t1 REGIONS", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L1);
}
@Test
void tidb_show_split_regions_is_l1() {
    assertThat(analyzer.analyze("SHOW SPLIT REGIONS", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L1);
}
@Test
void tidb_show_stats_is_l1() {
    assertThat(analyzer.analyze("SHOW STATS_HEALTHY", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L1);
}
@Test
void tidb_show_generic_is_l1() {
    assertThat(analyzer.analyze("SHOW DATABASES", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L1);
}
@Test
void tidb_admin_show_ddl_is_l1() {
    assertThat(analyzer.analyze("ADMIN SHOW DDL", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L1);
}
@Test
void tidb_admin_show_ddl_jobs_is_l1() {
    assertThat(analyzer.analyze("ADMIN SHOW DDL JOBS", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L1);
}

// L2: bounded write or heavy read
@Test
void tidb_split_table_is_l2() {
    assertThat(analyzer.analyze("SPLIT TABLE t1 BETWEEN (0) AND (1000) REGIONS 8", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L2);
}
@Test
void tidb_recover_table_is_l2() {
    assertThat(analyzer.analyze("RECOVER TABLE t1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L2);
}
@Test
void tidb_alter_table_compact_is_l2() {
    assertThat(analyzer.analyze("ALTER TABLE t1 COMPACT", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L2);
}
@Test
void tidb_admin_check_table_is_l2() {
    assertThat(analyzer.analyze("ADMIN CHECK TABLE t1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L2);
}

// L3: destructive or cluster-affecting
@Test
void tidb_admin_cancel_ddl_is_l3() {
    assertThat(analyzer.analyze("ADMIN CANCEL DDL JOBS 42", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_admin_pause_ddl_is_l3() {
    assertThat(analyzer.analyze("ADMIN PAUSE DDL JOBS 42", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_admin_resume_ddl_is_l3() {
    assertThat(analyzer.analyze("ADMIN RESUME DDL JOBS 42", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_admin_unrecognized_is_l3() {
    assertThat(analyzer.analyze("ADMIN RECOVER INDEX t1 idx_a", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_backup_database_is_l3() {
    assertThat(analyzer.analyze("BACKUP DATABASE analytics TO 's3://x/y'", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_restore_database_is_l3() {
    assertThat(analyzer.analyze("RESTORE DATABASE analytics FROM 's3://x/y'", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_import_into_is_l3() {
    assertThat(analyzer.analyze("IMPORT INTO t1 FROM 's3://x/data.csv'", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_load_data_infile_is_l3() {
    assertThat(analyzer.analyze("LOAD DATA INFILE '/tmp/x.csv' INTO TABLE t1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_flashback_cluster_is_l3() {
    assertThat(analyzer.analyze("FLASHBACK CLUSTER TO TIMESTAMP '2026-01-01 00:00:00'", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_flashback_database_is_l3() {
    assertThat(analyzer.analyze("FLASHBACK DATABASE analytics", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_flashback_table_is_l3() {
    assertThat(analyzer.analyze("FLASHBACK TABLE analytics.t1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_alter_placement_policy_is_l3() {
    assertThat(analyzer.analyze("ALTER PLACEMENT POLICY p FOLLOWERS=3", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_create_placement_policy_is_l3() {
    assertThat(analyzer.analyze("CREATE PLACEMENT POLICY p FOLLOWERS=2", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_drop_placement_policy_is_l3() {
    assertThat(analyzer.analyze("DROP PLACEMENT POLICY p", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_kill_tidb_is_l3() {
    assertThat(analyzer.analyze("KILL TIDB 12345", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_set_global_is_l3() {
    assertThat(analyzer.analyze("SET GLOBAL tidb_gc_life_time = '24h'", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_session_set_is_not_l3() {
    // SET (not GLOBAL) is session-level — falls through to Calcite generic, not L3
    assertThat(analyzer.analyze("SET tidb_isolation_read_engines = 'tikv'", Category.QUERY, "tidb").riskLevel())
        .isNotEqualTo(RiskLevel.L3);
}
@Test
void tidb_batch_on_insert_is_l3() {
    assertThat(analyzer.analyze("BATCH ON id LIMIT 1000 INSERT INTO t2 SELECT * FROM t1", Category.QUERY, "tidb").riskLevel())
        .isEqualTo(RiskLevel.L3);
}
@Test
void tidb_batch_on_update_is_l3() {
    assertThat(analyzer.analyze("BATCH ON id LIMIT 1000 UPDATE t1 SET v = v + 1", Category.QUERY, "tidb").riskLevel())
        .isEqualTo(RiskLevel.L3);
}
@Test
void tidb_batch_on_delete_is_l3() {
    assertThat(analyzer.analyze("BATCH ON id LIMIT 1000 DELETE FROM t1 WHERE v < 0", Category.QUERY, "tidb").riskLevel())
        .isEqualTo(RiskLevel.L3);
}

// Standard SQL falls through to Calcite generic
@Test
void tidb_select_is_l1() {
    assertThat(analyzer.analyze("SELECT * FROM t1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L1);
}
@Test
void tidb_insert_is_l2() {
    assertThat(analyzer.analyze("INSERT INTO t1 VALUES (1)", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L2);
}
@Test
void tidb_update_with_where_is_l2() {
    assertThat(analyzer.analyze("UPDATE t1 SET v=1 WHERE id=1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L2);
}
@Test
void tidb_delete_with_where_is_l2() {
    assertThat(analyzer.analyze("DELETE FROM t1 WHERE id=1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L2);
}
@Test
void tidb_drop_table_is_l3() {
    assertThat(analyzer.analyze("DROP TABLE t1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_truncate_is_l3() {
    assertThat(analyzer.analyze("TRUNCATE TABLE t1", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_alter_is_l3() {
    assertThat(analyzer.analyze("ALTER TABLE t1 ADD COLUMN c INT", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
@Test
void tidb_grant_is_l3() {
    assertThat(analyzer.analyze("GRANT SELECT ON *.* TO u@'%'", Category.QUERY, "tidb").riskLevel()).isEqualTo(RiskLevel.L3);
}
```

- [x] **Step 2: Run failing tests**

```bash
cd server && mvn -q -pl data-talk-application test -Dtest=CalciteSqlRiskAnalyzerTest
```

Expected: many failures — TiDB statements currently fall through to generic Calcite which does not recognize them.

- [x] **Step 3: Add `case ConnectionKind.TIDB` to dispatcher**

Edit `CalciteSqlRiskAnalyzer.java:165-197` (`classifyDialectSpecific` method). Insert before the `return null;` line:

```java
        if (ConnectionKind.TIDB.equalsIgnoreCase(connectionKind)) {
            return classifyTidbSpecific(sql);
        }
```

- [x] **Step 4: Implement `classifyTidbSpecific`**

Add the method after `classifyHiveSpecific` (or wherever the kind-specific methods cluster ends), modeled after `classifyStarrocksSpecific` (lines 542–637). **Order is critical**: more-specific patterns first, generic catch-alls last:

```java
    private SqlRiskAnalysis classifyTidbSpecific(String sql) {
        String normalized = stripLeadingComments(sql).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;

        // L1: read-only introspection (specific SHOW/ADMIN forms first)
        if (normalized.startsWith("show placement")) {
            return SqlRiskAnalysis.low("tidb_show_placement");
        }
        if (normalized.startsWith("show table") && normalized.contains("regions")) {
            return SqlRiskAnalysis.low("tidb_show_regions");
        }
        if (normalized.startsWith("show split regions")) {
            return SqlRiskAnalysis.low("tidb_show_split_regions");
        }
        if (normalized.startsWith("show stats_")) {
            return SqlRiskAnalysis.low("tidb_show_stats");
        }
        if (normalized.startsWith("admin show ddl")) {
            return SqlRiskAnalysis.low("tidb_admin_show_ddl");
        }
        // Generic SHOW catch-all (after specific SHOW forms)
        if (startsWithKeyword(normalized, "show")) {
            return SqlRiskAnalysis.low("tidb_show");
        }
        if (startsWithKeyword(normalized, "describe") || startsWithKeyword(normalized, "desc")) {
            return SqlRiskAnalysis.low("tidb_describe");
        }
        if (startsWithKeyword(normalized, "explain")) {
            // Includes EXPLAIN ANALYZE — read-only-equivalent per spec §8.3.2
            return SqlRiskAnalysis.low("tidb_explain");
        }

        // L2: bounded write or heavy read
        if (normalized.startsWith("split table")) {
            return SqlRiskAnalysis.medium("tidb_split_table");
        }
        if (normalized.startsWith("recover table")) {
            return SqlRiskAnalysis.medium("tidb_recover_table");
        }
        if (normalized.startsWith("alter table") && normalized.contains("compact")) {
            return SqlRiskAnalysis.medium("tidb_alter_compact");
        }
        if (normalized.startsWith("admin check table") || normalized.startsWith("admin check index")) {
            return SqlRiskAnalysis.medium("tidb_admin_check");
        }
        if (startsWithKeyword(normalized, "analyze")) {
            return SqlRiskAnalysis.medium("tidb_analyze");
        }

        // L3: destructive or cluster-affecting (specific ADMIN forms before generic)
        if (normalized.startsWith("admin cancel ddl")
            || normalized.startsWith("admin pause ddl")
            || normalized.startsWith("admin resume ddl")) {
            return SqlRiskAnalysis.high("tidb_admin_ddl_jobs");
        }
        if (startsWithKeyword(normalized, "admin")) {
            // Conservative catch-all for any other ADMIN verb
            return SqlRiskAnalysis.high("tidb_admin_unrecognized");
        }
        if (normalized.startsWith("backup database") || normalized.startsWith("backup table")) {
            return SqlRiskAnalysis.high("tidb_backup");
        }
        if (normalized.startsWith("restore database") || normalized.startsWith("restore table")) {
            return SqlRiskAnalysis.high("tidb_restore");
        }
        if (normalized.startsWith("import into")) {
            return SqlRiskAnalysis.high("tidb_import_into");
        }
        if (normalized.startsWith("load data")) {
            return SqlRiskAnalysis.high("tidb_load_data");
        }
        if (normalized.startsWith("flashback ")) {
            return SqlRiskAnalysis.high("tidb_flashback");
        }
        if (normalized.contains("placement policy")
            && (normalized.startsWith("create ") || normalized.startsWith("alter ") || normalized.startsWith("drop "))) {
            return SqlRiskAnalysis.high("tidb_placement_policy");
        }
        if (normalized.startsWith("kill tidb")) {
            return SqlRiskAnalysis.high("tidb_kill");
        }
        if (normalized.startsWith("set global")) {
            return SqlRiskAnalysis.high("tidb_set_global");
        }
        if (normalized.startsWith("batch on") || normalized.startsWith("batch ")) {
            return SqlRiskAnalysis.high("tidb_batch_dml");
        }

        // Standard SQL falls through to Calcite generic classifier
        if (startsWithKeyword(normalized, "select")
            || startsWithKeyword(normalized, "with")
            || startsWithKeyword(normalized, "insert")
            || startsWithKeyword(normalized, "update")
            || startsWithKeyword(normalized, "delete")
            || startsWithKeyword(normalized, "create")
            || startsWithKeyword(normalized, "drop")
            || startsWithKeyword(normalized, "truncate")
            || startsWithKeyword(normalized, "alter")
            || startsWithKeyword(normalized, "grant")
            || startsWithKeyword(normalized, "revoke")
            || startsWithKeyword(normalized, "rename")
            || startsWithKeyword(normalized, "set")) {
            return null;
        }

        // Catch-all for any unrecognized TiDB statement
        return SqlRiskAnalysis.high("tidb_unrecognized");
    }
```

The exact `startsWithKeyword` helper signature must match the existing private helper used by `classifyStarrocksSpecific`. Read the file before writing if needed.

- [x] **Step 5: Run risk analyzer tests**

```bash
cd server && mvn -q -pl data-talk-application test -Dtest=CalciteSqlRiskAnalyzerTest
```

Expected: PASS (existing tests + ~35 new tidb tests).

- [x] **Step 6: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java \
        server/data-talk-application/src/test/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzerTest.java
git commit -m "feat(tidb): add classifyTidbSpecific with TiDB-only L1/L2/L3 rules"
```

---

### Task 7: `MySqlProtocolReuseRule` Abstract Base Test Kit

**Files (all new):**
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/mysqlprotocol/AbstractMySqlSplitterEquivalenceTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/mysqlprotocol/AbstractMySqlMetadataReuseTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/mysqlprotocol/AbstractMySqlTargetResolutionReuseTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/mysqlprotocol/AbstractMySqlBatchDmlReuseTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/mysqlprotocol/AbstractMySqlResultNormalizationReuseTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/mysqlprotocol/AbstractMySqlConnectionTestReuseTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/mysqlprotocol/package-info.java`

These six abstract classes are the deliverable mandated by Wave C umbrella §8. They contain the real assertions; concrete subclasses (Task 8) only provide `kindUnderTest()` and `dataSourceFor(...)`.

- [x] **Step 1: Write `package-info.java`**

```java
/**
 * MySqlProtocolReuseRule — Wave C cross-kind reuse test kit.
 *
 * Wave C umbrella §8 mandates that every kind reusing MySQL-protocol code
 * (mysql / mariadb / apache_doris / starrocks / tidb / future oceanbase
 * MySQL-mode) provide a concrete subclass of every Abstract*ReuseTest base
 * in this package. Subclasses provide only the kindUnderTest() identifier
 * and the DataSource fixture; assertions live here.
 *
 * Naming is intentionally neutral. Do not introduce kind-anchored names
 * (e.g. TiDbSplitterTest) inside this package; that would create
 * later-refactor debt when oceanbase reuses the code.
 *
 * See: docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md §8
 *      docs/product-specs/2026-05-08-data-source-coverage-tidb-design.md §10
 */
package com.datatalk.application.coverage.mysqlprotocol;
```

- [x] **Step 2: `AbstractMySqlSplitterEquivalenceTest`**

Verifies `MySqlSqlStatementSplitter` produces identical output for kind-under-test as for `mysql`. Includes seven canonical TiDB-relevant cases (basic SELECT, DELIMITER, comment styles, quote styles, escapes, hint syntax, `SPLIT TABLE`).

```java
package com.datatalk.application.coverage.mysqlprotocol;

import com.datatalk.sql.MySqlSqlStatementSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractMySqlSplitterEquivalenceTest {

    protected abstract String kindUnderTest();

    private final MySqlSqlStatementSplitter splitter = new MySqlSqlStatementSplitter();

    @Test
    void basicSelectScript() {
        List<String> parts = splitter.split("SELECT 1; SELECT 2;");
        assertThat(parts).hasSize(2);
    }

    @Test
    void delimiterCustomMarker() {
        String sql = "DELIMITER //\nCREATE PROCEDURE p() BEGIN SELECT 1; END //\nDELIMITER ;";
        List<String> parts = splitter.split(sql);
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0)).contains("CREATE PROCEDURE");
    }

    @Test
    void commentsAreStripped() {
        String sql = "-- a comment\n# another\n/* block */\nSELECT 1;";
        List<String> parts = splitter.split(sql);
        assertThat(parts).hasSize(1);
    }

    @Test
    void quoteStylesAreRespected() {
        String sql = "SELECT 'a;b', \"c;d\", `e;f` FROM t;";
        List<String> parts = splitter.split(sql);
        assertThat(parts).hasSize(1);
    }

    @Test
    void escapeSequencesInsideStrings() {
        String sql = "SELECT 'a\\';b' FROM t;";
        List<String> parts = splitter.split(sql);
        assertThat(parts).hasSize(1);
    }

    @Test
    void mysqlHintCommentsAreNotStatementSeparators() {
        String sql = "SELECT /*+ INL_JOIN(t1, t2) */ * FROM t1 JOIN t2 ON t1.id = t2.id;";
        List<String> parts = splitter.split(sql);
        assertThat(parts).hasSize(1);
    }

    @Test
    void splitTableIsSingleStatement() {
        // SPLIT TABLE is TiDB-specific but uses standard ; termination
        String sql = "SPLIT TABLE t1 BETWEEN (0) AND (10000) REGIONS 8;";
        List<String> parts = splitter.split(sql);
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0)).contains("SPLIT TABLE");
    }
}
```

- [x] **Step 3: `AbstractMySqlMetadataReuseTest`**

Asserts `INFORMATION_SCHEMA.KEY_COLUMN_USAGE`, `REFERENTIAL_CONSTRAINTS`, `getCatalogs()`, `getTables()` work. Requires `dataSourceFor(...)`.

```java
package com.datatalk.application.coverage.mysqlprotocol;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractMySqlMetadataReuseTest {

    protected abstract String kindUnderTest();
    protected abstract DataSource dataSource();
    protected abstract String testDatabaseName();

    @Test
    void getCatalogsReturnsAtLeastTestDatabase() throws Exception {
        try (Connection c = dataSource().getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getCatalogs()) {
                boolean foundTestDb = false;
                while (rs.next()) {
                    if (testDatabaseName().equalsIgnoreCase(rs.getString(1))) {
                        foundTestDb = true;
                        break;
                    }
                }
                assertThat(foundTestDb).isTrue();
            }
        }
    }

    @Test
    void getTablesReturnsTestTable() throws Exception {
        try (Connection c = dataSource().getConnection();
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS reuse_meta_t (id BIGINT PRIMARY KEY)");
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getTables(testDatabaseName(), null, "reuse_meta_t", new String[]{"TABLE"})) {
                assertThat(rs.next()).isTrue();
            }
        }
    }

    @Test
    void informationSchemaKeyColumnUsageIsQueryable() throws Exception {
        try (Connection c = dataSource().getConnection();
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS reuse_kcu_a (id BIGINT PRIMARY KEY)");
            st.execute("CREATE TABLE IF NOT EXISTS reuse_kcu_b (id BIGINT, a_id BIGINT, PRIMARY KEY (id), FOREIGN KEY (a_id) REFERENCES reuse_kcu_a(id))");
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = '" + testDatabaseName() + "'")) {
                rs.next();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(2);
            }
        }
    }
}
```

- [x] **Step 4: `AbstractMySqlTargetResolutionReuseTest`**

Asserts `USE <db>` works, `SHOW DATABASES` lists the test DB, system filter behavior is correct.

```java
package com.datatalk.application.coverage.mysqlprotocol;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractMySqlTargetResolutionReuseTest {

    protected abstract String kindUnderTest();
    protected abstract DataSource dataSource();
    protected abstract String testDatabaseName();
    protected abstract Set<String> expectedSystemDatabases();

    @Test
    void useStatementChangesActiveDatabase() throws Exception {
        try (Connection c = dataSource().getConnection();
             Statement st = c.createStatement()) {
            st.execute("USE " + testDatabaseName());
            try (ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
                rs.next();
                assertThat(rs.getString(1)).isEqualToIgnoringCase(testDatabaseName());
            }
        }
    }

    @Test
    void showDatabasesIncludesTestDatabaseAndExpectedSystemDatabases() throws Exception {
        try (Connection c = dataSource().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SHOW DATABASES")) {
            Set<String> seen = new HashSet<>();
            while (rs.next()) {
                seen.add(rs.getString(1).toLowerCase());
            }
            assertThat(seen).contains(testDatabaseName().toLowerCase());
            for (String sys : expectedSystemDatabases()) {
                assertThat(seen).contains(sys.toLowerCase());
            }
        }
    }
}
```

- [x] **Step 5: `AbstractMySqlBatchDmlReuseTest`**

Asserts JDBC `Statement.addBatch` / `executeBatch` and consecutive INSERT batching work.

```java
package com.datatalk.application.coverage.mysqlprotocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractMySqlBatchDmlReuseTest {

    protected abstract String kindUnderTest();
    protected abstract DataSource dataSource();

    @BeforeEach
    void resetTable() throws Exception {
        try (Connection c = dataSource().getConnection();
             Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS reuse_batch_t");
            st.execute("CREATE TABLE reuse_batch_t (id BIGINT PRIMARY KEY, v INT)");
        }
    }

    @Test
    void executeBatchInsertsAllRows() throws Exception {
        try (Connection c = dataSource().getConnection();
             Statement st = c.createStatement()) {
            st.addBatch("INSERT INTO reuse_batch_t VALUES (1, 10)");
            st.addBatch("INSERT INTO reuse_batch_t VALUES (2, 20)");
            st.addBatch("INSERT INTO reuse_batch_t VALUES (3, 30)");
            int[] counts = st.executeBatch();
            assertThat(counts).hasSize(3);
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM reuse_batch_t")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(3);
            }
        }
    }
}
```

- [x] **Step 6: `AbstractMySqlResultNormalizationReuseTest`**

Asserts JDBC return values for AUTO_RANDOM, JSON, DECIMAL, BIT, ENUM, SET, YEAR types match mysql normalization.

```java
package com.datatalk.application.coverage.mysqlprotocol;

import com.datatalk.application.sql.JdbcResultValueNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractMySqlResultNormalizationReuseTest {

    protected abstract String kindUnderTest();
    protected abstract DataSource dataSource();

    private final JdbcResultValueNormalizer normalizer = new JdbcResultValueNormalizer();

    @BeforeEach
    void resetTable() throws Exception {
        try (Connection c = dataSource().getConnection();
             Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS reuse_norm_t");
            // AUTO_RANDOM is TiDB-only; concrete subclass MAY override this DDL
            st.execute("CREATE TABLE reuse_norm_t (" +
                "id BIGINT PRIMARY KEY," +
                "j JSON," +
                "d DECIMAL(10,2)," +
                "b BIT(8)," +
                "e ENUM('a','b','c')," +
                "s SET('x','y','z')," +
                "y YEAR(4)" +
                ")");
            st.execute("INSERT INTO reuse_norm_t VALUES (1, '{\"k\":\"v\"}', 12.34, b'10101010', 'b', 'x,z', 2026)");
        }
    }

    @Test
    void allTypesNormalizeWithoutError() throws Exception {
        try (Connection c = dataSource().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM reuse_norm_t WHERE id = 1")) {
            rs.next();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                Object normalized = normalizer.normalize(rs.getObject(i));
                assertThat(normalized).as("column " + i + " (" + rs.getMetaData().getColumnLabel(i) + ")").isNotNull();
            }
        }
    }
}
```

If `JdbcResultValueNormalizer.normalize(Object)` does not exist with that signature, follow the existing call shape used by `JdbcResultValueNormalizerTest`.

- [x] **Step 7: `AbstractMySqlConnectionTestReuseTest`**

Asserts `Connection.isValid(2)` returns true and `SELECT 1` returns 1.

```java
package com.datatalk.application.coverage.mysqlprotocol;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractMySqlConnectionTestReuseTest {

    protected abstract String kindUnderTest();
    protected abstract DataSource dataSource();

    @Test
    void connectionIsValid() throws Exception {
        try (Connection c = dataSource().getConnection()) {
            assertThat(c.isValid(2)).isTrue();
        }
    }

    @Test
    void selectOneReturnsOne() throws Exception {
        try (Connection c = dataSource().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }
}
```

- [x] **Step 8: Compile to verify**

```bash
cd server && mvn -q -pl data-talk-application test-compile
```

Expected: 0 errors. Abstract bases compile but do not run yet (no concrete subclasses).

- [x] **Step 9: Commit**

```bash
git add server/data-talk-application/src/test/java/com/datatalk/application/coverage/mysqlprotocol/
git commit -m "test(coverage): add MySqlProtocolReuseRule abstract base classes (Wave C kit)"
```

---

### Task 8: TiDB Testcontainers + 6 Concrete `TiDb*ReuseIT` Subclasses

**Files (all new):**
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/tidb/TiDbContainerSupport.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/tidb/TiDbSplitterEquivalenceIT.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/tidb/TiDbMetadataReuseIT.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/tidb/TiDbTargetResolutionReuseIT.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/tidb/TiDbBatchDmlReuseIT.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/tidb/TiDbResultNormalizationReuseIT.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/tidb/TiDbConnectionTestReuseIT.java`

- [x] **Step 1: Write `TiDbContainerSupport`**

```java
package com.datatalk.application.coverage.tidb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;

public final class TiDbContainerSupport {

    public static final String IMAGE = "pingcap/tidb:v7.5.5";
    public static final String TEST_DATABASE = "reuse_test";

    private TiDbContainerSupport() {}

    public static GenericContainer<?> startContainer() {
        GenericContainer<?> container = new GenericContainer<>(IMAGE)
            .withExposedPorts(4000)
            .waitingFor(Wait.forLogMessage(".*server is running.*\\n", 1)
                .withStartupTimeout(Duration.ofMinutes(3)));
        container.start();
        return container;
    }

    public static DataSource dataSourceFor(GenericContainer<?> container) {
        Integer mappedPort = container.getMappedPort(4000);
        String url = "jdbc:mysql://" + container.getHost() + ":" + mappedPort
            + "/" + TEST_DATABASE
            + "?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true";
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername("root");
        cfg.setPassword("");
        cfg.setMaximumPoolSize(2);
        cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
        HikariDataSource ds = new HikariDataSource(cfg);
        bootstrapTestDatabase(ds);
        return ds;
    }

    private static void bootstrapTestDatabase(DataSource ds) {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS " + TEST_DATABASE);
            st.execute("USE " + TEST_DATABASE);
            // Required for INFORMATION_SCHEMA.KEY_COLUMN_USAGE FK rows in TiDB 6.5+
            st.execute("SET GLOBAL foreign_key_checks = 1");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to bootstrap TiDB test database", e);
        }
    }
}
```

- [x] **Step 2: Write `TiDbSplitterEquivalenceIT`**

```java
package com.datatalk.application.coverage.tidb;

import com.datatalk.application.coverage.mysqlprotocol.AbstractMySqlSplitterEquivalenceTest;

class TiDbSplitterEquivalenceIT extends AbstractMySqlSplitterEquivalenceTest {
    @Override
    protected String kindUnderTest() {
        return "tidb";
    }
}
```

This subclass needs no DataSource — splitter is pure Java, fixture-free.

- [x] **Step 3: Write `TiDbMetadataReuseIT`**

```java
package com.datatalk.application.coverage.tidb;

import com.datatalk.application.coverage.mysqlprotocol.AbstractMySqlMetadataReuseTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;

import javax.sql.DataSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TiDbMetadataReuseIT extends AbstractMySqlMetadataReuseTest {

    private GenericContainer<?> container;
    private DataSource dataSource;

    @BeforeAll
    void setUp() {
        container = TiDbContainerSupport.startContainer();
        dataSource = TiDbContainerSupport.dataSourceFor(container);
    }

    @AfterAll
    void tearDown() {
        if (container != null) container.stop();
    }

    @Override
    protected String kindUnderTest() {
        return "tidb";
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    protected String testDatabaseName() {
        return TiDbContainerSupport.TEST_DATABASE;
    }
}
```

- [x] **Step 4: Write `TiDbTargetResolutionReuseIT`**

Same lifecycle scaffold as Step 3, plus `expectedSystemDatabases()`:

```java
@Override
protected java.util.Set<String> expectedSystemDatabases() {
    // Per spec §7.2: TiDB has METRICS_SCHEMA in addition to mysql's list
    return java.util.Set.of("INFORMATION_SCHEMA", "mysql", "PERFORMANCE_SCHEMA", "METRICS_SCHEMA", "sys");
}
```

- [x] **Step 5: Write `TiDbBatchDmlReuseIT`**

Same lifecycle scaffold, no extra abstract methods.

- [x] **Step 6: Write `TiDbResultNormalizationReuseIT`**

Same lifecycle scaffold. Override `resetTable()` from the base if AUTO_RANDOM-specific DDL is needed — the base table is YEAR(4) etc., which TiDB supports. Add a TiDB-only AUTO_RANDOM smoke test:

```java
@Override
protected String kindUnderTest() {
    return "tidb";
}

@Override
protected DataSource dataSource() {
    return dataSource;
}

// TiDB-specific AUTO_RANDOM equivalence smoke
@org.junit.jupiter.api.Test
void tidbAutoRandomNormalizesAsBigInt() throws Exception {
    try (java.sql.Connection c = dataSource.getConnection();
         java.sql.Statement st = c.createStatement()) {
        st.execute("DROP TABLE IF EXISTS auto_random_t");
        st.execute("CREATE TABLE auto_random_t (id BIGINT AUTO_RANDOM(5) PRIMARY KEY, v INT)");
        st.execute("INSERT INTO auto_random_t (v) VALUES (1), (2), (3)");
        try (java.sql.ResultSet rs = st.executeQuery("SELECT id, v FROM auto_random_t")) {
            int rowCount = 0;
            while (rs.next()) {
                Object id = rs.getObject(1);
                org.assertj.core.api.Assertions.assertThat(id).isInstanceOf(Long.class);
                rowCount++;
            }
            org.assertj.core.api.Assertions.assertThat(rowCount).isEqualTo(3);
        }
    }
}
```

- [x] **Step 7: Write `TiDbConnectionTestReuseIT`**

Same lifecycle scaffold, no extra abstract methods.

- [x] **Step 8: Run all six IT subclasses**

```bash
cd server && mvn -q -pl data-talk-application test \
  -Dtest='com.datatalk.application.coverage.tidb.*'
```

Expected: PASS. First run pulls the `pingcap/tidb:v7.5.5` image (~500 MB) and may take 2–3 min. Subsequent runs reuse the cached image.

- [x] **Step 9: Commit**

```bash
git add server/data-talk-application/src/test/java/com/datatalk/application/coverage/tidb/
git commit -m "test(tidb): wire 6 concrete IT subclasses + Testcontainers (T1 fixture)"
```

---

### Task 9: MCP Adapter Enum + AGENTS.md

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java:39`
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`
- Test: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/ontology/ConnectionObjectTypeTest.java` (if exists; otherwise add to nearby existing test)

- [x] **Step 1: Extend `ConnectionObjectType.java:39` enum list**

Change:

```java
                Map.entry("kind",         Map.of("type", "string", "enum", List.of("mysql", "postgresql", "sqlite", "h2", "mariadb", "oracle", "sqlserver", "duckdb", "clickhouse", "apache_doris", "starrocks", "trino", "presto", "hive"))),
```

to:

```java
                Map.entry("kind",         Map.of("type", "string", "enum", List.of("mysql", "postgresql", "sqlite", "h2", "mariadb", "tidb", "oracle", "sqlserver", "duckdb", "clickhouse", "apache_doris", "starrocks", "trino", "presto", "hive"))),
```

Insert position: between `"mariadb"` and `"oracle"` — within the MySQL-protocol cluster, immediately after `mariadb`. Mirrors the frontend picker order from spec §9.1.

- [x] **Step 2: Add AGENTS.md TiDB section**

Append the following section to `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` (find the appropriate sibling section like "MariaDB" or "Apache Doris" and add adjacent):

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
  `ANALYZE TABLE`, `ALTER TABLE ... COMPACT`, `ADMIN CHECK TABLE/INDEX`.
- TiDB-only L1 read-only introspection (safe to suggest to the user):
  `SHOW PLACEMENT`, `SHOW PLACEMENT FOR ...`, `SHOW PLACEMENT LABELS`,
  `SHOW TABLE <t> REGIONS`, `SHOW SPLIT REGIONS`, `SHOW STATS_HEALTHY`,
  `SHOW STATS_HISTOGRAMS`, `SHOW STATS_META`, `SHOW STATS_BUCKETS`,
  `ADMIN SHOW DDL`, `ADMIN SHOW DDL JOBS`. Use these when the user asks
  about cluster topology, region distribution, statistics health, or DDL
  job state.
- Diagnostics (lock/pool/table_space/EXPLAIN-real/index-hints/terminate/
  optimize) are dialect_unsupported on TiDB Day-1. Suggest the user run
  `EXPLAIN ANALYZE` or the L1 introspection statements above manually in
  the Query Editor when execution plans, region distribution, or
  statistics are needed.
- ER Inspector and Designer are dialect_unsupported on TiDB Day-1.
- User may type "TiDB", "tidb", "PingCAP TiDB". Map to canonical `tidb`
  only. Do not invent aliases.
```

- [x] **Step 3: Add MCP enum / AGENTS.md prompt-contract test cases**

Find the existing `*PromptContractTest` (likely under `server/data-talk-adapter/src/test/java/com/datatalk/adapter/...`) that asserts AGENTS.md prompt rules and MCP schema enum coverage. Add tidb-specific test cases mirroring the apache_doris cases:

```java
@Test
void tidbAppearsInConnectionKindEnum() {
    Map<String, Object> schema = new ConnectionObjectType().propertySchema();
    @SuppressWarnings("unchecked")
    List<String> kinds = (List<String>) ((Map<String, Object>) schema.get("kind")).get("enum");
    assertThat(kinds).contains("tidb");
}

@Test
void agentsMdMentionsTidbCanonicalKind() {
    String agentsMd = readResource("agents/AGENTS.md");
    assertThat(agentsMd).contains("Canonical kind: `tidb`");
    assertThat(agentsMd).contains("Default port: 4000");
}
```

- [x] **Step 4: Run + commit**

```bash
cd server && mvn -q -pl data-talk-adapter test
```

Expected: PASS.

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java \
        server/data-talk-adapter/src/main/resources/agents/AGENTS.md \
        server/data-talk-adapter/src/test/
git commit -m "feat(tidb): expose tidb in MCP enum + AGENTS.md prompt rules"
```

---

### Task 10: Frontend — Form, Picker, Toolbar, Formatter, Outline, i18n

**Files (all modify):**
- `client/src/features/settings/data-sources/connection-form-dialog.tsx`
- `client/src/features/chat/components/composer/data-source-picker.tsx`
- `client/src/features/stage/components/connection-picker.tsx`
- `client/src/features/stage/components/query-editor-toolbar.tsx`
- `client/src/features/stage/utils/format-sql.ts`
- `client/src/features/stage/utils/parse-sql-outline.ts`
- `client/src/i18n/messages.ts`

- [x] **Step 1: Add i18n entries**

Edit `client/src/i18n/messages.ts`. Find the existing `connections.kind.*` block. Add:

```typescript
// zh-CN block
'connections.kind.tidb': 'TiDB',
'connections.tidb.kindLabel': 'TiDB (OSS / 自部署)',
'connections.tidb.placeholder.host': '127.0.0.1',
'connections.tidb.placeholder.port': '4000',
'connections.tidb.placeholder.databaseOptional': '可选 — 留空表示连接级不绑库',
'connections.tidb.help.databaseOptional': '不填则按服务器默认 — 后续可在 Query Editor 用 USE 切换',
```

```typescript
// en-US block
'connections.kind.tidb': 'TiDB',
'connections.tidb.kindLabel': 'TiDB (OSS / self-hosted)',
'connections.tidb.placeholder.host': '127.0.0.1',
'connections.tidb.placeholder.port': '4000',
'connections.tidb.placeholder.databaseOptional': 'Optional — leave empty for connection-level unbound',
'connections.tidb.help.databaseOptional': 'Leave empty to use server default; switch later in Query Editor with USE',
```

- [x] **Step 2: Add `tidb` to `DATABASE_TYPES` (or equivalent kind list)**

Find the source of truth for kind labels (typically `client/src/features/settings/data-sources/database-types.ts` or in `connection-form-dialog.tsx`). Add a `tidb` entry following the `apache_doris` precedent:

```typescript
{
  kind: 'tidb',
  label: 'TiDB',
  defaultPort: 4000,
  i18nKindLabel: 'connections.tidb.kindLabel',
  schemaSelector: 'none',
}
```

The exact field shape must match existing entries. Read the file before writing.

- [x] **Step 3: Extend `connection-form-dialog.tsx`**

Field set: host / port (default 4000) / username / password / database (optional). Identical to mysql shape. **No** TLS field. The label visible in the kind dropdown is "TiDB (OSS / 自部署)" / "TiDB (OSS / self-hosted)" via the i18n key `connections.tidb.kindLabel`.

- [x] **Step 4: Extend `data-source-picker.tsx` and `connection-picker.tsx`**

Per spec §9.1: insertion order — `mysql` → `mariadb` → `tidb` → `apache_doris` → `starrocks` → ... TiDB sits in the MySQL-protocol cluster between `mariadb` and `apache_doris`.

If the picker currently uses an explicit array of kinds for ordering, insert `'tidb'` after `'mariadb'`. If the picker uses a `DATABASE_TYPES` source iteration, ensure that source's array order matches.

- [x] **Step 5: Extend `query-editor-toolbar.tsx` schemaless branch**

Find the condition that suppresses the schema selector for mysql / mariadb / apache_doris / starrocks. Add `'tidb'` to the kind list.

- [x] **Step 6: Extend `format-sql.ts`**

Find the kind-to-monaco-language map. Add:

```typescript
tidb: 'mysql',
```

immediately after the existing `mysql: 'mysql'` entry.

- [x] **Step 7: Extend `parse-sql-outline.ts`**

Find the kind-to-keyword-dictionary map. Add:

```typescript
tidb: MYSQL_OUTLINE_KEYWORDS,
```

(or whatever variable holds the mysql keyword list) — same dictionary as mysql Day-1 per spec §9.1; TiDB-only outline keywords are explicit Out-of-Scope.

- [x] **Step 8: Regenerate API types**

```bash
cd server && mvn spring-boot:run -pl data-talk-adapter &
# Wait for server to come up on port 8080
cd client && npm run generate:api-types
# Or whatever the codebase's regen command is — check `client/package.json`
kill %1
```

Verify `client/src/services/api/generated/` includes `tidb` in any union types.

- [x] **Step 9: Run frontend type check**

```bash
cd client && npx tsc --noEmit
```

Expected: 0 errors.

- [x] **Step 10: Commit**

```bash
git add client/src/features/settings/data-sources/ \
        client/src/features/chat/components/composer/data-source-picker.tsx \
        client/src/features/stage/components/connection-picker.tsx \
        client/src/features/stage/components/query-editor-toolbar.tsx \
        client/src/features/stage/utils/format-sql.ts \
        client/src/features/stage/utils/parse-sql-outline.ts \
        client/src/i18n/messages.ts \
        client/src/services/api/generated/
git commit -m "feat(client): expose tidb kind in form, picker, toolbar, formatter, outline, i18n"
```

---

### Task 11: Frontend Unit Tests

**Files:**
- Test: `client/src/features/settings/data-sources/__tests__/connection-form-dialog.tidb.test.tsx`
- Test: `client/src/features/stage/components/__tests__/connection-picker.tidb.test.tsx`
- Test: `client/src/features/stage/utils/__tests__/format-sql.tidb.test.ts`

- [x] **Step 1: Add connection-form tidb test**

Following the existing `*.apache-doris.test.tsx` pattern (or whatever naming is used), assert:
- selecting `tidb` kind populates default port `4000`
- the database field is rendered as optional (placeholder includes "leave empty")
- no TLS toggle appears
- form label shows "TiDB" in the kind dropdown

- [x] **Step 2: Add picker tidb test**

Assert tidb appears in the picker, in position immediately after mariadb (or whatever the local convention is for asserting picker order).

- [x] **Step 3: Add format-sql tidb test**

Assert `formatSql(sqlText, 'tidb')` returns identical output to `formatSql(sqlText, 'mysql')` for a representative SQL.

- [x] **Step 4: Run + commit**

```bash
cd client && npm test
```

Expected: PASS.

```bash
git add client/src/features/settings/data-sources/__tests__/ \
        client/src/features/stage/components/__tests__/ \
        client/src/features/stage/utils/__tests__/
git commit -m "test(client): add tidb-specific connection form / picker / formatter tests"
```

---

### Task 12: Consolidated Verification

- [x] **Step 1: Backend full verify**

```bash
cd server && mvn clean verify
```

Expected: BUILD SUCCESS. All existing tests + new tidb tests + 6 `TiDb*ReuseIT` classes pass. The Testcontainers-driven IT classes pull `pingcap/tidb:v7.5.5` on first run (~500 MB).

- [x] **Step 2: Frontend type check**

```bash
cd client && npx tsc --noEmit
```

Expected: 0 errors.

- [x] **Step 3: Frontend full unit test**

```bash
cd client && npm test
```

Expected: 0 failures.

- [x] **Step 4: Manual smoke (recommended, not gating)**

Start the backend:

```bash
cd server && mvn install -pl data-talk-domain,data-talk-application,data-talk-infrastructure -am -DskipTests
cd server && mvn spring-boot:run -pl data-talk-adapter
```

Start a local TiDB:

```bash
docker run -d --name tidb-smoke -p 4000:4000 pingcap/tidb:v7.5.5
```

Start the Tauri dev client:

```bash
cd client && npm run tauri dev
```

In the Tauri client:
1. Settings → Data Sources → Add Connection → kind = TiDB → host=127.0.0.1, port=4000, username=root, password=(empty). Save → Test passes.
2. Open Query Editor with the new connection → run `SHOW DATABASES` → verify result.
3. Run `CREATE DATABASE smoke; USE smoke; CREATE TABLE t(id BIGINT AUTO_RANDOM PRIMARY KEY, v INT);` → verify L2/L3 confirmation flow on `CREATE DATABASE`/`CREATE TABLE`.
4. Run `INSERT INTO t (v) VALUES (1),(2),(3); SELECT * FROM t;` → verify result and AUTO_RANDOM ID column displays as long integer.
5. Run `ADMIN SHOW DDL` → verify L1 (no confirmation card).
6. Run `SPLIT TABLE t BETWEEN (0) AND (10000) REGIONS 4` → verify L2 confirmation card.
7. Run `ADMIN CANCEL DDL JOBS 1` → verify L3 confirmation card with destructive warning.
8. Cleanup: `docker rm -f tidb-smoke`.

- [x] **Step 5: Lint clean check**

```bash
cd /home/wushengzhou/workspace/github/data-talk
git diff --check -- server client docs
```

Expected: no whitespace errors.

- [x] **Step 6: Commit (verification stamp commit, optional)**

If any small fixups arose, commit them now. Otherwise skip to Task 13.

---

### Task 13: Documentation Housekeeping

**Files:**
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
- Modify: `docs/exec-plans/index.md`

- [x] **Step 1: Append `tidb` row to Current Support Snapshot table**

In `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`, find the "Current Support Snapshot" table (`hive` is the current last row). Add:

```md
| `tidb` | First-class | Connection UI (host/port/username/password, default port 4000), JDBC URL (`jdbc:mysql://...?useSSL=false&allowPublicKeyRetrieval=true`) reusing existing `com.mysql:mysql-connector-j` driver, MySQL-protocol metadata reuse proven by 6 `TiDb*ReuseIT` concrete subclasses of the new `MySqlProtocolReuseRule` abstract base kit, `MySqlSqlStatementSplitter` reuse, `JdbcResultValueNormalizer` mysql baseline reuse, batch DML path, AUTO_RANDOM normalized as `BIGINT UNSIGNED`, independent `classifyTidbSpecific` risk rules covering 30+ TiDB-only patterns (ADMIN CANCEL/PAUSE/RESUME DDL JOBS L3, ADMIN CHECK TABLE L2, ADMIN SHOW DDL L1, SPLIT TABLE L2, RECOVER TABLE L2, ALTER TABLE COMPACT L2, BACKUP/RESTORE/IMPORT INTO/LOAD DATA INFILE/FLASHBACK/PLACEMENT POLICY/KILL TIDB/SET GLOBAL/BATCH ON L3, SHOW PLACEMENT/REGIONS/STATS_* L1), `TiDbDiagnosticsProvider` returning structured `dialect_unsupported` for all 7 hooks, structured unsupported ER. TiDB Cloud (Serverless / Dedicated) and TLS / SSL are out of scope for Day-1, deferred to a future cross-kind TLS design. Day-2: real diagnostics via EXPLAIN ANALYZE / Statement Summary / ADMIN SHOW DDL, ER Inspector / Designer, TiDB-only outline keywords, brand icon. Minimum supported server version: TiDB 6.5 LTS. |
```

- [x] **Step 2: Update ER matrix and Feature Compatibility Matrix**

In the "ER Inspector follows this matrix" paragraph: add `tidb` to the explicitly-unsupported list.
In the "ER Designer follows this DDL matrix" paragraph: add `tidb` to the explicitly-unsupported list.
In the "Feature Compatibility Matrix" table's "ER Tabs" row: add `tidb` to the `dialect_unsupported` list.

- [x] **Step 3: Update Wave C Child Artifact Tracking row for tidb**

Either find the existing Wave C Tracking table (mirrored from Wave C umbrella §9 by the umbrella-approval mini commit), or — if it does not yet exist in `DATA_SOURCE_TYPE_COMPATIBILITY.md` — add it now. The `tidb` outcome cell becomes:

```md
| `tidb` | `docs/product-specs/2026-05-08-data-source-coverage-tidb-design.md` | `docs/exec-plans/2026-05-08-data-source-coverage-tidb-plan.md` | Completed YYYY-MM-DD: first-class OSS / self-hosted TiDB support via mysql-connector-j reuse; produced `MySqlProtocolReuseRule` 6-base kit with 6 TiDB concrete IT subclasses; 30+ TiDB-only risk rules; structured unsupported diagnostics + ER. Day-2: real diagnostics, ER, TiDB Cloud / TLS via cross-kind design. |
```

(Replace `YYYY-MM-DD` with the actual completion date.)

- [x] **Step 4: Move plan from Active → Completed in `docs/exec-plans/index.md`**

Find `2026-05-08-data-source-coverage-tidb-plan.md` in the Active section, move it to the Completed section with a one-line completion note.

- [x] **Step 5: Mark plan checkboxes**

In this plan file, replace all `- [x]` with `- [x]` for completed steps.

- [x] **Step 6: Commit**

```bash
git add docs/DATA_SOURCE_TYPE_COMPATIBILITY.md \
        docs/exec-plans/index.md \
        docs/exec-plans/2026-05-08-data-source-coverage-tidb-plan.md
git commit -m "docs(tidb): mark first-class in compatibility snapshot + housekeeping"
```

---

### Task 14: Independent Mini Commit — Umbrella License Correction

This task is **separate from the TiDB child plan PR** (per spec §6.1: a child design must not absorb fixes for parent-document factual errors). It can ship before, after, or alongside the TiDB plan in its own commit / PR.

**Files:**
- Modify: `docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md`

- [x] **Step 1: Locate the offending row in Wave C umbrella §5**

```bash
grep -n "MIT" docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md
```

Expected: the `tidb` row in the Wave C candidate matrix listing the driver license as "MIT".

- [x] **Step 2: Replace MIT with the correct license**

In the `tidb` matrix row, replace the substring `; MIT;` with `; GPL-2.0 with Universal FOSS Exception;`.

- [x] **Step 3: Commit independently**

```bash
git add docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md
git commit -m "docs(wave-c): fix mysql-connector-j license in umbrella §5 (was MIT, is GPL-2.0 w/ FOSS Exception)"
```

This commit is the only edit to the Wave C umbrella spec and does not touch any code or other doc.

---

## Self-Review

**1. Spec coverage** — every spec section has at least one task:

| Spec section | Task |
|---|---|
| §1 Purpose / §2 Compatibility Gate / §3 Design Inputs | Task 1 (gate re-read) |
| §4 Support Statement | Tasks 2–11 (functional deliverables) + Task 12 (verification) |
| §5 Kind Naming | Task 2 (ConnectionKind) + Task 9 (MCP enum) |
| §6.1 Driver Decision (incl. min version) | Task 1 Steps 3–4 (driver + image pin) |
| §6.2 Connection Persistence | Task 2 |
| §6.3 JDBC URL | Task 2 Step 4 |
| §6.4 ConnectionKind / JdbcUrlBuilder / ConnectionService | Task 2 |
| §7.1 Namespace mapping | Task 4 (hasIndependentSchemaNamespace) |
| §7.2 Target resolution + system filter (METRICS_SCHEMA) | Task 4 Steps 3–4 + Task 8 Step 4 (IT) |
| §7.3 Schema discovery | Task 4 Step 5 |
| §7.4 Identifier quoting & case | Reused via Task 10 (formatter mysql language map) — no specialization Day-1 |
| §8.1 SQL Execution | Task 3 Steps 5–6 |
| §8.2 Splitter | Task 3 Steps 1–4 + Task 7 (abstract base) + Task 8 (concrete IT) |
| §8.3 Risk Rules (8.3.1 strategy + 8.3.2 rule table) | Task 6 |
| §8.4 Result Normalization | Task 7 base + Task 8 IT (incl. AUTO_RANDOM smoke) |
| §8.5 Diagnostics | Task 5 |
| §9.1 Frontend | Tasks 10–11 |
| §9.2 MCP | Task 9 |
| §9.3 AGENTS.md | Task 9 Step 2 |
| §10 MySqlProtocolReuseRule kit (6 abstract bases + 6 concrete) | Tasks 7 + 8 |
| §11.1 Verification matrix | Task 12 |
| §11.2 Test fixture | Task 1 Step 4 + Task 8 Step 1 |
| §11.3 Documentation sync | Task 13 |
| §11.4 Approval process | Task 1 Step 5 (status flip) |
| §11.5 Out-of-Scope | not implemented (correctly) |
| Umbrella §5 license correction | Task 14 (independent mini commit) |

No spec section is unmapped.

**2. Placeholder scan** — no `TBD`, no `TODO` (the only `TODO` text appearing in the plan is inside a quoted code excerpt from `DefaultSqlStatementSplitters.java` showing existing comments; that is descriptive, not a plan placeholder), no "implement later", no "similar to Task N", no "add appropriate error handling".

**3. Type consistency** —

- `ConnectionKind.TIDB` (constant name `TIDB`, value `"tidb"`) is consistent across Tasks 2, 4, 6.
- `TiDbDiagnosticsProvider` extends `AbstractDiagnosticsProvider` (Task 5) — must verify `AbstractDiagnosticsProvider` exists; if it is named differently, Task 5 Step 1 instructs to "match the exact shape used by `DorisDiagnosticsProvider`".
- `classifyTidbSpecific(String sql)` returning `SqlRiskAnalysis` matches the existing `classifyStarrocksSpecific` shape (Task 6).
- `MySqlProtocolReuseRule` package `com.datatalk.application.coverage.mysqlprotocol` and `tidb` subclasses package `com.datatalk.application.coverage.tidb` are consistent across Tasks 7, 8.
- `TiDbContainerSupport.IMAGE = "pingcap/tidb:v7.5.5"` is referenced once (Task 8); the same constant is documented in Task 1 Step 4.
- `TEST_DATABASE = "reuse_test"` is referenced via `TiDbContainerSupport.TEST_DATABASE` and consumed by `testDatabaseName()` overrides in Task 8 — consistent.
- Frontend kind string `'tidb'` consistent across format-sql, parse-sql-outline, picker, form (Task 10).

No type inconsistencies detected.
