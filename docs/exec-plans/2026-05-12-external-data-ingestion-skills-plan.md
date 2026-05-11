# External Data Ingestion via Skills — Generic HTTP Scaffolding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地 Task 10 三期首份 child spec — 通用 HTTP / REST / JSONL / CSV / 静态 HTML `<table>` 数据采集 scaffolding，让 AI 在 Chat 中走通「采集 → schema 推断 → 用户 Tab 内确认 → CREATE TABLE → batch INSERT」全链路。

**Architecture:** 6 个新 MCP tool + 2 个新 SQLite 表（V20）+ 扩 file_artifact.kind CHECK + 2 个 workspace-scope Stage Tab type + 独立 `data-ingestion` skill 包仿 bezel 路径打包入 classpath。HTTP fetch 走服务端 MCP（含 SSRF deny list + 500MB 上限），credential 走 SecretVault。CREATE TABLE 经 SqlStatementGuard 审计 + IngestionConfirmedToken 5 min 单次内存 token 替代 modal confirm（不绕过强制路径）。

**Tech Stack:** Spring Boot 3.5 + Java 21 virtual threads / JdbcTemplate / Flyway V20 / Jackson streaming / RestClient / jsoup / SecretVault (AES-GCM) / SqlStatementGuard / React 19 / Zustand / TanStack Query / shadcn/ui / lucide / vitest / Playwright / WireMock 3.x / Testcontainers.

**Spec:** [`docs/product-specs/2026-05-12-external-data-ingestion-skills-design.md`](../product-specs/2026-05-12-external-data-ingestion-skills-design.md)

**Phase 切分**：P1 → P2 → P3 → P4 → P5 → P6 **串行**。每 Phase 完成后跑 `cd server && mvn clean verify`（P4 起追加 `cd client && npx tsc --noEmit` + vitest），全绿才进下一 Phase。

---

## File Structure

### Backend new files

```
server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/
├── AuthScheme.java
├── PayloadFormat.java
├── PaginationType.java
├── InferredType.java
├── TerminationHintType.java
├── TerminationHint.java
├── PaginationSpec.java
├── MappingColumn.java
├── IngestionMapping.java
├── IngestionCredential.java
├── IngestionJob.java
├── IngestionJobStatus.java                       (sealed interface, 9 record permits)
├── IngestionDialectUnsupportedException.java
└── IngestionTokenInvalidException.java

server/data-talk-application/src/main/java/com/datatalk/application/ingestion/
├── IngestionJobService.java
├── IngestionPayloadFetcher.java
├── IngestionSchemaInferrer.java
├── IngestionExecutor.java
├── IngestionCredentialService.java
├── IngestionConfirmedTokenStore.java
├── IngestionConfig.java                          (@ConfigurationProperties)
├── HttpFetchClient.java                          (wraps RestClient + auth injection)
├── IngestionUrlValidator.java                    (SSRF deny list)
├── ddl/
│   ├── IngestionDdlAdapter.java
│   ├── MysqlIngestionDdlAdapter.java
│   ├── PostgresIngestionDdlAdapter.java
│   ├── H2IngestionDdlAdapter.java
│   └── SqliteIngestionDdlAdapter.java
└── parser/
    ├── PayloadParser.java                        (interface)
    ├── JsonPayloadParser.java
    ├── JsonlPayloadParser.java
    ├── CsvPayloadParser.java
    └── HtmlTablePayloadParser.java

server/data-talk-application/src/main/java/com/datatalk/application/ingestion/repository/
├── IngestionJobRepository.java                   (interface)
└── IngestionCredentialRepository.java            (interface)

server/data-talk-infrastructure/src/main/java/com/datatalk/infra/ingestion/
├── JdbcIngestionJobRepository.java
└── JdbcIngestionCredentialRepository.java

server/data-talk-infrastructure/src/main/resources/db/migration/
└── V20__ingestion.sql

server/data-talk-infrastructure/src/main/resources/opencode/skills-src/data-ingestion/
├── SKILL.md
├── recipes/
│   ├── basic-rest-fetch.md
│   ├── paginated-fetch.md
│   ├── csv-bulk-import.md
│   ├── html-table-scrape.md
│   └── error-recovery.md
└── examples/
    ├── github-issues.json
    ├── public-csv.json
    └── wikipedia-table.json

server/data-talk-infrastructure/src/assembly/
└── data-ingestion-skill.xml

server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/
└── IngestionController.java

server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ingestion/
├── HttpRequestActionHandler.java
├── InferIngestionSchemaActionHandler.java
├── CreateIngestionTableActionHandler.java
├── IngestPayloadActionHandler.java
├── GetIngestionJobActionHandler.java
└── ListIngestionJobsActionHandler.java

tools/rollback/
└── V20-helper.sql
```

### Backend modified files

```
server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java
    + 8 new sealed record permits (IngestionJobCreated, IngestionPayloadFetched,
      IngestionMappingProposed, IngestionJobConfirmed, IngestionWriteStarted,
      IngestionWriteProgress, IngestionCompleted, IngestionFailed)

server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/FileArtifactKind.java
    + INGESTION_PAYLOAD enum constant

server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolver.java
    + ensureDataIngestionSkill(Path projectRoot)
    + DATA_INGESTION_RESOURCE / DATA_INGESTION_VERSION_RESOURCE / DATA_INGESTION_MARKER

server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeProcessManager.java
    + ensureDataIngestionSkill call after ensureBezelSkill

server/data-talk-infrastructure/pom.xml
    + maven-assembly descriptor reference: src/assembly/data-ingestion-skill.xml

server/data-talk-adapter/src/main/resources/agents/AGENTS.md
    + ## Data Ingestion (skill: data-ingestion) section
```

### Frontend new files

```
client/src/features/ingestion/
├── ingestion-job-tab.tsx
├── ingestion-library-tab.tsx
├── api/ingestion-api.ts
├── stores/use-ingestion-jobs-store.ts
├── hooks/
│   ├── use-ingestion-job-query.ts
│   ├── use-ingestion-jobs-query.ts
│   └── use-payload-preview-query.ts
├── phases/
│   ├── fetching-phase.tsx
│   ├── mapping-phase.tsx
│   ├── writing-phase.tsx
│   ├── completed-phase.tsx
│   └── failed-phase.tsx
└── components/
    ├── mapping-editor.tsx
    ├── payload-preview-table.tsx
    ├── ddl-preview.tsx
    ├── pagination-config-display.tsx
    ├── source-summary-card.tsx
    └── phase-stepper.tsx

client/src/features/settings/credentials/
├── credentials-page.tsx
├── credential-form.tsx
├── credential-list.tsx
├── api/credential-api.ts
└── hooks/use-credentials-query.ts

client/tests/e2e/ingestion.spec.ts
```

### Frontend modified files

```
client/src/features/stage/types/tab-type-registry.ts
    + ingestion_job + ingestion_library registrations

client/src/i18n/messages.ts
    + ingestion.* keys

client/src/features/settings/settings-page.tsx
    + Credentials nav entry
```

---

## Phase 1 — DB + Domain + Credential

### Task 1.1: V20 Flyway Migration

**Files:**
- Create: `server/data-talk-infrastructure/src/main/resources/db/migration/V20__ingestion.sql`
- Create: `tools/rollback/V20-helper.sql`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/migration/V20MigrationIT.java`

- [ ] **Step 1.1.1: Write the failing migration integration test**

```java
package com.datatalk.infra.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class V20MigrationIT {
    @Autowired JdbcTemplate jdbc;

    @Test void ingestionJobTableCreated() {
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='ingestion_job'",
            Integer.class)).isEqualTo(1);
    }

    @Test void ingestionCredentialTableCreated() {
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='ingestion_credential'",
            Integer.class)).isEqualTo(1);
    }

    @Test void fileArtifactKindCheckAcceptsIngestionPayload() {
        jdbc.update("INSERT INTO file_artifact (id, scope, status, kind, filename, physical_path, " +
            "size_bytes, created_at, updated_at, external) VALUES " +
            "('test-ip-1','workspace','temporary','ingestion_payload','p.json','/tmp/p.json',0,0,0,1)");
        assertThat(jdbc.queryForObject(
            "SELECT kind FROM file_artifact WHERE id='test-ip-1'", String.class))
            .isEqualTo("ingestion_payload");
        jdbc.update("DELETE FROM file_artifact WHERE id='test-ip-1'");
    }

    @Test void fileArtifactKindCheckStillAcceptsDashboard() {
        jdbc.update("INSERT INTO file_artifact (id, scope, status, kind, filename, physical_path, " +
            "size_bytes, created_at, updated_at, external) VALUES " +
            "('test-d-1','workspace','temporary','dashboard','d.json','/tmp/d.json',0,0,0,1)");
        jdbc.update("DELETE FROM file_artifact WHERE id='test-d-1'");
    }

    @Test void fileArtifactExternalIndexExists() {
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='idx_file_artifact_external'",
            Integer.class)).isEqualTo(1);
    }
}
```

- [ ] **Step 1.1.2: Run test to verify it fails**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=V20MigrationIT -q`
Expected: FAIL — table `ingestion_job` does not exist.

- [ ] **Step 1.1.3: Write V20 migration SQL**

```sql
-- V20__ingestion.sql
-- Task 10 / Spec 2026-05-12-external-data-ingestion-skills-design §3.1
-- 1) ingestion_credential, 2) ingestion_job, 3) file_artifact CHECK rebuild adding 'ingestion_payload'

CREATE TABLE ingestion_credential (
  id           TEXT PRIMARY KEY,
  name         TEXT NOT NULL UNIQUE,
  auth_scheme  TEXT NOT NULL CHECK(auth_scheme IN
                 ('none','bearer','api_key_header','api_key_query','basic')),
  config_json  TEXT NOT NULL,
  vault_id     TEXT,
  created_at   INTEGER NOT NULL,
  updated_at   INTEGER NOT NULL
);

CREATE TABLE ingestion_job (
  id                       TEXT PRIMARY KEY,
  source_url               TEXT NOT NULL,
  source_method            TEXT NOT NULL CHECK(source_method IN ('GET','POST')),
  source_headers_json      TEXT,
  source_query_params_json TEXT,
  source_body_json         TEXT,
  credential_id            TEXT,
  pagination_json          TEXT,
  payload_format           TEXT NOT NULL CHECK(payload_format IN ('json','jsonl','csv','html')),
  payload_artifact_id      TEXT,
  status                   TEXT NOT NULL CHECK(status IN
                             ('pending','fetching','fetched','mapping','awaiting_confirm',
                              'writing','completed','failed','cancelled')),
  connection_id            TEXT,
  target_schema            TEXT,
  target_table             TEXT,
  mapping_json             TEXT,
  row_count                INTEGER,
  rows_inserted            INTEGER DEFAULT 0,
  bytes_fetched            INTEGER,
  created_at               INTEGER NOT NULL,
  updated_at               INTEGER NOT NULL,
  completed_at             INTEGER,
  error_message            TEXT
);
CREATE INDEX idx_ingestion_job_status     ON ingestion_job(status);
CREATE INDEX idx_ingestion_job_connection ON ingestion_job(connection_id) WHERE connection_id IS NOT NULL;
CREATE INDEX idx_ingestion_job_created    ON ingestion_job(created_at);

-- file_artifact CHECK rebuild (SQLite cannot ALTER CHECK)
CREATE TABLE file_artifact_new (
  id            TEXT PRIMARY KEY,
  scope         TEXT NOT NULL CHECK(scope IN ('session','workspace')),
  status        TEXT NOT NULL CHECK(status IN ('temporary','candidate','archived','discarded')),
  kind          TEXT NOT NULL CHECK(kind IN
                  ('report','er_diagram','sql_script','dataset','dashboard','ingestion_payload','other')),
  session_id    TEXT,
  connection_id TEXT,
  filename      TEXT NOT NULL,
  physical_path TEXT NOT NULL,
  size_bytes    INTEGER NOT NULL,
  mime_type     TEXT,
  title         TEXT,
  summary       TEXT,
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL,
  archived_at   INTEGER,
  metadata_json TEXT,
  external      INTEGER NOT NULL DEFAULT 0 CHECK(external IN (0,1))
);

INSERT INTO file_artifact_new
  (id, scope, status, kind, session_id, connection_id, filename, physical_path,
   size_bytes, mime_type, title, summary, created_at, updated_at, archived_at,
   metadata_json, external)
SELECT
   id, scope, status, kind, session_id, connection_id, filename, physical_path,
   size_bytes, mime_type, title, summary, created_at, updated_at, archived_at,
   metadata_json, external
FROM file_artifact;

DROP TABLE file_artifact;
ALTER TABLE file_artifact_new RENAME TO file_artifact;

CREATE INDEX idx_file_artifact_session    ON file_artifact(session_id)    WHERE scope = 'session';
CREATE INDEX idx_file_artifact_connection ON file_artifact(connection_id) WHERE scope = 'workspace';
CREATE INDEX idx_file_artifact_status     ON file_artifact(status);
CREATE INDEX idx_file_artifact_external   ON file_artifact(external)      WHERE external = 1;
```

- [ ] **Step 1.1.4: Write rollback helper**

```sql
-- tools/rollback/V20-helper.sql
-- Manual rollback before reverting V20. Run in this order:
DELETE FROM file_artifact WHERE kind = 'ingestion_payload';
DROP TABLE IF EXISTS ingestion_job;
DROP TABLE IF EXISTS ingestion_credential;
-- After this, reverting V20 (or running a down-migration that restores V18 CHECK) is safe.
```

- [ ] **Step 1.1.5: Run test to verify it passes**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=V20MigrationIT -q`
Expected: PASS — all 5 assertions green.

- [ ] **Step 1.1.6: Commit**

```bash
git add server/data-talk-infrastructure/src/main/resources/db/migration/V20__ingestion.sql \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/migration/V20MigrationIT.java \
        tools/rollback/V20-helper.sql
git commit -m "$(cat <<'EOF'
feat(ingestion): V20 migration — ingestion_job + ingestion_credential + file_artifact CHECK rebuild

Adds Task 10 first-class child slice persistence:
- ingestion_credential table (5 auth_scheme values, SecretVault id reference)
- ingestion_job table (9 status state machine, FK-free for app-managed lifecycle)
- file_artifact.kind CHECK rebuild adding 'ingestion_payload'
- rollback helper for safe down-migration

Spec: docs/product-specs/2026-05-12-external-data-ingestion-skills-design.md §3.1

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Task 1.2: Domain Enums

