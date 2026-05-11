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
- **2026-04-27 update:** Tasks 1-5 已完成（TD-026 已清除、Pagination/Query History 评估完成、Bounded Export shipped、Guarded DDL/DML shipped、Chart Artifact Inline Preview 子计划 shipped）。同日产品总设计新增 §3.11 跨 session 工作台 + Tab 内容索引 + `ui_find` 与 §3.12 外部数据采集（skill 驱动），roadmap 重排：原 Task 6 Intelligent Operations 降为 Task 7、原 Task 7 Visualization 降为 Task 8，新插入 Task 6 跨 session 工作台持久化作为下一启动项；当时新增外部数据采集作为下一阶段占位，后续在 2026-04-29 后移为 Task 10。
- **2026-04-28 update:** Task 6 已通过 [Cross-Session Workbench Tabs](./2026-04-27-cross-session-workbench-tabs-plan.md) 和 Shared Stage Workbench P1/P2/P3/P3.5 系列收口；Task 7 Intelligent Operations 已 shipped；当时下一条产品主线是 Task 8 Visualization Expansion，外部数据采集继续等待 Task 8 至少一个生产切片稳定。
- **2026-04-29 update:** 插入新的 Task 9 Data Source Coverage Expansion，外部数据采集后移为 Task 10。新增数据源候选必须按 [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md) 分批落地；不得只改 UI 下拉或 prompt 文案就宣称支持。
- **2026-05-06 update:** Task 8 ER slice fully closed — ER Designer (Plan B) manual Tauri + real-database smoke confirmed by owner. Task 8 first visualization slice (ER Inspector + Designer) is now complete. Report/Dashboard remain future visualization candidates.
- **2026-05-07 update:** Task 9 Wave A (MariaDB/Oracle/SQL Server) 全部收口为 first-class 支持。代码实现已完成：MariaDB 有意复用 MySQL 生态（含 MariaDbDdlGenerator 修复）、Oracle 有意 unsupported PL/SQL+ER+Diagnostics execution、SQL Server 新增 mssql-jdbc 12.8.1 驱动 + loginTimeout + mssql Dialect alias。Wave B 7 个 child plan 仍待实现。后端 `mvn clean verify` BUILD SUCCESS (148 tests, 0 failures)。
- **2026-05-12 update:** Task 9 Wave B 7/7 全部完成（DuckDB / ClickHouse / Apache Doris / StarRocks / Presto / Trino / Hive 均为 first-class）。Wave C 6/6 全部完成：TiDB（产出 MySqlProtocolReuseRule）→ openGauss（产出 PgForkReuseRule）→ OceanBase（产出 MultiModeConnectionShape v1）→ KingbaseES（消费 PgForkReuseRule + MultiModeConnectionShape）→ Dameng（独立无依赖）→ **GaussDB（集中式密码连接，gaussdbjdbc v2.0-8.218.0）** 全部 ship。Task 9 data source coverage expansion **完整关闭**——17 个 first-class kind。Task 8 Dashboard P1 已 ship（bezel v0.1.0 + iframe sandbox 渲染管线），Dashboard Premium Redesign（12 行业大屏）与 FileArtifact 集成已收尾。Task 11 Part 5b（Housekeeping & Maintenance）已 ship，OpenCode 工作目录 + File Artifact 系统 5 Part 全链路闭环。
- **2026-04-29 out-of-roadmap insertion:** 用户从运行时观察提出了 OpenCode 工作目录治理 + AI 产出文件归属问题（`~/.data-talk/opencode/` 下出现孤儿文件、备份/log 堆积、删除 session 不联动清理 OpenCode 自管目录、未来报告/ER 图等持久资产无归属维度），不在原 roadmap Task 1-10 范围内。经 brainstorming + spec 修订（含一次代码核实驱动的 v2 重写）后立项为 Task 11 "OpenCode Workdir & File Artifact System"。这是**运行时基础设施**类别的工作，与 Task 8 visualization 是天然搭档（ER/报表/数据集等长生命周期产物需要 file artifact 系统提供物理归属与生命周期管理）。Spec 与 Part 1+2+3+4+5a 计划已完成并登记到对应 index；按 5 Part 推进，Part 1 (Migration & Domain) 已于 2026-04-29 完成，Part 2 (Watcher & Reconcile) 已于 2026-04-30 完成，Part 3 (MCP Tool & AGENTS Template) 已于 2026-05-07 完成，Part 4 (Frontend Tabs) 已于 2026-05-07 完成，Part 5a (Deletion Flow & Archive/Discard Endpoints) 已于 2026-05-07 完成，Part 5b (Housekeeping & Maintenance) 待补正式 child plan。

