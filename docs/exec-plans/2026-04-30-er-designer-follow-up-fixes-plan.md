# ER Designer Follow-up Fixes

## Status

- Created: 2026-04-30
- State: completed
- Scope: narrow follow-up for post-ship ER Designer regressions and test drift
- Related plan: [2026-04-29-er-designer-plan.md](./2026-04-29-er-designer-plan.md)

## Goal

Close the shipped behavior gaps called out during follow-up review without changing the ER Designer visual contract or widening day-1 scope.

## Non-Goals

- Add concurrency/version protection to backend `sync()` flow
- Make designer type options dialect-aware
- Rework designer auto-layout behavior globally

## Design Inputs

Source: [client/DESIGN.md](../../client/DESIGN.md)

Applicable constraints:

- Preserve the existing ER canvas visual language and token usage.
- Keep FK vs virtual relation semantics aligned with current edge rendering (`fk` solid, `virtual` dashed/warn).
- Reuse existing compact table/editor affordances; this plan is behavior-only, not a UI redesign.

## Compatibility Gate

Source: [docs/DATA_SOURCE_TYPE_COMPATIBILITY.md](../DATA_SOURCE_TYPE_COMPATIBILITY.md)

- Data-source type expansion: N/A. This follow-up does not add/change supported dialects or type compatibility rules.
- JDBC/schema discovery behavior: N/A. `sync_from_db` still consumes the same backend payload; only local view-position fallback changes.
- DDL/type option coverage: N/A in this patch. Dialect-aware column type lists remain deferred.

## Tasks

- [x] Add failing frontend tests for designer relation kind mapping and designer column callback ids.
- [x] Add failing frontend test for `sync_from_db` assigning non-overlapping fallback positions to newly added tables.
- [x] Fix `ErCanvas.designerToGraph` to map `constraintMethod` into `fk` vs `virtual`.
- [x] Fix `ErTableNode` designer test fixtures so callback assertions match production id-based behavior.
- [x] Fix `ErDesignerAdapter.sync_from_db` to preserve existing positions and assign fallback positions for new tables missing coordinates.
- [x] Sync the workspace `open` payload documentation in backend `AGENTS.md` with the shipped schema aliases/fields.
- [x] Run frontend verification (`npx tsc --noEmit` plus targeted vitest).
- [x] Housekeeping: mark this plan complete and move its index entry to Completed.

## Execution Notes

- Red step: targeted vitest initially failed on two expected gaps:
  - `ErCanvas` still treated `comment_ref` as `fk`
  - `sync_from_db` left new table ids without coordinates
- Green step: updated `designerToGraph`, corrected the designer test fixture ids, and added fallback position assignment during sync merge.
- Doc sync: clarified `workspace.open` query-editor payload fields in backend `AGENTS.md` so AI-facing instructions match `UiExecAction` / `WorkspaceAdapter`.
- Verification completed:
  - `cd client && npm test -- --run src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx src/features/stage/adapters/__tests__/ErDesignerAdapter.test.ts`
  - `cd client && npx tsc --noEmit`

## Notes

- `M-2` stays deferred because current sync entry is user-triggered and no automated concurrent caller is introduced here.
- `M-3` stays deferred because day-1 designer type options remain intentionally narrow.
