# ER Entry Enablement Plan

## Status

- Created: 2026-04-30
- State: completed
- Scope: enable shipped ER Diagram Designer from global workbench entry points
- Related plans:
  - [2026-04-29-er-inspector-plan.md](./2026-04-29-er-inspector-plan.md)
  - [2026-04-29-er-designer-plan.md](./2026-04-29-er-designer-plan.md)
  - [2026-04-30-er-title-unification-plan.md](./2026-04-30-er-title-unification-plan.md)

## Goal

Expose the already-implemented ER Diagram Designer through the empty workbench card and the tab-bar `+` menu, instead of leaving it in a front-end pending state.

## Design Inputs

Source: [client/DESIGN.md](../../client/DESIGN.md)

Applicable constraints:

- Preserve existing stage/workbench layout and token usage.
- This is an interaction wiring change, not a visual redesign.
- Keep the global launcher precise: only expose tools that can be opened without extra hidden state.

## Decision

- Enable `ER Diagram Designer` in:
  - empty workbench action list
  - tab-bar `+` menu
- Do **not** expose `ER Diagram Viewer` in the same global launcher for now.

Reason:

- `open_er_designer` can open a valid blank draft with no extra parameters.
- `open_er_inspector` requires `connectionId + tables`, so a global empty-state button would either fail or need an additional chooser flow that does not exist yet.

## Tasks

- [x] Add failing frontend tests for ER Diagram Designer entry enablement.
- [x] Wire empty workbench ER action to `open_er_designer`.
- [x] Restore a clickable ER Diagram Designer item in the tab-bar `+` menu.
- [x] Align launcher labels with the designer concept where needed.
- [x] Update AI/runtime wording only where product-facing terminology would otherwise diverge.
- [x] Run frontend verification (`npx tsc --noEmit` plus targeted vitest).
- [x] Housekeeping: mark plan complete and move index entry to Completed.

## Execution Notes

- Empty workbench `ER 图设计器 / ER Diagram Designer` is now enabled and opens a blank `er_designer` tab through the existing `WorkspaceAdapter.exec('open_er_designer')` flow.
- Tab-bar `+` menu now exposes the same designer entry as a real action, not a pending placeholder.
- `ER Diagram Viewer / er_inspector` was intentionally not added to the same global launcher in this patch:
  - the protocol requires `connectionId + tables`
  - there is still no valid blank-open flow or chooser in that surface
  - exposing it there would create a misleading dead-end entry
- Verification completed:
  - `cd client && npm test -- --run src/features/stage/components/stage-workbench-empty-state.test.tsx src/features/stage/components/stage-tab-bar-add-button.test.tsx src/features/stage/components/stage-window.test.tsx`
  - `cd client && npx tsc --noEmit`
