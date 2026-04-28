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
- **2026-04-27 update:** Tasks 1-5 已完成（TD-026 已清除、Pagination/Query History 评估完成、Bounded Export shipped、Guarded DDL/DML shipped、Chart Artifact Inline Preview 子计划 shipped）。同日产品总设计新增 §3.11 跨 session 工作台 + Tab 内容索引 + `ui_find` 与 §3.12 外部数据采集（skill 驱动），roadmap 重排：原 Task 6 Intelligent Operations 降为 Task 7、原 Task 7 Visualization 降为 Task 8，新插入 Task 6 跨 session 工作台持久化作为下一启动项，新增 Task 9 外部数据采集作为三期占位。
- **2026-04-28 update:** Task 6 已通过 [Cross-Session Workbench Tabs](./2026-04-27-cross-session-workbench-tabs-plan.md) 和 Shared Stage Workbench P1/P2/P3/P3.5 系列收口；Task 7 Intelligent Operations 已 shipped；当前下一条产品主线是 Task 8 Visualization Expansion，Task 9 继续等待 Task 8 至少一个生产切片稳定。

## Context

- `docs/exec-plans/index.md` currently has no active plans before this roadmap is registered.
- The following foundations are already shipped: Stage UI Object Protocol, Stage Window Layout, SQL Workbench, Query Editor object actions, SQL risk classification, Composer data source picker, MCP tool migration, chart fence rendering, and real OpenCode MCP bridge smoke coverage.
- The product roadmap in `docs/product-specs/index.md` places the next major work in "二期": SQL editing, query result management, export, DDL / DML guarded execution, visualization, and performance analysis.
- The current live technical debt list is empty after the 2026-04-29 `TD-033` cleanup removed the deprecated `workspace.close` / `query_editor.close` aliases.

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

1. **Small cleanup:** close stale client residue before the next feature branch grows. _(shipped)_
2. **Bounded SQL results polish:** current client-side table pagination, toolbar `LIMIT`, backend `maxRows`, and `truncated` metadata are enough for now; only polish misleading labels or metadata gaps. _(assessed, no new child plan)_
3. **Query history enhancement:** current tab-local history is usable; persistence, search, filtering, and reopen modes should be a later focused enhancement. _(assessed, deferred)_
4. **Export:** next new implementation plan. Ship bounded CSV / JSON first; evaluate Excel only after result metadata is stable. _(shipped)_
5. **DDL / DML guarded execution:** extend the existing risk classification into user-facing confirmation flows. _(shipped)_
6. **Cross-session workbench persistence + Tab content index + `ui_find`:** promote workbench Tabs to globally persisted, content-indexed objects so the AI can locate, read, and patch any open work surface across sessions; this is the foundation that makes report / dashboard / ER work durable rather than throwaway artifacts. _(shipped — see [2026-04-27-cross-session-workbench-tabs-plan.md](./2026-04-27-cross-session-workbench-tabs-plan.md) and Shared Stage Workbench P1/P2/P3/P3.5)_
7. **Intelligent operations:** introduce read-only diagnostics such as `EXPLAIN`, slow query analysis, index recommendations, and audit visibility. _(shipped — see [2026-04-27-intelligent-operations-plan.md](./2026-04-27-intelligent-operations-plan.md))_
8. **Visualization expansion:** ER designer, report, and dashboard work; now unblocked by Task 6 persistence, but should still start with one focused child spec rather than bundling all visualization surfaces.
9. **External data ingestion via skills:** e-commerce platform / generic web data fetching with auto-table creation under guarded execution. _(phase-3 placeholder; do not start until Tasks 6 and 8 are stable)_

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

- [x] **Step 1.1: Register this roadmap as Active** — 已登记于 `docs/exec-plans/index.md` Active 表。

- [x] **Step 1.2: Close TD-026 in a narrow cleanup** — `client/src/features/session/hero-view.tsx` 已删除，`rg "HeroView|hero-view" client/src` 零结果。

- [x] **Step 1.3: Verify cleanup** — `TD-026` 已迁入 `docs/exec-plans/tech-debt-tracker.md` 已清除债务表（2026-04-27）。

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

- [x] **Step 2.2: Optional polish only** — 评估完成，无即时 polish 启动；后续按需触发独立小补丁，不再走 roadmap。

- [x] **Step 2.3: Defer server pagination** — 已确认延后；export 子计划已基于现有 bounded rendering 完成（Task 4 shipped）。

- [x] **Step 2.4: Define tests and verification** — 无新代码改动，仅评估，不需要新测试。

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

- [x] **Step 3.2: Defer persistent history** — 已确认延后；export 已 ship，未被 history 阻塞。

