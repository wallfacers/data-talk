# ER Title Unification Plan

## Status

- Created: 2026-04-30
- State: completed
- Scope: unify ER tab naming across viewer/designer titles and labels
- Related plans:
  - [2026-04-29-er-inspector-plan.md](./2026-04-29-er-inspector-plan.md)
  - [2026-04-29-er-designer-plan.md](./2026-04-29-er-designer-plan.md)

## Goal

Use one consistent ER naming scheme for user-visible tab labels and default tab titles so ER browsing and ER design no longer mix `ER 浏览`, `ER 设计器`, `ER Inspector`, and `ER Designer`.

## Design Inputs

Source: [client/DESIGN.md](../../client/DESIGN.md)

Applicable constraints:

- Preserve the existing stage visual language and token system.
- This is a naming-only UI change; no layout, component, or motion changes.
- Keep related surfaces precise and product-facing rather than implementation-facing.

## Approach

- Chinese:
  - `ER 图浏览器`
  - `ER 图设计器`
- English:
  - `ER Diagram Viewer`
  - `ER Diagram Designer`

This keeps a shared `ER 图 / ER Diagram` prefix while preserving the distinction between read-only viewing and schema drafting.

## Tasks

- [x] Add/update failing frontend tests for ER default titles and labels.
- [x] Update ER Inspector / ER Designer default titles and i18n labels.
- [x] Update nearby test fixtures that hardcode the old names.
- [x] Run frontend verification (`npx tsc --noEmit` plus targeted vitest).
- [x] Housekeeping: mark plan complete and move index entry to Completed.

## Execution Notes

- Default inspector tab titles now use `ER Diagram Viewer: ...`.
- Default designer tab titles now use `ER Diagram Designer (...)`.
- User-facing i18n labels now use:
  - zh: `ER 图浏览器` / `ER 图设计器`
  - en: `ER Diagram Viewer` / `ER Diagram Designer`
- Verification completed:
  - `cd client && npm test -- --run src/features/stage/adapters/__tests__/WorkspaceAdapter.er.test.ts src/features/stage/components/stage-tab-content.test.tsx src/features/stage/components/stage-ui-object-registry.test.tsx`
  - `cd client && npx tsc --noEmit`
