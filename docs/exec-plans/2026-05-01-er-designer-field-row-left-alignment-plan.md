# ER Designer Field Row Left Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the ER designer table-header pencil button and adjust field-row layout so the left side feels tighter while the editable field area gets a bit more usable width.

**Architecture:** This is a narrow frontend-only follow-up on the ER designer node component. The change stays inside `ErTableNode`, reusing the current inline editing behavior and token system while only tuning header affordance and row spacing/layout.

**Tech Stack:** React 19, TypeScript, React Flow, shadcn/ui, Vitest, Testing Library.

---

## Design Inputs

Frontend constraints from [client/DESIGN.md](../../client/DESIGN.md):

- Stay inside the existing Stage/ER visual system; no parallel UI pattern or local style fork.
- Reuse current semantic tokens and shared primitives; this is a spacing correction, not a redesign.
- Preserve accessible names and focus behavior for the remaining inline inputs.
- Keep the current `bg.subtle` / `bg.canvas` surface contract intact.

## Spec Mapping

- Existing feature design: [ER Graph Browsing & Designing — `er_inspector` + `er_designer`](../product-specs/2026-04-29-er-graph-browsing-design.md)
- Existing follow-up plans:
  - [ER Designer Bind Target And Canvas Polish](./2026-05-01-er-designer-bind-target-and-canvas-polish-plan.md)
  - [ER Designer Header Affordance And Column Spacing](./2026-05-01-er-designer-header-affordance-and-column-spacing-plan.md)

## Files

- Modify: `client/src/features/stage/components/er-canvas/ErTableNode.tsx`
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx`
- Modify: `docs/exec-plans/index.md`

## Task 1: Lock The Follow-Up In Tests

**Files:**
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx`

- [x] **Step 1.1: Add a failing test for removing the header pencil button**
  - Assert the designer node no longer renders a rename pencil button in the header.

- [x] **Step 1.2: Add a failing test for tighter left alignment and more field width**
  - Assert the designer row shifts slightly left, trims excess gap near the left connection handle, and keeps a slightly wider field area.

## Task 2: Implement The Minimal Layout Adjustment

**Files:**
- Modify: `client/src/features/stage/components/er-canvas/ErTableNode.tsx`

- [x] **Step 2.1: Remove the header pencil button**
  - Keep inline table-name editing, but drop the dedicated header rename button affordance.

- [x] **Step 2.2: Tighten the left side of field rows**
  - Reduce the excess left-side spacing between the left connection handle / key-fk icon gutter and the field content.

- [x] **Step 2.3: Increase usable field width**
  - Rebalance the row so the editable field area gains a bit more horizontal room without breaking the type control or delete affordance.

## Task 3: Verification And Housekeeping

**Files:**
- Modify: `docs/exec-plans/index.md`

- [x] **Step 3.1: Run targeted frontend verification**
  - Ran `cd client && npm test -- --run src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx` and all 2 files / 26 tests passed.

- [x] **Step 3.2: Run frontend type-check**
  - Ran `cd client && npx tsc --noEmit` with zero type errors.

- [x] **Step 3.3: Complete plan housekeeping**
  - Marked this plan complete, moved it from Active to Completed in `docs/exec-plans/index.md`, and summarized the shipped follow-up.
