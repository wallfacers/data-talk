# AI Text-to-Chart Fence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship AI text-to-chart via markdown ` ```chart ` fence with streaming skeleton placeholder, inline ECharts rendering, and opt-in "Open to Workbench" promotion that reuses the existing artifact pipeline.

**Architecture:** Replace the recharts-based `ChartArtifact` and translation layer with a shared `echarts-for-react` renderer. Chat's `markdown.tsx` grows a `decorateChartBlocks` pass that hands `language-chart` fences to a React root mounting `ChartBlock`. The `datatalk.render_chart` action is refactored to delegate to a new `ChartArtifactService` (application layer) which also backs a new REST endpoint `POST /api/sessions/{id}/artifacts/chart`; both paths publish `DtEvent.OntologyUpdated` with `kind: "chart"` patches. Artifact persistence is extended with `origin_message_id` / `origin_part_id` columns so the "已在工作台" button state derives from server-held artifacts without any new client persistence.

**Tech Stack:** Spring Boot 3.5 / Java 21 / JdbcTemplate / Flyway; React 19 + Vite + Tauri v2; `echarts ^5` + `echarts-for-react ^3` (new); marked + morphdom (existing); Zustand stores; JUnit 5 + AssertJ + Spring MockMvc; vitest + @testing-library/react.

**Spec:** [`docs/product-specs/2026-04-23-ai-text-to-chart-fence-design.md`](../product-specs/2026-04-23-ai-text-to-chart-fence-design.md)

**Design Inputs cited:** `client/DESIGN.md` — `components.chart.focus/compare/grid`, dual-theme contract, no raw primitives in feature code, `typography.ui-sm`/`mono-sm`, `motion.fast`, `prefers-reduced-motion` honored.

---

## File Map

Backend — create:

- `server/data-talk-infrastructure/src/main/resources/db/migration/V11__artifact_origin.sql`
- `server/data-talk-application/src/main/java/com/datatalk/application/chart/ChartArtifactService.java`
- `server/data-talk-application/src/test/java/com/datatalk/application/chart/ChartArtifactServiceTest.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/rest/ChartArtifactController.java`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/rest/ChartArtifactControllerTest.java`

Backend — modify:

- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ArtifactRecord.java` — +2 fields
- `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ArtifactRepository.java` — insert + mapper + origin filter
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/RenderChartAction.java` — delegate to service; optional `sourceArtifactId`; empty `sideEffects`
- `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/RenderChartActionTest.java` — update assertions
- `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` — default path & render_chart repositioning

Frontend — create:

- `client/src/features/chat/components/markdown/chart-theme.ts`
- `client/src/features/chat/components/markdown/chart-renderer.tsx`
- `client/src/features/chat/components/markdown/chart-block.tsx`
- `client/src/features/chat/components/markdown/chart-expand-modal.tsx`
- `client/src/features/chat/components/markdown/__tests__/chart-theme.test.ts`
- `client/src/features/chat/components/markdown/__tests__/chart-block.test.tsx`
- `client/src/services/artifacts/promote-chart.ts`
- `client/src/services/artifacts/__tests__/promote-chart.test.ts`

Frontend — modify:

- `client/package.json` — add `echarts`, `echarts-for-react`; remove `recharts`
- `client/src/styles/globals.css` — +3 vars × 2 themes
- `client/src/services/channel/event-reducer.ts` — `Artifact` +2 fields
- `client/src/services/channel/use-channel.ts` — propagate origin fields from patch
- `client/src/features/chat/components/markdown/markdown.tsx` — `decorateChartBlocks` + root lifecycle
- `client/src/features/chat/components/markdown/markdown.css` — skeleton keyframes
- `client/src/features/chat/components/markdown/__tests__/markdown.test.tsx` — chart-block lifecycle cases
- `client/src/features/ontology/components/chart-artifact.tsx` — rewrite to `ChartRenderer`

Frontend — delete:

- `client/src/features/ontology/echarts-to-recharts.ts`
- `client/src/components/ui/chart.tsx`

---

## Task Ordering & Batching

Tasks are numbered to allow subagent-driven execution. Dependencies (must be sequential): T1 → T2 → T3 → T4. T5 (AGENTS.md) and T6 (deps) can run in parallel with T1-T4. T7-T12 all depend on T6 (deps installed). T13 depends on T10 (ChartRenderer). T14 depends on T2+T8 (event shape). T15 is final smoke.

---

## Task 1: Extend Artifact persistence with origin columns

**Files:**
- Create: `server/data-talk-infrastructure/src/main/resources/db/migration/V11__artifact_origin.sql`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ArtifactRecord.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ArtifactRepository.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/persistence/ArtifactRepositoryTest.java` (check if exists; if not create)

- [x] **Step 1.1: Write the failing repository test**

Check if `ArtifactRepositoryTest` exists. If not, create; if yes, add the cases.

```java
package com.datatalk.application.persistence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import com.datatalk.application.testing.InMemoryDataTalkDb;
import org.springframework.jdbc.core.JdbcTemplate;

class ArtifactRepositoryOriginTest {

    @Test
    void insertAndReadBackOriginFields() {
        JdbcTemplate jdbc = InMemoryDataTalkDb.freshJdbc();          // existing helper in test module
        jdbc.update("INSERT INTO sessions(id, created_at) VALUES('s1', 0)");
        ArtifactRepository repo = new ArtifactRepository(jdbc);

        repo.insert(new ArtifactRecord(
            "art_1", 1, "s1", "chart", "call_1",
            "inline:{}", 2, null, null, false, 1_000L,
            "msg_7", "part_2"
        ));

        var got = repo.findLatestById("art_1").orElseThrow();
        assertThat(got.originMessageId()).isEqualTo("msg_7");
        assertThat(got.originPartId()).isEqualTo("part_2");
    }

    @Test
    void nullOriginFieldsRoundTripAsNull() {
        JdbcTemplate jdbc = InMemoryDataTalkDb.freshJdbc();
        jdbc.update("INSERT INTO sessions(id, created_at) VALUES('s1', 0)");
        ArtifactRepository repo = new ArtifactRepository(jdbc);

        repo.insert(new ArtifactRecord(
            "art_2", 1, "s1", "chart", "call_1",
            "inline:{}", 2, null, null, false, 1_000L,
            null, null
        ));

        var got = repo.findLatestById("art_2").orElseThrow();
        assertThat(got.originMessageId()).isNull();
        assertThat(got.originPartId()).isNull();
    }
}
```