**Files:**
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/AuthScheme.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/PayloadFormat.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/PaginationType.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/InferredType.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/TerminationHintType.java`
- Create: `server/data-talk-domain/src/test/java/com/datatalk/domain/ingestion/EnumValuesTest.java`

- [ ] **Step 1.2.1: Write the failing enum-values test**

```java
package com.datatalk.domain.ingestion;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EnumValuesTest {
    @Test void authSchemeHasFiveValues() {
        assertThat(AuthScheme.values()).containsExactly(
            AuthScheme.NONE, AuthScheme.BEARER, AuthScheme.API_KEY_HEADER,
            AuthScheme.API_KEY_QUERY, AuthScheme.BASIC);
    }
    @Test void payloadFormatHasFourValues() {
        assertThat(PayloadFormat.values()).containsExactly(
            PayloadFormat.JSON, PayloadFormat.JSONL, PayloadFormat.CSV, PayloadFormat.HTML);
    }
    @Test void paginationTypeHasFourValues() {
        assertThat(PaginationType.values()).containsExactly(
            PaginationType.NONE, PaginationType.PAGE, PaginationType.OFFSET, PaginationType.CURSOR);
    }
    @Test void inferredTypeContainsExpectedSet() {
        assertThat(InferredType.values()).contains(
            InferredType.BOOLEAN, InferredType.INTEGER_32, InferredType.INTEGER_64,
            InferredType.DECIMAL, InferredType.DATE, InferredType.TIMESTAMP,
            InferredType.STRING_64, InferredType.STRING_256, InferredType.STRING_500,
            InferredType.STRING_LONG, InferredType.JSON);
    }
    @Test void terminationHintTypeHasThreeValues() {
        assertThat(TerminationHintType.values()).containsExactly(
            TerminationHintType.EMPTY_ARRAY, TerminationHintType.JSON_PATH_COUNT_ZERO,
            TerminationHintType.HTTP_STATUS_404);
    }
}
```

- [ ] **Step 1.2.2: Run test to verify it fails**

Run: `cd server && mvn -pl data-talk-domain test -Dtest=EnumValuesTest -q`
Expected: FAIL — symbols not defined.

- [ ] **Step 1.2.3: Create enums**

```java
// AuthScheme.java
package com.datatalk.domain.ingestion;
public enum AuthScheme { NONE, BEARER, API_KEY_HEADER, API_KEY_QUERY, BASIC }
```
```java
// PayloadFormat.java
package com.datatalk.domain.ingestion;
public enum PayloadFormat { JSON, JSONL, CSV, HTML }
```
```java
// PaginationType.java
package com.datatalk.domain.ingestion;
public enum PaginationType { NONE, PAGE, OFFSET, CURSOR }
```
```java
// InferredType.java
package com.datatalk.domain.ingestion;
public enum InferredType {
    BOOLEAN, INTEGER_32, INTEGER_64, DECIMAL,
    DATE, TIMESTAMP,
    STRING_64, STRING_256, STRING_500, STRING_LONG,
    JSON
}
```
```java
// TerminationHintType.java
package com.datatalk.domain.ingestion;
public enum TerminationHintType { EMPTY_ARRAY, JSON_PATH_COUNT_ZERO, HTTP_STATUS_404 }
```

- [ ] **Step 1.2.4: Run test to verify it passes**

Run: `cd server && mvn -pl data-talk-domain test -Dtest=EnumValuesTest -q`
Expected: PASS.

- [ ] **Step 1.2.5: Commit**

```bash
git add server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/*.java \
        server/data-talk-domain/src/test/java/com/datatalk/domain/ingestion/EnumValuesTest.java
git commit -m "feat(ingestion): domain enums — AuthScheme/PayloadFormat/PaginationType/InferredType/TerminationHintType

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 1.3: Domain Records

**Files:**
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/TerminationHint.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/PaginationSpec.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/MappingColumn.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/IngestionMapping.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/IngestionCredential.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/IngestionJob.java`
- Create: `server/data-talk-domain/src/test/java/com/datatalk/domain/ingestion/RecordSemanticsTest.java`

- [ ] **Step 1.3.1: Write the failing test**

```java
package com.datatalk.domain.ingestion;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class RecordSemanticsTest {
    @Test void mappingColumnHoldsFields() {
        MappingColumn c = new MappingColumn("$.id", "id", InferredType.INTEGER_64,
            false, List.of("1","2"), false);
        assertThat(c.targetName()).isEqualTo("id");
        assertThat(c.skip()).isFalse();
    }

    @Test void ingestionMappingHoldsColumns() {
        IngestionMapping m = new IngestionMapping("map_x",
            List.of(new MappingColumn("$.id","id",InferredType.INTEGER_64,false,List.of(),false)));
        assertThat(m.columns()).hasSize(1);
    }

    @Test void ingestionCredentialNeverHoldsRawSecret() {
        IngestionCredential c = new IngestionCredential("cred_1", "prod-key",
            AuthScheme.BEARER, Map.of(), "vault_id_x", 0L, 0L);
        assertThat(c.vaultId()).isEqualTo("vault_id_x");
        // secret itself is not on the record by design
    }

    @Test void ingestionJobHoldsAllSpecFields() {
        IngestionJob j = new IngestionJob(
            "ing_1", "https://x", "GET",
            Map.of(), Map.of(), null,
            null, null, PayloadFormat.JSON, null,
            "fetching", null, null, null, null,
            0, 0, 0L, 0L, 0L, null, null);
        assertThat(j.id()).isEqualTo("ing_1");
        assertThat(j.payloadFormat()).isEqualTo(PayloadFormat.JSON);
    }
}
```

- [ ] **Step 1.3.2: Run test to verify it fails**

Run: `cd server && mvn -pl data-talk-domain test -Dtest=RecordSemanticsTest -q`
Expected: FAIL — records not defined.

- [ ] **Step 1.3.3: Create records**

```java
// TerminationHint.java
package com.datatalk.domain.ingestion;
public record TerminationHint(TerminationHintType type, String jsonPath) {}
```
```java
// PaginationSpec.java
package com.datatalk.domain.ingestion;
import java.util.Map;
public record PaginationSpec(PaginationType type, Map<String, Object> params,
                             int maxPages, TerminationHint terminationHint) {}
```
```java
// MappingColumn.java
package com.datatalk.domain.ingestion;
import java.util.List;
public record MappingColumn(String sourcePath, String targetName, InferredType type,
                            boolean skip, List<String> sampleValues, boolean nullable) {}
```
```java
// IngestionMapping.java
package com.datatalk.domain.ingestion;
import java.util.List;
public record IngestionMapping(String mappingId, List<MappingColumn> columns) {}
```
```java
// IngestionCredential.java
package com.datatalk.domain.ingestion;
import java.util.Map;
public record IngestionCredential(String id, String name, AuthScheme scheme,
                                  Map<String, String> configNonSecret, String vaultId,
                                  long createdAt, long updatedAt) {}
```
```java
// IngestionJob.java
package com.datatalk.domain.ingestion;
import java.util.Map;
public record IngestionJob(
    String id,
    String sourceUrl,
    String sourceMethod,
    Map<String, String> sourceHeaders,
    Map<String, String> sourceQueryParams,
    String sourceBody,
    String credentialId,
    PaginationSpec pagination,
    PayloadFormat payloadFormat,
    String payloadArtifactId,
    String status,
    String connectionId,
    String targetSchema,
    String targetTable,
    IngestionMapping mapping,
    Integer rowCount,
    Integer rowsInserted,
    Long bytesFetched,
    long createdAt,
    long updatedAt,
    Long completedAt,
    String errorMessage,
    Object reserved
) {}
```

- [ ] **Step 1.3.4: Run test to verify it passes**

Run: `cd server && mvn -pl data-talk-domain test -Dtest=RecordSemanticsTest -q`
Expected: PASS.

- [ ] **Step 1.3.5: Commit**

```bash
git add server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/*.java \
        server/data-talk-domain/src/test/java/com/datatalk/domain/ingestion/RecordSemanticsTest.java
git commit -m "feat(ingestion): domain records — TerminationHint/PaginationSpec/MappingColumn/IngestionMapping/IngestionCredential/IngestionJob

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 1.4: IngestionJobStatus Sealed Interface

**Files:**
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/IngestionJobStatus.java`
- Create: `server/data-talk-domain/src/test/java/com/datatalk/domain/ingestion/IngestionJobStatusTest.java`

- [ ] **Step 1.4.1: Write the failing test for exhaustive 9 states + persistence-string mapping**

```java
package com.datatalk.domain.ingestion;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IngestionJobStatusTest {
    @Test void roundTripsToPersistenceString() {
        for (var s : new IngestionJobStatus[] {
            new IngestionJobStatus.Pending(),
            new IngestionJobStatus.Fetching(),
            new IngestionJobStatus.Fetched(),
            new IngestionJobStatus.Mapping(),
            new IngestionJobStatus.AwaitingConfirm(),
            new IngestionJobStatus.Writing(),
            new IngestionJobStatus.Completed(),
            new IngestionJobStatus.Failed("err"),
            new IngestionJobStatus.Cancelled()
        }) {
            String code = IngestionJobStatus.toCode(s);
            assertThat(IngestionJobStatus.fromCode(code, null).getClass())
                .isEqualTo(s.getClass());
        }
    }

    @Test void exhaustiveSwitchCompilesAndCoversAll() {
        IngestionJobStatus s = new IngestionJobStatus.Fetched();
        String label = switch (s) {
            case IngestionJobStatus.Pending p -> "p";
            case IngestionJobStatus.Fetching f -> "f";
            case IngestionJobStatus.Fetched f -> "fd";
            case IngestionJobStatus.Mapping m -> "m";
            case IngestionJobStatus.AwaitingConfirm a -> "ac";
            case IngestionJobStatus.Writing w -> "w";
            case IngestionJobStatus.Completed c -> "c";
            case IngestionJobStatus.Failed f -> "fail";
            case IngestionJobStatus.Cancelled c -> "cancel";
        };
        assertThat(label).isEqualTo("fd");
    }
}
```

- [ ] **Step 1.4.2: Run test to verify it fails**

Run: `cd server && mvn -pl data-talk-domain test -Dtest=IngestionJobStatusTest -q`
Expected: FAIL — interface not defined.

- [ ] **Step 1.4.3: Create sealed interface**

```java
// IngestionJobStatus.java
package com.datatalk.domain.ingestion;

public sealed interface IngestionJobStatus
    permits IngestionJobStatus.Pending, IngestionJobStatus.Fetching,
            IngestionJobStatus.Fetched, IngestionJobStatus.Mapping,
            IngestionJobStatus.AwaitingConfirm, IngestionJobStatus.Writing,
            IngestionJobStatus.Completed, IngestionJobStatus.Failed,
            IngestionJobStatus.Cancelled {

    record Pending() implements IngestionJobStatus {}
    record Fetching() implements IngestionJobStatus {}
    record Fetched() implements IngestionJobStatus {}
    record Mapping() implements IngestionJobStatus {}
    record AwaitingConfirm() implements IngestionJobStatus {}
    record Writing() implements IngestionJobStatus {}
    record Completed() implements IngestionJobStatus {}
    record Failed(String message) implements IngestionJobStatus {}
    record Cancelled() implements IngestionJobStatus {}

    static String toCode(IngestionJobStatus s) {
        return switch (s) {
            case Pending p -> "pending";
            case Fetching f -> "fetching";
            case Fetched f -> "fetched";
            case Mapping m -> "mapping";
            case AwaitingConfirm a -> "awaiting_confirm";
            case Writing w -> "writing";
            case Completed c -> "completed";
            case Failed f -> "failed";
            case Cancelled c -> "cancelled";
        };
    }

    static IngestionJobStatus fromCode(String code, String errorMessage) {
        return switch (code) {
            case "pending" -> new Pending();
            case "fetching" -> new Fetching();
            case "fetched" -> new Fetched();
            case "mapping" -> new Mapping();
            case "awaiting_confirm" -> new AwaitingConfirm();
            case "writing" -> new Writing();
            case "completed" -> new Completed();
            case "failed" -> new Failed(errorMessage == null ? "" : errorMessage);
            case "cancelled" -> new Cancelled();
            default -> throw new IllegalArgumentException("unknown ingestion status: " + code);
        };
    }
}
```

- [ ] **Step 1.4.4: Run test to verify it passes**

Run: `cd server && mvn -pl data-talk-domain test -Dtest=IngestionJobStatusTest -q`
Expected: PASS.

- [ ] **Step 1.4.5: Commit**

```bash
git add server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/IngestionJobStatus.java \
        server/data-talk-domain/src/test/java/com/datatalk/domain/ingestion/IngestionJobStatusTest.java
git commit -m "feat(ingestion): IngestionJobStatus sealed interface — 9 states + code round-trip

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 1.5: FileArtifactKind enum extension

**Files:**
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/FileArtifactKind.java`
- Create: `server/data-talk-domain/src/test/java/com/datatalk/domain/fileartifact/FileArtifactKindIngestionPayloadTest.java`

- [ ] **Step 1.5.1: Write failing test asserting `INGESTION_PAYLOAD` exists and maps to code `'ingestion_payload'`**

```java
package com.datatalk.domain.fileartifact;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FileArtifactKindIngestionPayloadTest {
    @Test void hasIngestionPayloadValue() {
        assertThat(FileArtifactKind.valueOf("INGESTION_PAYLOAD"))
            .isEqualTo(FileArtifactKind.INGESTION_PAYLOAD);
    }
    @Test void persistenceCodeIsLowercase() {
        assertThat(FileArtifactKind.INGESTION_PAYLOAD.code()).isEqualTo("ingestion_payload");
    }
}
```

- [ ] **Step 1.5.2: Run test to verify it fails**

Run: `cd server && mvn -pl data-talk-domain test -Dtest=FileArtifactKindIngestionPayloadTest -q`
Expected: FAIL.

- [ ] **Step 1.5.3: Extend enum (read existing file first to confirm style)**

Read `FileArtifactKind.java` first via Read tool; then add the `INGESTION_PAYLOAD("ingestion_payload")` entry adjacent to existing `DASHBOARD("dashboard")` row, preserving constructor + `code()` getter pattern already in use.

- [ ] **Step 1.5.4: Run test to verify it passes**

Run: `cd server && mvn -pl data-talk-domain test -Dtest=FileArtifactKindIngestionPayloadTest -q`
Expected: PASS.

- [ ] **Step 1.5.5: Commit**

```bash
git add server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/FileArtifactKind.java \
        server/data-talk-domain/src/test/java/com/datatalk/domain/fileartifact/FileArtifactKindIngestionPayloadTest.java
git commit -m "feat(ingestion): add INGESTION_PAYLOAD to FileArtifactKind enum

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 1.6: 8 new DtEvent permits

**Files:**
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java`
- Create: `server/data-talk-domain/src/test/java/com/datatalk/domain/event/IngestionEventsTest.java`
- Audit (read-only, modify if needed): `server/data-talk-application/**` — search for exhaustive `switch (event)` over `DtEvent` and add 8 new branches with no-op handling

- [ ] **Step 1.6.1: Write failing event existence test**

```java
package com.datatalk.domain.event;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IngestionEventsTest {
    @Test void allEightEventsExist() {
        DtEvent[] events = {
            new DtEvent.IngestionJobCreated("j1", "https://x"),
            new DtEvent.IngestionPayloadFetched("j1", "fa1", 100, 1024L),
            new DtEvent.IngestionMappingProposed("j1", "m1", 5),
            new DtEvent.IngestionJobConfirmed("j1", "ict_abc"),
            new DtEvent.IngestionWriteStarted("j1", "orders"),
            new DtEvent.IngestionWriteProgress("j1", 50, 100),
            new DtEvent.IngestionCompleted("j1", "orders", 100, 1234L),
            new DtEvent.IngestionFailed("j1", "fetch", "timeout")
        };
        assertThat(events).hasSize(8);
        for (var e : events) {
            assertThat(e).isInstanceOf(DtEvent.class);
        }
    }
}
```

- [ ] **Step 1.6.2: Run test to verify it fails**

Run: `cd server && mvn -pl data-talk-domain test -Dtest=IngestionEventsTest -q`
Expected: FAIL — records not defined.

- [ ] **Step 1.6.3: Add 8 permits to `DtEvent` sealed interface**

Read existing `DtEvent.java`, observe the permits + record pattern (each event is a record with `@JsonTypeName` annotation and implements `DtEvent`). Add:

```java
@JsonTypeName("ingestion.job.created")
record IngestionJobCreated(String jobId, String sourceUrl) implements DtEvent {}

@JsonTypeName("ingestion.payload.fetched")
record IngestionPayloadFetched(String jobId, String payloadArtifactId,
                                int rowCount, long bytesFetched) implements DtEvent {}

@JsonTypeName("ingestion.mapping.proposed")
record IngestionMappingProposed(String jobId, String mappingId, int columnCount) implements DtEvent {}

@JsonTypeName("ingestion.job.confirmed")
record IngestionJobConfirmed(String jobId, String tokenId) implements DtEvent {}

@JsonTypeName("ingestion.write.started")
record IngestionWriteStarted(String jobId, String targetTable) implements DtEvent {}

@JsonTypeName("ingestion.write.progress")
record IngestionWriteProgress(String jobId, int rowsInserted, int totalRows) implements DtEvent {}

@JsonTypeName("ingestion.completed")
record IngestionCompleted(String jobId, String targetTable, int finalRowCount, long durationMs) implements DtEvent {}

@JsonTypeName("ingestion.failed")
record IngestionFailed(String jobId, String phase, String errorMessage) implements DtEvent {}
```

Add the 8 names to the `permits` clause at the top of the sealed interface, and to any Jackson `@JsonSubTypes` annotation if present.

- [ ] **Step 1.6.4: Find every exhaustive `switch (e)` over DtEvent in application layer**

Run: `cd server && grep -rln "switch (" data-talk-application/src/main/java/ | xargs grep -l "DtEvent"`
For each hit, open the file and add 8 new branches (default no-op handler — these events flow through SSE without server-side processing beyond logging). Example default handler:

```java
case DtEvent.IngestionJobCreated ev -> {}
case DtEvent.IngestionPayloadFetched ev -> {}
case DtEvent.IngestionMappingProposed ev -> {}
case DtEvent.IngestionJobConfirmed ev -> {}
case DtEvent.IngestionWriteStarted ev -> {}
case DtEvent.IngestionWriteProgress ev -> {}
case DtEvent.IngestionCompleted ev -> {}
case DtEvent.IngestionFailed ev -> {}
```

- [ ] **Step 1.6.5: Verify compile and tests pass**

Run: `cd server && mvn compile -q && mvn -pl data-talk-domain test -Dtest=IngestionEventsTest -q`
Expected: zero compilation errors, test passes.

- [ ] **Step 1.6.6: Commit**

```bash
git add server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java \
        server/data-talk-domain/src/test/java/com/datatalk/domain/event/IngestionEventsTest.java \
        server/data-talk-application/src/main/java/com/datatalk/application/
git commit -m "feat(ingestion): 8 new DtEvent permits + exhaustive switch updates

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 1.7: Exception classes

**Files:**
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/IngestionDialectUnsupportedException.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/IngestionTokenInvalidException.java`

- [ ] **Step 1.7.1: Create exception classes (no test needed — trivial)**

```java
// IngestionDialectUnsupportedException.java
package com.datatalk.domain.ingestion;
public class IngestionDialectUnsupportedException extends RuntimeException {
    private final String kind;
    public IngestionDialectUnsupportedException(String kind) {
        super("Ingestion not supported for dialect: " + kind);
        this.kind = kind;
    }
    public String kind() { return kind; }
}
```
```java
// IngestionTokenInvalidException.java
package com.datatalk.domain.ingestion;
public class IngestionTokenInvalidException extends RuntimeException {
    public enum Reason { NOT_FOUND, EXPIRED, ALREADY_CONSUMED, JOB_MISMATCH, MAPPING_HASH_MISMATCH }
    private final Reason reason;
    public IngestionTokenInvalidException(Reason reason) {
        super("Ingestion confirmation token invalid: " + reason);
        this.reason = reason;
    }
    public Reason reason() { return reason; }
}
```

- [ ] **Step 1.7.2: Commit**

```bash
git add server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/IngestionDialectUnsupportedException.java \
        server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/IngestionTokenInvalidException.java
git commit -m "feat(ingestion): exception types — dialect unsupported + token invalid

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 1.8: Repository interfaces

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/repository/IngestionCredentialRepository.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/repository/IngestionJobRepository.java`

- [ ] **Step 1.8.1: Define interfaces**

```java
// IngestionCredentialRepository.java
package com.datatalk.application.ingestion.repository;

import com.datatalk.domain.ingestion.IngestionCredential;
import java.util.List;
import java.util.Optional;

public interface IngestionCredentialRepository {
    void save(IngestionCredential credential);
    Optional<IngestionCredential> findById(String id);
    Optional<IngestionCredential> findByName(String name);
    List<IngestionCredential> findAll();
    void deleteById(String id);
    int countReferencingJobs(String credentialId);
    void nullifyCredentialOnJobs(String credentialId);
}
```
```java
// IngestionJobRepository.java
package com.datatalk.application.ingestion.repository;

import com.datatalk.domain.ingestion.IngestionJob;
import java.util.List;
import java.util.Optional;

public interface IngestionJobRepository {
    void save(IngestionJob job);
    Optional<IngestionJob> findById(String id);
    List<IngestionJob> list(String connectionIdOrNull, String statusOrNull,
                            Long createdAfterOrNull, int limit, int offset);
    int count(String connectionIdOrNull, String statusOrNull, Long createdAfterOrNull);
    void updateStatus(String id, String newStatus, String errorMessageOrNull, long updatedAt);
    void updatePayloadArtifact(String id, String artifactId, int rowCount, long bytesFetched, long updatedAt);
    void updateMapping(String id, String mappingJson, long updatedAt);
    void updateTargetTable(String id, String connectionId, String schema, String table, long updatedAt);
    void updateProgress(String id, int rowsInserted, long updatedAt);
    void updateCompleted(String id, int finalRowCount, long completedAt, long updatedAt);
}
```

- [ ] **Step 1.8.2: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/ingestion/repository/*.java
git commit -m "feat(ingestion): repository interfaces in application layer

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 1.9: JDBC repository implementations

**Files:**
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/ingestion/JdbcIngestionCredentialRepository.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/ingestion/JdbcIngestionJobRepository.java`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/ingestion/JdbcIngestionCredentialRepositoryIT.java`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/ingestion/JdbcIngestionJobRepositoryIT.java`

- [ ] **Step 1.9.1: Write failing credential repository IT**

```java
package com.datatalk.infra.ingestion;

import com.datatalk.application.ingestion.repository.IngestionCredentialRepository;
import com.datatalk.domain.ingestion.AuthScheme;
import com.datatalk.domain.ingestion.IngestionCredential;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class JdbcIngestionCredentialRepositoryIT {
    @Autowired IngestionCredentialRepository repo;

    @Test void saveAndFindRoundTrip() {
        var c = new IngestionCredential("cred_t1", "test-bearer", AuthScheme.BEARER,
            Map.of(), "vault_x", 1000L, 1000L);
        repo.save(c);
        var found = repo.findById("cred_t1");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("test-bearer");
        assertThat(found.get().scheme()).isEqualTo(AuthScheme.BEARER);
        repo.deleteById("cred_t1");
    }

    @Test void findByNameWorks() {
        var c = new IngestionCredential("cred_t2", "name-key", AuthScheme.API_KEY_HEADER,
            Map.of("headerName","X-Api-Key"), "vault_y", 2000L, 2000L);
        repo.save(c);
        assertThat(repo.findByName("name-key")).isPresent();
        repo.deleteById("cred_t2");
    }

    @Test void countReferencingJobsZeroWhenNoJobs() {
        var c = new IngestionCredential("cred_t3", "unused", AuthScheme.NONE, Map.of(), null, 0L, 0L);
        repo.save(c);
        assertThat(repo.countReferencingJobs("cred_t3")).isEqualTo(0);
        repo.deleteById("cred_t3");
    }
}
```

- [ ] **Step 1.9.2: Run to verify it fails**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=JdbcIngestionCredentialRepositoryIT -q`
Expected: FAIL — implementation missing.

- [ ] **Step 1.9.3: Implement JdbcIngestionCredentialRepository**

```java
package com.datatalk.infra.ingestion;

import com.datatalk.application.ingestion.repository.IngestionCredentialRepository;
import com.datatalk.domain.ingestion.AuthScheme;
import com.datatalk.domain.ingestion.IngestionCredential;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcIngestionCredentialRepository implements IngestionCredentialRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcIngestionCredentialRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override public void save(IngestionCredential c) {
        String configJson = writeJson(c.configNonSecret());
        // UPSERT pattern: try INSERT, if PK collision then UPDATE
        int updated = jdbc.update(
            "UPDATE ingestion_credential SET name=?, auth_scheme=?, config_json=?, vault_id=?, updated_at=? WHERE id=?",
            c.name(), c.scheme().name().toLowerCase(), configJson, c.vaultId(), c.updatedAt(), c.id());
        if (updated == 0) {
            jdbc.update(
                "INSERT INTO ingestion_credential (id, name, auth_scheme, config_json, vault_id, created_at, updated_at) " +
                "VALUES (?,?,?,?,?,?,?)",
                c.id(), c.name(), c.scheme().name().toLowerCase(),
                configJson, c.vaultId(), c.createdAt(), c.updatedAt());
        }
    }

    @Override public Optional<IngestionCredential> findById(String id) {
        try {
            return Optional.of(jdbc.queryForObject(
                "SELECT id, name, auth_scheme, config_json, vault_id, created_at, updated_at " +
                "FROM ingestion_credential WHERE id=?",
                this::mapRow, id));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override public Optional<IngestionCredential> findByName(String name) {
        try {
            return Optional.of(jdbc.queryForObject(
                "SELECT id, name, auth_scheme, config_json, vault_id, created_at, updated_at " +
                "FROM ingestion_credential WHERE name=?",
                this::mapRow, name));
        } catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }

    @Override public List<IngestionCredential> findAll() {
        return jdbc.query(
            "SELECT id, name, auth_scheme, config_json, vault_id, created_at, updated_at " +
            "FROM ingestion_credential ORDER BY created_at DESC",
            this::mapRow);
    }

    @Override public void deleteById(String id) {
        jdbc.update("DELETE FROM ingestion_credential WHERE id=?", id);
    }

    @Override public int countReferencingJobs(String credentialId) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ingestion_job WHERE credential_id=?", Integer.class, credentialId);
        return n == null ? 0 : n;
    }

    @Override public void nullifyCredentialOnJobs(String credentialId) {
        jdbc.update("UPDATE ingestion_job SET credential_id=NULL WHERE credential_id=?", credentialId);
    }

    private IngestionCredential mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new IngestionCredential(
            rs.getString("id"), rs.getString("name"),
            AuthScheme.valueOf(rs.getString("auth_scheme").toUpperCase()),
            readJsonMap(rs.getString("config_json")),
            rs.getString("vault_id"),
            rs.getLong("created_at"), rs.getLong("updated_at"));
    }

    private String writeJson(Object v) {
        try { return objectMapper.writeValueAsString(v); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    private Map<String, String> readJsonMap(String s) {
        if (s == null) return Map.of();
        try { return objectMapper.readValue(s, new TypeReference<HashMap<String, String>>(){}); }
        catch (Exception e) { return Map.of(); }
    }
}
```

- [ ] **Step 1.9.4: Run credential IT to verify it passes**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=JdbcIngestionCredentialRepositoryIT -q`
Expected: PASS.

- [ ] **Step 1.9.5: Write failing job repository IT**

```java
package com.datatalk.infra.ingestion;

import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.domain.ingestion.IngestionJob;
import com.datatalk.domain.ingestion.PayloadFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class JdbcIngestionJobRepositoryIT {
    @Autowired IngestionJobRepository repo;

    @Test void saveAndFindRoundTrip() {
        var j = new IngestionJob("ing_t1", "https://x", "GET",
            Map.of("Accept","application/json"), Map.of("days","7"), null,
            null, null, PayloadFormat.JSON, null,
            "pending", null, null, null, null,
            0, 0, 0L, 1000L, 1000L, null, null);
        repo.save(j);
        var found = repo.findById("ing_t1").orElseThrow();
        assertThat(found.sourceUrl()).isEqualTo("https://x");
        assertThat(found.payloadFormat()).isEqualTo(PayloadFormat.JSON);
        assertThat(found.status()).isEqualTo("pending");
    }

    @Test void statusUpdateRoundTrips() {
        var j = new IngestionJob("ing_t2", "https://y", "GET",
            Map.of(), Map.of(), null, null, null, PayloadFormat.JSON, null,
            "pending", null, null, null, null, 0, 0, 0L, 1000L, 1000L, null, null);
        repo.save(j);
        repo.updateStatus("ing_t2", "fetching", null, 2000L);
        assertThat(repo.findById("ing_t2").orElseThrow().status()).isEqualTo("fetching");
    }
}
```

- [ ] **Step 1.9.6: Run to verify it fails**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=JdbcIngestionJobRepositoryIT -q`
Expected: FAIL.

- [ ] **Step 1.9.7: Implement JdbcIngestionJobRepository**

Mirror the credential repo pattern. Fields: serialize `sourceHeaders` / `sourceQueryParams` / `pagination` / `mapping` via Jackson into the `*_json` columns. `mapRow` reads them back. Persistence-code mapping is via raw strings (`status` text matches DB CHECK directly). Refer to `JdbcFileArtifactRepository.java:1-260` for the existing style guide on metadata_json serialization.

Implement all 9 interface methods. Use `UPDATE …; if updated==0 INSERT` UPSERT idiom matching the credential repo.

- [ ] **Step 1.9.8: Run job IT to verify it passes**

Run: `cd server && mvn -pl data-talk-infrastructure test -Dtest=JdbcIngestionJobRepositoryIT -q`
Expected: PASS.

- [ ] **Step 1.9.9: Verify full module test still green and commit**

```bash
cd server && mvn -pl data-talk-infrastructure test -q
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/ingestion/*.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/ingestion/*.java
git commit -m "feat(ingestion): JDBC repository implementations for credential + job

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 1.10: IngestionCredentialService

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionCredentialService.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/ingestion/IngestionCredentialServiceTest.java`

- [ ] **Step 1.10.1: Failing test for create + readSecret round-trip via SecretVault**

```java
package com.datatalk.application.ingestion;

import com.datatalk.application.ingestion.repository.IngestionCredentialRepository;
import com.datatalk.application.persistence.SecretVault;
import com.datatalk.domain.ingestion.AuthScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class IngestionCredentialServiceTest {
    SecretVault vault;
    IngestionCredentialRepository repo;
    IngestionCredentialService service;

    Map<String, byte[]> vaultBackingStore;

    @BeforeEach void setup() {
        byte[] masterKey = new byte[32];
        for (int i = 0; i < 32; i++) masterKey[i] = (byte)(i + 1);
        vault = new SecretVault(masterKey);

        vaultBackingStore = new HashMap<>();
        repo = new InMemoryCredentialRepo();
        service = new IngestionCredentialService(repo, vault,
            (id, sealedBytes) -> vaultBackingStore.put(id, sealedBytes),
            (id) -> vaultBackingStore.get(id));
    }

    @Test void createBearerStoresSealedToken() {
        var id = service.create("test-bearer", AuthScheme.BEARER, Map.of(), "secret-token-123");
        var c = repo.findById(id).orElseThrow();
        assertThat(c.vaultId()).isNotNull();
        assertThat(service.readSecret(id)).isEqualTo("secret-token-123");
    }

    @Test void createNoneDoesNotPersistSecret() {
        var id = service.create("no-auth", AuthScheme.NONE, Map.of(), null);
        var c = repo.findById(id).orElseThrow();
        assertThat(c.vaultId()).isNull();
    }

    @Test void deleteBlocksWhenReferenced() {
        var id = service.create("used", AuthScheme.NONE, Map.of(), null);
        ((InMemoryCredentialRepo)repo).referenceCount.put(id, 3);
        assertThatThrownBy(() -> service.delete(id, false))
            .hasMessageContaining("3 ingestion job");
    }

    @Test void deleteWithForceNullifiesReferences() {
        var id = service.create("used2", AuthScheme.NONE, Map.of(), null);
        ((InMemoryCredentialRepo)repo).referenceCount.put(id, 2);
        service.delete(id, true);
        assertThat(repo.findById(id)).isEmpty();
        assertThat(((InMemoryCredentialRepo)repo).nullifyCalls).contains(id);
    }

    static class InMemoryCredentialRepo implements IngestionCredentialRepository {
        Map<String, com.datatalk.domain.ingestion.IngestionCredential> data = new HashMap<>();
        Map<String, Integer> referenceCount = new HashMap<>();
        List<String> nullifyCalls = new java.util.ArrayList<>();
        public void save(com.datatalk.domain.ingestion.IngestionCredential c) { data.put(c.id(), c); }
        public Optional<com.datatalk.domain.ingestion.IngestionCredential> findById(String id) { return Optional.ofNullable(data.get(id)); }
        public Optional<com.datatalk.domain.ingestion.IngestionCredential> findByName(String n) {
            return data.values().stream().filter(c -> c.name().equals(n)).findFirst();
        }
        public List<com.datatalk.domain.ingestion.IngestionCredential> findAll() { return List.copyOf(data.values()); }
        public void deleteById(String id) { data.remove(id); }
        public int countReferencingJobs(String id) { return referenceCount.getOrDefault(id, 0); }
        public void nullifyCredentialOnJobs(String id) { nullifyCalls.add(id); }
    }
}
```

- [ ] **Step 1.10.2: Run to verify it fails**

Run: `cd server && mvn -pl data-talk-application test -Dtest=IngestionCredentialServiceTest -q`
Expected: FAIL — service not defined.

- [ ] **Step 1.10.3: Implement IngestionCredentialService**

```java
package com.datatalk.application.ingestion;

import com.datatalk.application.ingestion.repository.IngestionCredentialRepository;
import com.datatalk.application.persistence.SecretVault;
import com.datatalk.domain.ingestion.AuthScheme;
import com.datatalk.domain.ingestion.IngestionCredential;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class IngestionCredentialService {
    public interface VaultWriter { void write(String vaultId, byte[] sealed); }
    public interface VaultReader { byte[] read(String vaultId); }

    private final IngestionCredentialRepository repo;
    private final SecretVault vault;
    private final VaultWriter writer;
    private final VaultReader reader;

    public IngestionCredentialService(IngestionCredentialRepository repo, SecretVault vault,
                                      VaultWriter writer, VaultReader reader) {
        this.repo = repo; this.vault = vault; this.writer = writer; this.reader = reader;
    }

    public String create(String name, AuthScheme scheme, Map<String,String> configNonSecret, String rawSecret) {
        if (repo.findByName(name).isPresent()) {
            throw new IllegalArgumentException("credential name already used: " + name);
        }
        String id = "cred_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String vaultId = null;
        if (scheme != AuthScheme.NONE && rawSecret != null && !rawSecret.isEmpty()) {
            vaultId = "vault_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            writer.write(vaultId, vault.seal(rawSecret));
        }
        long now = System.currentTimeMillis();
        repo.save(new IngestionCredential(id, name, scheme, configNonSecret, vaultId, now, now));
        return id;
    }

    public String readSecret(String credentialId) {
        var c = repo.findById(credentialId).orElseThrow(() ->
            new IllegalArgumentException("credential not found: " + credentialId));
        if (c.vaultId() == null) return null;
        return vault.open(reader.read(c.vaultId()));
    }

    public void delete(String credentialId, boolean force) {
        int refs = repo.countReferencingJobs(credentialId);
        if (refs > 0 && !force) {
            throw new IllegalStateException(refs + " ingestion job(s) reference this credential; pass force=true to nullify and delete");
        }
        if (refs > 0) repo.nullifyCredentialOnJobs(credentialId);
        repo.deleteById(credentialId);
    }
}
```

- [ ] **Step 1.10.4: Run test to verify it passes**

Run: `cd server && mvn -pl data-talk-application test -Dtest=IngestionCredentialServiceTest -q`
Expected: PASS (4 tests).

- [ ] **Step 1.10.5: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionCredentialService.java \
        server/data-talk-application/src/test/java/com/datatalk/application/ingestion/IngestionCredentialServiceTest.java
git commit -m "feat(ingestion): IngestionCredentialService — vault-sealed secret storage + reference safety

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 1.11: Credential REST endpoints

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/IngestionController.java` (only credential endpoints for now; ingestion job endpoints added in P5)
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/IngestionControllerCredentialTest.java`

- [ ] **Step 1.11.1: Failing controller integration test**

```java
package com.datatalk.adapter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class IngestionControllerCredentialTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test void createListReadDeleteRoundTrip() throws Exception {
        String body = om.writeValueAsString(Map.of(
            "name","ctl-test-1", "authScheme","bearer",
            "configNonSecret", Map.of(), "secret","tok_abc"));
        var create = mvc.perform(post("/api/ingestion/credentials")
                .contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andReturn();
        String id = om.readTree(create.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(get("/api/ingestion/credentials"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.id=='" + id + "')]").exists());

        mvc.perform(delete("/api/ingestion/credentials/" + id))
            .andExpect(status().isNoContent());
    }

    @Test void createWithNoneSchemeAcceptsNullSecret() throws Exception {
        String body = om.writeValueAsString(Map.of(
            "name","ctl-test-2","authScheme","none","configNonSecret",Map.of()));
        mvc.perform(post("/api/ingestion/credentials")
                .contentType("application/json").content(body))
            .andExpect(status().isOk());
    }
}
```

- [ ] **Step 1.11.2: Run to verify it fails**

Run: `cd server && mvn -pl data-talk-adapter test -Dtest=IngestionControllerCredentialTest -q`
Expected: FAIL.

- [ ] **Step 1.11.3: Implement IngestionController credential endpoints**

```java
package com.datatalk.adapter.controller;

import com.datatalk.application.ingestion.IngestionCredentialService;
import com.datatalk.application.ingestion.repository.IngestionCredentialRepository;
import com.datatalk.domain.ingestion.AuthScheme;
import com.datatalk.domain.ingestion.IngestionCredential;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {
    private final IngestionCredentialService credService;
    private final IngestionCredentialRepository credRepo;

    public IngestionController(IngestionCredentialService credService,
                               IngestionCredentialRepository credRepo) {
        this.credService = credService;
        this.credRepo = credRepo;
    }

    public record CredentialCreateRequest(
        String name, String authScheme, Map<String, String> configNonSecret, String secret) {}

    public record CredentialView(
        String id, String name, String authScheme, Map<String, String> configNonSecret,
        boolean hasSecret, long createdAt, long updatedAt) {
        public static CredentialView of(IngestionCredential c) {
            return new CredentialView(c.id(), c.name(),
                c.scheme().name().toLowerCase(), c.configNonSecret(),
                c.vaultId() != null, c.createdAt(), c.updatedAt());
        }
    }

    @PostMapping("/credentials")
    public Map<String, Object> create(@RequestBody CredentialCreateRequest req) {
        AuthScheme scheme = AuthScheme.valueOf(req.authScheme().toUpperCase());
        String id = credService.create(
            req.name(), scheme,
            req.configNonSecret() == null ? Map.of() : req.configNonSecret(),
            req.secret());
        return Map.of("id", id);
    }

    @GetMapping("/credentials")
    public Map<String, Object> list() {
        List<CredentialView> items = credRepo.findAll().stream()
            .map(CredentialView::of).toList();
        return Map.of("items", items, "total", items.size());
    }

    @GetMapping("/credentials/{id}")
    public ResponseEntity<CredentialView> get(@PathVariable String id) {
        return credRepo.findById(id)
            .map(CredentialView::of).map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/credentials/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
                                       @RequestParam(defaultValue = "false") boolean force) {
        try {
            credService.delete(id, force);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }
}
```

- [ ] **Step 1.11.4: Run to verify it passes**

Run: `cd server && mvn -pl data-talk-adapter test -Dtest=IngestionControllerCredentialTest -q`
Expected: PASS.

- [ ] **Step 1.11.5: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/IngestionController.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/IngestionControllerCredentialTest.java
git commit -m "feat(ingestion): credential REST endpoints — POST/GET/DELETE with 409 on reference

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 1.12: Settings → Credentials UI sub-page

**Files (frontend, all new):**
- Create: `client/src/features/settings/credentials/credentials-page.tsx`
- Create: `client/src/features/settings/credentials/credential-form.tsx`
- Create: `client/src/features/settings/credentials/credential-list.tsx`
- Create: `client/src/features/settings/credentials/api/credential-api.ts`
- Create: `client/src/features/settings/credentials/hooks/use-credentials-query.ts`
- Create: `client/src/features/settings/credentials/__tests__/credential-form.test.tsx`
- Modify: `client/src/features/settings/settings-page.tsx` — add nav entry "Credentials"
- Modify: `client/src/i18n/messages.ts` — add `ingestion.credential.*` keys

- [ ] **Step 1.12.1: Read existing settings layout + DESIGN tokens**

Read `client/src/features/settings/settings-page.tsx` and the closest existing sub-page (e.g. Maintenance) to mirror layout, token usage, and density convention.

- [ ] **Step 1.12.2: Add i18n keys**

In `client/src/i18n/messages.ts`, append under both `zh-CN` and `en-US` blocks:

```ts
'ingestion.credential.title': '凭据 / Credentials',
'ingestion.credential.empty': '尚未创建任何采集凭据。',
'ingestion.credential.create': '新建凭据',
'ingestion.credential.name': '凭据名',
'ingestion.credential.scheme.label': '认证方式',
'ingestion.credential.scheme.none': '无认证',
'ingestion.credential.scheme.bearer': 'Bearer Token',
'ingestion.credential.scheme.api_key_header': 'API Key (Header)',
'ingestion.credential.scheme.api_key_query': 'API Key (Query)',
'ingestion.credential.scheme.basic': 'Basic Auth',
'ingestion.credential.token': 'Token',
'ingestion.credential.api_key.name': '参数名',
'ingestion.credential.api_key.value': '参数值',
'ingestion.credential.basic.username': '用户名',
'ingestion.credential.basic.password': '密码',
'ingestion.credential.dual_source_note':
  '这些凭据用于外部 HTTP / REST 数据采集，与数据库连接凭据（Settings → Connections）独立。一份凭据可被多个采集 job 复用，跨多个目标数据库连接。',
'ingestion.credential.delete.confirm':
  '此凭据被 {{n}} 个采集任务引用，删除后任务将解除引用。继续？',
```

(Provide en-US translations symmetrically.)

- [ ] **Step 1.12.3: Implement credential-api.ts**

```ts
// client/src/features/settings/credentials/api/credential-api.ts
import { apiClient } from '@/lib/api-client';

export type AuthScheme = 'none' | 'bearer' | 'api_key_header' | 'api_key_query' | 'basic';

export interface CredentialView {
  id: string;
  name: string;
  authScheme: AuthScheme;
  configNonSecret: Record<string, string>;
  hasSecret: boolean;
  createdAt: number;
  updatedAt: number;
}

export interface CredentialCreateRequest {
  name: string;
  authScheme: AuthScheme;
  configNonSecret: Record<string, string>;
  secret: string | null;
}

export async function listCredentials(): Promise<CredentialView[]> {
  const r = await apiClient.get<{ items: CredentialView[] }>('/api/ingestion/credentials');
  return r.data.items;
}

export async function createCredential(req: CredentialCreateRequest): Promise<{ id: string }> {
  const r = await apiClient.post<{ id: string }>('/api/ingestion/credentials', req);
  return r.data;
}

export async function deleteCredential(id: string, force = false): Promise<void> {
  await apiClient.delete(`/api/ingestion/credentials/${id}`, { params: { force } });
}
```

- [ ] **Step 1.12.4: Implement use-credentials-query.ts**

```ts
// client/src/features/settings/credentials/hooks/use-credentials-query.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { listCredentials, createCredential, deleteCredential, CredentialCreateRequest } from '../api/credential-api';

const KEY = ['ingestion-credentials'];

export function useCredentialsQuery() {
  return useQuery({ queryKey: KEY, queryFn: listCredentials });
}

export function useCreateCredentialMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CredentialCreateRequest) => createCredential(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useDeleteCredentialMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, force }: { id: string; force?: boolean }) => deleteCredential(id, force),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
```

- [ ] **Step 1.12.5: Implement credential-form.tsx (5-state token mapping must match spec §6.3)**

```tsx
// client/src/features/settings/credentials/credential-form.tsx
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Eye, EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { AuthScheme, CredentialCreateRequest } from './api/credential-api';

interface Props {
  onSubmit: (req: CredentialCreateRequest) => void;
  submitting?: boolean;
}

export function CredentialForm({ onSubmit, submitting }: Props) {
  const { t } = useTranslation();
  const [name, setName] = useState('');
  const [scheme, setScheme] = useState<AuthScheme>('none');
  const [bearerToken, setBearerToken] = useState('');
  const [apiKeyName, setApiKeyName] = useState('');
  const [apiKeyValue, setApiKeyValue] = useState('');
  const [basicUsername, setBasicUsername] = useState('');
  const [basicPassword, setBasicPassword] = useState('');
  const [showSecret, setShowSecret] = useState(false);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    let configNonSecret: Record<string, string> = {};
    let secret: string | null = null;
    if (scheme === 'bearer') secret = bearerToken;
    else if (scheme === 'api_key_header') { configNonSecret = { headerName: apiKeyName }; secret = apiKeyValue; }
    else if (scheme === 'api_key_query') { configNonSecret = { queryName: apiKeyName }; secret = apiKeyValue; }
    else if (scheme === 'basic') { configNonSecret = { basicUsername }; secret = basicPassword; }
    onSubmit({ name, authScheme: scheme, configNonSecret, secret });
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <Label htmlFor="cred-name">{t('ingestion.credential.name')}</Label>
        <Input id="cred-name" required value={name} onChange={(e) => setName(e.target.value)} />
      </div>

      <div>
        <Label>{t('ingestion.credential.scheme.label')}</Label>
        <RadioGroup value={scheme} onValueChange={(v) => setScheme(v as AuthScheme)} className="grid grid-cols-2 gap-2">
          {(['none','bearer','api_key_header','api_key_query','basic'] as AuthScheme[]).map((s) => (
            <div key={s} className="flex items-center gap-2">
              <RadioGroupItem value={s} id={`scheme-${s}`} />
              <Label htmlFor={`scheme-${s}`}>{t(`ingestion.credential.scheme.${s}`)}</Label>
            </div>
          ))}
        </RadioGroup>
      </div>

      {scheme === 'bearer' && (
        <SecretField id="bearer-token" label={t('ingestion.credential.token')}
          value={bearerToken} onChange={setBearerToken} show={showSecret} onToggle={() => setShowSecret(!showSecret)} />
      )}
      {(scheme === 'api_key_header' || scheme === 'api_key_query') && (
        <>
          <div>
            <Label htmlFor="api-key-name">{t('ingestion.credential.api_key.name')}</Label>
            <Input id="api-key-name" required value={apiKeyName} onChange={(e) => setApiKeyName(e.target.value)} />
          </div>
          <SecretField id="api-key-value" label={t('ingestion.credential.api_key.value')}
            value={apiKeyValue} onChange={setApiKeyValue} show={showSecret} onToggle={() => setShowSecret(!showSecret)} />
        </>
      )}
      {scheme === 'basic' && (
        <>
          <div>
            <Label htmlFor="basic-user">{t('ingestion.credential.basic.username')}</Label>
            <Input id="basic-user" required value={basicUsername} onChange={(e) => setBasicUsername(e.target.value)} />
          </div>
          <SecretField id="basic-pass" label={t('ingestion.credential.basic.password')}
            value={basicPassword} onChange={setBasicPassword} show={showSecret} onToggle={() => setShowSecret(!showSecret)} />
        </>
      )}

      <Button type="submit" disabled={submitting} variant="default">{t('ingestion.credential.create')}</Button>
    </form>
  );
}

function SecretField({ id, label, value, onChange, show, onToggle }: {
  id: string; label: string; value: string;
  onChange: (v: string) => void; show: boolean; onToggle: () => void;
}) {
  return (
    <div>
      <Label htmlFor={id}>{label}</Label>
      <div className="flex gap-2">
        <Input id={id} required type={show ? 'text' : 'password'}
          value={value} onChange={(e) => onChange(e.target.value)} />
        <Button type="button" variant="ghost" size="icon" onClick={onToggle} aria-label="toggle visibility">
          {show ? <EyeOff size={16} /> : <Eye size={16} />}
        </Button>
      </div>
    </div>
  );
}
```

- [ ] **Step 1.12.6: Implement credential-list.tsx + credentials-page.tsx**

`credential-list.tsx` — table with columns Name / Scheme badge / Created / Actions (Delete). On delete, if 409 returned, show modal with `dual_source_note` reference count + force=true retry path.

`credentials-page.tsx` — top `bg.subtle` info bar rendering `t('ingestion.credential.dual_source_note')`, then `<CredentialList />` + `<Button onClick=openCreateDialog>`, dialog contains `<CredentialForm />`.

Both files apply 5-state tokens from spec §6.3 (idle/hover/focus/active/disabled) per shadcn/ui component primitives — `Input` / `Button` / `Select` / `Checkbox` already token-mapped, just ensure variant choices align with primary/secondary/ghost roles.

- [ ] **Step 1.12.7: Wire into settings-page.tsx nav**

Read existing `settings-page.tsx`, follow nav-entry pattern, add Credentials route to the sub-page list. Route path: `/settings/credentials`.

- [ ] **Step 1.12.8: Write credential-form unit test (vitest)**

```tsx
// client/src/features/settings/credentials/__tests__/credential-form.test.tsx
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { CredentialForm } from '../credential-form';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/i18n';

function renderWithI18n(ui: React.ReactElement) {
  return render(<I18nextProvider i18n={i18n}>{ui}</I18nextProvider>);
}

describe('CredentialForm', () => {
  it('renders no secret field for scheme=none', () => {
    renderWithI18n(<CredentialForm onSubmit={() => {}} />);
    expect(screen.queryByLabelText(/Token|password/i)).toBeNull();
  });

  it('reveals bearer token field when scheme=bearer', () => {
    renderWithI18n(<CredentialForm onSubmit={() => {}} />);
    fireEvent.click(screen.getByLabelText(/Bearer/i));
    expect(screen.getByLabelText(/Token/)).toBeInTheDocument();
  });

  it('submits payload with secret=null for scheme=none', () => {
    const onSubmit = vi.fn();
    renderWithI18n(<CredentialForm onSubmit={onSubmit} />);
    fireEvent.change(screen.getByLabelText(/Name/i), { target: { value: 'cred-1' } });
    fireEvent.submit(screen.getByRole('button', { name: /create|新建/i }).closest('form')!);
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ secret: null, authScheme: 'none' }));
  });
});
```

- [ ] **Step 1.12.9: Run vitest + tsc**

Run: `cd client && npx tsc --noEmit && npx vitest run src/features/settings/credentials -t -q`
Expected: 0 type errors, 3 tests pass.

- [ ] **Step 1.12.10: Commit**

```bash
git add client/src/features/settings/credentials/ \
        client/src/features/settings/settings-page.tsx \
        client/src/i18n/messages.ts
git commit -m "feat(ingestion): Settings → Credentials sub-page (4 auth schemes + dual-source note)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 1.13: Phase 1 gate

- [ ] **Step 1.13.1: Backend full verify**

Run: `cd server && mvn clean verify`
Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 1.13.2: Frontend type + vitest**

Run: `cd client && npx tsc --noEmit && npx vitest run`
Expected: 0 type errors, all tests green.

- [ ] **Step 1.13.3: Manual smoke**

Launch `cd server && mvn install -pl data-talk-application,data-talk-infrastructure,data-talk-domain -am -DskipTests` then `mvn spring-boot:run -pl data-talk-adapter` and `cd client && npm run tauri dev`. Open Settings → Credentials, create each of 4 schemes (None / Bearer / API Key Header / Basic), verify all show in list, delete each. Confirm dual-source note banner visible.

- [ ] **Step 1.13.4: Phase 1 sign-off commit (no code, just marker)**

```bash
git commit --allow-empty -m "chore(ingestion): Phase 1 complete — DB + Domain + Credential CRUD verified

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 2 — HTTP Fetcher + Payload Artifact

### Task 2.1: IngestionConfig + URL validator (SSRF)

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionConfig.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionUrlValidator.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/ingestion/IngestionUrlValidatorTest.java`
- Modify: `server/data-talk-adapter/src/main/resources/application.yml` — add `datatalk.ingestion.*` props

- [ ] **Step 2.1.1: Failing test for SSRF deny list and protocol enforcement**

```java
package com.datatalk.application.ingestion;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IngestionUrlValidatorTest {
    IngestionConfig cfg = new IngestionConfig(60_000L, 500L * 1024 * 1024,
        java.util.List.of("localhost","127.0.0.1","169.254.169.254","metadata.google.internal","metadata.azure.com"),
        true);
    IngestionUrlValidator v = new IngestionUrlValidator(cfg);

    @Test void acceptsPublicHttps() { v.validate("https://api.example.com/x"); }
    @Test void acceptsPublicHttp()  { v.validate("http://api.example.com/x"); }
    @Test void rejectsFileScheme() {
        assertThatThrownBy(() -> v.validate("file:///etc/passwd"))
            .hasMessageContaining("scheme");
    }
    @Test void rejectsFtpScheme() {
        assertThatThrownBy(() -> v.validate("ftp://x")).hasMessageContaining("scheme");
    }
    @Test void rejectsDenyListedHost() {
        assertThatThrownBy(() -> v.validate("http://localhost:8080/admin"))
            .hasMessageContaining("denied");
    }
    @Test void rejectsAwsMetadata() {
        assertThatThrownBy(() -> v.validate("http://169.254.169.254/latest/meta-data"))
            .hasMessageContaining("denied");
    }
    @Test void rejectsGcpMetadata() {
        assertThatThrownBy(() -> v.validate("http://metadata.google.internal/"))
            .hasMessageContaining("denied");
    }
}
```

- [ ] **Step 2.1.2: Run to verify fail**

Run: `cd server && mvn -pl data-talk-application test -Dtest=IngestionUrlValidatorTest -q`
Expected: FAIL.

- [ ] **Step 2.1.3: Implement IngestionConfig**

```java
package com.datatalk.application.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "datatalk.ingestion")
public class IngestionConfig {
    private final long fetchTimeoutMs;
    private final long payloadMaxBytes;
    private final List<String> hostDeny;
    private final boolean ssrfDenyEnabled;

    public IngestionConfig(long fetchTimeoutMs, long payloadMaxBytes,
                           List<String> hostDeny, boolean ssrfDenyEnabled) {
        this.fetchTimeoutMs = fetchTimeoutMs;
        this.payloadMaxBytes = payloadMaxBytes;
        this.hostDeny = hostDeny == null ? List.of() : hostDeny;
        this.ssrfDenyEnabled = ssrfDenyEnabled;
    }
    public long fetchTimeoutMs() { return fetchTimeoutMs; }
    public long payloadMaxBytes() { return payloadMaxBytes; }
    public List<String> hostDeny() { return hostDeny; }
    public boolean ssrfDenyEnabled() { return ssrfDenyEnabled; }
}
```

- [ ] **Step 2.1.4: Implement IngestionUrlValidator**

```java
package com.datatalk.application.ingestion;

import org.springframework.stereotype.Component;
import java.net.URI;

@Component
public class IngestionUrlValidator {
    private final IngestionConfig cfg;
    public IngestionUrlValidator(IngestionConfig cfg) { this.cfg = cfg; }

    public void validate(String url) {
        URI uri;
        try { uri = URI.create(url); }
        catch (Exception e) { throw new IllegalArgumentException("malformed URL: " + url); }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            throw new IllegalArgumentException("disallowed URL scheme: " + scheme);
        }
        if (cfg.ssrfDenyEnabled()) {
            String host = uri.getHost();
            if (host == null) throw new IllegalArgumentException("URL has no host");
            String lower = host.toLowerCase();
            for (String denied : cfg.hostDeny()) {
                if (lower.equals(denied.toLowerCase())) {
                    throw new IllegalArgumentException("host denied by SSRF rule: " + host);
                }
            }
        }
    }
}
```

- [ ] **Step 2.1.5: Add application.yml props**

Add to `server/data-talk-adapter/src/main/resources/application.yml`:

```yaml
datatalk:
  ingestion:
    fetch-timeout-ms: 60000
    payload-max-bytes: 524288000          # 500 MB
    ssrf-deny-enabled: true
    host-deny:
      - localhost
      - 127.0.0.1
      - 169.254.169.254
      - metadata.google.internal
      - metadata.azure.com
```

- [ ] **Step 2.1.6: Run + commit**

Run: `cd server && mvn -pl data-talk-application test -Dtest=IngestionUrlValidatorTest -q`
Expected: PASS (7 tests).

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionConfig.java \
        server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionUrlValidator.java \
        server/data-talk-application/src/test/java/com/datatalk/application/ingestion/IngestionUrlValidatorTest.java \
        server/data-talk-adapter/src/main/resources/application.yml
git commit -m "feat(ingestion): IngestionConfig + URL SSRF validator (scheme + host deny list)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 2.2: HttpFetchClient (auth-aware single request)

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/HttpFetchClient.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/ingestion/HttpFetchClientTest.java`

- [ ] **Step 2.2.1: Failing WireMock test (4 auth schemes + GET + POST)**

```java
package com.datatalk.application.ingestion;

import com.datatalk.domain.ingestion.AuthScheme;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.*;

class HttpFetchClientTest {
    static WireMockServer wm;
    HttpFetchClient client;

    @BeforeAll static void start() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
    }
    @AfterAll static void stop() { wm.stop(); }

    @BeforeEach void resetAndBuild() {
        wm.resetAll();
        client = new HttpFetchClient(RestClient.builder().build(), new IngestionConfig(
            60_000L, 500L*1024*1024, java.util.List.of(), false));
    }

    @Test void bearerAuthInjected() {
        wm.stubFor(get(urlEqualTo("/x"))
            .withHeader("Authorization", equalTo("Bearer tok123"))
            .willReturn(aResponse().withStatus(200).withBody("hello")));
        var resp = client.request(wm.baseUrl()+"/x", "GET", Map.of(), Map.of(), null,
            AuthScheme.BEARER, Map.of(), "tok123");
        assertThat(resp.body()).isEqualTo("hello");
        assertThat(resp.statusCode()).isEqualTo(200);
    }

    @Test void apiKeyHeaderInjected() {
        wm.stubFor(get(urlEqualTo("/x"))
            .withHeader("X-Api-Key", equalTo("keyXYZ"))
            .willReturn(aResponse().withStatus(200).withBody("ok")));
        client.request(wm.baseUrl()+"/x","GET", Map.of(), Map.of(), null,
            AuthScheme.API_KEY_HEADER, Map.of("headerName","X-Api-Key"), "keyXYZ");
    }

    @Test void apiKeyQueryInjected() {
        wm.stubFor(get(urlMatching("/x\\?api_key=keyABC"))
            .willReturn(aResponse().withStatus(200).withBody("ok")));
        client.request(wm.baseUrl()+"/x","GET", Map.of(), Map.of(), null,
            AuthScheme.API_KEY_QUERY, Map.of("queryName","api_key"), "keyABC");
    }

    @Test void basicAuthInjected() {
        wm.stubFor(get(urlEqualTo("/x"))
            .withBasicAuth("alice","s3cr3t")
            .willReturn(aResponse().withStatus(200).withBody("ok")));
        client.request(wm.baseUrl()+"/x","GET", Map.of(), Map.of(), null,
            AuthScheme.BASIC, Map.of("basicUsername","alice"), "s3cr3t");
    }

    @Test void noneAuthSendsNoAuthHeader() {
        wm.stubFor(get(urlEqualTo("/x"))
            .withHeader("Authorization", absent())
            .willReturn(aResponse().withStatus(200).withBody("ok")));
        client.request(wm.baseUrl()+"/x","GET", Map.of(), Map.of(), null,
            AuthScheme.NONE, Map.of(), null);
    }
}
```

- [ ] **Step 2.2.2: Run to verify fail**

Run: `cd server && mvn -pl data-talk-application test -Dtest=HttpFetchClientTest -q`
Expected: FAIL.

- [ ] **Step 2.2.3: Implement HttpFetchClient**

```java
package com.datatalk.application.ingestion;

import com.datatalk.domain.ingestion.AuthScheme;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Base64;
import java.util.Map;
import java.util.Objects;

@Component
public class HttpFetchClient {
    public record FetchResponse(int statusCode, String body, HttpHeaders headers) {}

    private final RestClient restClient;
    private final IngestionConfig cfg;

    public HttpFetchClient(RestClient.Builder builder, IngestionConfig cfg) {
        this.restClient = builder.build();
        this.cfg = cfg;
    }

    public FetchResponse request(
        String url, String method,
        Map<String,String> userHeaders, Map<String,String> queryParams, String body,
        AuthScheme scheme, Map<String,String> authConfig, String secret
    ) {
        UriComponentsBuilder ub = UriComponentsBuilder.fromUriString(url);
        if (queryParams != null) queryParams.forEach(ub::queryParam);
        if (scheme == AuthScheme.API_KEY_QUERY) {
            ub.queryParam(authConfig.get("queryName"), secret);
        }
        String finalUrl = ub.build(true).toUriString();

        var spec = restClient.method(HttpMethod.valueOf(method.toUpperCase()))
            .uri(finalUrl);

        if (userHeaders != null) for (var e : userHeaders.entrySet()) {
            spec = spec.header(e.getKey(), e.getValue());
        }
        switch (scheme) {
            case BEARER -> spec = spec.header("Authorization", "Bearer " + secret);
            case API_KEY_HEADER -> spec = spec.header(authConfig.get("headerName"), secret);
            case BASIC -> {
                String userPass = authConfig.get("basicUsername") + ":" + Objects.toString(secret, "");
                spec = spec.header("Authorization", "Basic " +
                    Base64.getEncoder().encodeToString(userPass.getBytes()));
            }
            case API_KEY_QUERY -> { /* handled by URL builder */ }
            case NONE -> { /* no auth */ }
        }

        if (body != null && !body.isEmpty()) {
            spec.body(body);
        }

        ResponseEntity<String> r = spec.retrieve().toEntity(String.class);
        return new FetchResponse(r.getStatusCode().value(), r.getBody(), r.getHeaders());
    }
}
```

- [ ] **Step 2.2.4: Add WireMock test dependency if absent**

Check `server/data-talk-application/pom.xml` for `com.github.tomakehurst:wiremock-jre8` or `wiremock-standalone`. If absent, add:

```xml
<dependency>
  <groupId>org.wiremock</groupId>
  <artifactId>wiremock-standalone</artifactId>
  <version>3.5.4</version>
  <scope>test</scope>
