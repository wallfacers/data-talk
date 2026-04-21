# Stage SQL Workbench Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the existing Stage SQL experience with a new Monaco-based SQL workbench, rebuild `/api/sql/execute` into a multi-statement multi-result contract, and remove the legacy non-SQL Stage pages.

**Architecture:** Rebuild Stage in two parallel streams. The backend stream converts the SQL execution contract from single-result to ordered `results[]`. The frontend stream replaces the old `QueryEditorTab` chain with a new SQL workbench component tree and a dedicated SQL workbench store, while keeping DataTalk theme tokens and current table primitives.

**Tech Stack:** React 19, Zustand, Monaco Editor, Vitest, Spring Boot 3.5, Java 21, JUnit 5, MockMvc

---

## File Structure

### Backend

- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SqlExecuteController.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- Modify or create DTOs under `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/`
- Modify: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SqlExecuteControllerIT.java`

### Frontend

- Modify: `client/src/services/api/sql.ts`
- Modify: `client/src/features/stage/components/stage-tab-content.tsx`
- Modify: `client/src/features/stage/components/stage-tool-row.tsx`
- Modify: `client/src/features/stage/components/stage-window.test.tsx`
- Remove/replace behavior in:
  - `client/src/features/stage/components/query-editor-tab.tsx`
  - `client/src/features/stage/components/query-editor-toolbar.tsx`
  - `client/src/features/stage/components/query-editor-result-panel.tsx`
  - `client/src/features/stage/hooks/use-sql-execute.ts`
  - `client/src/features/stage/components/stage-placeholder-tab.tsx`
- Create:
  - `client/src/features/stage/stores/sql-workbench-store.ts`
  - `client/src/features/stage/components/sql-workbench-tab.tsx`
  - `client/src/features/stage/components/sql-editor-header.tsx`
  - `client/src/features/stage/components/sql-monaco-editor.tsx`
  - `client/src/features/stage/components/sql-result-tabs.tsx`
  - `client/src/features/stage/components/sql-result-panel.tsx`
  - `client/src/features/stage/components/sql-result-table.tsx`
  - `client/src/features/stage/components/sql-dml-summary-panel.tsx`
  - `client/src/features/stage/components/sql-error-result-panel.tsx`
- Create tests:
  - `client/src/features/stage/stores/sql-workbench-store.test.ts`
  - `client/src/features/stage/components/sql-workbench-tab.test.tsx`
  - `client/src/features/stage/components/sql-result-tabs.test.tsx`

## Batch A: Backend Contract Rebuild

### Task 1: Rebuild `/api/sql/execute` response model

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/controller/SqlExecuteController.java`
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- Modify or create: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/SqlExecuteRequest.java`
- Create or modify response DTOs under `server/data-talk-adapter/src/main/java/com/datatalk/adapter/dto/`
- Test: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SqlExecuteControllerIT.java`

- [x] Write failing integration tests for multi-statement, `dml_summary`, and `error` result items.
- [x] Run only `SqlExecuteControllerIT` and verify the new assertions fail for the current single-result contract.
- [x] Rework `SqlExecuteService` to parse statements, execute in order, aggregate DML blocks, and return ordered `results[]`.
- [x] Rework `SqlExecuteController` and DTO mapping to expose the new response shape.
- [x] Re-run `SqlExecuteControllerIT` until green.