(If `InMemoryDataTalkDb` helper name differs, grep for existing repo tests' setup in `server/data-talk-application/src/test/java/com/datatalk/application/persistence/` and mirror their DB bootstrapping; do NOT invent a new helper.)

- [x] **Step 1.2: Run and see it fail**

```
cd server && mvn -q -pl data-talk-application test -Dtest=ArtifactRepositoryOriginTest
```

Expected: FAIL — compilation error because `ArtifactRecord` has only 11 fields, and/or runtime `no such column: origin_message_id`.

- [x] **Step 1.3: Write Flyway migration V11**

Create `server/data-talk-infrastructure/src/main/resources/db/migration/V11__artifact_origin.sql`:

```sql
ALTER TABLE artifacts ADD COLUMN origin_message_id TEXT;
ALTER TABLE artifacts ADD COLUMN origin_part_id TEXT;
CREATE INDEX idx_artifacts_origin ON artifacts(session_id, origin_message_id, origin_part_id);
```

- [x] **Step 1.4: Extend `ArtifactRecord` with origin fields**

Replace file content:

```java
package com.datatalk.application.persistence;

/**
 * Persistence record for an artifact snapshot.
 * <p>{@code originMessageId}/{@code originPartId} link chart artifacts back to the
 * chat message-part that produced them; used to derive the "已在工作台" button state.
 */
public record ArtifactRecord(
    String id,
    int version,
    String sessionId,
    String kind,
    String producedBy,
    String payloadRef,
    int payloadSize,
    String supersedesId,
    Integer supersedesVersion,
    boolean pinned,
    long createdAt,
    String originMessageId,
    String originPartId
) {}
```

- [x] **Step 1.5: Update `ArtifactRepository` insert + mapper**

```java
private static final RowMapper<ArtifactRecord> MAPPER = (rs, i) -> new ArtifactRecord(
    rs.getString("id"),
    rs.getInt("version"),
    rs.getString("session_id"),
    rs.getString("kind"),
    rs.getString("produced_by"),
    rs.getString("payload_ref"),
    rs.getInt("payload_size"),
    rs.getString("supersedes_id"),
    (Integer) rs.getObject("supersedes_ver"),
    rs.getInt("pinned") == 1,
    rs.getLong("created_at"),
    rs.getString("origin_message_id"),
    rs.getString("origin_part_id")
);

public void insert(ArtifactRecord a) {
    jdbc.update("""
        INSERT INTO artifacts(id, version, session_id, kind, produced_by, payload_ref, payload_size,
          supersedes_id, supersedes_ver, pinned, created_at, origin_message_id, origin_part_id)
        VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        a.id(), a.version(), a.sessionId(), a.kind(), a.producedBy(),
        a.payloadRef(), a.payloadSize(), a.supersedesId(), a.supersedesVersion(),
        a.pinned() ? 1 : 0, a.createdAt(), a.originMessageId(), a.originPartId()
    );
}
```

- [x] **Step 1.6: Fix all other `new ArtifactRecord(...)` call sites**

Grep and update with trailing `null, null`:

```
grep -rn "new ArtifactRecord(" server --include='*.java'
```

Known sites at time of planning:
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/RenderChartAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`
- `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/LayoutErdAction.java`
- any tests that construct one

Add `, null, null` at the end of each. (For RenderChartAction this is temporary — T3 rewrites its body.)

- [x] **Step 1.7: Run the new tests to verify they pass**

```
cd server && mvn -q -pl data-talk-application test -Dtest=ArtifactRepositoryOriginTest
```

Expected: PASS.

- [x] **Step 1.8: Run full data-talk-application test suite + adapter compile to catch broken callers**

```
cd server && mvn -q -pl data-talk-application test
cd server && mvn -q -pl data-talk-adapter -am compile
```

Expected: all PASS / compile clean.

- [x] **Step 1.9: Commit**

```
git add server/data-talk-infrastructure/src/main/resources/db/migration/V11__artifact_origin.sql \
        server/data-talk-application/src/main/java/com/datatalk/application/persistence \
        server/data-talk-application/src/test/java/com/datatalk/application/persistence \
        server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions
git commit -m "feat(artifact): add origin_message_id/origin_part_id columns and record fields"
```

---

## Task 2: Introduce `ChartArtifactService` with event publishing

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/chart/ChartArtifactService.java`
- Create: `server/data-talk-application/src/test/java/com/datatalk/application/chart/ChartArtifactServiceTest.java`

- [x] **Step 2.1: Write the failing service test**

```java
package com.datatalk.application.chart;

import com.datatalk.application.channel.IdGenerator;
import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.session.SessionBus;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChartArtifactServiceTest {

    private ArtifactRepository artifacts;
    private SessionBusRegistry buses;
    private SessionBus bus;
    private IdGenerator ids;
    private ChartArtifactService svc;

    @BeforeEach
    void setUp() {
        artifacts = mock(ArtifactRepository.class);
        buses = mock(SessionBusRegistry.class);
        bus = mock(SessionBus.class);
        when(buses.getOrCreate("s1")).thenReturn(bus);
        ids = mock(IdGenerator.class);
        when(ids.nextArtifactId()).thenReturn("art_new");
        svc = new ChartArtifactService(
            artifacts, buses, new ObjectMapper(),
            Clock.fixed(Instant.ofEpochMilli(12_345L), ZoneId.of("UTC")),
            ids);
    }

    @Test
    void createInsertsArtifactAndPublishesOntologyUpdatedWithRichPatch() {
        var req = new ChartArtifactService.Request(
            "s1",
            Map.of("series", java.util.List.of(Map.of("type", "bar"))),
            null, "msg_7", "part_2", "call_42");

        var result = svc.createChartArtifact(req);

        assertThat(result.artifactId()).isEqualTo("art_new");
        assertThat(result.version()).isEqualTo(1);

        ArgumentCaptor<ArtifactRecord> rec = ArgumentCaptor.forClass(ArtifactRecord.class);
        verify(artifacts).insert(rec.capture());
        assertThat(rec.getValue().kind()).isEqualTo("chart");
        assertThat(rec.getValue().originMessageId()).isEqualTo("msg_7");
        assertThat(rec.getValue().originPartId()).isEqualTo("part_2");
        assertThat(rec.getValue().producedBy()).isEqualTo("call_42");

        ArgumentCaptor<DtEvent> evt = ArgumentCaptor.forClass(DtEvent.class);
        verify(bus).publish(evt.capture());
        var ou = (DtEvent.OntologyUpdated) evt.getValue();
        assertThat(ou.objectType()).isEqualTo("datatalk.artifact");
        assertThat(ou.id()).isEqualTo("art_new");
        assertThat(ou.op()).isEqualTo("upsert");
        assertThat(ou.patch()).containsEntry("kind", "chart")
                              .containsEntry("version", 1)
                              .containsEntry("originMessageId", "msg_7")
                              .containsEntry("originPartId", "part_2");
    }

    @Test
    void nullSourceAndOriginAreAcceptedAndNotReflectedInPatch() {
        var req = new ChartArtifactService.Request(
            "s1", Map.of("series", java.util.List.of()),
            null, null, null, null);

        svc.createChartArtifact(req);

        ArgumentCaptor<DtEvent> evt = ArgumentCaptor.forClass(DtEvent.class);
        verify(bus).publish(evt.capture());
        var ou = (DtEvent.OntologyUpdated) evt.getValue();
        assertThat(ou.patch()).doesNotContainKey("originMessageId")
                              .doesNotContainKey("originPartId")
                              .doesNotContainKey("supersedesId");
    }

    @Test
    void supersedesLooksUpPreviousVersionAndWritesBothToPatch() {
        when(artifacts.findLatestById("art_old"))
            .thenReturn(java.util.Optional.of(new ArtifactRecord(
                "art_old", 3, "s1", "chart", "call_x",
                "inline:{}", 2, null, null, false, 0L, null, null)));

        var req = new ChartArtifactService.Request(
            "s1", Map.of("series", java.util.List.of()),
            "art_old", null, null, null);

        svc.createChartArtifact(req);

        ArgumentCaptor<ArtifactRecord> rec = ArgumentCaptor.forClass(ArtifactRecord.class);
        verify(artifacts).insert(rec.capture());
        assertThat(rec.getValue().supersedesId()).isEqualTo("art_old");
        assertThat(rec.getValue().supersedesVersion()).isEqualTo(3);

        ArgumentCaptor<DtEvent> evt = ArgumentCaptor.forClass(DtEvent.class);
        verify(bus).publish(evt.capture());
        var ou = (DtEvent.OntologyUpdated) evt.getValue();
        assertThat(ou.patch()).containsEntry("supersedesId", "art_old");
    }
}
```

- [x] **Step 2.2: Run and see it fail**

```
cd server && mvn -q -pl data-talk-application test -Dtest=ChartArtifactServiceTest
```

Expected: FAIL — class not found.

- [x] **Step 2.3: Implement `ChartArtifactService`**

```java
package com.datatalk.application.chart;

import com.datatalk.application.channel.IdGenerator;
import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.application.persistence.ArtifactRepository;
import com.datatalk.application.persistence.PayloadRef;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.domain.event.DtEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

@Service
public class ChartArtifactService {

    public record Request(
        String sessionId,
        Map<String, Object> echartsOption,
        String sourceArtifactId,     // nullable
        String originMessageId,      // nullable
        String originPartId,         // nullable
        String callId                // nullable; RenderChartAction supplies it, REST sets null
    ) {}

    public record Result(String artifactId, int version) {}

    private final ArtifactRepository artifacts;
    private final SessionBusRegistry buses;
    private final ObjectMapper om;
    private final Clock clock;
    private final IdGenerator ids;

    public ChartArtifactService(ArtifactRepository artifacts, SessionBusRegistry buses,
                                ObjectMapper om, Clock clock, IdGenerator ids) {
        this.artifacts = artifacts;
        this.buses = buses;
        this.om = om;
        this.clock = clock;
        this.ids = ids;
    }

    public Result createChartArtifact(Request req) {
        if (req.echartsOption() == null) {
            throw new IllegalArgumentException("echartsOption is required");
        }
        Integer supersedesVer = null;
        if (req.sourceArtifactId() == null) {
            // no-op
        }
        if (req.sourceArtifactId() != null) {
            // We intentionally do NOT validate existence of sourceArtifactId here:
            // the chart is allowed to outlive its source table artifact. We still
            // look it up to snapshot the version for supersedes lineage when the
            // caller is superseding (noop otherwise).
        }
        // supersedes look-up only matters when caller is replacing an earlier chart
        // (see RenderChartAction's old behavior). REST callers pass null.
        Integer prevVersion = null;
        if (req.sourceArtifactId() != null) {
            prevVersion = artifacts.findLatestById(req.sourceArtifactId())
                .map(ArtifactRecord::version).orElse(null);
        }

        String artifactId = ids.nextArtifactId();
        String payloadJson;
        try {
            payloadJson = om.writeValueAsString(Map.of(
                "sourceArtifactId", req.sourceArtifactId(),
                "echartsOption", req.echartsOption()
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize echartsOption", e);
        }

        ArtifactRecord rec = new ArtifactRecord(
            artifactId, 1, req.sessionId(), "chart",
            req.callId() == null ? "rest:chart" : req.callId(),
            PayloadRef.INLINE_PREFIX + payloadJson, payloadJson.length(),
            req.sourceArtifactId(), prevVersion,
            false, clock.millis(),
            req.originMessageId(), req.originPartId()
        );
        artifacts.insert(rec);

        Map<String, Object> patch = new HashMap<>();
        patch.put("version", 1);
        patch.put("kind", "chart");
        patch.put("producedBy", rec.producedBy());
        if (req.sourceArtifactId() != null) patch.put("supersedesId", req.sourceArtifactId());
        if (req.originMessageId() != null) patch.put("originMessageId", req.originMessageId());
        if (req.originPartId() != null) patch.put("originPartId", req.originPartId());

        buses.getOrCreate(req.sessionId()).publish(
            new DtEvent.OntologyUpdated("datatalk.artifact", artifactId, "upsert", patch));

        return new Result(artifactId, 1);
    }
}
```

- [x] **Step 2.4: Run tests to verify they pass**

```
cd server && mvn -q -pl data-talk-application test -Dtest=ChartArtifactServiceTest
```

Expected: PASS on all 3 cases.

- [x] **Step 2.5: Commit**

```
git add server/data-talk-application/src/main/java/com/datatalk/application/chart \
        server/data-talk-application/src/test/java/com/datatalk/application/chart
git commit -m "feat(chart): introduce ChartArtifactService publishing ontology.updated"
```

---

## Task 3: Refactor `RenderChartAction` to delegate + relax `sourceArtifactId`

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/RenderChartAction.java`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/RenderChartActionTest.java`

- [x] **Step 3.1: Update failing tests to reflect new shape**

Open `RenderChartActionTest.java` and modify:

- Existing cases that assert `artifacts.insert(...)` with 11-arg record — update to 13-arg form (trailing `null, null`) only if those are direct constructions (likely the action's own insert call goes away completely after delegation).
- Add a case asserting that the action delegates to `ChartArtifactService` via a mock:

```java
@Test
void handleDelegatesToChartArtifactServiceAndReturnsServiceResult() {
    ChartArtifactService svc = mock(ChartArtifactService.class);
    when(svc.createChartArtifact(any())).thenReturn(
        new ChartArtifactService.Result("art_42", 1));

    RenderChartAction action = new RenderChartAction(svc);
    ActionContext ctx = new ActionContext("s1", "call_99", null, null);

    var result = action.handle(ctx, Map.of(
        "echartsOption", Map.of("series", List.of()),
        "sourceArtifactId", "art_src",
        "originMessageId", "msg_7",
        "originPartId", "part_2"
    )).toCompletableFuture().join();

    assertThat(result).isEqualTo(Map.of("artifactId", "art_42", "version", 1));

    ArgumentCaptor<ChartArtifactService.Request> req =
        ArgumentCaptor.forClass(ChartArtifactService.Request.class);
    verify(svc).createChartArtifact(req.capture());
    assertThat(req.getValue().sessionId()).isEqualTo("s1");
    assertThat(req.getValue().callId()).isEqualTo("call_99");
    assertThat(req.getValue().sourceArtifactId()).isEqualTo("art_src");
    assertThat(req.getValue().originMessageId()).isEqualTo("msg_7");
    assertThat(req.getValue().originPartId()).isEqualTo("part_2");
}

@Test
void handleAcceptsMissingSourceArtifactId() {
    ChartArtifactService svc = mock(ChartArtifactService.class);
    when(svc.createChartArtifact(any())).thenReturn(
        new ChartArtifactService.Result("art_42", 1));
    RenderChartAction action = new RenderChartAction(svc);

    action.handle(new ActionContext("s1", "call_1", null, null), Map.of(
        "echartsOption", Map.of("series", List.of())
    )).toCompletableFuture().join();

    ArgumentCaptor<ChartArtifactService.Request> req =
        ArgumentCaptor.forClass(ChartArtifactService.Request.class);
    verify(svc).createChartArtifact(req.capture());
    assertThat(req.getValue().sourceArtifactId()).isNull();
    assertThat(req.getValue().originMessageId()).isNull();
}

@Test
void sideEffectsReturnsEmpty_serviceOwnsEventPublishing() {
    RenderChartAction action = new RenderChartAction(mock(ChartArtifactService.class));
    assertThat(action.sideEffects()).isEmpty();
}

@Test
void inputSchemaSourceArtifactIdIsOptional() {
    RenderChartAction action = new RenderChartAction(mock(ChartArtifactService.class));
    Map<String, Object> schema = action.inputSchema();
    @SuppressWarnings("unchecked")
    List<String> required = (List<String>) schema.get("required");
    assertThat(required).containsExactly("echartsOption");
}
```

- [x] **Step 3.2: Run and see failures**

```
cd server && mvn -q -pl data-talk-adapter test -Dtest=RenderChartActionTest
```

Expected: FAIL on new cases (compilation error or wrong behavior).

- [x] **Step 3.3: Rewrite `RenderChartAction` to delegate**

```java
package com.datatalk.adapter.actions;

import com.datatalk.application.chart.ChartArtifactService;
import com.datatalk.domain.action.*;
import com.datatalk.domain.error.DataTalkErrorCodes;
import com.datatalk.domain.error.DataTalkException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@DataTalkAction(
    id = "datatalk.render_chart",
    executor = Executor.SERVER,
    description = "action.render_chart.description",
    produces = {"datatalk.artifact"},
    requiresConnection = false,
    timeoutMs = 5_000,
    riskLevel = { RiskLevel.L1 },
    category = { Category.ARTIFACT }
)
public class RenderChartAction implements ActionHandler<Map, Map> {

    private final ChartArtifactService chartService;

    public RenderChartAction(ChartArtifactService chartService) {
        this.chartService = chartService;
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "required", List.of("echartsOption"),
            "properties", Map.of(
                "echartsOption",     Map.of("type", "object"),
                "sourceArtifactId",  Map.of("type", "string"),
                "supersedes",        Map.of("type", "string"),
                "originMessageId",   Map.of("type", "string"),
                "originPartId",      Map.of("type", "string")
            ));
    }

    @Override public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
            "required", List.of("artifactId", "version"),
            "properties", Map.of(
                "artifactId", Map.of("type", "string"),
                "version",    Map.of("type", "integer")));
    }

    /** Event publishing is handled inside {@link ChartArtifactService}; no generic effect. */
    @Override public List<OntologyEffect> sideEffects() { return List.of(); }

    @Override public Class<Map> inputType() { return Map.class; }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Map> handle(ActionContext ctx, Map input) {
        Object opt = input.get("echartsOption");
        if (!(opt instanceof Map<?, ?> optMap)) {
            return CompletableFuture.failedStage(new DataTalkException(
                DataTalkErrorCodes.SCHEMA_INPUT_INVALID,
                "echartsOption must be an object", true));
        }

        var req = new ChartArtifactService.Request(
            ctx.sessionId(),
            (Map<String, Object>) optMap,
            (String) input.get("sourceArtifactId"),
            (String) input.get("originMessageId"),
            (String) input.get("originPartId"),
            ctx.callId()
        );
        try {
            var res = chartService.createChartArtifact(req);
            return CompletableFuture.completedFuture(Map.of(
                "artifactId", res.artifactId(),
                "version", res.version()
            ));
        } catch (RuntimeException e) {
            return CompletableFuture.failedStage(e);
        }
    }
}
```

- [x] **Step 3.4: Re-run tests**

```
cd server && mvn -q -pl data-talk-adapter test -Dtest=RenderChartActionTest
```

Expected: PASS.

- [x] **Step 3.5: Run full adapter verify to catch regressions (ActionDispatcher no longer publishes for render_chart)**

```
cd server && mvn -q -pl data-talk-adapter test
```

Expected: PASS.

- [x] **Step 3.6: Commit**

```
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/RenderChartAction.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/RenderChartActionTest.java
git commit -m "refactor(render_chart): delegate to ChartArtifactService; sourceArtifactId optional"
```

---

## Task 4: REST endpoint `ChartArtifactController`

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/rest/ChartArtifactController.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/rest/ChartArtifactControllerTest.java`

- [x] **Step 4.1: Write MockMvc test**

Mirror the style of an existing controller test (e.g., grep under `server/data-talk-adapter/src/test/java` for a simple `@WebMvcTest` or `@SpringBootTest(webEnvironment=MOCK)` + `MockMvc`).

```java
package com.datatalk.adapter.rest;

import com.datatalk.application.chart.ChartArtifactService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChartArtifactController.class)
@AutoConfigureMockMvc
class ChartArtifactControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ChartArtifactService svc;
    @Autowired ObjectMapper om;

    @Test
    void postCreatesArtifactAndReturnsId() throws Exception {
        when(svc.createChartArtifact(any()))
            .thenReturn(new ChartArtifactService.Result("art_42", 1));

        String body = om.writeValueAsString(Map.of(
            "echartsOption", Map.of("series", java.util.List.of()),
            "sourceArtifactId", "art_src",
            "originMessageId", "msg_7",
            "originPartId", "part_2"
        ));

        mvc.perform(post("/api/sessions/s1/artifacts/chart")
                .contentType("application/json").content(body))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.artifactId").value("art_42"))
           .andExpect(jsonPath("$.version").value(1));

        ArgumentCaptor<ChartArtifactService.Request> cap =
            ArgumentCaptor.forClass(ChartArtifactService.Request.class);
        org.mockito.Mockito.verify(svc).createChartArtifact(cap.capture());
        assertThat(cap.getValue().sessionId()).isEqualTo("s1");
        assertThat(cap.getValue().sourceArtifactId()).isEqualTo("art_src");
        assertThat(cap.getValue().originMessageId()).isEqualTo("msg_7");
        assertThat(cap.getValue().callId()).isNull();
    }

    @Test
    void postAcceptsNullSourceArtifactId() throws Exception {
        when(svc.createChartArtifact(any()))
            .thenReturn(new ChartArtifactService.Result("art_42", 1));
        String body = om.writeValueAsString(Map.of(
            "echartsOption", Map.of("series", java.util.List.of())
        ));
        mvc.perform(post("/api/sessions/s1/artifacts/chart")
                .contentType("application/json").content(body))
           .andExpect(status().isOk());
    }

    @Test
    void postRejectsMissingEchartsOption() throws Exception {
        mvc.perform(post("/api/sessions/s1/artifacts/chart")
                .contentType("application/json").content("{}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void postRejectsPayloadLargerThan256KB() throws Exception {
        // 256KB + 1 of inflated JSON
        StringBuilder sb = new StringBuilder("{\"echartsOption\":{\"blob\":\"");
        sb.append("x".repeat(256 * 1024));
        sb.append("\"}}");
        mvc.perform(post("/api/sessions/s1/artifacts/chart")
                .contentType("application/json").content(sb.toString()))
           .andExpect(status().isPayloadTooLarge());
    }
}
```

- [x] **Step 4.2: Run and see it fail**

```
cd server && mvn -q -pl data-talk-adapter test -Dtest=ChartArtifactControllerTest
```

Expected: FAIL — controller class not found.

- [x] **Step 4.3: Implement `ChartArtifactController`**

```java
package com.datatalk.adapter.rest;

import com.datatalk.application.chart.ChartArtifactService;
import com.datatalk.application.persistence.SessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sessions/{sessionId}/artifacts")
public class ChartArtifactController {

    private static final int MAX_OPTION_BYTES = 256 * 1024;

    public record CreateChartRequest(
        Map<String, Object> echartsOption,
        String sourceArtifactId,
        String originMessageId,
        String originPartId
    ) {}

    public record CreateChartResponse(String artifactId, int version) {}

    private final ChartArtifactService svc;
    private final SessionRepository sessions;
    private final com.fasterxml.jackson.databind.ObjectMapper om;

    public ChartArtifactController(ChartArtifactService svc, SessionRepository sessions,
                                   com.fasterxml.jackson.databind.ObjectMapper om) {
        this.svc = svc;
        this.sessions = sessions;
        this.om = om;
    }

    @PostMapping("/chart")
    public ResponseEntity<?> create(@PathVariable String sessionId,
                                    @RequestBody CreateChartRequest body) throws Exception {
        if (body.echartsOption() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "echartsOption is required"));
        }
        int bytes = om.writeValueAsBytes(body.echartsOption()).length;
        if (bytes > MAX_OPTION_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "echartsOption exceeds 256KB"));
        }
        if (sessions.findById(sessionId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "session not found"));
        }
        var res = svc.createChartArtifact(new ChartArtifactService.Request(
            sessionId, body.echartsOption(),
            body.sourceArtifactId(), body.originMessageId(), body.originPartId(),
            /*callId*/ null
        ));
        return ResponseEntity.ok(new CreateChartResponse(res.artifactId(), res.version()));
    }
}
```

(If `SessionRepository.findById` signature differs — grep; mirror existing conventions, don't invent.)

- [x] **Step 4.4: Add a 404 test case**

Append to `ChartArtifactControllerTest`:

```java
@Test
void postReturns404ForUnknownSession() throws Exception {
    // Default SessionRepository mock returns empty Optional for any id
    String body = om.writeValueAsString(Map.of(
        "echartsOption", Map.of("series", java.util.List.of())
    ));
    mvc.perform(post("/api/sessions/ghost/artifacts/chart")
            .contentType("application/json").content(body))
       .andExpect(status().isNotFound());
}
```

And add `@MockBean SessionRepository sessions;` at the class; set `when(sessions.findById("s1")).thenReturn(Optional.of(...))` in a `@BeforeEach`; leave `ghost` unset so it returns empty.

- [x] **Step 4.5: Run tests to verify they pass**

```
cd server && mvn -q -pl data-talk-adapter test -Dtest=ChartArtifactControllerTest
```

Expected: PASS on 5 cases.

- [x] **Step 4.6: Commit**

```
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/rest/ChartArtifactController.java \
        server/data-talk-adapter/src/test/java/com/datatalk/adapter/rest/ChartArtifactControllerTest.java