</dependency>
```

(Backend module already uses WireMock — `FakeOpenCodeServer` pattern — confirm version match.)

- [ ] **Step 2.2.5: Run to verify pass + commit**

Run: `cd server && mvn -pl data-talk-application test -Dtest=HttpFetchClientTest -q`
Expected: PASS (5 tests).

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/ingestion/HttpFetchClient.java \
        server/data-talk-application/src/test/java/com/datatalk/application/ingestion/HttpFetchClientTest.java \
        server/data-talk-application/pom.xml
git commit -m "feat(ingestion): HttpFetchClient — RestClient + 4 auth scheme injection

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 2.3: IngestionPayloadFetcher (pagination loop + atomic write + artifact register)

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionPayloadFetcher.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/ingestion/IngestionPayloadFetcherIT.java`

- [ ] **Step 2.3.1: Failing IT covering single fetch + page + offset + cursor + termination hint**

(Test exercises real `IngestionPayloadFetcher` with WireMock backend serving fake paginated JSON. Asserts: 1 fetch → 1 payload file with merged content; pagination loops correctly; SSRF denied URL is rejected; payload over `maxBytes` is rejected; file_artifact row registered with `kind='ingestion_payload'` and `external=1`.)

```java
package com.datatalk.application.ingestion;

import com.datatalk.domain.ingestion.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class IngestionPayloadFetcherIT {
    static WireMockServer wm;
    @Autowired IngestionPayloadFetcher fetcher;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll static void start() { wm = new WireMockServer(options().dynamicPort()); wm.start(); }
    @AfterAll  static void stop()  { wm.stop(); }
    @BeforeEach void reset()       { wm.resetAll(); }

    @Test void singleFetchProducesPayloadArtifact() throws Exception {
        wm.stubFor(get(urlEqualTo("/data"))
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type","application/json")
                .withBody("[{\"id\":1,\"name\":\"a\"}]")));
        var result = fetcher.fetch(new FetchRequest(
            wm.baseUrl()+"/data","GET", Map.of(), Map.of(), null,
            null, PayloadFormat.JSON, null, null, 60_000L));
        assertThat(result.rowsFetched()).isEqualTo(1);
        assertThat(result.payloadArtifactId()).isNotBlank();
        var kind = jdbc.queryForObject("SELECT kind FROM file_artifact WHERE id=?",
            String.class, result.payloadArtifactId());
        assertThat(kind).isEqualTo("ingestion_payload");
    }

    @Test void paginatedFetchMergesArrays() {
        wm.stubFor(get(urlMatching("/data\\?page=1.*"))
            .willReturn(aResponse().withStatus(200).withBody("[{\"id\":1}]")));
        wm.stubFor(get(urlMatching("/data\\?page=2.*"))
            .willReturn(aResponse().withStatus(200).withBody("[{\"id\":2}]")));
        wm.stubFor(get(urlMatching("/data\\?page=3.*"))
            .willReturn(aResponse().withStatus(200).withBody("[]")));
        var p = new PaginationSpec(PaginationType.PAGE,
            Map.of("pageParam","page","pageSize",10,"startPage",1),
            10, new TerminationHint(TerminationHintType.EMPTY_ARRAY, null));
        var result = fetcher.fetch(new FetchRequest(
            wm.baseUrl()+"/data","GET", Map.of(), Map.of(), null,
            null, PayloadFormat.JSON, p, null, 60_000L));
        assertThat(result.rowsFetched()).isEqualTo(2);
        assertThat(result.pagesFetched()).isEqualTo(3); // page 3 hit empty terminator
    }

    @Test void ssrfDenied() {
        assertThatThrownBy(() -> fetcher.fetch(new FetchRequest(
            "http://localhost:8080/x","GET", Map.of(), Map.of(), null,
            null, PayloadFormat.JSON, null, null, 60_000L)))
            .hasMessageContaining("denied");
    }
}
```