- [x] **Step 3.3: Future source-of-truth decision** — 决策延后到 history 真正立项时；候选方案（复用 `action_invocations` vs 独立表）已记录。

- [x] **Step 3.4: Define tests and verification** — 无代码改动，无新测试需求。

> **2026-04-27 note:** Task 6（Cross-Session Workbench Persistence）落地后，全局 Tab 持久化与内容索引能力会与 query history 形成强耦合。届时应先看看是否可以让 history 复用 Task 6 的 Tab 持久化与 `ui_find` 通道，而不是单独建一张 SQL history 表。

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

### Task 6: Cross-Session Workbench Persistence And Content-Aware Tab Search

> Promotes 总设计 §3.11. Shipped via the Cross-Session Workbench Tabs plan and the Shared Stage Workbench follow-up phases; checklist below records the roadmap-level closure rather than the full child-plan task list.

**Files:**
- Create: `docs/product-specs/<YYYY-MM-DD>-cross-session-workbench-tabs-design.md`
- Create: `docs/exec-plans/<YYYY-MM-DD>-cross-session-workbench-tabs-plan.md`
- Modify later: SQLite migration under `server/data-talk-adapter/src/main/resources/db/migration/`
- Modify later: new domain / application Tab persistence model under `server/data-talk-domain/**` and `server/data-talk-application/**`
- Modify later: new `UiFindAction` under `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/`
- Modify later: `client/src/features/stage/stores/stage-store.ts` (cross-session Tab scope + hydration)
- Modify later: `client/src/features/stage/adapters/WorkspaceAdapter.ts` (and per-Tab adapters that need full-document `ui_read`)
- Modify later: `client/src/features/session/**` (sidebar entry: "Tab 打开记录")
- Modify later: AGENTS.md / system-prompt template (inject open-Tab summary + recently-touched Tabs + `ui_find` priority)

- [x] **Step 6.1: Promote 总设计 §3.11 to a focused product spec**
  - Decompose into: Tab persistence schema, content indexing strategy, `ui_find` action contract, sidebar Tab history surface, system-prompt injection.
  - Spec must explicitly cite [Stage UI Object Protocol](../product-specs/2026-04-20-stage-ui-object-protocol-design.md) as the foundation it extends.
  - Spec must classify each existing Tab type as workbench-scope (e.g. `query_editor`, future `er_designer`, future `report_designer`) vs session-scope (e.g. `chart_artifact`, `file_preview` snapshots), and set the default for new Tab types.

- [x] **Step 6.2: Define persistence contract**
  - SQLite schema for Tabs: `id / type / title / objectId / connectionId / payloadSnapshot / lastTouchedAt / openedBySessionId`.
  - Decide whether content snapshots live alongside metadata or in a separate blob table (likely separate to keep list queries cheap).
  - Migration is additive; on cold start `StageStore` hydrates from DB instead of starting empty.
  - Out of scope: full per-keystroke history; only logical save points and explicit AI patches snapshot.

- [x] **Step 6.3: Define indexing and `ui_find` contract**
  - `ui_find` is the Claude Code `find + grep + cat` analogue: single action covers metadata filter, content search, and ranged content read.
  - Filter modes: by `type / connectionId / objectId / openedBySessionId / lastTouchedAt window`.
  - Search modes: substring, regex, optional semantic (deferred slice if scope grows).
  - Read mode: returns `tabId` plus matched fragment with byte / line range so AI can decide a precise `ui_patch` without re-fetching the whole document.
  - `ui_find` stays read-only. All mutation must continue to flow through `ui_patch` per the existing UI Object Protocol.

- [x] **Step 6.4: Define UI surface**
  - Sidebar gains a "Tab 打开记录" entry parallel to the session list, using `bg.subtle / border.subtle / interaction.selected / text.strong` per [client/DESIGN.md](../../client/DESIGN.md).
  - Search input and result navigation are keyboard accessible; focus rings follow `interaction.focusRing`.
  - Search hits highlight using `accent.primary`; switching / focusing a Tab animates with `motion.normal + easing.standard`, used only as state confirmation.
  - A Tab opened in session A renders identically when re-opened from session B.

- [x] **Step 6.5: Define AI integration**
  - System prompt injects "current open Tabs summary + recently-touched Tabs"; `ui_find` listed as the preferred locator before broader `ui_list` traversal or full-document `ui_read`.
  - AGENTS.md updated to teach the find / grep / cat mental model and rule out "fetch everything to context" patterns.
  - Tab summary surfaces enough discriminator metadata (object kind + a one-line snippet) so the AI can pick targets without an extra round-trip.