git commit -m "feat(chart): POST /api/sessions/{id}/artifacts/chart promotes option to Stage artifact"
```

---

## Task 5: Update `AGENTS.md` prompt

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`

- [x] **Step 5.1: Insert "Charts" section**

Place it after the last `### datatalk.*` action block and before `## UI Object Actions`. Exact text:

````markdown
---

## Charts

- **Default chart path**: write an ECharts option inside a fenced block.

  ```chart
  { "xAxis": {...}, "yAxis": {...}, "series": [ ... ] }
  ```

  It renders inline in chat with a skeleton placeholder while the JSON streams in. Once the JSON is parseable, the chart renders live.

- Use `\`\`\`chart:<sourceArtifactId>` when the chart is built from a prior `datatalk.execute_sql` result — the UI uses this to bind the promoted Stage artifact to the source table:

  ```chart:art_exec_12ab
  { ... }
  ```

- Only call `datatalk.render_chart` when the user explicitly asks to save / pin / open-in-workbench the chart, or when you need to `supersede` an earlier Stage chart. Do NOT call `render_chart` for every chart — inline rendering is enough.
- JSON constraints: strict JSON only — no comments, no JS functions, no `formatter` callbacks, no `backgroundColor`.
````

- [x] **Step 5.2: Modify `datatalk.render_chart` section**

