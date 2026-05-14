## Context

The Ingestion Library tab (`client/src/features/ingestion/ingestion-library-tab.tsx`) renders a `<Table>` listing ingestion jobs with columns: Status, Source URL, Target, Rows, Created. Each row supports double-click to open a per-job detail tab (`type: 'ingestion_job'`). The component is ~155 lines, uses the base shadcn `<Table>` primitives, and has no custom date formatting or tab deduplication.

Two other data-table surfaces exist in the app as reference implementations:
- `sql-result-table.tsx` (600 lines): sticky header, column sorting, pagination, search/filter, context menus, export toolbar. Represents the most polished data-table style.
- `payload-preview-table.tsx` (42 lines): read-only table for ingestion payload preview. Shared styling pattern with the library tab but with mono treatment for null values.

The `openTab` function in `stage-store.ts` (line 501) unconditionally pushes a new `StageTab` into the `tabs[]` array. Other callers (`openArtifactPreviewTab`, `openQueryEditor`, `openOrFocusFilePreviewTab`) wrap `openTab` with an existence check — they search for a matching tab first and call `focusTab()` if found. `openJobTab` in `ingestion-library-tab.tsx` (line 57) does not perform this check.

## Design Inputs (client/DESIGN.md)

The following constraints from `client/DESIGN.md` apply to this change:

| Constraint | Source | Current State | Target State |
|---|---|---|---|
| `table.headerBg: "bg.subtle"` | components.table | Already `bg-bg-subtle` | Keep |
| `table.rowHover: "interaction.hover"` | components.table | Already `hover:bg-interaction-hover` | Keep |
| `table.rowSelected: "interaction.selected"` | components.table | Missing | Add single-click selection |
| "mono treatment for numeric or technical content" | components.table | Missing — Rows/Created use proportional font | Apply `font-mono` to Rows, Created, and job ID |
| `density.compact`: "table" | density | Already `text-ui-xs`, `h-8` headers, `py-1.5` cells | Keep |
| Sticky header | (convention from sql-result-table) | Missing | Add `sticky top-0` to header |
| `motion.fast: "120ms"` | motion | No transition on row state | Add transition-colors for selection |
| `cobalt` accent for primary/selection | primitives.cobalt | Missing | Selected row uses `interaction.selected` (cobalt.50 / rgba cobalt) |

## Goals / Non-Goals

**Goals:**
- Fix date formatting to `yyyy-MM-dd HH:mm:ss` independent of browser locale.
- Prevent duplicate `ingestion_job` tabs by implementing "open or focus" in `openJobTab`.
- Elevate table styling to match the app's structured data-table conventions.
- Ensure all changes comply with `client/DESIGN.md` table token contract.

**Non-Goals:**
- Adding sort, filter, pagination, or export to the ingestion library table (those belong in a separate feature change).
- Changing the backend API or the `IngestionJobView` type shape.
- Adding keyboard navigation or a11y audit (separate concern).
- Modifying `openTab` in `stage-store.ts` — the fix belongs in the caller, matching the existing pattern.

## Decisions

### Decision 1 — Date formatting: lightweight formatter over `date-fns` / `Intl.DateTimeFormat`

**Choice**: Write a ~5-line helper `formatDateTime(ms: number): string` using manual `Date` getter methods + zero-padding. Output: `yyyy-MM-dd HH:mm:ss`.

**Why not `date-fns`**: The codebase has no existing `date-fns` dependency. Adding a library for one format call is overkill.

**Why not `Intl.DateTimeFormat`**: While `Intl.DateTimeFormat('sv-SE', {...})` can produce `yyyy-MM-dd HH:mm:ss`, it's obscure and locale-hacky. A manual formatter is explicit, testable, and ~5 lines.

**Why not a shared utility module**: The formatter is specific to the ingestion library's display needs. If a second caller emerges, extract then. YAGNI.

### Decision 2 — Tab deduplication: "open or focus" in the caller, not in `openTab`

**Choice**: Modify `openJobTab` to search existing tabs for `tabId: ingestion_job_<id>` and call `focusTab()` if found, falling through to `openTab()` only for new jobs. Follow the exact pattern from `openArtifactPreviewTab` (stage-store.ts lines 593-609).

**Why not modify `openTab` to deduplicate by `tabId`**: `openTab` is the low-level "push a tab" primitive. Changing its contract would affect all 7+ callers. The existing convention is caller-side dedup — `openArtifactPreviewTab`, `openQueryEditor`, and `openOrFocusFilePreviewTab` all check before calling `openTab`. `openJobTab` is the outlier; fixing it at the call site matches the established pattern with minimal blast radius.

**Why double-click rather than single-click to open**: The table will gain single-click row selection (visual highlight). Double-click is the established "open" gesture in the data-table surfaces. This avoids accidental tab storms from single-click navigation.

### Decision 3 — Table styling: incremental elevation, not full `sql-result-table` parity

**Choice**: Apply targeted improvements to the existing `<Table>`-based implementation:
1. Sticky header (`sticky top-0 z-10`) on `TableHeader`
2. Mono font on Created column (technical timestamp data)
3. Single-click row selection with `interaction.selected` background
4. `transition-colors` on data rows for smooth hover/selection

**Why not replace with `sql-result-table`**: That component carries 600 lines of sort/filter/paginate/export logic. The ingestion library is a simple job list — the complexity budget doesn't warrant that surface area. The targeted changes close the visual gap without the maintenance burden.

**Why not use the unused `DataGrid` component**: `DataGrid` is a lowest-common-denominator wrapper with no selection, no sticky header, and no ingestion-specific rendering (status badges, URL truncation). Using it would be a regression.

## Design Token Compliance (Post-Change)

After this change, the ingestion library table will use:

| Element | Token | Source |
|---|---|---|
| Header background | `bg-bg-subtle` | `table.headerBg` |
| Header text | `text-text-muted` | `table` convention |
| Row hover | `hover:bg-interaction-hover` | `table.rowHover` |
| Row selected | `bg-interaction-selected` | `table.rowSelected` |
| Row transition | `transition-colors duration-120` | `motion.fast` |
| Body text | `text-ui-xs` | `density.compact` |
| Technical columns | `font-mono text-ui-xs` | `components.table` mono rule |
| Status badges | `status.*` tokens | `status` semantics |
| Sticky header | `sticky top-0` | sql-result-table convention |

## Risks / Trade-offs

- **Single-click selection + double-click open**: Users accustomed to single-click-to-open may find the extra click surprising. Mitigation: this is the standard OS file-manager pattern; the selection highlight provides clear affordance.
- **Sticky header z-index**: Must coordinate with any parent scroll containers. The current DOM has a single `overflow-auto` wrapper — no z-index conflict expected.

## Migration Plan

- No data migration needed — pure frontend visual change.
- No API contract change — `createdAt` remains Unix-ms on the wire.
- No persistence change — stage tabs are already persisted server-side; the "open or focus" change only affects runtime behavior.

## Open Questions

None.
