# Data Source Coverage: OceanBase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship first-class Wave C step 3 OceanBase MySQL-mode support per [oceanbase design](../product-specs/2026-05-08-data-source-coverage-oceanbase-design.md), producing `MultiModeConnectionShape` v1 + Flyway V18 + 6 concrete `OceanBase*ReuseIT` subclasses + `OceanBaseDiagnosticsProvider` Day-1 all-9-hook `dialect_unsupported`.

**Architecture:** Backend adds canonical `oceanbase` kind via `com.oceanbase:oceanbase-client` JDBC, reusing MySQL splitter / normalizer / metadata path proven equivalent through 6 concrete subclasses of tidb-shipped `MySqlProtocolReuseRule` abstract bases. Multi-mode handling lands as a kind-neutral `MultiModeConnectionShape` v1 application-layer abstract unit (CompatibilityMode enum + validateModeForKind + isDay1FirstClassMode) consumed later by kingbase. Frontend ships multi-mode form skeleton with 5-state token contract. AGENTS.md / MCP enum / first-class snapshot only after `mvn verify` SUCCESS + 6 IT pass.

**Tech Stack:** Spring Boot 3.5 + Java 21 (virtual threads), Maven, Flyway 9, JUnit 5 + AssertJ, Testcontainers `oceanbase/oceanbase-ce:4.2.1-lts`, React 19 + Vite + shadcn/ui, OpenCode SDK

**Spec author**: This plan implements the design at commits `de81ead` (initial) + `1231153` (review fixes).

---

## File Structure

### Backend (Java 21)

**Create:**
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/multimode/MultiModeConnectionShape.java` — kind-neutral abstract unit
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/multimode/CompatibilityMode.java` — enum (also used by kingbase later)
- `server/data-talk-infrastructure/src/main/resources/db/migration/V18__multimode_and_oceanbase_fields.sql` — Flyway migration (PR-time renumber per spec §6.4 if other plans land first)
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/OceanBaseDiagnosticsProvider.java` — all 9 hooks `dialect_unsupported`
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/oceanbase/OceanBaseSplitterReuseIT.java` (extends tidb-shipped `AbstractMySqlSplitterEquivalenceTest`)
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/oceanbase/OceanBaseMetadataReuseIT.java` (extends `AbstractMySqlMetadataReuseTest`)
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/oceanbase/OceanBaseTargetResolutionReuseIT.java` (extends `AbstractMySqlTargetResolutionReuseTest`)
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/oceanbase/OceanBaseBatchDmlReuseIT.java` (extends `AbstractMySqlBatchDmlReuseTest`)
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/oceanbase/OceanBaseNormalizerReuseIT.java` (extends `AbstractMySqlResultNormalizationReuseTest`)
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/oceanbase/OceanBaseConnectionReuseIT.java` (extends `AbstractMySqlConnectionTestReuseTest`)
- `server/data-talk-application/src/test/java/com/datatalk/application/coverage/oceanbase/OceanBaseContainerSupport.java` — single-container shared mode
- `server/data-talk-application/src/test/java/com/datatalk/application/connection/multimode/MultiModeConnectionShapeTest.java` — kind-neutral validation
- `server/data-talk-application/src/test/java/com/datatalk/application/connection/OceanBaseUsernameComposeTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/connection/OceanBaseConnectionRecordValidationTest.java`
- `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/sql/risk/OceanBaseRiskClassifierTest.java` — 6 anchored patterns × hit + boundary
- `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/OceanBaseDiagnosticsDialectUnsupportedTest.java` — all 9 hooks
- `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/OceanBaseDiagnosticsProviderRegistrationTest.java`
- `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/jdbc/OceanBaseDriverCoexistenceTest.java`
- `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/flyway/OceanBaseFlywayMigrationTest.java`

**Modify:**
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java` — add `OCEANBASE` enum value + normalize() rules
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRecord.java` — add 3 new fields
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java` — add OceanBase URL branch
- `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java` — add `composeOceanBaseUsername()` + invocation site
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/DefaultSqlStatementSplitters.java` — route oceanbase to MySqlSqlStatementSplitter
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/risk/CalciteSqlRiskAnalyzer.java` — `classifyOceanBaseSpecific` 6 patterns
- `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java` — oceanbase branch
- `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java` — oceanbase entry validation
- `server/data-talk-infrastructure/pom.xml` — add `com.oceanbase:oceanbase-client` dependency
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRepository.java` — handle 3 new columns
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java` — add `oceanbase` enum (LAST step, post-verify)
- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` — add OceanBase section (LAST step, post-verify)
- `server/data-talk-adapter/src/main/resources/messages.properties` — i18n entries (en)
- `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties` — i18n entries (zh)
- `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` — Current Support Snapshot row update (LAST step)
- `docs/exec-plans/index.md` — register this plan (Active → Completed)
- `docs/product-specs/2026-05-08-data-source-coverage-oceanbase-design.md` — backfill final patch versions, fixture verified schema list

### Frontend (TypeScript / React 19)

**Create:**
- `client/src/features/settings/data-sources/multi-mode-connection-fields.tsx` — kind-neutral component
- `client/src/features/settings/data-sources/oceanbase-connection-fields.tsx` — kind-private tenant + cluster fields
- `client/src/features/settings/data-sources/__tests__/multi-mode-connection-fields.test.tsx`
- `client/src/features/settings/data-sources/__tests__/oceanbase-connection-fields.test.tsx`
- `client/src/features/settings/data-sources/__tests__/oceanbase-form-validation.test.tsx`

**Modify:**
- `client/src/features/settings/data-sources/connection-form-dialog.tsx` — render multi-mode + oceanbase fields when kind=oceanbase
- `client/src/features/settings/data-sources/data-sources-page.tsx` — picker entry for OceanBase
- `client/src/features/stage/utils/format-sql.ts` — oceanbase formatter routing (reuse mysql)
- `client/src/i18n/messages.ts` — labels + diagnostics dialect_unsupported keys (zh + en)

### Manual Smoke

**Create:**
- `tools/manual-smoke/oceanbase-day1.sh` — 9-case smoke script (optional; T1 fixture means CI runs IT, but smoke script doc provides QA acceptance)

---

## Task Order

Tasks 1-13 are mostly sequential due to enum / schema dependencies. Tasks 7-8 (6 IT subclasses + risk classifier patterns) can run in parallel after Task 5. Tasks 10-11 (frontend) can start after Task 4 (`MultiModeConnectionShape` ships).

---

### Task 1: Approval Gate + Sanity Check + Kickoff Decisions

**Files:**
- Read: `docs/product-specs/2026-05-08-data-source-coverage-oceanbase-design.md` (entire spec)
- Read: `docs/product-specs/2026-05-08-data-source-coverage-wave-c-design.md` §5/§7/§10
- Verify: Maven Central `com.oceanbase:oceanbase-client` 2.4.x latest stable patch
- Verify: Docker Hub `oceanbase/oceanbase-ce:4.2.1-lts` tag still exists
- Verify: Flyway next available version number on `develop` branch HEAD

- [ ] **Step 1: Read the full spec end to end**

Re-read the design doc; produce a 1-page bullet list of decisions to apply (driver version, default port, system schemas, anchored patterns, i18n keys). No code yet.

- [ ] **Step 2: Verify driver Maven Central visibility**

Run:
```bash
curl -sf "https://search.maven.org/solrsearch/select?q=g:com.oceanbase+AND+a:oceanbase-client&rows=20&wt=json" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); [print(r['v']) for r in d['response']['docs']]"
```
Expected: list of versions including `2.4.13` or later 2.4.x patch. Pin **the highest 2.4.x non-snapshot patch** as the version constant. Record the exact pinned version in this plan's first changelog line.

- [ ] **Step 3: Verify Docker image tag**

Run:
```bash
curl -sf "https://hub.docker.com/v2/repositories/oceanbase/oceanbase-ce/tags/4.2.1-lts/" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('name'))"
```
Expected: `4.2.1-lts`. If tag not found, pick the highest `4.2.x-lts` available and update the spec §12.1 accordingly.

- [ ] **Step 4: Verify next available Flyway version**

Run:
```bash
ls server/data-talk-infrastructure/src/main/resources/db/migration | sort -V | tail -3
```
Expected: highest is `V17__duckdb_readonly.sql`. The next available is `V18`. **If V18 is taken** by another concurrent plan that has already merged, rename this plan's migration to the actual next-available version and update every reference. Document the final number here.

- [ ] **Step 5: Commit Approval Gate report**

Append to this plan a one-line "Driver Reachability Report" stating pinned driver version, Docker tag, and Flyway version. Then:
```bash
git add docs/exec-plans/2026-05-08-data-source-coverage-oceanbase-plan.md
git commit -m "chore(oceanbase): record approval gate (driver/fixture/flyway pin)"
```

---

### Task 2: Add `CompatibilityMode` Enum (Application Layer)

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/connection/multimode/CompatibilityMode.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/connection/multimode/CompatibilityModeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.datatalk.application.connection.multimode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompatibilityModeTest {
    @Test
    void wireValueRoundTrip() {
        assertThat(CompatibilityMode.MYSQL.wireValue()).isEqualTo("mysql");
        assertThat(CompatibilityMode.ORACLE.wireValue()).isEqualTo("oracle");
        assertThat(CompatibilityMode.PG.wireValue()).isEqualTo("pg");
    }

    @Test
    void parseAcceptsKnownValues() {
        assertThat(CompatibilityMode.of("mysql")).isEqualTo(CompatibilityMode.MYSQL);
        assertThat(CompatibilityMode.of("oracle")).isEqualTo(CompatibilityMode.ORACLE);
        assertThat(CompatibilityMode.of("pg")).isEqualTo(CompatibilityMode.PG);
    }

    @Test
    void parseRejectsUnknownValues() {
        assertThatThrownBy(() -> CompatibilityMode.of("postgres"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown compatibility mode: postgres");
    }
}
```