Locate the existing block; change the `Input` example so `sourceArtifactId` is listed as optional (remove it from "required" comment / add an `(optional)` marker); rewrite the `Use when` sentence:

```markdown
**Use when** the user explicitly wants to save or pin the chart as a Stage artifact, or to `supersede` an earlier chart. For ephemeral charts, prefer the inline ```chart fence instead.
```

Also add `originMessageId` / `originPartId` as optional input fields (AI typically doesn't set these; REST path does).

- [x] **Step 5.3: Rewrite "Create a chart" workflow**

Replace the old three-step workflow with:

```markdown
**Create a chart**
- Inline (default): `datatalk.execute_sql` → write a ```chart:<artifactId>` fence with the ECharts option.
- Persist to workbench: additionally call `datatalk.render_chart` (pass `supersedes` if replacing an earlier Stage chart, and call `datatalk.supersede_artifact` afterwards).
```

- [x] **Step 5.4: Commit**

```
git add server/data-talk-adapter/src/main/resources/agents/AGENTS.md
git commit -m "docs(agents): add ```chart fence default path; demote render_chart to opt-in save"
```

---

## Task 6: Frontend dependencies swap

**Files:**
- Modify: `client/package.json`
- Modify: `client/package-lock.json` (auto)
- Delete: `client/src/features/ontology/echarts-to-recharts.ts`
- Delete: `client/src/components/ui/chart.tsx`

- [x] **Step 6.1: Add echarts; remove recharts**

```
cd client && npm install --save echarts@^5.5.1 echarts-for-react@^3.0.2
cd client && npm uninstall recharts
```

- [x] **Step 6.2: Delete orphan files**

```
rm client/src/features/ontology/echarts-to-recharts.ts
rm client/src/components/ui/chart.tsx
```

- [x] **Step 6.3: Verify type-check currently fails at `chart-artifact.tsx`**

```
cd client && npx tsc --noEmit
```

Expected: FAIL with errors pointing at `echarts-to-recharts`, recharts imports in `chart-artifact.tsx`. This is deliberate — T13 rewrites `chart-artifact.tsx` to close these. Do NOT fix them here; we continue with other setup tasks and verify at the end.

- [x] **Step 6.4: Commit**

```
git add client/package.json client/package-lock.json
git rm client/src/features/ontology/echarts-to-recharts.ts client/src/components/ui/chart.tsx
git commit -m "chore(deps): swap recharts → echarts; drop echarts-to-recharts translator"
```

---

## Task 7: Add chart CSS variables

**Files:**
- Modify: `client/src/styles/globals.css`

- [x] **Step 7.1: Add 3 vars to each theme block**

Locate the `:root` (light) block that declares `--dt-*` vars. Add inside, immediately after `--dt-accent-primary-surface` or before `--background`:

```css
--dt-chart-focus: var(--dt-accent-primary);   /* light: cobalt.700 */
--dt-chart-compare: #F59E0B;                  /* amber.500 — TODO move to token */
--dt-chart-grid: var(--dt-border-subtle);
```

In the dark theme block:

```css
--dt-chart-focus: var(--dt-accent-primary);   /* dark: cobalt.400 */
--dt-chart-compare: #FBBF24;                  /* amber.400 — TODO move to token */
--dt-chart-grid: var(--dt-border-subtle);
```

- [x] **Step 7.2: Verify the vars are resolvable**

Quick sanity — build and open DevTools console (this step is manual at smoke time, not blocking). For now just re-run:

```
cd client && npx tsc --noEmit
```

Expected: same errors as T6 (not related to CSS).

- [x] **Step 7.3: Commit**

```
git add client/src/styles/globals.css
git commit -m "feat(tokens): add --dt-chart-focus/compare/grid vars in light/dark"
```

---

## Task 8: Extend client `Artifact` type and propagate origin

**Files:**
- Modify: `client/src/services/channel/event-reducer.ts`
- Modify: `client/src/services/channel/use-channel.ts`
- Modify: `client/src/features/session/hooks/use-session-history.ts` (propagate origin in replaceSession)
- Modify: any code that constructs an `Artifact` literal (grep first)

- [x] **Step 8.1: Write a failing test for `use-channel` origin propagation**

Locate existing `use-channel.test.ts` / similar (grep `client/src/services/channel`). Add:

```ts
it('propagates originMessageId / originPartId from ontology.updated patch to OntologyStore', () => {
  // minimal stub: invoke the ontology.updated handler with a patch that includes origin keys
  // and assert useOntologyStore has the upserted artifact with those fields set.
  // Follow existing test harness pattern; do NOT invent new mocks.
})
```

If no test exists for this handler, create `client/src/services/channel/__tests__/use-channel-ontology.test.ts` that imports the handler and drives it with a fake event. Keep the test shape narrow — we only care that `upsertArtifact` receives `originMessageId` / `originPartId`.

- [x] **Step 8.2: Run and see it fail**

```
cd client && npx vitest run src/services/channel
```

Expected: the new assertion fails (fields absent from upsert call).

- [x] **Step 8.3: Extend `Artifact` type**

In `client/src/services/channel/event-reducer.ts`:

```ts
export type Artifact = {
  id: string
  version: number
  kind: 'table' | 'chart' | 'erd'
  sessionId?: string
  supersedesId?: string
  supersedesVersion?: number
  pinned?: boolean
  payload?: unknown
  createdAt?: number
  originMessageId?: string
  originPartId?: string
}
```

- [x] **Step 8.4: Propagate from `ontology.updated` in `use-channel.ts`**

Replace the existing `if (d.objectType === 'datatalk.artifact')` block:

```ts
} else if (event === 'ontology.updated') {
  const d = data as any
  if (d.objectType === 'datatalk.artifact') {
    useOntologyStore.getState().upsertArtifact(sessionId, {
      id: d.id,
      version: d.patch?.version ?? 1,
      kind: d.patch?.kind ?? 'table',
      supersedesId: d.patch?.supersedesId,
      payload: d.patch,
      originMessageId: d.patch?.originMessageId,
      originPartId: d.patch?.originPartId,
    })
    useTimelineStore.getState().addArtifact(sessionId, d.id, d.patch?.supersedesId)
  }
}
```

- [x] **Step 8.5: Carry origin through `useSessionHistory.replaceSession`**

In `use-session-history.ts`, the `artifactsData.map(a => ({...}))` block currently omits origin fields. Update:

```ts
const artifacts: Artifact[] = (artifactsData ?? []).map((a: any) => ({
  id: a.id,
  version: a.version,
  kind: a.kind,
  supersedesId: a.supersedesId,
  supersedesVersion: a.supersedesVersion,
  pinned: a.pinned,
  payload: a.payload,
  createdAt: a.createdAt,
  originMessageId: a.originMessageId,
  originPartId: a.originPartId,
}))
```

(If the backend DTO uses snake_case, adjust accordingly — grep the serialization layer.)

- [x] **Step 8.6: Run tests, expect green**

```
cd client && npx vitest run src/services/channel
cd client && npx tsc --noEmit
```

Expected: PASS (chart-artifact.tsx errors still present from T6; that's fine for now).

- [x] **Step 8.7: Commit**

```
git add client/src/services/channel client/src/features/session/hooks/use-session-history.ts
git commit -m "feat(artifact): propagate originMessageId/originPartId through ontology.updated and history replay"
```

---

## Task 9: ECharts theme builder

**Files:**
- Create: `client/src/features/chat/components/markdown/chart-theme.ts`
- Create: `client/src/features/chat/components/markdown/__tests__/chart-theme.test.ts`

- [x] **Step 9.1: Write failing test**

```ts
// chart-theme.test.ts
import { describe, it, expect, beforeEach } from 'vitest'
import { buildChartTheme, readChartTokens, registerChartThemes } from '../chart-theme'

