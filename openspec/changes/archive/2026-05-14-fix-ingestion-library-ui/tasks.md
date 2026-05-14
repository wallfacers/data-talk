## 1. Preflight

- [x] 1.1 Read `client/DESIGN.md` sections: components.table, typography, density, motion — confirm design token targets
- [x] 1.2 Read `client/src/features/ingestion/ingestion-library-tab.tsx` end-to-end
- [x] 1.3 Read `client/src/stores/stage-store.ts` `openArtifactPreviewTab` (lines 593-609) and `focusTab` (lines 557-571) for the "open or focus" reference pattern
- [x] 1.4 Run `cd client && npx tsc --noEmit` to confirm clean baseline

## 2. Fix date format to yyyy-MM-dd HH:mm:ss

- [x] 2.1 Add a `formatDateTime(ms: number): string` helper at the top of `ingestion-library-tab.tsx` that returns `yyyy-MM-dd HH:mm:ss` using manual `Date` getters + zero-padding (no external dependency)
- [x] 2.2 Replace `{new Date(job.createdAt).toLocaleString()}` (line 140) with `{formatDateTime(job.createdAt)}`
- [x] 2.3 Run `cd client && npx tsc --noEmit` — zero errors

## 3. Fix duplicate tab: implement "open or focus" pattern

- [x] 3.1 Import `focusTab` from `useStageStore` in `ingestion-library-tab.tsx`
- [x] 3.2 Rewrite `openJobTab` callback: search existing tabs for `tabId: ingestion_job_<id>`, call `focusTab()` if found, call `openTab()` only for new jobs
- [x] 3.3 Run `cd client && npx tsc --noEmit` — zero errors
- [x] 3.4 ~~Add a unit test in `client/src/features/ingestion/` (vitest)~~ → covered by E2E test 5.3 (codebase has no vitest infrastructure; all tests are Playwright E2E)

## 4. Elevate table styling to structured data-table conventions

- [x] 4.1 Add `sticky top-0 z-10` to `TableHeader` for sticky header
- [x] 4.2 Add `font-mono` to Created column (line 139) for mono treatment of technical timestamp data
- [x] 4.3 Add single-click row selection: track `selectedJobId` state, apply `bg-interaction-selected` class to the selected row `<TableRow>`
- [x] 4.4 Add `transition-colors` to data rows (remove if already covered by base `TableRow`; verify the base component already provides `transition-colors`)
- [x] 4.5 Add `w-full` to the `<Table>` for full-width rendering
- [x] 4.6 Run `cd client && npx tsc --noEmit` — zero errors

## 5. E2E test update

- [x] 5.1 Read `client/tests/e2e/ingestion-library-tab-ui.spec.ts` to understand current assertions
- [x] 5.2 Update date format assertions to expect `yyyy-MM-dd HH:mm:ss` pattern (regex: `\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}`)
- [x] 5.3 Add a test: "double-clicking the same job row twice opens only one tab" — verify `openTabIds` contains exactly one `ingestion_job_<id>` entry
- [x] 5.4 Add a test: "single-click selects row, double-click opens/focuses tab"
- [x] 5.5 Run `cd client && npx tsc --noEmit` — zero errors

## 6. Ingestion i18n: fix double-brace template syntax

- [x] 6.1 Change all `{{...}}` to `{...}` in ingestion i18n entries (28 entries, Chinese + English) — `translateMessage` regex only matches single braces
- [x] 6.2 Restore accidentally deleted ingestion i18n keys (library search/select/page, status filters, mapping columns, phase labels, writing/completed phases, job loading)
- [x] 6.3 Run `cd client && npx tsc --noEmit` — zero errors

## 7. Mapping editor redesign

- [x] 7.1 Remove skip checkbox column, add horizontal chips row above table for column toggle
- [x] 7.2 Strip `$.` prefix from source field names in chips and table
- [x] 7.3 Replace nullable `✓`/`✗` text with Switch toggle component (size="sm")
- [x] 7.4 Convert native `<table>` to shadcn Table components with sticky header matching sql-result-table style
- [x] 7.5 Fix SelectTrigger height: use `!h-6 !py-0` to match Input `h-6` (data-attribute selector overrides plain class)
- [x] 7.6 Reduce table container max height from 400px to 280px to avoid default scrollbar
- [x] 7.7 Add `scrollContainer={false}` to Table to prevent nested scroll container causing scrollbar artifacts
- [x] 7.8 Wrap scroll container in border wrapper, use `data-result-scrollbar="header-offset"` for scrollbar offset, `overflow-hidden` on outer wrapper
- [x] 7.9 Run `cd client && npx tsc --noEmit` — zero errors

