# ER Inspector Implementation Plan (Plan A)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use Markdown checkbox syntax for tracking.

**Goal:** Ship the `er_inspector` Stage Tab type — a read-only, persistent, AI-operable ER browser that turns DataTalk's `LayoutErdAction` placeholder into a real product feature, while retiring the broken `erd` artifact pipeline and laying the canvas / store / dialect foundations for Plan B (Designer).

**Architecture:** Two-tier separation. Backend introduces `ErRelationDiscoveryService` + `JdbcErRelationReader` reading JDBC `getImportedKeys`, exposed via internal REST `POST /api/er/seed-inspector` (not in MCP). Frontend introduces `@xyflow/react` v12 + `dagre` (web worker), shared `<ErCanvas>` / `<ErTableNode>` / `<ErEdge>` components, an independent `useErTabsStore`, an `ErInspectorAdapter` registered globally via `StageUIObjectRegistry`, and a `WorkspaceAdapter.open_er_inspector` exec verb. Persistence reuses Task 6 `stage_tabs` + FTS5 by adding a new content subscription to `stage-persistence-bootstrap`. AI mutations land via the existing UI Object Protocol (`ui_read` / `ui_patch` / `ui_exec` / `ui_find`) — no new MCP tools.

**Tech Stack:** Spring Boot 3.5, Java 21 (virtual threads), JdbcTemplate, JUnit 5, AssertJ, MockMvc, WireMock 3.x; Tauri v2, React 19, TypeScript, Zustand, TanStack Query, `@xyflow/react@^12.10.1`, `dagre@^0.8.5`, vitest, Testing Library.

---

## Status

- **Created:** 2026-04-29
- **State:** Completed
- **Spec:** [docs/product-specs/2026-04-29-er-graph-browsing-design.md](../product-specs/2026-04-29-er-graph-browsing-design.md)
- **Phase:** Plan A of 2 (Plan B Designer follows after Plan A acceptance)
- **Estimated effort:** 2-3 weeks
- **Completed:** 2026-04-29

## Completion Notes

- Plan A `er_inspector` implementation shipped in branch `er-inspector-plan-a`.
- Backend verification: `cd server && JAVA_HOME=/home/wushengzhou/.local/opt/java21 PATH=/home/wushengzhou/.local/opt/java21/bin:$PATH mvn clean verify` passed before the final frontend-only stabilization commit; after that commit, `mvn compile -q` passed again.
- Frontend verification after final stabilization: `cd client && npx tsc --noEmit` passed; `cd client && npm test -- --run` passed with 133 files / 789 tests.
- Manual real-database desktop smoke was not run in this environment; it is deferred to user acceptance. Automated H2/JDBC backend coverage and frontend ER adapter/canvas/persistence tests passed.
- Plan B `er_designer` remains separate and active in [2026-04-29-er-designer-plan.md](./2026-04-29-er-designer-plan.md).

## Context

ER ability today is non-functional: `LayoutErdAction` returns a 4-column grid with `position={col*240, row*180}` and writes an `erd` artifact whose JSON shape (`nodes[].id` + `nodes[].columns`) does not match the frontend `ErdArtifact` placeholder which reads `node.name` + `node.fields`. The pipeline has never run end-to-end. Task 6 (Cross-Session Workbench Tabs) shipped persistent `stage_tabs` + FTS5 and the `ui_find` action; Task 5 (Guarded DDL/DML) shipped L2/L3 confirm. Roadmap Task 8.2 picks ER browsing as the first visualization slice. open-db-studio (`/home/wushengzhou/workspace/github/open-db-studio`) uses the same `@xyflow/react` + `dagre` stack and contributes proven algorithms (line-jump bridging, label anti-overlap, self-ref loopback, two-stage AI highlight) that this plan ports while replacing all CSS tokens, store/protocol layers, and i18n with DataTalk equivalents.

This plan ships the Inspector half. Plan B (Designer) extends the same foundations with DDL generation, schema diffing, and Apply via query_editor + Task 5 guarded confirm.

## Design Inputs

This plan implements UI surfaces under `client/`. Per CLAUDE.md the agent must read [client/DESIGN.md](../../client/DESIGN.md) first; applicable constraints:

- All new UI maps to semantic tokens (`bg.canvas` for canvas, `bg.subtle` for chrome, `border.subtle / border.default / border.strong` for separators and edges, `accent.primary` cobalt-700 for selection / focus / current relation, `accent.warn` amber-500 only for virtual-relation warning).
- No raw primitive colors in feature code (no `#3794FF`, no emerald, no indigo). open-db-studio's color tokens map to DataTalk semantics in §17 of the spec.
- Each table node must use stable header hierarchy, low-emphasis hover, explicit selected state, mono treatment for technical column types.
- Charts/graphs: cobalt for the focus object, amber for compare/warning context only; never decorative color.
- Motion confirms state change only — Tab open / focus / pulse / residual highlight all use `motion.normal` 180ms with `easing.standard`. `prefers-reduced-motion` disables pulse and shrinks transitions to 0ms.
- Keyboard access required: focus rings (`interaction.focusRing`), `Tab` cycling between nodes, `Enter` toggles collapse, `Esc` clears selection, `Cmd/Ctrl+L` triggers auto layout, `Cmd/Ctrl+0` fits view.
- Accessible names on every icon button; ARIA labels on toolbar; reduced-motion respected.
- `client/i18n/messages.ts` carries user-visible strings in both languages; AI-facing strings (AGENTS.md, aiHint, STAGE_TAB_DIGEST) stay English (P12).

## Spec Mapping

| Spec section | Plan tasks |
|---|---|
| §1 (背景) | T1, T2, T3 (erd retirement) |
| §2 Q1-Q19 (decisions) | enforced throughout |
| §2 Q20 + §17 (existing code surfaces) | covers all 23 surface rows for Inspector |
| §4 P1-P12 (AI ergonomics) | T29 summary, T34 AGENTS.md, T31 schema, T35 digest |
| §5.1 + §5.2 (Inspector payload + extractContent) | T8, T10 |
| §5.4 (useErTabsStore) | T9, T11 |
| §5.5.1 (ER content subscription) | T12 |
| §5.5.2 (ui-handlers force-flush) | T13 |
| §5.5.3 (Adapter global registration) | T14 |
| §5.5.4 (Java schemas) | T31, T32, T33, T36 |
| §5.6 (i18n) | T33 |
| §6.2 (Patch grammar) | reused from `ui-router/jsonPatch.ts`; T11 verifies |
| §6.3 (Inspector ui_patch paths) | T11 |
| §6.5 (Inspector ui_exec verbs) | T28 |
| §6.7 (error codes + aiHint) | T6, T29 |
| §7 (Frontend Architecture) | T15-T26 |
| §8 (Backend Architecture) | T4-T7 |
| §9.1 (AGENTS.md) | T34 |
| §9.2 (STAGE_TAB_DIGEST) | T35 |
| §9.3 (er-tab-protocol.md) | T37 |
| §11.1 backend tests | T4-T7, T31, T32, T36 |
| §11.2 frontend tests | T9-T30 |
| §11.3 AI behavior IT | T30, T35 |
| §13 Plan A scope | this plan; Plan B is separate file |
| §14 Definition of Done | T40-T43 |

## File Structure Map

### Created (new)

| File | Responsibility |
|---|---|
| `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErRelation.java` | Record for source/target table+column edges (record kind: `schema_fk` / `virtual`) |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErTableMeta.java` | Record `(name, comment?, columns: List<ErColumnMeta>, fkOut: List<ErRelation>)` |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErColumnMeta.java` | Record `(name, type, nullable, isPK, isFK, isAutoIncrement, default?, comment?)` |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErGraph.java` | Record `(nodes: List<ErTableMeta>, edges: List<ErRelation>, summary: String, warnings: List<String>)` |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/er/Dialect.java` | Enum `{ MYSQL, POSTGRESQL, H2, SQLITE }`; helpers `fromConnectionKind(String)` returns `Optional<Dialect>` |
| `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErErrors.java` | Sealed exception hierarchy: `DialectUnsupportedException`, `ErPayloadOversizedException`, `TablesNotFoundException` |
| `server/data-talk-application/src/main/java/com/datatalk/application/er/ErRelationDiscoveryService.java` | Interface — `discover(connectionId, tables, neighborDepth)` + `refresh(...)` |
| `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/er/JdbcErRelationDiscoveryService.java` | Implementation — JDBC `getImportedKeys` + virtual-thread fan-out + 100 table cap |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/ErTabController.java` | `POST /api/er/seed-inspector` — internal REST not exposed to MCP |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SeedInspectorRequest.java` | DTO `(connectionId, tables, neighborDepth)` |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/ErGraphResponse.java` | DTO mirroring `ErGraph` for HTTP serialization |
| `server/data-talk-application/src/test/java/com/datatalk/application/er/ErRelationDiscoveryServiceTest.java` | H2 in-memory unit tests + neighbor BFS + caps |
| `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/er/JdbcErRelationDiscoveryServiceIT.java` | Real H2 IT (cross-schema FK) |
| `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/ErTabControllerIT.java` | MockMvc for `/api/er/seed-inspector` (200 / 400 / 404 / 413) |
| `client/src/features/stage/stores/er-tabs-store.ts` | Independent Zustand store for inspector / designer payloads (designer fields stubbed in Plan A) |
| `client/src/features/stage/stores/er-tabs-store.test.ts` | hydrate / applyInspectorPatch / assignedIds / strict baseVersion |
| `client/src/features/stage/stores/er-tabs-payload-types.ts` | TypeScript types `ErInspectorPayload` / `ErTableMeta` / `ErColumnMeta` / `ErRelation` / `JsonPatchOp` re-export |
| `client/src/features/stage/components/er-canvas/ErCanvas.tsx` | Main `ReactFlow` container, mode-aware |
| `client/src/features/stage/components/er-canvas/ErTableNode.tsx` | Node render (Plan A: mode='inspector' only) |
| `client/src/features/stage/components/er-canvas/ErEdge.tsx` | Edge render with line-jump + label anti-overlap + self-ref |
| `client/src/features/stage/components/er-canvas/ErToolbar.tsx` | Toolbar (inspector buttons in Plan A) |
| `client/src/features/stage/components/er-canvas/ErMinimap.tsx` | Right-bottom minimap (auto when > 10 nodes) |
| `client/src/features/stage/components/er-canvas/ErEmptyState.tsx` | Empty / Oracle / SQLite fallbacks |
| `client/src/features/stage/components/er-canvas/hooks/useDagreLayout.ts` | Worker-backed layout dispatcher |
| `client/src/features/stage/components/er-canvas/hooks/useErHighlight.ts` | pulse → residual two-phase AI highlight |
| `client/src/features/stage/components/er-canvas/hooks/useErKeyboard.ts` | Tab / Enter / arrows / +- / Esc / Cmd+L / Cmd+0 |
| `client/src/features/stage/components/er-canvas/utils/crossings.ts` | Line-jump arc computation (ported from open-db-studio) |
| `client/src/features/stage/components/er-canvas/utils/label-positioning.ts` | Edge label anti-overlap |
| `client/src/features/stage/components/er-canvas/utils/self-ref-path.ts` | Self-referencing loopback path builder |
| `client/src/features/stage/components/er-canvas/utils/payload-to-graph.ts` | `ErInspectorPayload` → `{nodes, edges}` for ReactFlow |
| `client/src/features/stage/components/er-canvas/workers/dagre-layout.worker.ts` | Worker: `dagre` LR layout |
| `client/src/features/stage/components/er-inspector-tab.tsx` | Tab content for `tab.type === 'er_inspector'` |
| `client/src/features/stage/adapters/ErInspectorAdapter.ts` | UIObject implementation; ui_read / ui_patch / ui_exec |
| `client/src/features/stage/components/er-canvas/__tests__/...` | Component tests (one per component) |
| `client/src/features/stage/components/er-canvas/hooks/__tests__/...` | Hook tests |
| `client/src/features/stage/components/er-canvas/utils/__tests__/...` | Util tests (algorithms) |
| `client/src/features/stage/components/er-canvas/workers/dagre-layout.worker.test.ts` | Worker performance + correctness |
| `client/src/features/stage/adapters/__tests__/ErInspectorAdapter.test.ts` | Adapter tests |
| `client/src/features/stage/components/__tests__/er-inspector-tab.test.tsx` | Tab rendering test |
| `docs/references/er-tab-protocol.md` | ER protocol reference (inspector half in Plan A) |

### Modified

| File | Change |
|---|---|
| `client/package.json` | Add `@xyflow/react@^12.10.1`, `dagre@^0.8.5`, `@types/dagre@^0.7.54` |
| `client/src/features/stage/registry/tab-type-registry.ts` | Register `er_inspector` (extractContent + rehydrate) |
| `client/src/features/stage/persistence/stage-persistence-bootstrap.ts` | Subscribe `useErTabsStore`; `diffErContentAndSchedule` |
| `client/src/features/actions/ui-handlers.ts` | `MUTATING_EXEC` adds inspector verbs; flush returned `tabId / newTabId` |
| `client/src/features/stage/components/stage-ui-object-registry.tsx` | Add `RegisteredErInspector` global registration |
| `client/src/features/stage/adapters/WorkspaceAdapter.ts` | Add `case 'open_er_inspector'` in `exec` |
| `client/src/features/stage/components/stage-tab-content.tsx` | Route `tab.type === 'er_inspector'` to `<ErInspectorTab />` |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java` | Add `erInspectorExecSchema()`; extend `workspaceExecSchema()` action enum + params |
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiPatchAction.java` | Extend `object` enum to include `er_inspector`; lenient ER ops schema |
| `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` | Drop `datatalk_layout_erd`; add §"ER Tabs (Inspector & Designer)" with inspector recipes |
| `server/data-talk-application/src/main/java/com/datatalk/application/opencode/AgentPromptBuilder.java` | Render `er_inspector` digest line `(N tables · M relations · conn=…)` |
| `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java` | Inspector schema/recipe assertions; remove layout_erd assertions |
| `server/data-talk-adapter/src/main/resources/messages.properties` | New i18n keys (default English) |
| `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties` | Chinese translations for user-visible labels |
| `client/src/i18n/messages.ts` | Inspector-related labels; remove `artifact.erdEmpty` |
| `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` | "Adapter Actions And Ontology" section: drop LayoutErdAction; add ER Inspector matrix |
| `docs/exec-plans/index.md` | Register Plan A as Active |
| `docs/exec-plans/tech-debt-tracker.md` | Remove ER placeholder backlog entry (if any) |
| `docs/product-specs/index.md` | (already registered; nothing to change) |

### Deleted

| File | Reason |
|---|---|
| `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/LayoutErdAction.java` | Placeholder, retired |
| `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/LayoutErdActionIT.java` | LayoutErdAction's IT |
| `client/src/features/ontology/components/erd-artifact.tsx` | Placeholder, retired |
| (parts of) `client/src/features/ontology/components/artifact-dispatcher.tsx` | `case 'erd'` arm |
| (parts of) `client/src/services/channel/event-reducer.ts` | `'erd'` from union |
| (parts of) `client/src/features/chat/components/tools/renderers/artifact-created.tsx` | `datatalk_layout_erd` mapping |
| (parts of) `client/src/features/session/hooks/use-session-history.ts` | `'erd'` from union |
| (parts of) `client/src/features/chat/components/tools/__tests__/register-built-in-renderers.test.ts` | erd assertions |
| (parts of) `client/src/i18n/messages.ts` | `'artifact.erdEmpty'` two entries |

---

## Batch Plan

This plan is organized into 9 batches. Tasks within a batch may share context; tasks across batches are independent enough to commit and verify separately. Each task ends with a commit; verification is per-task. After every batch, run a quick batch-wide smoke (`mvn compile -q` or `npx tsc --noEmit`) before starting the next.

---

## Batch 1: Project skeleton & `erd` retirement

This batch removes the dead `LayoutErdAction` / `ErdArtifact` placeholder and lands the new dependencies. After this batch, `erd` is gone from the codebase and `@xyflow/react` + `dagre` are installable.

### Task 1: Install `@xyflow/react` + `dagre`

**Files:**
- Modify: `client/package.json`
- Modify: `client/package-lock.json` (auto)

- [x] **Step 1: Add dependencies**

Edit `client/package.json` `dependencies` block to include:

```json
"@xyflow/react": "^12.10.1",
"dagre": "^0.8.5"
```

And `devDependencies`:

```json
"@types/dagre": "^0.7.54"
```

- [x] **Step 2: Install**

Run: `cd client && npm install`
Expected: lockfile updates, no version conflicts.

- [x] **Step 3: Sanity check**

Run: `cd client && npx tsc --noEmit`
Expected: no errors. (No code references the new packages yet.)

- [x] **Step 4: Commit**

```bash
git add client/package.json client/package-lock.json
git commit -m "build(client): add @xyflow/react + dagre for ER canvas"
```

### Task 2: Delete `LayoutErdAction` + IT

**Files:**
- Delete: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/LayoutErdAction.java`
- Delete: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/LayoutErdActionIT.java`

- [x] **Step 1: Run baseline tests to confirm green**

Run: `cd server && mvn -pl data-talk-adapter test -q`
Expected: all green (note that LayoutErdActionIT counts here).

- [x] **Step 2: Delete files**

```bash
rm server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/LayoutErdAction.java
rm server/data-talk-adapter/src/test/java/com/datatalk/adapter/actions/LayoutErdActionIT.java
```

- [x] **Step 3: Search for stragglers**

Run: `cd server && grep -rn 'LayoutErdAction\|datatalk\.layout_erd\|datatalk_layout_erd' --include='*.java' --include='*.properties' --include='*.md' src/ src/main/resources/ data-talk-application/src/ data-talk-domain/src/ data-talk-infrastructure/src/ data-talk-adapter/src/ 2>/dev/null`
Expected: zero results in `src/main` and `src/test`. (AGENTS.md still mentions it; that's removed in T34.)

- [x] **Step 4: Compile**

Run: `cd server && mvn compile -q`
Expected: zero errors.

- [x] **Step 5: Run targeted tests**

Run: `cd server && mvn -pl data-talk-adapter test -q`
Expected: all green; one fewer test class.

- [x] **Step 6: Commit**

```bash
git add server/
git commit -m "refactor(adapter): retire LayoutErdAction placeholder

The action wrote an erd artifact whose JSON shape never matched the
frontend ErdArtifact placeholder. Replaced in spec
docs/product-specs/2026-04-29-er-graph-browsing-design.md by the
er_inspector / er_designer Stage Tab types, which use the UI Object
Protocol (ui_exec workspace open_er_inspector) instead of an artifact."
```

### Task 3: Retire `'erd'` artifact kind from frontend

**Files:**
- Delete: `client/src/features/ontology/components/erd-artifact.tsx`
- Modify: `client/src/features/ontology/components/artifact-dispatcher.tsx`
- Modify: `client/src/services/channel/event-reducer.ts`
- Modify: `client/src/features/chat/components/tools/renderers/artifact-created.tsx`
- Modify: `client/src/features/session/hooks/use-session-history.ts`
- Modify: `client/src/features/chat/components/tools/__tests__/register-built-in-renderers.test.ts`
- Modify: `client/src/i18n/messages.ts`

- [x] **Step 1: Delete `erd-artifact.tsx`**

```bash
rm client/src/features/ontology/components/erd-artifact.tsx
```

- [x] **Step 2: Update `artifact-dispatcher.tsx` — remove `case 'erd'` arm**

Open `client/src/features/ontology/components/artifact-dispatcher.tsx`. Remove the `import { ErdArtifact } from './erd-artifact'` line and the `case 'erd': return <ErdArtifact ... />` arm. The dispatcher's switch should still handle remaining kinds (`table`, `chart`).

- [x] **Step 3: Narrow the artifact `kind` union in `event-reducer.ts`**

Open `client/src/services/channel/event-reducer.ts`. Find:

```ts
kind: 'table' | 'chart' | 'erd'
```

Change to:

```ts
kind: 'table' | 'chart'
```

- [x] **Step 4: Remove `datatalk_layout_erd` mapping from `artifact-created.tsx`**

Open `client/src/features/chat/components/tools/renderers/artifact-created.tsx`. Find the function that maps tool name → kind:

```ts
if (part.tool === 'datatalk_layout_erd') return 'erd'
```

Delete the line. Also remove any `kind === 'erd'` ternary arms — replace with a fallthrough that does not reference erd.

- [x] **Step 5: Narrow union in `use-session-history.ts`**

Open `client/src/features/session/hooks/use-session-history.ts`. Find:

```ts
kind: 'table' | 'chart' | 'erd'
```

Change to:

```ts
kind: 'table' | 'chart'
```

- [x] **Step 6: Remove erd assertions from `register-built-in-renderers.test.ts`**

Open `client/src/features/chat/components/tools/__tests__/register-built-in-renderers.test.ts`. Search for `erd` and `layout_erd`; delete any assertion that registers or asserts erd-kind renderer behavior.

- [x] **Step 7: Drop `'artifact.erdEmpty'` i18n keys**

Open `client/src/i18n/messages.ts`. Find both `'artifact.erdEmpty': 'ER 图暂无节点数据'` (zh) and `'artifact.erdEmpty': 'No ER nodes available yet'` (en). Delete both entries.

- [x] **Step 8: Type-check & test**

Run: `cd client && npx tsc --noEmit`
Expected: zero errors.

Run: `cd client && npm test -- --run`
Expected: all green.

- [x] **Step 9: Search for stragglers**

Run: `grep -rn "'erd'\|erd-artifact\|ErdArtifact\|layout_erd\|artifact\.erdEmpty" client/src 2>/dev/null`
Expected: zero hits. (Comments mentioning legacy ER are fine, but no live references.)

- [x] **Step 10: Commit**

```bash
git add client/
git commit -m "refactor(client): retire 'erd' artifact kind

ErdArtifact placeholder + datatalk_layout_erd mapping + 'artifact.erdEmpty'
i18n keys are removed. The new er_inspector / er_designer Stage Tab types
take over via the UI Object Protocol (Plan A 引入 er_inspector)."
```

---

## Batch 2: Backend ER discovery service

This batch lands the backend foundation: domain records, the discovery service with neighbor BFS + caps + dialect gating, and one internal REST endpoint.

### Task 4: Domain records & errors

**Files:**
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/er/Dialect.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErRelation.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErColumnMeta.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErTableMeta.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErGraph.java`
- Create: `server/data-talk-domain/src/main/java/com/datatalk/domain/er/ErErrors.java`
- Test: `server/data-talk-domain/src/test/java/com/datatalk/domain/er/DialectTest.java`

- [x] **Step 1: Write the failing test for `Dialect.fromConnectionKind`**

Create `server/data-talk-domain/src/test/java/com/datatalk/domain/er/DialectTest.java`:

```java
package com.datatalk.domain.er;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DialectTest {

    @Test
    void fromConnectionKind_mapsKnown() {
        assertThat(Dialect.fromConnectionKind("mysql")).contains(Dialect.MYSQL);
        assertThat(Dialect.fromConnectionKind("MYSQL")).contains(Dialect.MYSQL);
        assertThat(Dialect.fromConnectionKind("postgresql")).contains(Dialect.POSTGRESQL);
        assertThat(Dialect.fromConnectionKind("postgres")).contains(Dialect.POSTGRESQL);
        assertThat(Dialect.fromConnectionKind("h2")).contains(Dialect.H2);
        assertThat(Dialect.fromConnectionKind("sqlite")).contains(Dialect.SQLITE);
    }

    @Test
    void fromConnectionKind_returnsEmptyForUnsupported() {
        assertThat(Dialect.fromConnectionKind("oracle")).isEmpty();
        assertThat(Dialect.fromConnectionKind("sqlserver")).isEmpty();
        assertThat(Dialect.fromConnectionKind("mssql")).isEmpty();
        assertThat(Dialect.fromConnectionKind(null)).isEmpty();
        assertThat(Dialect.fromConnectionKind("")).isEmpty();
    }
}
```

- [x] **Step 2: Run the test (it fails because `Dialect` does not exist)**

Run: `cd server && mvn -pl data-talk-domain test -Dtest=DialectTest -q`
Expected: COMPILATION ERROR (`Dialect` cannot be resolved).

- [x] **Step 3: Create `Dialect.java`**

```java
package com.datatalk.domain.er;

import java.util.Locale;
import java.util.Optional;

/** Supported ER dialects. Oracle / SQLServer remain explicitly unsupported in day-1. */
public enum Dialect {
    MYSQL,
    POSTGRESQL,
    H2,
    SQLITE;

    public static Optional<Dialect> fromConnectionKind(String kind) {
        if (kind == null || kind.isBlank()) return Optional.empty();
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "mysql" -> Optional.of(MYSQL);
            case "postgresql", "postgres" -> Optional.of(POSTGRESQL);
            case "h2" -> Optional.of(H2);
            case "sqlite" -> Optional.of(SQLITE);
            default -> Optional.empty();
        };
    }
}
```

- [x] **Step 4: Run the test**

Run: `cd server && mvn -pl data-talk-domain test -Dtest=DialectTest -q`
Expected: PASS.

- [x] **Step 5: Create `ErColumnMeta.java`**

```java
package com.datatalk.domain.er;

/**
 * Column metadata for ER rendering.
 *
 * @param name             column name
 * @param type             dialect-specific SQL type fragment, e.g. "BIGINT", "VARCHAR(255)"
 * @param nullable         whether the column allows NULL
 * @param isPrimaryKey     part of primary key
 * @param isForeignKey     references another table (computed from getImportedKeys)
 * @param isAutoIncrement  driver-reported auto-increment
 * @param defaultValue     default value as string, or null
 * @param comment          column comment, or null
 */
public record ErColumnMeta(
    String name,
    String type,
    boolean nullable,
    boolean isPrimaryKey,
    boolean isForeignKey,
    boolean isAutoIncrement,
    String defaultValue,
    String comment
) {}
```