- [x] **Step 6.6: Define tests and verification**
  - Backend: `TabRepository` CRUD, indexer determinism, `ui_find` filter / search / read modes, migration round-trip, cold-start hydration.
  - Frontend: cross-session Tab hydration, sidebar Tab history rendering, search interaction, `ui_find → ui_patch` flow over a mocked Adapter.
  - Verification gates per the roadmap's Verification Gates section.

### Task 7: Intelligent Operations Track

**Files:**
- Create: `docs/product-specs/2026-04-25-intelligent-operations-design.md`
- Create: `docs/exec-plans/2026-04-25-intelligent-operations-plan.md`
- Modify later: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ExecuteSqlAction.java`
- Modify later: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/ReadSchemaAction.java`
- Modify later: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- Modify later: `client/src/features/stage/components/activity-rail/schema-panel.tsx`
- Modify later: `client/src/features/stage/components/query-editor-inspector.tsx`
- Modify later: `client/src/features/chat/components/tools/renderers/execute-sql.tsx`

- [x] **Step 7.1: Start with read-only diagnostics**
  - Spec confirmed: L1-only scope. `datatalk_explain_query` + `datatalk_index_hints` are read-only; no DDL/index creation in first slice.
  - Product spec: [docs/product-specs/2026-04-27-intelligent-operations-design.md](../product-specs/2026-04-27-intelligent-operations-design.md)

- [x] **Step 7.2: Define dialect boundaries**
  - MySqlDiagnosticsProvider (`EXPLAIN FORMAT=JSON`), PostgreSqlDiagnosticsProvider (`EXPLAIN (FORMAT JSON, ANALYZE false)`), H2DiagnosticsProvider (text parse), OracleDiagnosticsProvider (stub → UNSUPPORTED).
  - Normalized model: `ExplainPlan { sql, dialect, nodes: ExplainNode[], scanTypes }` + `DiagnosticResult<T>` sealed interface.

- [x] **Step 7.3: Define AI collaboration**
  - `datatalk_explain_query`: plain EXPLAIN, returns normalized tree + raw JSON.
  - `datatalk_index_hints`: chains EXPLAIN → AI call once → returns `IndexRecommendation[]`; AI sees a single-step interface.
  - Three stub actions registered: `datatalk_lock_info`, `datatalk_pool_status`, `datatalk_table_space` (return `{unsupported: true}` until future specs).

- [x] **Step 7.4: Define operations surfaces**
  - `DiagnosticsTab` registered as `scope: 'workspace'` / `persistent: true` from the start (workbench-scope Tab, ready for Task 6 persistence).
  - `DiagnosticsPanel` in Activity Rail alongside Schema/History/Outline.
  - `DiagnosticsCard` in chat tool renderer for both `datatalk.explain_query` and `datatalk.index_hints`.
  - SQL Editor toolbar gains an "Explain" button that opens/focuses the DiagnosticsTab.
  - REST: `POST /api/sessions/{sessionId}/diagnostics/explain` + `/index-hints` for direct toolbar entry.

- [x] **Step 7.5: Define tests and verification**
  - Backend: JUnit tests for DiagnosticsService, all four providers, ExplainQueryAction, IndexHintsAction, stub actions, DiagnosticsController (MockMvc).
  - Frontend: vitest for TypeScript types, API service, ExplainPlanTree, IndexRecommendationList, DiagnosticsTab, DiagnosticsCard, DiagnosticsPanel.
  - Implementation plan: [docs/exec-plans/2026-04-27-intelligent-operations-plan.md](./2026-04-27-intelligent-operations-plan.md) — 20 tasks, 4 batches. Execute after Task 6 completes.

### Task 8: Visualization Expansion Candidate

**Files:**
- Create when this slice starts: `docs/product-specs/<YYYY-MM-DD>-visualization-expansion-design.md`
- Create when this slice starts: `docs/exec-plans/<YYYY-MM-DD>-visualization-expansion-plan.md`
- Review later: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/LayoutErdAction.java`
- Review later: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/actions/RenderChartAction.java`
- Review later: `client/src/features/ontology/components/chart-artifact.tsx`
- Review later: `client/src/features/chat/components/markdown/chart-block.tsx`
- Review later: `client/src/features/stage/components/artifact-preview-tab.tsx`

- [ ] **Step 8.1: Keep visualization behind cross-session persistence**
  - ER designer, report builder, and dashboard composition only deliver real product value once Task 6 (cross-session Tab persistence) ships, because these objects are long-lived and edited across sessions.
  - This roadmap records the direction but does not start visualization implementation before Task 6 has at least a child spec, and ideally before its first usable slice ships.