## Context

- `docs/exec-plans/index.md` currently has no active plans before this roadmap is registered.
- The following foundations are already shipped: Stage UI Object Protocol, Stage Window Layout, SQL Workbench, Query Editor object actions, SQL risk classification, Composer data source picker, MCP tool migration, chart fence rendering, and real OpenCode MCP bridge smoke coverage.
- The product roadmap in `docs/product-specs/index.md` places the next major work in "二期": SQL editing, query result management, export, DDL / DML guarded execution, visualization, performance analysis, and broader data-source coverage.
- The current live technical debt list is empty after the 2026-04-29 `TD-033` cleanup removed the deprecated `workspace.close` / `query_editor.close` aliases.
- Database popularity inputs are directional, not support commitments. DB-Engines' April 2026 ranking lists 431 systems and keeps Oracle / MySQL / SQL Server / PostgreSQL / MongoDB / Snowflake / Databricks / Redis / Db2 / Cassandra / Elasticsearch / SQLite / MariaDB / Apache Hive / BigQuery / ClickHouse / DuckDB / Trino among visible high-ranking or fast-moving systems; implementation priority still follows product fit, JDBC feasibility, and compatibility-gate cost.

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
3. **Query history enhancement:** current tab-local history is usable. After Task 6 shipped, persistent history should reuse the persistent Tab / `ui_find` substrate instead of introducing a standalone history store first. This remains a later product enhancement, not technical debt. _(re-assessed 2026-04-29, deferred)_
4. **Export:** next new implementation plan. Ship bounded CSV / JSON first; evaluate Excel only after result metadata is stable. _(shipped)_
5. **DDL / DML guarded execution:** extend the existing risk classification into user-facing confirmation flows. _(shipped)_
6. **Cross-session workbench persistence + Tab content index + `ui_find`:** promote workbench Tabs to globally persisted, content-indexed objects so the AI can locate, read, and patch any open work surface across sessions; this is the foundation that makes report / dashboard / ER work durable rather than throwaway artifacts. _(shipped — see [2026-04-27-cross-session-workbench-tabs-plan.md](./2026-04-27-cross-session-workbench-tabs-plan.md) and Shared Stage Workbench P1/P2/P3/P3.5)_
7. **Intelligent operations:** introduce read-only diagnostics such as `EXPLAIN`, slow query analysis, index recommendations, and audit visibility. _(shipped — see [2026-04-27-intelligent-operations-plan.md](./2026-04-27-intelligent-operations-plan.md))_
8. **Visualization expansion:** ER designer, report, and dashboard work; now unblocked by Task 6 persistence, but should still start with one focused child spec rather than bundling all visualization surfaces. Existing disabled / placeholder ER, report, and dashboard UI is classified as product backlog for this task, not as a separate tech-debt item.
9. **Data source coverage expansion:** add first-class support for mainstream and currently popular database/data-warehouse sources in batches, starting from SQL/JDBC-compatible systems and following [DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md) for every kind. Candidate pool includes Doris / Apache Doris, Oracle, Hive, GaussDB / openGauss, Dameng, SQL Server, MariaDB, ClickHouse, DuckDB, Snowflake, BigQuery, Redshift, Databricks SQL, Trino / Presto, StarRocks, OceanBase, TiDB, KingbaseES, IBM Db2, SAP HANA, Teradata, Elasticsearch / OpenSearch, and MongoDB. _(new 2026-04-29; open only as focused child specs, not one mega-implementation)_
10. **External data ingestion via skills:** e-commerce platform / generic web data fetching with auto-table creation under guarded execution. _(phase-3 placeholder; do not start until Tasks 8 and 9 have stable production slices)_
11. **OpenCode workdir & file artifact system:** runtime-infrastructure work inserted out-of-roadmap on 2026-04-29 in response to user-observed issues (orphan files in OpenCode cwd, backup/log accumulation, session deletion not cascading to OpenCode-managed directories, no ownership model for AI-produced reports / ER diagrams / scripts). Establishes a dual-track (temporary / persistent) and dual-dimension (session / connection) `file_artifact` system separate from the existing payload-type `artifacts` table; subdirectory soft-isolation (single OpenCode process), `datatalk_archive_artifact` MCP tool, AGENTS.md `{{ACTIVE_SESSION_DIR}}` placeholder, `io.methvin` directory watcher, two-phase session/connection delete with final-confirm modal, and HousekeepingScheduler for backup/log/_trash rotation + one-shot legacy migration. Runs in parallel with Task 8 visualization rather than blocking it; physical persistence of long-lived ER / report objects from Task 8 will eventually flow through this system.