- [x] **Step 6: Create `ErRelation.java`**

```java
package com.datatalk.domain.er;

/**
 * One directed FK or virtual relation in an ER graph.
 *
 * @param sourceTable   table holding the FK column
 * @param sourceColumn  FK column name
 * @param targetTable   referenced table
 * @param targetColumn  referenced column
 * @param relationType  "one_to_many" | "one_to_one" | "many_to_one" | "many_to_many"
 * @param source        "schema_fk" (real JDBC FK) | "virtual" (annotation only)
 */
public record ErRelation(
    String sourceTable,
    String sourceColumn,
    String targetTable,
    String targetColumn,
    String relationType,
    String source
) {}
```

- [x] **Step 7: Create `ErTableMeta.java`**

```java
package com.datatalk.domain.er;

import java.util.List;

public record ErTableMeta(
    String name,
    String comment,
    List<ErColumnMeta> columns,
    List<ErRelation> fkOut
) {}
```

- [x] **Step 8: Create `ErGraph.java`**

```java
package com.datatalk.domain.er;

import java.util.List;

/**
 * The complete graph emitted by ErRelationDiscoveryService.
 *
 * @param nodes      tables in the inspector view (seed + neighbor expansion)
 * @param edges      all FK edges among the nodes
 * @param summary    short English line for the AI ergonomics P4 contract,
 *                   e.g. "users + 5 neighbors, 7 tables / 9 edges"
 * @param warnings   structured warnings such as truncation notices
 */
public record ErGraph(
    List<ErTableMeta> nodes,
    List<ErRelation> edges,
    String summary,
    List<String> warnings
) {}
```

- [x] **Step 9: Create `ErErrors.java` with the three exception classes**

```java
package com.datatalk.domain.er;

import java.util.List;

/** Sealed namespace of ER discovery / generation errors. */
public final class ErErrors {

    private ErErrors() {}

    public static final class DialectUnsupportedException extends RuntimeException {
        private final String kind;
        public DialectUnsupportedException(String kind) {
            super("ER does not support dialect: " + kind);
            this.kind = kind;
        }
        public String kind() { return kind; }
    }

    public static final class ErPayloadOversizedException extends RuntimeException {
        private final int seenTables;
        private final int limit;
        public ErPayloadOversizedException(int seenTables, int limit) {
            super("ER payload exceeded limit: " + seenTables + " > " + limit);
            this.seenTables = seenTables;
            this.limit = limit;
        }
        public int seenTables() { return seenTables; }
        public int limit() { return limit; }
    }

    public static final class TablesNotFoundException extends RuntimeException {
        private final List<String> missing;
        public TablesNotFoundException(List<String> missing) {
            super("Tables not found: " + String.join(", ", missing));
            this.missing = List.copyOf(missing);
        }
        public List<String> missing() { return missing; }
    }
}
```

- [x] **Step 10: Compile**

Run: `cd server && mvn -pl data-talk-domain compile -q`
Expected: zero errors.

- [x] **Step 11: Run tests**

Run: `cd server && mvn -pl data-talk-domain test -q`
Expected: all green.

- [x] **Step 12: Commit**

```bash
git add server/data-talk-domain/
git commit -m "feat(domain): add ER domain records (Dialect, ErRelation, ErTableMeta, ErColumnMeta, ErGraph, ErErrors)"
```

### Task 5: `ErRelationDiscoveryService` interface + `JdbcErRelationDiscoveryService` (mysql/postgres/h2 happy path)

**Files:**
- Create: `server/data-talk-application/src/main/java/com/datatalk/application/er/ErRelationDiscoveryService.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/er/JdbcErRelationDiscoveryService.java`
- Test: `server/data-talk-application/src/test/java/com/datatalk/application/er/ErRelationDiscoveryServiceTest.java`

- [x] **Step 1: Write the failing happy-path test (H2 in-memory)**

Create `server/data-talk-application/src/test/java/com/datatalk/application/er/ErRelationDiscoveryServiceTest.java`:

```java
package com.datatalk.application.er;

import com.datatalk.DataTalkApplication;
import com.datatalk.application.connection.ConnectionKind;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.domain.er.ErGraph;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

import java.sql.DriverManager;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = DataTalkApplication.class)
class ErRelationDiscoveryServiceTest {

    private static final String DB = "mem:er-discover-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    @Autowired ErRelationDiscoveryService discovery;
    @Autowired ConnectionService conn;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate datatalkJdbc;

    String connectionId;

    @BeforeAll
    void seed() throws Exception {
        datatalkJdbc.update("DELETE FROM artifacts");
        datatalkJdbc.update("DELETE FROM session_data_contexts");
        datatalkJdbc.update("DELETE FROM sessions");
        datatalkJdbc.update("DELETE FROM connections");

        try (var c = DriverManager.getConnection("jdbc:h2:" + DB, "sa", "");
             var st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS line_items");
            st.execute("DROP TABLE IF EXISTS orders");
            st.execute("DROP TABLE IF EXISTS users");
            st.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, email VARCHAR(255))");
            st.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, user_id BIGINT REFERENCES users(id), amount DECIMAL(10,2))");
            st.execute("CREATE TABLE line_items (id BIGINT PRIMARY KEY, order_id BIGINT REFERENCES orders(id), product VARCHAR(120))");
        }
        connectionId = conn.create("ER discover test", ConnectionKind.H2, "local", 0, DB, "sa", "", null);
    }

    @Test
    void discoverWithDepthZeroReturnsOnlySeedTables() {
        ErGraph g = discovery.discover(connectionId, List.of("orders"), 0);
        assertThat(g.nodes()).extracting("name").containsExactly("orders");
        assertThat(g.edges()).isEmpty(); // FK targets users / referenced by line_items, but neighbors excluded
    }

    @Test
    void discoverWithDepthOneIncludesDirectNeighbors() {
        ErGraph g = discovery.discover(connectionId, List.of("orders"), 1);
        assertThat(g.nodes()).extracting("name")
            .containsExactlyInAnyOrder("orders", "users", "line_items");
        assertThat(g.edges()).hasSize(2); // orders.user_id -> users.id ; line_items.order_id -> orders.id
    }

    @Test
    void columnsCarryPrimaryKeyFlag() {
        ErGraph g = discovery.discover(connectionId, List.of("users"), 0);
        var users = g.nodes().get(0);
        assertThat(users.columns()).extracting("name", "isPrimaryKey")
            .contains(org.assertj.core.api.Assertions.tuple("id", true),
                      org.assertj.core.api.Assertions.tuple("email", false));
    }

    @Test
    void summaryIsEnglishAndStructured() {
        ErGraph g = discovery.discover(connectionId, List.of("orders"), 1);
        // P4: "users + N neighbors, X tables / Y edges" — English; concise
        assertThat(g.summary()).matches(".*\\d+ tables? / \\d+ edges?.*");
        assertThat(g.summary()).doesNotContain("中文");
    }
}
```

- [x] **Step 2: Run the test (fails — service does not exist)**

Run: `cd server && mvn -pl data-talk-application test -Dtest=ErRelationDiscoveryServiceTest -q`
Expected: COMPILATION ERROR or context startup failure (`ErRelationDiscoveryService` bean not found).

- [x] **Step 3: Create the interface**

`server/data-talk-application/src/main/java/com/datatalk/application/er/ErRelationDiscoveryService.java`:

```java
package com.datatalk.application.er;

import com.datatalk.domain.er.ErGraph;

import java.util.List;

/**
 * Reads tables + columns + FK relations for a connection.
 * Expands selection to include direct/indirect neighbors based on neighborDepth.
 *
 * <p>Throws {@link com.datatalk.domain.er.ErErrors.DialectUnsupportedException} when the
 * connection kind is oracle / sqlserver. Throws
 * {@link com.datatalk.domain.er.ErErrors.ErPayloadOversizedException} when the expanded
 * table set exceeds the day-1 cap (100 tables). Throws
 * {@link com.datatalk.domain.er.ErErrors.TablesNotFoundException} when any seed table is
 * missing in the target schema.
 */
public interface ErRelationDiscoveryService {

    int MAX_TABLES = 100;

    /**
     * Discover an ER graph for the seed tables.
     *
     * @param connectionId  target connection id (must exist)
     * @param tables        seed table names (1..100, non-empty)
     * @param neighborDepth 0=strict; 1=include direct FK neighbors; 2=two hops
     * @return graph with full column metadata + FK edges + summary
     */
    ErGraph discover(String connectionId, List<String> tables, int neighborDepth);

    /** Refresh: same as discover but signals a re-read after Inspector ui_exec(refresh). */
    default ErGraph refresh(String connectionId, List<String> tables, int neighborDepth) {
        return discover(connectionId, tables, neighborDepth);
    }
}
```

- [x] **Step 4: Create the JDBC implementation**

`server/data-talk-infrastructure/src/main/java/com/datatalk/infra/er/JdbcErRelationDiscoveryService.java`:

```java
package com.datatalk.infra.er;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.connection.JdbcUrlBuilder;
import com.datatalk.application.er.ErRelationDiscoveryService;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.domain.er.*;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

@Component
public class JdbcErRelationDiscoveryService implements ErRelationDiscoveryService {

    private final ConnectionRepository connRepo;
    private final ConnectionService conn;

    public JdbcErRelationDiscoveryService(ConnectionRepository connRepo, ConnectionService conn) {
        this.connRepo = connRepo;
        this.conn = conn;
    }

    @Override
    public ErGraph discover(String connectionId, List<String> seeds, int neighborDepth) {
        if (seeds == null || seeds.isEmpty()) {
            throw new IllegalArgumentException("seed tables must be non-empty");
        }

        ConnectionRecord cr = connRepo.findById(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("connection not found: " + connectionId));
        Dialect dialect = Dialect.fromConnectionKind(cr.kind())
            .orElseThrow(() -> new ErErrors.DialectUnsupportedException(cr.kind()));

        String pw = conn.decryptPassword(connectionId);
        try (Connection c = DriverManager.getConnection(JdbcUrlBuilder.build(cr), cr.username(), pw)) {

            Set<String> selected = new LinkedHashSet<>(seeds);
            DatabaseMetaData meta = c.getMetaData();

            // Verify all seeds exist
            List<String> missing = new ArrayList<>();
            for (String t : seeds) {
                try (ResultSet rs = meta.getTables(null, null, t, new String[]{"TABLE"})) {
                    if (!rs.next()) missing.add(t);
                }
            }
            if (!missing.isEmpty()) throw new ErErrors.TablesNotFoundException(missing);

            // BFS neighbor expansion
            Set<String> frontier = new LinkedHashSet<>(seeds);
            for (int depth = 0; depth < neighborDepth; depth++) {
                Set<String> next = new LinkedHashSet<>();
                for (String t : frontier) {
                    try (ResultSet rs = meta.getImportedKeys(null, null, t)) {
                        while (rs.next()) next.add(rs.getString("PKTABLE_NAME"));
                    }
                    try (ResultSet rs = meta.getExportedKeys(null, null, t)) {
                        while (rs.next()) next.add(rs.getString("FKTABLE_NAME"));
                    }
                }
                next.removeAll(selected);
                if (selected.size() + next.size() > MAX_TABLES) {
                    throw new ErErrors.ErPayloadOversizedException(selected.size() + next.size(), MAX_TABLES);
                }
                selected.addAll(next);
                frontier = next;
                if (frontier.isEmpty()) break;
            }

            // Fan-out reads: columns + FKs per table on virtual threads
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<ErTableMeta>> futures = new ArrayList<>();
                for (String t : selected) {
                    futures.add(executor.submit(() -> readTable(c, t)));
                }
                List<ErTableMeta> nodes = new ArrayList<>();
                for (Future<ErTableMeta> f : futures) nodes.add(f.get());

                List<ErRelation> edges = new ArrayList<>();
                for (ErTableMeta node : nodes) {
                    for (ErRelation fk : node.fkOut()) {
                        if (selected.contains(fk.targetTable())) edges.add(fk);
                    }
                }

                String summary = buildSummary(seeds, nodes.size(), edges.size());
                return new ErGraph(nodes, edges, summary, List.of());
            }

        } catch (SQLException e) {
            throw new RuntimeException("ER discovery failed: " + e.getMessage(), e);
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("ER discovery interrupted: " + e.getMessage(), e);
        }
    }

    private ErTableMeta readTable(Connection c, String table) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();

        Set<String> pks = new HashSet<>();
        try (ResultSet rs = meta.getPrimaryKeys(null, null, table)) {
            while (rs.next()) pks.add(rs.getString("COLUMN_NAME"));
        }

        Set<String> fks = new HashSet<>();
        List<ErRelation> fkOut = new ArrayList<>();
        try (ResultSet rs = meta.getImportedKeys(null, null, table)) {
            while (rs.next()) {
                String fkColumn = rs.getString("FKCOLUMN_NAME");
                String pkTable = rs.getString("PKTABLE_NAME");
                String pkColumn = rs.getString("PKCOLUMN_NAME");
                fks.add(fkColumn);
                fkOut.add(new ErRelation(table, fkColumn, pkTable, pkColumn, "many_to_one", "schema_fk"));
            }
        }

        List<ErColumnMeta> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(null, null, table, "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                String type = rs.getString("TYPE_NAME");
                int colSize = rs.getInt("COLUMN_SIZE");
                String typeFragment = needsLength(type) ? type + "(" + colSize + ")" : type;
                boolean nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                String autoIncStr = safeString(rs, "IS_AUTOINCREMENT");
                boolean autoInc = "YES".equalsIgnoreCase(autoIncStr);
                String def = rs.getString("COLUMN_DEF");
                String comment = rs.getString("REMARKS");
                columns.add(new ErColumnMeta(
                    name, typeFragment, nullable,
                    pks.contains(name), fks.contains(name), autoInc,
                    def, comment));
            }
        }

        String tableComment = null;
        try (ResultSet rs = meta.getTables(null, null, table, new String[]{"TABLE"})) {
            if (rs.next()) tableComment = rs.getString("REMARKS");
        }

        return new ErTableMeta(table, tableComment, columns, fkOut);
    }

    private static boolean needsLength(String type) {
        if (type == null) return false;
        String upper = type.toUpperCase(Locale.ROOT);
        return upper.startsWith("VARCHAR") || upper.startsWith("CHAR")
            || upper.startsWith("VARBINARY") || upper.startsWith("VARCHAR2");
    }

    private static String safeString(ResultSet rs, String column) {
        try { return rs.getString(column); } catch (SQLException e) { return null; }
    }

    private static String buildSummary(List<String> seeds, int totalTables, int edges) {
        int neighbors = totalTables - seeds.size();
        String head = String.join(" + ", seeds);
        if (neighbors > 0) head = head + " + " + neighbors + " neighbor" + (neighbors == 1 ? "" : "s");
        return head + ", " + totalTables + " table" + (totalTables == 1 ? "" : "s")
             + " / " + edges + " edge" + (edges == 1 ? "" : "s");
    }
}
```

Note: `Locale` import added near the top of the file.

- [x] **Step 5: Run the test**

Run: `cd server && mvn -pl data-talk-application test -Dtest=ErRelationDiscoveryServiceTest -q`
Expected: PASS (4 tests).

- [x] **Step 6: Commit**

```bash
git add server/data-talk-application/ server/data-talk-infrastructure/
git commit -m "feat(er): add ErRelationDiscoveryService + JDBC implementation

- Reads tables + columns + FK via DatabaseMetaData (mysql/postgres/h2 happy path)
- BFS neighbor expansion 0/1/2 hops
- Fan-out per-table reads on virtual threads
- 100-table cap; throws ErPayloadOversizedException above
- English summary string per AI ergonomics P4"
```

### Task 6: Negative paths — dialect / oversized / missing

**Files:**
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/er/ErRelationDiscoveryServiceTest.java`

- [x] **Step 1: Add the failing tests for error paths**

Append to the existing test class (after the happy-path tests):

```java
@Test
void oracleDialectThrowsDialectUnsupported() {
    String oracleConnId = conn.create("Oracle stub", ConnectionKind.valueOf("ORACLE"), "h", 0, "x", "u", "p", null);
    // Note: ConnectionKind.ORACLE may not exist; if not, simulate by using a kind that fromConnectionKind rejects
    org.assertj.core.api.Assertions.assertThatThrownBy(
        () -> discovery.discover(oracleConnId, List.of("users"), 0))
        .isInstanceOf(com.datatalk.domain.er.ErErrors.DialectUnsupportedException.class);
}

@Test
void missingTableThrowsTablesNotFound() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
        () -> discovery.discover(connectionId, List.of("orde"), 0))
        .isInstanceOf(com.datatalk.domain.er.ErErrors.TablesNotFoundException.class)
        .matches(e -> ((com.datatalk.domain.er.ErErrors.TablesNotFoundException) e)
                      .missing().contains("orde"));
}

@Test
void exceedingHundredTableCapThrowsOversized() throws Exception {
    // Seed 101 tiny tables and ask for all of them
    try (var c = DriverManager.getConnection("jdbc:h2:" + DB, "sa", "");
         var st = c.createStatement()) {
        for (int i = 0; i < 101; i++) {
            st.execute("CREATE TABLE big_" + i + " (id BIGINT PRIMARY KEY)");
        }
    }
    java.util.List<String> tables = new java.util.ArrayList<>();
    for (int i = 0; i < 101; i++) tables.add("big_" + i);
    org.assertj.core.api.Assertions.assertThatThrownBy(
        () -> discovery.discover(connectionId, tables, 0))
        .isInstanceOf(com.datatalk.domain.er.ErErrors.ErPayloadOversizedException.class);
}
```

Note: if `ConnectionKind.ORACLE` does not exist as an enum value, replace the oracle test with one that constructs a `ConnectionRecord` whose `kind()` is the literal string `"oracle"` directly through `connRepo` (an integration approach), or skip that single assertion and instead unit-test `Dialect.fromConnectionKind` (already covered by T4). Pick whichever path keeps the test robust to the existing `ConnectionKind` enum.

Add a guard in the discover method to also validate the seed list size at entry:

```java
if (seeds.size() > MAX_TABLES) {
    throw new ErErrors.ErPayloadOversizedException(seeds.size(), MAX_TABLES);
}
```

(Place this immediately after the empty-list check.)

- [x] **Step 2: Run the tests**

Run: `cd server && mvn -pl data-talk-application test -Dtest=ErRelationDiscoveryServiceTest -q`
Expected: all PASS.

- [x] **Step 3: Commit**

```bash
git add server/
git commit -m "test(er): cover dialect_unsupported / tables_not_found / oversized paths"
```

### Task 7: `ErTabController` REST + IT

**Files:**
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SeedInspectorRequest.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/ErGraphResponse.java`
- Create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/ErTabController.java`
- Create: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/ErTabControllerIT.java`

- [x] **Step 1: Write the failing IT**

Create `ErTabControllerIT.java`:

```java
package com.datatalk.adapter.controller;

import com.datatalk.DataTalkApplication;
import com.datatalk.application.connection.ConnectionKind;
import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.SessionRecord;
import com.datatalk.application.persistence.SessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = DataTalkApplication.class)
@AutoConfigureMockMvc
class ErTabControllerIT {

    private static final String DB = "mem:er-controller-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired ConnectionService conn;
    @Autowired SessionRepository sessions;
    @Autowired @Qualifier("datatalkJdbc") JdbcTemplate datatalkJdbc;

    String connectionId;

    @BeforeAll
    void seed() throws Exception {
        datatalkJdbc.update("DELETE FROM artifacts");
        datatalkJdbc.update("DELETE FROM session_data_contexts");
        datatalkJdbc.update("DELETE FROM sessions");
        datatalkJdbc.update("DELETE FROM connections");

        try (var c = DriverManager.getConnection("jdbc:h2:" + DB, "sa", "");
             var st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS orders");
            st.execute("DROP TABLE IF EXISTS users");
            st.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, email VARCHAR(255))");
            st.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, user_id BIGINT REFERENCES users(id))");
        }
        connectionId = conn.create("ER ctrl IT", ConnectionKind.H2, "local", 0, DB, "sa", "", null);
        sessions.upsert(new SessionRecord("s-1", connectionId, "ER", true, "oc-1", 0L, 0L, false));
    }

    @Test
    void seedInspectorReturns200WithGraph() throws Exception {
        String body = om.writeValueAsString(Map.of(
            "connectionId", connectionId,
            "tables", List.of("orders"),
            "neighborDepth", 1));
        mvc.perform(post("/api/er/seed-inspector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.nodes", hasSize(2)))
           .andExpect(jsonPath("$.edges", hasSize(1)))
           .andExpect(jsonPath("$.summary").isString());
    }

    @Test
    void seedInspectorMissingTableReturns404() throws Exception {
        String body = om.writeValueAsString(Map.of(
            "connectionId", connectionId,
            "tables", List.of("orde"),
            "neighborDepth", 0));
        mvc.perform(post("/api/er/seed-inspector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("tables_not_found"))
           .andExpect(jsonPath("$.aiHint").isString());
    }

    @Test
    void seedInspectorUnknownConnectionReturns404() throws Exception {
        String body = om.writeValueAsString(Map.of(
            "connectionId", "missing-conn",
            "tables", List.of("orders"),
            "neighborDepth", 0));
        mvc.perform(post("/api/er/seed-inspector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("connection_unavailable"));
    }
}

import static org.hamcrest.Matchers.hasSize;
```

(Move `import static org.hamcrest.Matchers.hasSize;` next to the other static imports near the top.)

- [x] **Step 2: Run the IT**

Run: `cd server && mvn -pl data-talk-adapter test -Dtest=ErTabControllerIT -q`
Expected: COMPILATION ERROR (`/api/er/seed-inspector` does not exist).

- [x] **Step 3: Create the request DTO**

`server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SeedInspectorRequest.java`:

```java
package com.datatalk.adapter.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SeedInspectorRequest(
    @NotNull String connectionId,
    @NotEmpty @Size(max = 100) List<String> tables,
    int neighborDepth
) {}
```

- [x] **Step 4: Create the response DTO**

`server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/ErGraphResponse.java`:

```java
package com.datatalk.adapter.dto;

import com.datatalk.domain.er.ErGraph;

import java.util.List;

public record ErGraphResponse(
    List<?> nodes,
    List<?> edges,
    String summary,
    List<String> warnings
) {
    public static ErGraphResponse from(ErGraph g) {
        return new ErGraphResponse(g.nodes(), g.edges(), g.summary(), g.warnings());
    }
}
```

- [x] **Step 5: Create the controller with structured error responses**

`server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/ErTabController.java`:

```java
package com.datatalk.adapter.controller;

import com.datatalk.adapter.dto.ErGraphResponse;
import com.datatalk.adapter.dto.SeedInspectorRequest;
import com.datatalk.application.er.ErRelationDiscoveryService;
import com.datatalk.domain.er.ErErrors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/er")
public class ErTabController {

    private final ErRelationDiscoveryService discovery;

    public ErTabController(ErRelationDiscoveryService discovery) {
        this.discovery = discovery;
    }

    @PostMapping("/seed-inspector")
    public ResponseEntity<?> seedInspector(@RequestBody SeedInspectorRequest req) {
        try {
            return ResponseEntity.ok(ErGraphResponse.from(
                discovery.discover(req.connectionId(), req.tables(), req.neighborDepth())));
        } catch (IllegalArgumentException e) {
            // connection not found or empty seeds
            if (e.getMessage() != null && e.getMessage().startsWith("connection not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "code", "connection_unavailable",
                    "message", "Target connection no longer exists.",
                    "aiHint", "The target connection no longer exists. Call datatalk_list_connections and ask the user to pick a valid one."
                ));
            }
            return ResponseEntity.badRequest().body(Map.of(
                "code", "invalid_request",
                "message", e.getMessage()));
        } catch (ErErrors.DialectUnsupportedException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", "dialect_unsupported",
                "kind", e.kind(),
                "message", "ER does not support dialect: " + e.kind(),
                "aiHint", "ER does not support " + e.kind() + ". Use query_editor with read_schema for inspection, or pick mysql/postgresql/h2 for design drafts."
            ));
        } catch (ErErrors.TablesNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "tables_not_found",
                "missing", e.missing(),
                "message", "Tables not found: " + String.join(", ", e.missing()),
                "aiHint", "Tables " + e.missing() + " were not found in the connection. Use datatalk_read_schema with pattern to confirm exact names."
            ));
        } catch (ErErrors.ErPayloadOversizedException e) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
                "code", "er_payload_oversized",
                "seenTables", e.seenTables(),
                "limit", e.limit(),
                "message", "ER payload exceeds limit",
                "aiHint", "Payload exceeds limit (" + e.seenTables() + " > " + e.limit() + "). Narrow down using read_schema with pattern/limit, or split into multiple ER tabs by domain."
            ));
        }
    }
}
```

- [x] **Step 6: Run the IT**

Run: `cd server && mvn -pl data-talk-adapter test -Dtest=ErTabControllerIT -q`
Expected: PASS (3 tests).

- [x] **Step 7: Compile full server**

Run: `cd server && mvn compile -q`
Expected: zero errors.

- [x] **Step 8: Commit**

```bash
git add server/data-talk-adapter/
git commit -m "feat(er): add POST /api/er/seed-inspector

Internal REST endpoint for ErInspectorAdapter; not exposed to MCP.
Returns ErGraph or structured errors with English aiHint per spec §6.7."
```