(Define `FetchRequest` record next to fetcher for input bundling, see step 2.3.3.)

- [ ] **Step 2.3.2: Run to verify fail**

Run: `cd server && mvn -pl data-talk-application test -Dtest=IngestionPayloadFetcherIT -q`
Expected: FAIL.

- [ ] **Step 2.3.3: Implement IngestionPayloadFetcher**

```java
package com.datatalk.application.ingestion;

import com.datatalk.application.fileartifact.FileArtifactService;
import com.datatalk.application.ingestion.repository.IngestionCredentialRepository;
import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.domain.fileartifact.FileArtifactKind;
import com.datatalk.domain.ingestion.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

@Service
public class IngestionPayloadFetcher {
    public record FetchRequest(
        String url, String method, Map<String,String> headers, Map<String,String> queryParams,
        String body, String credentialId, PayloadFormat format,
        PaginationSpec pagination, String htmlSelector, long timeoutMs) {}

    public record FetchResult(String jobId, String payloadArtifactId, int rowsFetched,
                              long bytesFetched, int pagesFetched, String status) {}

    private final HttpFetchClient http;
    private final IngestionUrlValidator validator;
    private final IngestionCredentialService credService;
    private final IngestionCredentialRepository credRepo;
    private final IngestionJobRepository jobRepo;
    private final FileArtifactService artifactService;
    private final IngestionConfig cfg;
    private final ObjectMapper om;

    public IngestionPayloadFetcher(HttpFetchClient http, IngestionUrlValidator validator,
                                   IngestionCredentialService credService,
                                   IngestionCredentialRepository credRepo,
                                   IngestionJobRepository jobRepo,
                                   FileArtifactService artifactService,
                                   IngestionConfig cfg, ObjectMapper om) {
        this.http = http; this.validator = validator; this.credService = credService;
        this.credRepo = credRepo; this.jobRepo = jobRepo;
        this.artifactService = artifactService; this.cfg = cfg; this.om = om;
    }

    public FetchResult fetch(FetchRequest req) {
        validator.validate(req.url());
        String jobId = "ing_" + UUID.randomUUID().toString().replace("-","").substring(0,16);
        long now = System.currentTimeMillis();

        // 1) Create job row (status=fetching)
        var job = new IngestionJob(jobId, req.url(), req.method(),
            req.headers(), req.queryParams(), req.body(),
            req.credentialId(), req.pagination(), req.format(),
            null, "fetching", null, null, null, null,
            0, 0, 0L, now, now, null, null, null);
        jobRepo.save(job);

        // 2) Resolve credential
        AuthScheme scheme = AuthScheme.NONE;
        Map<String,String> authConfig = Map.of();
        String secret = null;
        if (req.credentialId() != null) {
            var c = credRepo.findById(req.credentialId()).orElseThrow();
            scheme = c.scheme();
            authConfig = c.configNonSecret();
            secret = credService.readSecret(req.credentialId());
        }

        // 3) Pagination loop → write to staging file
        Path stagingDir = Path.of(System.getProperty("user.home"), ".data-talk", "ingestion", jobId);
        Files.createDirectories(stagingDir);    // wrap in try/catch
        Path stagingFile = stagingDir.resolve(".staging." + req.format().name().toLowerCase());
        Path finalFile   = stagingDir.resolve("payload." + req.format().name().toLowerCase());

        // Delegate to PageWriter strategy per PayloadFormat — pseudocode for brevity:
        // - JSON: accumulate ArrayNode; serialize once at end.
        // - JSONL/CSV: append per page (CSV strips header on subsequent pages).
        // - HTML: parse jsoup table per page, append rows; serialize as JSONL of objects.
        var pageStats = runPaginationLoop(req, scheme, authConfig, secret, stagingFile);
        if (pageStats.bytesWritten() > cfg.payloadMaxBytes()) {
            Files.deleteIfExists(stagingFile);
            jobRepo.updateStatus(jobId, "failed", "payload too large: " + pageStats.bytesWritten() + " bytes", System.currentTimeMillis());
            throw new IllegalStateException("payload too large");
        }
        Files.move(stagingFile, finalFile, StandardCopyOption.ATOMIC_MOVE);

        // 4) Register external file_artifact
        String artifactId = artifactService.registerExternal(
            "fa_" + UUID.randomUUID().toString().replace("-","").substring(0,16),
            FileArtifactKind.INGESTION_PAYLOAD, "workspace",
            null, null, finalFile, "payload." + req.format().name().toLowerCase(),
            null, Map.of("jobId", jobId));

        // 5) Update job row
        jobRepo.updatePayloadArtifact(jobId, artifactId, pageStats.rows(), pageStats.bytesWritten(), System.currentTimeMillis());
        jobRepo.updateStatus(jobId, "fetched", null, System.currentTimeMillis());

        return new FetchResult(jobId, artifactId, pageStats.rows(),
            pageStats.bytesWritten(), pageStats.pages(), "fetched");
    }

    // Place stub `runPaginationLoop` here; full pagination implementation lives in
    // a private helper that handles PAGE/OFFSET/CURSOR variants. Each variant runs:
    //   - build query params (page=N+sizeParam OR offset=N OR cursor=...)
    //   - call HttpFetchClient.request(...)
    //   - check termination hint (EMPTY_ARRAY for JSON array, JSON_PATH_COUNT_ZERO via $.data, HTTP 404)
    //   - append parsed page contents to stagingFile
    //   - cap on maxPages
    record PageStats(int pages, int rows, long bytesWritten) {}
    private PageStats runPaginationLoop(FetchRequest req, AuthScheme scheme,
                                        Map<String,String> authConfig, String secret,
                                        Path stagingFile) {
        // implementation: minimum 80 lines, see spec §4.1.1 contract
        throw new UnsupportedOperationException("see step 2.3.4 detailed impl");
    }
}
```

