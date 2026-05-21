# ER Designer Local Edit Fix

Date: 2026-04-30
Status: Completed

## Scope

Fix the shipped ER designer regression where blank drafts show an empty canvas and toolbar-driven local edits do not apply.

## Design Inputs

- `client/DESIGN.md`
- Preserve the existing Stage / ER designer layout and compact toolbar density.
- Reuse existing empty-state presentation instead of inventing a new visual container.
- Keep strict versioning for protocol / AI patch flows, but make user-driven in-canvas edits supply the current version automatically.

## Tasks

- [x] Add failing tests that reproduce blank-designer UX and rejected local structural edits.
- [x] Inject the current designer payload version into user-driven structural patches before they reach the adapter.
- [x] Add an explicit empty-state message for a blank ER designer draft.
- [x] Run targeted vitest coverage and `cd client && npx tsc --noEmit`.
- [x] Mark this plan complete and move it to the completed index section.

## Outcome

- User-driven ER designer structural edits now attach the current payload version automatically before delegating to `ErDesignerAdapter.patch`, so add/edit/delete/connect flows are no longer rejected by strict local version checks.
- Blank ER designer drafts now show an explicit empty-state hint with an `Add table` CTA instead of a silent empty canvas.
- Verification passed:
  - `cd client && npm test -- --run src/features/stage/components/__tests__/er-designer-tab.test.tsx src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx`
  - `cd client && npx tsc --noEmit`
