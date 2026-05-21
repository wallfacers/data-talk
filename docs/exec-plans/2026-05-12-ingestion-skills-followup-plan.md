# External Data Ingestion — Follow-up Closure Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining gaps from the original ingestion plan (`2026-05-12-external-data-ingestion-skills-plan.md`) so the HTTP-fetch → infer → confirm → CREATE TABLE → batch-INSERT chain is genuinely production-ready: (a) mapping shows up in the user Tab automatically; (b) confirm-token's `mappingHash` actually protects against mapping drift; (c) ingestion events flow through `SessionBus` so the Tab opens/updates on its own; (d) large payloads stream without OOM; (e) the skill bundle is packaged and unpacked at startup; (f) Testcontainers / Playwright nail down the cross-process behaviour; (g) documentation (plan checkboxes, index, db-schema, product-spec status) is reconciled with reality.

**Architecture:** Four sequential phases, each running mutually-independent tasks in parallel:
- **Phase A — MVP closure** (3 parallel tasks): API surface for `mapping` + server-side hash, DtEvent publishing, streaming row parser.
- **Phase B — Skill deployment** (3 tasks, partially parallel): maven-assembly descriptor, `OpenCodeBinaryResolver.ensureDataIngestionSkill`, AGENTS.md contract test.
- **Phase C — Verification reinforcement** (2 parallel tasks): Testcontainers MySQL + Postgres IT for `IngestionExecutor`, Playwright E2E happy path.
- **Phase D — Documentation housekeeping** (sequential): tick checkboxes, move plan to Completed, update `db-schema.md`, flip product-spec §3.12, register conventions in CLAUDE.md.

**Tech Stack:** Spring Boot 3.5 + Java 21 virtual threads, JUnit 5 + AssertJ + WireMock 3 + Testcontainers (mysql:8.0, postgres:16-alpine), React 19 + Zustand + TanStack Query, Vitest + Playwright.

**Original plan & spec:**
- Plan: [docs/exec-plans/2026-05-12-external-data-ingestion-skills-plan.md](2026-05-12-external-data-ingestion-skills-plan.md)
- Spec: [docs/product-specs/2026-05-12-external-data-ingestion-skills-design.md](../product-specs/2026-05-12-external-data-ingestion-skills-design.md)

**Out of scope** (explicit non-goals, do not expand this plan):
- DDL `SqlStatementGuard` — `IngestionDdlAdapter` already emits from a constrained template with quoted identifiers + enum-only types; adding a parser-based guard adds no real safety. Document the deviation in Phase D.
- Per-dialect Testcontainer ITs beyond mysql/postgres — the other 15 first-class kinds are scope of their own follow-up child plans (`IngestionDialectUnsupportedException` already covers them).
- Skill manifest `requires_bash_network` declaration changes — Day-1 ships without native fetch.

---

## File Structure

### Backend new files
- `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/MappingHash.java` — pure function `compute(IngestionMapping) → String` (Phase A T1).
- `server/data-talk-application/src/test/java/com/datatalk/application/ingestion/MappingHashTest.java` — determinism + column-order independence + skip-flag sensitivity.
- `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionEventPublisher.java` — thin wrapper around `SessionBusRegistry` that resolves the bus, builds `DtEvent` and publishes; isolates publishing from each call-site (Phase A T2).
- `server/data-talk-application/src/test/java/com/datatalk/application/ingestion/IngestionEventPublisherTest.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/ingestion/IngestionExecutorEventsTest.java` — verifies executor publishes the right events via a fake `SessionBus`.
- `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/parser/RowStream.java` — `AutoCloseable Iterator<Map<String,Object>>` for streaming rows (Phase A T3).
- `server/data-talk-application/src/test/java/com/datatalk/application/ingestion/parser/RowStreamTest.java`
- `server/data-talk-infrastructure/src/assembly/data-ingestion.xml` — maven-assembly descriptor (Phase B T1).
- `server/data-talk-infrastructure/src/main/resources/opencode/skills/data-ingestion.version` — generated/static version marker.
- `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolverDataIngestionTest.java` — mirror of `OpenCodeBinaryResolverBezelTest` (Phase B T2).
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentsTemplateContractTest.java` — asserts AGENTS.md classpath resource contains required sections (Phase B T3).
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/ingestion/IngestionExecutorMysqlIT.java` — Testcontainers MySQL 8.0 (Phase C T1).
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/ingestion/IngestionExecutorPostgresIT.java` — Testcontainers postgres:16-alpine (Phase C T1).
- `client/tests/e2e/ingestion-happy-path.spec.ts` — Playwright (Phase C T2).

### Backend modified files
- `server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/IngestionJob.java` — add `mappingHash` accessor / setter (record component already serialised through repo; just expose).
- `server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java` — no permits change, but verify exhaustive switch coverage for all 8 ingestion permits still compiles.
- `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionSchemaInferrer.java` — after `updateMapping`, also compute & persist `mappingHash`; publish `IngestionMappingProposed`.
- `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionExecutor.java` — replace `Files.readString` with `RowStream`; inject `IngestionEventPublisher`; publish `IngestionWriteStarted` / `IngestionWriteProgress` / `IngestionCompleted` / `IngestionFailed`; re-verify `mappingHash` immediately before `tokenStore.consume` (defence in depth).
- `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionPayloadFetcher.java` — publish `IngestionJobCreated` (on first byte) and `IngestionPayloadFetched`.
- `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/repository/IngestionJobRepository.java` — extend with `updateMappingHash(jobId, hash, updatedAt)` if not already present; add `findMappingHash`.
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/ingestion/JdbcIngestionJobRepository.java` — wire new repo methods + ensure read/write of `mapping_hash` column (V20 already declares it).
- `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/parser/PayloadParser.java` — extend interface with `RowStream openRowStream(Path)`.
- `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/parser/JsonPayloadParser.java`, `JsonlPayloadParser.java`, `CsvPayloadParser.java`, `HtmlTablePayloadParser.java` — implement `openRowStream`.
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/IngestionController.java` — `jobToMap` includes `mapping` + `mappingHash`; `confirm` returns and persists server-computed hash (frontend body becomes `{}` or omitted).
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ingestion/HttpRequestActionHandler.java`, `InferIngestionSchemaActionHandler.java`, `CreateIngestionTableActionHandler.java`, `IngestPayloadActionHandler.java` — pass through `sessionId` from `ActionContext` so the executor can resolve the bus; surface `mappingHash` in `get_ingestion_job` action output.
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolver.java` — add `DATA_INGESTION_RESOURCE`, `DATA_INGESTION_VERSION_RESOURCE`, `DATA_INGESTION_MARKER`, `ensureDataIngestionSkill`, `readEmbeddedDataIngestionVersion`.
- `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeProcessManager.java` — call `ensureDataIngestionSkill(projectRoot)` right after `ensureBezelSkill`.
- `server/data-talk-infrastructure/pom.xml` — add `<execution>vendor-data-ingestion</execution>` mirroring `vendor-bezel`.

### Frontend modified files
- `client/src/features/ingestion/api/ingestion-api.ts` — extend `IngestionJobView` with `mapping?: { columns: MappingColumn[] }` + `mappingHash?: string`; `confirmIngestionJob` body becomes `{}` and returns `{ tokenId, expiresAt, mappingHash }`.
- `client/src/features/ingestion/stores/use-ingestion-jobs-store.ts` — add `hydrateFrom(job)` that fills `editingMapping[jobId]` from `job.mapping` when the store has no edits yet.
- `client/src/features/ingestion/ingestion-job-tab.tsx` — invoke `hydrateFrom` inside `useEffect` and pass `mappingHash` along (now informative only — backend trusts persisted hash).
- `client/src/features/ingestion/phases/mapping-phase.tsx` — render `<MappingEditor>` unconditionally once `columns.length > 0`; disable confirm only while no columns.

### Documentation modified files (Phase D)
- `docs/exec-plans/2026-05-12-external-data-ingestion-skills-plan.md` — tick all checkboxes that are now done.
- `docs/exec-plans/index.md` — move ingestion row from `## 活跃计划` to `## 已完成计划`.
- `docs/exec-plans/2026-05-12-ingestion-skills-followup-plan.md` (this file) — register under `## 活跃计划`; move to Completed after Phase D.
- `docs/generated/db-schema.md` — append `ingestion_credential`, `ingestion_job`, `ingestion_vault_store` tables; note `file_artifact.kind` CHECK extension.
- `docs/product-specs/index.md` — flip Task 10 三期 from `placeholder` to `active (Generic HTTP scaffolding shipped 2026-05-12)`.
- `docs/product-specs/2026-05-12-external-data-ingestion-skills-design.md` — top `Phase` line: `三期 — 首份 child spec (shipped)`.
- `CLAUDE.md` — under "Knowledge Base Navigation" add a row for the new ingestion plan; under "Working Rules" add an Ingestion Artifact Path Convention block (`~/.data-talk/ingestion/<jobId>/payload.*`).

---

## Execution batching for the leader

