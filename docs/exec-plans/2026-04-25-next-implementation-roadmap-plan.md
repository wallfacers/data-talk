# Next Implementation Roadmap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Define the next DataTalk implementation direction after the Stage / Query Editor / MCP foundation shipped, so the project has one active roadmap for what to build next.

**Architecture:** This is a coordination plan, not a direct feature patch. Each non-trivial capability below must become its own focused product spec and execution plan before code changes begin; this roadmap fixes priority, dependency order, excluded approaches, and acceptance gates.

**Tech Stack:** Spring Boot 3.5, Java 21, SQLite metadata store, dynamic JDBC, Tauri v2, React 19, TypeScript, Zustand, TanStack Query, Vitest, JUnit 5, Maven.

---

## Status

- **Created:** 2026-04-25
- **State:** Active
- **Owner intent:** Decide "接下来做什么" after the 2026-04-21 roadmap completed.
- **Primary direction:** Make SQL Workbench reliably useful for daily work before expanding into visualization and intelligent operations.
- **2026-04-27 update:** Tasks 1-4 已完成。Chart Artifact Inline Preview（子计划）已实现，待 commit。下一步应推进 Task 5 Guarded DDL/DML 或 Task 6 Intelligent Operations。

## Context

- `docs/exec-plans/index.md` currently has no active plans before this roadmap is registered.
- The following foundations are already shipped: Stage UI Object Protocol, Stage Window Layout, SQL Workbench, Query Editor object actions, SQL risk classification, Composer data source picker, MCP tool migration, chart fence rendering, and real OpenCode MCP bridge smoke coverage.
- The product roadmap in `docs/product-specs/index.md` places the next major work in "二期": SQL editing, query result management, export, DDL / DML guarded execution, visualization, and performance analysis.
- The current live technical debt list is small. `TD-026` is a stale client file cleanup item; `TD-SINGLE-EMPTY-SESSION-MULTINODE` remains a future deployment concern and is not a desktop blocker.

## Design Inputs

Frontend work in this roadmap must follow [client/DESIGN.md](../../client/DESIGN.md):

- Chat and Workbench must remain one visual system, not separate products.
- New UI must map to semantic tokens before adding component-local styling.
- Stage work surfaces use `bg.subtle` for chrome and `bg.canvas` for the main work surface.
- Tables use stable headers, low-emphasis hover, explicit selected state, and mono treatment for technical values.
- Charts use cobalt for the focus object, amber for compare or warning context, and green/red only for health or outcome semantics.
- Motion confirms state change only; it is not decoration.
- Keyboard access, focus rings, accessible names, and reduced-motion behavior are required for new controls.

## Product Direction

### Recommended Order

1. **Small cleanup:** close stale client residue before the next feature branch grows.
2. **Bounded SQL results polish:** current client-side table pagination, toolbar `LIMIT`, backend `maxRows`, and `truncated` metadata are enough for now; only polish misleading labels or metadata gaps.
3. **Query history enhancement:** current tab-local history is usable; persistence, search, filtering, and reopen modes should be a later focused enhancement.
4. **Export:** next new implementation plan. Ship bounded CSV / JSON first; evaluate Excel only after result metadata is stable.
5. **DDL / DML guarded execution:** extend the existing risk classification into user-facing confirmation flows.
6. **Intelligent operations:** introduce read-only diagnostics such as `EXPLAIN`, slow query analysis, index recommendations, and audit visibility.
7. **Visualization expansion:** ER designer, report, and dashboard work should start after the SQL workbench data surface is stable.

### Explicit Exclusion

- **No virtual scrolling in this roadmap.** User feedback on 2026-04-25: virtual scrolling is too prone to visible jank. Large-result strategy must use pagination, explicit limits, and bounded rendering.

## Spec Mapping

- High-level product roadmap: [docs/product-specs/index.md](../product-specs/index.md), sections 3.4, 3.5, 3.6, 3.8, and 4.
- Current implementation tracking: [docs/exec-plans/index.md](./index.md).
- Design-system gate: [client/DESIGN.md](../../client/DESIGN.md).
- Quality gates: [docs/QUALITY.md](../QUALITY.md).
- Plan workflow: [docs/PLANS.md](../PLANS.md).

## File Structure Map

### Coordination Documents

| File | Responsibility |
|------|----------------|
| `docs/exec-plans/2026-04-25-next-implementation-roadmap-plan.md` | This active roadmap and ordering source |
| `docs/exec-plans/index.md` | Active / Completed registration for this roadmap and later child plans |
| `docs/product-specs/index.md` | Product roadmap source and index for future feature specs |
| `docs/design-docs/index.md` | Design status tracking once child specs are created |
| `docs/exec-plans/tech-debt-tracker.md` | TD-026 closure and any new debt discovered during execution |