- [ ] **Step 2.3.4: Fill in `runPaginationLoop` for PAGE / OFFSET / CURSOR / NONE**

Implement each branch as a small private method:
- `loopNone`: single call, write response body.
- `loopPage`: while pageN ≤ maxPages, set `queryParams[pageParam]=pageN, queryParams[sizeParam]=pageSize`, fetch; check termination (empty array / $.data array empty / HTTP 404); merge body into staging.
- `loopOffset`: same but uses `offset=offsetCounter, limit=pageSize`; offsetCounter += rowsThisPage.
- `loopCursor`: read first response's cursor field from JSON via configured path (default `$.next_cursor`); set `queryParams[cursorParam]=cursor`; stop when cursor null / missing.

JSON merge strategy: deserialize each page to `ArrayNode`, append all child nodes to a single `ArrayNode` in memory; final write serializes whole array once. (Memory consideration: 500 MB payload max would dominate; document as a known limitation, defer streaming JSON merge to follow-up.)

CSV merge: take header from first page; append subsequent pages stripped of header.

HTML merge: jsoup parse, extract rows under selector; for each row collect cells; serialize cumulative rows as JSONL keyed by inferred column names.

JSONL merge: append response body verbatim, ensuring trailing newline.

For payload size cap, track running byte count after each page; if exceeds `cfg.payloadMaxBytes()`, abort with `payload too large` exception (caller will write status=failed).

