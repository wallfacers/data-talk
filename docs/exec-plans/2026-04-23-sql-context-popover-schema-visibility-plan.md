# SQL Context Popover Schema Visibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the SQL editor session-context popover echo selected `Database` / `Schema` values correctly, and hide the `Schema` field for database kinds that do not use schemas.

**Architecture:** Keep the change entirely in the client stage workbench layer. `SqlWorkbenchTab` remains the composition/root that resolves connection metadata; `SqlContextChip` owns the popover rendering and schema-visibility rule (`connection.kind` primary, existing schema value as compatibility fallback).

**Tech Stack:** React 19, Vitest, Testing Library, Base UI Select, Zustand-backed stage/session state

---

## Design Inputs

- [client/DESIGN.md](/home/wushengzhou/workspace/github/data-talk/client/DESIGN.md)
- Constraints applied:
  - Reuse the existing token/component system and current compact toolbar density
  - Express state changes through structure and visibility, not new decorative UI
  - Preserve the current SQL workbench information hierarchy and interaction vocabulary

## File Map

- Modify: `client/src/features/stage/components/sql-context-chip.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`
- Modify: `client/src/features/stage/components/sql-context-chip.test.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.test.tsx` (only if data-flow coverage is needed)
- Modify: `docs/exec-plans/index.md`

### Task 1: Lock behavior with failing popover tests

**Files:**
- Modify: `client/src/features/stage/components/sql-context-chip.test.tsx`

- [x] **Step 1: Add a regression test for echoed database/schema values**

Write a test that opens `SqlContextChip`, verifies the editable controls show the current `Database` / `Schema`, and asserts the popover keeps showing the current context summary.

- [x] **Step 2: Add schema-visibility tests by connection kind**

Write tests covering:
- `postgres` / `postgresql` => `Schema` field is rendered
- `mysql` / `h2` / `sqlite` => `Schema` field is hidden
- non-schema kind + existing `schema` value => `Schema` still renders for compatibility

- [x] **Step 3: Run the focused test file and confirm failure before implementation**

Run: `cd client && npx vitest run src/features/stage/components/sql-context-chip.test.tsx`
Status: failed as expected before implementation because the `Database` / `Schema` controls did not echo selected values and `Schema` had no kind-aware visibility rule.

### Task 2: Implement connection-kind aware popover behavior

**Files:**
- Modify: `client/src/features/stage/components/sql-context-chip.tsx`
- Modify: `client/src/features/stage/components/sql-workbench-tab.tsx`

- [x] **Step 1: Extend connection options with `kind` and pass it from the workbench**

Ensure `SqlWorkbenchTab` passes each connection’s `kind` into `SqlContextChip` so the popover can make a local visibility decision without new API calls.

- [x] **Step 2: Replace database/schema free-text suggestions with explicit selects**

Render `Database` as a `Select` that includes the current value and known options, so the selected value is always clearly echoed in the trigger.

- [x] **Step 3: Add schema support rule with compatibility fallback**

Implement a helper in `SqlContextChip` that:
- treats `postgres` / `postgresql` as schema-aware
- treats `mysql` / `h2` / `sqlite` as schema-less by default
- still renders `Schema` when the current context or draft already has a schema value

- [x] **Step 4: Keep existing context-reset semantics intact**

Status note: no extra `sql-workbench-tab` assertions were needed beyond the existing workbench test file; the data-flow stayed covered by targeted regression plus the workbench suite.

Preserve the existing `pin current`, `apply override`, and `use session context` flows; only the field rendering and selection controls should change.

### Task 3: Verify and close out

**Files:**
- Modify: `docs/exec-plans/2026-04-23-sql-context-popover-schema-visibility-plan.md`
- Modify: `docs/exec-plans/index.md`

- [x] **Step 1: Run focused frontend verification**

Run:
- `cd client && npx vitest run src/features/stage/components/sql-context-chip.test.tsx src/features/stage/components/sql-workbench-tab.test.tsx`
- `cd client && npx tsc --noEmit`

Result:
- `vitest`: 28 tests passed
- `tsc --noEmit`: exit 0

- [x] **Step 2: Mark the executed checklist items complete with any notes**

Update this plan file to reflect the final implementation status and any scope adjustments.

- [x] **Step 3: Move the plan index entry from Active to Completed**

Record the completion date and a one-line summary in `docs/exec-plans/index.md`.