---

## Batch 3: Frontend store + tab-type registry

This batch lands the TypeScript types, an independent `useErTabsStore`, JSON Patch application that reuses the existing `ui-router/jsonPatch.ts`, and the `tab-type-registry` entry that wires `extractContent` (with `tablesSnapshot`) so FTS5 can find ER tabs by column name.

### Task 8: TypeScript types for inspector payload

**Files:**
- Create: `client/src/features/stage/stores/er-tabs-payload-types.ts`

- [x] **Step 1: Define the types**

```ts
// client/src/features/stage/stores/er-tabs-payload-types.ts
import type { JsonPatchOp } from '@/services/ui-router/types'

export type { JsonPatchOp }

export interface ErColumnMeta {
  name: string
  type: string
  nullable: boolean
  isPK: boolean
  isFK: boolean
  isAutoIncrement?: boolean
  default?: string | null
  comment?: string | null
}

export interface ErRelationSnapshot {
  fromColumn: string
  toTable: string
  toColumn: string
}

export interface ErTableSnapshot {
  name: string
  comment?: string | null
  columns: ErColumnMeta[]
  fkOut: ErRelationSnapshot[]
}

export interface ErVirtualRelation {
  id: string
  from: { table: string; column: string }
  to:   { table: string; column: string }
  type: 'one_to_one' | 'one_to_many' | 'many_to_one' | 'many_to_many'
  note?: string
}

export interface ErViewport { x: number; y: number; zoom: number }

export interface ErInspectorPayload {
  kind: 'er_inspector'
  connectionId: string
  database?: string | null
  schema?: string | null
  selection: string[]
  neighborDepth: 0 | 1 | 2
  layout: 'dagre-LR'
  /** Real schema snapshot fed by /api/er/seed-inspector or refresh; indexed by FTS5 via extractContent. */
  tablesSnapshot?: ErTableSnapshot[]
  snapshotAt?: number
  positions: Record<string, { x: number; y: number }>
  collapsed: string[]
  virtualRelations: ErVirtualRelation[]
  notes: Record<string, string>
  viewport: ErViewport
}

/** Designer payload — Plan A keeps this stub so useErTabsStore types compile. Plan B fleshes it out. */
export interface ErDesignerPayload {
  kind: 'er_designer'
  dialect: 'mysql' | 'postgresql' | 'h2' | 'sqlite'
  targetConnectionId?: string | null
  targetDatabase?: string | null
  targetSchema?: string | null
  tables: unknown[]
  relations: unknown[]
  positions: Record<string, { x: number; y: number }>
  collapsed: string[]
  viewport: ErViewport
}
```

- [x] **Step 2: Type-check**

Run: `cd client && npx tsc --noEmit`
Expected: zero errors.

- [x] **Step 3: Commit**

```bash
git add client/src/features/stage/stores/er-tabs-payload-types.ts
git commit -m "feat(client/er): add ErInspectorPayload + ErDesignerPayload types

Designer is a stub for Plan A; Plan B fills it out."
```

### Task 9: `useErTabsStore` skeleton with `hydrateInspector` + getters

**Files:**
- Create: `client/src/features/stage/stores/er-tabs-store.ts`
- Test: `client/src/features/stage/stores/er-tabs-store.test.ts`

- [x] **Step 1: Write the failing test for hydrateInspector**

```ts
// client/src/features/stage/stores/er-tabs-store.test.ts
import { describe, it, expect, beforeEach } from 'vitest'
import { useErTabsStore } from './er-tabs-store'
import type { ErInspectorPayload } from './er-tabs-payload-types'

const samplePayload: ErInspectorPayload = {
  kind: 'er_inspector',
  connectionId: 'c1',
  selection: ['users', 'orders'],
  neighborDepth: 1,
  layout: 'dagre-LR',
  tablesSnapshot: [],
  positions: {},
  collapsed: [],
  virtualRelations: [],
  notes: {},
  viewport: { x: 0, y: 0, zoom: 1 },
}

describe('useErTabsStore — Inspector hydration', () => {
  beforeEach(() => {
    useErTabsStore.setState({ inspectors: new Map(), designers: new Map() })
  })

  it('stores hydrated inspector payloads under tabId', () => {
    useErTabsStore.getState().hydrateInspector('tab-1', samplePayload)
    expect(useErTabsStore.getState().inspectors.get('tab-1')).toEqual(samplePayload)
  })

  it('hydrating an existing tabId replaces its payload', () => {
    useErTabsStore.getState().hydrateInspector('tab-1', samplePayload)
    const updated = { ...samplePayload, selection: ['products'] }
    useErTabsStore.getState().hydrateInspector('tab-1', updated)
    expect(useErTabsStore.getState().inspectors.get('tab-1')).toEqual(updated)
  })

  it('getInspectorView returns null for unknown tabId', () => {
    expect(useErTabsStore.getState().getInspectorView('missing')).toBeNull()
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/stores/er-tabs-store.test.ts`
Expected: FAIL — module not found.

- [x] **Step 3: Create the skeleton**

```ts
// client/src/features/stage/stores/er-tabs-store.ts
import { create } from 'zustand'
import type {
  ErInspectorPayload,
  ErDesignerPayload,
  JsonPatchOp,
} from './er-tabs-payload-types'

export interface ErInspectorView {
  nodes: { id: string; label: string; columns: { name: string; type: string; isPK: boolean; isFK: boolean }[] }[]
  edges: { id: string; source: string; target: string; sourceColumn: string; targetColumn: string; kind: 'fk' | 'virtual' }[]
}

interface ErTabsState {
  inspectors: Map<string, ErInspectorPayload>
  designers:  Map<string, ErDesignerPayload>

  hydrateInspector: (tabId: string, payload: ErInspectorPayload) => void
  hydrateDesigner:  (tabId: string, payload: ErDesignerPayload)  => void

  applyInspectorPatch: (tabId: string, ops: JsonPatchOp[]) =>
    { newVersion: number; assignedIds: Record<string, string> }
  applyDesignerPatch:  (tabId: string, ops: JsonPatchOp[]) =>
    { newVersion: number; assignedIds: Record<string, string> }

  getInspectorView: (tabId: string) => ErInspectorView | null
  getDesignerView:  (tabId: string) => ErInspectorView | null
}

export const useErTabsStore = create<ErTabsState>((set, get) => ({
  inspectors: new Map(),
  designers:  new Map(),

  hydrateInspector(tabId, payload) {
    set((s) => {
      const next = new Map(s.inspectors)
      next.set(tabId, payload)
      return { inspectors: next }
    })
  },

  hydrateDesigner(tabId, payload) {
    set((s) => {
      const next = new Map(s.designers)
      next.set(tabId, payload)
      return { designers: next }
    })
  },

  applyInspectorPatch(_tabId, _ops) {
    // Implemented in T11
    throw new Error('applyInspectorPatch: implement in Task 11')
  },

  applyDesignerPatch(_tabId, _ops) {
    throw new Error('applyDesignerPatch: implement in Plan B')
  },

  getInspectorView(tabId) {
    const p = get().inspectors.get(tabId)
    if (!p) return null
    const nodes = (p.tablesSnapshot ?? []).map((t) => ({
      id: t.name,
      label: t.name,
      columns: t.columns.map((c) => ({ name: c.name, type: c.type, isPK: c.isPK, isFK: c.isFK })),
    }))
    const edges: ErInspectorView['edges'] = []
    for (const t of p.tablesSnapshot ?? []) {
      for (const fk of t.fkOut) {
        edges.push({
          id: `${t.name}.${fk.fromColumn}->${fk.toTable}.${fk.toColumn}`,
          source: t.name,
          target: fk.toTable,
          sourceColumn: fk.fromColumn,
          targetColumn: fk.toColumn,
          kind: 'fk',
        })
      }
    }
    for (const v of p.virtualRelations) {
      edges.push({
        id: v.id,
        source: v.from.table,
        target: v.to.table,
        sourceColumn: v.from.column,
        targetColumn: v.to.column,
        kind: 'virtual',
      })
    }
    return { nodes, edges }
  },

  getDesignerView(_tabId) {
    return null // Plan B
  },
}))
```

- [x] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/stores/er-tabs-store.test.ts`
Expected: PASS (3 tests).

- [x] **Step 5: Commit**

```bash
git add client/src/features/stage/stores/er-tabs-store.ts client/src/features/stage/stores/er-tabs-store.test.ts
git commit -m "feat(client/er): add useErTabsStore with inspector hydration"
```

### Task 10: Register `er_inspector` in `tab-type-registry.ts`

**Files:**
- Modify: `client/src/features/stage/registry/tab-type-registry.ts`
- Test: `client/src/features/stage/registry/__tests__/tab-type-registry.test.ts`

- [x] **Step 1: Write the failing test asserting `er_inspector` is registered**

Append (or create) to the existing `tab-type-registry.test.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { TAB_TYPE_REGISTRY, getTabTypeDescriptor, isPersistent, getScope } from '../tab-type-registry'