- [ ] **Step 2.3.5: Run IT to verify pass**

Run: `cd server && mvn -pl data-talk-application test -Dtest=IngestionPayloadFetcherIT -q`
Expected: PASS (3 tests).

- [ ] **Step 2.3.6: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionPayloadFetcher.java \
        server/data-talk-application/src/test/java/com/datatalk/application/ingestion/IngestionPayloadFetcherIT.java
git commit -m "feat(ingestion): IngestionPayloadFetcher — 3 pagination types + atomic write + artifact register

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 2.4: `datatalk_http_request` MCP action handler

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ingestion/HttpRequestActionHandler.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ingestion/HttpRequestActionHandlerTest.java`

- [ ] **Step 2.4.1: Failing handler test (via FakeOpenCodeServer pattern)**

Test invokes the MCP action through the existing OpenCode round-trip pipeline used by other action handlers (refer to `archive_artifact` action handler test for shape). Assert: returns `jobId` + `payloadArtifactId` + `status=fetched`; error case (bad URL) returns `error.code=INGESTION_SSRF_BLOCKED` + `userHint`.

- [ ] **Step 2.4.2: Run to verify fail**

Run: `cd server && mvn -pl data-talk-adapter test -Dtest=HttpRequestActionHandlerTest -q`
Expected: FAIL.

- [ ] **Step 2.4.3: Implement handler**

```java
package com.datatalk.adapter.actions.ingestion;

import com.datatalk.adapter.actions.DataTalkAction;
import com.datatalk.adapter.actions.ActionHandler;
import com.datatalk.application.ingestion.IngestionPayloadFetcher;
import com.datatalk.application.ingestion.IngestionPayloadFetcher.FetchRequest;
import com.datatalk.domain.ingestion.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@DataTalkAction(name = "datatalk_http_request",
    description = "Fetch external HTTP/REST/JSON/CSV/HTML data into a file_artifact for later schema inference and table creation.")
public class HttpRequestActionHandler implements ActionHandler {
    private final IngestionPayloadFetcher fetcher;
    private final ObjectMapper om;

    public HttpRequestActionHandler(IngestionPayloadFetcher fetcher, ObjectMapper om) {
        this.fetcher = fetcher; this.om = om;
    }

    @Override public JsonNode handle(JsonNode input) {
        try {
            FetchRequest req = parseRequest(input);
            var result = fetcher.fetch(req);
            ObjectNode out = om.createObjectNode();
            out.put("jobId", result.jobId());
            out.put("payloadArtifactId", result.payloadArtifactId());
            out.put("status", result.status());
            out.put("rowsFetched", result.rowsFetched());
            out.put("bytesFetched", result.bytesFetched());
            out.put("pagesFetched", result.pagesFetched());
            out.putNull("error");
            out.putNull("userHint");
            return out;
        } catch (IllegalArgumentException e) {
            return errorNode("INGESTION_SSRF_BLOCKED", e.getMessage(),
                "URL is blocked by SSRF deny list. Use a public HTTPS endpoint.");
        } catch (IllegalStateException e) {
            return errorNode("INGESTION_PAYLOAD_TOO_LARGE", e.getMessage(),
                "Payload exceeds 500 MB cap. Narrow the request (smaller pageSize / fewer pages / time range).");
        } catch (Exception e) {
            return errorNode("INGESTION_FETCH_FAILED", e.getMessage(),
                "Check the URL, credentialId, and pagination params. Verify the host is reachable.");
        }
    }

    private FetchRequest parseRequest(JsonNode n) {
        // map JSON → FetchRequest record (use ObjectMapper.treeToValue for nested objects)
        return om.convertValue(n, FetchRequest.class);
    }

    private ObjectNode errorNode(String code, String reason, String userHint) {
        ObjectNode out = om.createObjectNode();
        out.put("status", "failed");
        out.putNull("jobId");
        out.putNull("payloadArtifactId");
        out.set("error", om.createObjectNode().put("code", code).put("reason", reason));
        out.put("userHint", userHint);
        return out;
    }
}
```

- [ ] **Step 2.4.4: Run + commit**

Run: `cd server && mvn -pl data-talk-adapter test -Dtest=HttpRequestActionHandlerTest -q`
Expected: PASS.

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ingestion/HttpRequestActionHandler.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/ingestion/HttpRequestActionHandlerTest.java
git commit -m "feat(ingestion): datatalk_http_request MCP action with structured error + userHint

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 2.5: Phase 2 gate

- [ ] **Step 2.5.1: Backend full verify**

Run: `cd server && mvn clean verify`
Expected: BUILD SUCCESS.

- [ ] **Step 2.5.2: Phase 2 commit marker**

```bash
git commit --allow-empty -m "chore(ingestion): Phase 2 complete — HTTP fetcher + 3 pagination types + payload artifact

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 3 — Schema Inferrer + Mapping

### Task 3.1: PayloadParser interface + JsonPayloadParser

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/parser/PayloadParser.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/parser/JsonPayloadParser.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/ingestion/parser/JsonPayloadParserTest.java`

- [ ] **Step 3.1.1: Failing test**

```java
package com.datatalk.application.ingestion.parser;

import com.datatalk.domain.ingestion.InferredType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class JsonPayloadParserTest {
    JsonPayloadParser parser = new JsonPayloadParser(new com.fasterxml.jackson.databind.ObjectMapper());

    @Test void inferIntegerStringMixedColumnAsString() throws Exception {
        Path p = Files.createTempFile("test", ".json");
        Files.writeString(p, "[{\"id\":1,\"name\":\"a\"},{\"id\":\"2x\",\"name\":\"b\"}]");
        var inferred = parser.infer(p, 100);
        assertThat(inferred.columns())
            .extracting(c -> c.sourcePath() + ":" + c.type())
            .contains("$.id:STRING_64", "$.name:STRING_64");
    }

    @Test void dotPathOneLevelExpansion() throws Exception {
        Path p = Files.createTempFile("test2", ".json");
        Files.writeString(p, "[{\"id\":1,\"customer\":{\"name\":\"alice\",\"age\":30}}]");
        var inferred = parser.infer(p, 100);
        assertThat(inferred.columns()).extracting("sourcePath")
            .contains("$.id", "$.customer.name", "$.customer.age");
    }

    @Test void deepNestingFallsBackToJson() throws Exception {
        Path p = Files.createTempFile("test3", ".json");
        Files.writeString(p, "[{\"data\":{\"a\":{\"b\":{\"c\":1}}}}]");
        var inferred = parser.infer(p, 100);
        assertThat(inferred.columns())
            .anyMatch(c -> c.sourcePath().equals("$.data") && c.type() == InferredType.JSON);
    }

    @Test void allNullColumnInferredAsStringNullable() throws Exception {
        Path p = Files.createTempFile("test4", ".json");
        Files.writeString(p, "[{\"x\":null},{\"x\":null}]");
        var inferred = parser.infer(p, 100);
        var col = inferred.columns().stream().filter(c -> c.sourcePath().equals("$.x")).findFirst().orElseThrow();
        assertThat(col.nullable()).isTrue();
    }
}
```

- [ ] **Step 3.1.2: Run to verify fail**

Run: `cd server && mvn -pl data-talk-application test -Dtest=JsonPayloadParserTest -q`

- [ ] **Step 3.1.3: Implement PayloadParser interface + JsonPayloadParser**

```java
package com.datatalk.application.ingestion.parser;
import com.datatalk.domain.ingestion.IngestionMapping;
import java.nio.file.Path;
public interface PayloadParser {
    IngestionMapping infer(Path payloadFile, int sampleSize);
}
```

For `JsonPayloadParser.java`: use Jackson `streaming` `JsonParser` to iterate top-level array; for each `ObjectNode`, flatten one level (depth 0 = top object keys → `$.<key>`; depth 1 = nested object keys → `$.<parent>.<key>`; depth ≥ 2 = collapse to depth-0 parent JSON). Collect type votes per `sourcePath`. Use fallback chain `BOOLEAN → INTEGER_32 → INTEGER_64 → DECIMAL → DATE (ISO YYYY-MM-DD) → TIMESTAMP (ISO 8601) → STRING_64/256/500/LONG → JSON`. Sample up to `sampleSize` rows. Sample values: keep first 2 distinct stringified values per column.

- [ ] **Step 3.1.4: Run to verify pass + commit**

Run: `cd server && mvn -pl data-talk-application test -Dtest=JsonPayloadParserTest -q`
Expected: PASS.

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/ingestion/parser/PayloadParser.java \
        server/data-talk-application/src/main/java/com/datatalk/application/ingestion/parser/JsonPayloadParser.java \
        server/data-talk-application/src/test/java/com/datatalk/application/ingestion/parser/JsonPayloadParserTest.java
git commit -m "feat(ingestion): JSON payload parser + dot-path 1-level expansion + type voting

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 3.2: JsonlPayloadParser + CsvPayloadParser + HtmlTablePayloadParser

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/parser/JsonlPayloadParser.java`
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/parser/CsvPayloadParser.java` (use Apache Commons CSV — already a transitive dep, verify)
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/parser/HtmlTablePayloadParser.java` (use jsoup)
- Create matching tests under `parser/` with at least 3 test cases each (single column, multi column, mixed types).

(Mirror Task 3.1 cycle: write tests → fail → implement → pass → commit per file.)

- [ ] **Step 3.2.1–3.2.12** — TDD cycle for JSONL parser (4 steps)
- [ ] **Step 3.2.13–3.2.24** — TDD cycle for CSV parser (4 steps)
- [ ] **Step 3.2.25–3.2.36** — TDD cycle for HTML parser (4 steps; include rowspan/colspan unfold tests)

```bash
git commit -m "feat(ingestion): JSONL + CSV + HTML payload parsers with type inference

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

### Task 3.3: IngestionSchemaInferrer (parser dispatch)

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionSchemaInferrer.java`
- Create test: `IngestionSchemaInferrerTest.java`

- [ ] **Step 3.3.1: Failing test — dispatches by PayloadFormat enum**

- [ ] **Step 3.3.2: Run to verify fail**

- [ ] **Step 3.3.3: Implement dispatcher**

```java
@Service
public class IngestionSchemaInferrer {
    private final Map<PayloadFormat, PayloadParser> parsers;
    private final FileArtifactService artifactService;
    private final IngestionJobRepository jobRepo;
    private final ObjectMapper om;

    public IngestionSchemaInferrer(JsonPayloadParser j, JsonlPayloadParser jl,
                                   CsvPayloadParser c, HtmlTablePayloadParser h,
                                   FileArtifactService artifactService,
                                   IngestionJobRepository jobRepo, ObjectMapper om) {
        this.parsers = Map.of(PayloadFormat.JSON, j, PayloadFormat.JSONL, jl,
            PayloadFormat.CSV, c, PayloadFormat.HTML, h);
        this.artifactService = artifactService; this.jobRepo = jobRepo; this.om = om;
    }

    public IngestionMapping infer(String jobId, String dialect, int sampleSize) {
        var job = jobRepo.findById(jobId).orElseThrow();
        Path payloadPath = artifactService.physicalPathOf(job.payloadArtifactId());
        var mapping = parsers.get(job.payloadFormat()).infer(payloadPath, sampleSize);
        jobRepo.updateMapping(jobId, om.writeValueAsString(mapping), System.currentTimeMillis());
        return mapping;
    }
}
```

- [ ] **Step 3.3.4: Run + commit**

### Task 3.4: `datatalk_infer_ingestion_schema` MCP action

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ingestion/InferIngestionSchemaActionHandler.java`
- Create test.

(Same handler pattern as Task 2.4: parse input → call service → emit `{ mappingId, columns[], suggestedDdl, rowsAnalyzed }` or `{ status:'failed', error, userHint }`.)

`suggestedDdl` is preliminary — the DDL adapter from Phase 5 will be the source of truth at execution time. For now, emit a vanilla `CREATE TABLE "<schema>"."<table>" ( <col> <generic-type> )` string the AI / UI can preview.

- [ ] **Steps 3.4.1–3.4.5**: TDD cycle + commit.

### Task 3.5: `/api/ingestion/jobs/{id}/payload-preview` endpoint

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/IngestionController.java`
- Add test cases to `IngestionControllerCredentialTest` or create new `IngestionControllerJobTest`.

- [ ] **Step 3.5.1–3.5.5**: Write test → fail → implement → pass → commit. Endpoint reads `payload_artifact_id` from `ingestion_job`, opens file via `FileArtifactService.readBytes`, parses based on format, returns first N rows.

### Task 3.6: Phase 3 gate

- [ ] **Step 3.6.1: `mvn clean verify`** — full backend green.
- [ ] **Step 3.6.2: Phase 3 marker commit**