### Existing Runtime Areas Future Plans Will Touch

| Area | Files |
|------|-------|
| SQL execution API | `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SqlExecuteController.java`, `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteRequest.java`, `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteResult.java`, `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteResultItem.java` |
| SQL execution service | `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`, `server/data-talk-application/src/main/java/com/datatalk/command/ExecuteSqlCommand.java` |
| Risk analysis | `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java`, `server/data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java`, `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java` |
| Stage SQL frontend | `client/src/features/stage/components/sql-workbench-tab.tsx`, `client/src/features/stage/components/sql-result-panel.tsx`, `client/src/features/stage/components/sql-result-table.tsx`, `client/src/features/stage/components/sql-result-tabs.tsx`, `client/src/features/stage/components/sql-editor-toolbar.tsx`, `client/src/features/stage/components/sql-workbench-status-bar.tsx` |
| Stage SQL state and API | `client/src/features/stage/stores/sql-workbench-store.ts`, `client/src/features/stage/hooks/use-sql-execute.ts`, `client/src/services/api/sql.ts` |
| Stage panels | `client/src/features/stage/components/activity-rail/history-panel.tsx`, `client/src/features/stage/components/activity-rail/schema-panel.tsx`, `client/src/features/stage/components/activity-rail/outline-panel.tsx` |
| Session and workspace context | `client/src/features/session/**`, `client/src/features/stage/adapters/WorkspaceAdapter.ts`, `client/src/features/stage/adapters/QueryEditorAdapter.ts`, `client/src/services/ui-router/**` |

## Batch Plan

### Task 1: Roadmap Activation And Small Cleanup

