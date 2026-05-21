# ER Designer Header Affordance And Column Spacing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the ER designer table-header pencil affordance trigger rename focus, and slightly loosen field-name/type spacing plus type width inside table nodes.

**Architecture:** This is a focused frontend-only follow-up on the shipped ER designer canvas. The change stays inside the existing `ErCanvas` and `ErTableNode` path, reusing the current inline rename flow and shared token classes instead of inventing a second rename UI or a local style fork.

**Tech Stack:** React 19, TypeScript, React Flow, shadcn/ui, Vitest, Testing Library.

---

## Design Inputs

Frontend constraints from [client/DESIGN.md](../../client/DESIGN.md):

- Chat and Workbench stay on one shared visual system; this patch must stay within the established Stage/ER shell.
- Updated controls should reuse semantic tokens and current primitives before adding component-local styling.
- Stage chrome remains on `bg.subtle` and work surfaces on `bg.canvas`; spacing tweaks should preserve the same surface/border contract.
- Accessible names, focus rings, and keyboard focus behavior must remain intact for rename interactions.
- This is a density/affordance correction, not a visual redesign.

## Spec Mapping

- Existing feature design: [ER Graph Browsing & Designing — `er_inspector` + `er_designer`](../product-specs/2026-04-29-er-graph-browsing-design.md)
- Existing protocol contract: [ER Tab Protocol](../references/er-tab-protocol.md)
- Existing follow-up plan: [ER Designer Bind Target And Canvas Polish](./2026-05-01-er-designer-bind-target-and-canvas-polish-plan.md)

## Files

- Modify: `client/src/features/stage/components/er-canvas/ErTableNode.tsx`
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx`
- Modify: `docs/exec-plans/index.md`

## Task 1: Lock The Follow-Up Behavior In Tests

**Files:**
- Modify: `client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx`

- [x] **Step 1.1: Add a failing test for the header pencil affordance**
  - Assert the designer header pencil is a focusable button and clicking it focuses/selects the inline table-name input.

- [x] **Step 1.2: Add a failing test for the loosened column layout**
  - Assert the designer field row uses a slightly wider gap and the type select uses a slightly wider fixed width.

## Task 2: Implement The Minimal UI Fix

**Files:**
- Modify: `client/src/features/stage/components/er-canvas/ErTableNode.tsx`

- [x] **Step 2.1: Make the pencil icon a real rename affordance**
  - Reuse the existing inline rename input and focus/select it when the header pencil is clicked.

- [x] **Step 2.2: Loosen field-name/type spacing**
  - Slightly increase the row gap between the name block and the type control while preserving handle hit targets and delete affordance behavior.

- [x] **Step 2.3: Widen the field type control a bit**
  - Increase the designer type control width modestly without changing the token system or introducing layout overflow.

## Task 3: Verification And Housekeeping

**Files:**
- Modify: `docs/exec-plans/index.md`
- Verify: `client/src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx`

- [x] **Step 3.1: Run targeted frontend verification**
  - Ran `cd client && npm test -- --run src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx` and all 2 files / 27 tests passed.

- [x] **Step 3.2: Run frontend type-check**
  - Ran `cd client && npx tsc --noEmit` with zero type errors.

- [x] **Step 3.3: Complete plan housekeeping**
  - Marked this plan complete, moved it from Active to Completed in `docs/exec-plans/index.md`, and summarized the shipped follow-up.