When dispatching subagents, run these batches in order. Within a batch, dispatch all tasks **simultaneously** in one message with multiple `Agent` tool blocks. Between batches, run a consolidated verification pass (`mvn clean verify` + `npx tsc --noEmit` + targeted vitest) before starting the next.

| Batch | Tasks | Independence rationale |
|-------|-------|------------------------|
| **A** | A1 (mapping API+hash), A2 (event publish), A3 (streaming parser) | A1 touches controller/repo/frontend; A2 touches publisher + 3 call-sites; A3 touches parser interface + executor batch loop. The three only intersect in `IngestionExecutor.java` — A2 + A3 must coordinate via the constructor signature (see Coordination Note in T2.1 + T3.1). |
| **B** | B1 (assembly), B3 (contract test) in parallel → then B2 (resolver+wiring+test) | B2 depends on B1 (the tarball must exist for the resolver test to fixture from classpath). B3 is independent of B1/B2. |
| **C** | C1 (Testcontainers), C2 (Playwright) | C1 only touches backend; C2 only touches frontend + mocked backend. |
| **D** | D1 (doc housekeeping) | Sequential, executed at the end as a single agent run. |

**Coordination note for Phase A:** Tasks A2 and A3 both modify `IngestionExecutor.java`'s constructor. If both run in parallel subagents, the second to merge must rebase. To prevent merge churn, the leader (you) should hand A2 and A3 to the same subagent (combined into one task brief), or run them sequentially with explicit "after A2 lands, before A3 starts" gating. The plan below assumes **A2 and A3 are run sequentially in one subagent**, while A1 runs in parallel as a separate subagent. Adjust the dispatch accordingly.

---

## Phase A — MVP closure

### Task A1: Surface mapping in API + server-computed mappingHash

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/MappingHash.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/ingestion/MappingHashTest.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionSchemaInferrer.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/repository/IngestionJobRepository.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/ingestion/JdbcIngestionJobRepository.java`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/IngestionController.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionExecutor.java`
- Modify: `client/src/features/ingestion/api/ingestion-api.ts`
- Modify: `client/src/features/ingestion/stores/use-ingestion-jobs-store.ts`
- Modify: `client/src/features/ingestion/ingestion-job-tab.tsx`
- Modify: `client/src/features/ingestion/phases/mapping-phase.tsx`
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ingestion/GetIngestionJobActionHandler.java`

- [ ] **Step A1.1: Write `MappingHashTest` (failing)**

```java
package com.datatalk.application.ingestion;

import com.datatalk.domain.ingestion.InferredType;
import com.datatalk.domain.ingestion.IngestionMapping;
import com.datatalk.domain.ingestion.MappingColumn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MappingHashTest {

    private static MappingColumn col(String src, String dst, InferredType t, boolean skip) {
        return new MappingColumn(src, dst, t, skip, List.of(), false);
    }

    @Test
    void hashIsDeterministicForSameLogicalMapping() {
        var a = new IngestionMapping("m1", List.of(
            col("$.id", "id", InferredType.INTEGER_64, false),
            col("$.name", "name", InferredType.STRING_64, false)));
        var b = new IngestionMapping("m2-different-id", List.of(
            col("$.id", "id", InferredType.INTEGER_64, false),
            col("$.name", "name", InferredType.STRING_64, false)));
        assertThat(MappingHash.compute(a)).isEqualTo(MappingHash.compute(b));
    }

    @Test
    void hashIgnoresColumnOrder() {
        var a = new IngestionMapping("m", List.of(
            col("$.id", "id", InferredType.INTEGER_64, false),
            col("$.name", "name", InferredType.STRING_64, false)));
        var b = new IngestionMapping("m", List.of(
            col("$.name", "name", InferredType.STRING_64, false),
            col("$.id", "id", InferredType.INTEGER_64, false)));
        assertThat(MappingHash.compute(a)).isEqualTo(MappingHash.compute(b));
    }

    @Test
    void hashIgnoresSampleValuesAndNullable() {
        var a = new IngestionMapping("m", List.of(
            new MappingColumn("$.id", "id", InferredType.INTEGER_64, false, List.of("1","2"), false)));
        var b = new IngestionMapping("m", List.of(
            new MappingColumn("$.id", "id", InferredType.INTEGER_64, false, List.of(), true)));
        assertThat(MappingHash.compute(a)).isEqualTo(MappingHash.compute(b));
    }

    @Test
    void hashDiffersOnSkipChange() {
        var a = new IngestionMapping("m", List.of(
            col("$.id", "id", InferredType.INTEGER_64, false)));
        var b = new IngestionMapping("m", List.of(
            col("$.id", "id", InferredType.INTEGER_64, true)));
        assertThat(MappingHash.compute(a)).isNotEqualTo(MappingHash.compute(b));
    }

    @Test
    void hashDiffersOnTypeChange() {
        var a = new IngestionMapping("m", List.of(
            col("$.id", "id", InferredType.INTEGER_64, false)));
        var b = new IngestionMapping("m", List.of(
            col("$.id", "id", InferredType.STRING_64, false)));
        assertThat(MappingHash.compute(a)).isNotEqualTo(MappingHash.compute(b));
    }

    @Test
    void hashIsHexLowercase64Chars() {
        var m = new IngestionMapping("m", List.of(
            col("$.x", "x", InferredType.STRING_64, false)));
        String h = MappingHash.compute(m);
        assertThat(h).matches("^[0-9a-f]{64}$");
    }
}
```

- [ ] **Step A1.2: Run test — expect compile error (`MappingHash` missing)**

```bash
cd server && mvn test -pl data-talk-application -Dtest=MappingHashTest -q
```
Expected: `cannot find symbol class MappingHash`.

- [ ] **Step A1.3: Implement `MappingHash`**

```java
package com.datatalk.application.ingestion;

import com.datatalk.domain.ingestion.IngestionMapping;
import com.datatalk.domain.ingestion.MappingColumn;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * Pure deterministic SHA-256 of the user-meaningful subset of an
 * {@link IngestionMapping}. Sample values, nullable flag, and the
 * mapping's own random id are excluded — only sourcePath / targetName /
 * type / skip participate. Columns are canonicalised by sourcePath
 * ordering so the hash is order-independent.
 */
public final class MappingHash {

    private MappingHash() {}

    public static String compute(IngestionMapping mapping) {
        String canonical = mapping.columns().stream()
            .sorted(Comparator.comparing(MappingColumn::sourcePath))
            .map(MappingHash::columnLine)
            .collect(Collectors.joining("\n"));
        return sha256Hex(canonical);
    }

