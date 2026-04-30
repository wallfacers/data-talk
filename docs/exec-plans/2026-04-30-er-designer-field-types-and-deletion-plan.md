# ER Designer Field Types And Deletion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix ER Designer field type coverage, relation editing/deletion, keyboard deletion, and edge endpoint alignment.

**Architecture:** Keep the ER Designer as a frontend canvas behavior patch over the existing `ErDesignerPayload` JSON patch contract. Add dialect-aware type option metadata in the ER table node layer, relation mutation callbacks in `ErCanvas`, and compact interactive controls in `ErEdge` without changing table node or field row dimensions.

**Tech Stack:** React 19, TypeScript, `@xyflow/react`, shadcn/ui Select, Vitest, Testing Library.

---

## Status

- Created: 2026-04-30
- State: completed
- Scope: frontend ER Designer behavior and docs housekeeping

## Design Inputs

Source: [client/DESIGN.md](../../client/DESIGN.md)

Applicable constraints:

- Stage work surfaces use `bg.canvas`, `bg.subtle`, semantic border/text/accent tokens, and accessible controls.
- ER Designer is a dense workbench surface; controls must remain compact and avoid resizing table nodes or field rows.
- User-visible labels and accessible names must use i18n keys where new text is introduced.
- Keyboard access must cover canvas editing actions.

## Compatibility Gate

Source: [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)

- Data-source type expansion: N/A. This patch does not add database kinds; it only improves ER Designer UI for existing `mysql`, `postgresql`, `h2`, and `sqlite` dialect values.
- JDBC connection handling, schema discovery, SQL execution, SQL splitting, risk analysis, diagnostics: N/A. Generated DDL still flows through the existing ER Designer payload and query editor execution path.
- Frontend type options: Applicable. The field type dropdown must expose dialect-aware native candidates while preserving arbitrary string payload values already stored in drafts or synced from DB metadata.

## Tasks

- [x] Add failing Vitest coverage for dialect-aware type options, relation type mutation, explicit relation deletion, and edge endpoint alignment without node size changes.
- [x] Implement dialect-aware column type option lists in `ErTableNode` and pass the active designer dialect from `ErCanvas`.
- [x] Make designer relation labels interactive: change relation type through a compact dropdown and delete the selected relation with an icon button.
- [x] Add explicit keyboard deletion handling for selected designer nodes and edges, while preserving existing auto-layout and fit-view shortcuts.
- [x] Fix handle endpoint geometry so edge paths terminate on the table border while keeping the existing table/field size and width.
- [x] Update i18n keys and data-source compatibility notes.
- [x] Run targeted Vitest, `cd client && npx tsc --noEmit`, and required doc housekeeping.

## Execution Notes

- Added failing tests first for MySQL type option coverage, relation type patching, relation delete controls, `Delete` / `Backspace` keyboard deletion, and edge endpoint border alignment.
- Field row and table node sizing were intentionally left unchanged; endpoint alignment is done in edge path coordinate calculation by snapping left/right endpoints to the measured node border.
- Designer relation labels now expose a compact relation-type select and delete icon; inspector edges remain read-only labels.
- Deleting a table now also removes designer relations connected to that table to avoid stale draft relations.

## Verification

- `cd client && npm test -- --run src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx src/features/stage/components/er-canvas/__tests__/ErEdge.test.tsx src/features/stage/components/er-canvas/hooks/__tests__/useErKeyboard.test.ts`
- `cd client && npx tsc --noEmit`