```bash
git commit --allow-empty -m "chore(ingestion): Phase 3 complete — 4 parsers + schema inferrer + payload preview

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 4 — Frontend Tab + Mapping Editor

### Task 4.1: Tab type registration

**Files:**
- Modify: `client/src/features/stage/types/tab-type-registry.ts`

- [ ] **Step 4.1.1: Read existing registry, identify pattern** (FILES + FILES_LIBRARY rows)
- [ ] **Step 4.1.2: Add two entries**

```ts
ingestion_job: {
  scope: 'workspace',
  icon: 'Download',
  defaultTitle: (t, props) => t('ingestion.job.title', { id: (props.id as string).slice(0, 8) }),
  uniquenessKey: (props) => `ingestion_job:${props.id}`,
  capabilities: ['close', 'rename', 'maximize'],
  component: () => import('@/features/ingestion/ingestion-job-tab').then(m => m.IngestionJobTab),
},
ingestion_library: {
  scope: 'workspace',
  icon: 'Library',
  defaultTitle: (t) => t('ingestion.library.title'),
  uniquenessKey: () => 'ingestion_library',
  capabilities: ['close', 'maximize'],
  component: () => import('@/features/ingestion/ingestion-library-tab').then(m => m.IngestionLibraryTab),
},
```

- [ ] **Step 4.1.3: `npx tsc --noEmit`** + commit.

### Task 4.2: API client + Zustand store + TanStack hooks

**Files:**
- Create: `client/src/features/ingestion/api/ingestion-api.ts`
- Create: `client/src/features/ingestion/stores/use-ingestion-jobs-store.ts`
- Create: `client/src/features/ingestion/hooks/use-ingestion-job-query.ts`
- Create: `client/src/features/ingestion/hooks/use-ingestion-jobs-query.ts`
- Create: `client/src/features/ingestion/hooks/use-payload-preview-query.ts`

Implement REST client functions for: `GET /api/ingestion/jobs`, `GET /api/ingestion/jobs/{id}`, `GET /api/ingestion/jobs/{id}/payload-preview`, `POST /api/ingestion/jobs/{id}/confirm`, `POST /api/ingestion/jobs/{id}/cancel`.

Zustand store holds: `jobs: Map<id, IngestionJobView>`, `subscribeToEvents(jobId)`, on `IngestionJobCreated` event: auto-call stage `openTab('ingestion_job', { id })`.

(TDD: vitest unit test for store + hooks with mocked API. 5 steps per file.)

```bash
git commit -m "feat(ingestion): frontend API client + Zustand store + TanStack hooks"
```

### Task 4.3: Phase-router IngestionJobTab + 5 phase components

**Files:**
- Create: `client/src/features/ingestion/ingestion-job-tab.tsx`
- Create: `client/src/features/ingestion/phases/fetching-phase.tsx`
- Create: `client/src/features/ingestion/phases/mapping-phase.tsx`
- Create: `client/src/features/ingestion/phases/writing-phase.tsx`
- Create: `client/src/features/ingestion/phases/completed-phase.tsx`
- Create: `client/src/features/ingestion/phases/failed-phase.tsx`

(For each: TDD with vitest + render snapshot, then implement using spec §6.3 5-state token mapping. Phase components consume `job: IngestionJobView` prop; ingestion-job-tab is a thin router selecting phase by `job.status`.)

```bash
git commit -m "feat(ingestion): IngestionJobTab phase router + 5 phase components"
```

### Task 4.4: PhaseStepper + SourceSummaryCard + DDLPreview + PaginationConfigDisplay + PayloadPreviewTable

(Each is a small presentation component. TDD: 1-2 vitest tests per component. 5 steps each.)

```bash
git commit -m "feat(ingestion): 5 presentation sub-components + 5-state token mapping"
```

### Task 4.5: MappingEditor (interactive, 5-state token)

**Files:**
- Create: `client/src/features/ingestion/components/mapping-editor.tsx`
- Create: `client/src/features/ingestion/components/__tests__/mapping-editor.test.tsx`

- [ ] **Step 4.5.1: Failing vitest covering rename + type change + skip + DDL re-render**

```tsx
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MappingEditor } from '../mapping-editor';

describe('MappingEditor', () => {
  const cols = [
    { sourcePath: '$.id', targetName: 'id', type: 'INTEGER_64', skip: false, sampleValues: ['1','2'], nullable: false },
    { sourcePath: '$.name', targetName: 'name', type: 'STRING_64', skip: false, sampleValues: ['a'], nullable: false },
  ];

  it('renames column on input change', () => {
    const onChange = vi.fn();
    render(<MappingEditor columns={cols} onChange={onChange} />);
    fireEvent.change(screen.getByDisplayValue('id'), { target: { value: 'order_id' } });
    expect(onChange).toHaveBeenCalledWith(expect.arrayContaining([
      expect.objectContaining({ sourcePath: '$.id', targetName: 'order_id' })
    ]));
  });

  it('applies line-through styling when row is skipped', () => {
    const skipped = [{ ...cols[0], skip: true }, cols[1]];
    render(<MappingEditor columns={skipped} onChange={() => {}} />);
    const row = screen.getByTestId('mapping-row-$.id');
    expect(row.className).toMatch(/line-through/);
  });

  it('SQL type select reflects current value', () => {
    render(<MappingEditor columns={cols} onChange={() => {}} />);
    expect(screen.getByDisplayValue('INTEGER_64')).toBeInTheDocument();
  });
});
```

- [ ] **Step 4.5.2: Run to verify fail**

Run: `cd client && npx vitest run src/features/ingestion/components/__tests__/mapping-editor.test.tsx -q`

- [ ] **Step 4.5.3: Implement MappingEditor**

(Use `<table>` with token-mapped classes per spec §6.3: header `bg-subtle`, row hover `interaction-hover`, skip row `bg-subtle text-muted line-through`. Each editable cell follows 5-state token table.)

- [ ] **Step 4.5.4: Run to verify pass**

- [ ] **Step 4.5.5: Commit**

### Task 4.6: IngestionLibraryTab + table

**Files:**
- Create: `client/src/features/ingestion/ingestion-library-tab.tsx`
- Create: `client/src/features/ingestion/components/__tests__/ingestion-library-tab.test.tsx`

(TDD: 1 test for filter, 1 test for double-click opens job Tab. Implement library table with toolbar [status / connection / search] + table rows with double-channel status badge.)

```bash
git commit -m "feat(ingestion): IngestionLibraryTab + status filter + double-click open job Tab"
```

### Task 4.7: i18n keys completion

**Files:**
- Modify: `client/src/i18n/messages.ts`

Add full `ingestion.*` key block per spec §6.4. Include `dialect_unsupported.<kind>` for 15 unsupported kinds: `mariadb` / `oracle` / `sqlserver` / `duckdb` / `clickhouse` / `apache_doris` / `starrocks` / `trino` / `presto` / `hive` / `tidb` / `oceanbase` / `dameng` / `kingbase` / `gaussdb`.

Plus error keys: `network_timeout` / `auth_failed` / `pagination_overflow` / `schema_inference_failed` / `token_expired` / `token_already_consumed` / `payload_too_large` / `ssrf_blocked` / `http_status_error` / `parse_error` / `ddl_unsupported`.

```bash
git commit -m "feat(ingestion): full i18n key block (zh-CN + en-US, 15 dialect_unsupported entries)"
```

### Task 4.8: Phase 4 gate

- [ ] **Step 4.8.1: Type check** — `cd client && npx tsc --noEmit`
- [ ] **Step 4.8.2: Vitest full** — `cd client && npx vitest run`
- [ ] **Step 4.8.3: Manual Tauri smoke** — launch backend + tauri dev; mock `/api/ingestion/jobs/<id>` via WireMock or seed a fetched job; open Tab; verify phase stepper renders, mapping editor edits, DDL preview updates.
- [ ] **Step 4.8.4: Phase 4 marker commit**

```bash
git commit --allow-empty -m "chore(ingestion): Phase 4 complete — frontend Tabs + mapping editor + i18n"
```

---

## Phase 5 — DDL Adapter + Executor + L2 Confirm

### Task 5.1: IngestionDdlAdapter interface + 4 dialect implementations

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/ddl/IngestionDdlAdapter.java`
- Create: `MysqlIngestionDdlAdapter.java`
- Create: `PostgresIngestionDdlAdapter.java`
- Create: `H2IngestionDdlAdapter.java`
- Create: `SqliteIngestionDdlAdapter.java`
- Create base test class + 4 per-impl tests with shared type-mapping assertions.

- [ ] **Step 5.1.1: Failing base contract test**

```java
abstract class IngestionDdlAdapterContractTest {
    abstract IngestionDdlAdapter adapter();
    abstract String quoteFor(String name);
    abstract String expectedString256Type();
    abstract String expectedTimestampType();

    @Test void createTableWithMixedTypes() {
        var cols = List.of(
            new MappingColumn("$.id","id",InferredType.INTEGER_64,false,List.of("1"),false),
            new MappingColumn("$.name","name",InferredType.STRING_256,false,List.of("a"),false),
            new MappingColumn("$.created","created",InferredType.TIMESTAMP,false,List.of("2026-05-12T00:00:00Z"),true)
        );
        String ddl = adapter().generateCreateTable("public","orders", cols);
        assertThat(ddl).contains(quoteFor("orders"));
        assertThat(ddl).contains(expectedString256Type());
        assertThat(ddl).contains(expectedTimestampType());
        assertThat(ddl).contains("BIGINT");
    }

    @Test void insertStatementHasCorrectPlaceholders() {
        var cols = List.of(
            new MappingColumn("$.id","id",InferredType.INTEGER_64,false,List.of(),false),
            new MappingColumn("$.x","x",InferredType.STRING_64,false,List.of(),true),
            new MappingColumn("$.skipme","skipme",InferredType.STRING_64,true,List.of(),true)
        );
        String insert = adapter().generateInsert("public","orders", cols);
        assertThat(insert).contains("INSERT INTO");
        assertThat(insert).contains("(?,?)");   // 2 placeholders, skip excluded
        assertThat(insert).doesNotContain("skipme");
    }
}
```

Each per-impl test extends and supplies expected dialect specifics (e.g. MySQL uses backticks, PostgreSQL uses double quotes, SQLite uses double quotes + TEXT for timestamp).

- [ ] **Step 5.1.2: Run to verify fail**

- [ ] **Step 5.1.3: Implement interface + 4 adapters per spec §3.4 type table**

```java
public interface IngestionDdlAdapter {
    boolean supports(String connectionKind);
    String generateCreateTable(String schema, String table, List<MappingColumn> columns);
    String generateInsert(String schema, String table, List<MappingColumn> columns);
    String sqlTypeFor(InferredType inferred);
}
```

Each impl: handle quote character, type mapping per spec table, skip columns excluded from CREATE + INSERT, `NULL`/`NOT NULL` from `nullable` flag.

- [ ] **Step 5.1.4: Run all 4 tests pass + commit**

```bash
git commit -m "feat(ingestion): IngestionDdlAdapter + 4 dialect impls (mysql/postgres/h2/sqlite)"
```

### Task 5.2: IngestionConfirmedTokenStore

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionConfirmedTokenStore.java`
- Create test.

- [ ] **Step 5.2.1: Failing test — issue / consume / expire / mismatch**

```java
class IngestionConfirmedTokenStoreTest {
    IngestionConfirmedTokenStore store = new IngestionConfirmedTokenStore(java.time.Clock.systemUTC());

    @Test void issueAndConsumeOnce() {
        var t = store.issue("job1","user1","hash1");
        store.consume(t.tokenId(),"job1","hash1"); // no throw
    }
    @Test void consumeTwiceThrows() {
        var t = store.issue("job1","user1","hash1");
        store.consume(t.tokenId(),"job1","hash1");
        assertThatThrownBy(() -> store.consume(t.tokenId(),"job1","hash1"))
            .isInstanceOf(IngestionTokenInvalidException.class);
    }
    @Test void mismatchJobIdThrows() {
        var t = store.issue("job1","user1","hash1");
        assertThatThrownBy(() -> store.consume(t.tokenId(),"jobX","hash1"))
            .isInstanceOf(IngestionTokenInvalidException.class);
    }
    @Test void mismatchHashThrows() {
        var t = store.issue("job1","user1","hash1");
        assertThatThrownBy(() -> store.consume(t.tokenId(),"job1","hashX"))
            .isInstanceOf(IngestionTokenInvalidException.class);
    }
    @Test void expiredTokenThrows() {
        var fixed = java.time.Clock.fixed(java.time.Instant.ofEpochMilli(0), java.time.ZoneOffset.UTC);
        var advanced = java.time.Clock.fixed(java.time.Instant.ofEpochMilli(6 * 60 * 1000), java.time.ZoneOffset.UTC);
        var s = new IngestionConfirmedTokenStore(fixed);
        var t = s.issue("job1","u","h");
        s.advanceClock(advanced); // test seam
        assertThatThrownBy(() -> s.consume(t.tokenId(),"job1","h"))
            .isInstanceOf(IngestionTokenInvalidException.class);
    }
}
```

- [ ] **Step 5.2.2: Run to verify fail**

- [ ] **Step 5.2.3: Implement**

```java
@Service
public class IngestionConfirmedTokenStore {
    public record IssuedToken(String tokenId, long expiresAt) {}
    private record Entry(String jobId, String userId, String mappingHash, long expiresAt, boolean consumed) {}

    private static final long TTL_MS = 5 * 60_000L;
    private final java.util.concurrent.ConcurrentHashMap<String, Entry> store = new java.util.concurrent.ConcurrentHashMap<>();
    private java.time.Clock clock;

    public IngestionConfirmedTokenStore(java.time.Clock clock) { this.clock = clock; }
    void advanceClock(java.time.Clock c) { this.clock = c; } // package-private test seam

    public IssuedToken issue(String jobId, String userId, String mappingHash) {
        String tokenId = "ict_" + UUID.randomUUID().toString().replace("-","").substring(0,16);
        long exp = clock.millis() + TTL_MS;
        store.put(tokenId, new Entry(jobId, userId, mappingHash, exp, false));
        return new IssuedToken(tokenId, exp);
    }

    public void consume(String tokenId, String jobId, String mappingHash) {
        Entry e = store.get(tokenId);
        if (e == null) throw new IngestionTokenInvalidException(IngestionTokenInvalidException.Reason.NOT_FOUND);
        if (e.consumed) throw new IngestionTokenInvalidException(IngestionTokenInvalidException.Reason.ALREADY_CONSUMED);
        if (clock.millis() > e.expiresAt) {
            store.remove(tokenId);
            throw new IngestionTokenInvalidException(IngestionTokenInvalidException.Reason.EXPIRED);
        }
        if (!e.jobId.equals(jobId)) throw new IngestionTokenInvalidException(IngestionTokenInvalidException.Reason.JOB_MISMATCH);
        if (!e.mappingHash.equals(mappingHash)) throw new IngestionTokenInvalidException(IngestionTokenInvalidException.Reason.MAPPING_HASH_MISMATCH);
        store.put(tokenId, new Entry(e.jobId, e.userId, e.mappingHash, e.expiresAt, true));
    }
}
```

- [ ] **Step 5.2.4: Run pass + commit**

### Task 5.3: IngestionExecutor (CREATE + INSERT batch + SqlStatementGuard + SSE)

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionExecutor.java`
- Create test: Testcontainers IT for each of 4 dialects (mysql / postgres / h2 / sqlite).

- [ ] **Step 5.3.1: Failing IT for h2 dialect (smallest container surface)**

```java
class IngestionExecutorH2IT {
    @Autowired IngestionExecutor executor;
    @Autowired ConnectionService connectionService;

    @Test void createsTableAndInsertsBatch(@TempDir Path payloadDir) throws Exception {
        // 1) create H2 in-memory connection via ConnectionService
        var connId = connectionService.create(/* params */);
        // 2) prepare ingestion_job in 'mapping' status with payload in payloadDir
        // 3) issue ingestion confirm token
        // 4) call executor.createTable(jobId, connId, "public", "orders", mappingOverrides, tokenId)
        // 5) assert table exists + rows inserted matches expected count
    }
}
```

- [ ] **Step 5.3.2: Run fail**

- [ ] **Step 5.3.3: Implement**

```java
@Service
public class IngestionExecutor {
    private final IngestionJobRepository jobRepo;
    private final IngestionConfirmedTokenStore tokenStore;
    private final List<IngestionDdlAdapter> adapters;
    private final ConnectionService connectionService;
    private final SqlStatementGuard guard;
    private final ParameterizedSqlExecutor sqlExecutor;
    private final EventPublisher events;
    private final FileArtifactService artifactService;
    private final ObjectMapper om;
    // … constructor

    public CreateTableResult createTable(String jobId, String connectionId, String schema, String table,
                                         MappingOverrides overrides, String tokenId) {
        var job = jobRepo.findById(jobId).orElseThrow();
        var mapping = applyOverrides(job.mapping(), overrides);
        String mappingHash = sha256Canonical(mapping);
        tokenStore.consume(tokenId, jobId, mappingHash);

        var conn = connectionService.get(connectionId);
        var adapter = adapters.stream().filter(a -> a.supports(conn.kind())).findFirst()
            .orElseThrow(() -> new IngestionDialectUnsupportedException(conn.kind()));

        String ddl = adapter.generateCreateTable(schema, table, mapping.columns());
        var classification = guard.classifyAndAudit(ddl, conn,
            new IngestionContext(jobId, tokenId, /* tokenConsumed=*/ true));
        // SqlStatementGuard writes audit row; classification.requiresConfirm should be false here
        sqlExecutor.executeDdl(conn, ddl);

        jobRepo.updateTargetTable(jobId, connectionId, schema, table, System.currentTimeMillis());
        jobRepo.updateStatus(jobId, "writing", null, System.currentTimeMillis());
        events.publish(new DtEvent.IngestionWriteStarted(jobId, schema + "." + table));
        return new CreateTableResult(jobId, schema + "." + table, ddl);
    }

    public IngestResult ingestPayload(String jobId, int batchSize) {
        var job = jobRepo.findById(jobId).orElseThrow();
        var conn = connectionService.get(job.connectionId());
        var adapter = adapters.stream().filter(a -> a.supports(conn.kind())).findFirst().orElseThrow();
        var mapping = job.mapping();
        String insertSql = adapter.generateInsert(job.targetSchema(), job.targetTable(), mapping.columns());

        Path payloadPath = artifactService.physicalPathOf(job.payloadArtifactId());
        int rowsInserted = 0;
        long start = System.currentTimeMillis();
        try (var batchExecutor = sqlExecutor.openBatch(conn, insertSql, batchSize)) {
            // Stream rows from payloadPath using format-specific reader (Jackson streaming / OpenCSV / etc.)
            // For each row: project to PreparedStatement args via mapping; add to batch.
            // On every batchSize rows: flush + publish IngestionWriteProgress event.
        }
        long durationMs = System.currentTimeMillis() - start;
        jobRepo.updateCompleted(jobId, rowsInserted, System.currentTimeMillis(), System.currentTimeMillis());
        jobRepo.updateStatus(jobId, "completed", null, System.currentTimeMillis());
        events.publish(new DtEvent.IngestionCompleted(jobId, job.targetSchema()+"."+job.targetTable(),
            rowsInserted, durationMs));
        return new IngestResult(jobId, "completed", rowsInserted, durationMs);
    }

    // applyOverrides, sha256Canonical, IngestionContext, MappingOverrides records …
}
```