### Task 2: Preserve risk and context behavior in the new execution model

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/sql/SqlExecuteService.java`
- Test: `server/data-talk-adapter/src/test/java/com/datatalk/adapter/controller/SqlExecuteControllerIT.java`

- [x] Add failing tests that cover session-derived context and risk-blocked multi-statement execution.
- [x] Run the targeted IT tests and verify failure happens for the intended reasons.
- [x] Update the service so context resolution remains intact and high-risk user SQL blocks the whole batch before execution.
- [x] Re-run the targeted tests until green.

## Batch B: Frontend SQL Workbench Replacement

### Task 3: Replace the old SQL API client and introduce workbench state

**Files:**
- Modify: `client/src/services/api/sql.ts`
- Create: `client/src/features/stage/stores/sql-workbench-store.ts`
- Test: `client/src/features/stage/stores/sql-workbench-store.test.ts`

- [x] Write failing store tests for per-tab SQL text, result replacement, active result switching, and cleanup on tab close.
- [x] Run the new store test file and verify failure.
- [x] Replace the old single-result client types with the new multi-result response contract.
- [x] Implement `useSqlWorkbenchStore` with tab-scoped workbench state and lifecycle helpers.
- [x] Re-run the new store tests until green.

### Task 4: Replace `QueryEditorTab` with the new SQL workbench component tree

**Files:**
- Create: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Create: `client/src/features/stage/components/sql-editor-header.tsx`
- Create: `client/src/features/stage/components/sql-monaco-editor.tsx`
- Create: `client/src/features/stage/components/sql-result-tabs.tsx`
- Create: `client/src/features/stage/components/sql-result-panel.tsx`
- Create: `client/src/features/stage/components/sql-result-table.tsx`
- Create: `client/src/features/stage/components/sql-dml-summary-panel.tsx`
- Create: `client/src/features/stage/components/sql-error-result-panel.tsx`
- Test: `client/src/features/stage/components/sql-workbench-tab.test.tsx`
- Test: `client/src/features/stage/components/sql-result-tabs.test.tsx`

- [x] Write failing component tests for Monaco mount wrapper, result tab rendering, result switching, and `result_set / dml_summary / error` panels.
- [x] Run the targeted Vitest files and verify failure.
- [x] Implement the new SQL workbench component tree using DataTalk tokens and `open-db-studio` structure as the reference.
- [x] Keep result table rendering on current project table primitives, but restyle it to match the new workbench.
- [x] Re-run the targeted tests until green.

### Task 5: Remove legacy non-SQL Stage paths and wire Stage to SQL only

**Files:**
- Modify: `client/src/features/stage/components/stage-tab-content.tsx`
- Modify: `client/src/features/stage/components/stage-tool-row.tsx`
- Modify: `client/src/features/stage/components/stage-window.test.tsx`
- Modify or delete behavior in:
  - `client/src/features/stage/components/stage-placeholder-tab.tsx`
  - `client/src/features/stage/components/query-editor-tab.tsx`
  - `client/src/features/stage/components/query-editor-toolbar.tsx`
  - `client/src/features/stage/components/query-editor-result-panel.tsx`
  - `client/src/features/stage/hooks/use-sql-execute.ts`

- [x] Write failing Stage tests asserting that non-SQL legacy tabs are not routed/rendered and SQL tabs use the new workbench.
- [x] Run the targeted Stage tests and verify failure.
- [x] Rewire `StageTabContent` so Stage only renders the new SQL workbench path and filters old non-SQL tab types.
- [x] Simplify `StageToolRow` to SQL-only actions.
- [x] Re-run the targeted Stage tests until green.

## Batch C: Integration and Verification

### Task 6: Integrate frontend execution flow with rebuilt backend contract

**Files:**
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Modify: `client/src/services/api/sql.ts`
- Test: `client/src/features/stage/components/sql-workbench-tab.test.tsx`

- [x] Add failing tests for execute -> replace results -> select first result flow.
- [x] Run the targeted frontend tests and verify failure.
- [x] Connect the workbench execute action to the rebuilt `/api/sql/execute` contract.
- [x] Re-run the targeted frontend tests until green.

### Task 7: Consolidated verification and document housekeeping

**Files:**
- Modify: `docs/exec-plans/2026-04-21-stage-sql-workbench-rebuild-plan.md`
- Modify: `docs/exec-plans/index.md`
- Modify canonical docs only if implementation changes conventions materially

- [x] Run `cd client && npx vitest run src/features/stage/stores/sql-workbench-store.test.ts src/features/stage/components/sql-workbench-tab.test.tsx src/features/stage/components/sql-result-tabs.test.tsx src/features/stage/components/stage-window.test.tsx src/features/stage/components/stage-tool-row.test.tsx src/features/stage/components/query-editor-tab.test.tsx src/features/stage/hooks/use-sql-execute.test.ts`
- [x] Run `cd client && npx tsc --noEmit`
- [x] Run `cd server && mvn -q compile`
- [x] Run `cd server && mvn -q -pl data-talk-adapter -am -Dtest=SqlExecuteControllerIT -Dsurefire.failIfNoSpecifiedTests=false test`
- [x] Mark completed steps in this plan and move the plan entry from Active to Completed once all verification is green.

## Parallel Execution Notes

- Batch A and Batch B can run in parallel once the plan is written.
- Task 6 depends on Task 1-5 finishing their code shape.
- During Batch A and Batch B, skip per-edit compile/typecheck and do consolidated verification in Task 7, per repository rule for parallel plan execution.

## Status Notes

- Legacy `query-editor-*` files were retained as compatibility shims that delegate to the new workbench or return `null`, instead of being physically deleted in this pass.
- `SqlExecuteService` now provides transactional multi-statement execution with rollback on statement failure, but its custom SQL splitter is still not procedural-SQL-safe for dialect-specific bodies such as PostgreSQL dollar-quoted blocks.
- Result tables intentionally continue to use DataTalk table primitives; only the editor shell, Monaco integration, and result-tab interaction were migrated toward the `open-db-studio` structure in this phase.

## Self-Review

- Spec coverage: backend contract rebuild, frontend workbench rebuild, legacy Stage path removal, and verification are all covered.
- Placeholder scan: no TODO/TBD placeholders remain.
- Type consistency: frontend and backend both use the same `results[]` model with `result_set | dml_summary | error`.