- [ ] **Step 2: Run the failing test**

Run: `cd server && mvn -pl data-talk-application test -Dtest=CompatibilityModeTest -q`
Expected: FAIL with "cannot find symbol class CompatibilityMode".

- [ ] **Step 3: Implement the enum**

Create `server/data-talk-application/src/main/java/com/datatalk/application/connection/multimode/CompatibilityMode.java`:

```java
package com.datatalk.application.connection.multimode;

public enum CompatibilityMode {
    MYSQL("mysql"),
    ORACLE("oracle"),
    PG("pg");

    private final String wireValue;

    CompatibilityMode(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static CompatibilityMode of(String wire) {
        for (CompatibilityMode m : values()) {
            if (m.wireValue.equals(wire)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown compatibility mode: " + wire);
    }
}
```

- [ ] **Step 4: Run the test, verify pass**

Run: `cd server && mvn -pl data-talk-application test -Dtest=CompatibilityModeTest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/connection/multimode/CompatibilityMode.java \
        server/data-talk-application/src/test/java/com/datatalk/application/connection/multimode/CompatibilityModeTest.java
git commit -m "feat(oceanbase): add CompatibilityMode enum (mysql/oracle/pg)"
```

---

### Task 3: Add `MultiModeConnectionShape` Validation Logic

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/connection/multimode/MultiModeConnectionShape.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/connection/multimode/MultiModeConnectionShapeTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.datatalk.application.connection.multimode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultiModeConnectionShapeTest {
    @Test
    void oceanbaseAcceptsMysqlMode() {
        MultiModeConnectionShape.validateModeForKind("oceanbase", CompatibilityMode.MYSQL);
        // no throw
    }

    @Test
    void oceanbaseAcceptsOracleMode() {
        MultiModeConnectionShape.validateModeForKind("oceanbase", CompatibilityMode.ORACLE);
        // no throw — Day-1 unsupported but legal at the validate boundary
    }

    @Test
    void oceanbaseRejectsPgMode() {
        assertThatThrownBy(() ->
            MultiModeConnectionShape.validateModeForKind("oceanbase", CompatibilityMode.PG))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("oceanbase requires compatibility_mode in {mysql,oracle}");
    }

    @Test
    void oceanbaseRejectsNullMode() {
        assertThatThrownBy(() ->
            MultiModeConnectionShape.validateModeForKind("oceanbase", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("oceanbase requires compatibility_mode");
    }

    @Test
    void singleModeKindRejectsAnyMode() {
        assertThatThrownBy(() ->
            MultiModeConnectionShape.validateModeForKind("mysql", CompatibilityMode.MYSQL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not specify compatibility_mode");
    }

    @Test
    void singleModeKindAcceptsNullMode() {
        MultiModeConnectionShape.validateModeForKind("mysql", null);
        // no throw
    }

    @Test
    void oceanbaseDay1FirstClassOnlyMysql() {
        assertThat(MultiModeConnectionShape.isDay1FirstClassMode("oceanbase", CompatibilityMode.MYSQL)).isTrue();
        assertThat(MultiModeConnectionShape.isDay1FirstClassMode("oceanbase", CompatibilityMode.ORACLE)).isFalse();
    }

    @Test
    void singleModeKindIsDay1FirstClassWhenModeIsNull() {
        assertThat(MultiModeConnectionShape.isDay1FirstClassMode("mysql", null)).isTrue();
        assertThat(MultiModeConnectionShape.isDay1FirstClassMode("mysql", CompatibilityMode.MYSQL)).isFalse();
    }
}
```

- [ ] **Step 2: Run tests, verify they fail**

Run: `cd server && mvn -pl data-talk-application test -Dtest=MultiModeConnectionShapeTest -q`
Expected: FAIL with "cannot find symbol class MultiModeConnectionShape".

- [ ] **Step 3: Implement `MultiModeConnectionShape`**

Create `server/data-talk-application/src/main/java/com/datatalk/application/connection/multimode/MultiModeConnectionShape.java`:

```java
package com.datatalk.application.connection.multimode;

public final class MultiModeConnectionShape {

    private MultiModeConnectionShape() {}

    public static void validateModeForKind(String kind, CompatibilityMode mode) {
        switch (kind) {
            case "oceanbase" -> {
                if (mode != CompatibilityMode.MYSQL && mode != CompatibilityMode.ORACLE) {
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

    public static boolean isDay1FirstClassMode(String kind, CompatibilityMode mode) {
        return switch (kind) {
            case "oceanbase" -> mode == CompatibilityMode.MYSQL;
            // kingbase row added by kingbase child plan
            default -> mode == null;
        };
    }
}
```

- [ ] **Step 4: Run tests, verify pass**

Run: `cd server && mvn -pl data-talk-application test -Dtest=MultiModeConnectionShapeTest -q`
Expected: PASS (8/8).

- [ ] **Step 5: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/connection/multimode/MultiModeConnectionShape.java \
        server/data-talk-application/src/test/java/com/datatalk/application/connection/multimode/MultiModeConnectionShapeTest.java
git commit -m "feat(oceanbase): add MultiModeConnectionShape v1 validation logic"
```

---

### Task 4: Flyway V18 Migration (3 New Columns + CHECK Constraints)

**Files:**
- Create: `server/data-talk-infrastructure/src/main/resources/db/migration/V18__multimode_and_oceanbase_fields.sql`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/flyway/OceanBaseFlywayMigrationTest.java`

- [ ] **Step 1: Read existing schema for table rebuild**

Run: `cd server && cat data-talk-infrastructure/src/main/resources/db/migration/V1__init.sql | grep -A 30 "CREATE TABLE connection"`
Capture the exact column list and constraint state before rebuild.

- [ ] **Step 2: Write the failing test**

```java
package com.datatalk.infra.flyway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
@ActiveProfiles("test")
class OceanBaseFlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v18ColumnsExist() {
        var columns = jdbc.queryForList("PRAGMA table_info(connection)");
        var names = columns.stream().map(c -> (String) c.get("name")).toList();
        assertThat(names).contains("compatibility_mode", "oceanbase_tenant", "oceanbase_cluster");
    }

    @Test
    void compatibilityModeAcceptsValidEnumValues() {
        jdbc.update(insertSql(), "id1", "test1", "oceanbase", "h", 2881, "db",
            "u", "mysql", "sys", null);
        // succeeds
    }

    @Test
    void compatibilityModeRejectsInvalidEnumValue() {
        assertThatThrownBy(() -> jdbc.update(insertSql(), "id2", "test2", "oceanbase",
            "h", 2881, "db", "u", "redis", "sys", null))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oceanbaseRowRequiresTenant() {
        assertThatThrownBy(() -> jdbc.update(insertSql(), "id3", "test3", "oceanbase",
            "h", 2881, "db", "u", "mysql", null, null))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oceanbaseRowRequiresCompatibilityMode() {
        assertThatThrownBy(() -> jdbc.update(insertSql(), "id4", "test4", "oceanbase",
            "h", 2881, "db", "u", null, "sys", null))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mysqlRowAllowsNullTenantAndMode() {
        jdbc.update(insertSql(), "id5", "test5", "mysql", "h", 3306, "db",
            "u", null, null, null);
        // succeeds
    }

    private String insertSql() {
        return """
            INSERT INTO connection
                (id, name, kind, host, port, database_name, username,
                 password_enc, schema_digest, created_at, connect_timeout,
                 compatibility_mode, oceanbase_tenant, oceanbase_cluster)
            VALUES (?, ?, ?, ?, ?, ?, ?, x'00', '', 0, 10, ?, ?, ?)
            """;
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=OceanBaseFlywayMigrationTest -q`
Expected: FAIL with "no such column: compatibility_mode".

- [ ] **Step 4: Write the migration**

Create `server/data-talk-infrastructure/src/main/resources/db/migration/V18__multimode_and_oceanbase_fields.sql`. **First read existing schema** (Step 1 output) to get the exact current column list, then construct the rebuild script. Template (verify column list against your Step 1 output before applying):

```sql
-- V18__multimode_and_oceanbase_fields.sql
-- SQLite cannot ADD CHECK on existing tables. Rebuild required.

CREATE TABLE connection_new (
    -- existing columns preserved (verify against V1 + V5/V6/V7/V15/V16/V17 amendments)
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    kind TEXT NOT NULL,
    host TEXT NOT NULL,
    port INTEGER NOT NULL,
    database_name TEXT,
    username TEXT,
    password_enc BLOB NOT NULL,
    schema_digest TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    connect_timeout INTEGER NOT NULL DEFAULT 10,
    last_test_status TEXT,
    last_test_at INTEGER,
    oracle_service_type TEXT,
    sqlserver_encrypt INTEGER NOT NULL DEFAULT 1,
    sqlserver_trust_server_certificate INTEGER NOT NULL DEFAULT 1,
    sqlserver_instance_name TEXT,
    duckdb_read_only INTEGER NOT NULL DEFAULT 0,

    -- new Wave C step 3 columns
    compatibility_mode TEXT NULL,
    oceanbase_tenant TEXT NULL,
    oceanbase_cluster TEXT NULL,

    CHECK (compatibility_mode IS NULL
        OR compatibility_mode IN ('mysql','oracle','pg')),
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

-- Recreate indexes (verify against current schema)
CREATE INDEX IF NOT EXISTS idx_connection_kind ON connection(kind);
CREATE INDEX IF NOT EXISTS idx_connection_created_at ON connection(created_at);
```

- [ ] **Step 5: Run tests to verify pass**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=OceanBaseFlywayMigrationTest -q`
Expected: PASS (6/6).

- [ ] **Step 6: Commit**

```bash
git add server/data-talk-infrastructure/src/main/resources/db/migration/V18__multimode_and_oceanbase_fields.sql \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/flyway/OceanBaseFlywayMigrationTest.java
git commit -m "feat(oceanbase): add Flyway V18 multimode + oceanbase columns"
```

---

### Task 5: ConnectionRecord 3 New Fields + ConnectionKind.OCEANBASE + JdbcUrlBuilder Branch

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRecord.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRepository.java`
- Modify: `server/data-talk-infrastructure/pom.xml` (add `com.oceanbase:oceanbase-client`)
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/connection/JdbcUrlBuilderOceanBaseTest.java`

- [ ] **Step 1: Add `oceanbase-client` Maven dependency**

Edit `server/data-talk-infrastructure/pom.xml` (under `<dependencies>`):

```xml
<dependency>
    <groupId>com.oceanbase</groupId>
    <artifactId>oceanbase-client</artifactId>
    <version>2.4.13</version> <!-- pin from Task 1 Step 2 result -->
</dependency>
```

(Use the exact version pinned in Task 1.)

- [ ] **Step 2: Write failing JdbcUrlBuilder test**

```java
package com.datatalk.application.connection;

import com.datatalk.application.persistence.ConnectionRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcUrlBuilderOceanBaseTest {
    @Test
    void buildsOceanBaseUrlWithDatabase() {
        var record = oceanbaseRecord("dbX", "sys", null);
        assertThat(JdbcUrlBuilder.build(record))
            .isEqualTo("jdbc:oceanbase://h:2881/dbX");
    }

    @Test
    void buildsOceanBaseUrlServerLevelWhenDatabaseBlank() {
        var record = oceanbaseRecord("", "sys", null);
        assertThat(JdbcUrlBuilder.build(record))
            .isEqualTo("jdbc:oceanbase://h:2881");
    }

    @Test
    void buildsOceanBaseUrlServerLevelWhenDatabaseNull() {
        var record = oceanbaseRecord(null, "sys", null);
        assertThat(JdbcUrlBuilder.build(record))
            .isEqualTo("jdbc:oceanbase://h:2881");
    }

    private static ConnectionRecord oceanbaseRecord(String db, String tenant, String cluster) {
        return new ConnectionRecord(
            "id", "n", "oceanbase", "h", 2881, db, "root", new byte[]{},
            "", 0L, 10, null, null, null, 1, true, null, false,
            "mysql", tenant, cluster);
    }
}
```

- [ ] **Step 3: Run failing test**

Run: `cd server && mvn -pl data-talk-application test -Dtest=JdbcUrlBuilderOceanBaseTest -q`
Expected: FAIL — `ConnectionRecord` constructor signature mismatch.

- [ ] **Step 4: Update ConnectionRecord with 3 new fields**

Replace `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRecord.java` content:

```java
package com.datatalk.application.persistence;

public record ConnectionRecord(
    String id, String name, String kind, String host, int port,
    String databaseName,  // nullable
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

- [ ] **Step 5: Add `OCEANBASE` to `ConnectionKind` enum + normalize rules**

Edit `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java` to add `OCEANBASE` to the enum and to `normalize(String)`:

```java
// inside normalize()
return switch (lower) {
    case "mysql" -> ConnectionKind.MYSQL;
    // ... existing cases ...
    case "oceanbase" -> ConnectionKind.OCEANBASE;
    // explicitly reject all aliases (oceanbase-ce, ob, obcluster, oceanbase_ce)
    default -> throw new DataTalkException(DataTalkErrorCodes.UNKNOWN_CONNECTION_KIND, "kind=" + raw);
};
```

- [ ] **Step 6: Add OceanBase branch in `JdbcUrlBuilder`**

In `JdbcUrlBuilder.build(ConnectionRecord c)`, add:

```java
case ConnectionKind.OCEANBASE -> {
    String db = c.databaseName();
    yield (db != null && !db.isBlank())
        ? "jdbc:oceanbase://" + c.host() + ":" + c.port() + "/" + db
        : "jdbc:oceanbase://" + c.host() + ":" + c.port();
}
```

- [ ] **Step 7: Update `ConnectionRepository` to read/write 3 new columns**

Edit `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRepository.java` row mapper and INSERT / UPDATE statements to include `compatibility_mode`, `oceanbase_tenant`, `oceanbase_cluster` columns.

- [ ] **Step 8: Run JdbcUrlBuilder test, verify pass**

Run: `cd server && mvn -pl data-talk-application test -Dtest=JdbcUrlBuilderOceanBaseTest -q`
Expected: PASS (3/3).

- [ ] **Step 9: Run full module compile to verify no exhaustive-switch breaks**

Run: `cd server && mvn -pl data-talk-application,data-talk-infrastructure,data-talk-adapter compile -q`
Expected: 0 errors. If any switch on `ConnectionKind` lacks the `OCEANBASE` case, fix in this step (typically: `JdbcUrlBuilder`, `ConnectionService`, `SqlExecuteService`, `ConnectionTargetDiscoveryService`, `DefaultSqlStatementSplitters`).

- [ ] **Step 10: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRecord.java \
        server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java \
        server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java \
        server/data-talk-application/src/main/java/com/datatalk/application/persistence/ConnectionRepository.java \
        server/data-talk-infrastructure/pom.xml \
        server/data-talk-application/src/test/java/com/datatalk/application/connection/JdbcUrlBuilderOceanBaseTest.java
git commit -m "feat(oceanbase): add ConnectionKind + ConnectionRecord fields + JDBC URL"
```

---

### Task 6: ConnectionService Username Composition + ConnectionRecord Validation

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/connection/OceanBaseUsernameComposeTest.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/connection/OceanBaseConnectionRecordValidationTest.java`

- [ ] **Step 1: Write failing tests for username composition**

```java
package com.datatalk.application.connection;

import com.datatalk.application.persistence.ConnectionRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OceanBaseUsernameComposeTest {
    @Test
    void tenantOnly() {
        var c = oceanbaseRecord("root", "sys", null);
        assertThat(ConnectionService.composeOceanBaseUsername(c))
            .isEqualTo("root@sys");
    }

    @Test
    void tenantAndCluster() {
        var c = oceanbaseRecord("root", "sys", "obcluster");
        assertThat(ConnectionService.composeOceanBaseUsername(c))
            .isEqualTo("root@sys#obcluster");
    }

    @Test
    void blankClusterTreatedAsAbsent() {
        var c = oceanbaseRecord("root", "sys", "  ");
        assertThat(ConnectionService.composeOceanBaseUsername(c))
            .isEqualTo("root@sys");
    }

    private static ConnectionRecord oceanbaseRecord(String user, String tenant, String cluster) {
        return new ConnectionRecord(
            "id", "n", "oceanbase", "h", 2881, "db", user, new byte[]{},
            "", 0L, 10, null, null, null, 1, true, null, false,
            "mysql", tenant, cluster);
    }
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `cd server && mvn -pl data-talk-application test -Dtest=OceanBaseUsernameComposeTest -q`
Expected: FAIL with "cannot find symbol method composeOceanBaseUsername".

- [ ] **Step 3: Implement `composeOceanBaseUsername`**

Add to `ConnectionService.java` (visible at package or test scope; `static` for unit testability):

```java
static String composeOceanBaseUsername(ConnectionRecord c) {
    StringBuilder sb = new StringBuilder(c.username());
    sb.append('@').append(c.oceanbaseTenant());  // tenant required
    if (c.oceanbaseCluster() != null && !c.oceanbaseCluster().isBlank()) {
        sb.append('#').append(c.oceanbaseCluster());
    }
    return sb.toString();
}
```

- [ ] **Step 4: Wire into `openConnection(...)` for oceanbase kind**

In `ConnectionService.openConnection(ConnectionRecord c)`, when `c.kind().equals("oceanbase")`, set the JDBC username:

```java
String username = "oceanbase".equals(c.kind())
    ? composeOceanBaseUsername(c)
    : c.username();
Properties props = new Properties();
props.put("user", username);
// ... existing password handling ...
```

- [ ] **Step 5: Add ConnectionRecord validation test**

```java
package com.datatalk.application.connection;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.connection.multimode.CompatibilityMode;
import com.datatalk.application.connection.multimode.MultiModeConnectionShape;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OceanBaseConnectionRecordValidationTest {
    @Test
    void oceanbaseRecordRequiresMultiModeValidation() {
        // mysql is legal mode, no throw
        MultiModeConnectionShape.validateModeForKind("oceanbase", CompatibilityMode.MYSQL);
    }

    @Test
    void oceanbaseWithPgModeIsRejected() {
        assertThatThrownBy(() ->
            MultiModeConnectionShape.validateModeForKind("oceanbase", CompatibilityMode.PG))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 6: Run all tests, verify pass**

Run: `cd server && mvn -pl data-talk-application test -Dtest=OceanBaseUsernameComposeTest,OceanBaseConnectionRecordValidationTest -q`
Expected: PASS (5/5).

- [ ] **Step 7: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionService.java \
        server/data-talk-application/src/test/java/com/datatalk/application/connection/OceanBaseUsernameComposeTest.java \
        server/data-talk-application/src/test/java/com/datatalk/application/connection/OceanBaseConnectionRecordValidationTest.java
git commit -m "feat(oceanbase): ConnectionService username composition + validation"
```

---

### Task 7: SQL Splitter + Discovery + Risk Classifier (6 Anchored Patterns)

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/DefaultSqlStatementSplitters.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/risk/CalciteSqlRiskAnalyzer.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/sql/risk/OceanBaseRiskClassifierTest.java`

- [ ] **Step 1: Write failing risk classifier tests**

```java
package com.datatalk.infra.sql.risk;

import com.datatalk.domain.action.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OceanBaseRiskClassifierTest {

    private final CalciteSqlRiskAnalyzer analyzer = new CalciteSqlRiskAnalyzer();

    // ---- OUTLINE DDL ----
    @Test
    void alterOutlineIsL3() {
        assertThat(analyzer.classifyOceanBaseSpecific("ALTER OUTLINE my_outline ON SELECT * FROM t"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void createOutlineIsL3() {
        assertThat(analyzer.classifyOceanBaseSpecific("CREATE OUTLINE my_outline ON SELECT * FROM t"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void updateOutlineTableIsNotMatched() {
        // boundary: UPDATE/SELECT against tables containing 'outline' substring must NOT match
        assertThat(analyzer.classifyOceanBaseSpecific("UPDATE my_outline_table SET x = 1"))
            .isEmpty();
    }

    // ---- TENANT DDL ----
    @Test
    void createTenantIsL3() {
        assertThat(analyzer.classifyOceanBaseSpecific("CREATE TENANT t1 RESOURCE_POOL_LIST=('p1')"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void selectTenantViewIsNotMatched() {
        assertThat(analyzer.classifyOceanBaseSpecific("SELECT * FROM tenant_resource_view"))
            .isEmpty();
    }

    // ---- RESOURCE POOL/UNIT DDL ----
    @Test
    void dropResourcePoolIsL3() {
        assertThat(analyzer.classifyOceanBaseSpecific("DROP RESOURCE POOL p1"))
            .hasValue(RiskLevel.L3);
    }

    // ---- ALTER SYSTEM ----
    @Test
    void alterSystemIsL3() {
        assertThat(analyzer.classifyOceanBaseSpecific("ALTER SYSTEM SET enable_rebalance = false"))
            .hasValue(RiskLevel.L3);
    }

    // ---- FREEZE ----
    @Test
    void majorFreezeIsL3() {
        assertThat(analyzer.classifyOceanBaseSpecific("MAJOR FREEZE"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void selectMajorFreezeHistoryViewIsNotMatched() {
        assertThat(analyzer.classifyOceanBaseSpecific("SELECT * FROM major_freeze_history"))
            .isEmpty();
    }

    // ---- BACKUP / RESTORE ----
    @Test
    void backupDatabaseIsL3() {
        assertThat(analyzer.classifyOceanBaseSpecific("BACKUP DATABASE TO 'oss://...'"))
            .hasValue(RiskLevel.L3);
    }

    @Test
    void insertBackupLogIsNotMatched() {
        assertThat(analyzer.classifyOceanBaseSpecific("INSERT INTO backup_log VALUES (1)"))
            .isEmpty();
    }

    // ---- whitespace anchor ----
    @Test
    void leadingWhitespaceStillMatches() {
        assertThat(analyzer.classifyOceanBaseSpecific("   CREATE TENANT foo RESOURCE_POOL_LIST=('p1')"))
            .hasValue(RiskLevel.L3);
    }
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=OceanBaseRiskClassifierTest -q`
Expected: FAIL — `classifyOceanBaseSpecific` not defined.

- [ ] **Step 3: Add 6 anchored Pattern constants + classify method**

In `CalciteSqlRiskAnalyzer.java`, add:

```java
private static final java.util.regex.Pattern OCEANBASE_OUTLINE_DDL =
    java.util.regex.Pattern.compile(
        "^\\s*(ALTER|CREATE|DROP)\\s+OUTLINE\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);

private static final java.util.regex.Pattern OCEANBASE_TENANT_DDL =
    java.util.regex.Pattern.compile(
        "^\\s*(ALTER|CREATE|DROP)\\s+TENANT\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);

private static final java.util.regex.Pattern OCEANBASE_RESOURCE_DDL =
    java.util.regex.Pattern.compile(
        "^\\s*(ALTER|CREATE|DROP)\\s+RESOURCE\\s+(POOL|UNIT)\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);

private static final java.util.regex.Pattern OCEANBASE_ALTER_SYSTEM =
    java.util.regex.Pattern.compile(
        "^\\s*ALTER\\s+SYSTEM\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);

private static final java.util.regex.Pattern OCEANBASE_FREEZE =
    java.util.regex.Pattern.compile(
        "^\\s*(MAJOR|MINOR)\\s+FREEZE\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);

private static final java.util.regex.Pattern OCEANBASE_BACKUP_RESTORE =
    java.util.regex.Pattern.compile(
        "^\\s*(BACKUP|RESTORE)\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE);

public java.util.Optional<com.datatalk.domain.action.RiskLevel>
        classifyOceanBaseSpecific(String sql) {
    if (sql == null) return java.util.Optional.empty();
    if (OCEANBASE_OUTLINE_DDL.matcher(sql).find()
        || OCEANBASE_TENANT_DDL.matcher(sql).find()
        || OCEANBASE_RESOURCE_DDL.matcher(sql).find()
        || OCEANBASE_ALTER_SYSTEM.matcher(sql).find()
        || OCEANBASE_FREEZE.matcher(sql).find()
        || OCEANBASE_BACKUP_RESTORE.matcher(sql).find()) {
        return java.util.Optional.of(com.datatalk.domain.action.RiskLevel.L3);
    }
    return java.util.Optional.empty();
}
```

Wire into the main `classify(String sql, ConnectionKind kind)` (or analogous method):

```java
if (kind == ConnectionKind.OCEANBASE) {
    var ob = classifyOceanBaseSpecific(sql);
    if (ob.isPresent()) return new SqlExecutionRisk(ob.get(), "oceanbase_admin_command", sql);
}
// fall through to base mysql classification
```

- [ ] **Step 4: Add splitter routing**

In `DefaultSqlStatementSplitters.java`, route `oceanbase` to `MySqlSqlStatementSplitter`:

```java
if ("mysql".equalsIgnoreCase(connectionKind) || "oceanbase".equalsIgnoreCase(connectionKind)) {
    return mySqlSplitter;
}
```

- [ ] **Step 5: Add discovery routing**

In `ConnectionTargetDiscoveryService.java`, add `oceanbase` to the mysql branch (same SQL queries; OceanBase-specific system-schema filter):

```java
private static final java.util.Set<String> OCEANBASE_SYSTEM_SCHEMAS = java.util.Set.of(
    "mysql", "information_schema", "performance_schema", "sys",
    "oceanbase", "LBACSYS", "SYS"
);

case "mysql", "oceanbase" -> mysqlLikeDiscovery(c, isOceanbase ? OCEANBASE_SYSTEM_SCHEMAS : MYSQL_SYSTEM_SCHEMAS);
```

- [ ] **Step 6: Add SqlExecuteService entry validation**

In `SqlExecuteService.execute(...)`, before JDBC call:

```java
if ("oceanbase".equals(c.kind())) {
    var mode = c.compatibilityMode() != null
        ? CompatibilityMode.of(c.compatibilityMode()) : null;
    if (!MultiModeConnectionShape.isDay1FirstClassMode(c.kind(), mode)) {
        throw new DataTalkException(
            DataTalkErrorCodes.DIALECT_UNSUPPORTED,
            translator.t("connection.kind.oceanbase.mode_oracle_unsupported_day1"));
    }
}
```

- [ ] **Step 7: Run risk classifier tests, verify pass**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=OceanBaseRiskClassifierTest -q`
Expected: PASS (12/12).

- [ ] **Step 8: Run full module compile**

Run: `cd server && mvn compile -q`
Expected: 0 errors.

- [ ] **Step 9: Commit**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/DefaultSqlStatementSplitters.java \
        server/data-talk-infrastructure/src/main/java/com/datatalk/infra/sql/risk/CalciteSqlRiskAnalyzer.java \
        server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java \
        server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/sql/risk/OceanBaseRiskClassifierTest.java
git commit -m "feat(oceanbase): splitter + discovery + risk 6 anchored patterns"
```

---

### Task 8: OceanBaseDiagnosticsProvider (All 9 Hooks `dialect_unsupported`)

**Files:**
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/OceanBaseDiagnosticsProvider.java`
- Test: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/OceanBaseDiagnosticsDialectUnsupportedTest.java`
- Modify: `server/data-talk-adapter/src/main/resources/messages.properties` + `messages_zh_CN.properties`

- [ ] **Step 1: Write failing test**

```java
package com.datatalk.infra.diagnostics;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OceanBaseDiagnosticsDialectUnsupportedTest {
    private final Translator translator = mock(Translator.class);
    private final OceanBaseDiagnosticsProvider provider;

    OceanBaseDiagnosticsDialectUnsupportedTest() {
        when(translator.t(org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(inv -> "[" + inv.getArgument(0) + "]");
        provider = new OceanBaseDiagnosticsProvider(translator);
    }

    @Test
    void supportedDriverTypesIsOnlyOceanbase() {
        assertThat(provider.supportedDriverTypes()).containsExactly("oceanbase");
    }

    @Test
    void supportedCapabilitiesIsEmpty() {
        // Day-1 returns dialect_unsupported for every hook
        assertThat(provider.supportedCapabilities()).isEmpty();
    }

    @Test
    void explainReturnsDialectUnsupported() {
        var conn = oceanbaseRecord();
        var result = provider.explain("SELECT 1", conn, "pw", "db", null);
        assertThat(result.outcome()).isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
        assertThat(result.message())
            .contains("[diagnostics.dialect_unsupported.oceanbase.explain_real]");
    }

    @Test
    void lockInfoReturnsDialectUnsupported() {
        var conn = oceanbaseRecord();
        var result = provider.lockInfo(conn, "pw", "db");
        assertThat(result.outcome()).isEqualTo(DiagnosticOutcome.DIALECT_UNSUPPORTED);
    }

    // ... (parallel tests for poolStatus, tableSpace, terminateSession,
    //      optimizeTable, indexHints, erInspector, erDesigner)

    private static ConnectionRecord oceanbaseRecord() {
        return new ConnectionRecord(
            "id", "n", "oceanbase", "h", 2881, "db", "root",
            new byte[]{}, "", 0L, 10, null, null, null, 1, true, null,
            false, "mysql", "sys", null);
    }
}
```

- [ ] **Step 2: Run test to verify fail**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=OceanBaseDiagnosticsDialectUnsupportedTest -q`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement `OceanBaseDiagnosticsProvider`**

Create the file. Follow the pattern of an existing all-`dialect_unsupported` provider (see TidbDiagnosticsProvider; tidb returns dialect_unsupported for all 7 hooks Day-1):

```java
package com.datatalk.infra.diagnostics;

import com.datatalk.application.i18n.Translator;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class OceanBaseDiagnosticsProvider extends AbstractDiagnosticsProvider {

    public OceanBaseDiagnosticsProvider(Translator translator) {
        super(translator);
    }

    @Override
    public Set<String> supportedDriverTypes() {
        return Set.of("oceanbase");
    }

    @Override
    public Set<DiagnosticCapability> supportedCapabilities() {
        return Set.of();  // Day-1: all dialect_unsupported
    }

    @Override
    public DiagnosticResult<ExplainPlan> explain(String sql, ConnectionRecord conn,
                                                  String decryptedPassword,
                                                  String database, String schema) {
        return dialectUnsupported("explain_real", "OceanBase MySQL-mode does not support explain_real in Day-1.");
    }

    @Override
    public DiagnosticResult<List<IndexHint>> indexHints(String sql, ConnectionRecord conn,
                                                        String decryptedPassword,
                                                        String database, String schema) {
        return dialectUnsupported("index_hints", "OceanBase MySQL-mode does not support index_hints in Day-1.");
    }

    @Override
    public DiagnosticResult<List<LockInfoEntry>> lockInfo(ConnectionRecord conn,
                                                           String decryptedPassword, String database) {
        return dialectUnsupported("lock_info", "OceanBase MySQL-mode does not support lock_info in Day-1.");
    }

    @Override
    public DiagnosticResult<PoolStatus> poolStatus(ConnectionRecord conn,
                                                   String decryptedPassword, String database) {
        return dialectUnsupported("pool_status", "OceanBase MySQL-mode does not support pool_status in Day-1.");
    }

    @Override
    public DiagnosticResult<List<TableSpaceEntry>> tableSpace(ConnectionRecord conn,
                                                              String decryptedPassword,
                                                              String database) {
        return dialectUnsupported("table_space", "OceanBase MySQL-mode does not support table_space in Day-1.");
    }

    @Override
    public DiagnosticResult<TerminateSessionResult> terminateSession(long sessionId,
                                                                      ConnectionRecord conn,
                                                                      String decryptedPassword,
                                                                      String database) {
        return dialectUnsupported("terminate_session", "OceanBase MySQL-mode does not support terminate_session in Day-1.");
    }

    @Override
    public DiagnosticResult<OptimizeTableResult> optimizeTable(String table,
                                                                ConnectionRecord conn,
                                                                String decryptedPassword,
                                                                String database, String schema) {
        return dialectUnsupported("optimize_table", "OceanBase MySQL-mode does not support optimize_table in Day-1.");
    }

    private <T> DiagnosticResult<T> dialectUnsupported(String capability, String fallback) {
        String key = "diagnostics.dialect_unsupported.oceanbase." + capability;
        String msg = translator.t(key);
        return DiagnosticResult.dialectUnsupported(msg.equals(key) ? fallback : msg);
    }
}
```

- [ ] **Step 4: Add i18n entries**

To `messages.properties` (en):

```
diagnostics.dialect_unsupported.oceanbase.lock_info=OceanBase MySQL-mode does not support lock_info in Day-1. See Wave C Day-3 candidate (oceanbase mysql-mode upgrade path).
diagnostics.dialect_unsupported.oceanbase.pool_status=OceanBase MySQL-mode does not support pool_status in Day-1. See Wave C Day-3 candidate (oceanbase mysql-mode upgrade path).
diagnostics.dialect_unsupported.oceanbase.table_space=OceanBase MySQL-mode does not support table_space in Day-1. See Wave C Day-3 candidate (oceanbase mysql-mode upgrade path).
diagnostics.dialect_unsupported.oceanbase.terminate_session=OceanBase MySQL-mode does not support terminate_session in Day-1. See Wave C Day-3 candidate (oceanbase mysql-mode upgrade path).
diagnostics.dialect_unsupported.oceanbase.optimize_table=OceanBase MySQL-mode does not support optimize_table in Day-1. See Wave C Day-3 candidate (oceanbase mysql-mode upgrade path).
diagnostics.dialect_unsupported.oceanbase.explain_real=OceanBase MySQL-mode does not support explain_real in Day-1. See Wave C Day-3 candidate (oceanbase mysql-mode upgrade path).
diagnostics.dialect_unsupported.oceanbase.index_hints=OceanBase MySQL-mode does not support index_hints in Day-1. See Wave C Day-3 candidate (oceanbase mysql-mode upgrade path).
diagnostics.dialect_unsupported.oceanbase.er_inspector=OceanBase MySQL-mode does not support ER Inspector in Day-1.
diagnostics.dialect_unsupported.oceanbase.er_designer=OceanBase MySQL-mode does not support ER Designer in Day-1.
connection.kind.oceanbase.label=OceanBase
connection.kind.oceanbase.tenant_required=OceanBase tenant is required.
connection.kind.oceanbase.tenant_placeholder=Tenant (e.g. sys)
connection.kind.oceanbase.cluster_placeholder=Cluster (optional)
connection.kind.oceanbase.mode_oracle_unsupported_day1=OceanBase Oracle-mode is not supported in Day-1. See Wave C Day-3 candidate (oceanbase Oracle-mode plan).
```

To `messages_zh_CN.properties` (zh): identical keys with Chinese translations.

- [ ] **Step 5: Run all tests**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=OceanBaseDiagnosticsDialectUnsupportedTest -q`
Expected: PASS (all hooks).

- [ ] **Step 6: Commit**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/OceanBaseDiagnosticsProvider.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/diagnostics/OceanBaseDiagnosticsDialectUnsupportedTest.java \
        server/data-talk-adapter/src/main/resources/messages.properties \
        server/data-talk-adapter/src/main/resources/messages_zh_CN.properties
git commit -m "feat(oceanbase): DiagnosticsProvider all-9-hooks dialect_unsupported"
```

---

### Task 9: 6 Concrete `OceanBase*ReuseIT` Subclasses + Container Support (T1 Fixture)

**Files:**
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/coverage/oceanbase/OceanBaseContainerSupport.java`
- Create: 6 concrete IT files (paths in §File Structure)

- [ ] **Step 1: Write `OceanBaseContainerSupport`**

```java
package com.datatalk.application.coverage.oceanbase;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public final class OceanBaseContainerSupport {

    private static final GenericContainer<?> CONTAINER;

    static {
        CONTAINER = new GenericContainer<>(DockerImageName.parse("oceanbase/oceanbase-ce:4.2.1-lts"))
            .withExposedPorts(2881)
            .withEnv("MODE", "slim")
            .waitingFor(Wait.forLogMessage(".*observer\\s+is\\s+ready.*", 1)
                .withStartupTimeout(Duration.ofSeconds(180)));
        CONTAINER.start();
        Runtime.getRuntime().addShutdownHook(new Thread(CONTAINER::stop));
    }

    private OceanBaseContainerSupport() {}

    public static String host() { return CONTAINER.getHost(); }
    public static int port()    { return CONTAINER.getMappedPort(2881); }
    public static String tenant() { return "sys"; }
    public static String username() { return "root"; }
    public static String password() { return ""; }
}
```

- [ ] **Step 2: Write 6 concrete IT subclasses (parallel files)**

Each subclass extends one of the 6 tidb-shipped abstract bases. Example for splitter:

```java
package com.datatalk.application.coverage.oceanbase;

import com.datatalk.application.coverage.mysqlprotocol.AbstractMySqlSplitterEquivalenceTest;
import org.junit.jupiter.api.Tag;

@Tag("oceanbase-it")
class OceanBaseSplitterReuseIT extends AbstractMySqlSplitterEquivalenceTest {

    @Override
    protected String jdbcUrl() {
        return "jdbc:oceanbase://" + OceanBaseContainerSupport.host()
             + ":" + OceanBaseContainerSupport.port() + "/test";
    }

    @Override
    protected String composedUsername() {
        return OceanBaseContainerSupport.username() + "@"
             + OceanBaseContainerSupport.tenant();
    }

    @Override
    protected String password() { return OceanBaseContainerSupport.password(); }
}
```

Repeat the same pattern for `OceanBaseMetadataReuseIT` (extends `AbstractMySqlMetadataReuseTest`), `OceanBaseTargetResolutionReuseIT` (extends `AbstractMySqlTargetResolutionReuseTest`), `OceanBaseBatchDmlReuseIT` (extends `AbstractMySqlBatchDmlReuseTest`), `OceanBaseNormalizerReuseIT` (extends `AbstractMySqlResultNormalizationReuseTest`), and `OceanBaseConnectionReuseIT` (extends `AbstractMySqlConnectionTestReuseTest`). Each overrides `jdbcUrl()`, `composedUsername()`, `password()` to point at `OceanBaseContainerSupport`.

- [ ] **Step 3: Verify abstract base methods exist**

Run: `cd server && grep -rn "protected abstract\|protected String" data-talk-application/src/test/java/com/datatalk/application/coverage/mysqlprotocol/ | head -30`
Confirm `jdbcUrl()`, `composedUsername()` (or whatever override hook), `password()` exist on the abstract bases. **If the hook names differ**, adapt your subclass overrides to match the actual abstract base contract.

- [ ] **Step 4: Run a single IT (smoke check container starts)**

Run: `cd server && mvn -pl data-talk-application test -Dtest=OceanBaseConnectionReuseIT -DfailIfNoTests=false -q`
Expected: PASS. Container takes ~120s on first cold-start.

- [ ] **Step 5: Run all 6 IT**

Run: `cd server && mvn -pl data-talk-application test -Dtest='OceanBase*ReuseIT' -q`
Expected: PASS (6/6 IT classes; total time depends on per-test count, typically 5-10 min).

- [ ] **Step 6: Commit**

```bash
git add server/data-talk-application/src/test/java/com/datatalk/application/coverage/oceanbase/
git commit -m "test(oceanbase): 6 concrete *ReuseIT subclasses + Testcontainers fixture"
```

---

### Task 10: Frontend — Multi-Mode Component + Connection Form

**Files:**
- Create: `client/src/features/settings/data-sources/multi-mode-connection-fields.tsx`
- Create: `client/src/features/settings/data-sources/oceanbase-connection-fields.tsx`
- Modify: `client/src/features/settings/data-sources/connection-form-dialog.tsx`
- Modify: `client/src/features/settings/data-sources/data-sources-page.tsx`
- Modify: `client/src/i18n/messages.ts`

- [ ] **Step 1: Read `client/DESIGN.md` semantic token list**

Re-confirm the token names referenced in the spec §10.4 5-state matrix table (`bg.panel`, `border.default`, `interaction.focusRing`, `interaction.hover`, `interaction.active`, `interaction.selected`, `interaction.disabled`, `accent.primary`, `text.onAccent`, `text.muted`, `text.strong`, `feedback.warning.bg`, `feedback.warning.border`, `text.warning`).

- [ ] **Step 2: Write failing component test**

```typescript
// client/src/features/settings/data-sources/__tests__/multi-mode-connection-fields.test.tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MultiModeConnectionFields } from "../multi-mode-connection-fields";

describe("MultiModeConnectionFields", () => {
    it("renders enabled mode options", () => {
        render(<MultiModeConnectionFields
            kind="oceanbase"
            mode="mysql"
            onModeChange={() => {}}
            modeOptions={["mysql", "oracle"]}
            modeDisabled={["oracle"]}
        />);
        expect(screen.getByRole("radio", { name: /MySQL/i })).not.toBeDisabled();
        expect(screen.getByRole("radio", { name: /Oracle/i })).toBeDisabled();
    });

    it("invokes onModeChange when user picks legal mode", () => {
        const onModeChange = vi.fn();
        render(<MultiModeConnectionFields
            kind="oceanbase"
            mode="mysql"
            onModeChange={onModeChange}
            modeOptions={["mysql", "oracle"]}
            modeDisabled={["oracle"]}
        />);
        // Already mysql; clicking again should still call (no-op debouncing in component-under-test scope)
        fireEvent.click(screen.getByRole("radio", { name: /MySQL/i }));
        expect(onModeChange).toHaveBeenCalledWith("mysql");
    });

    it("disabled option shows a tooltip with Day-3 anchor", () => {
        render(<MultiModeConnectionFields
            kind="oceanbase"
            mode="mysql"
            onModeChange={() => {}}
            modeOptions={["mysql", "oracle"]}
            modeDisabled={["oracle"]}
        />);
        const oracleOption = screen.getByRole("radio", { name: /Oracle/i });
        expect(oracleOption).toHaveAttribute("aria-describedby");
    });
});
```

- [ ] **Step 3: Run test to verify fail**

Run: `cd client && npx vitest run multi-mode-connection-fields`
Expected: FAIL — module not found.

- [ ] **Step 4: Implement `MultiModeConnectionFields`**

Create `client/src/features/settings/data-sources/multi-mode-connection-fields.tsx`:

```tsx
import { type FC } from "react";
import { useT } from "@/i18n/hooks";

export type CompatibilityMode = "mysql" | "oracle" | "pg";

interface Props {
    kind: string;
    mode: CompatibilityMode | null;
    onModeChange: (mode: CompatibilityMode) => void;
    modeOptions: CompatibilityMode[];
    modeDisabled: CompatibilityMode[];
}

export const MultiModeConnectionFields: FC<Props> = ({
    kind, mode, onModeChange, modeOptions, modeDisabled,
}) => {
    const t = useT();
    return (
        <fieldset className="flex flex-col gap-2 p-3 rounded-md
                              bg-[var(--color-bg-panel)]
                              border border-[var(--color-border-default)]">
            <legend className="text-sm text-[var(--color-text-strong)]">
                {t("connection.compatibility_mode")}
            </legend>
            <div className="flex gap-2">
                {modeOptions.map((option) => {
                    const disabled = modeDisabled.includes(option);
                    return (
                        <label
                            key={option}
                            className={[
                                "flex items-center gap-2 px-3 py-2 rounded",
                                "cursor-pointer",
                                "border",
                                disabled
                                    ? "opacity-50 cursor-not-allowed border-[var(--color-border-default)]"
                                    : mode === option
                                        ? "bg-[var(--color-interaction-selected)] text-[var(--color-text-strong)] border-[var(--color-border-default)]"
                                        : "hover:bg-[var(--color-interaction-hover)] border-[var(--color-border-default)]",
                                "focus-within:ring-2 focus-within:ring-[var(--color-interaction-focus-ring)]",
                            ].join(" ")}
                        >
                            <input
                                type="radio"
                                name={`${kind}-mode`}
                                value={option}
                                checked={mode === option}
                                disabled={disabled}
                                onChange={() => !disabled && onModeChange(option)}
                                aria-describedby={disabled ? `${kind}-${option}-disabled-reason` : undefined}
                            />
                            <span>{t(`connection.compatibility_mode.${option}`)}</span>
                            {disabled && (
                                <span
                                    id={`${kind}-${option}-disabled-reason`}
                                    className="sr-only"
                                >
                                    {t("connection.compatibility_mode.disabled.day3_candidate")}
                                </span>
                            )}
                        </label>
                    );
                })}
            </div>
        </fieldset>
    );
};
```

- [ ] **Step 5: Add i18n keys**

Append to `client/src/i18n/messages.ts`:

```typescript
"connection.compatibility_mode": { en: "Compatibility Mode", "zh-CN": "兼容模式" },
"connection.compatibility_mode.mysql": { en: "MySQL", "zh-CN": "MySQL" },
"connection.compatibility_mode.oracle": { en: "Oracle", "zh-CN": "Oracle" },
"connection.compatibility_mode.pg": { en: "PostgreSQL", "zh-CN": "PostgreSQL" },
"connection.compatibility_mode.disabled.day3_candidate": {
    en: "Day-3 candidate; not supported in Day-1.",
    "zh-CN": "Day-3 候选；Day-1 不支持。"
},
"connection.kind.oceanbase.label": { en: "OceanBase", "zh-CN": "OceanBase" },
"connection.kind.oceanbase.tenant_placeholder": {
    en: "Tenant (e.g. sys)", "zh-CN": "租户（例如 sys）"
},
"connection.kind.oceanbase.cluster_placeholder": {
    en: "Cluster (optional)", "zh-CN": "集群（可选）"
},
"connection.kind.oceanbase.tenant_required": {
    en: "OceanBase tenant is required.", "zh-CN": "OceanBase 必须填写租户。"
},
```

- [ ] **Step 6: Run vitest, verify pass**

Run: `cd client && npx vitest run multi-mode-connection-fields`
Expected: PASS (3/3).

- [ ] **Step 7: Implement `OceanBaseConnectionFields` (tenant + cluster inputs)**

Create `client/src/features/settings/data-sources/oceanbase-connection-fields.tsx` with two text inputs (tenant required, cluster optional) using same semantic token classes.

- [ ] **Step 8: Wire into `connection-form-dialog.tsx`**

Render `MultiModeConnectionFields` + `OceanBaseConnectionFields` when `kind === "oceanbase"`. Pass `modeOptions=["mysql","oracle"]`, `modeDisabled=["oracle"]`. Add OceanBase entry to picker in `data-sources-page.tsx`.

- [ ] **Step 9: Run typecheck**

Run: `cd client && npx tsc --noEmit`
Expected: 0 errors.

- [ ] **Step 10: Commit**

```bash
git add client/src/features/settings/data-sources/multi-mode-connection-fields.tsx \
        client/src/features/settings/data-sources/oceanbase-connection-fields.tsx \
        client/src/features/settings/data-sources/__tests__ \
        client/src/features/settings/data-sources/connection-form-dialog.tsx \
        client/src/features/settings/data-sources/data-sources-page.tsx \
        client/src/i18n/messages.ts
git commit -m "feat(oceanbase): frontend multi-mode form + connection fields"
```

---

### Task 11: MCP Enum + AGENTS.md (Last Backend Step, Post-Verify)

**⚠️ Hard timing constraint per umbrella §7.5: this task runs ONLY AFTER all preceding tasks pass `mvn verify` SUCCESS + 6 IT pass.**

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java`
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`

- [ ] **Step 1: Run consolidated pre-flight verification**

Run:
```bash
cd server && mvn clean verify -DskipITs=false -q
```
Expected: BUILD SUCCESS. **DO NOT proceed if any test fails or compilation errors exist.**

- [ ] **Step 2: Add `oceanbase` to `ConnectionObjectType` enum**

Edit `ConnectionObjectType.java`:

```java
public enum ConnectionObjectType {
    MYSQL, POSTGRESQL, /* ... */ OCEANBASE("oceanbase");
    // ... existing constructor and wireValue() pattern ...
}
```

- [ ] **Step 3: Add OceanBase section to `AGENTS.md`**

Edit `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`:

```markdown
### OceanBase (kind: `oceanbase`)

- canonical kind: `oceanbase` (no aliases — `oceanbase-ce`, `ob`, `obcluster`,
  `OceanBase` are all rejected at `ConnectionKind.normalize`).
- Day-1 first-class: MySQL-mode only. Oracle-mode (`compatibility_mode='oracle'`)
  returns `dialect_unsupported`.
- driver: `com.oceanbase:oceanbase-client`; URL `jdbc:oceanbase://<host>:<port>/<db>`;
  default port 2881.
- username form `<user>@<tenant>` or `<user>@<tenant>#<cluster>` is composed by
  ConnectionService; AI must NOT fabricate this; structured fields in MCP.
- Chinese aliases (`蚂蚁 OceanBase`, `沃趣 OceanBase`) are recognized in user
  natural-language input only; they map to canonical kind `oceanbase`, NOT to
  `mysql`.
- Day-1 unsupported: PROCEDURE / FUNCTION / TENANT / OUTLINE / RESOURCE POOL /
  ALTER SYSTEM / MAJOR-MINOR FREEZE / BACKUP-RESTORE — all classified L3 or
  dialect_unsupported.
- All 9 diagnostics hooks (lock_info / pool_status / table_space /
  terminate_session / optimize_table / explain_real / index_hints /
  er_inspector / er_designer) return structured dialect_unsupported.
```

- [ ] **Step 4: Re-run mvn verify**

Run: `cd server && mvn verify -q`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/ontology/ConnectionObjectType.java \
        server/data-talk-adapter/src/main/resources/agents/AGENTS.md
git commit -m "feat(oceanbase): MCP enum + AGENTS.md (post-verify per umbrella §7.5)"
```

---

### Task 12: Consolidated Verification + Manual Smoke

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

- [ ] **Step 4: Manual smoke — create OceanBase connection**

In the running app:
1. Open Connections, click "+", select OceanBase.
2. Fill host=127.0.0.1, port=2881 (assuming local dev OB or fixture container), user=root, tenant=sys, cluster=blank, mode=MySQL (Oracle disabled).
3. Click Test — expect SUCCESS.
4. Save.

- [ ] **Step 5: Manual smoke — discover schema + run SELECT**

1. Pick the new connection. Browse schemas: `oceanbase`, `LBACSYS`, `SYS` should be filtered out from the visible list.
2. Run `SELECT 1;` — expect 1 row.

- [ ] **Step 6: Manual smoke — risk classifier rejection**

1. Run `CREATE TENANT t1 RESOURCE_POOL_LIST=('p1');` — expect L3 confirmation prompt.
2. Run `MAJOR FREEZE;` — expect L3 confirmation prompt.

- [ ] **Step 7: Manual smoke — diagnostics dialect_unsupported**

1. Open ER Inspector for the connection — expect "OceanBase MySQL-mode does not support ER Inspector in Day-1." message.
2. Open Lock Info diagnostics — expect dialect_unsupported with i18n message.

- [ ] **Step 8: Manual smoke — Oracle-mode rejection**

1. Edit the connection, switch mode to Oracle (which is disabled in UI). Verify: cannot select.
2. Manually craft a request via DevTools / curl with `compatibility_mode=oracle` — expect API rejection with `connection.kind.oceanbase.mode_oracle_unsupported_day1`.

- [ ] **Step 9: Commit pre-housekeeping (no code changes; just smoke log if you wrote one)**

If you produced a manual smoke log in `tmp/`, do NOT commit it (per CLAUDE.md tmp/ rule). If everything passes, no commit needed in this step.

---

### Task 13: Documentation Housekeeping

**Files:**
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` — Current Support Snapshot row
- Modify: `docs/exec-plans/index.md` — move from Active to Completed
- Modify: `docs/product-specs/2026-05-08-data-source-coverage-oceanbase-design.md` — backfill final patch versions
- Modify: `docs/product-specs/2026-05-08-data-source-coverage-wave-c-roadmap.md` — mark step 3 ship

- [ ] **Step 1: Update `DATA_SOURCE_TYPE_COMPATIBILITY.md` Snapshot**

Add `oceanbase` to the "First-class Day-1" row with summary of decisions:

```
| `oceanbase` | First-class Day-1 (MySQL-mode only) | driver com.oceanbase:oceanbase-client@<pinned-version>, URL jdbc:oceanbase://<h>:<p>/<db> port 2881, MySQL splitter / metadata / normalizer reuse via 6 OceanBase*ReuseIT subclasses, Risk classifier 6 anchored OB-specific patterns, all 9 diagnostics hooks dialect_unsupported, MultiModeConnectionShape v1 introduced. Oracle-mode dialect_unsupported until Day-3. T1 fixture oceanbase/oceanbase-ce:4.2.1-lts. |
```

- [ ] **Step 2: Verify day2 plan §Day-3 oceanbase row anchor still matches**

Run: `cd /home/wallfacers/project/data-talk && grep -n 'oceanbase' docs/exec-plans/2026-05-08-diagnostics-day2-plan.md | head -5`
Expected: oceanbase row line ~3117 already mentions `EXPLAIN FORMAT=JSON` + `parseMySqlJsonPlan`. **No backfill needed** (per spec §11.2).

- [ ] **Step 3: Backfill final pinned versions to spec**

In `docs/product-specs/2026-05-08-data-source-coverage-oceanbase-design.md`:
- §6.1 Driver version: replace "2.4.x series latest stable patch" with the exact patch from Task 1 Step 2.
- §7.2 system schema list: append any additional OB system schemas discovered during Task 9 fixture run (if any).

- [ ] **Step 4: Move plan from Active to Completed in exec-plans index**

Edit `docs/exec-plans/index.md`: move the oceanbase plan entry from Active to Completed; add a one-line Completion Log: `Completed YYYY-MM-DD; commits: <list>; verified: mvn verify + 6 IT + manual smoke`.

- [ ] **Step 5: Commit final housekeeping**

```bash
git add docs/DATA_SOURCE_TYPE_COMPATIBILITY.md \
        docs/exec-plans/index.md \
        docs/product-specs/2026-05-08-data-source-coverage-oceanbase-design.md \
        docs/product-specs/2026-05-08-data-source-coverage-wave-c-roadmap.md
git commit -m "docs(oceanbase): housekeeping — Snapshot upgrade + plan completion"
```

- [ ] **Step 6: Mark every checkbox in this plan as `[x]`**

Use:
```bash
sed -i 's/^- \[ \]/- [x]/g' docs/exec-plans/2026-05-08-data-source-coverage-oceanbase-plan.md
```

Then commit:
```bash
git add docs/exec-plans/2026-05-08-data-source-coverage-oceanbase-plan.md
git commit -m "docs(oceanbase): mark all plan checkboxes complete"
```

---

## Self-Review

**1. Spec coverage**

- §1 Purpose → Tasks 1, 2, 3 (kit landing) + 4 (V18) + 13 (housekeeping)
- §2 Compatibility Gate → Tasks 5-12 cover every database area listed
- §3 Design Inputs → Task 1 Step 1 (read spec) + Task 13 (backfill)
- §4 Support Statement → Tasks 5-9 implement the bullet list literally
- §5 Kind Naming → Task 5 Step 5 (normalize rules reject all aliases)
- §6 Connection And Persistence → Tasks 4 (Flyway) + 5 (record + URL) + 6 (compose username)
- §7 Metadata Discovery → Task 7 Step 5 (mysql-like discovery branch + system schema filter)
- §8 SQL Execution / Splitter / Risk Classifier → Task 7 (full coverage)
- §9 Diagnostics Provider — All 9 hooks `dialect_unsupported` → Task 8
- §10 MultiModeConnectionShape Outputs → Tasks 2 + 3 (kit) + 4 (Flyway V18) + 10 (frontend skeleton)
- §11 Day-2 / Day-3 Upgrade Path — Bidirectional Anchor → Task 13 Step 2 (verify already in day2 plan)
- §12 Out-of-Scope / T1 Fixture / i18n / AGENTS.md → Task 8 Step 4 (i18n) + Task 9 (fixture) + Task 11 (AGENTS.md timing)

All 12 spec sections covered.

**2. Placeholder scan**

No "TBD", "TODO", "implement later", "fill in details" appear in steps. Risk classifier wiring code in Task 7 Step 3 is complete and concrete. Discovery Task 7 Step 5 includes full system schema filter set. Frontend tests are concrete with assertion code, not stubs.

**3. Type consistency**

- `CompatibilityMode.MYSQL/ORACLE/PG` consistent across Tasks 2 / 3 / 7 / 10.
- `MultiModeConnectionShape.validateModeForKind` and `isDay1FirstClassMode` signatures match Tasks 3 and 7 invocation sites.
- `ConnectionRecord` 22-field constructor consistent across Tasks 5 / 6 / 8 (test fixture builders use 22 args after the new fields).
- 6 abstract base class names (`AbstractMySqlSplitterEquivalenceTest`, `AbstractMySqlMetadataReuseTest`, `AbstractMySqlTargetResolutionReuseTest`, `AbstractMySqlBatchDmlReuseTest`, `AbstractMySqlResultNormalizationReuseTest`, `AbstractMySqlConnectionTestReuseTest`) match Task 9 IT subclass extends clauses and the spec §12.2 table verbatim.
- 6 anchored Pattern constant names (`OCEANBASE_OUTLINE_DDL`, `_TENANT_DDL`, `_RESOURCE_DDL`, `_ALTER_SYSTEM`, `_FREEZE`, `_BACKUP_RESTORE`) match Task 7 Step 3 + spec §8.3.
- 9 i18n keys consistent across Task 8 Step 4 + spec §12.4.