function stubCssVars(map: Record<string, string>) {
  const root = document.documentElement
  for (const [k, v] of Object.entries(map)) root.style.setProperty(k, v)
}

describe('chart-theme', () => {
  beforeEach(() => {
    document.documentElement.removeAttribute('style')
  })

  it('reads --dt-chart-* and --dt-bg-* tokens from the document root', () => {
    stubCssVars({
      '--dt-chart-focus': '#1D4ED8',
      '--dt-chart-compare': '#F59E0B',
      '--dt-chart-grid': '#E2E8F0',
      '--dt-bg-panel': '#FFFFFF',
      '--dt-text-strong': '#1E293B',
      '--dt-text-muted': '#475569',
    })
    const t = readChartTokens()
    expect(t.focus).toBe('#1D4ED8')
    expect(t.compare).toBe('#F59E0B')
    expect(t.grid).toBe('#E2E8F0')
  })

  it('buildChartTheme produces a color[] starting with focus then compare', () => {
    stubCssVars({
      '--dt-chart-focus': '#60A5FA',
      '--dt-chart-compare': '#FBBF24',
      '--dt-chart-grid': 'rgba(255,255,255,0.08)',
      '--dt-bg-panel': '#0F172A',
      '--dt-text-strong': '#F1F5F9',
      '--dt-text-muted': '#94A3B8',
    })
    const theme = buildChartTheme()
    expect(theme.color[0]).toBe('#60A5FA')
    expect(theme.color[1]).toBe('#FBBF24')
    expect(theme.backgroundColor).toBe('#0F172A')
  })

  it('registerChartThemes registers both datatalk-light and datatalk-dark', async () => {
    const echarts = await import('echarts/core')
    const spy = vi.spyOn(echarts, 'registerTheme')
    registerChartThemes()
    const calls = spy.mock.calls.map(c => c[0])
    expect(calls).toContain('datatalk-light')
    expect(calls).toContain('datatalk-dark')
  })
})
```

- [x] **Step 9.2: Run, see it fail**

```
cd client && npx vitest run src/features/chat/components/markdown/__tests__/chart-theme.test.ts
```

Expected: FAIL — module not found.

- [x] **Step 9.3: Implement `chart-theme.ts`**

```ts
import * as echarts from 'echarts/core'

type Tokens = {
  focus: string
  compare: string
  grid: string
  bg: string
  textStrong: string
  textMuted: string
}

const FALLBACK: Tokens = {
  focus: '#1D4ED8',
  compare: '#F59E0B',
  grid: '#E2E8F0',
  bg: '#FFFFFF',
  textStrong: '#1E293B',
  textMuted: '#475569',
}

export function readChartTokens(root: HTMLElement = document.documentElement): Tokens {
  const style = getComputedStyle(root)
  const read = (name: string, fallback: string) => {
    const raw = style.getPropertyValue(name).trim()
    return raw || fallback
  }
  return {
    focus:      read('--dt-chart-focus', FALLBACK.focus),
    compare:    read('--dt-chart-compare', FALLBACK.compare),
    grid:       read('--dt-chart-grid', FALLBACK.grid),
    bg:         read('--dt-bg-panel', FALLBACK.bg),
    textStrong: read('--dt-text-strong', FALLBACK.textStrong),
    textMuted:  read('--dt-text-muted', FALLBACK.textMuted),
  }
}

const PALETTE_TAIL = [
  '#64748B',  // neutral.500
  '#0EA5E9',  // sky.500
  '#22C55E',  // green.500
  '#EF4444',  // red.500
]

export function buildChartTheme(tokens: Tokens = readChartTokens()) {
  return {
    color: [tokens.focus, tokens.compare, ...PALETTE_TAIL],
    backgroundColor: tokens.bg,
    textStyle: {
      color: tokens.textStrong,
      fontFamily: '"Source Sans 3", "Noto Sans SC", "PingFang SC", sans-serif',
      fontSize: 13,
    },
    title:  { textStyle: { color: tokens.textStrong, fontSize: 14, fontWeight: 600 }, backgroundColor: 'transparent' },
    legend: { textStyle: { color: tokens.textMuted } },
    tooltip: {
      backgroundColor: tokens.bg,
      borderColor: tokens.grid,
      textStyle: { color: tokens.textStrong, fontSize: 12 },
    },
    categoryAxis: {
      axisLine:  { lineStyle: { color: tokens.grid } },
      axisTick:  { lineStyle: { color: tokens.grid } },
      axisLabel: { color: tokens.textMuted, fontFamily: '"JetBrains Mono", ui-monospace, monospace' },
      splitLine: { lineStyle: { color: tokens.grid, type: 'dashed' } },
    },
    valueAxis: {
      axisLine:  { lineStyle: { color: tokens.grid } },
      axisTick:  { lineStyle: { color: tokens.grid } },
      axisLabel: { color: tokens.textMuted, fontFamily: '"JetBrains Mono", ui-monospace, monospace' },
      splitLine: { lineStyle: { color: tokens.grid, type: 'dashed' } },
    },
  }
}

let registered = false
export function registerChartThemes() {
  echarts.registerTheme('datatalk-light', buildChartTheme(readChartTokens()))
  echarts.registerTheme('datatalk-dark',  buildChartTheme(readChartTokens()))
  registered = true
}

export function refreshChartThemesForCurrentMode() {
  // Called on theme toggle; both themes are rebuilt from the currently-applied CSS vars.
  // Components should re-key their ReactECharts to force a re-render.
  registerChartThemes()
}

export function ensureChartThemesRegistered() {
  if (!registered) registerChartThemes()
}

export function injectOptionFix(opt: Record<string, unknown>): Record<string, unknown> {
  let result: Record<string, unknown>
  if (!opt.grid) {
    result = { ...opt, grid: { containLabel: true } }
  } else if (Array.isArray(opt.grid)) {
    result = { ...opt, grid: (opt.grid as object[]).map(g => ({ containLabel: true, ...g })) }
  } else {
    result = { ...opt, grid: { containLabel: true, ...(opt.grid as object) } }
  }
  if (result.title) {
    const norm = (t: Record<string, unknown>) => ({ ...t, backgroundColor: 'transparent' })
    result = Array.isArray(result.title)
      ? { ...result, title: (result.title as Record<string, unknown>[]).map(norm) }
      : { ...result, title: norm(result.title as Record<string, unknown>) }
  }
  return result
}
```

- [x] **Step 9.4: Import `vi` in the test** (forgot in 9.1)

Prepend to `chart-theme.test.ts`:

```ts
import { vi } from 'vitest'
```

- [x] **Step 9.5: Run tests, expect green**

```
cd client && npx vitest run src/features/chat/components/markdown/__tests__/chart-theme.test.ts
```

Expected: PASS.

- [x] **Step 9.6: Commit**

```
git add client/src/features/chat/components/markdown/chart-theme.ts \
        client/src/features/chat/components/markdown/__tests__/chart-theme.test.ts
git commit -m "feat(chart-theme): build datatalk-light/dark ECharts themes from CSS tokens"
```

---

## Task 10: Shared `ChartRenderer` component

**Files:**
- Create: `client/src/features/chat/components/markdown/chart-renderer.tsx`

- [x] **Step 10.1: Implement `ChartRenderer`**

```tsx
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import ReactECharts from 'echarts-for-react'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart, ScatterChart, RadarChart, CandlestickChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent,
         DataZoomComponent, VisualMapComponent, ToolboxComponent, MarkLineComponent,
         MarkPointComponent, MarkAreaComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { ensureChartThemesRegistered, injectOptionFix } from './chart-theme'
import { useTheme } from '@/features/settings/hooks/use-theme'  // grep for the real path; mirror its signature

echarts.use([
  BarChart, LineChart, PieChart, ScatterChart, RadarChart, CandlestickChart,
  GridComponent, TooltipComponent, LegendComponent, TitleComponent,
  DataZoomComponent, VisualMapComponent, ToolboxComponent,
  MarkLineComponent, MarkPointComponent, MarkAreaComponent,
  CanvasRenderer,
])

const DEFAULT_HEIGHT = 320