**Files:**
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/exec-plans/tech-debt-tracker.md`
- Delete candidate: `client/src/features/session/hero-view.tsx`
- Verify: `client/src/features/session/**`

- [ ] **Step 1.1: Register this roadmap as Active**
  - Add `2026-04-25-next-implementation-roadmap-plan.md` to the Active table in `docs/exec-plans/index.md`.
  - Keep the summary short: next roadmap for bounded SQL results, query history, export, guarded DDL / DML, intelligent operations, and later visualization.

- [ ] **Step 1.2: Close TD-026 in a narrow cleanup**
  - Run: `rg -n "HeroView|hero-view" client/src`
  - If the only hit is `client/src/features/session/hero-view.tsx`, delete that file.
  - If imports still exist, remove the dead import path and keep current empty-session behavior in `client/src/features/session/welcome-empty.tsx`.

- [ ] **Step 1.3: Verify cleanup**
  - Run: `cd client && npx tsc --noEmit`
  - Expected: zero TypeScript errors.
  - Update `docs/exec-plans/tech-debt-tracker.md` by moving `TD-026` to cleared debt with the verification command and date.

### Task 2: SQL Results Pagination And Bounded Limits Assessment

**Files:**
- Create only if polish grows beyond a small patch: `docs/product-specs/2026-04-25-sql-results-pagination-design.md`
- Create only if polish grows beyond a small patch: `docs/exec-plans/2026-04-25-sql-results-pagination-plan.md`
- Modify later: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteRequest.java`
- Modify later: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteResultItem.java`
- Modify later: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- Modify later: `client/src/features/stage/components/sql-result-table.tsx`
- Modify later: `client/src/features/stage/components/sql-result-panel.tsx`
- Modify later: `client/src/features/stage/components/sql-limit-select.tsx`
- Modify later: `client/src/features/stage/stores/sql-workbench-store.ts`

- [x] **Step 2.1: Assess current support**
  - 2026-04-25 result: current support is already adequate for the near term.
  - `client/src/features/stage/components/sql-result-table.tsx` renders bounded client-side pages of 100 rows.
  - `client/src/features/stage/components/sql-limit-select.tsx` exposes `10 / 100 / 1000 / none`.
  - `client/src/features/stage/utils/query-editor-actions.ts` injects `LIMIT` into `select` / `with` statements and preserves existing `LIMIT`.
  - `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java` enforces `datatalk.sql.max-rows` and returns `truncated`.
  - No virtual scrolling should be introduced.

- [ ] **Step 2.2: Optional polish only**
  - Clarify the `none` limit label so users do not read it as unlimited; backend still has `datatalk.sql.max-rows`.
  - Keep `rowCount` semantics as returned row count, not total database count.
  - Avoid `COUNT(*)` or server-side page queries until a concrete product need appears.

- [ ] **Step 2.3: Defer server pagination**
  - Do not add `pageNumber`, cursor, or server-side page loading in the next child plan.
  - Current bounded rendering plus query `LIMIT` is sufficient for the export plan.

- [ ] **Step 2.4: Define tests and verification**
  - If polish is implemented, run the focused stage tests and `cd client && npx tsc --noEmit`.
  - Backend tests are only needed if `SqlExecuteService` or API DTOs change.

### Task 3: Query History And Result Management Assessment

**Files:**
- Create later: `docs/product-specs/2026-04-25-query-history-result-management-design.md`
- Create later: `docs/exec-plans/2026-04-25-query-history-result-management-plan.md`
- Modify later: `client/src/features/stage/components/activity-rail/history-panel.tsx`
- Modify later: `client/src/features/stage/stores/sql-workbench-store.ts`
- Modify later: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Modify later: `server/data-talk-application/src/main/java/com/datatalk/application/persistence/ActionInvocationRepository.java`
- Modify later: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SqlExecuteController.java`

- [x] **Step 3.1: Assess current support**
  - 2026-04-25 result: a useful tab-local first slice already exists.
  - `SqlWorkbenchStore.history` stores the latest 50 entries per query editor tab.
  - `runQueryEditorSql` appends success, risk-blocked, and error entries.
  - `HistoryPanel` renders entries in the Activity Rail and appends selected SQL back into the editor.
  - Existing tests cover append, clear, and history panel behavior.

- [ ] **Step 3.2: Defer persistent history**
  - Missing capabilities remain valid backlog: persistence across refresh, session-level and cross-session views, search, filters, context capture, replace-current-editor, open-new-tab, and rerun actions.
  - Do not block SQL export on these missing capabilities.

- [ ] **Step 3.3: Future source-of-truth decision**
  - When this becomes active, first decide whether to reuse `action_invocations` metadata or create a dedicated SQL history table.
  - Persisting full result rows is still excluded; store SQL text, context, status, timing, row count, and error summary.

- [ ] **Step 3.4: Define tests and verification**
  - Future frontend tests should cover history panel rendering, replace/append/rerun modes, filters, and result-tab isolation.
  - Future backend tests are only required once history persists beyond the Zustand tab store.

### Task 4: Bounded Export

**Files:**
- Create: `docs/product-specs/2026-04-25-sql-result-export-design.md`
- Create: `docs/exec-plans/2026-04-25-sql-result-export-plan.md`
- Modify later: `client/src/features/stage/components/sql-result-panel.tsx`
- Modify later: `client/src/features/stage/components/sql-result-table.tsx`
- Modify later: `client/src/features/stage/utils/query-editor-actions.ts`
- Modify later: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SqlExecuteController.java`

- [x] **Step 4.0: Select as next new implementation**
  - 2026-04-25 decision: proceed with SQL result export after roadmap notes are updated.
  - First slice is frontend-only unless implementation discovers a backend gap.
  - Reuse existing chat Markdown table serialization patterns where possible.
  - 2026-04-25 child docs created: `docs/product-specs/2026-04-25-sql-result-export-design.md` and `docs/exec-plans/2026-04-25-sql-result-export-plan.md`.

- [x] **Step 4.1: Ship small exports first**
  - Shipped 2026-04-27: copy CSV, copy JSON, and download CSV in `SqlResultTable` footer.
  - Supported scope: current page and current bounded result already returned to the client.

- [x] **Step 4.2: Define safety limits**
  - Scope selector shows page vs returned result; truncated results are labeled honestly.

- [x] **Step 4.3: Define user-facing controls**
  - Export controls in `SqlResultTable` footer: scope select, copy CSV, copy JSON, download CSV.
  - Controls use shadcn/ui with accessible names and aria-labels.

- [x] **Step 4.4: Define tests and verification**
  - 5 serializer tests + 2 component tests, all 18 tests pass, `tsc --noEmit` clean.

### Task 5: Guarded DDL / DML Execution

**Files:**
- Create: `docs/product-specs/2026-04-25-guarded-ddl-dml-execution-design.md`
- Create: `docs/exec-plans/2026-04-25-guarded-ddl-dml-execution-plan.md`
- Modify later: `server/data-talk-application/src/main/java/com/datatalk/application/sql/CalciteSqlRiskAnalyzer.java`
- Modify later: `server/data-talk-application/src/main/java/com/datatalk/application/session/ActionDispatcher.java`
- Modify later: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`
- Modify later: `client/src/features/stage/components/sql-dml-summary-panel.tsx`
- Modify later: `client/src/features/chat/components/tools/renderers/execute-sql.tsx`
- Modify later: `client/src/features/chat/components/tools/renderers/preview-sql.tsx`

- [x] **Step 5.1: Build on existing risk classification** — 已实现于 Guarded DDL/DML Execution 子计划（2026-04-27 完成）

- [x] **Step 5.2: Define backend enforcement** — 已实现

- [x] **Step 5.3: Define user flow** — 已实现（Workbench AlertDialog + Chat inline 卡片）

- [x] **Step 5.4: Define tests and verification** — 214 后端测试 + 400 前端测试通过

### Task 6: Intelligent Operations Track

**Files:**
- Create: `docs/product-specs/2026-04-25-intelligent-operations-design.md`
- Create: `docs/exec-plans/2026-04-25-intelligent-operations-plan.md`
- Modify later: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`
- Modify later: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java`
- Modify later: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- Modify later: `client/src/features/stage/components/activity-rail/schema-panel.tsx`
- Modify later: `client/src/features/stage/components/query-editor-inspector.tsx`
- Modify later: `client/src/features/chat/components/tools/renderers/execute-sql.tsx`

- [ ] **Step 6.1: Start with read-only diagnostics**
  - First intelligent operations scope should be L1-only: `EXPLAIN`, execution-plan capture, slow-query explanation, table statistics display, and index recommendation text.
  - It should not create indexes or change schema in its first slice.

- [ ] **Step 6.2: Define dialect boundaries**
  - MySQL, PostgreSQL, and H2 have different `EXPLAIN` output shapes.
  - The first plan should normalize a minimal common model: statement, dialect, raw plan text or rows, timing if available, and AI-readable summary input.

- [ ] **Step 6.3: Define AI collaboration**
  - AI can ask DataTalk for plan data and schema metadata.
  - DataTalk performs database reads; OpenCode only reasons over returned context.
  - Index recommendations are suggestions until the guarded DDL / DML flow supports confirmed index creation.

- [ ] **Step 6.4: Define operations surfaces**
  - Query Editor inspector can show execution plan, risk level, timing, and recommendation summary.
  - Chat tool rendering can show a compact diagnostic card with "open in workbench".
  - Audit visibility should start as "what SQL did I run in this session" using query history before becoming a full compliance log.

- [ ] **Step 6.5: Define tests and verification**
  - Backend tests cover dialect-specific plan command construction and failure handling.
  - Frontend tests cover inspector rendering, empty plan states, and chat-to-workbench promotion.

### Task 7: Visualization Expansion Candidate

**Files:**
- Create when this slice starts: `docs/product-specs/2026-04-25-visualization-expansion-design.md`
- Create when this slice starts: `docs/exec-plans/2026-04-25-visualization-expansion-plan.md`
- Review later: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/LayoutErdAction.java`
- Review later: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/RenderChartAction.java`
- Review later: `client/src/features/ontology/components/chart-artifact.tsx`
- Review later: `client/src/features/chat/components/markdown/chart-block.tsx`
- Review later: `client/src/features/stage/components/artifact-preview-tab.tsx`

- [ ] **Step 7.1: Keep visualization behind SQL Workbench stabilization**
  - ER designer, report builder, and dashboard composition depend on stable result metadata and export semantics.
  - This roadmap records the direction but does not start visualization implementation before Tasks 2 through 4 are planned.

- [ ] **Step 7.2: Pick one first visualization slice**
  - Candidate A: ER graph browsing from metadata.
  - Candidate B: chart editing and replacement from existing chart fence artifacts.
  - Candidate C: dashboard tab that composes existing chart artifacts.
  - The first visualization child plan should choose one candidate only.

## Ordering And Parallelism

- Task 1 can run immediately.
- Tasks 2 and 3 have been assessed as partially shipped. Task 4 is the next new child plan.
- Task 5 should start after Task 2 because confirmation UI needs accurate result and impact metadata.
- Task 6 can be designed in parallel with Task 5, but its first implementation must stay read-only until guarded DDL / DML exists.
- Task 7 stays behind Tasks 2 through 4 unless product priority explicitly changes.

## Verification Gates

Every child implementation plan created from this roadmap must include:

- Backend compile gate: `cd server && mvn compile -q`
- Backend full gate when server behavior changes: `cd server && mvn clean verify`
- Frontend type gate when client behavior changes: `cd client && npx tsc --noEmit`
- Focused frontend tests for changed `client/src/features/stage/**` or `client/src/features/session/**`
- Documentation housekeeping: plan checkbox status, `docs/exec-plans/index.md`, `docs/design-docs/index.md`, and affected canonical docs

## Exit Criteria

- TD-026 is closed or explicitly split into a small active cleanup plan.
- SQL results pagination / limits are either left as existing support plus polish, or a small follow-up plan exists; virtual scrolling remains excluded.
- Query history / result management is intentionally deferred with the current tab-local implementation documented.
- Bounded export has a clear child spec and plan, or the roadmap records why it was deferred.
- Guarded DDL / DML execution has a child spec that connects backend risk enforcement to frontend confirmation UI.
- Intelligent operations has a child spec covering read-only diagnostics, dialect boundaries, and AI collaboration rules.
- The next roadmap or child plans are registered in `docs/exec-plans/index.md` before this roadmap is moved to Completed.
