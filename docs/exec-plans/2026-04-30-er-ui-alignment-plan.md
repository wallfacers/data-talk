# ER UI Alignment

Date: 2026-04-30
Status: Completed

## Scope

Fix two shipped frontend inconsistencies in the ER designer surface:

1. The designer dialect picker in the canvas toolbar still uses a native `<select>` instead of the project's shared Select control.
2. The ER designer tab icon does not match the icon used by the global creation entry points.

## Design Inputs

- `client/DESIGN.md`
- Apply the compact toolbar/tabs density already used by Stage.
- Reuse shared UI primitives instead of introducing one-off native controls where a project component already exists.
- Keep iconography semantically consistent for the same object across entry points and opened tabs.

## Tasks

- [x] Add failing tests that capture the toolbar control mismatch and tab icon mismatch.
- [x] Replace the ER designer dialect native select with the shared Select primitive, preserving existing behavior.
- [x] Align ER designer tab icon rendering with the creation-entry icon.
- [x] Run targeted vitest coverage and `cd client && npx tsc --noEmit`.
- [x] Mark this plan complete and move it to the completed index section.

## Outcome

- `ErToolbar` designer dialect picker now uses the shared project `Select` control, matching Stage toolbar interaction and styling.
- `er_designer` now uses `NetworkIcon` consistently in both the Stage tab bar and the tab type registry, matching the global creation entry points.
- Verification passed:
  - `cd client && npm test -- --run src/features/stage/components/er-canvas/__tests__/ErToolbar.test.tsx src/features/stage/components/stage-tab-bar.test.tsx src/features/stage/registry/__tests__/tab-type-registry.test.ts`
  - `cd client && npx tsc --noEmit`