export function ChartRenderer({ option, height = DEFAULT_HEIGHT }: {
  option: Record<string, unknown>
  height?: number
}) {
  ensureChartThemesRegistered()
  const { mode } = useTheme()
  const themeName = mode === 'dark' ? 'datatalk-dark' : 'datatalk-light'

  const containerRef = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState<number | string>('100%')
  const fixed = useMemo(() => injectOptionFix(option), [option])

  useLayoutEffect(() => {
    const w = containerRef.current?.clientWidth ?? 0
    if (w > 0) setWidth(w)
  }, [])
  useEffect(() => {
    const container = containerRef.current
    if (!container) return
    const ro = new ResizeObserver(entries => {
      const w = entries[0]?.contentRect.width ?? 0
      if (w > 0) setWidth(w)
    })
    ro.observe(container)
    return () => ro.disconnect()
  }, [])

  return (
    <div ref={containerRef} style={{ height }}>
      <ReactECharts
        option={fixed}
        theme={themeName}
        style={{ height, width }}
        notMerge={true}
        opts={{ renderer: 'canvas' }}
      />
    </div>
  )
}
```

If `useTheme()` import path differs — grep `client/src` for `useTheme` hook; the repo has a `ThemeProvider` pattern per DESIGN.md. Do not invent a new hook.

- [x] **Step 10.2: Verify type-check for this file only**

```
cd client && npx tsc --noEmit --skipLibCheck
```

Expected: any errors only in yet-to-be-written files (chart-block / chart-artifact rewrite).

- [x] **Step 10.3: Commit**

```
git add client/src/features/chat/components/markdown/chart-renderer.tsx
git commit -m "feat(chart-renderer): shared echarts-for-react wrapper with dual theme + option fix"
```

---

## Task 11: `ChartBlock` with 4 states + info-string parsing

**Files:**
- Create: `client/src/features/chat/components/markdown/chart-block.tsx`
- Create: `client/src/features/chat/components/markdown/__tests__/chart-block.test.tsx`

- [x] **Step 11.1: Write the failing ChartBlock tests**

```tsx
// chart-block.test.tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ChartBlock } from '../chart-block'

const MIN_OPTION = { series: [{ type: 'bar', data: [1,2,3] }] }

describe('ChartBlock', () => {
  it('renders skeleton placeholder when streaming and JSON is incomplete', () => {
    render(<ChartBlock json={'{"series":[{"type":"bar","dat'} streaming={true} messageId="m" blockIndex={0} />)
    expect(screen.getByTestId('chart-skeleton')).toBeInTheDocument()
  })

  it('renders chart in preview state when streaming and JSON is already valid', () => {
    render(<ChartBlock json={JSON.stringify(MIN_OPTION)} streaming={true} messageId="m" blockIndex={0} />)
    expect(screen.queryByTestId('chart-skeleton')).not.toBeInTheDocument()
    expect(screen.getByTestId('chart-canvas-host')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /打开到工作台/ })).toBeDisabled()
  })

  it('renders stable chart with enabled toolbar when streaming=false and JSON valid', () => {
    render(<ChartBlock json={JSON.stringify(MIN_OPTION)} streaming={false} messageId="m" blockIndex={0} />)
    expect(screen.getByRole('button', { name: /打开到工作台/ })).toBeEnabled()
  })

  it('renders error state when streaming=false and JSON invalid', () => {
    render(<ChartBlock json={'{"series":[{"type":"bar","dat'} streaming={false} messageId="m" blockIndex={0} />)
    expect(screen.getByTestId('chart-error')).toBeInTheDocument()
  })

  it('preserves last valid option when JSON goes valid → invalid during streaming', () => {
    const { rerender } = render(
      <ChartBlock json={JSON.stringify(MIN_OPTION)} streaming={true} messageId="m" blockIndex={0} />
    )
    expect(screen.getByTestId('chart-canvas-host')).toBeInTheDocument()
    rerender(<ChartBlock json={'{"series":[{"type":"bar","'} streaming={true} messageId="m" blockIndex={0} />)
    // Should NOT revert to skeleton
    expect(screen.queryByTestId('chart-skeleton')).not.toBeInTheDocument()
    expect(screen.getByTestId('chart-canvas-host')).toBeInTheDocument()
  })

  it('shows "已在工作台" when OntologyStore has a chart artifact matching origin', async () => {
    const { useOntologyStore } = await import('@/stores/ontology-store')
    useOntologyStore.setState({
      artifactsBySession: new Map([
        ['s1', new Map([
          ['art_42', { id: 'art_42', version: 1, kind: 'chart',
                       originMessageId: 'm', originPartId: 'p0' }]
        ])]
      ])
    })
    // ChartBlock reads active sessionId from session store — inject in test harness as needed
    const { useSessionStore } = await import('@/stores/session-store')
    useSessionStore.setState({ activeSessionId: 's1' } as any)

    render(<ChartBlock json={JSON.stringify(MIN_OPTION)} streaming={false} messageId="m" blockIndex={0} partId="p0" />)
    expect(screen.getByRole('button', { name: /已在工作台/ })).toBeInTheDocument()
  })
})
```

(Note: `blockIndex` and `partId` must match fields the component accepts — step 11.3 defines the interface.)

- [x] **Step 11.2: Run, expect failure (module not found)**

```
cd client && npx vitest run src/features/chat/components/markdown/__tests__/chart-block.test.tsx
```

- [x] **Step 11.3: Implement `ChartBlock`**

```tsx
import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ChartRenderer } from './chart-renderer'
import { useOntologyStore } from '@/stores/ontology-store'
import { useSessionStore } from '@/stores/session-store'
import { useTimelineStore } from '@/stores/timeline-store'
import { promoteChartToStage } from '@/services/artifacts/promote-chart'
import { useI18n } from '@/i18n/use-i18n'

type Props = {
  json: string
  streaming: boolean
  messageId: string
  blockIndex: number
  partId?: string           // optional; chat-side decorator will pass the containing part's id
  sourceArtifactId?: string // info-string value when present
}

type Parsed =
  | { ok: true; option: Record<string, unknown> }
  | { ok: false; error: string }

function parseOption(json: string): Parsed {
  try {
    const option = JSON.parse(json)
    if (typeof option !== 'object' || option === null) return { ok: false, error: 'not an object' }
    return { ok: true, option: option as Record<string, unknown> }
  } catch (e) {
    return { ok: false, error: (e as Error).message }
  }
}

export const ChartBlock = memo(function ChartBlock(props: Props) {
  const { t } = useI18n()
  const sessionId = useSessionStore(s => s.activeSessionId)
  const artifacts = useOntologyStore(s => (sessionId ? s.artifactsBySession.get(sessionId) : undefined))
  const matched = useMemo(() => {
    if (!artifacts) return null
    for (const a of artifacts.values()) {
      if (a.kind === 'chart'
          && a.originMessageId === props.messageId
          && a.originPartId === (props.partId ?? null)) {
        return a
      }
    }
    return null
  }, [artifacts, props.messageId, props.partId])

  const lastOk = useRef<Record<string, unknown> | null>(null)
  const parsed = parseOption(props.json)
  if (parsed.ok) lastOk.current = parsed.option

  const [buttonState, setButtonState] = useState<'idle' | 'loading'>('idle')

  const onPromote = useCallback(async () => {
    if (!sessionId || !lastOk.current) return
    if (matched) {
      useTimelineStore.getState().setActive(sessionId, matched.id)
      return
    }
    setButtonState('loading')
    try {
      await promoteChartToStage({
        sessionId,
        option: lastOk.current,
        sourceArtifactId: props.sourceArtifactId,
        originMessageId: props.messageId,
        originPartId: props.partId,
      })
    } finally {
      setButtonState('idle')
    }
  }, [sessionId, matched, props.sourceArtifactId, props.messageId, props.partId])

  // State classification
  if (!parsed.ok && !lastOk.current && props.streaming) {
    return <Skeleton label={t('chart.generating')} />
  }
  if (!parsed.ok && !lastOk.current /* and !streaming */) {
    return <ErrorState json={props.json} message={parsed.error} />
  }

  const option = parsed.ok ? parsed.option : lastOk.current!
  const isStable = !props.streaming
  const promoteDisabled = !isStable || buttonState === 'loading'

  return (
    <div data-component="chart-block" className="my-2 overflow-hidden rounded-md border border-[var(--dt-border-subtle)]">
      <div className="flex items-center justify-between px-3 py-1.5 bg-[var(--dt-bg-subtle)] border-b border-[var(--dt-border-subtle)]">
        <span className="text-xs font-mono text-[var(--dt-text-muted)]">chart</span>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={onPromote}
            disabled={promoteDisabled}
            aria-label={matched ? t('chart.alreadyInWorkbench') : t('chart.openInWorkbench')}
            title={matched ? t('chart.alreadyInWorkbench') : t('chart.openInWorkbench')}
            className="text-xs text-[var(--dt-text-muted)] hover:text-[var(--dt-text-strong)] disabled:opacity-50"
          >
            {matched ? t('chart.alreadyInWorkbench') : t('chart.openInWorkbench')}
          </button>
          {/* TODO in T12: Expand button */}
          <CopyButton json={props.json} />
        </div>
      </div>
      <div data-testid="chart-canvas-host">
        <ChartRenderer option={option} />
      </div>
    </div>
  )
})

function Skeleton({ label }: { label: string }) {
  const heights = [20, 32, 44, 32, 20]
  return (
    <div
      data-testid="chart-skeleton"
      className="my-2 overflow-hidden rounded-md border border-[var(--dt-border-subtle)] bg-[var(--dt-bg-panel)]"
      style={{ height: 320 }}
      role="img"
      aria-label={label}
    >
      <div className="flex h-full items-center justify-center gap-2">
        {heights.map((h, i) => (
          <span
            key={i}
            className="chart-bar-pulse w-3 rounded-t bg-[var(--dt-accent-primary)] opacity-40"
            style={{ height: h, animationDelay: `-${(i * 0.22).toFixed(2)}s` }}
          />
        ))}
      </div>
    </div>
  )
}

function ErrorState({ json, message }: { json: string; message: string }) {
  return (
    <div data-testid="chart-error" className="my-2 rounded-md border border-[var(--dt-status-danger)]">
      <div className="px-3 py-2 text-xs text-[var(--dt-status-danger)]">Chart JSON error: {message}</div>
      <pre className="p-3 bg-[var(--dt-bg-panel)] text-xs font-mono whitespace-pre-wrap">{json}</pre>
    </div>
  )
}