    private static String columnLine(MappingColumn c) {
        return c.sourcePath() + "\t" + c.targetName() + "\t" + c.type().name() + "\t" + c.skip();
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
```

- [ ] **Step A1.4: Run test — expect PASS**

```bash
cd server && mvn test -pl data-talk-application -Dtest=MappingHashTest -q
```
Expected: `Tests run: 6, Failures: 0`.

- [ ] **Step A1.5: Verify V20 already has `mapping_hash` column**

```bash
grep -n "mapping_hash" server/data-talk-infrastructure/src/main/resources/db/migration/V20__*.sql
```
If absent, append `mapping_hash TEXT NULL` to the `ingestion_job` table (it should be present per original P1; if missing, add it in this step — column add only, no data migration required).

- [ ] **Step A1.6: Extend `IngestionJobRepository` interface**

Open `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/repository/IngestionJobRepository.java` and add (after `updateMapping`):

```java
void updateMappingHash(String id, String mappingHash, long updatedAt);
```

- [ ] **Step A1.7: Implement in `JdbcIngestionJobRepository`**

Append method:

```java
@Override
public void updateMappingHash(String id, String mappingHash, long updatedAt) {
    jdbc.update(
        "UPDATE ingestion_job SET mapping_hash = ?, updated_at = ? WHERE id = ?",
        mappingHash, updatedAt, id);
}
```

And in the row mapper used by `findById` / `list`, add `mapping_hash` retrieval. Find the existing `RowMapper<IngestionJob>` lambda; add a line that reads `rs.getString("mapping_hash")` and threads it into the `IngestionJob` constructor (extend the record if necessary — see A1.8).

- [ ] **Step A1.8: Extend `IngestionJob` record with `mappingHash`**

If `IngestionJob` does not yet have `String mappingHash`, add it as the final component (so existing positional calls without it break loudly). Update the row mapper, `JdbcIngestionJobRepository.save/upsert` SQL, and any inline test factories.

- [ ] **Step A1.9: Compile probe**

```bash
cd server && mvn compile -q
```
Expected: zero errors. Fix call sites until clean.

- [ ] **Step A1.10: Compute hash in `IngestionSchemaInferrer.infer`**

After the existing `jobRepo.updateMapping(...)` line, add:

```java
String mappingHash = MappingHash.compute(mapping);
jobRepo.updateMappingHash(jobId, mappingHash, System.currentTimeMillis());
```

(Phase A T2 will also publish `IngestionMappingProposed` here — leave the publishing slot empty; T2 fills it.)

- [ ] **Step A1.11: Server-side hash in `IngestionController.confirm`**

Replace the current `confirm` handler body with:

```java
@PostMapping("/jobs/{id}/confirm")
public ResponseEntity<Map<String, Object>> confirm(@PathVariable String id) {
    var jobOpt = jobRepo.findById(id);
    if (jobOpt.isEmpty()) return ResponseEntity.notFound().build();
    IngestionJob job = jobOpt.get();
    if (job.mapping() == null) {
        return ResponseEntity.status(409).body(Map.of(
            "error", Map.of("code", "INGESTION_MAPPING_MISSING",
                            "reason", "Run datatalk_infer_ingestion_schema first")));
    }
    String hash = MappingHash.compute(job.mapping());
    jobRepo.updateMappingHash(id, hash, System.currentTimeMillis());
    var token = tokenStore.issue(id, hash);
    jobRepo.updateStatus(id, "confirmed", null, System.currentTimeMillis());
    return ResponseEntity.ok(Map.of(
        "tokenId", token.tokenId(),
        "expiresAt", token.expiresAt(),
        "mappingHash", hash
    ));
}
```

Remove the `ConfirmRequest` record. Delete the `@RequestBody ConfirmRequest req` parameter.

- [ ] **Step A1.12: Expose `mapping` + `mappingHash` in `jobToMap`**

Inside `IngestionController.jobToMap`, after `map.put("errorMessage", j.errorMessage());` add:

```java
map.put("mappingHash", j.mappingHash());
if (j.mapping() != null) {
    Map<String, Object> mapping = new LinkedHashMap<>();
    mapping.put("mappingId", j.mapping().mappingId());
    mapping.put("columns", j.mapping().columns().stream().map(c -> {
        Map<String, Object> col = new LinkedHashMap<>();
        col.put("sourcePath", c.sourcePath());
        col.put("targetName", c.targetName());
        col.put("type", c.type().name());
        col.put("skip", c.skip());
        col.put("sampleValues", c.sampleValues());
        col.put("nullable", c.nullable());
        return col;
    }).toList());
    map.put("mapping", mapping);
} else {
    map.put("mapping", null);
}
```

- [ ] **Step A1.13: Surface mappingHash in `GetIngestionJobActionHandler`**

In `GetIngestionJobActionHandler.handle`, when building the output map, add `out.put("mappingHash", job.mappingHash());` and `out.put("mapping", /* same shape as controller */);`. This is what the AI uses to obtain the hash to pass to `create_ingestion_table`.

- [ ] **Step A1.14: Tighten executor hash check**

In `IngestionExecutor.createTable`, before `tokenStore.consume(tokenId, jobId, mappingHash)`, add:

```java
String persistedHash = job.mappingHash();
if (persistedHash != null && !persistedHash.equals(mappingHash)) {
    throw new IngestionTokenInvalidException(
        "mapping hash mismatch — re-confirm the job");
}
```

- [ ] **Step A1.15: Compile probe + run impacted application tests**

```bash
cd server && mvn -pl data-talk-application -am compile -q
cd server && mvn -pl data-talk-application test -Dtest='Ingestion*Test' -q
```
Expected: green.

- [ ] **Step A1.16: Frontend — extend types**

`client/src/features/ingestion/api/ingestion-api.ts`:

```ts
export interface MappingColumnView {
  sourcePath: string
  targetName: string
  type: string
  skip: boolean
  sampleValues: string[]
  nullable: boolean
}

export interface IngestionJobView {
  id: string
  sourceUrl: string
  status: string
  payloadFormat: string | null
  payloadArtifactId: string | null
  connectionId: string | null
  targetSchema: string | null
  targetTable: string | null
  rowCount: number | null
  rowsInserted: number | null
  bytesFetched: number | null
  createdAt: number
  updatedAt: number
  completedAt: number | null
  errorMessage: string | null
  mappingHash: string | null
  mapping: { mappingId: string; columns: MappingColumnView[] } | null
}

export async function confirmIngestionJob(jobId: string): Promise<{
  tokenId: string
  expiresAt: number
  mappingHash: string
}> {
  return http.post(`ingestion/jobs/${jobId}/confirm`, { json: {} })
    .json<{ tokenId: string; expiresAt: number; mappingHash: string }>()
}
```

- [ ] **Step A1.17: Frontend — hydrate store from `job.mapping`**

`client/src/features/ingestion/stores/use-ingestion-jobs-store.ts`, add:

```ts
import type { IngestionJobView } from '../api/ingestion-api'

interface IngestionJobsState {
  editingMapping: Map<string, MappingEditState>
  setMappingEdit: (jobId: string, edits: MappingEditState) => void
  clearMappingEdit: (jobId: string) => void
  hydrateFromJob: (job: IngestionJobView) => void
}

// in the create():
hydrateFromJob: (job) =>
  set((s) => {
    if (s.editingMapping.has(job.id)) return s
    if (!job.mapping || job.mapping.columns.length === 0) return s
    const next = new Map(s.editingMapping)
    next.set(job.id, {
      columns: job.mapping.columns.map((c) => ({
        sourcePath: c.sourcePath,
        targetName: c.targetName,
        type: c.type,
        skip: c.skip,
        sampleValues: c.sampleValues,
        nullable: c.nullable,
      })),
      targetTable: job.targetTable ?? 'ingested_data',
      targetSchema: job.targetSchema,
    })
    return { editingMapping: next }
  }),
```

- [ ] **Step A1.18: Frontend — wire hydration in `ingestion-job-tab.tsx`**

Replace the existing `useEffect` block with:

```ts
const hydrateFromJob = useIngestionJobsStore((s) => s.hydrateFromJob)
useEffect(() => {
  if (job) hydrateFromJob(job)
}, [job, hydrateFromJob])
```

- [ ] **Step A1.19: Frontend — type check**

```bash
cd client && npx tsc --noEmit
```
Expected: zero errors.

- [ ] **Step A1.20: Frontend — vitest impacted**

```bash
cd client && npx vitest run --reporter=verbose features/ingestion
```
Expected: green (no ingestion-specific failures; pre-existing failures elsewhere are out of scope).

- [ ] **Step A1.21: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/ingestion/MappingHash.java \
        server/data-talk-application/src/test/java/com/datatalk/application/ingestion/MappingHashTest.java \
        server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionSchemaInferrer.java \
        server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionExecutor.java \
        server/data-talk-application/src/main/java/com/datatalk/application/ingestion/repository/IngestionJobRepository.java \
        server/data-talk-infrastructure/src/main/java/com/datatalk/infra/ingestion/JdbcIngestionJobRepository.java \
        server/data-talk-domain/src/main/java/com/datatalk/domain/ingestion/IngestionJob.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/IngestionController.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ingestion/GetIngestionJobActionHandler.java \
        client/src/features/ingestion/api/ingestion-api.ts \
        client/src/features/ingestion/stores/use-ingestion-jobs-store.ts \
        client/src/features/ingestion/ingestion-job-tab.tsx
git commit -m "feat(ingestion): A1 — surface mapping + server-computed mappingHash; auto-hydrate Tab"
```

---

### Task A2: Publish `DtEvent` from fetcher / inferrer / executor

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionEventPublisher.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/ingestion/IngestionEventPublisherTest.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/ingestion/IngestionExecutorEventsTest.java`
- Modify: `IngestionPayloadFetcher`, `IngestionSchemaInferrer`, `IngestionExecutor`
- Modify: `HttpRequestActionHandler`, `InferIngestionSchemaActionHandler`, `CreateIngestionTableActionHandler`, `IngestPayloadActionHandler` — forward `ctx.sessionId()` to the application service.

- [ ] **Step A2.1: Write `IngestionEventPublisherTest` (failing)**

```java
package com.datatalk.application.ingestion;

import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IngestionEventPublisherTest {

    @Test
    void publishesToResolvedBus() {
        SessionBusRegistry registry = mock(SessionBusRegistry.class);
        SessionBus bus = mock(SessionBus.class);
        when(registry.getOrCreate("sess-1")).thenReturn(bus);

        IngestionEventPublisher publisher = new IngestionEventPublisher(registry);
        DtEvent.IngestionJobCreated event = new DtEvent.IngestionJobCreated("job-1", "https://example/api");
        publisher.publish("sess-1", event);

        verify(bus).publish(event);
    }

    @Test
    void nullSessionIdIsNoop() {
        SessionBusRegistry registry = mock(SessionBusRegistry.class);
        IngestionEventPublisher publisher = new IngestionEventPublisher(registry);
        publisher.publish(null, new DtEvent.IngestionJobCreated("job-1", "u"));
        verifyNoInteractions(registry);
    }

    @Test
    void blankSessionIdIsNoop() {
        SessionBusRegistry registry = mock(SessionBusRegistry.class);
        IngestionEventPublisher publisher = new IngestionEventPublisher(registry);
        publisher.publish("   ", new DtEvent.IngestionJobCreated("job-1", "u"));
        verifyNoInteractions(registry);
    }
}
```

- [ ] **Step A2.2: Run — expect compile failure**

```bash
cd server && mvn test -pl data-talk-application -Dtest=IngestionEventPublisherTest -q
```

- [ ] **Step A2.3: Implement `IngestionEventPublisher`**

```java
package com.datatalk.application.ingestion;

import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import org.springframework.stereotype.Component;

@Component
public class IngestionEventPublisher {

    private final SessionBusRegistry buses;

    public IngestionEventPublisher(SessionBusRegistry buses) {
        this.buses = buses;
    }

    public void publish(String sessionId, DtEvent event) {
        if (sessionId == null || sessionId.isBlank()) return;
        buses.getOrCreate(sessionId).publish(event);
    }
}
```

- [ ] **Step A2.4: Run test — expect PASS**

- [ ] **Step A2.5: Inject + publish in `IngestionPayloadFetcher`**

Add `IngestionEventPublisher publisher` to constructor. Add a `String sessionId` parameter to the public `fetch(...)` method (overloads can stay if existing callers cannot be updated atomically — prefer to update all callers). At these points, publish:
- after job row first persisted: `publisher.publish(sessionId, new DtEvent.IngestionJobCreated(job.id(), request.url()));`
- after final atomic rename + `file_artifact` row written: `publisher.publish(sessionId, new DtEvent.IngestionPayloadFetched(job.id(), artifactId, bytesFetched, finalRowCount));`
- on failure path: `publisher.publish(sessionId, new DtEvent.IngestionFailed(job.id(), "fetching", err.getMessage()));`

- [ ] **Step A2.6: Inject + publish in `IngestionSchemaInferrer`**

After the new `updateMappingHash` call from A1.10, add:

```java
publisher.publish(sessionId, new DtEvent.IngestionMappingProposed(
    jobId, mapping.mappingId(), mapping.columns().size()));
```

Add `String sessionId` to `infer(...)` signature; update `InferIngestionSchemaActionHandler` to pass `ctx.sessionId()`.

- [ ] **Step A2.7: Inject + publish in `IngestionExecutor`**

Constructor adds `IngestionEventPublisher publisher`. Both `createTable` and `ingestPayload` gain a `String sessionId` parameter (forwarded from the calling Action handler via `ctx.sessionId()`).

In `createTable`, after DDL succeeds and before returning:
```java
publisher.publish(sessionId, new DtEvent.IngestionJobConfirmed(jobId, tokenId));
publisher.publish(sessionId, new DtEvent.IngestionWriteStarted(jobId,
    schema != null ? schema + "." + table : table));
```

In `ingestPayload`, on success:
```java
publisher.publish(sessionId, new DtEvent.IngestionCompleted(
    jobId, job.targetTable(), rowsInserted, durationMs));
```

On failure (catch block):
```java
publisher.publish(sessionId, new DtEvent.IngestionFailed(jobId, "writing", e.getMessage()));
```

Inside the batch loop, every 5 batches publish progress (`IngestionWriteProgress(jobId, rowsInserted, mapping.columns().size())` — the second int is unknown total at stream time; the event spec uses `totalRows`, pass `-1` if unknown, but per current record signature pass the running count for both fields; if signature needs tweak, update `DtEvent.IngestionWriteProgress` definition with care). Acceptable approach: publish at end of each batch with `(rowsInserted, /*totalRows*/ -1)`.

- [ ] **Step A2.8: Update Action handlers to forward `sessionId`**

In each of the 4 ingestion Action handlers (`HttpRequest`, `InferIngestionSchema`, `CreateIngestionTable`, `IngestPayload`), the `handle(ActionContext ctx, Map input)` method now reads `ctx.sessionId()` and passes it to the application call. Example for `IngestPayloadActionHandler`:

```java
var result = executor.ingestPayload(jobId, batchSize, ctx.sessionId());
```

- [ ] **Step A2.9: Write `IngestionExecutorEventsTest`**

```java
package com.datatalk.application.ingestion;

import com.datatalk.application.connection.*;
import com.datatalk.application.fileartifact.FileArtifactRepository;
import com.datatalk.application.ingestion.ddl.*;
import com.datatalk.application.ingestion.parser.*;
import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.domain.event.DtEvent;
import com.datatalk.domain.fileartifact.FileArtifact;
import com.datatalk.domain.ingestion.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IngestionExecutorEventsTest {

    @Test
    void createTablePublishesConfirmedAndWriteStarted(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        // ... wire mocks: jobRepo.findById returns a job with mapping + matching hash,
        //     tokenStore.consume passes, adapter resolves to H2 adapter, connection record kind=h2.
        //     Capture publisher.publish() arguments and assert sequence:
        //       IngestionJobConfirmed, IngestionWriteStarted
    }

    @Test
    void ingestPayloadPublishesCompletedOnSuccess(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        // ... wire artifact pointing to a 3-row JSON file; H2 in-memory connection.
        //     Assert IngestionWriteProgress published at least once and IngestionCompleted last.
    }

    @Test
    void ingestPayloadPublishesFailedOnException(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        // ... artifact points to a malformed payload. Assert IngestionFailed with phase="writing".
    }
}
```

Fill the bodies with a real in-memory H2 connection (use `JdbcUrlBuilder` + an in-process H2 jdbc URL, e.g. `jdbc:h2:mem:exec_test;MODE=PostgreSQL`). For the publisher capture, inject a `IngestionEventPublisher` whose `SessionBusRegistry` is a Mockito mock returning a `SessionBus` mock; verify ordered captures with `InOrder`.

- [ ] **Step A2.10: Run executor events test**

```bash
cd server && mvn test -pl data-talk-application -Dtest='IngestionExecutorEventsTest' -q
```
Expected: green.

- [ ] **Step A2.11: Compile probe**

```bash
cd server && mvn compile -q
```

- [ ] **Step A2.12: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionEventPublisher.java \
        server/data-talk-application/src/test/java/com/datatalk/application/ingestion/IngestionEventPublisherTest.java \
        server/data-talk-application/src/test/java/com/datatalk/application/ingestion/IngestionExecutorEventsTest.java \
        server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionPayloadFetcher.java \
        server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionSchemaInferrer.java \
        server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionExecutor.java \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ingestion/
git commit -m "feat(ingestion): A2 — publish 8 DtEvent permits through SessionBus across fetch/infer/execute"
```

---

### Task A3: Streaming `RowStream` parser (drop `Files.readString`)

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/ingestion/parser/RowStream.java`
- Modify: `PayloadParser` interface
- Modify: `JsonPayloadParser`, `JsonlPayloadParser`, `CsvPayloadParser`, `HtmlTablePayloadParser`
- Modify: `IngestionExecutor.executeBatchInsert`
- Create: `RowStreamTest` (per parser × 1 case)

- [ ] **Step A3.1: Define `RowStream`**

```java
package com.datatalk.application.ingestion.parser;

import java.util.Iterator;
import java.util.Map;

/**
 * One-shot iterator over payload rows. Caller MUST close. Implementations
 * back this with a streaming reader (Jackson token stream / line reader /
 * jsoup table walker) so memory stays O(1) in payload size.
 */
public interface RowStream extends AutoCloseable, Iterator<Map<String, Object>> {
    @Override
    void close();
}
```

- [ ] **Step A3.2: Extend `PayloadParser` interface**

```java
public interface PayloadParser {
    IngestionMapping infer(Path payloadFile, int sampleSize);
    RowStream openRowStream(Path payloadFile);
}
```

- [ ] **Step A3.3: Implement `openRowStream` per parser**

For `JsonlPayloadParser` (simplest, line-by-line):

```java
@Override
public RowStream openRowStream(Path payloadFile) {
    try {
        var reader = Files.newBufferedReader(payloadFile, StandardCharsets.UTF_8);
        ObjectMapper om = this.objectMapper;
        return new RowStream() {
            String nextLine = advance();
            String advance() {
                try {
                    String l;
                    while ((l = reader.readLine()) != null) {
                        if (!l.isBlank()) return l;
                    }
                    return null;
                } catch (IOException e) { throw new RuntimeException(e); }
            }
            @Override public boolean hasNext() { return nextLine != null; }
            @Override public Map<String, Object> next() {
                try {
                    JsonNode node = om.readTree(nextLine);
                    Map<String, Object> row = new LinkedHashMap<>();
                    node.fields().forEachRemaining(f -> row.put(f.getKey(), convertNode(f.getValue())));
                    nextLine = advance();
                    return row;
                } catch (IOException e) { throw new RuntimeException(e); }
            }
            @Override public void close() {
                try { reader.close(); } catch (IOException ignored) {}
            }
        };
    } catch (IOException e) { throw new RuntimeException(e); }
}
```

For `JsonPayloadParser` (use Jackson `JsonParser` token-stream):

```java
@Override
public RowStream openRowStream(Path payloadFile) {
    try {
        JsonParser p = objectMapper.getFactory().createParser(payloadFile.toFile());
        JsonToken first = p.nextToken();
        if (first != JsonToken.START_ARRAY) {
            // envelope/object → wrap a single record
            JsonNode root = objectMapper.readTree(p);
            p.close();
            Iterator<JsonNode> single = List.of(root).iterator();
            return wrap(single, () -> {});
        }
        return new RowStream() {
            JsonToken cur = p.nextToken();
            @Override public boolean hasNext() { return cur != null && cur != JsonToken.END_ARRAY; }
            @Override public Map<String, Object> next() {
                try {
                    JsonNode node = objectMapper.readTree(p);
                    cur = p.nextToken();
                    Map<String, Object> row = new LinkedHashMap<>();
                    if (node.isObject()) {
                        node.fields().forEachRemaining(f -> row.put(f.getKey(), convertNode(f.getValue())));
                    }
                    return row;
                } catch (IOException e) { throw new RuntimeException(e); }
            }
            @Override public void close() { try { p.close(); } catch (IOException ignored) {} }
        };
    } catch (IOException e) { throw new RuntimeException(e); }
}
```

For `CsvPayloadParser`: use `Files.newBufferedReader` + manual line consumption, reading header from first line, returning `Map<String,Object>` per data line. Mirror the existing `parseCsvLine` helper.

For `HtmlTablePayloadParser`: HTML is bounded in practice; either keep full DOM parse and iterate over `<tbody><tr>` (acceptable since HTML payload cap is the smaller of the two limits), or document that this format remains buffered. Provide a `RowStream` that wraps the existing rows list as an iterator — explicitly noted in a `// streaming intentionally deferred — HTML payload bounded to 50MB hard cap` comment.

- [ ] **Step A3.4: Write `RowStreamTest`**

```java
package com.datatalk.application.ingestion.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class RowStreamTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void jsonlStreamsThreeRows(@TempDir Path tmp) throws Exception {
        Path p = tmp.resolve("a.jsonl");
        Files.writeString(p, "{\"id\":1}\n{\"id\":2}\n{\"id\":3}\n");
        var parser = new JsonlPayloadParser(om);
        try (RowStream rs = parser.openRowStream(p)) {
            List<Map<String,Object>> out = new ArrayList<>();
            while (rs.hasNext()) out.add(rs.next());
            assertThat(out).hasSize(3);
            assertThat(out.get(2).get("id")).isEqualTo(3);
        }
    }

    @Test
    void jsonArrayStreamsRowByRow(@TempDir Path tmp) throws Exception {
        Path p = tmp.resolve("a.json");
        Files.writeString(p, "[{\"id\":1},{\"id\":2}]");
        var parser = new JsonPayloadParser(om);
        try (RowStream rs = parser.openRowStream(p)) {
            assertThat(rs.hasNext()).isTrue();
            assertThat(rs.next().get("id")).isEqualTo(1);
            assertThat(rs.next().get("id")).isEqualTo(2);
            assertThat(rs.hasNext()).isFalse();
        }
    }

    @Test
    void csvStreamsWithHeaderRow(@TempDir Path tmp) throws Exception {
        Path p = tmp.resolve("a.csv");
        Files.writeString(p, "id,name\n1,alice\n2,bob\n");
        var parser = new CsvPayloadParser(om);
        try (RowStream rs = parser.openRowStream(p)) {
            var first = rs.next();
            assertThat(first).containsEntry("id", "1").containsEntry("name", "alice");
        }
    }
}
```

- [ ] **Step A3.5: Run test — expect PASS after implementations are in place**

```bash
cd server && mvn test -pl data-talk-application -Dtest=RowStreamTest -q
```

- [ ] **Step A3.6: Rewire `IngestionExecutor.executeBatchInsert`**

Replace the body with:

```java
private int executeBatchInsert(ConnectionRecord cr, String insertSql, Path payloadPath,
                                PayloadFormat format, List<MappingColumn> columns, int batchSize,
                                String jobId, String sessionId) throws Exception {
    List<MappingColumn> active = columns.stream().filter(c -> !c.skip()).toList();
    if (active.isEmpty()) return 0;

    String url = JdbcUrlBuilder.build(cr);
    String password = connService.decryptPassword(cr.id());

    PayloadParser parser = parsers.get(format);
    if (parser == null) throw new UnsupportedOperationException("unsupported format: " + format);

    int rowsInserted = 0;
    int sinceLastEmit = 0;
    try (Connection conn = DriverManager.getConnection(url, cr.username(), password);
         PreparedStatement ps = conn.prepareStatement(insertSql);
         RowStream rs = parser.openRowStream(payloadPath)) {
        conn.setAutoCommit(false);
        int batchCount = 0;
        while (rs.hasNext()) {
            Map<String, Object> row = rs.next();
            for (int c = 0; c < active.size(); c++) {
                MappingColumn col = active.get(c);
                Object val = row.get(col.targetName());
                if (val == null) val = row.get(col.sourcePath());
                ps.setObject(c + 1, val);
            }
            ps.addBatch();
            rowsInserted++;
            batchCount++;
            if (batchCount % batchSize == 0) {
                ps.executeBatch();
                conn.commit();
                publisher.publish(sessionId,
                    new DtEvent.IngestionWriteProgress(jobId, rowsInserted, -1));
                sinceLastEmit = 0;
            } else {
                sinceLastEmit++;
            }
        }
        if (sinceLastEmit > 0) {
            ps.executeBatch();
            conn.commit();
        }
    }
    return rowsInserted;
}
```

Remove the now-unused `parseRows` and `parseCsvLine` private helpers from `IngestionExecutor`.

- [ ] **Step A3.7: Compile + run impacted tests**

```bash
cd server && mvn -pl data-talk-application,data-talk-adapter -am test -Dtest='Ingestion*Test,RowStreamTest' -q
```
Expected: green.

- [ ] **Step A3.8: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/ingestion/parser/RowStream.java \
        server/data-talk-application/src/main/java/com/datatalk/application/ingestion/parser/PayloadParser.java \
        server/data-talk-application/src/main/java/com/datatalk/application/ingestion/parser/*PayloadParser.java \
        server/data-talk-application/src/main/java/com/datatalk/application/ingestion/IngestionExecutor.java \
        server/data-talk-application/src/test/java/com/datatalk/application/ingestion/parser/RowStreamTest.java
git commit -m "perf(ingestion): A3 — streaming RowStream parsers; executor INSERT is O(1) memory"
```

---

### Phase A gate

- [ ] **Step A.G.1: Full backend verify**

```bash
cd server && mvn clean verify
```
Expected: BUILD SUCCESS, all existing tests pass, new tests pass.

- [ ] **Step A.G.2: Frontend type + impacted vitest**

```bash
cd client && npx tsc --noEmit && npx vitest run features/ingestion
```

- [ ] **Step A.G.3: Marker commit**

```bash
git commit --allow-empty -m "chore(ingestion): Phase A closure complete (mapping API + events + streaming)"
```

---

## Phase B — Skill deployment

### Task B1: Maven-assembly descriptor + pom execution + version resource

**Files:**
- Create: `server/data-talk-infrastructure/src/assembly/data-ingestion.xml`
- Create: `server/data-talk-infrastructure/src/main/resources/opencode/skills/data-ingestion.version`
- Modify: `server/data-talk-infrastructure/pom.xml`

- [ ] **Step B1.1: Read existing bezel descriptor as template**

```bash
cat server/data-talk-infrastructure/src/assembly/bezel.xml
```

- [ ] **Step B1.2: Create `data-ingestion.xml`**

```xml
<assembly xmlns="http://maven.apache.org/ASSEMBLY/2.1.1"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/ASSEMBLY/2.1.1
                              http://maven.apache.org/xsd/assembly-2.1.1.xsd">
  <id>data-ingestion</id>
  <formats>
    <format>tar.gz</format>
  </formats>
  <includeBaseDirectory>false</includeBaseDirectory>
  <fileSets>
    <fileSet>
      <directory>${project.basedir}/src/main/resources/opencode/skills-src/data-ingestion</directory>
      <outputDirectory>/</outputDirectory>
      <excludes>
        <exclude>**/tmp/**</exclude>
        <exclude>**/.DS_Store</exclude>
      </excludes>
    </fileSet>
  </fileSets>
</assembly>
```

- [ ] **Step B1.3: Create version marker resource**

`server/data-talk-infrastructure/src/main/resources/opencode/skills/data-ingestion.version` containing exactly:

```
1.0.0
```

- [ ] **Step B1.4: Patch `pom.xml`** — duplicate the `vendor-bezel` execution

In `server/data-talk-infrastructure/pom.xml`, inside `<plugin><artifactId>maven-assembly-plugin</artifactId><executions>`, after the existing `vendor-bezel` execution add:

```xml
<execution>
    <id>vendor-data-ingestion</id>
    <phase>process-resources</phase>
    <goals><goal>single</goal></goals>
    <configuration>
        <descriptors>
            <descriptor>src/assembly/data-ingestion.xml</descriptor>
        </descriptors>
        <finalName>data-ingestion</finalName>
        <appendAssemblyId>false</appendAssemblyId>
        <outputDirectory>${project.build.outputDirectory}/opencode/skills</outputDirectory>
        <tarLongFileMode>posix</tarLongFileMode>
    </configuration>
</execution>
```

- [ ] **Step B1.5: Verify the tarball is produced**

```bash
cd server && mvn -pl data-talk-infrastructure -am process-resources -q && \
  ls -la server/data-talk-infrastructure/target/classes/opencode/skills/
```
Expected: both `bezel.tar.gz` and `data-ingestion.tar.gz` listed.

- [ ] **Step B1.6: Commit**

```bash
git add server/data-talk-infrastructure/src/assembly/data-ingestion.xml \
        server/data-talk-infrastructure/src/main/resources/opencode/skills/data-ingestion.version \
        server/data-talk-infrastructure/pom.xml
git commit -m "build(ingestion): B1 — maven-assembly descriptor + version resource for data-ingestion skill"
```

---

### Task B2: `OpenCodeBinaryResolver.ensureDataIngestionSkill` + wiring + test

**Depends on:** B1 (the tarball must exist on the classpath for the test fixture).

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolver.java`
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeProcessManager.java`
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolverDataIngestionTest.java`

- [ ] **Step B2.1: Read `OpenCodeBinaryResolverBezelTest` to mirror exactly**

```bash
cat server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolverBezelTest.java
```

- [ ] **Step B2.2: Write failing `OpenCodeBinaryResolverDataIngestionTest`**

Mirror the bezel test verbatim except substitute `bezel` → `data-ingestion`, `BEZEL_*` → `DATA_INGESTION_*`, `ensureBezelSkill` → `ensureDataIngestionSkill`. Cover at minimum:
- fresh project root → extracts skill files
- marker already matches embedded version → skipped (no re-extraction)
- marker mismatch → re-extracts (deletes old skill dir first)
- missing classpath resource → silent no-op

- [ ] **Step B2.3: Run — expect compile failure (`ensureDataIngestionSkill` missing)**

```bash
cd server && mvn test -pl data-talk-infrastructure -Dtest=OpenCodeBinaryResolverDataIngestionTest -q
```

- [ ] **Step B2.4: Add constants + method to `OpenCodeBinaryResolver`**

After the `BEZEL_MARKER` line add:

```java
static final String DATA_INGESTION_RESOURCE = "opencode/skills/data-ingestion.tar.gz";
static final String DATA_INGESTION_VERSION_RESOURCE = "opencode/skills/data-ingestion.version";
static final String DATA_INGESTION_MARKER = ".data-ingestion-installed";
```

After `ensureBezelSkill` add:

```java
/**
 * Mirror of {@link #ensureBezelSkill} for the data-ingestion skill.
 */
public void ensureDataIngestionSkill(Path projectRoot) {
    Path opencodeDir = projectRoot.resolve(".opencode");
    Path skillDir    = opencodeDir.resolve("skills").resolve("data-ingestion");
    Path marker      = opencodeDir.resolve(DATA_INGESTION_MARKER);

    String embeddedVersion = readEmbeddedDataIngestionVersion();
    if (Files.exists(marker) && Files.isDirectory(skillDir)) {
        try {
            if (embeddedVersion.equals(Files.readString(marker).trim())) {
                return;
            }
        } catch (IOException ignored) { /* fall through */ }
        deleteRecursively(skillDir);
    }

    try (InputStream in = openClasspathResource(DATA_INGESTION_RESOURCE)) {
        if (in == null) return;
        Files.createDirectories(skillDir);
        extractDepsTarGz(in, skillDir);
        Files.createDirectories(opencodeDir);
        Files.writeString(marker, embeddedVersion);
    } catch (Exception e) {
        log.warn("Failed to extract data-ingestion skill: {}", e.getMessage());
    }
}

String readEmbeddedDataIngestionVersion() {
    try (InputStream in = openClasspathResource(DATA_INGESTION_VERSION_RESOURCE)) {
        if (in == null) return "";
        return new String(in.readAllBytes()).trim();
    } catch (IOException e) {
        return "";
    }
}
```

- [ ] **Step B2.5: Wire into `OpenCodeProcessManager`**

Grep for `ensureBezelSkill(`:

```bash
grep -n "ensureBezelSkill" server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeProcessManager.java
```

Directly after each `ensureBezelSkill(projectRoot);` invocation add:

```java
resolver.ensureDataIngestionSkill(projectRoot);
```

- [ ] **Step B2.6: Run new test — expect PASS**

```bash
cd server && mvn test -pl data-talk-infrastructure -Dtest=OpenCodeBinaryResolverDataIngestionTest -q
```

- [ ] **Step B2.7: Run full infra test suite**

```bash
cd server && mvn test -pl data-talk-infrastructure -q
```
Expected: green.

- [ ] **Step B2.8: Commit**

```bash
git add server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolver.java \
        server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeProcessManager.java \
        server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolverDataIngestionTest.java
git commit -m "feat(ingestion): B2 — OpenCodeBinaryResolver.ensureDataIngestionSkill + startup wiring"
```

---

### Task B3: AGENTS.md contract test

**Files:**
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentsTemplateContractTest.java`

- [ ] **Step B3.1: Write the test**

```java
package com.datatalk.adapter.agents;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AgentsTemplateContractTest {

    @Test
    void agentsTemplateContainsDataIngestionSection() throws IOException {
        String content = loadClasspath("/agents/AGENTS.md");
        assertThat(content)
            .contains("## Data Ingestion (skill: data-ingestion)")
            .contains("skills/data-ingestion/SKILL.md")
            .contains("`datatalk_http_request`")
            .contains("`datatalk_infer_ingestion_schema`")
            .contains("`datatalk_create_ingestion_table`")
            .contains("`datatalk_ingest_payload`")
            .contains("INGESTION_SSRF_BLOCKED")
            .contains("INGESTION_DIALECT_UNSUPPORTED")
            .contains("INGESTION_TOKEN_INVALID");
    }

    @Test
    void agentsTemplateContainsStageTabDigestPlaceholder() throws IOException {
        String content = loadClasspath("/agents/AGENTS.md");
        assertThat(content).contains("{{STAGE_TAB_DIGEST}}");
    }

    private static String loadClasspath(String path) throws IOException {
        try (InputStream in = AgentsTemplateContractTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing classpath resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

- [ ] **Step B3.2: Run — expect PASS (content already in place from P6)**

```bash
cd server && mvn test -pl data-talk-adapter -Dtest=AgentsTemplateContractTest -q
```

- [ ] **Step B3.3: Commit**

```bash
git add server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentsTemplateContractTest.java
git commit -m "test(ingestion): B3 — AGENTS.md contract guards data-ingestion section drift"
```

---

### Phase B gate

- [ ] **Step B.G.1: Backend verify**

```bash
cd server && mvn clean verify
```

- [ ] **Step B.G.2: Marker**

```bash
git commit --allow-empty -m "chore(ingestion): Phase B closure (skill packaging + wiring + contract test)"
```

---

## Phase C — Verification reinforcement

### Task C1: Testcontainers IT — MySQL 8.0 + Postgres 16

**Files:**
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/ingestion/IngestionExecutorMysqlIT.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/ingestion/IngestionExecutorPostgresIT.java`

These are integration tests (suffix `IT`) so Surefire skips them — they run under Failsafe in `mvn verify`. Mirror the pattern of `SqlExecuteControllerIT`.

- [ ] **Step C1.1: Read existing IT for pattern**

```bash
sed -n '1,80p' server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SqlExecuteControllerIT.java
```

- [ ] **Step C1.2: Author `IngestionExecutorMysqlIT`**

```java
package com.datatalk.adapter.ingestion;

import com.datatalk.application.ingestion.IngestionExecutor;
import com.datatalk.application.ingestion.repository.IngestionJobRepository;
import com.datatalk.domain.ingestion.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = com.datatalk.adapter.App.class)
class IngestionExecutorMysqlIT {

    @Container
    static MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("ingest_it")
        .withUsername("root")
        .withPassword("root");

    @Autowired IngestionExecutor executor;
    @Autowired IngestionJobRepository jobRepo;
    // ... + ConnectionRepository, FileArtifactRepository, IngestionConfirmedTokenStore

    @Test
    void createTableThenIngestThreeRowsAgainstMysql(@TempDir Path tmp) throws Exception {
        // 1. write payload file (3 JSON rows) into tmp/payload.json
        Path payload = tmp.resolve("payload.json");
        Files.writeString(payload, "[{\"id\":1,\"name\":\"alice\"},{\"id\":2,\"name\":\"bob\"},{\"id\":3,\"name\":\"carol\"}]");

        // 2. insert a connection row pointing at MYSQL.getJdbcUrl(), kind=mysql
        // 3. insert an ingestion_job + ingestion_mapping with two columns (id INTEGER_64, name STRING_64)
        // 4. insert a file_artifact for the payload
        // 5. issue a confirmed token via tokenStore.issue + verify mapping hash
        // 6. call executor.createTable(jobId, connId, null, "ingest_test", hash, tokenId, sessionId=null)
        // 7. call executor.ingestPayload(jobId, batchSize=2, sessionId=null)
        // 8. assert returned IngestResult.rowsInserted == 3
        // 9. open a fresh JDBC connection to MYSQL and SELECT COUNT(*) FROM ingest_test → 3
        //    and SELECT name FROM ingest_test WHERE id=2 → "bob"
    }

    @Test
    void dialectAdapterRoundTripsAllSupportedTypes(@TempDir Path tmp) throws Exception {
        // similar but with 6 columns covering BOOLEAN, INTEGER_32, INTEGER_64, DECIMAL, DATE, TIMESTAMP
        // assert that SHOW COLUMNS returns the expected dtype list
    }
}
```

Use Spring Boot test slicing to wire only the beans actually needed — minimise startup time. Persist the connection row directly via `JdbcTemplate` against the metadata DB to avoid going through REST.

- [ ] **Step C1.3: Author `IngestionExecutorPostgresIT`**

Same shape, `PostgreSQLContainer<>("postgres:16-alpine")`. Use `INFORMATION_SCHEMA.COLUMNS` for type round-trip assertions. Verify JSONB type for `JSON` inferred type.

- [ ] **Step C1.4: Run ITs**

```bash
cd server && mvn -pl data-talk-adapter verify -Dit.test='IngestionExecutorMysqlIT,IngestionExecutorPostgresIT' -DfailIfNoTests=false -q
```
Expected: 2 IT classes pass within ~120s (image pull + container start).

- [ ] **Step C1.5: Commit**

```bash
git add server/data-talk-adapter/src/test/java/com/datatalk/adapter/ingestion/IngestionExecutor*IT.java
git commit -m "test(ingestion): C1 — Testcontainers IT for IngestionExecutor on mysql:8.0 + postgres:16"
```

---

### Task C2: Playwright happy-path E2E

**Files:**
- Create: `client/tests/e2e/ingestion-happy-path.spec.ts`

Use existing E2E patterns (see `client/tests/e2e/real-db-smoke.spec.ts` for fixture setup). Mock the backend through `mcp__playwright__*` proxies if Tauri-in-Playwright is the established harness, or run against `npm run dev` with a stubbed REST layer.

- [ ] **Step C2.1: Inspect existing pattern**

```bash
ls client/tests/e2e/
head -100 client/tests/e2e/real-db-smoke.spec.ts
```

- [ ] **Step C2.2: Author `ingestion-happy-path.spec.ts`**

```ts
import { test, expect } from '@playwright/test'

// Mirror existing E2E setup: launches the Tauri dev server / served Vite build,
// signs in, opens the connection picker etc. See real-db-smoke.spec.ts.

test('AI fetch → mapping editor → confirm → completed', async ({ page }) => {
  // 1. preconditions:
  //    - one mysql/h2 connection exists (seed via API or fixture)
  //    - one ingestion credential exists (auth_scheme=none, name='public-api')
  // 2. simulate IngestionJobCreated SSE event by posting to /api/sse/test-fixture
  //    (if such a fixture endpoint exists; otherwise call the action via the
  //    OpenCode bridge mock). The Tab MUST auto-open in the Stage area.
  // 3. await page.getByRole('heading', { name: /ingestion.job/ }).toBeVisible()
  // 4. await page.getByTestId('mapping-row-$.id').toBeVisible()
  // 5. uncheck skip on one column → assert DDL preview updates
  // 6. click [Confirm and Ingest] → expect status badge transitions:
  //    fetched → confirmed → writing → completed
  // 7. assert final row count + target table name match
})

test('failed scenario surfaces userHint', async ({ page }) => {
  // simulate INGESTION_SSRF_BLOCKED outcome → assert FailedPhase renders
  // the user hint text from i18n key 'ingestion.error.ssrf_blocked'.
})
```

(Fill the bodies based on the project's actual E2E fixture conventions — discover them by reading the smoke spec and `client/tests/e2e/fixtures/`.)

- [ ] **Step C2.3: Run**

```bash
cd client && npx playwright test ingestion-happy-path.spec.ts --reporter=line
```

- [ ] **Step C2.4: If failures expose product bugs**

Per CLAUDE.md "BUG Tracking Gate": create one BUG file per defect under `docs/bugs/`, register in `docs/bugs/index.md` with `status: open`. Even N=0 must be reported in the final summary.

- [ ] **Step C2.5: Commit**

```bash
git add client/tests/e2e/ingestion-happy-path.spec.ts
git commit -m "test(ingestion): C2 — Playwright E2E happy path + SSRF error scenario"
```

---

### Phase C gate

- [ ] **Step C.G.1: Full backend verify**

```bash
cd server && mvn clean verify
```

- [ ] **Step C.G.2: Full frontend pipeline**

```bash
cd client && npx tsc --noEmit && npx vitest run && npx playwright test ingestion-happy-path.spec.ts
```

- [ ] **Step C.G.3: Marker**

```bash
git commit --allow-empty -m "chore(ingestion): Phase C closure (Testcontainers IT + E2E)"
```

---

## Phase D — Documentation housekeeping

Sequential. One subagent, ~10 file edits.

### Task D1: Tick original plan checkboxes

**Files:**
- Modify: `docs/exec-plans/2026-05-12-external-data-ingestion-skills-plan.md`

- [ ] **Step D1.1: For every `- [ ] **Step X.Y.Z` in the original plan whose work is now done (P1–P6 plus everything closed by this follow-up plan), change `- [ ]` → `- [x]`.** Leave a `- [ ]` only on items genuinely deferred (e.g. items in this plan's "Out of scope" list — annotate them with `(deferred — see followup plan)`).

- [ ] **Step D1.2: Add a `## Status` block at the top of the plan**

```markdown
## Status — 2026-05-12 (post-followup)

| Phase | Status | Notes |
|-------|--------|-------|
| P1 — DB + Domain + Credential | ✅ completed | Commits 5fcc9256 |
| P2 — HTTP Fetcher + Payload | ✅ completed | bde74cc0 |
| P3 — Schema Inferrer | ✅ completed | fcf28e6e |
| P4 — Frontend Tab | ✅ completed | 716f8b46 |
| P5 — DDL + Executor | ✅ completed | 71afbecd; mapping hash + streaming + events delivered in follow-up |
| P6 — Skill Bundle + AGENTS | ✅ completed | f2f16029; resolver wiring + assembly + contract test delivered in follow-up |
| Follow-up gap-closure | ✅ completed | See [2026-05-12-ingestion-skills-followup-plan.md](2026-05-12-ingestion-skills-followup-plan.md) |
```

- [ ] **Step D1.3: Commit**

```bash
git add docs/exec-plans/2026-05-12-external-data-ingestion-skills-plan.md
git commit -m "docs(ingestion): D1 — tick original plan checkboxes; add closure status block"
```

### Task D2: Move both plans into `## 已完成计划`

**Files:**
- Modify: `docs/exec-plans/index.md`

- [ ] **Step D2.1: Delete the ingestion plan row from `## 活跃计划`**

Locate the row beginning with `| [External Data Ingestion via Skills — Generic HTTP Scaffolding]`. Remove that single row.

- [ ] **Step D2.2: Append two rows in `## 已完成计划`** — original plan + this follow-up

```markdown
| [External Data Ingestion via Skills — Generic HTTP Scaffolding](./2026-05-12-external-data-ingestion-skills-plan.md) | 2026-05-12 | 2026-05-12 | Task 10 三期首份 child plan — 6 Phase 全部完成（commits 5fcc9256 → f2f16029）+ follow-up gap-closure。Day-1 写入 dialect: mysql/postgresql/h2/sqlite。15 个其余 first-class kind 走独立 follow-up child plan。 |
| [Ingestion Follow-up Closure](./2026-05-12-ingestion-skills-followup-plan.md) | 2026-05-12 | 2026-05-12 | MVP closure：API surface `mapping` + 服务端 `mappingHash`、DtEvent publishing、streaming RowStream parser、`OpenCodeBinaryResolver.ensureDataIngestionSkill` + assembly tarball、AGENTS.md contract test、Testcontainers MySQL/PG IT、Playwright happy-path E2E、文档收尾。 |
```

(Adjust column count to match the index's existing schema.)

- [ ] **Step D2.3: Commit**

```bash
git add docs/exec-plans/index.md
git commit -m "docs(ingestion): D2 — move ingestion plans to Completed in exec-plans index"
```

### Task D3: Update `db-schema.md`

**Files:**
- Modify: `docs/generated/db-schema.md`

- [ ] **Step D3.1: Read existing schema doc**

```bash
sed -n '1,40p' docs/generated/db-schema.md
```

- [ ] **Step D3.2: Append three tables + the `file_artifact` CHECK extension note**

After the last existing table block, add:

```markdown
### `ingestion_credential`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | TEXT | PK | UUID, `cred_` prefix |
| name | TEXT | NOT NULL UNIQUE | user-visible label |
| auth_scheme | TEXT | NOT NULL CHECK IN (none, bearer, api_key_header, api_key_query, basic) | |
| config_non_secret | TEXT | NULL | JSON map — header names, query keys, etc. |
| vault_id | TEXT | NULL FK → ingestion_vault_store(id) | NULL when scheme=none |
| created_at | INTEGER | NOT NULL | epoch millis |
| updated_at | INTEGER | NOT NULL | |

### `ingestion_job`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | TEXT | PK | UUID, `job_` prefix |
| source_url | TEXT | NOT NULL | |
| status | TEXT | NOT NULL CHECK IN (fetching, fetched, mapped, confirmed, writing, completed, failed, cancelled) | |
| payload_format | TEXT | NULL CHECK IN (json, jsonl, csv, html) | NULL until fetched |
| payload_artifact_id | TEXT | NULL FK → file_artifact(id) | |
| connection_id | TEXT | NULL FK → connection(id) | |
| target_schema | TEXT | NULL | |
| target_table | TEXT | NULL | |
| mapping_json | TEXT | NULL | serialized IngestionMapping |
| mapping_hash | TEXT | NULL | SHA-256 hex, computed server-side at confirm |
| row_count | INTEGER | NULL | from inference |
| rows_inserted | INTEGER | NULL | from execution |
| bytes_fetched | INTEGER | NULL | |
| error_message | TEXT | NULL | last error if status in (failed, cancelled) |
| created_at | INTEGER | NOT NULL | |
| updated_at | INTEGER | NOT NULL | |
| completed_at | INTEGER | NULL | |

### `ingestion_vault_store`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | TEXT | PK | UUID, `vault_` prefix |
| ciphertext | BLOB | NOT NULL | AES-256-GCM |
| nonce | BLOB | NOT NULL | 12-byte GCM nonce |
| created_at | INTEGER | NOT NULL | |

### `file_artifact.kind` CHECK extension (V20)

V20 extends the `kind` CHECK to allow `ingestion_payload`. This is the only artifact kind that lives on the local filesystem under `~/.data-talk/ingestion/<jobId>/payload.<ext>` rather than under the standard artifact directory.
```

- [ ] **Step D3.3: Commit**

```bash
git add docs/generated/db-schema.md
git commit -m "docs(ingestion): D3 — db-schema.md adds ingestion_{credential,job,vault_store} + file_artifact kind"
```

### Task D4: Flip product-spec status

**Files:**
- Modify: `docs/product-specs/2026-05-12-external-data-ingestion-skills-design.md`
- Modify: `docs/product-specs/index.md`

- [ ] **Step D4.1: Open the design doc**

Find the `| Phase | 三期 — 首份 child spec…` line near the top metadata table. Change the cell to `三期 — 首份 child spec (shipped 2026-05-12)`.

- [ ] **Step D4.2: Update product-specs index**

Open `docs/product-specs/index.md`, locate the row referencing this spec under §8 "Individual Design Documents", append `· shipped 2026-05-12` after the date.

- [ ] **Step D4.3: Commit**

```bash
git add docs/product-specs/2026-05-12-external-data-ingestion-skills-design.md docs/product-specs/index.md
git commit -m "docs(ingestion): D4 — flip Task 10 三期 status to shipped"
```

### Task D5: Register conventions in CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step D5.1: Knowledge-base table row**

Under `## Knowledge Base Navigation (docs/)`, in the table, add (alphabetised near existing related rows):

```markdown
| Ingestion plan (closed)      | [docs/exec-plans/2026-05-12-external-data-ingestion-skills-plan.md](docs/exec-plans/2026-05-12-external-data-ingestion-skills-plan.md) |
| Ingestion follow-up plan     | [docs/exec-plans/2026-05-12-ingestion-skills-followup-plan.md](docs/exec-plans/2026-05-12-ingestion-skills-followup-plan.md) |
```

- [ ] **Step D5.2: Append Ingestion Artifact Path convention** under `## Working Rules`

```markdown
### Ingestion Artifact Path Convention

- HTTP-fetched payloads are persisted under `~/.data-talk/ingestion/<jobId>/payload.<json|jsonl|csv|html>` (separate from the standard artifact directory). They are registered in `file_artifact` with `kind=ingestion_payload` and `physical_path` set to the absolute filesystem path
- The 500MB cap is enforced by `IngestionPayloadFetcher` before final atomic rename — partial writes go to `payload.staging` and are deleted on overrun. Do not attempt to short-circuit the staging step
- `IngestionConfirmedToken` is in-memory only (`ConcurrentHashMap`, 5-min TTL, single-use). It is not persisted across server restarts — clients must re-confirm after a restart
```

- [ ] **Step D5.3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(ingestion): D5 — CLAUDE.md adds ingestion plan refs + artifact path / token convention"
```

### Task D6: Final summary commit + verification

- [ ] **Step D6.1: Verify the housekeeping is internally consistent**

```bash
# Plan checkboxes ticked
grep -c "^- \[x\]" docs/exec-plans/2026-05-12-external-data-ingestion-skills-plan.md
grep -c "^- \[ \]" docs/exec-plans/2026-05-12-external-data-ingestion-skills-plan.md   # should be 0 (or only deferred items)

# Plan moved out of Active
grep -A1 "## 活跃计划" docs/exec-plans/index.md | head -20
grep "ingestion-skills-plan\|ingestion-skills-followup" docs/exec-plans/index.md
```

- [ ] **Step D6.2: Final marker commit**

```bash
git commit --allow-empty -m "chore(ingestion): Phase D housekeeping complete — ingestion epic shipped"
```

---

## Self-Review

**Spec coverage:** This follow-up plan covers the exact set of gaps documented in the project status briefing at session start:

| Gap from briefing | Plan Task |
|-------------------|-----------|
| MappingEditor not seeded from infer result | A1 (controller exposes `mapping`; store hydrates) |
| `mappingHash` empty-string bypass | A1 (server computes hash; executor cross-checks) |
| `IngestionExecutor` not publishing DtEvents | A2 (`IngestionEventPublisher` + 5 publish sites) |
| `Files.readString` OOM risk | A3 (streaming `RowStream`) |
| T6.2 maven-assembly | B1 |
| T6.3 `ensureDataIngestionSkill` | B2 |
| T6.4 AGENTS.md contract test | B3 |
| T5.3 Testcontainers IT | C1 |
| T6.6 Playwright E2E | C2 |
| T6.8 doc housekeeping | D1–D6 |
| DDL `SqlStatementGuard` | Out of scope — documented under "Out of scope" with justification; D5 registers the deviation |

**Placeholder scan:** No `TBD` / `TODO` / `implement later`. Two specific spots use `// fill the bodies` instruction style (C1.2 step 2-9 of the IT body; C2.2 spec scaffolding) — these are necessary because the test fixture wiring is project-specific. The plan tells the executor what to verify and points at the existing files to mirror; the prescriptive 2-5min steps cover write-test → run-fail → implement → run-pass → commit.

**Type consistency:**
- `MappingHash.compute(IngestionMapping) → String` used identically in A1.3, A1.10 (inferrer), A1.11 (controller), A1.14 (executor)
- `IngestionEventPublisher.publish(String sessionId, DtEvent event)` used identically in A2.3, A2.5, A2.6, A2.7
- `RowStream extends AutoCloseable, Iterator<Map<String, Object>>` declared in A3.1, implemented in A3.3, consumed in A3.6 via `try-with-resources` — all match
- `IngestionExecutor.createTable(...)` extended with `String sessionId` in A2.7 — A1.14 inserts the hash check before the existing `tokenStore.consume`, both edits are confined to that single method and do not collide. Reviewed manually
- `IngestionJob` record gains `String mappingHash` component (A1.8) — every constructor call site (`JdbcIngestionJobRepository.findById/list/save`, controller `jobToMap`, test factories) is enumerated in A1.6–A1.9 with a compile probe gate

**Risk note for parallel dispatch:** Tasks A1, A2, A3 all eventually touch `IngestionExecutor.java`. The Coordination Note before Phase A spells out the resolution: dispatch A2+A3 to one subagent (sequential within), and A1 to a second subagent in parallel. Final merge of the three branches is a deterministic 3-way merge — no semantic conflicts because A1 adds a defensive hash check, A2 adds publishing, A3 rewrites the batch loop, all at distinct points in the file.

---

## Execution Handoff

**Plan complete and saved to `docs/exec-plans/2026-05-12-ingestion-skills-followup-plan.md`.**

Recommended dispatch:

1. **Phase A — parallel pair**:
   - Subagent #1: Task A1 in full (FE + BE pair).
   - Subagent #2: Tasks A2 → A3 sequentially (both touch `IngestionExecutor.java`; same agent avoids merge churn).
   - When both report green, run consolidated `mvn clean verify` + `tsc --noEmit` before Phase B.
2. **Phase B — staggered**:
   - Subagent #3: Task B1 (assembly tarball).
   - Subagent #4: Task B3 (contract test, independent of B1).
   - When B1 lands → Subagent #5: Task B2 (resolver + wiring + test against the now-existing tarball).
3. **Phase C — parallel pair**:
   - Subagent #6: Task C1 (Testcontainers ITs).
   - Subagent #7: Task C2 (Playwright E2E).
4. **Phase D — single sequential agent**: Tasks D1 → D6 in order.

Use `superpowers:subagent-driven-development` for the dispatch and two-stage review pattern.