- [ ] **Step 8.2: Pick one first visualization slice**
  - Candidate A: ER graph browsing from metadata, evolving toward an editable `er_designer` Tab type registered as workbench-scope under Task 6.
  - Candidate B: chart editing and replacement from existing chart fence artifacts.
  - Candidate C: dashboard tab that composes existing chart artifacts, registered as a workbench-scope persistent Tab via Task 6.
  - The first visualization child plan should choose one candidate only, and explicitly state how its persistent objects integrate with `ui_find` / `ui_patch`.

### Task 9: External Data Ingestion via Skills (Phase 3 Placeholder)

> Promotes 总设计 §3.12. Phase-3 placeholder; do not start a child spec until Task 6 ships and at least one Task 8 slice is in production.

**Files:**
- Create when this slice starts: `docs/product-specs/<YYYY-MM-DD>-external-data-ingestion-skills-design.md`
- Create when this slice starts: `docs/exec-plans/<YYYY-MM-DD>-external-data-ingestion-skills-plan.md`

- [ ] **Step 9.1: Hold until Tasks 6 and 8 are stable**
  - Do not open a child spec earlier; ingestion is an additive capability, not a foundation.
  - When opened, scope must start from a generic HTTP / API skill scaffolding interoperating with OpenCode MCP / skill protocol; platform-specific skills (Taobao / JD / Pinduoduo / Douyin commerce) come only after the generic scaffolding is proven.

- [ ] **Step 9.2: Hard architectural rules to preserve when this opens**
  - DataTalk core must not bundle any platform-specific SDK. All ingestion lives in skill packages, including credentials (OAuth / API key) and platform-specific scraping logic.
  - Auto-table creation must reuse the L2 risk flow shipped in Task 5; ingestion does not get a private bypass for guarded execution.
  - Ingestion source URL, run timing, and raw payload references must hit the audit log per §3.8.
  - Ingestion progress / mapping / target-table previews must surface as persistent Tabs registered through Task 6, so a user can resume a partially-configured pipeline across sessions.

- [ ] **Step 9.3: First-slice direction (when activated)**
  - Generic scaffolding first: HTTP / REST / GraphQL skill harness, credential vault hookup, schema-inference helper, target-table preview with L2 confirmation.
  - Only then sequence platform-specific skills, one platform per child plan.

## Ordering And Parallelism

- Tasks 1 through 5 are closed (shipped or assessed-and-deferred); no further roadmap-level action.
- Task 6 (Cross-Session Workbench Persistence) is closed; future work should use the shipped persistent Tab + `ui_find` substrate instead of reopening the foundation.
- Task 7 (Intelligent Operations) is closed as a read-only diagnostics slice.
- Task 8 (Visualization Expansion) is the next product candidate. ER / report / dashboard objects must register as workbench-scope persistent Tabs from the start; do not ship throwaway session-scope versions first.
- Task 9 (External Data Ingestion) is a phase-3 placeholder; do not open a child spec until at least one Task 8 slice is in production.

## Verification Gates

Every child implementation plan created from this roadmap must include:

- Backend compile gate: `cd server && mvn compile -q`
- Backend full gate when server behavior changes: `cd server && mvn clean verify`
- Frontend type gate when client behavior changes: `cd client && npx tsc --noEmit`
- Focused frontend tests for changed `client/src/features/stage/**` or `client/src/features/session/**`
- Documentation housekeeping: plan checkbox status, `docs/exec-plans/index.md`, `docs/design-docs/index.md`, and affected canonical docs

## Exit Criteria

- TD-026 is closed or explicitly split into a small active cleanup plan. _(closed 2026-04-27)_
- SQL results pagination / limits are either left as existing support plus polish, or a small follow-up plan exists; virtual scrolling remains excluded. _(left as existing support; no new plan)_
- Query history / result management is intentionally deferred with the current tab-local implementation documented. _(deferred; will likely fold into Task 6's persistence + `ui_find` once that ships)_
- Bounded export has a clear child spec and plan, or the roadmap records why it was deferred. _(shipped)_
- Guarded DDL / DML execution has a child spec that connects backend risk enforcement to frontend confirmation UI. _(shipped)_
- Cross-session workbench persistence + `ui_find` has a child spec covering Tab persistence schema, content indexing, action contract, sidebar surface, and AI integration; all classified Tab types have an explicit workbench-scope vs session-scope decision. _(shipped)_
- Intelligent operations has a child spec covering read-only diagnostics, dialect boundaries, and AI collaboration rules; surfaces target persistent Tabs from Task 6 where applicable. _(shipped)_
- Visualization expansion has at least one child spec choosing one initial slice (ER / chart-edit / dashboard), with explicit `ui_find` / `ui_patch` integration for its persistent objects.
- External data ingestion remains a registered phase-3 placeholder until Tasks 6 and 8 are stable; no child spec opened prematurely.
- The next roadmap or child plans are registered in `docs/exec-plans/index.md` before this roadmap is moved to Completed.