## 8. DDL nullable linkage

- [x] 8.1 Update `buildSuggestedDdl` in mapping-phase.tsx to include `NOT NULL` when `nullable` is false
- [x] 8.2 Verify DDL preview updates reactively when Switch toggles
- [x] 8.3 Run `cd client && npx tsc --noEmit` — zero errors

## 9. Section titles for mapping phase components

- [x] 9.1 Add title labels above each component in mapping-phase.tsx: "采集概要", "目标列名", "DDL 预览", "数据预览"
- [x] 9.2 Run `cd client && npx tsc --noEmit` — zero errors

## 10. Payload preview table fixes

- [x] 10.1 Table headers use stickyHeaderCellClass pattern matching sql-result-table (remove text-muted-foreground)
- [x] 10.2 Move row summary ("显示 X / Y 行") from above table to bottom toolbar row (`border-t px-3 py-2`) matching sql-result-table
- [x] 10.3 Add `scrollContainer={false}` and `data-result-scrollbar="header-offset"` for scrollbar consistency
- [x] 10.4 Run `cd client && npx tsc --noEmit` — zero errors

## 11. Source summary card redesign

- [x] 11.1 Change from horizontal truncating layout to vertical card (`rounded-md border bg-muted/50 p-3`)
- [x] 11.2 URL: full display with `break-all select-all`, no truncation
- [x] 11.3 Metadata row: flex-wrap with icons (Table2, HardDrive, Clock)
- [x] 11.4 Run `cd client && npx tsc --noEmit` — zero errors

## 12. Confirm button full-stack fix

- [x] 12.1 Backend: modify `IngestionController.confirm` to directly call `executor.createTable()` + `executor.ingestPayload()` after issuing token
- [x] 12.2 Frontend: fix `[, setConfirming]` destructuring to `const [confirming, setConfirming]`
- [x] 12.3 Frontend: add loading state with Loader2 spinner and disabled button
- [x] 12.4 Frontend: add query invalidation after confirm success
- [x] 12.5 Run `mvn compile -q` and `npx tsc --noEmit` — zero errors

## 13. Job tab delete button fix

- [x] 13.1 Add `deleting` state and try/catch error handling
- [x] 13.2 Add Loader2 spinner when deleting, disabled state
- [x] 13.3 Run `npx tsc --noEmit` — zero errors

## 14. Library tab enhancements

- [x] 14.1 Add pagination: `PAGE_SIZE=20`, `page` state, `limit/offset` API params, Previous/Next buttons
- [x] 14.2 Add batch delete: loading state, try/catch, auto-page-back on empty page
- [x] 14.3 Add `onDoubleClick` on TableRow to open job tab
- [x] 14.4 Add `data-result-scrollbar="header-offset"` for scrollbar header offset
- [x] 14.5 Run `npx tsc --noEmit` — zero errors

## 15. Tab icon unification

- [x] 15.1 Replace hardcoded switch-case in `stage-tab-bar.tsx` `getTabIcon()` with registry lookup via `getTabTypeDescriptor(type).icon`
- [x] 15.2 Remove unused icon imports (BarChart2Icon, DatabaseIcon, FileTextIcon, NetworkIcon)
- [x] 15.3 Run `npx tsc --noEmit` — zero errors

## 16. Refresh persistence fix

- [x] 16.1 Add `coordinator.ensureHydrated(tab.tabId)` call in IngestionJobTab to restore payload from server after refresh
- [x] 16.2 Change empty `jobId` handling from `return null` to loading state (awaiting hydration)
- [x] 16.3 Run `npx tsc --noEmit` — zero errors

## 17. Consolidated verification

- [x] 17.1 Run full client typecheck: `cd client && npx tsc --noEmit` — zero ingestion errors (remaining errors are pre-existing maintenance-page issues)
- [x] 17.2 Run backend compile: `cd server && mvn compile -q` — zero errors
