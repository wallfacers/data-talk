## Why

The Ingestion Library tab (`ingestion-library-tab.tsx`) has three issues that degrade the workbench experience:

1. **Date formatting is locale-dependent and verbose**: `new Date(job.createdAt).toLocaleString()` produces `2026/5/12 18:50:21` — inconsistent with the rest of the app and hard to scan in a dense table. The user expects `yyyy-MM-dd HH:mm:ss`.

2. **Double-clicking a job row always opens a new tab**: `openJobTab()` calls `openTab()` unconditionally without checking whether a tab for that job already exists. Repeated double-clicks on the same row produce duplicate `Job ing_6e66` tabs. Other tab-opening paths (`openArtifactPreviewTab`, `openQueryEditor`, `openOrFocusFilePreviewTab`) already follow an "open or focus" pattern — the ingestion job path is the outlier.

3. **Table styling diverges from the app's data-table conventions**: While the table uses some design tokens (`bg-bg-subtle`, `hover:bg-interaction-hover`), it lacks the polish of other data surfaces — no sticky header, no mono treatment for technical columns (job ID, row count), and no selected-row state. The user describes this as wanting the "结构集" (structured data table) style seen elsewhere in the app.

A design audit against `client/DESIGN.md` confirms all three gaps are measurable deviations from the contract.

## What Changes

- **Fix date formatting**: Replace `toLocaleString()` with `yyyy-MM-dd HH:mm:ss` using a lightweight formatter.
- **Fix duplicate tab**: Change `openJobTab` to follow the "open or focus" pattern — if a tab with `tabId: ingestion_job_<id>` already exists, focus it instead of creating a duplicate.
- **Elevate table styling**: Apply the structured data-table conventions used by `sql-result-table` and `payload-preview-table` — sticky header, mono font for technical columns, consistent cell sizing, and selected-row state on single click.
- **Design compliance**: Document which `client/DESIGN.md` constraints the ingestion table satisfies (row hover, header bg, density) and which it currently misses (mono treatment for technical content, selected state).

## Capabilities

### Modified Capabilities
- `ingestion-ui-e2e-testing`: Update spec to reflect the new "open or focus" behavior for job tabs and the date format change.

## Impact

- **Files changed**: `client/src/features/ingestion/ingestion-library-tab.tsx` (date format, openOrFocus, table styling)
- **No backend changes**: This is a pure frontend UI fix.
- **No DB / data-source type compatibility** changes — `docs/DATA_SOURCE_TYPE_COMPATIBILITY.md` is N/A.
- **Design Inputs**: `client/DESIGN.md` applies. Section references: `components.table` (headerBg, rowHover, rowSelected), `typography` (mono-sm for technical data), `density.compact` (table density), `motion.fast` (selection transitions).
- **Open BUGs overlapping**: None — all ingestion BUGs (BUG-0013 through BUG-0034) are `fixed`. Verified via `docs/bugs/index.md`.
- **Risks**:
  - The "open or focus" change means single-click (to select) and double-click (to open) need clear separation. Mitigation: single-click selects the row visually; double-click opens/focuses the tab. This matches file-manager UX conventions.
  - Date format change is a visual-only change; no API contract is altered (`createdAt` remains a Unix-ms number on the wire).
