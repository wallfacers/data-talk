# SQL Result Export Design

## 1. Purpose

Stage SQL result sets need the same practical export affordances already available for AI Markdown tables. The first slice adds result-set-level export actions to Query Editor results without changing backend execution, pagination, or result persistence.

## 2. Current State

- `SqlResultTable` already renders bounded client-side pages of 100 rows.
- Query execution already injects user-selected `LIMIT 10 / 100 / 1000` for `select` / `with` statements when no SQL `LIMIT` exists.
- Backend `SqlExecuteService` still caps returned rows by `datatalk.sql.max-rows` and marks `truncated`.
- AI Markdown tables already have table serialization patterns for CSV / TSV / Markdown / JSON and CSV download.

## 3. Design Inputs

This feature follows [client/DESIGN.md](../../client/DESIGN.md):

- Stage uses `bg.subtle` for chrome and `bg.canvas` for the main result surface.
- Table controls must stay compact, predictable, and aligned with the existing SQL result footer.
- Export actions are result-level controls, not row-level controls.
- New controls must have accessible names and keyboard access.
- The UI must use existing semantic tokens and shadcn/ui primitives; no raw feature-local colors.
- No virtual scrolling is introduced.

## 4. Scope

First slice:

- Copy CSV.
- Copy JSON.
- Download CSV.
- Export scope selector:
  - current page
  - all returned rows in the current bounded result
- Works only for `result_set` items.
- Uses rows already present in `SqlExecuteResultItem.rows`; no backend export endpoint.

Out of scope:

- Excel export.
- TSV / Markdown export from SQL results.
- Server-side streaming export.
- Exporting rows beyond the already returned bounded result.
- Export for DML summary and error result tabs.

## 5. Data Semantics

CSV:

- Header row is `result.columns`.
- Cell values use display-safe scalar conversion.
- `null` and `undefined` export as `NULL`.
- Embedded comma, quote, CR, or LF is RFC4180-escaped.
- Downloaded CSV includes UTF-8 BOM for spreadsheet compatibility.

JSON:

- Output is an array of objects.
- Object keys come from column labels.
- Empty column labels become `column_N`.
- Duplicate column labels are suffixed as `name_2`, `name_3`, and so on.
- Values preserve the raw normalized `SqlExecuteResultItem.rows` values.

Scope:

- Current page exports only rows visible in the current table page.
- Returned result exports all `result.rows` currently held by the client.
- If `result.truncated` is true, the UI still labels this as returned rows, not full database rows.

## 6. UI Behavior

Controls live in the `SqlResultTable` footer, beside the existing summary and pagination controls.

Layout:

- Left: existing row summary.
- Right: export scope select, copy CSV, copy JSON, download CSV, then pagination controls when needed.
- On narrow widths, controls may wrap inside the footer; table height must remain stable.

Feedback:

- Copy actions use existing clipboard utility.
- Successful copy uses a short-lived copied state on the clicked button.
- Download action creates a CSV file named from result title plus UTC timestamp.
- Clipboard failure leaves the button available; no destructive state is introduced.

## 7. Testing

Required tests:

- Serializer unit tests for CSV escaping, JSON key normalization, UTF-8 BOM, filename generation, and scope row selection.
- Component tests for:
  - export controls rendering only for result sets
  - current-page CSV export
  - all-returned JSON export
  - CSV download content and filename
  - pagination still works after export controls are present

## 8. Acceptance Criteria

- Query Editor result sets expose copy CSV, copy JSON, and download CSV.
- Export scope defaults to current page.
- Exporting all returned rows never claims to export the full database result when `truncated` is true.
- Existing result pagination, context menu actions, cell detail dialog, and scroll restoration keep working.
- `cd client && npx vitest run src/features/stage/components/sql-result-display.test.tsx src/features/stage/utils/sql-result-export.test.ts`
- `cd client && npx tsc --noEmit`