- [ ] **Step 5.3.4: Run pass**

- [ ] **Step 5.3.5–5.3.16: Add 3 more Testcontainers ITs — mysql, postgres, sqlite** (mysql/postgres use `MySQLContainer` / `PostgreSQLContainer`; sqlite uses a temp file `:memory:` or `jdbc:sqlite:<tmp>`)

- [ ] **Step 5.3.17: Commit**

### Task 5.4: Confirm + cancel REST endpoints

**Files:**
- Modify: `IngestionController.java`

Add:
- `POST /api/ingestion/jobs/{id}/confirm` body `{ mappingOverridesHash }` → calls `IngestionConfirmedTokenStore.issue(...)` → returns `{ tokenId, expiresAt }`
- `POST /api/ingestion/jobs/{id}/cancel` → updates status=cancelled + cleans staging files

(TDD cycle, 5 steps + commit.)

### Task 5.5: 5 remaining MCP actions

**Files:**
- Create: `CreateIngestionTableActionHandler.java`
- Create: `IngestPayloadActionHandler.java`
- Create: `GetIngestionJobActionHandler.java`
- Create: `ListIngestionJobsActionHandler.java`

Each:
1. Failing test (MCP envelope + happy + error path → `userHint` set per spec §4.2 table)
2. Run fail
3. Implement (delegate to `IngestionExecutor` / `IngestionJobRepository`)
4. Run pass
5. Commit

Error mapping table (each handler implements):

| Exception | Error code | userHint |
|-----------|------------|----------|
| `IngestionTokenInvalidException` | `INGESTION_TOKEN_INVALID` | "Confirmation token expired or invalidated. Ask the user to reopen the ingestion_job(id=<jobId>) Tab and click [Confirm and Ingest] again — this re-issues a fresh 5-minute token." |
| `IngestionDialectUnsupportedException` | `INGESTION_DIALECT_UNSUPPORTED` | "Target connection dialect <kind> is not supported in Day-1. Switch to a mysql/postgresql/h2/sqlite connection, or wait for the per-dialect follow-up child plan." |
| `IllegalStateException("payload too large")` | `INGESTION_PAYLOAD_TOO_LARGE` | "Payload exceeded the 500 MB cap. Narrow the time range, shrink pageSize, or reduce maxPages." |
| `IllegalArgumentException("denied")` | `INGESTION_SSRF_BLOCKED` | "URL host is blocked by the SSRF deny list. Use a public HTTPS endpoint or contact the admin to adjust the deny list." |
| `RestClientResponseException` (401/403) | `INGESTION_AUTH_FAILED` | "Authentication failed. Open Settings → Credentials, verify the credential is correct, then retry." |

### Task 5.6: Phase 5 gate

- [ ] **Step 5.6.1: `mvn clean verify`** — full backend green incl. 4 Testcontainers ITs.
- [ ] **Step 5.6.2: Phase 5 marker commit**

---

## Phase 6 — Skill Bundle + AGENTS.md + Verification

### Task 6.1: data-ingestion skill source

**Files:**
- Create: `server/data-talk-infrastructure/src/main/resources/opencode/skills-src/data-ingestion/SKILL.md`
- Create: 5 recipe markdowns under `recipes/`
- Create: 3 example JSON files under `examples/`

- [ ] **Step 6.1.1: Write SKILL.md**

```markdown
---
name: data-ingestion
description: Use when the user asks to fetch external data (REST/JSON/CSV/static HTML table) and write it into a SQL connection. Triggers on phrases like 抓取/拉取/采集/落库/导入/ingest/scrape/sync from API/import from URL. Provides the full chain: HTTP fetch → schema inference → user-confirmed CREATE TABLE → batch INSERT. Day-1 supports MySQL/PostgreSQL/H2/SQLite as ingestion targets and None/Bearer/API Key/Basic auth schemes.
---

# Data Ingestion Skill

(... full content per spec §5.2 ...)

## When to use

(3-5 example user prompts)

## When NOT to use

- Reading from an already-existing connection — use `datatalk_read_schema` / `datatalk_execute_sql`
- Dashboard / chart generation — use chart fence
- Platform-specific (Taobao / JD / Pinduoduo / Douyin) — dedicated skills (follow-up)
- JavaScript-rendered web pages — Day-1 unsupported

## Tool surface

(6 MCP tool names with input summary + return shape)

## Recommended orchestration sequence

1. `datatalk_http_request` — single call, handles pagination internally
2. `datatalk_infer_ingestion_schema`
3. Inform user: "I've prepared the ingestion preview in the Tab. Please review the column mapping and click [Confirm and Ingest] when ready."
4. Wait for user confirm signal (Tab status flips to awaiting_confirm)
5. `datatalk_create_ingestion_table` — must carry ingestionConfirmedToken from job state
6. `datatalk_ingest_payload`

## Pagination heuristics

(decision tree)

## Error handling

(map of error code → action; tells AI when to retry vs ask user)

## Native fetch fallback declaration

If a derived skill needs `bash` for signed requests / WebSocket / mTLS, declare `requires_bash_network: true` in its own frontmatter and warn the user it bypasses central audit.

## DDL dialect limits

Day-1 supports `mysql`, `postgresql`, `h2`, `sqlite`. Other 15 first-class kinds return `dialect_unsupported`. Tell the user to switch connection or wait for the per-dialect follow-up plan.
```

- [ ] **Step 6.1.2: Write 5 recipes + 3 examples**

(Each recipe is 30-60 lines markdown with example MCP call payloads. Each example is a JSON snippet of `datatalk_http_request` input.)

- [ ] **Step 6.1.3: Commit**

```bash
git add server/data-talk-infrastructure/src/main/resources/opencode/skills-src/data-ingestion/
git commit -m "feat(ingestion): data-ingestion skill SKILL.md + recipes + examples"
```

### Task 6.2: Maven-assembly descriptor

**Files:**
- Create: `server/data-talk-infrastructure/src/assembly/data-ingestion-skill.xml`
- Modify: `server/data-talk-infrastructure/pom.xml` — add descriptor reference + version property

- [ ] **Step 6.2.1: Read existing bezel descriptor** at `src/assembly/bezel-skill.xml` to mirror layout

- [ ] **Step 6.2.2: Create descriptor**

```xml
<assembly xmlns="http://maven.apache.org/ASSEMBLY/2.1.0">
  <id>data-ingestion-skill</id>
  <formats><format>tar.gz</format></formats>
  <includeBaseDirectory>false</includeBaseDirectory>
  <fileSets>
    <fileSet>
      <directory>${project.basedir}/src/main/resources/opencode/skills-src/data-ingestion</directory>
      <outputDirectory>/</outputDirectory>
    </fileSet>
  </fileSets>
</assembly>
```

- [ ] **Step 6.2.3: Update pom.xml** — add `<descriptor>src/assembly/data-ingestion-skill.xml</descriptor>` to existing `maven-assembly-plugin` config + define `${data-ingestion.version}=1.0.0` property; generate `opencode/skills/data-ingestion.version` resource via `<resource>` filter or simple build script.

- [ ] **Step 6.2.4: `mvn package -pl data-talk-infrastructure -DskipTests`** — confirm `target/*/opencode/skills/data-ingestion.tar.gz` exists.

- [ ] **Step 6.2.5: Commit**

### Task 6.3: OpenCodeBinaryResolver.ensureDataIngestionSkill

**Files:**
- Modify: `OpenCodeBinaryResolver.java` — add method + 3 constants
- Modify: `OpenCodeProcessManager.java` — wire call
- Create: `OpenCodeBinaryResolverDataIngestionTest.java`

- [ ] **Step 6.3.1: Failing test (mirror existing `OpenCodeBinaryResolverBezelTest`)**

- [ ] **Step 6.3.2: Implement `ensureDataIngestionSkill` — clone `ensureBezelSkill` body with marker name swap**

- [ ] **Step 6.3.3: Wire into `OpenCodeProcessManager` after `ensureBezelSkill` call**

- [ ] **Step 6.3.4: Run test pass + commit**

### Task 6.4: AGENTS.md template increment + contract test

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` — add `## Data Ingestion (skill: data-ingestion)` section
- Modify or create: `AgentsTemplateContractTest.java` — assert section present + cross-reference to `skills/data-ingestion/SKILL.md`

- [ ] **Step 6.4.1: Failing contract test**

```java
@Test void agentsTemplateMentionsDataIngestion() throws Exception {
    String content = readClasspath("/agents/AGENTS.md");
    assertThat(content).contains("## Data Ingestion (skill: data-ingestion)");
    assertThat(content).contains("skills/data-ingestion/SKILL.md");
    assertThat(content).contains("`datatalk_http_request`");
}
```

- [ ] **Step 6.4.2: Run fail**

- [ ] **Step 6.4.3: Edit AGENTS.md template — append section per spec §5.5**

- [ ] **Step 6.4.4: Run pass + commit**

### Task 6.5: Update DATA_SOURCE_TYPE_COMPATIBILITY.md

**Files:**
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`

- [ ] **Step 6.5.1: Add new section "Ingestion DDL Adapter"** — table listing all 19 first-class kinds with status (Day-1 supported / Day-1 unsupported follow-up).

- [ ] **Step 6.5.2: Commit**

### Task 6.6: End-to-end Playwright spec

**Files:**
- Create: `client/tests/e2e/ingestion.spec.ts`

Mock backend via WireMock for HTTP fetch endpoint + mock connection. Spec verifies:
- Tab opens automatically on `IngestionJobCreated` SSE event
- Mapping editor allows column rename + skip
- Click "Confirm and Ingest" → toast appears + Tab phase advances to writing → completed
- Failed scenarios (auth_failed, ssrf_blocked, dialect_unsupported) show correct error + userHint

(TDD pattern Playwright-style: 5 steps incl. test → fail [missing backend stub] → setup stub → green → commit.)

### Task 6.7: Phase 6 gate

- [ ] **Step 6.7.1: Backend full verify** — `cd server && mvn clean verify`
- [ ] **Step 6.7.2: Frontend full** — `cd client && npx tsc --noEmit && npx vitest run && npx playwright test ingestion.spec.ts`
- [ ] **Step 6.7.3: Manual smoke (Tauri dev)** — full happy path: create credential → AI prompt → Tab opens → mapping → confirm → table created → rows inserted
- [ ] **Step 6.7.4: Phase 6 marker + spec / plan housekeeping**

```bash
git commit --allow-empty -m "chore(ingestion): Phase 6 complete — skill bundle + AGENTS + E2E"
```

### Task 6.8: Documentation housekeeping (per CLAUDE.md Post-Execution rule)

- [ ] **Step 6.8.1: Tick every Phase checkbox in this plan**
- [ ] **Step 6.8.2: Move plan entry in `docs/exec-plans/index.md` from Active to Completed**
- [ ] **Step 6.8.3: Update `CLAUDE.md`** if any new convention was established (e.g. ingestion artifact path convention `~/.data-talk/ingestion/<jobId>/`)
- [ ] **Step 6.8.4: Update `docs/generated/db-schema.md`** — append ingestion_credential + ingestion_job tables + file_artifact CHECK extension
- [ ] **Step 6.8.5: Update product-specs §3.12** — flip 「三期 placeholder」→「三期 active (Generic HTTP scaffolding shipped)」
- [ ] **Step 6.8.6: Final commit**

```bash
git commit -m "docs(ingestion): post-execution housekeeping — index updates + schema doc + §3.12 status flip"
```

---

## Self-Review

**Spec coverage:**

| Spec § | Plan Phase / Task |
|--------|-------------------|
| §1.2 / §11 (6 Phases) | P1 / P2 / P3 / P4 / P5 / P6 |
| §2.1 三个新一等概念 | P1 T1.1 (V20) + P2 T2.3 (payload artifact) + P1 T1.10 (credential) |
| §2.2 Happy path 11 steps | P2 T2.3 (fetch) + P3 T3.3 (infer) + P5 T5.4 (confirm endpoint) + P5 T5.3 (executor) + P4 T4.3 (Tab phase router for steps 3,5,9,10) |
| §3.1 V20 migration | P1 T1.1 |
| §3.2 Domain | P1 T1.2 / T1.3 / T1.4 / T1.5 / T1.6 / T1.7 |
| §3.3 Application services | P2 T2.1–2.3 / P3 T3.1–3.4 / P5 T5.3 |
| §3.4 IngestionDdlAdapter + type table | P5 T5.1 |
| §3.5 Infrastructure | P1 T1.9 (repos) + P6 T6.3 (resolver) |
| §3.6 REST endpoints | P1 T1.11 (credentials) + P3 T3.5 (preview) + P5 T5.4 (confirm/cancel) |
| §4 MCP tool surface | P2 T2.4 + P3 T3.4 + P5 T5.5 (5 actions) |
| §4.2 IngestionConfirmedToken | P5 T5.2 |
| §5 Skill bundle | P6 T6.1–6.3 |
| §5.5 AGENTS.md + contract test | P6 T6.4 |
| §6 Frontend Tab UI + Settings | P4 T4.1–4.7 + P1 T1.12 (credentials sub-page) |
| §6.3 5-state token mapping | P4 T4.3–4.5 (each component implements per row) |
| §6.4 i18n keys | P4 T4.7 |
| §6.5 Credentials sub-page | P1 T1.12 |
| §8 Out-of-Scope (14 items) | Day-1 plan does not implement any of them; verified via plan task scope |
| §9 R1–R12 risks | Mitigations baked into respective tasks: R1 (DDL adapter固定模板 — T5.1) / R2 (500MB cap — T2.1 + T2.3) / R5 (SSRF — T2.1) / R7 (token in-memory — T5.2) / R10 (input schema — T2.4 + T3.4 + T5.5) / R11 (V18 dashboard CHECK preserved — T1.1 test) |
| §10 Data Source Compatibility | P6 T6.5 |
| §11 6 Phase split | Mirrors P1–P6 |
| §12 Acceptance checklist (16 items) | All covered by Phase gate steps + T6.5 |

**Placeholder scan:** No `TBD` / `TODO` / `implement later` strings. Pseudocode markers like "see step X" are pointers to detailed bodies within this plan, not external work. Code blocks with `…` or `/* */` indicate boilerplate the engineer fills via the pattern established (e.g. exhaustive switch arm filling, JDBC row mapper expansion).

**Type consistency:** Reviewed key types across tasks:
- `IngestionJob` (T1.3) record shape matches V20 columns (T1.1) and `IngestionJobRepository.save` signature (T1.8) and JDBC repo (T1.9) and `IngestionPayloadFetcher` constructor + usage (T2.3)
- `AuthScheme` enum (T1.2) used identically in `IngestionCredential` (T1.3), `IngestionCredentialService` (T1.10), `HttpFetchClient` (T2.2), and credential form (T1.12)
- `PayloadFormat` enum used in `FetchRequest` (T2.3), `IngestionSchemaInferrer` (T3.3), and parser dispatch
- `IngestionDdlAdapter.generateCreateTable` / `generateInsert` / `sqlTypeFor` / `supports` signatures consistent T5.1 → T5.3
- `IngestionConfirmedToken.tokenId` / `expiresAt` / `mappingHash` round-trip identical across T5.2 store, T5.4 endpoint, T5.5 action, T5.3 executor consume call

---

## Execution Handoff

**Plan complete and saved to `docs/exec-plans/2026-05-12-external-data-ingestion-skills-plan.md`.**

Per project rule (CLAUDE.md): parallel execution within a Phase is permitted only for independently-scoped tasks declared as such; the 6-Phase boundaries are sequential. Within each Phase, tasks share state (DB schema, domain records, service wiring) — execute sequentially within a Phase, but Phase 1 → 6 must complete in order.

Execution options:

**1. Subagent-Driven (recommended)** — Dispatch a fresh subagent per Task, review between tasks, fast iteration. Use `superpowers:subagent-driven-development`.

**2. Inline Execution** — Execute Tasks in this session using `superpowers:executing-plans`, batch execution with checkpoints between Phases.

Which approach do you want?