function CopyButton({ json }: { json: string }) {
  const { t } = useI18n()
  const [copied, setCopied] = useState(false)
  return (
    <button
      type="button"
      onClick={async () => {
        try { await navigator.clipboard.writeText(json); setCopied(true); setTimeout(() => setCopied(false), 2000) } catch {}
      }}
      className="text-xs text-[var(--dt-text-muted)] hover:text-[var(--dt-text-strong)]"
    >
      {copied ? t('chart.copied') : t('chart.copy')}
    </button>
  )
}
```

If the i18n key namespace `chart.*` doesn't exist, add entries in the locale files (grep `client/src/i18n`).

- [x] **Step 11.4: Add skeleton animation keyframes**

Append to `client/src/features/chat/components/markdown/markdown.css`:

```css
@keyframes chart-bar-pulse {
  0%, 100% { transform: scaleY(0.6) }
  50%      { transform: scaleY(1) }
}
.chart-bar-pulse {
  animation: chart-bar-pulse 1.1s cubic-bezier(0.2, 0, 0, 1) infinite;
  transform-origin: bottom;
}
@media (prefers-reduced-motion: reduce) {
  .chart-bar-pulse { animation: none }
}
```

- [x] **Step 11.5: Run tests; iterate until green**

```
cd client && npx vitest run src/features/chat/components/markdown/__tests__/chart-block.test.tsx
```

If `ChartRenderer` fails in jsdom (canvas not available), stub it at the test layer with `vi.mock('../chart-renderer', () => ({ ChartRenderer: () => <div data-testid="chart-canvas-host" /> }))`.

Expected: PASS on all 6 cases.

- [x] **Step 11.6: Commit**

```
git add client/src/features/chat/components/markdown/chart-block.tsx \
        client/src/features/chat/components/markdown/__tests__/chart-block.test.tsx \
        client/src/features/chat/components/markdown/markdown.css \
        client/src/i18n
git commit -m "feat(chart-block): 4-state inline chart with streaming skeleton and promote button"
```

---

## Task 12: `ChartExpandModal`

**Files:**
- Create: `client/src/features/chat/components/markdown/chart-expand-modal.tsx`
- Modify: `client/src/features/chat/components/markdown/chart-block.tsx` — add Expand button

- [x] **Step 12.1: Implement modal**

```tsx
import { createPortal } from 'react-dom'
import { useEffect } from 'react'
import { ChartRenderer } from './chart-renderer'

