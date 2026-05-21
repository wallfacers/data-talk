# SQL Editor Selection Run And Result Scroll Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make SQL Run execute exact selected text when present, and isolate result table scroll offsets per result tab.

**Architecture:** Monaco selection changes populate the existing SQL workbench store selection field. The SQL workbench derives selected text and passes it as an optional override to the shared execution action. Result scroll offsets are owned by the workbench tab and keyed by `resultId`, then passed down as controlled state to the result table.

**Tech Stack:** React 19, Monaco via `@monaco-editor/react`, Zustand, Vitest, Testing Library, TypeScript.

---

## Design Inputs

- Source spec: `docs/product-specs/2026-04-25-sql-editor-selection-run-result-scroll-design.md`.
- `client/DESIGN.md` constraints applied: keep SQL workbench in the existing Stage/instrument-lane style; preserve dense table/editor layout; do not add new visual language; keep keyboard execution behavior; treat table scroll as stable interaction state.

## Files

- Modify: `client/src/features/stage/components/sql-monaco-editor.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Modify: `client/src/features/stage/components/sql-result-panel.tsx`
- Modify: `client/src/features/stage/components/sql-result-table.tsx`
- Modify: `client/src/features/stage/utils/query-editor-actions.ts`
- Test: `client/src/features/stage/components/sql-workbench-tab.test.tsx`
- Test: `client/src/features/stage/utils/query-editor-actions.test.ts`

## Task 1: Selection-Aware Execution

- [x] **Step 1: Write the failing component test**

Add a test in `client/src/features/stage/components/sql-workbench-tab.test.tsx` that renders a tab with multiple SQL statements, simulates a Monaco selection whose exact text is `select 2`, clicks Run, and expects `executeSqlMock` to receive `sql: 'select 2 LIMIT 100'` or, with limit disabled, `sql: 'select 2'`.

- [x] **Step 2: Run the failing component test**

Run: `cd client && npx vitest run src/features/stage/components/sql-workbench-tab.test.tsx --runInBand`

Expected: the new test fails because selection changes are not tracked and Run still submits the full editor text.

- [x] **Step 3: Write the failing action test**

Add a test in `client/src/features/stage/utils/query-editor-actions.test.ts` that calls `runQueryEditorSql({ tabId, sessionId, limit: null, sqlOverride: 'select 2' })` and expects `executeSqlMock` to receive only `select 2`.

- [x] **Step 4: Run the failing action test**

Run: `cd client && npx vitest run src/features/stage/utils/query-editor-actions.test.ts --runInBand`

Expected: TypeScript or runtime failure because `sqlOverride` is not supported yet.

- [x] **Step 5: Implement selection reporting in Monaco**

In `client/src/features/stage/components/sql-monaco-editor.tsx`, add an `onSelectionChange?: (selection: SqlWorkbenchSelection | null) => void` prop shape without importing the store type directly if a local prop type is clearer. Register `editor.onDidChangeCursorSelection` on mount. Convert empty Monaco selections to `null`; convert non-empty selections to `{ startLine, startColumn, endLine, endColumn }`.

- [x] **Step 6: Derive exact selected SQL in the workbench tab**

In `client/src/features/stage/components/sql-workbench-tab.tsx`, select `setSelection` from `useSqlWorkbenchStore`, pass it to `SqlMonacoEditor`, and add a helper that slices exact text from `tabState.sqlText` using the stored line/column range. If the selected text trims to empty, pass no override.

- [x] **Step 7: Add SQL override support to the shared action**

In `client/src/features/stage/utils/query-editor-actions.ts`, extend `runQueryEditorSql` params with `sqlOverride?: string | null`. Use `sqlOverride` when it is non-null and non-blank; otherwise use `tabState.sqlText`. Keep limit injection, request construction, success/error/risk state, and history based on the effective SQL.

- [x] **Step 8: Verify selection-aware tests pass**

Run:

```bash
cd client && npx vitest run src/features/stage/components/sql-workbench-tab.test.tsx src/features/stage/utils/query-editor-actions.test.ts --runInBand
```

Expected: both focused suites pass.

## Task 2: Per-Result Scroll State

- [x] **Step 1: Write the failing scroll-position test**

Add a test in `client/src/features/stage/components/sql-workbench-tab.test.tsx` that executes SQL returning two wide/tall result sets. Scroll result A to `{ top: 80, left: 120 }`, switch to result B and scroll it to `{ top: 30, left: 40 }`, then switch back to A and assert the visible `sql-result-table-scroll` element has A's original offsets. Switch to B and assert B's offsets are restored.

- [x] **Step 2: Run the failing scroll-position test**

Run: `cd client && npx vitest run src/features/stage/components/sql-workbench-tab.test.tsx --runInBand`

Expected: the new test fails because switching active results reuses the current DOM scroll offsets.

- [x] **Step 3: Make `SqlResultTable` controlled for scroll offsets**

In `client/src/features/stage/components/sql-result-table.tsx`, add props `scrollPosition?: { scrollTop: number; scrollLeft: number }` and `onScrollPositionChange?: (position) => void`. Attach a ref to the scroll container, restore offsets after `result.resultId` or `scrollPosition` changes, and report `scrollTop/scrollLeft` from `onScroll`.

- [x] **Step 4: Pass scroll props through `SqlResultPanel`**

In `client/src/features/stage/components/sql-result-panel.tsx`, add `activeScrollPosition` and `onActiveScrollPositionChange` props and pass them only to `SqlResultTable`. DML and error panels remain unchanged.

- [x] **Step 5: Store result scroll offsets in `SqlWorkbenchTab`**

In `client/src/features/stage/components/sql-workbench-tab.tsx`, add component-local state keyed by `resultId`. Before rendering the panel, select the active result's saved offset or `{ scrollTop: 0, scrollLeft: 0 }`. Update the map when the table reports scroll changes, and prune keys that are no longer present after results are replaced or closed.

- [x] **Step 6: Verify scroll tests pass**

Run: `cd client && npx vitest run src/features/stage/components/sql-workbench-tab.test.tsx --runInBand`

Expected: the new scroll test and existing workbench tests pass.

## Task 3: Final Verification And Housekeeping

- [x] **Step 1: Run TypeScript verification**

Run: `cd client && npx tsc --noEmit`

Expected: zero type errors.

- [x] **Step 2: Run diff whitespace check**

Run: `git diff --check`

Expected: no whitespace errors.

- [x] **Step 3: Update this plan after implementation**

Mark all completed checkboxes in this plan. Record any deviations in this section if implementation differs from the design.

- [x] **Step 4: Move the plan index entry to completed**

## Implementation Notes

- Implemented as designed: exact non-empty Monaco selection is submitted via `sqlOverride`; empty selection falls back to full editor content.
- Added keyboard command coverage after discovering Monaco's mount-time command registration can otherwise capture stale React state. `handleRun` now reads the current Zustand tab state before deriving selected SQL.
- The plan initially listed `--runInBand`, but the installed Vitest version rejects that option. Verification used `npx vitest run <files>` instead.
- No backend API or schema changes were required.

Move this plan from `docs/exec-plans/index.md` Active to Completed with a concise completion summary.

## Decision Log

- 2026-04-25: User confirmed exact selected text should run. Selection is not expanded to full touched lines.
- 2026-04-25: Scroll offsets are scoped by result id, not by result index or result title, because backend result ids are already the active tab identity.
