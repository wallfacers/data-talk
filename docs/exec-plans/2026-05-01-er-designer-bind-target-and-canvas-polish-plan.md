# ER Designer Bind Target And Canvas Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the ER designer bind-target dialog display regressions, add direct table-name editing, and tighten the ER node/edge layout polish the user requested.

**Architecture:** This is a frontend-only follow-up patch on top of the shipped `er_designer` slice. The fix keeps the current `ErDesignerAdapter` protocol intact, moves the bind-target UX correction into the `ErDesignerTab` wrapper, and extends the existing `ErCanvas`/`ErTableNode`/`ErEdge` path instead of introducing parallel widgets or a second editing surface.

**Tech Stack:** React 19, TypeScript, Zustand, shadcn/ui, Base UI Select/Dialog, Vitest, Testing Library.

---

## Design Inputs

Frontend constraints from [client/DESIGN.md](../../client/DESIGN.md):

- Chat and Workbench remain one visual system; this patch stays inside the existing Stage/ER shell rather than creating a special modal style.
- New and updated controls must reuse semantic tokens and shared primitives before adding component-local styling.
- Stage chrome stays on `bg.subtle` and work surfaces on `bg.canvas`; ER dialogs and controls must keep the same token contract.
- Motion confirms state only; this patch should not add decorative animation.
- Accessible names, keyboard focus, and focus-ring behavior are required for the bind-target dialog and inline table-name editing.

## Spec Mapping

- Existing feature design: [ER Graph Browsing & Designing — `er_inspector` + `er_designer`](../product-specs/2026-04-29-er-graph-browsing-design.md)
- Existing protocol contract: [ER Tab Protocol](../references/er-tab-protocol.md)
- Existing shipped follow-ups:
  - [ER Canvas Redesign](./2026-04-30-er-canvas-redesign-plan.md)
  - [ER Designer Field Types And Deletion](./2026-04-30-er-designer-field-types-and-deletion-plan.md)
  - [ER UI Alignment](./2026-04-30-er-ui-alignment-plan.md)

## Files

- Modify: `client/src/features/stage/components/er-designer-tab.tsx`
- Modify: `client/src/features/stage/components/er-canvas/ErCanvas.tsx`
- Modify: `client/src/features/stage/components/er-canvas/ErTableNode.tsx`
- Modify: `client/src/features/stage/components/er-canvas/ErEdge.tsx`
- Modify: `client/src/features/stage/components/__tests__/er-designer-tab.test.tsx`
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx`
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErEdge.test.tsx`
- Modify: `client/src/i18n/messages.ts`
- Modify: `docs/exec-plans/index.md`

## Task 1: Bind Target Dialog Display Fix

**Files:**
- Modify: `client/src/features/stage/components/er-designer-tab.tsx`
- Modify: `client/src/features/stage/components/__tests__/er-designer-tab.test.tsx`
- Modify: `client/src/i18n/messages.ts`

- [x] **Step 1.1: Lock the regression with a failing test**
  - Extend `er-designer-tab.test.tsx` so the bind-target dialog asserts human-readable labels for the selected connection and empty database/schema states.

- [x] **Step 1.2: Fix dialog value rendering**
  - Keep the current dialog/select structure, but render trigger labels from the selected connection/database/schema state rather than leaking raw internal select values like connection UUIDs or `__empty__`.

- [x] **Step 1.3: Preserve compatible filtering and empty-state copy**
  - Keep dialect-compatible connection filtering and ensure the dialog still exposes a clear empty state when no compatible targets exist.

## Task 2: Designer Table Name Editing

**Files:**
- Modify: `client/src/features/stage/components/er-canvas/ErCanvas.tsx`
- Modify: `client/src/features/stage/components/er-canvas/ErTableNode.tsx`
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx`

- [x] **Step 2.1: Add a failing test for table-name editing**
  - Extend `ErTableNode.test.tsx` so designer-mode tables assert an editable table-name control and verify the update callback receives the renamed value.

- [x] **Step 2.2: Wire table-name updates through the existing patch path**
  - Add an `onUpdateTable` callback from `ErCanvas` into `ErTableNode` and patch `/tables[id=<id>]/name` through the existing strict-baseVersion path.

- [x] **Step 2.3: Keep inspector mode read-only**
  - Do not change inspector behavior; only designer tabs expose the editable title control.

## Task 3: Edge And Column Layout Polish

**Files:**
- Modify: `client/src/features/stage/components/er-canvas/ErTableNode.tsx`
- Modify: `client/src/features/stage/components/er-canvas/ErEdge.tsx`
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx`
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErEdge.test.tsx`

- [x] **Step 3.1: Tighten the editable relation-label spacing**
  - Reduce the visual gap between the relation-type select and delete button without changing the underlying edge actions.

- [x] **Step 3.2: Nudge row content to the right**
  - Adjust the designer row spacing so field names and field types sit slightly farther from the left/right handles while preserving handle hit targets and delete affordance behavior.

- [x] **Step 3.3: Keep the existing token system**
  - Reuse current semantic colors, spacing, and border tokens; this is a layout polish patch, not a visual redesign.

## Task 4: Verification And Housekeeping

**Files:**
- Modify: `docs/exec-plans/index.md`
- Verify: `client/src/features/stage/components/__tests__/er-designer-tab.test.tsx`
- Verify: `client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx`
- Verify: `client/src/features/stage/components/er-canvas/__tests__/ErEdge.test.tsx`

- [x] **Step 4.1: Run targeted frontend verification**
  - Ran `cd client && npm test -- --run src/features/stage/components/__tests__/er-designer-tab.test.tsx src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx src/features/stage/components/er-canvas/__tests__/ErEdge.test.tsx src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx` and all 4 files / 39 tests passed.

- [x] **Step 4.2: Run frontend type-check**
  - Ran `cd client && npx tsc --noEmit` with zero type errors.

- [x] **Step 4.3: Complete plan housekeeping**
  - Marked this plan complete, moved it from Active to Completed in `docs/exec-plans/index.md`, and summarized the shipped outcomes.