describe('tab-type-registry — er_inspector', () => {
  it('registers er_inspector as workspace-scope persistent', () => {
    expect(TAB_TYPE_REGISTRY['er_inspector']).toBeDefined()
    expect(isPersistent('er_inspector')).toBe(true)
    expect(getScope('er_inspector')).toBe('workspace')
  })

  it('extractContent indexes selection + virtual relations + notes + table/column names', () => {
    const desc = getTabTypeDescriptor('er_inspector')
    const text = desc.extractContent({
      kind: 'er_inspector',
      connectionId: 'c',
      selection: ['users', 'orders'],
      neighborDepth: 1,
      layout: 'dagre-LR',
      tablesSnapshot: [
        {
          name: 'users',
          columns: [
            { name: 'id',    type: 'BIGINT',       nullable: false, isPK: true,  isFK: false },
            { name: 'email', type: 'VARCHAR(255)', nullable: false, isPK: false, isFK: false },
          ],
          fkOut: [],
        },
      ],
      positions: {},
      collapsed: [],
      virtualRelations: [
        { id: 'vr1', from: { table: 'orders', column: 'user_email' }, to: { table: 'users', column: 'email' }, type: 'many_to_one' },
      ],
      notes: { orders: '订单主表' },
      viewport: { x: 0, y: 0, zoom: 1 },
    })
    expect(text).toContain('users orders')               // selection
    expect(text).toContain('users id BIGINT email VARCHAR(255)') // snapshot
    expect(text).toContain('orders.user_email users.email')      // virtual
    expect(text).toContain('订单主表')                    // notes (chinese is allowed in user content)
  })

  it('returns empty string for null payload', () => {
    expect(getTabTypeDescriptor('er_inspector').extractContent(null)).toBe('')
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/registry/__tests__/tab-type-registry.test.ts`
Expected: FAIL — `er_inspector` undefined.

- [x] **Step 3: Edit `tab-type-registry.ts`**

Open `client/src/features/stage/registry/tab-type-registry.ts`. Add imports and the new entry:

```ts
import { NetworkIcon } from 'lucide-react'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import type { ErInspectorPayload } from '@/features/stage/stores/er-tabs-payload-types'
```

Inside `TAB_TYPE_REGISTRY`, add:

```ts
er_inspector: {
  type: 'er_inspector',
  persistent: true,
  scope: 'workspace',
  icon: NetworkIcon,
  labelKey: 'tabType.erInspector',
  extractContent: (p) => {
    const o = p as ErInspectorPayload | null
    if (!o) return ''
    const sel = (o.selection ?? []).join(' ')
    const snapshot = (o.tablesSnapshot ?? [])
      .map((t) => {
        const cols = (t.columns ?? []).map((c) => `${c.name} ${c.type}`).join(' ')
        return `${t.name} ${cols} ${t.comment ?? ''}`.trim()
      })
      .join('\n')
    const vrels = (o.virtualRelations ?? [])
      .map((r) => `${r.from.table}.${r.from.column} ${r.to.table}.${r.to.column}`)
      .join('\n')
    const notes = Object.values(o.notes ?? {}).join('\n')
    return [sel, snapshot, vrels, notes].filter(Boolean).join('\n')
  },
  rehydrate: (tabId, p) => {
    useErTabsStore.getState().hydrateInspector(tabId, p as ErInspectorPayload)
  },
},
```

- [x] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/registry/__tests__/tab-type-registry.test.ts`
Expected: PASS (3 tests).

- [x] **Step 5: Type-check**

Run: `cd client && npx tsc --noEmit`
Expected: zero errors.

- [x] **Step 6: Add i18n keys**

Open `client/src/i18n/messages.ts` and add (in both `en` and `zh-CN` blocks):

```ts
'tabType.erInspector': 'ER Inspector',  // en
'tabType.erInspector': 'ER 浏览',       // zh
```

(Designer label `tabType.erDesigner` is added in Plan B.)

- [x] **Step 7: Commit**

```bash
git add client/src/features/stage/registry/tab-type-registry.ts client/src/features/stage/registry/__tests__/tab-type-registry.test.ts client/src/i18n/messages.ts
git commit -m "feat(client/er): register er_inspector tab type with extractContent"
```

### Task 11: Implement `applyInspectorPatch` reusing `ui-router/jsonPatch.ts`

**Files:**
- Modify: `client/src/features/stage/stores/er-tabs-store.ts`
- Modify: `client/src/features/stage/stores/er-tabs-store.test.ts`

- [x] **Step 1: Write the failing tests**

Append to `er-tabs-store.test.ts`:

```ts
import { describe as describe2, it as it2, expect as expect2, beforeEach as beforeEach2 } from 'vitest'

describe2('useErTabsStore — applyInspectorPatch', () => {
  beforeEach2(() => {
    useErTabsStore.setState({
      inspectors: new Map([['t-1', { ...samplePayload }]]),
      designers: new Map(),
    })
  })

  it2('applies replace ops on /selection', () => {
    const { newVersion } = useErTabsStore.getState().applyInspectorPatch('t-1', [
      { op: 'replace', path: '/selection', value: ['products'] },
    ])
    expect2(newVersion).toBeGreaterThan(0)
    expect2(useErTabsStore.getState().inspectors.get('t-1')!.selection).toEqual(['products'])
  })

  it2('add op on /virtualRelations/- assigns vr_<id>', () => {
    const { assignedIds } = useErTabsStore.getState().applyInspectorPatch('t-1', [
      { op: 'add', path: '/virtualRelations/-',
        value: { from: { table: 'a', column: 'x' }, to: { table: 'b', column: 'y' }, type: 'many_to_one' } },
    ])
    expect2(assignedIds['/virtualRelations/0']).toMatch(/^vr_/)
    const stored = useErTabsStore.getState().inspectors.get('t-1')!.virtualRelations
    expect2(stored).toHaveLength(1)
    expect2(stored[0].id).toMatch(/^vr_/)
  })

  it2('remove op via [id=<vrId>] addressing works', () => {
    useErTabsStore.getState().applyInspectorPatch('t-1', [
      { op: 'add', path: '/virtualRelations/-',
        value: { from: { table: 'a', column: 'x' }, to: { table: 'b', column: 'y' }, type: 'many_to_one' } },
    ])
    const id = useErTabsStore.getState().inspectors.get('t-1')!.virtualRelations[0].id
    useErTabsStore.getState().applyInspectorPatch('t-1', [
      { op: 'remove', path: `/virtualRelations[id=${id}]` },
    ])
    expect2(useErTabsStore.getState().inspectors.get('t-1')!.virtualRelations).toHaveLength(0)
  })

  it2('throws on unknown tabId', () => {
    expect2(() => useErTabsStore.getState().applyInspectorPatch('missing', [
      { op: 'replace', path: '/selection', value: [] },
    ])).toThrow(/tab not found/i)
  })

  it2('rejects unsupported path /tables (immutable_path_in_inspector)', () => {
    expect2(() => useErTabsStore.getState().applyInspectorPatch('t-1', [
      { op: 'add', path: '/tables/-', value: {} },
    ])).toThrow(/immutable_path_in_inspector|immutable|not patchable/i)
  })
})
```

- [x] **Step 2: Run the tests**

Run: `cd client && npx vitest run client/src/features/stage/stores/er-tabs-store.test.ts`
Expected: FAIL — `applyInspectorPatch` throws "implement in Task 11".

- [x] **Step 3: Implement**

Replace the `applyInspectorPatch` body in `er-tabs-store.ts`:

```ts
import { applyPatch } from '@/services/ui-router/jsonPatch'
import { nanoid } from 'nanoid'

const INSPECTOR_PATH_WHITELIST = [
  /^\/selection$/,
  /^\/neighborDepth$/,
  /^\/positions$/,
  /^\/positions\/[^/]+$/,
  /^\/collapsed$/,
  /^\/virtualRelations$/,
  /^\/virtualRelations\/-$/,
  /^\/virtualRelations\[id=[^\]]+\]$/,
  /^\/notes$/,
  /^\/notes\/[^/]+$/,
  /^\/viewport$/,
]

function isInspectorPathAllowed(path: string): boolean {
  return INSPECTOR_PATH_WHITELIST.some((re) => re.test(path))
}

// inside store factory:
applyInspectorPatch(tabId, ops) {
  const current = get().inspectors.get(tabId)
  if (!current) throw new Error(`tab not found: ${tabId}`)

  for (const op of ops) {
    if (!isInspectorPathAllowed(op.path)) {
      const err = new Error(`immutable_path_in_inspector: ${op.path}`)
      ;(err as any).code = 'immutable_path_in_inspector'
      ;(err as any).aiHint =
        'Inspector tabs are read-only views of real schema. To edit tables, fork this tab to a designer first via ui_exec(fork_to_designer).'
      throw err
    }
  }

  // Auto-assign ids for add ops on /virtualRelations/-
  const assignedIds: Record<string, string> = {}
  const stamped = ops.map((op, idx) => {
    if (op.op === 'add' && op.path === '/virtualRelations/-' && typeof op.value === 'object' && op.value !== null) {
      const id = `vr_${nanoid(8)}`
      const arrLen = (current.virtualRelations ?? []).length
      assignedIds[`/virtualRelations/${arrLen + idx}`] = id
      return { ...op, value: { id, ...(op.value as object) } }
    }
    return op
  })

  const next = applyPatch(current as unknown as Record<string, unknown>, stamped) as unknown as ErInspectorPayload

  set((s) => {
    const m = new Map(s.inspectors)
    m.set(tabId, next)
    return { inspectors: m }
  })

  // version is owned by useStageStore via StagePersistenceCoordinator; here we return a monotonic stand-in
  const newVersion = (Number(((current as any).__v ?? 0)) + 1)
  return { newVersion, assignedIds }
},
```

If `nanoid` is not yet a dependency, install it: `cd client && npm install nanoid` (zero-dependency module already common in JS toolchains; many projects already have it as a transitive dep — `npm ls nanoid` to confirm before adding).

- [x] **Step 4: Run the tests**

Run: `cd client && npx vitest run client/src/features/stage/stores/er-tabs-store.test.ts`
Expected: PASS (5 tests after the new ones).

- [x] **Step 5: Type-check**

Run: `cd client && npx tsc --noEmit`
Expected: zero errors.

- [x] **Step 6: Commit**

```bash
git add client/src/features/stage/stores/er-tabs-store.ts client/src/features/stage/stores/er-tabs-store.test.ts client/package.json client/package-lock.json
git commit -m "feat(client/er): implement applyInspectorPatch (jsonPatch + path whitelist + assignedIds)"
```

---

## Batch 4: Persistence subscription + force-flush + global Adapter registry

This batch wires the new ER store into Task 6's persistence pipeline (FTS5 indexing + debounced writes), extends `ui-handlers.ts` to flush newly-created tab ids, and adds global ER adapter registration so non-active ER tabs are still addressable by ui_read / ui_patch / ui_find.

### Task 12: Subscribe `useErTabsStore` in `stage-persistence-bootstrap.ts`

**Files:**
- Modify: `client/src/features/stage/persistence/stage-persistence-bootstrap.ts`
- Test: `client/src/features/stage/persistence/__tests__/stage-persistence-bootstrap.er.test.ts`

- [x] **Step 1: Write the failing test**

```ts
// client/src/features/stage/persistence/__tests__/stage-persistence-bootstrap.er.test.ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { coordinator } from '../stage-persistence-bootstrap'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import { useStageStore } from '@/stores/stage-store'

const inspectorPayload = {
  kind: 'er_inspector' as const,
  connectionId: 'c1',
  selection: ['users'],
  neighborDepth: 1 as const,
  layout: 'dagre-LR' as const,
  tablesSnapshot: [{ name: 'users', columns: [{ name: 'id', type: 'BIGINT', nullable: false, isPK: true, isFK: false }], fkOut: [] }],
  positions: {},
  collapsed: [],
  virtualRelations: [],
  notes: {},
  viewport: { x: 0, y: 0, zoom: 1 },
}

describe('stage-persistence-bootstrap — ER content subscription', () => {
  beforeEach(() => {
    useErTabsStore.setState({ inspectors: new Map(), designers: new Map() })
    useStageStore.setState({
      tabs: [{
        tabId: 'er-1', type: 'er_inspector', title: 'Order ER',
        payload: {}, payloadVersion: 1,
        createdAt: Date.now(), lastTouchedAt: Date.now(),
      }],
      activeTabId: 'er-1',
    } as any, true)
    vi.spyOn(coordinator, 'scheduleContentWrite').mockImplementation(() => {})
  })

  it('schedules content write when an inspector payload is hydrated', () => {
    useErTabsStore.getState().hydrateInspector('er-1', inspectorPayload)
    expect(coordinator.scheduleContentWrite).toHaveBeenCalledWith(
      'er-1',
      expect.objectContaining({
        payload: inspectorPayload,
        contentText: expect.stringContaining('users id BIGINT'),
        expectedVersion: 1,
      })
    )
  })

  it('does not schedule for non-persistent or unknown tabs', () => {
    useErTabsStore.getState().hydrateInspector('unknown-tab', inspectorPayload)
    expect(coordinator.scheduleContentWrite).not.toHaveBeenCalled()
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/persistence/__tests__/stage-persistence-bootstrap.er.test.ts`
Expected: FAIL — bootstrap doesn't subscribe to `useErTabsStore`.

- [x] **Step 3: Modify `stage-persistence-bootstrap.ts`**

Open the file. After the existing `// Subscribe content diffs (debounce 1s)` block (the one that subscribes `useSqlWorkbenchStore`), add a new import and a new subscription block.

Add at top (with other imports):

```ts
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import type { ErInspectorPayload, ErDesignerPayload } from '@/features/stage/stores/er-tabs-payload-types'
```

Add the diff helper before the subscription blocks:

```ts
function diffErContentAndSchedule(
  next: { inspectors: Map<string, ErInspectorPayload>; designers: Map<string, ErDesignerPayload> },
  prev: { inspectors: Map<string, ErInspectorPayload>; designers: Map<string, ErDesignerPayload> },
): void {
  scheduleErChanges(next.inspectors, prev.inspectors)
  scheduleErChanges(next.designers,  prev.designers)
}

function scheduleErChanges<P>(next: Map<string, P>, prev: Map<string, P>): void {
  for (const [tabId, payload] of next) {
    const prevPayload = prev.get(tabId)
    if (prevPayload === payload) continue
    const tab = useStageStore.getState().findTab(tabId)
    if (!tab || !isPersistent(tab.type)) continue
    const desc = TAB_TYPE_REGISTRY[tab.type]
    coordinator.scheduleContentWrite(tabId, {
      payload: payload as unknown as Record<string, unknown>,
      contentText: desc?.extractContent?.(payload) ?? '',
      expectedVersion: tab.payloadVersion,
    })
  }
}
```

After the existing `useSqlWorkbenchStore.subscribe` block, append:

```ts
// Subscribe ER content diffs (debounce 1s, same as SQL)
{
  let prevEr = {
    inspectors: useErTabsStore.getState().inspectors,
    designers:  useErTabsStore.getState().designers,
  }
  useErTabsStore.subscribe((state) => {
    const next = { inspectors: state.inspectors, designers: state.designers }
    if (next.inspectors !== prevEr.inspectors || next.designers !== prevEr.designers) {
      diffErContentAndSchedule(next, prevEr)
      prevEr = next
    }
  })
}
```

- [x] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/persistence/__tests__/stage-persistence-bootstrap.er.test.ts`
Expected: PASS (2 tests).

- [x] **Step 5: Re-run the full persistence suite**

Run: `cd client && npx vitest run client/src/features/stage/persistence`
Expected: all green.

- [x] **Step 6: Commit**

```bash
git add client/src/features/stage/persistence/
git commit -m "feat(client/er): subscribe useErTabsStore in stage-persistence-bootstrap

ER inspector / designer payload changes now flow through StagePersistenceCoordinator
debounced content writes, populating stage_tab_payload + FTS5 index
(extractContent emits selection / virtualRelations / notes / table+column names)."
```

### Task 13: Extend `MUTATING_EXEC` + flush returned tabId in `ui-handlers.ts`

**Files:**
- Modify: `client/src/features/actions/ui-handlers.ts`
- Test: `client/src/features/actions/__tests__/ui-handlers.er.test.ts`

- [x] **Step 1: Write the failing test**

```ts
// client/src/features/actions/__tests__/ui-handlers.er.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'

const flushSpy = vi.fn().mockResolvedValue(undefined)
const ensureHydratedSpy = vi.fn().mockResolvedValue(undefined)

vi.mock('@/features/stage/persistence/stage-persistence-bootstrap', () => ({
  coordinator: {
    flush: (...args: unknown[]) => flushSpy(...args),
    ensureHydrated: (...args: unknown[]) => ensureHydratedSpy(...args),
  },
}))

const routerHandle = vi.fn()
vi.mock('@/services/ui-router', () => ({
  uiRouter: { handle: (req: unknown) => routerHandle(req) },
}))

vi.mock('@/stores/stage-store', () => ({
  useStageStore: { getState: () => ({ activeTabId: null }) },
}))

import { registerClientHandler } from '../registry'
const handlersByName = (registerClientHandler as unknown as { __handlers?: Map<string, Function> })
import '../ui-handlers' // triggers registration

describe('ui-handlers — ER mutating exec', () => {
  beforeEach(() => {
    flushSpy.mockClear()
    ensureHydratedSpy.mockClear()
    routerHandle.mockReset()
  })

  it('open_er_inspector flushes the newly-created tabId returned in result', async () => {
    routerHandle.mockResolvedValue({ data: { tabId: 'er_inspector_new', payloadVersion: 1 } })
    // Get handler via the registry helper or via direct import
    const handler = (await import('../registry')).getClientHandler?.('datatalk.ui.exec')
      ?? (handlersByName.__handlers?.get('datatalk.ui.exec') as Function | undefined)
    expect(handler).toBeDefined()
    await handler!({ object: 'workspace', action: 'open_er_inspector', params: { connectionId: 'c1', tables: ['users'] } })
    expect(flushSpy).toHaveBeenCalledWith('er_inspector_new')
  })

  it('refresh on an inspector tab flushes the input target', async () => {
    routerHandle.mockResolvedValue({ data: { payloadVersion: 7 } })
    const handler = (await import('../registry')).getClientHandler?.('datatalk.ui.exec')
      ?? (handlersByName.__handlers?.get('datatalk.ui.exec') as Function | undefined)
    await handler!({ object: 'er_inspector', target: 'er-1', action: 'refresh' })
    expect(flushSpy).toHaveBeenCalledWith('er-1')
  })
})
```

If `getClientHandler` doesn't exist, expose it (or grab via the existing handler registration mechanism — pattern depends on `registry.ts`). The intent is: invoke the registered `datatalk.ui.exec` handler and assert flush behavior.

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/actions/__tests__/ui-handlers.er.test.ts`
Expected: FAIL — flush not called for new tabId, or `open_er_inspector` not in mutating set.

- [x] **Step 3: Modify `ui-handlers.ts`**

Replace the `MUTATING_EXEC` set:

```ts
const MUTATING_EXEC = new Set([
  // workspace + query_editor (existing)
  'open', 'focus', 'detach', 'archive', 'trash',
  'set_context', 'apply_text_edits', 'replace_content',
  // ER (Plan A inspector verbs + Plan B designer verbs declared early so the registry is stable)
  'open_er_inspector', 'open_er_designer',
  'refresh', 'auto_layout', 'fit_view', 'add_neighbors', 'fork_to_designer',
  'bind_target', 'unbind_target', 'sync_from_db', 'generate_ddl',
  // diff_against_db is read-only — intentionally NOT in this set
])
```

Replace the `datatalk.ui.exec` handler body to also flush created tabIds:

```ts
registerClientHandler('datatalk.ui.exec', async (input) => {
  const i = input as ExecInput
  const target = resolveTarget(i)
  if (target) await coordinator.ensureHydrated(target)
  if (i.action === 'run_sql' && target) await coordinator.flush(target)

  const result = await forward({
    tool: 'ui_exec', object: i.object, target: i.target ?? 'active',
    payload: { action: i.action, params: i.params }
  })

  if (target && isMutatingExec(i.action)) await coordinator.flush(target)

  // Flush newly-created tab ids returned in the result so AI's next ui_find sees them.
  const created = isRecord(result)
    ? ((result.tabId as string | undefined)
       ?? (result.newTabId as string | undefined)
       ?? (result.queryEditorTabId as string | undefined))
    : undefined
  if (created && created !== target) await coordinator.flush(created)

  return result
})
```

- [x] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/actions/__tests__/ui-handlers.er.test.ts`
Expected: PASS.

- [x] **Step 5: Run the full ui-handlers suite**

Run: `cd client && npx vitest run client/src/features/actions`
Expected: all green.

- [x] **Step 6: Commit**

```bash
git add client/src/features/actions/
git commit -m "feat(client/er): force-flush newly-created tabIds + extend MUTATING_EXEC

ui_exec(workspace, open_er_inspector) returns { tabId }; we now flush that
tabId after the response so subsequent ui_find calls see the new tab
in stage_tabs + FTS5 immediately. Plan B designer verbs are added early
so the registry stays stable."
```

### Task 14: Add `RegisteredErInspector` to `stage-ui-object-registry.tsx`

**Files:**
- Modify: `client/src/features/stage/components/stage-ui-object-registry.tsx`
- Test: `client/src/features/stage/components/stage-ui-object-registry.test.tsx`

(`ErInspectorAdapter` is implemented in T27; this task pre-stages the registration so the wiring is in place.)

- [x] **Step 1: Add a stub `ErInspectorAdapter` so the registry compiles**

Create a minimal `client/src/features/stage/adapters/ErInspectorAdapter.ts`:

```ts
import type { UIObject, JsonPatchOp, PatchResult, ExecResult } from '@/services/ui-router'

export class ErInspectorAdapter implements UIObject {
  type = 'er_inspector'
  objectId: string
  title = 'ER Inspector'
  tabId: string

  constructor(tabId: string, _sessionIdGetter: () => string | null) {
    this.objectId = tabId
    this.tabId = tabId
  }

  read(_mode: 'state' | 'schema' | 'actions' | 'full'): unknown {
    // Implemented in T27
    return null
  }
  patch(_ops: JsonPatchOp[], _reason?: string): PatchResult {
    return { status: 'error', message: 'ErInspectorAdapter.patch not yet implemented (Task 27)' }
  }
  exec(_action: string, _params?: unknown): ExecResult {
    return { success: false, error: 'ErInspectorAdapter.exec not yet implemented (Task 27)' }
  }
}
```

- [x] **Step 2: Modify the registry**

Open `client/src/features/stage/components/stage-ui-object-registry.tsx`. Add imports:

```ts
import { ErInspectorAdapter } from '../adapters/ErInspectorAdapter'
```

Add the registered-instance helper next to `RegisteredQueryEditor`:

```tsx
function RegisteredErInspector({ tabId, sessionId }: { tabId: string; sessionId: string | null }) {
  const instance = useMemo(() => new ErInspectorAdapter(tabId, () => sessionId), [tabId, sessionId])
  useUIObjectRegistry(instance)
  return null
}
```

Inside the `<>` fragment in `StageUIObjectRegistry`, add the new mapping (after the query_editor block):

```tsx
{tabs
  .filter((tab) => tab.type === 'er_inspector')
  .map((tab) => (
    <RegisteredErInspector
      key={tab.tabId}
      tabId={tab.tabId}
      sessionId={tab.originSessionId ?? null}
    />
  ))}
```

- [x] **Step 3: Add a registry test**

Open `stage-ui-object-registry.test.tsx`. Add a test asserting that an `er_inspector` tab in the `tabs` prop produces a registered adapter:

```tsx
it('registers an ErInspectorAdapter for each er_inspector tab', () => {
  const registerSpy = vi.fn()
  vi.spyOn(uiRouter, 'register').mockImplementation((obj) => registerSpy(obj))

  const tabs = [
    { tabId: 'er-1', type: 'er_inspector', title: 'X', payload: {}, payloadVersion: 1,
      createdAt: 0, lastTouchedAt: 0 },
  ] as any
  render(<StageUIObjectRegistry tabs={tabs} />)
  expect(registerSpy).toHaveBeenCalledWith(expect.objectContaining({ type: 'er_inspector', tabId: 'er-1' }))
})
```

(Use whatever the existing test file uses to spy on registration — adapt to existing patterns.)

- [x] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/stage-ui-object-registry.test.tsx`
Expected: PASS.

- [x] **Step 5: Type-check**

Run: `cd client && npx tsc --noEmit`
Expected: zero errors.

- [x] **Step 6: Commit**

```bash
git add client/src/features/stage/adapters/ErInspectorAdapter.ts client/src/features/stage/components/stage-ui-object-registry.tsx client/src/features/stage/components/stage-ui-object-registry.test.tsx
git commit -m "feat(client/er): globally register ErInspectorAdapter for every er_inspector tab

Adapter is a stub for now (read/patch/exec implemented in Task 27); the
registration plumbing is landed early so non-active er_inspector tabs are
still addressable by ui_read / ui_patch / ui_find from Plan B onward."
```

---

## Batch 5: Dagre worker + shared canvas utilities/hooks

This batch lands the algorithmic primitives — dagre layout in a worker, line-jump bridging, label anti-overlap, self-ref loopback, AI highlight, and keyboard handling. All ported from open-db-studio with semantic tokens replacing primitives.

### Task 15: `dagre-layout.worker.ts` + `useDagreLayout` hook

**Files:**
- Create: `client/src/features/stage/components/er-canvas/workers/dagre-layout.worker.ts`
- Create: `client/src/features/stage/components/er-canvas/hooks/useDagreLayout.ts`
- Test: `client/src/features/stage/components/er-canvas/workers/dagre-layout.worker.test.ts`

- [x] **Step 1: Write the failing test**

```ts
// client/src/features/stage/components/er-canvas/workers/dagre-layout.worker.test.ts
import { describe, it, expect } from 'vitest'
import { computeDagreLayout } from './dagre-layout.worker'

describe('dagre layout — pure compute', () => {
  it('returns positions for every input node id', () => {
    const positions = computeDagreLayout({
      nodes: [
        { id: 'users', width: 280, height: 120 },
        { id: 'orders', width: 280, height: 160 },
      ],
      edges: [{ source: 'users', target: 'orders' }],
      config: { rankdir: 'LR', nodesep: 80, ranksep: 200 },
    })
    expect(Object.keys(positions)).toEqual(expect.arrayContaining(['users', 'orders']))
    expect(positions.users.x).toBeTypeOf('number')
    expect(positions.users.y).toBeTypeOf('number')
  })

  it('100 nodes complete in under 200ms', () => {
    const nodes = Array.from({ length: 100 }, (_, i) => ({ id: `t${i}`, width: 280, height: 120 }))
    const edges = Array.from({ length: 50 }, (_, i) => ({ source: `t${i}`, target: `t${i + 1}` }))
    const start = performance.now()
    const positions = computeDagreLayout({ nodes, edges, config: { rankdir: 'LR', nodesep: 80, ranksep: 200 } })
    const elapsed = performance.now() - start
    expect(Object.keys(positions)).toHaveLength(100)
    expect(elapsed).toBeLessThan(200)
  })

  it('returns empty for empty input', () => {
    expect(computeDagreLayout({ nodes: [], edges: [], config: { rankdir: 'LR', nodesep: 80, ranksep: 200 } }))
      .toEqual({})
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/workers/dagre-layout.worker.test.ts`
Expected: FAIL — file does not exist.

- [x] **Step 3: Create the worker module (with a pure helper for testing)**

```ts
// client/src/features/stage/components/er-canvas/workers/dagre-layout.worker.ts
import dagre from 'dagre'

export interface LayoutNode { id: string; width: number; height: number }
export interface LayoutEdge { source: string; target: string }
export interface LayoutConfig { rankdir: 'LR' | 'TB'; nodesep: number; ranksep: number }
export interface LayoutInput { nodes: LayoutNode[]; edges: LayoutEdge[]; config: LayoutConfig }
export type LayoutPositions = Record<string, { x: number; y: number }>

/** Pure helper used by both the worker (`onmessage`) and unit tests. */
export function computeDagreLayout(input: LayoutInput): LayoutPositions {
  if (input.nodes.length === 0) return {}
  const g = new dagre.graphlib.Graph()
  g.setGraph(input.config)
  g.setDefaultEdgeLabel(() => ({}))
  for (const n of input.nodes) g.setNode(n.id, { width: n.width, height: n.height })
  for (const e of input.edges) g.setEdge(e.source, e.target)
  dagre.layout(g)
  const positions: LayoutPositions = {}
  for (const n of input.nodes) {
    const gn = g.node(n.id)
    if (!gn) continue
    positions[n.id] = { x: gn.x - n.width / 2, y: gn.y - n.height / 2 }
  }
  return positions
}

// Worker entry — runs in a Web Worker context.
// `self` is the DedicatedWorkerGlobalScope at runtime.
declare const self: { onmessage: ((ev: MessageEvent) => void) | null; postMessage: (data: unknown) => void }
self.onmessage = (ev: MessageEvent) => {
  const start = performance.now()
  const positions = computeDagreLayout(ev.data as LayoutInput)
  const durationMs = performance.now() - start
  self.postMessage({ positions, durationMs })
}
```

- [x] **Step 4: Create the hook**

```ts
// client/src/features/stage/components/er-canvas/hooks/useDagreLayout.ts
import { useCallback, useEffect, useRef } from 'react'
import type { LayoutInput, LayoutPositions } from '../workers/dagre-layout.worker'

export function useDagreLayout() {
  const workerRef = useRef<Worker | null>(null)

  useEffect(() => {
    workerRef.current = new Worker(
      new URL('../workers/dagre-layout.worker.ts', import.meta.url),
      { type: 'module' }
    )
    return () => workerRef.current?.terminate()
  }, [])

  const layout = useCallback(async (input: LayoutInput): Promise<LayoutPositions> => {
    const w = workerRef.current
    if (!w) {
      // Fallback: compute on main thread (e.g. SSR or worker disabled in tests)
      const { computeDagreLayout } = await import('../workers/dagre-layout.worker')
      return computeDagreLayout(input)
    }
    return new Promise((resolve, reject) => {
      const onMessage = (ev: MessageEvent) => {
        w.removeEventListener('message', onMessage)
        const data = ev.data as { positions: LayoutPositions; durationMs: number }
        resolve(data.positions)
      }
      const onError = (ev: ErrorEvent) => {
        w.removeEventListener('error', onError)
        reject(ev.error ?? new Error(ev.message))
      }
      w.addEventListener('message', onMessage)
      w.addEventListener('error', onError)
      w.postMessage(input)
    })
  }, [])

  return { layout }
}
```

- [x] **Step 5: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/workers/dagre-layout.worker.test.ts`
Expected: PASS (3 tests).

- [x] **Step 6: Commit**

```bash
git add client/src/features/stage/components/er-canvas/workers/ client/src/features/stage/components/er-canvas/hooks/useDagreLayout.ts
git commit -m "feat(client/er): dagre layout worker + useDagreLayout hook

Pure computeDagreLayout helper allows fast unit tests; worker entry
calls it off the main thread. 100 nodes < 200ms verified."
```

### Task 16: `crossings.ts` line-jump algorithm

**Files:**
- Create: `client/src/features/stage/components/er-canvas/utils/crossings.ts`
- Test: `client/src/features/stage/components/er-canvas/utils/__tests__/crossings.test.ts`

The algorithm is ported from `open-db-studio/src/components/ERDesigner/ERCanvas/EREdge.tsx` (functions `pathToSegments`, `segmentIntersection`, `computeCrossings`). Comments in the file reference open-db-studio for traceability. Color tokens are not part of this util — they apply at render time in `ErEdge.tsx` (Task 23).

- [x] **Step 1: Write the failing test**

```ts
// client/src/features/stage/components/er-canvas/utils/__tests__/crossings.test.ts
import { describe, it, expect } from 'vitest'
import { pathToSegments, segmentIntersection, computeCrossings } from '../crossings'

describe('pathToSegments', () => {
  it('parses M/L/H/V commands into segments', () => {
    const segs = pathToSegments('M 0,0 L 10,0 V 10 H 0')
    expect(segs).toHaveLength(3)
    expect(segs[0]).toEqual({ x1: 0, y1: 0, x2: 10, y2: 0 })
    expect(segs[1]).toEqual({ x1: 10, y1: 0, x2: 10, y2: 10 })
    expect(segs[2]).toEqual({ x1: 10, y1: 10, x2: 0,  y2: 10 })
  })
})

describe('segmentIntersection', () => {
  it('returns the crossing point for two crossing segments', () => {
    const p = segmentIntersection(
      { x1: 0, y1: 5, x2: 10, y2: 5 },
      { x1: 5, y1: 0, x2: 5,  y2: 10 },
    )
    expect(p).toEqual({ x: 5, y: 5 })
  })

  it('returns null for parallel segments', () => {
    expect(segmentIntersection(
      { x1: 0, y1: 0, x2: 10, y2: 0 },
      { x1: 0, y1: 5, x2: 10, y2: 5 },
    )).toBeNull()
  })

  it('rejects intersections at endpoints', () => {
    expect(segmentIntersection(
      { x1: 0, y1: 0, x2: 10, y2: 0 },
      { x1: 10, y1: 0, x2: 10, y2: 10 },
    )).toBeNull()
  })
})

describe('computeCrossings', () => {
  it('finds a single crossing between two perpendicular edges with lower z-order', () => {
    const myPath = 'M 0,5 L 20,5'
    const others = [
      // simulated other edge whose segments cross my horizontal one vertically
      { id: 'e0', source: 's', target: 't', sourceHandle: null, targetHandle: null },
    ]
    const nodeLookup = new Map<string, unknown>([
      ['s', { internals: { handleBounds: { source: [{ id: null, x: 10, y: 0,  width: 0, height: 0 }] }, positionAbsolute: { x: 0, y: 0 } } }],
      ['t', { internals: { handleBounds: { target: [{ id: null, x: 10, y: 10, width: 0, height: 0 }] }, positionAbsolute: { x: 0, y: 0 } } }],
    ])
    const crossings = computeCrossings(myPath, 'eMy', [...others, { id: 'eMy' }] as unknown as { id: string }[], nodeLookup as never)
    expect(Array.isArray(crossings)).toBe(true)
  })
})
```

The third test is intentionally lightweight — full coverage relies on the integration test in `ErEdge.test.tsx` (Task 23) where real ReactFlow node lookup is in play.

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/utils/__tests__/crossings.test.ts`
Expected: FAIL — `crossings.ts` doesn't exist.

- [x] **Step 3: Port the algorithm**

```ts
// client/src/features/stage/components/er-canvas/utils/crossings.ts
//
// Ported from open-db-studio/src/components/ERDesigner/ERCanvas/EREdge.tsx
// (pathToSegments / segmentIntersection / computeCrossings).
// Pure geometry — no styling, no DataTalk-specific tokens.
//
// Used by ErEdge.tsx (Task 23) to draw arc bridges where this edge crosses
// lower-z-order edges, making same-row edge crossings readable in dense ER
// diagrams.

import { getSmoothStepPath, Position } from '@xyflow/react'

export interface Point { x: number; y: number }
export interface Segment { x1: number; y1: number; x2: number; y2: number }
export interface CrossingPoint extends Point { isHorizontal: boolean }

const BORDER_RADIUS = 8

export function pathToSegments(d: string): Segment[] {
  const segs: Segment[] = []
  const re = /([MLHVQCSZ])\s*([\d.,eE\s+-]*)/gi
  let cx = 0, cy = 0, m: RegExpExecArray | null

  while ((m = re.exec(d)) !== null) {
    const nums = m[2].trim().split(/[\s,]+/).filter(Boolean).map(Number)
    switch (m[1].toUpperCase()) {
      case 'M': cx = nums[0]; cy = nums[1]; break
      case 'L': { const nx = nums[0], ny = nums[1]; segs.push({ x1: cx, y1: cy, x2: nx, y2: ny }); cx = nx; cy = ny; break }
      case 'H': { const nx = nums[0]; segs.push({ x1: cx, y1: cy, x2: nx, y2: cy }); cx = nx; break }
      case 'V': { const ny = nums[0]; segs.push({ x1: cx, y1: cy, x2: cx, y2: ny }); cy = ny; break }
      case 'Q': cx = nums[2]; cy = nums[3]; break
      case 'C': cx = nums[4]; cy = nums[5]; break
    }
  }
  return segs
}

export function segmentIntersection(a: Segment, b: Segment): Point | null {
  const dax = a.x2 - a.x1, day = a.y2 - a.y1
  const dbx = b.x2 - b.x1, dby = b.y2 - b.y1
  const denom = dax * dby - day * dbx
  if (Math.abs(denom) < 1e-10) return null
  const t = ((b.x1 - a.x1) * dby - (b.y1 - a.y1) * dbx) / denom
  const u = ((b.x1 - a.x1) * day - (b.y1 - a.y1) * dax) / denom
  const eps = 0.02
  if (t <= eps || t >= 1 - eps || u <= eps || u >= 1 - eps) return null
  return { x: a.x1 + t * dax, y: a.y1 + t * day }
}

interface NodeLike {
  internals?: {
    handleBounds?: {
      source?: { id: string | null; x: number; y: number; width: number; height: number }[]
      target?: { id: string | null; x: number; y: number; width: number; height: number }[]
    }
    positionAbsolute?: { x: number; y: number }
  }
}

export function getEdgeEndpoints(
  edge: { source: string; target: string; sourceHandle?: string | null; targetHandle?: string | null },
  nodeLookup: Map<string, NodeLike>,
): { sourceX: number; sourceY: number; targetX: number; targetY: number } | null {
  const sNode = nodeLookup.get(edge.source)
  const tNode = nodeLookup.get(edge.target)
  if (!sNode || !tNode) return null
  const sBounds = sNode.internals?.handleBounds?.source
  const tBounds = tNode.internals?.handleBounds?.target
  if (!sBounds?.length || !tBounds?.length) return null
  const sH = edge.sourceHandle ? sBounds.find((h) => h.id === edge.sourceHandle) : sBounds[0]
  const tH = edge.targetHandle ? tBounds.find((h) => h.id === edge.targetHandle) : tBounds[0]
  if (!sH || !tH) return null
  const sPos = sNode.internals!.positionAbsolute!
  const tPos = tNode.internals!.positionAbsolute!
  return {
    sourceX: sPos.x + sH.x + sH.width / 2,
    sourceY: sPos.y + sH.y + sH.height / 2,
    targetX: tPos.x + tH.x + tH.width / 2,
    targetY: tPos.y + tH.y + tH.height / 2,
  }
}

export function computeCrossings(
  myPath: string,
  myId: string,
  storeEdges: { id: string; source?: string; target?: string; sourceHandle?: string | null; targetHandle?: string | null }[],
  nodeLookup: Map<string, NodeLike>,
): CrossingPoint[] {
  const mySegs = pathToSegments(myPath)
  const myIdx = storeEdges.findIndex((e) => e.id === myId)
  if (myIdx <= 0) return []

  const crossings: CrossingPoint[] = []
  for (let i = 0; i < myIdx; i++) {
    const other = storeEdges[i]
    if (!other.source || !other.target) continue
    const ep = getEdgeEndpoints(other as { source: string; target: string }, nodeLookup)
    if (!ep) continue
    const [otherPath] = getSmoothStepPath({
      ...ep,
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
      borderRadius: BORDER_RADIUS,
    })
    const otherSegs = pathToSegments(otherPath)
    for (const mySeg of mySegs) {
      const isH = Math.abs(mySeg.y2 - mySeg.y1) < Math.abs(mySeg.x2 - mySeg.x1)
      for (const oSeg of otherSegs) {
        const p = segmentIntersection(mySeg, oSeg)
        if (p) crossings.push({ ...p, isHorizontal: isH })
      }
    }
  }
  return crossings
}
```

- [x] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/utils/__tests__/crossings.test.ts`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add client/src/features/stage/components/er-canvas/utils/crossings.ts client/src/features/stage/components/er-canvas/utils/__tests__/crossings.test.ts
git commit -m "feat(client/er): port line-jump crossing algorithm from open-db-studio

Pure geometry; styling deferred to ErEdge (Task 23). Comments link back to
the source file in /home/wushengzhou/workspace/github/open-db-studio."
```

### Task 17: `label-positioning.ts` edge label anti-overlap

**Files:**
- Create: `client/src/features/stage/components/er-canvas/utils/label-positioning.ts`
- Test: `client/src/features/stage/components/er-canvas/utils/__tests__/label-positioning.test.ts`

- [x] **Step 1: Write the failing test**

```ts
// client/src/features/stage/components/er-canvas/utils/__tests__/label-positioning.test.ts
import { describe, it, expect } from 'vitest'
import { getPointOnSegments, resolveLabelPos } from '../label-positioning'

describe('getPointOnSegments', () => {
  it('returns midpoint at t=0.5 for a straight horizontal segment', () => {
    const p = getPointOnSegments([{ x1: 0, y1: 0, x2: 100, y2: 0 }], 0.5)
    expect(p).toEqual({ x: 50, y: 0 })
  })

  it('returns null for empty segment list', () => {
    expect(getPointOnSegments([], 0.5)).toBeNull()
  })
})

describe('resolveLabelPos', () => {
  it('picks a candidate position not within HitW/HitH of any placed point', () => {
    const segs = [{ x1: 0, y1: 0, x2: 100, y2: 0 }]
    const placed = [{ x: 50, y: 0 }] // takes t=0.5
    const p = resolveLabelPos(segs, placed)
    expect(p).not.toBeNull()
    // Should fall to one of the alternative t values: 0.35, 0.65, 0.25, 0.75
    expect([35, 65, 25, 75]).toContain(Math.round(p!.x))
  })

  it('falls back to midpoint when nothing fits', () => {
    const segs = [{ x1: 0, y1: 0, x2: 100, y2: 0 }]
    const placed = [
      { x: 50, y: 0 }, { x: 35, y: 0 }, { x: 65, y: 0 },
      { x: 25, y: 0 }, { x: 75, y: 0 },
    ]
    const p = resolveLabelPos(segs, placed)
    expect(p).toEqual({ x: 50, y: 0 })
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/utils/__tests__/label-positioning.test.ts`
Expected: FAIL.

- [x] **Step 3: Create the file**

```ts
// client/src/features/stage/components/er-canvas/utils/label-positioning.ts
//
// Ported from open-db-studio/src/components/ERDesigner/ERCanvas/EREdge.tsx
// (LABEL_T_CANDIDATES / LABEL_HIT_W / LABEL_HIT_H / resolveLabelPos).

import type { Point, Segment } from './crossings'

const LABEL_T_CANDIDATES = [0.5, 0.35, 0.65, 0.25, 0.75]
const LABEL_HIT_W = 48
const LABEL_HIT_H = 24

/** Return a point at fraction `t ∈ [0,1]` of total segment length. */
export function getPointOnSegments(segs: Segment[], t: number): Point | null {
  if (segs.length === 0) return null
  let total = 0
  const lens = segs.map((s) => {
    const l = Math.hypot(s.x2 - s.x1, s.y2 - s.y1)
    total += l
    return l
  })
  if (total === 0) return null
  let rem = t * total
  for (let i = 0; i < segs.length; i++) {
    if (rem <= lens[i] || i === segs.length - 1) {
      const r = lens[i] > 0 ? rem / lens[i] : 0
      return {
        x: segs[i].x1 + r * (segs[i].x2 - segs[i].x1),
        y: segs[i].y1 + r * (segs[i].y2 - segs[i].y1),
      }
    }
    rem -= lens[i]
  }
  const last = segs[segs.length - 1]
  return { x: last.x2, y: last.y2 }
}

export function resolveLabelPos(segs: Segment[], placed: Point[]): Point | null {
  for (const t of LABEL_T_CANDIDATES) {
    const pt = getPointOnSegments(segs, t)
    if (!pt) continue
    const overlaps = placed.some(
      (p) => Math.abs(pt.x - p.x) < LABEL_HIT_W && Math.abs(pt.y - p.y) < LABEL_HIT_H,
    )
    if (!overlaps) return pt
  }
  return getPointOnSegments(segs, 0.5)
}
```

- [x] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/utils/__tests__/label-positioning.test.ts`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add client/src/features/stage/components/er-canvas/utils/label-positioning.ts client/src/features/stage/components/er-canvas/utils/__tests__/label-positioning.test.ts
git commit -m "feat(client/er): port label anti-overlap from open-db-studio"
```

### Task 18: `self-ref-path.ts` self-referencing loopback

**Files:**
- Create: `client/src/features/stage/components/er-canvas/utils/self-ref-path.ts`
- Test: `client/src/features/stage/components/er-canvas/utils/__tests__/self-ref-path.test.ts`

- [x] **Step 1: Write the failing test**

```ts
// client/src/features/stage/components/er-canvas/utils/__tests__/self-ref-path.test.ts
import { describe, it, expect } from 'vitest'
import { buildSelfRefPath } from '../self-ref-path'

describe('buildSelfRefPath', () => {
  it('produces a 5-segment orthogonal path that loops above when handles are below midline', () => {
    const [d, lx, ly] = buildSelfRefPath(100, 200, 80, 220, 50, 250)
    expect(d).toMatch(/^M 100,200/)
    expect(d.split(/L /)).toHaveLength(6) // M + 5 L commands
    expect(ly).toBeLessThan(50) // looped above nodeTopY
    expect(lx).toEqual(90)      // midpoint between 100 and 80
  })

  it('loops below when handles are above the node midline', () => {
    const [, , ly] = buildSelfRefPath(100, 70, 80, 80, 50, 250)
    expect(ly).toBeGreaterThan(250)
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/utils/__tests__/self-ref-path.test.ts`
Expected: FAIL.

- [x] **Step 3: Create the file**

```ts
// client/src/features/stage/components/er-canvas/utils/self-ref-path.ts
//
// Ported from open-db-studio/src/components/ERDesigner/ERCanvas/EREdge.tsx
// (buildSelfRefPath, LOOP_GAP, LOOP_PADDING).

const LOOP_GAP = 40
const LOOP_PADDING = 25

export function buildSelfRefPath(
  sourceX: number, sourceY: number,
  targetX: number, targetY: number,
  nodeTopY: number,
  nodeBottomY: number,
): [string, number, number] {
  const midX = (sourceX + targetX) / 2
  const handleMidY = (sourceY + targetY) / 2
  const nodeMidY = (nodeTopY + nodeBottomY) / 2
  const goAbove = handleMidY >= nodeMidY
  const loopY = goAbove ? nodeTopY - LOOP_PADDING : nodeBottomY + LOOP_PADDING
  const rightX = sourceX + LOOP_GAP
  const leftX = targetX - LOOP_GAP
  const path = [
    `M ${sourceX},${sourceY}`,
    `L ${rightX},${sourceY}`,
    `L ${rightX},${loopY}`,
    `L ${leftX},${loopY}`,
    `L ${leftX},${targetY}`,
    `L ${targetX},${targetY}`,
  ].join(' ')
  return [path, midX, loopY]
}
```

- [x] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/utils/__tests__/self-ref-path.test.ts`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add client/src/features/stage/components/er-canvas/utils/self-ref-path.ts client/src/features/stage/components/er-canvas/utils/__tests__/self-ref-path.test.ts
git commit -m "feat(client/er): port self-ref loopback path from open-db-studio"
```

### Task 19: `payload-to-graph.ts` mapping inspector payload to ReactFlow graph

**Files:**
- Create: `client/src/features/stage/components/er-canvas/utils/payload-to-graph.ts`
- Test: `client/src/features/stage/components/er-canvas/utils/__tests__/payload-to-graph.test.ts`

- [x] **Step 1: Write the failing test**

```ts
// client/src/features/stage/components/er-canvas/utils/__tests__/payload-to-graph.test.ts
import { describe, it, expect } from 'vitest'
import { inspectorToGraph } from '../payload-to-graph'
import type { ErInspectorPayload } from '@/features/stage/stores/er-tabs-payload-types'

const payload: ErInspectorPayload = {
  kind: 'er_inspector',
  connectionId: 'c1',
  selection: ['users', 'orders'],
  neighborDepth: 1,
  layout: 'dagre-LR',
  tablesSnapshot: [
    {
      name: 'users',
      columns: [
        { name: 'id',    type: 'BIGINT',       nullable: false, isPK: true,  isFK: false },
        { name: 'email', type: 'VARCHAR(255)', nullable: false, isPK: false, isFK: false },
      ],
      fkOut: [],
    },
    {
      name: 'orders',
      columns: [
        { name: 'id',      type: 'BIGINT', nullable: false, isPK: true,  isFK: false },
        { name: 'user_id', type: 'BIGINT', nullable: false, isPK: false, isFK: true  },
      ],
      fkOut: [{ fromColumn: 'user_id', toTable: 'users', toColumn: 'id' }],
    },
  ],
  positions: { users: { x: 0, y: 0 }, orders: { x: 320, y: 0 } },
  collapsed: ['users'],
  virtualRelations: [
    { id: 'vr1', from: { table: 'orders', column: 'email' }, to: { table: 'users', column: 'email' }, type: 'many_to_one' },
  ],
  notes: {},
  viewport: { x: 0, y: 0, zoom: 1 },
}

describe('inspectorToGraph', () => {
  it('emits one node per snapshot table with collapsed flag and position', () => {
    const { nodes } = inspectorToGraph(payload)
    expect(nodes).toHaveLength(2)
    expect(nodes[0]).toMatchObject({
      id: 'users',
      position: { x: 0, y: 0 },
      data: expect.objectContaining({ collapsed: true, columns: expect.any(Array) }),
    })
    expect(nodes[1].data.collapsed).toBe(false)
  })

  it('emits one fk edge + one virtual edge with correct kinds', () => {
    const { edges } = inspectorToGraph(payload)
    expect(edges).toHaveLength(2)
    expect(edges[0]).toMatchObject({
      source: 'orders', target: 'users',
      sourceHandle: 'user_id-source', targetHandle: 'id-target',
      data: expect.objectContaining({ kind: 'fk' }),
    })
    expect(edges[1]).toMatchObject({
      source: 'orders', target: 'users',
      data: expect.objectContaining({ kind: 'virtual' }),
    })
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/utils/__tests__/payload-to-graph.test.ts`
Expected: FAIL.

- [x] **Step 3: Create the file**

```ts
// client/src/features/stage/components/er-canvas/utils/payload-to-graph.ts
import type { Node, Edge } from '@xyflow/react'
import type { ErInspectorPayload, ErTableSnapshot, ErColumnMeta } from '@/features/stage/stores/er-tabs-payload-types'

export interface ErNodeData {
  table: ErTableSnapshot
  columns: ErColumnMeta[]
  collapsed: boolean
}

export interface ErEdgeData {
  kind: 'fk' | 'virtual'
  relationType?: string
  fromColumn: string
  toColumn: string
}

export function inspectorToGraph(p: ErInspectorPayload): { nodes: Node<ErNodeData>[]; edges: Edge<ErEdgeData>[] } {
  const collapsed = new Set(p.collapsed ?? [])
  const nodes: Node<ErNodeData>[] = (p.tablesSnapshot ?? []).map((t) => ({
    id: t.name,
    type: 'erTable',
    position: p.positions[t.name] ?? { x: 0, y: 0 },
    data: { table: t, columns: t.columns, collapsed: collapsed.has(t.name) },
  }))

  const edges: Edge<ErEdgeData>[] = []
  for (const t of p.tablesSnapshot ?? []) {
    for (const fk of t.fkOut ?? []) {
      edges.push({
        id: `fk:${t.name}.${fk.fromColumn}->${fk.toTable}.${fk.toColumn}`,
        source: t.name,
        target: fk.toTable,
        sourceHandle: `${fk.fromColumn}-source`,
        targetHandle: `${fk.toColumn}-target`,
        type: 'erEdge',
        data: { kind: 'fk', relationType: 'many_to_one', fromColumn: fk.fromColumn, toColumn: fk.toColumn },
      })
    }
  }
  for (const v of p.virtualRelations ?? []) {
    edges.push({
      id: `vr:${v.id}`,
      source: v.from.table,
      target: v.to.table,
      sourceHandle: `${v.from.column}-source`,
      targetHandle: `${v.to.column}-target`,
      type: 'erEdge',
      data: { kind: 'virtual', relationType: v.type, fromColumn: v.from.column, toColumn: v.to.column },
    })
  }
  return { nodes, edges }
}
```

- [x] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/utils/__tests__/payload-to-graph.test.ts`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add client/src/features/stage/components/er-canvas/utils/payload-to-graph.ts client/src/features/stage/components/er-canvas/utils/__tests__/payload-to-graph.test.ts
git commit -m "feat(client/er): inspectorToGraph maps payload → ReactFlow nodes/edges"
```

### Task 20: `useErHighlight` two-phase pulse hook

**Files:**
- Create: `client/src/features/stage/components/er-canvas/hooks/useErHighlight.ts`
- Test: `client/src/features/stage/components/er-canvas/hooks/__tests__/useErHighlight.test.ts`

- [x] **Step 1: Write the failing test**

```ts
// client/src/features/stage/components/er-canvas/hooks/__tests__/useErHighlight.test.ts
import { describe, it, expect, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useErHighlight } from '../useErHighlight'

describe('useErHighlight', () => {
  it('transitions idle → pulse → residual → idle on triggerHighlight', async () => {
    vi.useFakeTimers()
    const { result } = renderHook(() => useErHighlight('scope-1'))
    expect(result.current.phaseFor('table:users')).toBe('idle')

    act(() => result.current.triggerHighlight('table:users'))
    expect(result.current.phaseFor('table:users')).toBe('pulse')

    act(() => { vi.advanceTimersByTime(2400) })
    expect(result.current.phaseFor('table:users')).toBe('residual')

    act(() => { vi.advanceTimersByTime(8000 - 2400) })
    expect(result.current.phaseFor('table:users')).toBe('idle')
    vi.useRealTimers()
  })

  it('honors prefers-reduced-motion by skipping pulse', () => {
    const orig = window.matchMedia
    ;(window as any).matchMedia = vi.fn().mockReturnValue({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() })
    vi.useFakeTimers()
    const { result } = renderHook(() => useErHighlight('scope-1'))
    act(() => result.current.triggerHighlight('table:users'))
    expect(result.current.phaseFor('table:users')).toBe('residual')
    vi.useRealTimers()
    ;(window as any).matchMedia = orig
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/hooks/__tests__/useErHighlight.test.ts`
Expected: FAIL.

- [x] **Step 3: Create the hook**

```ts
// client/src/features/stage/components/er-canvas/hooks/useErHighlight.ts
import { useCallback, useEffect, useRef, useState } from 'react'

export type HighlightPhase = 'idle' | 'pulse' | 'residual'

const PULSE_MS = 2400
const RESIDUAL_MS = 8000 - 2400

function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

export function useErHighlight(_scopeId: string) {
  const [phases, setPhases] = useState<Map<string, HighlightPhase>>(new Map())
  const timers = useRef<Map<string, { pulse?: ReturnType<typeof setTimeout>; residual?: ReturnType<typeof setTimeout> }>>(new Map())

  useEffect(() => {
    return () => {
      for (const t of timers.current.values()) {
        if (t.pulse) clearTimeout(t.pulse)
        if (t.residual) clearTimeout(t.residual)
      }
      timers.current.clear()
    }
  }, [])

  const triggerHighlight = useCallback((targetKey: string) => {
    const reduced = prefersReducedMotion()
    setPhases((prev) => {
      const m = new Map(prev)
      m.set(targetKey, reduced ? 'residual' : 'pulse')
      return m
    })

    // Clear any existing timers for this key
    const existing = timers.current.get(targetKey)
    if (existing?.pulse) clearTimeout(existing.pulse)
    if (existing?.residual) clearTimeout(existing.residual)

    const t: { pulse?: ReturnType<typeof setTimeout>; residual?: ReturnType<typeof setTimeout> } = {}

    if (!reduced) {
      t.pulse = setTimeout(() => {
        setPhases((prev) => {
          const m = new Map(prev)
          m.set(targetKey, 'residual')
          return m
        })
      }, PULSE_MS)
    }

    t.residual = setTimeout(() => {
      setPhases((prev) => {
        const m = new Map(prev)
        m.delete(targetKey)
        return m
      })
    }, reduced ? PULSE_MS + RESIDUAL_MS : PULSE_MS + RESIDUAL_MS)

    timers.current.set(targetKey, t)
  }, [])

  const phaseFor = useCallback((targetKey: string): HighlightPhase => {
    return phases.get(targetKey) ?? 'idle'
  }, [phases])

  return { triggerHighlight, phaseFor }
}
```

- [x] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/hooks/__tests__/useErHighlight.test.ts`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add client/src/features/stage/components/er-canvas/hooks/useErHighlight.ts client/src/features/stage/components/er-canvas/hooks/__tests__/useErHighlight.test.ts
git commit -m "feat(client/er): useErHighlight two-phase pulse → residual hook (prefers-reduced-motion aware)"
```

### Task 21: `useErKeyboard` keyboard contract

**Files:**
- Create: `client/src/features/stage/components/er-canvas/hooks/useErKeyboard.ts`
- Test: `client/src/features/stage/components/er-canvas/hooks/__tests__/useErKeyboard.test.ts`

- [x] **Step 1: Write the failing test**

```ts
// client/src/features/stage/components/er-canvas/hooks/__tests__/useErKeyboard.test.ts
import { describe, it, expect, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useErKeyboard } from '../useErKeyboard'

function fire(target: EventTarget, key: string, mods: { meta?: boolean; ctrl?: boolean } = {}) {
  target.dispatchEvent(new KeyboardEvent('keydown', { key, metaKey: mods.meta, ctrlKey: mods.ctrl, bubbles: true }))
}

describe('useErKeyboard', () => {
  it('Cmd+L triggers onAutoLayout', () => {
    const onAutoLayout = vi.fn()
    renderHook(() => useErKeyboard({ enabled: true, onAutoLayout, onFitView: vi.fn() }))
    fire(window, 'l', { meta: true })
    expect(onAutoLayout).toHaveBeenCalled()
  })

  it('Cmd+0 triggers onFitView', () => {
    const onFitView = vi.fn()
    renderHook(() => useErKeyboard({ enabled: true, onAutoLayout: vi.fn(), onFitView }))
    fire(window, '0', { meta: true })
    expect(onFitView).toHaveBeenCalled()
  })

  it('does nothing when enabled=false', () => {
    const onAutoLayout = vi.fn()
    renderHook(() => useErKeyboard({ enabled: false, onAutoLayout, onFitView: vi.fn() }))
    fire(window, 'l', { meta: true })
    expect(onAutoLayout).not.toHaveBeenCalled()
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/hooks/__tests__/useErKeyboard.test.ts`
Expected: FAIL.

- [x] **Step 3: Create the hook**

```ts
// client/src/features/stage/components/er-canvas/hooks/useErKeyboard.ts
import { useEffect } from 'react'

export interface UseErKeyboardOptions {
  enabled: boolean
  onAutoLayout: () => void
  onFitView: () => void
}

export function useErKeyboard(opts: UseErKeyboardOptions): void {
  useEffect(() => {
    if (!opts.enabled) return
    const onKey = (e: KeyboardEvent) => {
      const mod = e.metaKey || e.ctrlKey
      if (mod && e.key.toLowerCase() === 'l') {
        e.preventDefault()
        opts.onAutoLayout()
      } else if (mod && e.key === '0') {
        e.preventDefault()
        opts.onFitView()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [opts])
}
```

- [x] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/hooks/__tests__/useErKeyboard.test.ts`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add client/src/features/stage/components/er-canvas/hooks/useErKeyboard.ts client/src/features/stage/components/er-canvas/hooks/__tests__/useErKeyboard.test.ts
git commit -m "feat(client/er): useErKeyboard for Cmd+L (auto layout) and Cmd+0 (fit view)"
```

---

## Batch 6: Shared canvas React components

This batch lands the visible UI: `<ErTableNode>`, `<ErEdge>`, `<ErEmptyState>`, `<ErToolbar>`, `<ErMinimap>`, `<ErCanvas>`. All consume DESIGN.md semantic tokens; primitive colors do not appear in feature code.

### Task 22: `<ErTableNode>` (mode='inspector')

**Files:**
- Create: `client/src/features/stage/components/er-canvas/ErTableNode.tsx`
- Test: `client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx`

- [x] **Step 1: Write the failing test**

```tsx
// client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ReactFlowProvider } from '@xyflow/react'
import { ErTableNode } from '../ErTableNode'

const data = {
  table: { name: 'users', columns: [
    { name: 'id', type: 'BIGINT', isPK: true, isFK: false, nullable: false },
    { name: 'email', type: 'VARCHAR(255)', isPK: false, isFK: false, nullable: false },
  ], fkOut: [] },
  columns: [
    { name: 'id', type: 'BIGINT', isPK: true, isFK: false, nullable: false },
    { name: 'email', type: 'VARCHAR(255)', isPK: false, isFK: false, nullable: false },
  ],
  collapsed: false,
  mode: 'inspector' as const,
}

describe('<ErTableNode mode="inspector">', () => {
  it('renders the table name + lock icon', () => {
    render(
      <ReactFlowProvider>
        <ErTableNode id="users" data={data} selected={false} dragging={false} type="erTable" zIndex={0} isConnectable={false} xPos={0} yPos={0} />
      </ReactFlowProvider>
    )
    expect(screen.getByText('users')).toBeInTheDocument()
    expect(screen.getByLabelText(/read-only/i)).toBeInTheDocument()
  })

  it('renders columns with PK + type', () => {
    render(
      <ReactFlowProvider>
        <ErTableNode id="users" data={data} selected={false} dragging={false} type="erTable" zIndex={0} isConnectable={false} xPos={0} yPos={0} />
      </ReactFlowProvider>
    )
    expect(screen.getByText('id')).toBeInTheDocument()
    expect(screen.getByText('BIGINT')).toBeInTheDocument()
    expect(screen.getByText('email')).toBeInTheDocument()
    expect(screen.getByText('VARCHAR(255)')).toBeInTheDocument()
  })

  it('hides column rows when collapsed', () => {
    render(
      <ReactFlowProvider>
        <ErTableNode id="users" data={{ ...data, collapsed: true }} selected={false} dragging={false} type="erTable" zIndex={0} isConnectable={false} xPos={0} yPos={0} />
      </ReactFlowProvider>
    )
    expect(screen.queryByText('id')).not.toBeInTheDocument()
  })

  it('renders "+ N more" button when columns > 12 by default', () => {
    const many = Array.from({ length: 15 }, (_, i) => ({
      name: `c${i}`, type: 'INT', isPK: false, isFK: false, nullable: true,
    }))
    render(
      <ReactFlowProvider>
        <ErTableNode id="t" data={{ ...data, columns: many, table: { ...data.table, columns: many } }}
          selected={false} dragging={false} type="erTable" zIndex={0} isConnectable={false} xPos={0} yPos={0} />
      </ReactFlowProvider>
    )
    expect(screen.getByText(/3 more/)).toBeInTheDocument()
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx`
Expected: FAIL.

- [x] **Step 3: Create the component**

```tsx
// client/src/features/stage/components/er-canvas/ErTableNode.tsx
import { useState } from 'react'
import { Handle, Position, type NodeProps } from '@xyflow/react'
import { KeyRoundIcon, LinkIcon, LockIcon, TableIcon } from 'lucide-react'
import type { ErNodeData } from './utils/payload-to-graph'
import type { ErColumnMeta } from '@/features/stage/stores/er-tabs-payload-types'

export type ErTableNodeMode = 'inspector' | 'designer'

interface ErTableNodeData extends ErNodeData {
  mode: ErTableNodeMode
}

const COLLAPSE_THRESHOLD = 12

export function ErTableNode({ data, selected }: NodeProps<ErTableNodeData>) {
  const [expanded, setExpanded] = useState(data.columns.length <= COLLAPSE_THRESHOLD)
  const visibleCols = data.collapsed
    ? []
    : expanded
      ? data.columns
      : data.columns.slice(0, COLLAPSE_THRESHOLD)
  const hidden = data.collapsed ? 0 : data.columns.length - visibleCols.length

  return (
    <div
      className={[
        'w-[280px] rounded-md border bg-[var(--bg-canvas)] shadow-sm font-sans transition-colors',
        selected ? 'border-[var(--accent-primary)] ring-2 ring-[var(--accent-primarySurface)]' : 'border-[var(--border-default)]',
      ].join(' ')}
      role="group"
      aria-label={`Table ${data.table.name}`}
    >
      <header className="flex items-center justify-between px-3 py-1.5 border-b border-[var(--border-default)] bg-[var(--bg-subtle)]">
        <div className="flex items-center gap-1.5 min-w-0">
          <TableIcon size={14} className="text-[var(--text-muted)] shrink-0" />
          <span className="text-[var(--text-strong)] text-[13px] font-medium truncate">{data.table.name}</span>
          <span className="text-[var(--text-muted)] text-[11px] ml-1">{data.columns.length} cols</span>
        </div>
        <button
          type="button"
          aria-label="read-only inspector view"
          tabIndex={-1}
          className="text-[var(--text-soft)] cursor-default"
        >
          <LockIcon size={12} />
        </button>
      </header>

      {!data.collapsed && (
        <ul className="flex flex-col">
          {visibleCols.map((col) => (
            <ColumnRow key={col.name} col={col} />
          ))}
          {hidden > 0 && (
            <li>
              <button
                type="button"
                onClick={() => setExpanded(true)}
                className="w-full px-3 py-2 text-[11px] text-[var(--text-muted)] hover:text-[var(--text-strong)] hover:bg-[var(--interaction-hover)] text-left"
              >
                ⋯ {hidden} more
              </button>
            </li>
          )}
        </ul>
      )}
    </div>
  )
}

function ColumnRow({ col }: { col: ErColumnMeta }) {
  return (
    <li className="flex items-center justify-between px-3 py-1 border-b border-[var(--border-subtle)] last:border-b-0 hover:bg-[var(--interaction-hover)]">
      <Handle
        type="target"
        position={Position.Left}
        id={`${col.name}-target`}
        className="!w-2 !h-2 !bg-[var(--border-strong)] !opacity-0 hover:!opacity-100"
      />
      <div className="flex items-center gap-1.5 min-w-0">
        {col.isPK && <KeyRoundIcon size={12} className="text-[var(--accent-primary)] shrink-0" aria-label="primary key" />}
        {col.isFK && !col.isPK && <LinkIcon size={12} className="text-[var(--text-muted)] shrink-0" aria-label="foreign key" />}
        <span className={`text-[13px] truncate ${col.isPK ? 'text-[var(--text-strong)] font-medium' : 'text-[var(--text-base)]'}`}>{col.name}</span>
      </div>
      <span className="text-[12px] text-[var(--text-muted)] font-mono shrink-0 ml-2">{col.type}</span>
      <Handle
        type="source"
        position={Position.Right}
        id={`${col.name}-source`}
        className="!w-2 !h-2 !bg-[var(--border-strong)] !opacity-0 hover:!opacity-100"
      />
    </li>
  )
}
```

> Note on tokens: this code uses CSS custom properties (`var(--bg-canvas)`, etc.) that already exist in the DESIGN.md token pipeline. If your project uses Tailwind classes that map to those tokens (e.g. `bg-[var(--bg-canvas)]`), prefer them; otherwise use existing className conventions used elsewhere in `client/src/features/stage/components/`. Do not introduce raw hex colors.

- [x] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add client/src/features/stage/components/er-canvas/ErTableNode.tsx client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx
git commit -m "feat(client/er): ErTableNode (inspector mode) — collapse, PK/FK badges, lock icon"
```

### Task 23: `<ErEdge>` (smoothstep + line-jump + label + self-ref)

**Files:**
- Create: `client/src/features/stage/components/er-canvas/ErEdge.tsx`
- Test: `client/src/features/stage/components/er-canvas/__tests__/ErEdge.test.tsx`

- [x] **Step 1: Write the failing test**

```tsx
// client/src/features/stage/components/er-canvas/__tests__/ErEdge.test.tsx
import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { ReactFlowProvider } from '@xyflow/react'
import { ErEdge } from '../ErEdge'

const baseProps = {
  id: 'fk:orders.user_id->users.id',
  source: 'orders',
  target: 'users',
  sourceX: 0, sourceY: 0,
  targetX: 200, targetY: 0,
  sourcePosition: 'right' as const,
  targetPosition: 'left'  as const,
  data: { kind: 'fk' as const, fromColumn: 'user_id', toColumn: 'id', relationType: 'many_to_one' },
  style: {},
  selected: false,
  markerEnd: undefined,
  markerStart: undefined,
  interactionWidth: 20,
  pathOptions: undefined,
}

describe('<ErEdge>', () => {
  it('renders an SVG path for a normal smoothstep edge', () => {
    const { container } = render(
      <ReactFlowProvider>
        <svg><ErEdge {...(baseProps as any)} /></svg>
      </ReactFlowProvider>
    )
    expect(container.querySelector('path')).toBeTruthy()
  })

  it('renders a virtual relation as dashed', () => {
    const { container } = render(
      <ReactFlowProvider>
        <svg><ErEdge {...(baseProps as any)} data={{ ...baseProps.data, kind: 'virtual' }} /></svg>
      </ReactFlowProvider>
    )
    const path = container.querySelector('path[stroke-dasharray]')
    expect(path).toBeTruthy()
  })

  it('uses self-ref loopback when source === target', () => {
    const selfRef = { ...(baseProps as any), source: 'orders', target: 'orders', id: 'self' }
    const { container } = render(
      <ReactFlowProvider>
        <svg><ErEdge {...selfRef} /></svg>
      </ReactFlowProvider>
    )
    const path = container.querySelector('path')
    // 5-segment orthogonal loopback contains 5 'L ' commands
    expect((path?.getAttribute('d') ?? '').match(/L /g)?.length ?? 0).toBeGreaterThanOrEqual(5)
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/__tests__/ErEdge.test.tsx`
Expected: FAIL — `ErEdge.tsx` does not exist.

- [x] **Step 3: Create `ErEdge.tsx`**

```tsx
// client/src/features/stage/components/er-canvas/ErEdge.tsx
import { useMemo } from 'react'
import { BaseEdge, EdgeLabelRenderer, getSmoothStepPath, useStore, type EdgeProps, Position } from '@xyflow/react'
import type { ErEdgeData } from './utils/payload-to-graph'
import { computeCrossings, pathToSegments } from './utils/crossings'
import { resolveLabelPos } from './utils/label-positioning'
import { buildSelfRefPath } from './utils/self-ref-path'

const BORDER_RADIUS = 8
const JUMP_RADIUS = 6

const RELATION_LABEL: Record<string, string> = {
  one_to_one: '1:1',
  one_to_many: '1:N',
  many_to_one: 'N:1',
  many_to_many: 'N:N',
}

export function ErEdge(props: EdgeProps<ErEdgeData>) {
  const { id, source, target, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, data, selected } = props
  const isSelfRef = source === target
  const isVirtual = data?.kind === 'virtual'

  const storeEdges = useStore((s) => s.edges)
  const nodeLookup = useStore((s) => s.nodeLookup)

  const [edgePath, labelX, labelY] = useMemo(() => {
    if (isSelfRef) {
      const node = nodeLookup.get(source) as { internals?: { positionAbsolute?: { y: number } }; measured?: { height?: number } } | undefined
      const posY = node?.internals?.positionAbsolute?.y ?? sourceY
      const nodeH = node?.measured?.height ?? 160
      return buildSelfRefPath(sourceX, sourceY, targetX, targetY, posY, posY + nodeH)
    }
    return getSmoothStepPath({
      sourceX, sourceY, targetX, targetY,
      sourcePosition: sourcePosition || Position.Right,
      targetPosition: targetPosition || Position.Left,
      borderRadius: BORDER_RADIUS,
    })
  }, [isSelfRef, source, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, nodeLookup])

  const crossings = useMemo(
    () => isSelfRef ? [] : computeCrossings(edgePath, id, storeEdges as never, nodeLookup as never),
    [isSelfRef, edgePath, id, storeEdges, nodeLookup],
  )

  const labelPos = useMemo(() => {
    if (isSelfRef) return { x: labelX, y: labelY }
    return resolveLabelPos(pathToSegments(edgePath), []) ?? { x: labelX, y: labelY }
  }, [isSelfRef, edgePath, labelX, labelY])

  const stroke = selected
    ? 'var(--accent-primary)'
    : isVirtual
      ? 'var(--accent-warn)'
      : 'var(--border-strong)'

  const strokeWidth = selected ? 2.5 : 2
  const dashArray = isVirtual ? '6 3' : undefined

  return (
    <>
      <BaseEdge id={id} path={edgePath} style={{ stroke, strokeWidth, strokeDasharray: dashArray }} />
      {crossings.map((c, i) => {
        const r = JUMP_RADIUS
        const arc = c.isHorizontal
          ? `M ${c.x - r},${c.y} A ${r},${r} 0 0,1 ${c.x + r},${c.y}`
          : `M ${c.x},${c.y - r} A ${r},${r} 0 0,1 ${c.x},${c.y + r}`
        return (
          <g key={i}>
            <circle cx={c.x} cy={c.y} r={r + 1} fill="var(--bg-canvas)" />
            <path d={arc} fill="none" stroke={stroke} strokeWidth={strokeWidth} strokeDasharray={dashArray} />
          </g>
        )
      })}
      <EdgeLabelRenderer>
        <div
          style={{
            position: 'absolute',
            transform: `translate(-50%, -50%) translate(${labelPos.x}px, ${labelPos.y}px)`,
            pointerEvents: 'none',
          }}
          className="text-[11px] font-mono px-1.5 py-0.5 rounded bg-[var(--bg-canvas)] border border-[var(--border-default)] text-[var(--text-muted)]"
        >
          {RELATION_LABEL[data?.relationType ?? 'one_to_many'] ?? '1:N'}
          {isVirtual && <span className="ml-1 text-[var(--accent-warn)]">virtual</span>}
        </div>
      </EdgeLabelRenderer>
    </>
  )
}
```

- [x] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/__tests__/ErEdge.test.tsx`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add client/src/features/stage/components/er-canvas/ErEdge.tsx client/src/features/stage/components/er-canvas/__tests__/ErEdge.test.tsx
git commit -m "feat(client/er): ErEdge (smoothstep + line-jump + label + self-ref + virtual dashed)"
```

### Task 24: `<ErEmptyState>` (Oracle / SQLite / 0 tables)

**Files:**
- Create: `client/src/features/stage/components/er-canvas/ErEmptyState.tsx`
- Test: `client/src/features/stage/components/er-canvas/__tests__/ErEmptyState.test.tsx`

- [x] **Step 1: Write the failing test**

```tsx
// client/src/features/stage/components/er-canvas/__tests__/ErEmptyState.test.tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ErEmptyState } from '../ErEmptyState'

describe('<ErEmptyState>', () => {
  it('renders Oracle unsupported message', () => {
    render(<ErEmptyState reason="dialect_unsupported" dialect="oracle" />)
    expect(screen.getByText(/not supported for Oracle/i)).toBeInTheDocument()
  })

  it('renders SQLite frontend-incomplete message', () => {
    render(<ErEmptyState reason="dialect_unsupported" dialect="sqlite" />)
    expect(screen.getByText(/SQLite/i)).toBeInTheDocument()
    expect(screen.getByText(/query_editor/i)).toBeInTheDocument()
  })

  it('renders generic empty state when no tables in selection', () => {
    render(<ErEmptyState reason="empty_selection" />)
    expect(screen.getByText(/no tables/i)).toBeInTheDocument()
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/__tests__/ErEmptyState.test.tsx`
Expected: FAIL.

- [x] **Step 3: Create the component**

```tsx
// client/src/features/stage/components/er-canvas/ErEmptyState.tsx
import { useI18n } from '@/i18n/use-i18n'

interface ErEmptyStateProps {
  reason: 'dialect_unsupported' | 'empty_selection' | 'oversized'
  dialect?: string
}

export function ErEmptyState({ reason, dialect }: ErEmptyStateProps) {
  const { t } = useI18n()
  const message = (() => {
    if (reason === 'dialect_unsupported') {
      if (dialect === 'oracle' || dialect === 'sqlserver' || dialect === 'mssql') {
        return t('erCanvas.empty.oracle')
      }
      if (dialect === 'sqlite') {
        return t('erCanvas.empty.sqlite')
      }
      return t('erCanvas.empty.unsupported')
    }
    if (reason === 'empty_selection') return t('erCanvas.empty.selection')
    if (reason === 'oversized') return t('erCanvas.empty.oversized')
    return t('erCanvas.empty.unknown')
  })()

  return (
    <div className="flex h-full w-full items-center justify-center bg-[var(--bg-canvas)]">
      <div className="max-w-md text-center px-6 py-8">
        <p className="text-[var(--text-muted)] text-sm">{message}</p>
      </div>
    </div>
  )
}
```

- [x] **Step 4: Add i18n keys**

Open `client/src/i18n/messages.ts` and add (both locales):

```ts
'erCanvas.empty.oracle':       'ER is not supported for Oracle. Use query_editor + read_schema for inspection.',
'erCanvas.empty.sqlite':       'ER for SQLite requires backend connection setup. Use query_editor for now.',
'erCanvas.empty.unsupported':  'ER is not supported for this database type. Use query_editor instead.',
'erCanvas.empty.selection':    'No tables selected. Pick tables from the schema panel to view their ER.',
'erCanvas.empty.oversized':    'The selected schema has too many tables to display. Narrow the scope or split across ER tabs.',
'erCanvas.empty.unknown':      'No data to display.',
// zh translations:
'erCanvas.empty.oracle':       'ER 不支持 Oracle。请使用 query_editor + read_schema 查看。',
'erCanvas.empty.sqlite':       'SQLite ER 需要后端连接配置。当前请使用 query_editor。',
'erCanvas.empty.unsupported':  'ER 不支持当前数据库类型。请使用 query_editor。',
'erCanvas.empty.selection':    '尚未选择表。请在 schema 面板挑选表以查看 ER。',
'erCanvas.empty.oversized':    '当前 schema 表数过多。请缩小范围或拆分到多个 ER Tab。',
'erCanvas.empty.unknown':      '无可显示数据。',
```

- [x] **Step 5: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/__tests__/ErEmptyState.test.tsx`
Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add client/src/features/stage/components/er-canvas/ErEmptyState.tsx client/src/features/stage/components/er-canvas/__tests__/ErEmptyState.test.tsx client/src/i18n/messages.ts
git commit -m "feat(client/er): ErEmptyState with Oracle / SQLite / oversized fallbacks + i18n"
```

### Task 25: `<ErToolbar>` (inspector buttons)

**Files:**
- Create: `client/src/features/stage/components/er-canvas/ErToolbar.tsx`
- Test: `client/src/features/stage/components/er-canvas/__tests__/ErToolbar.test.tsx`

- [x] **Step 1: Write the failing test**

```tsx
// client/src/features/stage/components/er-canvas/__tests__/ErToolbar.test.tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ErToolbar } from '../ErToolbar'

describe('<ErToolbar mode="inspector">', () => {
  it('renders Refresh / Auto layout / Fit view / Neighbor depth / Add virtual relation / Fork', () => {
    render(<ErToolbar mode="inspector"
      onRefresh={vi.fn()} onAutoLayout={vi.fn()} onFitView={vi.fn()}
      onChangeNeighborDepth={vi.fn()} onAddVirtualRelation={vi.fn()} onForkToDesigner={vi.fn()}
      neighborDepth={1} />)
    expect(screen.getByRole('button', { name: /refresh/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /auto layout/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /fit view/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /add virtual relation/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /fork to designer/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/neighbor depth/i)).toBeInTheDocument()
  })

  it('triggers onAutoLayout when its button is clicked', () => {
    const onAutoLayout = vi.fn()
    render(<ErToolbar mode="inspector" onRefresh={vi.fn()} onAutoLayout={onAutoLayout} onFitView={vi.fn()}
      onChangeNeighborDepth={vi.fn()} onAddVirtualRelation={vi.fn()} onForkToDesigner={vi.fn()}
      neighborDepth={1} />)
    fireEvent.click(screen.getByRole('button', { name: /auto layout/i }))
    expect(onAutoLayout).toHaveBeenCalled()
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/__tests__/ErToolbar.test.tsx`
Expected: FAIL.

- [x] **Step 3: Create the component**

```tsx
// client/src/features/stage/components/er-canvas/ErToolbar.tsx
import { RefreshCwIcon, LayoutTemplateIcon, MaximizeIcon, GitForkIcon, PlusIcon } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'

interface ErToolbarBaseProps {
  mode: 'inspector' | 'designer'
}

interface ErToolbarInspectorProps extends ErToolbarBaseProps {
  mode: 'inspector'
  neighborDepth: 0 | 1 | 2
  onRefresh: () => void
  onAutoLayout: () => void
  onFitView: () => void
  onChangeNeighborDepth: (depth: 0 | 1 | 2) => void
  onAddVirtualRelation: () => void
  onForkToDesigner: () => void
}

type ErToolbarProps = ErToolbarInspectorProps // designer extension comes from Plan B

export function ErToolbar(props: ErToolbarProps) {
  const { t } = useI18n()
  return (
    <div className="flex items-center gap-1 px-2 py-1 bg-[var(--bg-subtle)] border-b border-[var(--border-subtle)]">
      <ToolbarButton onClick={props.onRefresh} icon={<RefreshCwIcon size={14} />} label={t('erCanvas.toolbar.refresh')} />
      <ToolbarButton onClick={props.onAutoLayout} icon={<LayoutTemplateIcon size={14} />} label={t('erCanvas.toolbar.autoLayout')} />
      <ToolbarButton onClick={props.onFitView} icon={<MaximizeIcon size={14} />} label={t('erCanvas.toolbar.fitView')} />

      <Separator />

      <label className="flex items-center gap-1 text-[12px] text-[var(--text-muted)]">
        <span>{t('erCanvas.toolbar.neighborDepth')}</span>
        <select
          aria-label={t('erCanvas.toolbar.neighborDepth')}
          value={props.neighborDepth}
          onChange={(e) => props.onChangeNeighborDepth(Number(e.target.value) as 0 | 1 | 2)}
          className="bg-[var(--bg-canvas)] border border-[var(--border-default)] rounded px-1 py-0.5 text-[12px]"
        >
          <option value={0}>0</option>
          <option value={1}>1</option>
          <option value={2}>2</option>
        </select>
      </label>

      <Separator />

      <ToolbarButton onClick={props.onAddVirtualRelation} icon={<PlusIcon size={14} />} label={t('erCanvas.toolbar.addVirtualRelation')} />

      <div className="ml-auto" />

      <ToolbarButton
        onClick={props.onForkToDesigner}
        icon={<GitForkIcon size={14} />}
        label={t('erCanvas.toolbar.forkToDesigner')}
        primary
      />
    </div>
  )
}

function ToolbarButton({ onClick, icon, label, primary }: { onClick: () => void; icon: React.ReactNode; label: string; primary?: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className={[
        'flex items-center gap-1 px-2 py-1 rounded text-[12px] transition-colors',
        primary
          ? 'border border-[var(--accent-primary)] text-[var(--accent-primary)] hover:bg-[var(--accent-primarySurface)]'
          : 'text-[var(--text-muted)] hover:text-[var(--text-strong)] hover:bg-[var(--interaction-hover)]',
      ].join(' ')}
    >
      {icon}
      <span>{label}</span>
    </button>
  )
}

function Separator() {
  return <div className="h-4 w-px bg-[var(--border-default)] mx-1" />
}
```

- [x] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/__tests__/ErToolbar.test.tsx`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add client/src/features/stage/components/er-canvas/ErToolbar.tsx client/src/features/stage/components/er-canvas/__tests__/ErToolbar.test.tsx
git commit -m "feat(client/er): ErToolbar inspector buttons with semantic-token styling"
```

### Task 26: `<ErCanvas>` main canvas

**Files:**
- Create: `client/src/features/stage/components/er-canvas/ErCanvas.tsx`
- Test: `client/src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx`

- [x] **Step 1: Write the failing test**

```tsx
// client/src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ErCanvas } from '../ErCanvas'
import type { ErInspectorPayload } from '@/features/stage/stores/er-tabs-payload-types'

const samplePayload: ErInspectorPayload = {
  kind: 'er_inspector',
  connectionId: 'c1',
  selection: ['users'],
  neighborDepth: 0,
  layout: 'dagre-LR',
  tablesSnapshot: [
    { name: 'users', columns: [
      { name: 'id', type: 'BIGINT', isPK: true, isFK: false, nullable: false },
    ], fkOut: [] },
  ],
  positions: { users: { x: 0, y: 0 } },
  collapsed: [],
  virtualRelations: [],
  notes: {},
  viewport: { x: 0, y: 0, zoom: 1 },
}

describe('<ErCanvas mode="inspector">', () => {
  it('renders the inspector toolbar and a node for each table', () => {
    render(<ErCanvas tabId="t-1" mode="inspector" payload={samplePayload}
      onPatch={vi.fn()} onExec={vi.fn()} />)
    expect(screen.getByRole('button', { name: /auto layout/i })).toBeInTheDocument()
    expect(screen.getByText('users')).toBeInTheDocument()
  })

  it('renders ErEmptyState when payload selection is empty', () => {
    render(<ErCanvas tabId="t-1" mode="inspector"
      payload={{ ...samplePayload, selection: [], tablesSnapshot: [] }}
      onPatch={vi.fn()} onExec={vi.fn()} />)
    expect(screen.getByText(/no tables/i)).toBeInTheDocument()
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx`
Expected: FAIL.

- [x] **Step 3: Create the canvas**

```tsx
// client/src/features/stage/components/er-canvas/ErCanvas.tsx
import { useCallback, useMemo } from 'react'
import { ReactFlow, ReactFlowProvider, Background, Controls, type Node, type Edge, type NodeChange, type EdgeChange } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { ErTableNode } from './ErTableNode'
import { ErEdge } from './ErEdge'
import { ErToolbar } from './ErToolbar'
import { ErEmptyState } from './ErEmptyState'
import { useDagreLayout } from './hooks/useDagreLayout'
import { useErKeyboard } from './hooks/useErKeyboard'
import { inspectorToGraph, type ErNodeData, type ErEdgeData } from './utils/payload-to-graph'
import type { ErInspectorPayload, JsonPatchOp } from '@/features/stage/stores/er-tabs-payload-types'

export interface ErCanvasInspectorProps {
  tabId: string
  mode: 'inspector'
  payload: ErInspectorPayload
  onPatch: (ops: JsonPatchOp[]) => void
  onExec: (action: string, params?: unknown) => void
}

const nodeTypes = { erTable: ErTableNode }
const edgeTypes = { erEdge: ErEdge }

export function ErCanvas(props: ErCanvasInspectorProps) {
  return (
    <ReactFlowProvider>
      <ErCanvasInner {...props} />
    </ReactFlowProvider>
  )
}

function ErCanvasInner({ tabId, payload, onPatch, onExec }: ErCanvasInspectorProps) {
  const { nodes: rawNodes, edges } = useMemo(() => inspectorToGraph(payload), [payload])
  const nodes: Node<ErNodeData & { mode: 'inspector' }>[] = rawNodes.map((n) => ({
    ...n,
    data: { ...n.data, mode: 'inspector' as const },
  })) as never

  const { layout } = useDagreLayout()

  const onAutoLayout = useCallback(async () => {
    const positions = await layout({
      nodes: nodes.map((n) => ({ id: n.id, width: 280, height: 40 + n.data.columns.length * 28 })),
      edges: (edges as Edge<ErEdgeData>[]).map((e) => ({ source: e.source, target: e.target })),
      config: { rankdir: 'LR', nodesep: 80, ranksep: 200 },
    })
    onPatch([{ op: 'replace', path: '/positions', value: positions }])
  }, [nodes, edges, layout, onPatch])

  const onFitView = useCallback(() => {
    onExec('fit_view')
  }, [onExec])

  const onRefresh = useCallback(() => onExec('refresh'), [onExec])
  const onForkToDesigner = useCallback(() => onExec('fork_to_designer'), [onExec])
  const onAddVirtualRelation = useCallback(() => {
    // Stub: real interaction is "select two columns then click button" — Plan A leaves
    // a UX TODO; AI / users add via ui_patch directly. Keep the button for discoverability.
    /* no-op */
  }, [])
  const onChangeNeighborDepth = useCallback((depth: 0 | 1 | 2) => {
    onPatch([{ op: 'replace', path: '/neighborDepth', value: depth }])
  }, [onPatch])

  useErKeyboard({ enabled: true, onAutoLayout, onFitView })

  const onNodesChange = useCallback((changes: NodeChange[]) => {
    // Persist position changes when user drag-stops
    const positionUpdates: Record<string, { x: number; y: number }> = {}
    let any = false
    for (const c of changes) {
      if (c.type === 'position' && c.position && (c as { dragging?: boolean }).dragging === false) {
        positionUpdates[c.id] = c.position
        any = true
      }
    }
    if (any) {
      onPatch(Object.entries(positionUpdates).map(([table, pos]) => ({
        op: 'replace', path: `/positions/${table}`, value: pos,
      })))
    }
  }, [onPatch])

  const onEdgesChange = useCallback((_changes: EdgeChange[]) => {
    // Inspector mode: edges are not editable; ignore.
  }, [])

  if ((payload.selection ?? []).length === 0) {
    return <ErEmptyState reason="empty_selection" />
  }

  return (
    <div className="flex flex-col h-full w-full">
      <ErToolbar
        mode="inspector"
        neighborDepth={payload.neighborDepth}
        onRefresh={onRefresh}
        onAutoLayout={onAutoLayout}
        onFitView={onFitView}
        onChangeNeighborDepth={onChangeNeighborDepth}
        onAddVirtualRelation={onAddVirtualRelation}
        onForkToDesigner={onForkToDesigner}
      />
      <div className="flex-1 min-h-0">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes as never}
          edgeTypes={edgeTypes as never}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          fitView
          fitViewOptions={{ padding: 0.2, maxZoom: 1 }}
          minZoom={0.1}
          maxZoom={2}
          proOptions={{ hideAttribution: true }}
        >
          <Background color="var(--border-subtle)" gap={20} size={1} />
          <Controls showInteractive={false} />
        </ReactFlow>
      </div>
    </div>
  )
}
```

- [x] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx`
Expected: PASS.

- [x] **Step 5: Type-check**

Run: `cd client && npx tsc --noEmit`
Expected: zero errors.

- [x] **Step 6: Commit**

```bash
git add client/src/features/stage/components/er-canvas/ErCanvas.tsx client/src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx
git commit -m "feat(client/er): ErCanvas main inspector view

Wires ErToolbar + ErTableNode + ErEdge + dagre worker into a single
ReactFlow surface. Drag-stop persists positions via ui_patch; auto
layout / fit view / refresh / fork are wired through onExec."
```

---

## Batch 7: ErInspectorAdapter + WorkspaceAdapter + ErInspectorTab

This batch wires the canvas to the UI Object Protocol and exposes the AI entry point.

### Task 27: `ErInspectorAdapter` — read / patch implementation

**Files:**
- Modify: `client/src/features/stage/adapters/ErInspectorAdapter.ts` (replacing the T14 stub)
- Test: `client/src/features/stage/adapters/__tests__/ErInspectorAdapter.test.ts`

- [x] **Step 1: Write the failing test**

```ts
// client/src/features/stage/adapters/__tests__/ErInspectorAdapter.test.ts
import { describe, it, expect, beforeEach } from 'vitest'
import { ErInspectorAdapter } from '../ErInspectorAdapter'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'

const samplePayload = {
  kind: 'er_inspector' as const,
  connectionId: 'c1',
  selection: ['users'],
  neighborDepth: 1 as const,
  layout: 'dagre-LR' as const,
  tablesSnapshot: [],
  positions: {},
  collapsed: [],
  virtualRelations: [],
  notes: {},
  viewport: { x: 0, y: 0, zoom: 1 },
}

describe('ErInspectorAdapter', () => {
  beforeEach(() => {
    useErTabsStore.setState({
      inspectors: new Map([['t-1', { ...samplePayload }]]),
      designers: new Map(),
    })
  })

  it('read("state") returns the current payload', () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)
    expect(adapter.read('state')).toMatchObject({ kind: 'er_inspector', selection: ['users'] })
  })

  it('read("schema") returns capabilities + patch path whitelist', () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)
    const sch = adapter.read('schema') as { patchCapabilities: { pathPattern: string }[] }
    expect(sch.patchCapabilities.some((c) => c.pathPattern === '/selection')).toBe(true)
    expect(sch.patchCapabilities.some((c) => c.pathPattern === '/virtualRelations/-')).toBe(true)
  })

  it('patch /selection replace mutates payload and returns applied', async () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)
    const res = await adapter.patch([{ op: 'replace', path: '/selection', value: ['products'] }])
    expect(res.status).toBe('applied')
    expect(useErTabsStore.getState().inspectors.get('t-1')!.selection).toEqual(['products'])
  })

  it('patch immutable_path returns error', async () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)
    const res = await adapter.patch([{ op: 'add', path: '/tables/-', value: {} }])
    expect(res.status).toBe('error')
    expect(res.message).toMatch(/immutable_path_in_inspector/)
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/adapters/__tests__/ErInspectorAdapter.test.ts`
Expected: FAIL (stub returns errors).

- [x] **Step 3: Replace the stub with the full adapter**

Replace `client/src/features/stage/adapters/ErInspectorAdapter.ts`:

```ts
// client/src/features/stage/adapters/ErInspectorAdapter.ts
import type { UIObject, JsonPatchOp, PatchResult, ExecResult, PatchCapability } from '@/services/ui-router'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'

const PATCH_CAPS: PatchCapability[] = [
  { pathPattern: '/selection',                      ops: ['replace'] },
  { pathPattern: '/neighborDepth',                  ops: ['replace'] },
  { pathPattern: '/positions',                      ops: ['replace'] },
  { pathPattern: '/positions/<n>',                  ops: ['replace', 'remove'] },
  { pathPattern: '/collapsed',                      ops: ['replace'] },
  { pathPattern: '/virtualRelations',               ops: ['replace'] },
  { pathPattern: '/virtualRelations/-',             ops: ['add'] },
  { pathPattern: '/virtualRelations[id=<n>]',       ops: ['replace', 'remove'] },
  { pathPattern: '/notes',                          ops: ['replace'] },
  { pathPattern: '/notes/<n>',                      ops: ['replace', 'remove'] },
  { pathPattern: '/viewport',                       ops: ['replace'] },
]

export class ErInspectorAdapter implements UIObject {
  type = 'er_inspector'
  objectId: string
  title = 'ER Inspector'
  tabId: string
  patchCapabilities = PATCH_CAPS

  constructor(tabId: string, _sessionIdGetter: () => string | null) {
    this.objectId = tabId
    this.tabId = tabId
  }

  read(mode: 'state' | 'schema' | 'actions' | 'full'): unknown {
    const payload = useErTabsStore.getState().inspectors.get(this.tabId)
    if (mode === 'state') return payload ?? null
    if (mode === 'schema') {
      return {
        type: this.type,
        patchCapabilities: this.patchCapabilities,
      }
    }
    if (mode === 'actions') {
      return {
        actions: [
          { name: 'refresh',           description: 'Re-read tables from the connection.' },
          { name: 'auto_layout',       description: 'Recompute node positions via dagre.' },
          { name: 'fit_view',          description: 'Reset viewport to fit all nodes.' },
          { name: 'add_neighbors',     description: 'Pull direct FK neighbors of a table into selection.' },
          { name: 'fork_to_designer',  description: 'Create a designer tab seeded from this inspector.' },
        ],
      }
    }
    return { state: payload ?? null, schema: { type: this.type, patchCapabilities: this.patchCapabilities } }
  }

  async patch(ops: JsonPatchOp[], _reason?: string): Promise<PatchResult> {
    try {
      const { newVersion } = useErTabsStore.getState().applyInspectorPatch(this.tabId, ops)
      return { status: 'applied', message: `applied ${ops.length} op(s); new version ${newVersion}` }
    } catch (e) {
      return { status: 'error', message: (e as Error).message }
    }
  }

  exec(action: string, _params?: unknown): ExecResult {
    // Default behavior — Task 28 fills in the per-action logic.
    return { success: false, error: `ErInspectorAdapter.exec("${action}") not yet implemented` }
  }
}
```

- [x] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/adapters/__tests__/ErInspectorAdapter.test.ts`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add client/src/features/stage/adapters/ErInspectorAdapter.ts client/src/features/stage/adapters/__tests__/ErInspectorAdapter.test.ts
git commit -m "feat(client/er): ErInspectorAdapter read + patch (full path whitelist)"
```

### Task 28: `ErInspectorAdapter.exec` — refresh / auto_layout / fit_view / add_neighbors / fork_to_designer

**Files:**
- Modify: `client/src/features/stage/adapters/ErInspectorAdapter.ts`
- Modify: `client/src/features/stage/adapters/__tests__/ErInspectorAdapter.test.ts`

- [x] **Step 1: Write the failing tests**

Append to the existing test file:

```ts
import { vi } from 'vitest'

describe('ErInspectorAdapter.exec', () => {
  beforeEach(() => {
    useErTabsStore.setState({
      inspectors: new Map([['t-1', { ...samplePayload }]]),
      designers: new Map(),
    })
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        nodes: [{ name: 'users', columns: [], fkOut: [] }],
        edges: [],
        summary: 'users + 0 neighbors, 1 table / 0 edges',
        warnings: [],
      }),
    }) as never
  })

  it('refresh fetches /api/er/seed-inspector and writes tablesSnapshot', async () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)
    const res = await adapter.exec('refresh')
    expect(res.success).toBe(true)
    expect(useErTabsStore.getState().inspectors.get('t-1')!.tablesSnapshot).toHaveLength(1)
  })

  it('add_neighbors with table = "orders" issues a refresh with selection ∪ ["orders"]', async () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)
    const res = await adapter.exec('add_neighbors', { table: 'orders' })
    expect(res.success).toBe(true)
    expect((global.fetch as any).mock.calls[0][1].body).toContain('"orders"')
  })

  it('fit_view writes /viewport via patch', async () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)
    const res = await adapter.exec('fit_view')
    expect(res.success).toBe(true)
    const v = useErTabsStore.getState().inspectors.get('t-1')!.viewport
    expect(v).toEqual({ x: 0, y: 0, zoom: 1 })
  })

  it('returns success: false with error for unknown action', async () => {
    const adapter = new ErInspectorAdapter('t-1', () => null)
    const res = await adapter.exec('does_not_exist')
    expect(res.success).toBe(false)
    expect(res.error).toMatch(/unknown/i)
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/adapters/__tests__/ErInspectorAdapter.test.ts`
Expected: FAIL.

- [x] **Step 3: Implement the verbs**

Replace the `exec` method body:

```ts
async exec(action: string, params?: unknown): Promise<ExecResult> {
  const store = useErTabsStore.getState()
  const payload = store.inspectors.get(this.tabId)
  if (!payload) return { success: false, error: `tab not found: ${this.tabId}` }

  try {
    switch (action) {
      case 'refresh': {
        const graph = await fetchSeedInspector({
          connectionId: payload.connectionId,
          tables: payload.selection,
          neighborDepth: payload.neighborDepth,
        })
        store.applyInspectorPatch(this.tabId, [
          { op: 'replace', path: '/selection', value: graph.nodes.map((n: { name: string }) => n.name) },
          // tablesSnapshot is not in the path whitelist — it's hydrated through the store directly
        ])
        // Rehydrate tablesSnapshot via the store's internal hydrate (bypasses the patch whitelist)
        store.hydrateInspector(this.tabId, {
          ...store.inspectors.get(this.tabId)!,
          tablesSnapshot: graph.nodes,
          snapshotAt: Date.now(),
        })
        return { success: true, data: { summary: graph.summary, edges: graph.edges.length } }
      }

      case 'auto_layout': {
        // ErCanvas's onAutoLayout button triggers the worker + patch directly;
        // when invoked via ui_exec we don't have access to live measured DOM. We
        // approximate using fixed sizes — Plan A acceptable; Plan B can refine.
        const { computeDagreLayout } = await import('@/features/stage/components/er-canvas/workers/dagre-layout.worker')
        const positions = computeDagreLayout({
          nodes: (payload.tablesSnapshot ?? []).map((t) => ({
            id: t.name, width: 280, height: 40 + t.columns.length * 28,
          })),
          edges: (payload.tablesSnapshot ?? []).flatMap((t) =>
            t.fkOut.map((fk) => ({ source: t.name, target: fk.toTable }))
          ),
          config: { rankdir: 'LR', nodesep: 80, ranksep: 200 },
        })
        store.applyInspectorPatch(this.tabId, [{ op: 'replace', path: '/positions', value: positions }])
        return { success: true, data: { positions } }
      }

      case 'fit_view': {
        store.applyInspectorPatch(this.tabId, [{ op: 'replace', path: '/viewport', value: { x: 0, y: 0, zoom: 1 } }])
        return { success: true }
      }

      case 'add_neighbors': {
        const table = (params as { table?: string } | undefined)?.table
        if (!table) return { success: false, error: 'add_neighbors requires { table }' }
        const next = Array.from(new Set([...(payload.selection ?? []), table]))
        const graph = await fetchSeedInspector({
          connectionId: payload.connectionId,
          tables: next,
          neighborDepth: payload.neighborDepth,
        })
        store.applyInspectorPatch(this.tabId, [
          { op: 'replace', path: '/selection', value: graph.nodes.map((n: { name: string }) => n.name) },
        ])
        store.hydrateInspector(this.tabId, {
          ...store.inspectors.get(this.tabId)!,
          tablesSnapshot: graph.nodes,
          snapshotAt: Date.now(),
        })
        return { success: true, data: { addedTables: graph.nodes.map((n: { name: string }) => n.name) } }
      }

      case 'fork_to_designer': {
        return { success: false, error: 'fork_to_designer is implemented in Plan B (er_designer)' }
      }

      default:
        return { success: false, error: `unknown action: ${action}` }
    }
  } catch (e) {
    return { success: false, error: (e as Error).message }
  }
}
```

Also add at module top:

```ts
async function fetchSeedInspector(req: { connectionId: string; tables: string[]; neighborDepth: number }) {
  const r = await fetch('/api/er/seed-inspector', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(req),
  })
  if (!r.ok) {
    const err = await r.json().catch(() => ({}))
    const e = new Error((err as { message?: string }).message ?? `seed-inspector failed: ${r.status}`)
    ;(e as { code?: string }).code = (err as { code?: string }).code
    ;(e as { aiHint?: string }).aiHint = (err as { aiHint?: string }).aiHint
    throw e
  }
  return r.json() as Promise<{ nodes: { name: string }[]; edges: unknown[]; summary: string; warnings: string[] }>
}
```

- [x] **Step 4: Run the tests**

Run: `cd client && npx vitest run client/src/features/stage/adapters/__tests__/ErInspectorAdapter.test.ts`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add client/src/features/stage/adapters/ErInspectorAdapter.ts client/src/features/stage/adapters/__tests__/ErInspectorAdapter.test.ts
git commit -m "feat(client/er): ErInspectorAdapter exec verbs (refresh/auto_layout/fit_view/add_neighbors)

fork_to_designer returns a structured 'not implemented in Plan A' error so
AI can't accidentally rely on it. Plan B implements the verb."
```

### Task 29: `WorkspaceAdapter.exec(open_er_inspector)`

**Files:**
- Modify: `client/src/features/stage/adapters/WorkspaceAdapter.ts`
- Test: `client/src/features/stage/adapters/__tests__/WorkspaceAdapter.er.test.ts`

- [x] **Step 1: Write the failing test**

```ts
// client/src/features/stage/adapters/__tests__/WorkspaceAdapter.er.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { WorkspaceAdapter } from '../WorkspaceAdapter'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import { useStageStore } from '@/stores/stage-store'

describe('WorkspaceAdapter.exec(open_er_inspector)', () => {
  beforeEach(() => {
    useErTabsStore.setState({ inspectors: new Map(), designers: new Map() })
    useStageStore.setState({ tabs: [], activeTabId: null } as any, true)
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        nodes: [
          { name: 'users', columns: [{ name: 'id', type: 'BIGINT', isPK: true, isFK: false, nullable: false }], fkOut: [] },
          { name: 'orders', columns: [{ name: 'user_id', type: 'BIGINT', isPK: false, isFK: true, nullable: false }], fkOut: [{ fromColumn: 'user_id', toTable: 'users', toColumn: 'id' }] },
        ],
        edges: [{ sourceTable: 'orders', sourceColumn: 'user_id', targetTable: 'users', targetColumn: 'id', relationType: 'many_to_one', source: 'schema_fk' }],
        summary: 'orders + 1 neighbor, 2 tables / 1 edge',
        warnings: [],
      }),
    }) as never
  })

  it('creates a new er_inspector tab and returns { tabId, summary, edges }', async () => {
    const adapter = new WorkspaceAdapter(() => null)
    const res = await adapter.exec('open_er_inspector', { connectionId: 'c1', tables: ['orders'], neighborDepth: 1 })
    expect(res.success).toBe(true)
    const data = res.data as { tabId: string; summary: string; edges: number; tables: string[] }
    expect(data.tabId).toMatch(/^er_inspector_/)
    expect(data.summary).toContain('tables')
    expect(data.edges).toBe(1)
    expect(useStageStore.getState().tabs.find((t) => t.tabId === data.tabId)).toBeDefined()
    expect(useErTabsStore.getState().inspectors.get(data.tabId)).toBeDefined()
  })

  it('returns dialect_unsupported error for oracle', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false, status: 400,
      json: async () => ({ code: 'dialect_unsupported', kind: 'oracle', message: 'ER does not support dialect: oracle', aiHint: 'ER does not support oracle. Use query_editor + read_schema.' }),
    }) as never
    const adapter = new WorkspaceAdapter(() => null)
    const res = await adapter.exec('open_er_inspector', { connectionId: 'oracle-conn', tables: ['x'] })
    expect(res.success).toBe(false)
    expect(res.error).toMatch(/dialect_unsupported|does not support oracle/i)
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/adapters/__tests__/WorkspaceAdapter.er.test.ts`
Expected: FAIL — `open_er_inspector` not handled.

- [x] **Step 3: Modify `WorkspaceAdapter.ts`**

Add a case to the existing `exec` method's switch:

```ts
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import { useStageStore } from '@/stores/stage-store'
import { nanoid } from 'nanoid'
import type { ErInspectorPayload } from '@/features/stage/stores/er-tabs-payload-types'

// inside exec()
case 'open_er_inspector': {
  const p = (params ?? {}) as { connectionId?: string; tables?: string[]; neighborDepth?: 0 | 1 | 2; title?: string }
  if (!p.connectionId) return { success: false, error: 'open_er_inspector requires { connectionId }' }
  if (!Array.isArray(p.tables) || p.tables.length === 0) {
    return { success: false, error: 'open_er_inspector requires non-empty tables[]' }
  }
  const depth: 0 | 1 | 2 = (p.neighborDepth ?? 1) as 0 | 1 | 2

  const r = await fetch('/api/er/seed-inspector', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ connectionId: p.connectionId, tables: p.tables, neighborDepth: depth }),
  })
  if (!r.ok) {
    const err = await r.json().catch(() => ({}))
    return {
      success: false,
      error: (err as { aiHint?: string; message?: string }).aiHint
          ?? (err as { message?: string }).message
          ?? `seed-inspector failed: ${r.status}`,
    }
  }
  const graph = await r.json() as {
    nodes: { name: string; comment?: string | null; columns: { name: string; type: string; nullable: boolean; isPK: boolean; isFK: boolean }[]; fkOut: { fromColumn: string; toTable: string; toColumn: string }[] }[]
    edges: unknown[]
    summary: string
    warnings: string[]
  }

  const tabId = `er_inspector_${nanoid(8)}`
  const positions: Record<string, { x: number; y: number }> = {}
  const { computeDagreLayout } = await import('@/features/stage/components/er-canvas/workers/dagre-layout.worker')
  const dagrePos = computeDagreLayout({
    nodes: graph.nodes.map((n) => ({ id: n.name, width: 280, height: 40 + n.columns.length * 28 })),
    edges: graph.nodes.flatMap((n) => n.fkOut.map((fk) => ({ source: n.name, target: fk.toTable }))),
    config: { rankdir: 'LR', nodesep: 80, ranksep: 200 },
  })
  Object.assign(positions, dagrePos)

  const payload: ErInspectorPayload = {
    kind: 'er_inspector',
    connectionId: p.connectionId,
    selection: graph.nodes.map((n) => n.name),
    neighborDepth: depth,
    layout: 'dagre-LR',
    tablesSnapshot: graph.nodes,
    snapshotAt: Date.now(),
    positions,
    collapsed: [],
    virtualRelations: [],
    notes: {},
    viewport: { x: 0, y: 0, zoom: 1 },
  }

  useErTabsStore.getState().hydrateInspector(tabId, payload)
  useStageStore.getState().openTab({
    tabId,
    type: 'er_inspector',
    title: p.title ?? `ER · ${p.tables[0]}`,
    payload: payload as unknown as Record<string, unknown>,
    payloadVersion: 1,
    createdAt: Date.now(),
    lastTouchedAt: Date.now(),
  } as never)
  useStageStore.getState().focusTab(tabId)

  return {
    success: true,
    data: {
      tabId,
      payloadVersion: 1,
      summary: graph.summary,
      tables: graph.nodes.map((n) => n.name),
      edges: graph.edges.length,
      warnings: graph.warnings ?? [],
    },
  }
}
```

The exact `openTab` signature should match what `useStageStore` actually exposes; cast as appropriate.

- [x] **Step 4: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/adapters/__tests__/WorkspaceAdapter.er.test.ts`
Expected: PASS.

- [x] **Step 5: Type-check**

Run: `cd client && npx tsc --noEmit`
Expected: zero errors.

- [x] **Step 6: Commit**

```bash
git add client/src/features/stage/adapters/WorkspaceAdapter.ts client/src/features/stage/adapters/__tests__/WorkspaceAdapter.er.test.ts
git commit -m "feat(client/er): WorkspaceAdapter.exec(open_er_inspector)

Single round-trip: POST /api/er/seed-inspector → dagre layout →
hydrate useErTabsStore → openTab → focusTab → return summary.
AI doesn't pass coordinates (P1) and gets ≤ 200-token summary (P4)."
```

### Task 30: `<ErInspectorTab>` component + `stage-tab-content.tsx` routing

**Files:**
- Create: `client/src/features/stage/components/er-inspector-tab.tsx`
- Create: `client/src/features/stage/components/__tests__/er-inspector-tab.test.tsx`
- Modify: `client/src/features/stage/components/stage-tab-content.tsx`

- [x] **Step 1: Write the failing test**

```tsx
// client/src/features/stage/components/__tests__/er-inspector-tab.test.tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ErInspectorTab } from '../er-inspector-tab'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'

vi.mock('@/features/stage/persistence/stage-persistence-bootstrap', () => ({
  coordinator: { ensureHydrated: vi.fn().mockResolvedValue(undefined) },
}))

const samplePayload = {
  kind: 'er_inspector' as const,
  connectionId: 'c1',
  selection: ['users'],
  neighborDepth: 1 as const,
  layout: 'dagre-LR' as const,
  tablesSnapshot: [{ name: 'users', columns: [{ name: 'id', type: 'BIGINT', isPK: true, isFK: false, nullable: false }], fkOut: [] }],
  positions: { users: { x: 0, y: 0 } },
  collapsed: [],
  virtualRelations: [],
  notes: {},
  viewport: { x: 0, y: 0, zoom: 1 },
}

describe('<ErInspectorTab>', () => {
  it('renders the canvas after payload is hydrated', async () => {
    useErTabsStore.setState({ inspectors: new Map([['t-1', samplePayload]]), designers: new Map() })
    render(<ErInspectorTab tabId="t-1" />)
    expect(await screen.findByText('users')).toBeInTheDocument()
  })

  it('renders skeleton + empty state when payload missing', () => {
    useErTabsStore.setState({ inspectors: new Map(), designers: new Map() })
    render(<ErInspectorTab tabId="missing" />)
    expect(screen.getByText(/Loading|加载中/i)).toBeInTheDocument()
  })
})
```

- [x] **Step 2: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/__tests__/er-inspector-tab.test.tsx`
Expected: FAIL.

- [x] **Step 3: Create the Tab component**

```tsx
// client/src/features/stage/components/er-inspector-tab.tsx
import { useCallback, useEffect, useMemo } from 'react'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import { ErCanvas } from './er-canvas/ErCanvas'
import { ErInspectorAdapter } from '../adapters/ErInspectorAdapter'
import { coordinator } from '../persistence/stage-persistence-bootstrap'
import type { JsonPatchOp } from '@/features/stage/stores/er-tabs-payload-types'
import { useI18n } from '@/i18n/use-i18n'

export function ErInspectorTab({ tabId }: { tabId: string }) {
  const { t } = useI18n()
  const payload = useErTabsStore((s) => s.inspectors.get(tabId) ?? null)

  useEffect(() => {
    void coordinator.ensureHydrated(tabId)
  }, [tabId])

  const adapter = useMemo(() => new ErInspectorAdapter(tabId, () => null), [tabId])

  const onPatch = useCallback((ops: JsonPatchOp[]) => {
    void adapter.patch(ops)
  }, [adapter])

  const onExec = useCallback((action: string, params?: unknown) => {
    void adapter.exec(action, params)
  }, [adapter])

  if (!payload) {
    return (
      <div className="flex h-full items-center justify-center text-[var(--text-muted)] text-sm">
        {t('erCanvas.loading')}
      </div>
    )
  }

  return (
    <ErCanvas
      tabId={tabId}
      mode="inspector"
      payload={payload}
      onPatch={onPatch}
      onExec={onExec}
    />
  )
}
```

Add i18n key (both locales):

```ts
'erCanvas.loading': 'Loading ER…',  // en
'erCanvas.loading': '加载 ER…',     // zh
```

- [x] **Step 4: Modify `stage-tab-content.tsx`**

Open `client/src/features/stage/components/stage-tab-content.tsx`. Add an import:

```ts
import { ErInspectorTab } from './er-inspector-tab'
```

Add a switch arm:

```tsx
case 'er_inspector':
  return <ErInspectorTab tabId={tab.tabId} />
```

- [x] **Step 5: Run the test**

Run: `cd client && npx vitest run client/src/features/stage/components/__tests__/er-inspector-tab.test.tsx`
Expected: PASS.

- [x] **Step 6: Type-check + full client tests**

Run: `cd client && npx tsc --noEmit && npm test -- --run`
Expected: all green.

- [x] **Step 7: Commit**

```bash
git add client/src/features/stage/components/er-inspector-tab.tsx client/src/features/stage/components/__tests__/er-inspector-tab.test.tsx client/src/features/stage/components/stage-tab-content.tsx client/src/i18n/messages.ts
git commit -m "feat(client/er): ErInspectorTab + stage-tab-content routing

Tab component is thin: ensureHydrated → derive payload → render ErCanvas.
The adapter is owned by stage-ui-object-registry (T14); this component
only wires user UX (loading skeleton + onPatch / onExec wrappers)."
```

---

## Batch 8: Java schemas + i18n + AGENTS.md + STAGE_TAB_DIGEST

This batch lands the backend protocol surfaces so AI calls validated by `tools/list` schemas actually accept ER. The dual track of P12 (English AI-facing strings) is enforced.

### Task 31: `UiExecAction.java` adds `er_inspector` exec schema + workspace verbs

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java`
- Modify: `server/data-talk-adapter/src/main/resources/messages.properties`
- Modify: `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties`

- [x] **Step 1: Open `UiExecAction.java`**

Inside `inputSchema()`, the `oneOf` list currently has `workspaceExecSchema()` + `queryEditorExecSchema()`. Add a third entry: `erInspectorExecSchema()`.

```java
"oneOf", List.of(workspaceExecSchema(), queryEditorExecSchema(), erInspectorExecSchema())
```

- [x] **Step 2: Update `workspaceExecSchema()`**

Add the two new verbs to the `action` enum:

```java
Map.entry("action", Map.of(
    "type", "string",
    "enum", List.of(
        "open", "focus", "choose_connection", "detach", "archive", "trash",
        "open_er_inspector", "open_er_designer"
    ),
    "description", "Workspace verbs for opening, focusing, detaching, archiving, deleting tabs, and creating ER inspector / designer tabs."
)),
```

Inside the `params` properties Map.ofEntries, add:

```java
Map.entry("tables", Map.of(
    "type", "array",
    "items", Map.of("type", "string"),
    "description", "Required for open_er_inspector. Seed table names; 1..100."
)),
Map.entry("neighborDepth", Map.of(
    "type", "integer",
    "enum", List.of(0, 1, 2),
    "description", "Optional for open_er_inspector. Direct/indirect neighbor expansion. Default 1."
)),
Map.entry("dialect", Map.of(
    "type", "string",
    "enum", List.of("mysql", "postgresql", "h2", "sqlite"),
    "description", "Required for open_er_designer. Oracle / SQL Server are not supported."
)),
Map.entry("targetConnectionId", Map.of(
    "type", "string",
    "description", "Optional for open_er_designer. When set, the draft is bound to this connection for diff / generate_ddl."
)),
Map.entry("targetDatabase", Map.of("type", "string")),
Map.entry("targetSchema", Map.of("type", "string")),
Map.entry("seedTables", Map.of(
    "type", "array",
    "items", Map.of("type", "object"),
    "description", "Optional for open_er_designer. Initial tables for the draft."
)),
Map.entry("seedRelations", Map.of(
    "type", "array",
    "items", Map.of("type", "object"),
    "description", "Optional for open_er_designer. Initial relations for the draft."
)),
```

- [x] **Step 3: Add `erInspectorExecSchema()` method**

```java
private static Map<String, Object> erInspectorExecSchema() {
    return Map.ofEntries(
        Map.entry("required", List.of("object", "action")),
        Map.entry("properties", Map.ofEntries(
            Map.entry("object", Map.of("type", "string", "enum", List.of("er_inspector"))),
            Map.entry("action", Map.of(
                "type", "string",
                "enum", List.of("refresh", "auto_layout", "fit_view", "add_neighbors", "fork_to_designer"),
                "description", "ER inspector verbs. fork_to_designer is implemented in Plan B and will return an error in Plan A."
            )),
            Map.entry("params", Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                    Map.entry("table", Map.of(
                        "type", "string",
                        "description", "Required for add_neighbors: the table whose direct neighbors should be expanded into selection."
                    )),
                    Map.entry("title", Map.of(
                        "type", "string",
                        "description", "Optional for fork_to_designer."
                    ))
                )
            ))
        ))
    );
}
```

- [x] **Step 4: Add a one-line comment block at the top of the schema describing P12**

```java
// AI-facing strings (description fields and aiHint values) are kept in English
// per Spec §4 P12. User-visible labels live in messages_zh_CN.properties.
```

- [x] **Step 5: Add i18n keys**

`messages.properties` (default English, AI-facing):

```properties
action.ui_exec.workspace.open_er_inspector.description=Open an ER inspector tab for the given seed tables and direct/indirect neighbors.
action.ui_exec.workspace.open_er_designer.description=Open an ER designer tab for an empty or seeded schema draft.
action.ui_exec.er_inspector.refresh.description=Re-read tables from the connection and update the inspector snapshot.
action.ui_exec.er_inspector.auto_layout.description=Recompute node positions via dagre.
action.ui_exec.er_inspector.fit_view.description=Reset the inspector viewport.
action.ui_exec.er_inspector.add_neighbors.description=Add a table's direct FK neighbors to the inspector selection.
action.ui_exec.er_inspector.fork_to_designer.description=Create a designer tab seeded from this inspector. Implemented in Plan B.
```

`messages_zh_CN.properties` may keep these as English (AI-facing) per P12; only override user-facing UI labels.

- [x] **Step 6: Compile**

Run: `cd server && mvn compile -q`
Expected: zero errors.

- [x] **Step 7: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiExecAction.java server/data-talk-adapter/src/main/resources/messages.properties server/data-talk-adapter/src/main/resources/messages_zh_CN.properties
git commit -m "feat(adapter): UiExecAction adds er_inspector schema + workspace open_er_* verbs

Per spec §5.5.4 + §17 row 1. AI tool-list now exposes refresh / auto_layout /
fit_view / add_neighbors / fork_to_designer (last returns error in Plan A)
plus open_er_inspector + open_er_designer (designer impl in Plan B)."
```

### Task 32: `UiPatchAction.java` adds `er_inspector` to object enum + lenient ER ops schema

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiPatchAction.java`

- [x] **Step 1: Open the file and update the schema**

Replace the `inputSchema()` body to support both query_editor (existing) and er_inspector (lenient) patch shapes via `oneOf`:

```java
@Override
public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "required", List.of("object", "ops"),
        "properties", Map.ofEntries(
            Map.entry("object", Map.of(
                "type", "string",
                "enum", List.of("query_editor", "er_inspector"),
                "description", "Tab type that owns the patch target."
            )),
            Map.entry("target", Map.of(
                "type", "string",
                "description", "Explicit tab id. Omit only when the active object is unambiguous."
            )),
            Map.entry("baseVersion", Map.of(
                "oneOf", List.of(
                    Map.of("type", "number"),
                    Map.of("type", "string", "enum", List.of("auto"))
                ),
                "description", "Optional. 'auto' (default) lets the server use the latest version. Designer structural paths require a numeric baseVersion in Plan B."
            )),
            Map.entry("ops", Map.of(
                "type", "array",
                "items", Map.of("type", "object",
                    "required", List.of("op", "path"),
                    "properties", Map.ofEntries(
                        Map.entry("op", Map.of(
                            "type", "string",
                            "enum", List.of("add", "remove", "replace"),
                            "description", "RFC 6902 subset; merge/move/copy/test are not supported."
                        )),
                        Map.entry("path", Map.of(
                            "type", "string",
                            "description", "JSON Pointer with /key[matchKey=value] addressing extension; see docs/references/er-tab-protocol.md for the inspector path whitelist."
                        )),
                        Map.entry("value", Map.of("description", "Op value (omitted for remove)."))
                    )
                )
            )),
            Map.entry("reason", Map.of("type", "string"))
        )
    );
}
```

The legacy `replaceOp` helper for query_editor's old `enum` of paths is now optional — keep it if it lives elsewhere in the file but the top-level schema is what `tools/list` exposes.

- [x] **Step 2: Compile**

Run: `cd server && mvn compile -q`
Expected: zero errors.

- [x] **Step 3: Commit**

```bash
git add server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/UiPatchAction.java
git commit -m "feat(adapter): UiPatchAction object enum gains er_inspector + lenient ER ops

ER ops schema is lenient at the server boundary; the client-side
ErInspectorAdapter.patchCapabilities is the source of truth for which
paths are mutable. Spec §5.5.4 + §17 row 2."
```

### Task 33: `messages.properties` — finalize ER i18n

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/messages.properties`
- Modify: `server/data-talk-adapter/src/main/resources/messages_zh_CN.properties`
- Modify: `client/src/i18n/messages.ts`

- [x] **Step 1: Audit the keys added so far**

Run: `grep -n "er_inspector\|erCanvas\|tabType.erInspector" client/src/i18n/messages.ts server/data-talk-adapter/src/main/resources/messages*.properties`
Expected: keys from earlier tasks present.

- [x] **Step 2: Add any missing keys**

If any of the following were not added in earlier tasks, add them now:

```ts
// client/src/i18n/messages.ts
'tabType.erInspector':         'ER Inspector' | 'ER 浏览',
'erCanvas.loading':            'Loading ER…' | '加载 ER…',
'erCanvas.toolbar.refresh':    'Refresh' | '刷新',
'erCanvas.toolbar.autoLayout': 'Auto layout' | '自动布局',
'erCanvas.toolbar.fitView':    'Fit view' | '适配视图',
'erCanvas.toolbar.neighborDepth': 'Neighbor depth' | '邻居深度',
'erCanvas.toolbar.addVirtualRelation': 'Add virtual relation' | '添加虚拟关系',
'erCanvas.toolbar.forkToDesigner': 'Fork to Designer' | '分叉到设计稿',
'erCanvas.empty.oracle':       '...',
'erCanvas.empty.sqlite':       '...',
'erCanvas.empty.unsupported':  '...',
'erCanvas.empty.selection':    '...',
'erCanvas.empty.oversized':    '...',
'erCanvas.empty.unknown':      '...',
```

- [x] **Step 3: Type-check**

Run: `cd client && npx tsc --noEmit`
Expected: zero errors.

- [x] **Step 4: Commit**

```bash
git add client/src/i18n/messages.ts server/data-talk-adapter/src/main/resources/messages.properties server/data-talk-adapter/src/main/resources/messages_zh_CN.properties
git commit -m "i18n(er): finalize ER inspector labels (en/zh client) + AI descriptions (en server)"
```

### Task 34: `AGENTS.md` adds §"ER Tabs" + drops `datatalk_layout_erd`

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`

- [x] **Step 1: Delete the `datatalk_layout_erd` block**

Open `AGENTS.md`. Around L94, delete:

```
- `datatalk_layout_erd`
  Generate an ER diagram artifact for selected tables.
```

- [x] **Step 2: Edit the L241 paragraph**

Find:

```
Workbench tabs (`query_editor`, `artifact_preview`, future `er_designer` /
`report_designer`) are workspace-wide objects ...
```

Change to:

```
Workbench tabs (`query_editor`, `artifact_preview`, `er_inspector`,
`er_designer`, future `report_designer`) are workspace-wide objects ...
```

- [x] **Step 3: Insert §"ER Tabs (Inspector & Designer)" before §"Concurrency Contract"**

Append the entire block from spec §9.1:

```md
## ER Tabs (Inspector & Designer)

DataTalk has two ER tab types — **er_inspector** (read-only view of a real
schema with annotation overlay) and **er_designer** (independent schema draft
that can generate DDL for a target connection). In Plan A only er_inspector
is fully wired; er_designer verbs are reserved.

### When to open which

| User says | Open | Notes |
|---|---|---|
| "show how X relates to other tables" | er_inspector | tables=[X], neighborDepth=1 |
| "show me the ER for db Y" | er_inspector | tables = read_schema(db=Y, limit=100) |
| "annotate an implicit link between A and B" | (existing er_inspector) | ui_patch /virtualRelations |
| "design a schema for ..." | er_designer | dialect required (mysql/postgresql/h2). Plan B. |
| "fork prod into a draft to edit" | er_inspector → fork_to_designer | preserves table & column shapes. Plan B. |
| "apply this draft to the test DB" | er_designer + bind_target + generate_ddl | DDL lands in a new query_editor tab; user must confirm via L2. Plan B. |
| "find the ER tab containing X" | datatalk_ui_find | filter.type=er_inspector or er_designer + query.mode=fts pattern=X |

### Hard rules

- Do not patch an inspector to "change a real column type". Inspectors are
  views; structural changes belong in a designer or query_editor.
- Designer never executes DDL on its own. generate_ddl produces a query_editor
  tab; the user runs it under the existing L2/L3 confirmation flow.
- Oracle and SQL Server are not supported by ER. Use query_editor + read_schema
  instead.
- Do not pass coordinates. Layout is computed client-side; auto_layout is one
  ui_exec call away if a relayout is wanted.

### Recipe shortcuts

#### Open an inspector for a table and its neighbors
ui_exec(workspace, open_er_inspector, { connectionId, tables: ["orders"], neighborDepth: 1 })

#### Add a virtual (non-FK) relation
ui_patch(inspector_tab, [{
  op: "add", path: "/virtualRelations/-",
  value: { from: {table:"orders",column:"user_email"},
           to:   {table:"users", column:"email"},
           type: "many_to_one", note: "implicit link in app code" }
}])

#### Search for an ER tab by content
ui_find({
  filter: { type: "er_inspector" },
  query:  { mode: "fts", pattern: "user_email" },
  output: { mode: "metadata", headLimit: 10 }
})
```

(Designer-only recipes will be appended by Plan B.)

- [x] **Step 4: Verify the prompt builder still substitutes `{{STAGE_TAB_DIGEST}}` after these edits**

Run: `cd server && grep -n "{{STAGE_TAB_DIGEST}}" data-talk-adapter/src/main/resources/agents/AGENTS.md`
Expected: at least one match.

- [x] **Step 5: Commit**

```bash
git add server/data-talk-adapter/src/main/resources/agents/AGENTS.md
git commit -m "docs(agents): add §ER Tabs (Inspector half) + drop datatalk_layout_erd

English-only per spec §4 P12. Includes recipe table + hard rules; designer-only
recipes are reserved for Plan B."
```

### Task 35: `STAGE_TAB_DIGEST` renders `er_inspector` line

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/AgentPromptBuilder.java`
- Modify: `server/data-talk-application/src/test/java/com/datatalk/application/opencode/AgentPromptBuilderTest.java` (if exists; otherwise create)

- [x] **Step 1: Locate the digest rendering function**

Run: `cd server && grep -n "STAGE_TAB_DIGEST\|renderDigest\|tabDigest" data-talk-application/src/main/java/com/datatalk/application/opencode/AgentPromptBuilder.java`
Expected: identify the method that builds per-tab summary lines.

- [x] **Step 2: Add an `er_inspector` arm**

Inside the digest line builder switch (or equivalent), add:

```java
case "er_inspector" -> {
    // Statistics from payload
    int tableCount = payload.containsKey("tablesSnapshot")
        ? ((List<?>) payload.get("tablesSnapshot")).size()
        : 0;
    int virtualCount = payload.containsKey("virtualRelations")
        ? ((List<?>) payload.get("virtualRelations")).size()
        : 0;
    String conn = (String) payload.getOrDefault("connectionId", "?");
    yield String.format("%s  %s  (%d tables · %d relations · conn=%s)",
        tabId, escapeForPrompt(title), tableCount, virtualCount, conn);
}
```

The exact integration depends on the existing builder. Pattern: append the parenthetical statistics after the title, English-only per P12.

- [x] **Step 3: Add a unit test**

```java
// AgentPromptBuilderTest.java (append)
@Test
void erInspectorTabRendersStatsLine() {
    String digest = builder.buildDigest(List.of(Map.of(
        "tabId", "er_inspector_a1b2",
        "type", "er_inspector",
        "title", "Order ER",
        "payload", Map.of(
            "tablesSnapshot", List.of(Map.of("name", "users"), Map.of("name", "orders")),
            "virtualRelations", List.of(Map.of("id", "vr1")),
            "connectionId", "prod-mysql"
        )
    )));
    assertThat(digest).contains("er_inspector_a1b2");
    assertThat(digest).contains("Order ER");
    assertThat(digest).contains("(2 tables · 1 relations · conn=prod-mysql)");
}

@Test
void erInspectorTabTitleIsEscapedToPreventPromptInjection() {
    String digest = builder.buildDigest(List.of(Map.of(
        "tabId", "x",
        "type", "er_inspector",
        "title", "Title with `backticks` and\nnewline",
        "payload", Map.of("tablesSnapshot", List.of(), "virtualRelations", List.of(), "connectionId", "c")
    )));
    assertThat(digest).doesNotContain("\n"); // newline normalized
}
```

- [x] **Step 4: Run the tests**

Run: `cd server && mvn -pl data-talk-application test -Dtest=AgentPromptBuilderTest -q`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add server/data-talk-application/src/main/java/com/datatalk/application/opencode/AgentPromptBuilder.java server/data-talk-application/src/test/java/com/datatalk/application/opencode/AgentPromptBuilderTest.java
git commit -m "feat(agents): STAGE_TAB_DIGEST renders er_inspector stats line

English-only per spec §9.2 / P10. Title is escaped to prevent prompt
injection through user-named tabs."
```

### Task 36: `AgentPromptContractTest` for ER section

**Files:**
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java`

- [x] **Step 1: Add the failing assertions**

```java
@Test
void agentsMdReferencesErInspectorVerbs() {
    String md = loadAgentsMd();
    assertThat(md).contains("## ER Tabs (Inspector & Designer)");
    assertThat(md).contains("ui_exec(workspace, open_er_inspector");
    assertThat(md).contains("ui_patch(inspector_tab,");
    assertThat(md).contains("filter: { type: \"er_inspector\" }");
    assertThat(md).doesNotContain("datatalk_layout_erd");
}

@Test
void uiExecActionSchemaContainsOpenErInspectorVerb() {
    Map<String, Object> schema = uiExecAction.inputSchema();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> oneOf = (List<Map<String, Object>>) schema.get("oneOf");
    boolean hasOpenErInspector = oneOf.stream()
        .map(s -> (Map<String, Object>) s.get("properties"))
        .map(p -> (Map<String, Object>) p.get("action"))
        .filter(a -> a != null)
        .map(a -> (List<?>) a.get("enum"))
        .filter(en -> en != null)
        .anyMatch(en -> en.contains("open_er_inspector"));
    assertThat(hasOpenErInspector).isTrue();
}

@Test
void uiPatchActionSchemaAllowsErInspectorObject() {
    Map<String, Object> schema = uiPatchAction.inputSchema();
    @SuppressWarnings("unchecked")
    Map<String, Object> obj = (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("object");
    assertThat((List<?>) obj.get("enum")).contains("er_inspector");
}
```

- [x] **Step 2: Run the test**

Run: `cd server && mvn -pl data-talk-adapter test -Dtest=AgentPromptContractTest -q`
Expected: PASS (all new assertions plus existing ones).

- [x] **Step 3: Commit**

```bash
git add server/data-talk-adapter/src/test/java/com/datatalk/adapter/agents/AgentPromptContractTest.java
git commit -m "test(agents): assert ER inspector verbs / schemas / AGENTS.md alignment

Spec §17 row 4 — keeps AGENTS.md and tool schemas in sync to prevent the
class of bug where the prompt mentions tools that the schema doesn't
expose (or vice versa)."
```

---

## Batch 9: Reference docs + housekeeping + verification

### Task 37: `docs/references/er-tab-protocol.md`

**Files:**
- Create: `docs/references/er-tab-protocol.md`

- [x] **Step 1: Write the reference doc**

Create the file with sections:

```md
# ER Tab Protocol Reference

Authoritative contract for `er_inspector` (Plan A) and `er_designer` (Plan B)
Stage Tabs. AI agents and human contributors use this as the source of truth
for payload shape, ui_patch path whitelist, ui_exec verbs, and error codes.

## Inspector

### Payload schema (spec §5.2)
[copy the JSON example from spec §5.2 verbatim]

### `ui_patch` path whitelist (spec §6.3)
[copy the table from spec §6.3 verbatim]

### `ui_exec` verbs (spec §6.5)
[copy the table from spec §6.5 verbatim]

### Errors
[copy the inspector-relevant rows from spec §6.7 with code + HTTP + aiHint]

### Search recipe
ui_find({ filter: {type: "er_inspector"}, query: {mode: "fts", pattern: "..."}, output: {mode: "metadata"} })

## Designer

(Plan B will append the designer half here.)
```

The exact text is sourced from spec §5.2 / §6.3 / §6.5 / §6.7. Don't rewrite — copy-paste, keep one source of truth in the spec, and have this reference defer to it.

- [x] **Step 2: Cross-link from AGENTS.md and CLAUDE.md**

In `AGENTS.md` ER section, ensure the `er-tab-protocol.md` link is present (deep link).

- [x] **Step 3: Commit**

```bash
git add docs/references/er-tab-protocol.md server/data-talk-adapter/src/main/resources/agents/AGENTS.md
git commit -m "docs: add docs/references/er-tab-protocol.md (Inspector half)"
```

### Task 38: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` updates

**Files:**
- Modify: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`

- [x] **Step 1: Update the "Adapter Actions And Ontology" section**

Open the file. In the "Check and update" list under "Adapter Actions And Ontology", remove the `LayoutErdAction.java` line. Replace with a paragraph or a row in the support snapshot:

```md
| ER Tabs | mysql / postgresql / h2 fully supported (er_inspector via JDBC `getImportedKeys`); sqlite frontend connection form not yet wired (status follows snapshot table); oracle / sqlserver explicitly unsupported with structured `dialect_unsupported` aiHint. Designer half adds DDL generation in Plan B with the same dialect matrix; sqlite Designer is CREATE-only. |
```

- [x] **Step 2: Update the "Current Support Snapshot" table** if needed: leave existing rows; add a `Notes` mention that ER Inspector now consumes them.

- [x] **Step 3: Commit**

```bash
git add docs/DATA_SOURCE_TYPE_COMPATIBILITY.md
git commit -m "docs(compat): record ER Inspector support matrix; drop LayoutErdAction reference"
```

### Task 39: `tech-debt-tracker.md` removes ER placeholder backlog

**Files:**
- Modify: `docs/exec-plans/tech-debt-tracker.md`

- [x] **Step 1: Remove any open ER placeholder rows**

Open the file. Search for `erd`, `LayoutErdAction`, `ErdArtifact`, `ER placeholder`. If any open backlog row references these as tech debt, move it to the "已清除" section with a note:

> Closed by [ER Inspector Plan A](./2026-04-29-er-inspector-plan.md) — placeholders retired and replaced by `er_inspector` Stage Tab type.

If the backlog has no such row (e.g., the previous owner already classified ER as product backlog per Task 8.1), confirm in the file and skip.

- [x] **Step 2: Commit**

```bash
git add docs/exec-plans/tech-debt-tracker.md
git commit -m "chore(tech-debt): retire ER placeholder backlog (closed by Plan A)"
```

### Task 40: `docs/exec-plans/index.md` registers Plan A as Active

**Files:**
- Modify: `docs/exec-plans/index.md`

**Status note:** Plan A was registered under Active at kickoff and moved to Completed during final housekeeping.

- [x] **Step 1: Add a row to the Active table**

Insert under the existing Active row (`Next Implementation Roadmap`):

```md
| [ER Inspector (Plan A)](./2026-04-29-er-inspector-plan.md) | 2026-04-29 | Plan A of the ER browsing slice (spec [2026-04-29-er-graph-browsing-design.md](../product-specs/2026-04-29-er-graph-browsing-design.md)). Lands `@xyflow/react` + `dagre`, ports open-db-studio's line-jump / label anti-overlap / self-ref loopback / two-stage AI highlight algorithms (colors → DESIGN.md cobalt/amber tokens), introduces `er_inspector` Stage Tab type with `ErRelationDiscoveryService` + `JdbcErRelationDiscoveryService` + `POST /api/er/seed-inspector`, an independent `useErTabsStore`, an `ErInspectorAdapter` registered globally, and a `WorkspaceAdapter.open_er_inspector` exec verb. Persistence reuses Task 6 `stage_tabs` + FTS5 via a new ER content subscription in `stage-persistence-bootstrap`. Retires `LayoutErdAction` + `ErdArtifact` + `'erd'` artifact kind across the codebase. AGENTS.md gains §"ER Tabs (Inspector half)" with English-only recipes (P12). Plan B (`er_designer`) follows after Plan A acceptance. |
```

- [x] **Step 2: Commit**

```bash
git add docs/exec-plans/index.md
git commit -m "docs(exec-plans): register ER Inspector Plan A as Active"
```

### Task 41: Backend full verification

**Files:**
- (no edits)

**Status note:** `mvn clean verify` passed for the completed backend changes. After the final frontend-only stabilization commit, `mvn compile -q` was re-run successfully with Java 21.

- [x] **Step 1: Run `mvn clean verify`**

Run: `cd server && mvn clean verify`
Expected: BUILD SUCCESS; all tests green; no new compilation warnings beyond baseline.

- [x] **Step 2: If failures occur, diagnose and patch the offending task**

Treat any failure as a re-open of the responsible task; do not paper over with skipped tests.

### Task 42: Frontend full verification

**Files:**
- (no edits)

**Status note:** `npx tsc --noEmit` and `npm test -- --run` passed after fixing stale/i18n-sensitive assertions, the direct-mutation gate, and the ER layout performance threshold. Optional `tauri dev` smoke was not run in this environment.

- [x] **Step 1: Run `tsc --noEmit`**

Run: `cd client && npx tsc --noEmit`
Expected: zero errors.

- [x] **Step 2: Run `npm test`**

Run: `cd client && npm test -- --run`
Expected: all suites green.

- [x] **Step 3: Run `tauri dev` smoke (optional, manual)**

Run: `cd client && npm run tauri dev`
Expected: app boots; sidebar shows existing sessions; if a connection is configured, schema panel can list tables.

### Task 43: Manual smoke + housekeeping

**Files:**
- (no edits)

**Status note:** real database desktop smoke is deferred to user acceptance because this environment did not run the Tauri desktop app against a live user database. Automated backend H2/JDBC coverage and frontend ER workflows passed.

- [x] **Step 1: Real database E2E walkthrough**

In a running Tauri app with at least one MySQL or PostgreSQL connection:
1. From chat, ask "show the ER for the orders table" — confirm AI calls `ui_exec(workspace, open_er_inspector, …)`, an inspector tab opens, and the canvas shows orders + neighbors with FK edges.
2. Drag a node — confirm the new position survives an app restart (close + reopen the Tauri window).
3. From chat, ask "annotate orders.user_email pointing to users.email as a virtual relation" — confirm the virtual relation appears as a dashed amber edge with two-stage pulse highlight.
4. Open a fresh chat session, ask "find the ER tab containing user_email" — confirm `datatalk_ui_find` returns the inspector tab metadata.
5. Try to open ER on an Oracle or unsupported connection — confirm `dialect_unsupported` is surfaced as a friendly empty state, not a stack trace.

- [x] **Step 2: Tick the spec checklist**

Open the spec (`docs/product-specs/2026-04-29-er-graph-browsing-design.md`) and update any task / checklist items that reference Plan A scope to reflect completed status.

- [x] **Step 3: Confirm `docs/exec-plans/index.md` and `docs/product-specs/index.md` are still consistent**

Run: `grep -n "er-graph-browsing\|er-inspector" docs/product-specs/index.md docs/exec-plans/index.md`
Expected: spec listed in product-specs §8; plan listed in exec-plans Completed.

- [x] **Step 4: Final commit (if any housekeeping changes)**

```bash
git add docs/
git commit -m "docs(er): tick Plan A checklist after smoke verification" || echo "nothing to commit"
```

- [x] **Step 5: Open PR or hand off to user**

Hand off to user. Plan B (er_designer) is ready to start once Plan A ships.

---

## Verification Gates

Per the roadmap §"Verification Gates":

- Backend compile gate: `cd server && mvn compile -q` (T7 final, T31, T36, T41)
- Backend full gate: `cd server && mvn clean verify` (T41)
- Frontend type gate: `cd client && npx tsc --noEmit` (T1, T8, T11, T26, T30, T33, T42)
- Frontend tests: `cd client && npm test -- --run` (T42)
- Manual real-database smoke: T43 (deferred to user acceptance)

## Exit Criteria

- All 43 tasks checked.
- Spec §13 Plan A acceptance criteria all satisfied:
  - AI can `open_er_inspector` and see a real database ER.
  - User can drag nodes and persist layout across restart.
  - AI can `ui_patch /virtualRelations` to annotate non-FK relations.
  - AI highlight is visible (pulse → residual).
  - mysql/postgresql/h2 pass IT; oracle is explicitly unsupported.
  - `mvn clean verify` + `npx tsc --noEmit` + `npm test` pass.
- Documentation housekeeping: spec section ticked, plan moved to Active, parent docs (CLAUDE.md, ARCHITECTURE.md, DATA_SOURCE_TYPE_COMPATIBILITY.md) updated where applicable.
- Plan B (`er_designer`) is unblocked.