### Explicit Exclusion

- **No virtual scrolling in this roadmap.** User feedback on 2026-04-25: virtual scrolling is too prone to visible jank. Large-result strategy must use pagination, explicit limits, and bounded rendering.

## Spec Mapping

- High-level product roadmap: [docs/product-specs/index.md](../product-specs/index.md), sections 3.4, 3.5, 3.6, 3.8, and 4.
- Current implementation tracking: [docs/exec-plans/index.md](./index.md).
- Design-system gate: [client/DESIGN.md](../../client/DESIGN.md).
- Data-source compatibility gate: [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
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
| Data source expansion | `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`, `server/data-talk-application/src/main/java/com/datatalk/application/connection/**`, `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java`, `server/data-talk-application/src/main/java/com/datatalk/application/sql/**`, `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/**`, `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/**`, `client/src/features/settings/data-sources/**`, `client/src/features/stage/sql-dialects/**`, `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` |

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

- [x] **Step 3.2: Defer persistent history** — 已确认延后；export 已 ship，未被 history 阻塞。2026-04-29 复评后继续延后，且不登记为技术债；后续做法应优先复用 Task 6 已落地的持久 Tab、内容索引和 `ui_find` 能力。

- [x] **Step 3.3: Future source-of-truth decision** — 决策延后到 history 真正立项时；候选方案从“复用 `action_invocations` vs 独立表”收敛为先评估持久 Tab / `ui_find` 是否足够承载跨 Tab 查询历史检索、重开和过滤，只有明确不足时再引入专用 history 表或复用 `action_invocations`。

- [x] **Step 3.4: Define tests and verification** — 无代码改动，无新测试需求。

> **2026-04-29 re-assessment:** Task 6 已落地，全局 Tab 持久化与内容索引能力现在是 query history 的默认底座。Persistent query history 不作为技术债登记；当它重新进入 roadmap 时，应作为“result/history management”产品增强开独立 child spec，并先证明持久 Tab / `ui_find` 不能满足的具体场景，再考虑新增专用 history 表。

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

- [x] **Step 8.1: Keep visualization behind cross-session persistence**
  - ER designer, report builder, and dashboard composition only deliver real product value once Task 6 (cross-session Tab persistence) ships, because these objects are long-lived and edited across sessions.
  - Task 6 has now shipped; visualization is no longer blocked by persistence. Start with one child spec and one production slice.
  - Existing disabled / placeholder ER, report, and dashboard UI is intentionally moved out of tech-debt tracking. It is the visible product backlog for this task and should be retired by the relevant visualization child plans.
  - Status 2026-04-29: ER Inspector Plan A and ER Designer Plan B both use persistent workbench-scope Stage tabs registered through the Task 6 substrate.

- [x] **Step 8.2: Pick one first visualization slice**
  - Candidate A: ER graph browsing from metadata, evolving toward an editable `er_designer` Tab type registered as workbench-scope under Task 6.
  - Candidate B: chart editing and replacement from existing chart fence artifacts.
  - Candidate C: dashboard tab that composes existing chart artifacts, registered as a workbench-scope persistent Tab via Task 6.
  - The first visualization child plan should choose one candidate only, and explicitly state how its persistent objects integrate with `ui_find` / `ui_patch`.
  - Recommendation after the 2026-04-29 roadmap review: choose Candidate A first, because `LayoutErdAction` already exists and ER browsing is the narrowest visualization slice that exercises persistent workbench objects without requiring dashboard composition semantics.
  - Status 2026-04-29: Candidate A was selected and implemented through [ER Inspector Plan A](./2026-04-29-er-inspector-plan.md) plus [ER Designer Plan B](./2026-04-29-er-designer-plan.md). Automated verification passed; Plan B manual real-database smoke remains pending in its active plan.
  - Status 2026-05-06: Plan B manual smoke confirmed by owner. ER Inspector + Designer first visualization slice is fully complete. Report/Dashboard remain future candidates.

### Task 9: Data Source Coverage Expansion

> Promotes product roadmap §3.1 and the mandatory [Data Source Type Compatibility Gate](../DATA_SOURCE_TYPE_COMPATIBILITY.md). This is a platform-expansion track, not a single implementation batch. Every database kind must get its own focused child spec / plan or a small compatible batch with shared behavior.

**Files:**
- Create when this slice starts: `docs/product-specs/<YYYY-MM-DD>-data-source-coverage-<kind>-design.md`
- Create when this slice starts: `docs/exec-plans/<YYYY-MM-DD>-data-source-coverage-<kind>-plan.md`
- Update for every child plan: `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md`
- Review later: `server/data-talk-application/src/main/java/com/datatalk/application/connection/ConnectionKind.java`
- Review later: `server/data-talk-application/src/main/java/com/datatalk/application/connection/JdbcUrlBuilder.java`
- Review later: `server/data-talk-application/src/main/java/com/datatalk/application/session/ConnectionTargetDiscoveryService.java`
- Review later: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- Review later: `server/data-talk-infrastructure/src/main/java/com/datatalk/sql/DefaultSqlStatementSplitters.java`
- Review later: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/diagnostics/*DiagnosticsProvider.java`
- Review later: `client/src/features/settings/data-sources/connection-form-dialog.tsx`
- Review later: `client/src/features/stage/utils/format-sql.ts`
- Review later: `client/src/features/stage/sql-dialects/*.json`
- Review later: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md`

- [x] **Step 9.1: Keep the gate mandatory**
  - Before any child spec proposes or implements a new kind, read and apply [DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
  - Each child plan must mark every gate checklist section as applicable or `N/A` with a concrete reason.
  - If a child plan finds a missing compatibility point, update `DATA_SOURCE_TYPE_COMPATIBILITY.md` in the same change before claiming completion.
  - Do not add prompt-only or UI-only support. A kind is first-class only after connection creation, connection test, metadata discovery, SQL execution, result normalization, guarded risk flow, diagnostics status, frontend context UI, MCP/action schemas, runtime prompt, and tests are aligned or explicitly unsupported.

- [x] **Step 9.2: Candidate matrix**
  - User-requested candidates: Apache Doris (`apache_doris`, alias `doris`), Oracle (`oracle`), Apache Hive (`hive`), GaussDB / openGauss (`gaussdb`, `opengauss` pending exact driver decision), and Dameng (`dameng`, aliases `dm` / `dm8`).
  - Additional mainstream candidates to evaluate: SQL Server (`sqlserver`, alias `mssql`), MariaDB (`mariadb`), ClickHouse (`clickhouse`), DuckDB (`duckdb`), Snowflake (`snowflake`), Google BigQuery (`bigquery`), Amazon Redshift (`redshift`), Databricks SQL (`databricks_sql`), Trino / Presto (`trino`, `presto`), StarRocks (`starrocks`), OceanBase (`oceanbase`), TiDB (`tidb`), KingbaseES (`kingbase` / `kingbasees`), IBM Db2 (`db2`), SAP HANA (`sap_hana`), Teradata (`teradata`), Elasticsearch / OpenSearch (`elasticsearch`, `opensearch`), and MongoDB (`mongodb`).
  - The first implementation wave should prefer SQL/JDBC-compatible engines where DataTalk can preserve the existing SQL Workbench contract. Non-SQL/search/document systems need a separate read/query contract and must not be forced through fake SQL semantics.

- [x] **Step 9.3: Recommended implementation waves**
  - Wave A: close existing partial/stub support and common enterprise SQL: `sqlite` frontend completion, `oracle`, `sqlserver`, `mariadb`.
  - Wave B: high-demand analytics / OLAP JDBC engines: `apache_doris`, `starrocks`, `clickhouse`, `hive`, `trino`, `presto`, `duckdb`.
  - Wave C: domestic / enterprise compatibility: `gaussdb` / `opengauss`, `dameng`, `kingbase`, `oceanbase`, `tidb`.
  - Wave D: cloud warehouses: `snowflake`, `bigquery`, `redshift`, `databricks_sql`.
  - Wave E: non-SQL or semi-SQL data sources: `mongodb`, `elasticsearch`, `opensearch`; these need product decisions for read/query model, schema discovery, and mutation policy before implementation.
  - Exact order inside a wave should be chosen by user demand, available JDBC driver quality/license, test fixture availability, and whether the dialect can safely share an existing splitter/risk strategy.

- [x] **Step 9.4: Child-plan acceptance gates**
  - Update support snapshot and candidate notes in [DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md).
  - Backend: `JdbcUrlBuilder`, connection kind normalization, metadata discovery, context application (`database`/`schema`/`catalog`), SQL splitter, risk analyzer/guard, result normalization, diagnostics provider or structured unsupported.
  - Frontend: connection form fields, default port, data source picker, query editor context selectors, SQL formatter mapping, SQL outline keyword set, i18n.
  - AI/MCP: action schemas, runtime `AGENTS.md` rules, prompt contract tests, no unsupported capability overclaim.
  - Verification: focused tests for the touched kind plus `cd server && mvn compile -q`; run `cd client && npx tsc --noEmit` when frontend surfaces change.

### Task 10: External Data Ingestion via Skills (Phase 3 Placeholder)

> Promotes 总设计 §3.12. Phase-3 placeholder; do not start a child spec until Task 8 has at least one production slice and Task 9 has at least one stable target data source beyond the current first-class set.

**Files:**
- Create when this slice starts: `docs/product-specs/<YYYY-MM-DD>-external-data-ingestion-skills-design.md`
- Create when this slice starts: `docs/exec-plans/<YYYY-MM-DD>-external-data-ingestion-skills-plan.md`

- [ ] **Step 10.1: Hold until Tasks 8 and 9 are stable**
  - Do not open a child spec earlier; ingestion is an additive capability, not a foundation.
  - When opened, scope must start from a generic HTTP / API skill scaffolding interoperating with OpenCode MCP / skill protocol; platform-specific skills (Taobao / JD / Pinduoduo / Douyin commerce) come only after the generic scaffolding is proven.

- [ ] **Step 10.2: Hard architectural rules to preserve when this opens**
  - DataTalk core must not bundle any platform-specific SDK. All ingestion lives in skill packages, including credentials (OAuth / API key) and platform-specific scraping logic.
  - Auto-table creation must reuse the L2 risk flow shipped in Task 5; ingestion does not get a private bypass for guarded execution.
  - Ingestion source URL, run timing, and raw payload references must hit the audit log per §3.8.
  - Ingestion progress / mapping / target-table previews must surface as persistent Tabs registered through Task 6, so a user can resume a partially-configured pipeline across sessions.

- [ ] **Step 10.3: First-slice direction (when activated)**
  - Generic scaffolding first: HTTP / REST / GraphQL skill harness, credential vault hookup, schema-inference helper, target-table preview with L2 confirmation.
  - Only then sequence platform-specific skills, one platform per child plan.

### Task 11: OpenCode Workdir And File Artifact System (Out-Of-Roadmap, 2026-04-29)

> **Out-of-roadmap insertion**: not in the original Task 1-10 product direction. Triggered by user-observed runtime hygiene issues in `~/.data-talk/opencode/` and a stated need for ownership over AI-produced reports / ER diagrams / scripts. Runs as a **runtime-infrastructure track** in parallel with Task 8 visualization rather than blocking it. Reviewed and revised through one round of code-verification feedback before plan kick-off.

**Spec:** [docs/product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md](../product-specs/2026-04-29-opencode-workdir-and-artifact-system-design.md) (Draft v2 / partially implemented — first version was rejected during review for assuming per-session OpenCode cwd; v2 switched to subdirectory soft-isolation since OpenCode runs as a single process, and Part 1+2 have since shipped)

**Plans:**
- [Part 1 — Migration & Domain](./2026-04-29-file-artifact-system-part1-domain-and-migration-plan.md) (completed 2026-04-29)
- [Part 2 — Watcher & Reconcile](./2026-04-30-file-artifact-system-part2-watcher-reconcile-plan.md) (completed 2026-04-30)
- Part 3-5 (pending formal child plans — MCP+AGENTS / Frontend Tabs / Deletion Flow + Housekeeping)

**Files (high-level; per-Part plans hold exact paths):**
- New: `server/data-talk-domain/src/main/java/com/datatalk/domain/fileartifact/**` (FileArtifact sealed record + 3 enums)
- New: `server/data-talk-application/src/main/java/com/datatalk/application/fileartifact/**` (FileArtifactService + SessionWorkdirService + repository + path safety)
- New: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/fileartifact/**` (JdbcFileArtifactRepository) and `db/migration/V14__file_artifact.sql`
- New: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/FileArtifactController.java` and `actions/ArchiveArtifactActionHandler.java`
- Modify: `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` (classpath template; add `## Output Files & Artifacts` section + `{{ACTIVE_SESSION_DIR}}` placeholder)
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/stage/AgentPromptBuilder.java` (add second placeholder rendering)
- Modify: `server/data-talk-domain/src/main/java/com/datatalk/domain/event/DtEvent.java` (5 new sealed records)
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/session/SessionService.java` (Phase 2 cascade for file_artifact)
- New (frontend): Stage Files Tab + Files Library Tab, Zustand `useFileArtifactsStore`, Settings Maintenance tab, i18n keys in `client/src/i18n/messages.ts`

- [ ] **Step 11.1: Spec gate — code-verified v2 only**
  - The first spec assumed per-session OpenCode cwd; this is **not** supported (`OpenCodeProcessManager` uses a single fixed cwd). v2 spec uses subdirectory soft-isolation under the existing process cwd; do not regress to per-session cwd without first changing the process model.
  - The new `file_artifact` table must remain disjoint from the existing `artifacts(payload-type)` table — different name, different schema, different lifecycle. Do not unify them in this track.
  - `session_id` / `connection_id` on `file_artifact` are intentionally **not** FK-constrained; application-layer code (`SessionService.deleteRecord` + `FileArtifactService`) manages references. Archived rows survive session deletion with `session_id` set to `NULL`.

- [ ] **Step 11.2: Path safety is non-negotiable**
  - Eight rules in spec §5.4 (relative path, no `..`, realpath containment, `_`-prefixed segments rejected, NOFOLLOW_LINKS attribute check, regular-file check, pre-mv stat re-check, ATOMIC_MOVE without REPLACE_EXISTING).
  - Both watcher (followLinks=false) and `datatalk_archive_artifact` (back-end re-validation) must enforce these. AI cannot be trusted to stay inside the session subdir.

- [ ] **Step 11.3: Lifecycle is application-managed**
  - Two-phase delete: GET candidates → 409 on conflict → final-confirm modal → force=true → application-layer cascade (`deleteTransientByForSession` + `detachArchivedFromSession`) + `rm -rf opencode/sessions/<sid>/` + OpenCode `session_diff` / `tool-output` cleanup.
  - HousekeepingScheduler runs on startup + daily cron: `opencode.json.dt-bak-*` rotation (5 + 7 days union), OpenCode log rotation (5 + 7 days), `_trash` 7-day cleanup with synchronized `DELETE FROM file_artifact`, reconcile against on-disk truth.
  - LegacyMigrationRunner: one-shot move of orphan root files (e.g. observed `datatalk-tools-test-report.md`) under `~/.data-talk/opencode/` to `~/.data-talk/_legacy/`, marker file `~/.data-talk/.legacy-migrated` prevents repeat runs.

- [ ] **Step 11.4: AGENTS.md modification path is the classpath template**
  - Modify `server/data-talk-adapter/src/main/resources/agents/AGENTS.md` (the source-of-truth template); the runtime copy at `~/.data-talk/opencode/AGENTS.md` is rewritten by `AgentPromptBuilder` on each start, so direct edits there will be lost.
  - Add `AgentsTemplateContractTest` that asserts the `## Output Files & Artifacts` section is present and contains the `{{ACTIVE_SESSION_DIR}}` placeholder; this prevents accidental deletion in future template refactors.

- [ ] **Step 11.5: UI integration follows client/DESIGN.md and Stage globalization**
  - Two new Stage tab types (`FILES`, `FILES_LIBRARY`); tab instances are global; tab content is a function of `activeSessionId` / `activeConnectionId` per `tab-type-registry.scope`. Sidebar is **not** modified (user explicit constraint during brainstorming).
  - Status uses double-channel signaling (icon + color band), not color alone; warning surface for candidates, danger only for actual delete confirmation.
  - i18n keys land in `client/src/i18n/messages.ts` (the project's actual i18n location), not a separate locale JSON.

- [ ] **Step 11.6: Verification gates per Part**
  - Backend: `cd server && mvn clean verify` after each Part; new `FlywayMigrationIT` test asserts V14 migration creates `file_artifact` table and does **not** alter the existing `artifacts` table.
  - Frontend: vitest + `cd client && npx tsc --noEmit` after Part 4.
  - Integration: end-to-end Temporary → Candidate → Archived flow with FakeOpenCodeServer, tested per Part 3 / Part 5.
  - Path-attack regression: symlink to outside cwd, `..` traversal, `_`-prefixed segments — all must be rejected (Part 1 service tests).

## Ordering And Parallelism

- Tasks 1 through 5 are closed (shipped or assessed-and-deferred); no further roadmap-level action.
- Task 6 (Cross-Session Workbench Persistence) is closed; future work should use the shipped persistent Tab + `ui_find` substrate instead of reopening the foundation.
- Task 7 (Intelligent Operations) is closed as a read-only diagnostics slice.
- Query history persistence is a deferred product enhancement. It should reuse the Task 6 persistent Tab / `ui_find` substrate first and should not be tracked as technical debt unless a concrete reliability or data-loss defect is found.
- Task 8 (Visualization Expansion) first ER slice is fully complete — Plan A + Plan B + 2026-04-30 follow-up + 2026-05-06 manual smoke confirmation. Report/dashboard remain future visualization candidates.
- Task 9 (Data Source Coverage Expansion): Wave A 4/4 + Wave B 7/7 + Wave C 5/6 已完成（共 16 个 first-class kind）；GaussDB（Wave C 最后一个）仍为文档占位待独立 child spec。Wave D（Snowflake / BigQuery / Redshift / Databricks SQL）和 Wave E（MongoDB / Elasticsearch / OpenSearch）尚未启动。
- Task 10 (External Data Ingestion) is a phase-3 placeholder; do not open a child spec until at least one Task 8 slice is in production and Task 9 has at least one stable target data source beyond the current first-class set.
- Task 11 (OpenCode Workdir & File Artifact System) is an **out-of-roadmap** runtime-infrastructure track inserted on 2026-04-29. All 5 Parts complete: Part 1 (Migration & Domain), Part 2 (Watcher & Reconcile), Part 3 (MCP Tool & AGENTS Template), Part 4 (Frontend Tabs), Part 5a (Deletion Flow & Archive/Discard Endpoints), Part 5b (Housekeeping & Maintenance)。全链路闭环：物理文件归属 + DB 行生命周期 + 前端 Files/Files Library Tab + 定时治理。

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
- Query history / result management is intentionally deferred with the current tab-local implementation documented. _(re-assessed 2026-04-29; future product enhancement should first reuse Task 6 persistent Tabs + `ui_find`, not a standalone tech-debt item)_
- Bounded export has a clear child spec and plan, or the roadmap records why it was deferred. _(shipped)_
- Guarded DDL / DML execution has a child spec that connects backend risk enforcement to frontend confirmation UI. _(shipped)_
- Cross-session workbench persistence + `ui_find` has a child spec covering Tab persistence schema, content indexing, action contract, sidebar surface, and AI integration; all classified Tab types have an explicit workbench-scope vs session-scope decision. _(shipped)_
- Intelligent operations has a child spec covering read-only diagnostics, dialect boundaries, and AI collaboration rules; surfaces target persistent Tabs from Task 6 where applicable. _(shipped)_
- Visualization expansion has an ER child spec and implementation slice with explicit `ui_find` / `ui_patch` integration for persistent `er_inspector` / `er_designer` objects. Plan B automated verification passed on 2026-04-29; manual real-database smoke confirmed by owner on 2026-05-06. ER first slice is fully complete. _(closed 2026-05-06)_
- Data source coverage expansion has at least one child spec or an explicit prioritization decision for the first wave, and every new kind is tied back to [DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md). _(Wave A 4/4 + Wave B 7/7 + Wave C 5/6 complete = 16 first-class kinds shipped; GaussDB pending; Wave D/E not started)_
- External data ingestion remains a registered phase-3 placeholder until Tasks 8 and 9 are stable; no child spec opened prematurely.
- OpenCode workdir & file artifact system (Task 11, out-of-roadmap) has a code-verified spec v2 and all 5 Parts (1-5b) completed and registered in `docs/exec-plans/index.md`; full lifecycle from migration through frontend tabs to housekeeping is closed. _(Parts 1-5b all shipped by 2026-05-07)_
- The next roadmap or child plans are registered in `docs/exec-plans/index.md` before this roadmap is moved to Completed.