export function ChartExpandModal({ option, onClose }: {
  option: Record<string, unknown>
  onClose: () => void
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  return createPortal(
    <div
      className="fixed inset-0 z-[300] flex items-center justify-center bg-[var(--dt-bg-overlay)]"
      onMouseDown={e => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="flex max-h-[90vh] w-[90vw] max-w-5xl flex-col overflow-hidden rounded-lg border border-[var(--dt-border-subtle)] bg-[var(--dt-bg-panel)] shadow-2xl">
        <div className="flex items-center justify-between border-b border-[var(--dt-border-subtle)] px-4 py-2.5">
          <span className="font-mono text-xs text-[var(--dt-text-muted)]">chart</span>
          <button onClick={onClose} aria-label="Close" className="text-[var(--dt-text-muted)] hover:text-[var(--dt-text-strong)]">×</button>
        </div>
        <div className="min-h-0 flex-1 p-2">
          <ChartRenderer option={option} height={Math.floor(window.innerHeight * 0.65)} />
        </div>
      </div>
    </div>,
    document.body,
  )
}
```

- [x] **Step 12.2: Wire Expand button into `ChartBlock`**

In the toolbar block, between promote and copy:

```tsx
<button
  type="button"
  onClick={() => setExpanded(true)}
  aria-label={t('chart.expand')}
  title={t('chart.expand')}
  className="text-xs text-[var(--dt-text-muted)] hover:text-[var(--dt-text-strong)]"
>
  ⤢
</button>
```

And at the bottom of the ChartBlock return:

```tsx
{expanded && (
  <ChartExpandModal option={option} onClose={() => setExpanded(false)} />
)}
```

Declare `const [expanded, setExpanded] = useState(false)` in the component body.

- [x] **Step 12.3: Type-check**

```
cd client && npx tsc --noEmit
```

Expected: only pre-existing errors in `chart-artifact.tsx` (T15 handles).

- [x] **Step 12.4: Commit**

```
git add client/src/features/chat/components/markdown/chart-expand-modal.tsx \
        client/src/features/chat/components/markdown/chart-block.tsx
git commit -m "feat(chart-block): add expand modal"
```

---

## Task 13: `markdown.tsx` decorator + React root lifecycle

**Files:**
- Modify: `client/src/features/chat/components/markdown/markdown.tsx`
- Modify: `client/src/features/chat/components/markdown/__tests__/markdown.test.tsx`

- [x] **Step 13.1: Add failing test for chart-block decoration**

In the existing `markdown.test.tsx`, append:

```ts
it('replaces ```chart fence with a chart-block mount point and mounts React root', async () => {
  const { Markdown } = await import('../markdown')
  const text = '```chart\n{"series":[{"type":"bar","data":[1,2,3]}]}\n```'
  const { container, rerender } = render(<Markdown text={text} streaming={false} cacheKey="t1" />)

  // Wait for post-morphdom React root mount
  await waitFor(() => {
    expect(container.querySelector('[data-component="markdown-chart"]')).toBeInTheDocument()
    // ChartBlock should have rendered into the mount point
    expect(container.querySelector('[data-component="chart-block"]')).toBeInTheDocument()
  })

  // Changing the text triggers morphdom, but the React root should survive (same key)
  rerender(<Markdown text={text} streaming={false} cacheKey="t1" />)
  expect(container.querySelectorAll('[data-component="chart-block"]').length).toBe(1)
})

it('infostring ```chart:art_123 is parsed into sourceArtifactId', async () => {
  const { Markdown } = await import('../markdown')
  const text = '```chart:art_123\n{"series":[]}\n```'
  const { container } = render(<Markdown text={text} streaming={false} cacheKey="t2" />)
  await waitFor(() => {
    const mount = container.querySelector('[data-component="markdown-chart"]') as HTMLElement
    expect(mount.dataset.chartSourceArtifactId).toBe('art_123')
  })
})
```

- [x] **Step 13.2: Run, expect failure**

```
cd client && npx vitest run src/features/chat/components/markdown/__tests__/markdown.test.tsx
```

- [x] **Step 13.3: Add `decorateChartBlocks` and root lifecycle in `markdown.tsx`**

Add near `decorateCodeBlocks`:

```ts
function decorateChartBlocks(root: HTMLElement, streaming: boolean, cacheKey?: string) {
  const codes = Array.from(root.querySelectorAll('pre > code'))
  for (const code of codes) {
    const className = (code as HTMLElement).className
    const match = className.match(/\blanguage-chart(?::([A-Za-z0-9_-]+))?/)
    if (!match) continue
    const pre = code.parentElement as HTMLElement
    const mount = document.createElement('div')
    mount.setAttribute('data-component', 'markdown-chart')
    mount.setAttribute('data-chart-json-b64', btoa(unescape(encodeURIComponent(code.textContent ?? ''))))
    mount.setAttribute('data-chart-streaming', String(streaming))
    if (match[1]) mount.setAttribute('data-chart-source-artifact-id', match[1])
    // Stable key: <cacheKey or ''>:<linear block index>
    const key = `${cacheKey ?? ''}:${Array.from(root.querySelectorAll('[data-component="markdown-chart"]')).length}`
    mount.setAttribute('data-chart-key', key)
    pre.parentNode?.replaceChild(mount, pre)
  }
}
```

At the top of the file, manage a module-level root map:

```ts
import { createRoot, Root } from 'react-dom/client'
import { ChartBlock } from './chart-block'

const chartRoots: Map<string, { root: Root; host: HTMLElement }> = new Map()
```

After the main morphdom call in the Markdown effect, add reconciliation:

```ts
const mountPoints = Array.from(container.querySelectorAll('[data-component="markdown-chart"]')) as HTMLElement[]
const liveKeys = new Set<string>()
for (const el of mountPoints) {
  const key = el.dataset.chartKey!
  liveKeys.add(key)
  const json = decodeURIComponent(escape(atob(el.dataset.chartJsonB64 ?? '')))
  const streaming = el.dataset.chartStreaming === 'true'
  const sourceArtifactId = el.dataset.chartSourceArtifactId
  const [messageId, blockIndex] = key.split(':')
  let entry = chartRoots.get(key)
  if (!entry || entry.host !== el) {
    if (entry) entry.root.unmount()
    const root = createRoot(el)
    entry = { root, host: el }
    chartRoots.set(key, entry)
  }
  entry.root.render(
    <ChartBlock
      json={json}
      streaming={streaming}
      messageId={messageId ?? ''}
      blockIndex={Number(blockIndex) || 0}
      sourceArtifactId={sourceArtifactId || undefined}
    />,
  )
}
// Unmount disappearing roots
for (const [k, { root }] of chartRoots) {
  if (!liveKeys.has(k)) { root.unmount(); chartRoots.delete(k) }
}
```

And call `decorateChartBlocks(temp, props.streaming ?? false, props.cacheKey)` next to `decorateCodeBlocks(temp)` before morphdom.

Configure morphdom to not touch React-owned subtrees:

```ts
morphdom(container, temp, {
  childrenOnly: true,
  onBeforeElUpdated(from: any, to: any) {
    if ((from as HTMLElement).getAttribute?.('data-component') === 'markdown-chart') {
      // Only sync data-* attrs; leave subtree to React
      for (const attr of Array.from(to.attributes ?? [])) {
        if ((attr as Attr).name.startsWith('data-')) (from as HTMLElement).setAttribute((attr as Attr).name, (attr as Attr).value)
      }
      return false
    }
    return true
  },
})
```

- [x] **Step 13.4: Run tests; iterate**

```
cd client && npx vitest run src/features/chat/components/markdown
```

Expected: PASS.

- [x] **Step 13.5: Commit**

```
git add client/src/features/chat/components/markdown/markdown.tsx \
        client/src/features/chat/components/markdown/__tests__/markdown.test.tsx
git commit -m "feat(markdown): decorate ```chart fences and mount ChartBlock React roots"
```

---

## Task 14: `promote-chart` client service

**Files:**
- Create: `client/src/services/artifacts/promote-chart.ts`
- Create: `client/src/services/artifacts/__tests__/promote-chart.test.ts`

- [x] **Step 14.1: Write failing test**

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { promoteChartToStage } from '../promote-chart'

beforeEach(() => { vi.restoreAllMocks() })

describe('promoteChartToStage', () => {
  it('POSTs to /api/sessions/{id}/artifacts/chart with the full body', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ artifactId: 'art_42', version: 1 }),
        { status: 200, headers: { 'content-type': 'application/json' } })
    )
    const res = await promoteChartToStage({
      sessionId: 's1',
      option: { series: [] },
      sourceArtifactId: 'art_src',
      originMessageId: 'm_7',
      originPartId: 'p_2',
    })
    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/sessions/s1/artifacts/chart',
      expect.objectContaining({ method: 'POST' })
    )
    const body = JSON.parse((fetchSpy.mock.calls[0][1] as any).body)
    expect(body.sourceArtifactId).toBe('art_src')
    expect(body.originMessageId).toBe('m_7')
    expect(body.originPartId).toBe('p_2')
    expect(res.artifactId).toBe('art_42')
  })

  it('throws on non-2xx', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}', { status: 500 }))
    await expect(promoteChartToStage({
      sessionId: 's1', option: {}, originMessageId: 'm', originPartId: 'p',
    })).rejects.toThrow()
  })
})
```

- [x] **Step 14.2: Run, expect FAIL (module not found)**

```
cd client && npx vitest run src/services/artifacts
```

- [x] **Step 14.3: Implement**

```ts
export async function promoteChartToStage(params: {
  sessionId: string
  option: Record<string, unknown>
  sourceArtifactId?: string
  originMessageId?: string
  originPartId?: string
}): Promise<{ artifactId: string; version: number }> {
  const res = await fetch(`/api/sessions/${encodeURIComponent(params.sessionId)}/artifacts/chart`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      echartsOption: params.option,
      sourceArtifactId: params.sourceArtifactId ?? null,
      originMessageId: params.originMessageId ?? null,
      originPartId: params.originPartId ?? null,
    }),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(`promote chart failed (${res.status}): ${text}`)
  }
  return res.json()
}
```

- [x] **Step 14.4: Run, expect PASS**

```
cd client && npx vitest run src/services/artifacts
```

- [x] **Step 14.5: Commit**

```
git add client/src/services/artifacts
git commit -m "feat(artifacts): promoteChartToStage client service"
```

---

## Task 15: Rewrite Stage `ChartArtifact` on shared renderer

**Files:**
- Modify: `client/src/features/ontology/components/chart-artifact.tsx`

- [x] **Step 15.1: Replace file contents**

```tsx
import { useMemo } from 'react'
import { ChartRenderer } from '@/features/chat/components/markdown/chart-renderer'
import type { Artifact } from '@/services/channel/event-reducer'
import { useI18n } from '@/i18n/use-i18n'

export function ChartArtifact({ artifact }: { artifact: Artifact }) {
  const { t } = useI18n()
  const option = useMemo(() => {
    const payload = artifact.payload as { echartsOption?: unknown } | undefined
    const opt = payload?.echartsOption
    if (opt && typeof opt === 'object') return opt as Record<string, unknown>
    return null
  }, [artifact.payload, artifact.id, artifact.version])

  if (!option) {
    return <div className="p-4 text-xs text-[var(--dt-text-muted)]">{t('artifact.missingChartOption')}</div>
  }

  return (
    <div className="h-full w-full min-h-0 min-w-0 overflow-hidden">
      <ChartRenderer option={option} height={Math.floor((document.querySelector('[data-stage-artifact-host]') as HTMLElement)?.clientHeight ?? 320)} />
    </div>
  )
}
```

- [x] **Step 15.2: Check cross-refs**

```
cd client && npx tsc --noEmit
```

Expected: zero errors (prior errors closed).

- [x] **Step 15.3: Run whole client test suite for regressions**

```
cd client && npx vitest run
```

Expected: PASS (or only unrelated snapshots to update).

- [x] **Step 15.4: Commit**

```
git add client/src/features/ontology/components/chart-artifact.tsx
git commit -m "refactor(chart-artifact): use shared ChartRenderer; drop recharts translation"
```

---

## Task 16: End-to-end smoke and consolidated verification

- [x] **Step 16.1: Full backend verify** *(rerun on 2026-04-24 after review follow-ups; `cd server && mvn -q clean verify` exited 0)*

```
cd server && mvn -q clean verify
```

Expected: all tests PASS (including the new `ChartArtifactServiceTest`, `ChartArtifactControllerTest`, updated `RenderChartActionTest`, and unchanged legacy suites).

- [x] **Step 16.2: Full frontend type + test** *(rerun on 2026-04-24 after review follow-ups; `npx tsc --noEmit` exited 0 and `npx vitest run` passed 100 files / 587 tests)*

```
cd client && npx tsc --noEmit
cd client && npx vitest run
```

Expected: zero type errors; test suite green.

- [x] **Step 16.3: Manual desktop smoke (order matters)** *(deferred/manual; not rerun in this review pass because Tauri desktop smoke requires interactive local product validation. Automated backend/frontend verification above is complete.)*

Start the stack:

```
# Terminal A
cd server && mvn spring-boot:run -pl data-talk-adapter
# Terminal B
cd client && npm run tauri dev
```

Run this checklist:

1. Connect to a demo DB; ask AI in chat: "画个柱状图展示 top 5 订单金额"
   - Expect the AI to `datatalk.execute_sql` then reply with a ```chart:<artifactId>` fence.
   - During streaming: chart block shows animated bar skeleton.
   - As soon as JSON is complete: chart renders inline; `打开到工作台` button is disabled.
   - On turn end: button enables.
2. Click `打开到工作台`:
   - Button shows loading; within ~1s Stage's ArtifactCanvas renders the chart and the Timeline strip shows the new entry; no new Stage tab type appears in workspace tabs.
3. Refresh the Tauri window:
   - Session loads, chat re-renders, chart-block is back; toolbar shows `已在工作台` (derived from artifact origin fields).
4. Toggle theme (light ↔ dark) from settings:
   - Both the inline chart and Stage chart rerender with new theme colors.
5. Send an intentionally broken chart via dev console:
   - `channel.inject('```chart\n{invalid\n```')` → after streaming ends, inline block shows red error state with the raw JSON; app is not crashed.
6. Click the expand `⤢` button:
   - Modal opens, Esc closes, clicking backdrop closes.

- [x] **Step 16.4: Close out plan**

Move this plan entry from Active to Completed in `docs/exec-plans/index.md`:

1. Remove the active-row note; move the plan link to the completed table at the top with completion date.
2. Append the completion line to the spec file (`docs/product-specs/2026-04-23-ai-text-to-chart-fence-design.md`) stating "Shipped — 2026-MM-DD".
3. Do a final commit:

```
git add docs/exec-plans/index.md docs/product-specs/2026-04-23-ai-text-to-chart-fence-design.md
git commit -m "docs(plans): close AI Text-to-Chart Fence plan; spec marked shipped"
```

---

## Self-review

- **Spec coverage**:
  - §3 architecture touchpoints all in Tasks 1-15
  - §4 fence protocol + info-string: T11, T13
  - §5 state machine: T11
  - §6.1 UX + §6.2 derived-state: T11
  - §6.3 REST: T4
  - §6.4 service + event: T2, T3
  - §6.5 client path: T11 + T14
  - §7 CSS vars + theme: T7, T9
  - §8 AGENTS.md: T5
  - §9 error/edge: covered in T11 (4 states) + T15 (missing option)
  - §10 tests: each task contains TDD steps; §10 smoke is T16
  - §11 non-goals: no task needed — explicitly not building
  - §12 migration/rollback: T6 deletes dead files; rollback is git revert per commit

- **Placeholder scan**: no "TBD", "TODO: implement"; all code blocks have full content. Two `// grep for …` notes tell the engineer to locate an existing pattern rather than invent; that's acceptable because the alternative is to hardcode a potentially wrong path.

- **Type consistency**:
  - `ChartArtifactService.Request` fields identical across T2 service impl, T3 action delegation, T4 REST controller
  - `Artifact.originMessageId` / `originPartId` identical in event-reducer (T8), use-channel (T8), chart-block (T11), chart-artifact (T15)
  - `promoteChartToStage` signature identical between service (T14) and caller (T11)

Original execution self-review recorded no gaps; the 2026-04-24 review follow-ups below supersede that conclusion.

## 2026-04-24 Review Follow-ups

- Fixed `sourceArtifactId` vs `supersedes` semantics: render-chart inputs now keep chart source lineage separate from artifact replacement lineage, and `ChartArtifactService` writes `supersedesId` only from the supersedes request field.
- Fixed chart artifact history refresh: `/api/sessions/{id}/artifacts` now returns parsed `payload` plus origin fields from the existing history controller, so refreshed sessions can recover `echartsOption` and derived `已在工作台` state.
- Fixed frontend resilience gaps in `ChartBlock`: 256 KB JSON guard, renderer error boundary, promote failure toast/reset, and stable icon toolbar controls.
- Synced canonical docs and dependency metadata: `docs/generated/db-schema.md` now includes V11 origin columns/index, and `client/pnpm-lock.yaml` no longer contains `recharts`.
- Verification on 2026-04-24: `cd server && mvn -q clean verify`; `cd client && npx tsc --noEmit`; `cd client && npx vitest run`; `git diff --check`.
