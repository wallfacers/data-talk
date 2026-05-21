# ER Designer Performance And Visual Alignment

Date: 2026-04-30
Status: Completed

## Scope

Fix four shipped frontend issues in the ER designer:

1. Node dragging is visibly laggy.
2. Newly opened ER designer tabs still use a hard-coded English default title.
3. The canvas background should use a tighter dot-grid style instead of the current sparse background treatment.
4. The table node width and relation handles should be slightly larger, with clearer hover growth on the handle circles.

## Design Inputs

- `client/DESIGN.md`
- Preserve the current Stage shell and compact ER toolbar/tabs density.
- Reuse the existing token system and shared component idioms; no visual redesign outside the ER canvas surface itself.
- Prefer narrowly-scoped performance mitigation at the render hot path instead of broader store/architecture rewrites.
- Keep the same semantic iconography and naming already unified in prior ER follow-up fixes.

## Tasks

- [x] Add failing tests for localized ER designer default titles, ER canvas background configuration, node width / handle affordance styling, and the edge crossing performance guard.
- [x] Implement localized default ER designer titles in the workspace adapter.
- [x] Improve ER drag performance by reducing expensive edge-work during active dragging and memoizing custom node/edge components.
- [x] Update the ER canvas background to a compact dot grid and slightly widen node/handle visuals to match the requested reference direction.
- [x] Run targeted vitest coverage and `cd client && npx tsc --noEmit`.
- [x] Mark this plan complete and move it to the completed index section.

## Outcome

- `open_er_designer` now generates a localized default title from the active UI language instead of hard-coded English.
- ER drag performance is improved by skipping crossing-jump computation while any node is actively dragging, memoizing custom node/edge components, and enabling visible-element rendering in React Flow.
- The canvas now uses a tighter dot-grid background, table nodes are slightly wider, and relation handles are larger with a stronger hover-scale affordance.
- Verification passed:
  - `cd client && npm test -- --run src/features/stage/adapters/__tests__/WorkspaceAdapter.er.test.ts src/features/stage/components/er-canvas/__tests__/ErTableNode.test.tsx src/features/stage/components/er-canvas/__tests__/ErCanvas.test.tsx src/features/stage/components/er-canvas/__tests__/ErEdge.test.tsx`
  - `cd client && npx tsc --noEmit`
