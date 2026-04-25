# SQL Result Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add SQL result-set export actions for copying CSV, copying JSON, and downloading CSV from the Query Editor result table.

**Architecture:** Keep the first slice frontend-only. Add a focused Stage SQL export utility that serializes `SqlExecuteResultItem` rows already returned to the client, then wire compact export controls into `SqlResultTable` without changing backend SQL execution or pagination.

**Tech Stack:** React 19, TypeScript, Zustand, shadcn/ui, lucide-react, Vitest, Testing Library.

---

## Design Inputs

Source spec: [SQL Result Export Design](../product-specs/2026-04-25-sql-result-export-design.md).

Applicable [client/DESIGN.md](../../client/DESIGN.md) constraints:

- Stage result controls are workbench chrome and must stay compact.
- Table actions use stable hierarchy, low-emphasis controls, accessible names, and semantic tokens.
- Export controls belong to the result surface, not to individual rows.
- No virtual scrolling, decorative motion, or raw primitive colors.

## Current Code Map

| File | Current role |
|------|--------------|
| `client/src/features/stage/components/sql-result-table.tsx` | Renders SQL result-set rows, page state, context menu, detail dialog, summary, and pagination |
| `client/src/features/stage/components/sql-result-panel.tsx` | Routes `result_set` to `SqlResultTable`, DML summary to `SqlDmlSummaryPanel`, errors to `SqlErrorResultPanel` |
| `client/src/features/chat/components/markdown/table-serializers.ts` | Existing AI Markdown table CSV / JSON / download patterns for reference |
| `client/src/i18n/messages.ts` | zh-CN / en-US UI copy |
| `client/src/features/stage/components/sql-result-display.test.tsx` | Existing result table component tests |

## Non-Goals

- No backend export endpoint.
- No server-side streaming.
- No Excel export.
- No TSV / Markdown export in the SQL result footer.
- No DML summary or error export.
- No virtual scrolling.

## Task 1: Stage SQL Export Utility

**Files:**
- Create: `client/src/features/stage/utils/sql-result-export.ts`
- Create: `client/src/features/stage/utils/sql-result-export.test.ts`

- [ ] **Step 1.1: Write serializer tests**

Create `client/src/features/stage/utils/sql-result-export.test.ts` with these cases:

```ts
import { describe, expect, it } from 'vitest'
import {
  buildSqlResultExportFilename,
  selectSqlResultExportRows,
  toSqlResultCsv,
  toSqlResultDownloadCsv,
  toSqlResultJson,
} from './sql-result-export'

describe('sql-result-export', () => {
  const columns = ['id', 'status', 'status', '']
  const rows = [
    [1, 'paid', 'ok', null],
    [2, 'needs,quote', 'line\nbreak', 'plain'],
  ]

  it('selects either the current page rows or all returned rows', () => {
    expect(selectSqlResultExportRows(rows, rows.slice(0, 1), 'page')).toEqual([rows[0]])
    expect(selectSqlResultExportRows(rows, rows.slice(0, 1), 'result')).toEqual(rows)
  })

  it('serializes SQL result rows to CSV with escaped cells', () => {
    expect(toSqlResultCsv(columns, rows)).toBe(
      'id,status,status,\r\n1,paid,ok,NULL\r\n2,"needs,quote","line\nbreak",plain',
    )
  })

  it('serializes SQL result rows to JSON with stable duplicate column keys', () => {
    expect(toSqlResultJson(columns, rows)).toBe(
      JSON.stringify([
        { id: 1, status: 'paid', status_2: 'ok', column_4: null },
        { id: 2, status: 'needs,quote', status_2: 'line\nbreak', column_4: 'plain' },
      ], null, 2),
    )
  })

  it('adds a UTF-8 BOM for downloaded CSV', () => {
    expect(toSqlResultDownloadCsv(columns, rows).startsWith('\uFEFF')).toBe(true)
  })

  it('builds a filesystem-safe UTC filename', () => {
    expect(buildSqlResultExportFilename('结果集 1 / users', new Date('2026-04-25T09:08:07Z'))).toBe(
      'sql-result-1-users-20260425-090807.csv',
    )
  })
})
```

- [ ] **Step 1.2: Run the new tests and confirm failure**

Run:

```bash
cd client && npx vitest run src/features/stage/utils/sql-result-export.test.ts
```

Expected: the test file fails because `sql-result-export.ts` does not exist.

- [ ] **Step 1.3: Implement the serializer utility**

Create `client/src/features/stage/utils/sql-result-export.ts`:

```ts
export type SqlResultExportScope = 'page' | 'result'

const UTF8_BOM = '\uFEFF'

function stringifyCsvValue(value: unknown): string {
  if (value == null) return 'NULL'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function escapeCsvCell(value: unknown): string {
  const text = stringifyCsvValue(value)
  if (!/[,"\r\n]/.test(text)) return text
  return `"${text.replace(/"/g, '""')}"`
}

function normalizeJsonKeys(columns: string[]): string[] {
  const seen = new Map<string, number>()
  return columns.map((column, index) => {
    const base = column.trim() || `column_${index + 1}`
    const count = (seen.get(base) ?? 0) + 1
    seen.set(base, count)
    return count === 1 ? base : `${base}_${count}`
  })
}

function padNumber(value: number): string {
  return value.toString().padStart(2, '0')
}

function safeFilenamePart(value: string): string {
  const ascii = value
    .normalize('NFKD')
    .replace(/[^\w\s-]/g, ' ')
    .trim()
    .replace(/\s+/g, '-')
    .toLowerCase()
  return ascii || 'result'
}

export function selectSqlResultExportRows(
  allRows: unknown[][],
  pageRows: unknown[][],
  scope: SqlResultExportScope,
): unknown[][] {
  return scope === 'page' ? pageRows : allRows
}

export function toSqlResultCsv(columns: string[], rows: unknown[][]): string {
  return [columns, ...rows]
    .map((row) => row.map(escapeCsvCell).join(','))
    .join('\r\n')
}

export function toSqlResultDownloadCsv(columns: string[], rows: unknown[][]): string {
  return `${UTF8_BOM}${toSqlResultCsv(columns, rows)}`
}

export function toSqlResultJson(columns: string[], rows: unknown[][]): string {
  const keys = normalizeJsonKeys(columns)
  return JSON.stringify(
    rows.map((row) => Object.fromEntries(keys.map((key, index) => [key, row[index] ?? null]))),
    null,
    2,
  )
}

export function buildSqlResultExportFilename(title: string, now = new Date()): string {
  const yyyy = now.getUTCFullYear()
  const mm = padNumber(now.getUTCMonth() + 1)
  const dd = padNumber(now.getUTCDate())
  const hh = padNumber(now.getUTCHours())
  const mi = padNumber(now.getUTCMinutes())
  const ss = padNumber(now.getUTCSeconds())
  return `sql-result-${safeFilenamePart(title)}-${yyyy}${mm}${dd}-${hh}${mi}${ss}.csv`
}
```

- [ ] **Step 1.4: Verify serializer tests pass**

Run:

```bash
cd client && npx vitest run src/features/stage/utils/sql-result-export.test.ts
```

Expected: all tests pass.

## Task 2: SQL Result Table Export UI

**Files:**
- Modify: `client/src/features/stage/components/sql-result-table.tsx`
- Modify: `client/src/features/stage/components/sql-result-display.test.tsx`
- Modify: `client/src/i18n/messages.ts`

- [ ] **Step 2.1: Add i18n keys**

Add zh-CN keys near existing `stage.queryEditor.result.*` messages:

```ts
'stage.queryEditor.result.exportScope': '导出范围',
'stage.queryEditor.result.exportPage': '当前页',
'stage.queryEditor.result.exportResult': '已返回结果',
'stage.queryEditor.result.copyCsv': '复制 CSV',
'stage.queryEditor.result.copyJson': '复制 JSON',
'stage.queryEditor.result.downloadCsv': '下载 CSV',
'stage.queryEditor.result.copyCsvAria': '复制当前 SQL 结果为 CSV',
'stage.queryEditor.result.copyJsonAria': '复制当前 SQL 结果为 JSON',
'stage.queryEditor.result.downloadCsvAria': '下载当前 SQL 结果为 CSV',
'stage.queryEditor.result.copied': '已复制',
```

Add en-US keys:

```ts
'stage.queryEditor.result.exportScope': 'Export scope',
'stage.queryEditor.result.exportPage': 'Current page',
'stage.queryEditor.result.exportResult': 'Returned result',
'stage.queryEditor.result.copyCsv': 'Copy CSV',
'stage.queryEditor.result.copyJson': 'Copy JSON',
'stage.queryEditor.result.downloadCsv': 'Download CSV',
'stage.queryEditor.result.copyCsvAria': 'Copy current SQL result as CSV',
'stage.queryEditor.result.copyJsonAria': 'Copy current SQL result as JSON',
'stage.queryEditor.result.downloadCsvAria': 'Download current SQL result as CSV',
'stage.queryEditor.result.copied': 'Copied',
```

- [ ] **Step 2.2: Add component tests for export controls**

Extend the `useI18n` mock in `client/src/features/stage/components/sql-result-display.test.tsx` with the new keys, then add tests:

```tsx
it('copies CSV for the current result page', async () => {
  const writeText = vi.fn().mockResolvedValue(undefined)
  Object.assign(navigator, { clipboard: { writeText } })

  render(
    <SqlResultTable
      result={{
        resultId: 'export-result',
        kind: 'result_set',
        title: 'orders',
        statementIndex: 0,
        statementText: 'select * from orders',
        columns: ['id', 'status'],
        rows: Array.from({ length: 105 }, (_, index) => [index + 1, index === 100 ? 'needs,quote' : 'paid']),
        rowCount: 105,
        executionMs: 8,
        truncated: false,
      }}
    />,
  )

  fireEvent.click(screen.getByRole('button', { name: '复制当前 SQL 结果为 CSV' }))

  await waitFor(() => expect(writeText).toHaveBeenCalled())
  expect(writeText.mock.calls[0][0]).toContain('id,status')
  expect(writeText.mock.calls[0][0]).toContain('100,paid')
  expect(writeText.mock.calls[0][0]).not.toContain('101,"needs,quote"')
})

it('copies JSON for all returned rows when export scope is changed', async () => {
  const writeText = vi.fn().mockResolvedValue(undefined)
  Object.assign(navigator, { clipboard: { writeText } })

  render(
    <SqlResultTable
      result={{
        resultId: 'json-export-result',
        kind: 'result_set',
        title: 'orders',
        statementIndex: 0,
        statementText: 'select * from orders',
        columns: ['id', 'status'],
        rows: [[1, 'paid'], [2, 'pending']],
        rowCount: 2,
        executionMs: 8,
        truncated: false,
      }}
    />,
  )

  fireEvent.click(screen.getByRole('combobox', { name: '导出范围' }))
  fireEvent.click(screen.getByRole('option', { name: '已返回结果' }))
  fireEvent.click(screen.getByRole('button', { name: '复制当前 SQL 结果为 JSON' }))

  await waitFor(() => expect(writeText).toHaveBeenCalledWith(JSON.stringify([
    { id: 1, status: 'paid' },
    { id: 2, status: 'pending' },
  ], null, 2)))
})
```

- [ ] **Step 2.3: Run component tests and confirm failure**

Run:

```bash
cd client && npx vitest run src/features/stage/components/sql-result-display.test.tsx
```

Expected: new export control tests fail because the UI controls are not present.

- [ ] **Step 2.4: Wire export controls into `SqlResultTable`**

In `client/src/features/stage/components/sql-result-table.tsx`:

- Import `Download` and `Copy` icons from `lucide-react`.
- Import `Select`, `SelectContent`, `SelectItem`, and `SelectTrigger`.
- Import the new export utility functions.
- Add local state:

```ts
const [exportScope, setExportScope] = useState<SqlResultExportScope>('page')
const [copiedAction, setCopiedAction] = useState<'csv' | 'json' | null>(null)
```

- Derive rows:

```ts
const exportRows = useMemo(
  () => selectSqlResultExportRows(result.rows, visibleRows, exportScope),
  [exportScope, result.rows, visibleRows],
)
```

- Add handlers:

```ts
const markCopied = useCallback((action: 'csv' | 'json') => {
  setCopiedAction(action)
  window.setTimeout(() => setCopiedAction((current) => current === action ? null : current), 1200)
}, [])

const copyCsv = useCallback(async () => {
  const ok = await copyToClipboard(toSqlResultCsv(result.columns, exportRows))
  if (ok) markCopied('csv')
}, [exportRows, markCopied, result.columns])

const copyJson = useCallback(async () => {
  const ok = await copyToClipboard(toSqlResultJson(result.columns, exportRows))
  if (ok) markCopied('json')
}, [exportRows, markCopied, result.columns])

const downloadCsv = useCallback(() => {
  const blob = new Blob([toSqlResultDownloadCsv(result.columns, exportRows)], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = buildSqlResultExportFilename(result.title)
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}, [exportRows, result.columns, result.title])
```

- Render controls in the footer before pagination:

```tsx
<div className="flex flex-wrap items-center justify-end gap-2">
  <Select value={exportScope} onValueChange={(value) => setExportScope(value as SqlResultExportScope)}>
    <SelectTrigger size="sm" aria-label={t('stage.queryEditor.result.exportScope')}>
      <span>{exportScope === 'page' ? t('stage.queryEditor.result.exportPage') : t('stage.queryEditor.result.exportResult')}</span>
    </SelectTrigger>
    <SelectContent>
      <SelectItem value="page">{t('stage.queryEditor.result.exportPage')}</SelectItem>
      <SelectItem value="result">{t('stage.queryEditor.result.exportResult')}</SelectItem>
    </SelectContent>
  </Select>
  <Button size="sm" variant="outline" aria-label={t('stage.queryEditor.result.copyCsvAria')} onClick={() => void copyCsv()}>
    <Copy className="size-3.5" />
    {copiedAction === 'csv' ? t('stage.queryEditor.result.copied') : t('stage.queryEditor.result.copyCsv')}
  </Button>
  <Button size="sm" variant="outline" aria-label={t('stage.queryEditor.result.copyJsonAria')} onClick={() => void copyJson()}>
    <Copy className="size-3.5" />
    {copiedAction === 'json' ? t('stage.queryEditor.result.copied') : t('stage.queryEditor.result.copyJson')}
  </Button>
  <Button size="sm" variant="outline" aria-label={t('stage.queryEditor.result.downloadCsvAria')} onClick={downloadCsv}>
    <Download className="size-3.5" />
    {t('stage.queryEditor.result.downloadCsv')}
  </Button>
</div>
```

Keep existing pagination controls after the export controls.

- [ ] **Step 2.5: Verify component tests pass**

Run:

```bash
cd client && npx vitest run src/features/stage/components/sql-result-display.test.tsx
```

Expected: existing and new SQL result display tests pass.

## Task 3: Full Frontend Verification And Documentation

**Files:**
- Modify: `docs/exec-plans/2026-04-25-sql-result-export-plan.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/design-docs/index.md`
- Modify: `docs/product-specs/index.md`

- [ ] **Step 3.1: Run focused Stage tests**

Run:

```bash
cd client && npx vitest run src/features/stage/utils/sql-result-export.test.ts src/features/stage/components/sql-result-display.test.tsx
```

Expected: both test files pass.

- [ ] **Step 3.2: Run frontend type check**

Run:

```bash
cd client && npx tsc --noEmit
```

Expected: zero TypeScript errors.

- [ ] **Step 3.3: Run whitespace check**

Run:

```bash
git diff --check -- client/src/features/stage/utils/sql-result-export.ts client/src/features/stage/utils/sql-result-export.test.ts client/src/features/stage/components/sql-result-table.tsx client/src/features/stage/components/sql-result-display.test.tsx client/src/i18n/messages.ts docs/exec-plans/2026-04-25-sql-result-export-plan.md docs/product-specs/2026-04-25-sql-result-export-design.md
```

Expected: no output.

- [ ] **Step 3.4: Complete document housekeeping after implementation**

When implementation and verification pass:

- Mark all checkboxes in this plan complete.
- Move this plan from Active to Completed in `docs/exec-plans/index.md`.
- Change `sql-result-export-design` from `approved` to `shipped` in `docs/design-docs/index.md`.
- Update `docs/exec-plans/2026-04-25-next-implementation-roadmap-plan.md` Task 4 with the shipped outcome.

## Execution Order

1. Task 1 serializer utility.
2. Task 2 UI and i18n wiring.
3. Task 3 verification and docs.

## Acceptance Criteria

- Result sets expose copy CSV, copy JSON, and download CSV controls.
- Default scope is current page.
- Users can switch scope to all returned rows.
- Export actions do not appear for DML summary or error panels because only `SqlResultTable` renders them.
- Existing row context menu, cell detail dialog, pagination, and scroll restoration still pass tests.
- `cd client && npx vitest run src/features/stage/utils/sql-result-export.test.ts src/features/stage/components/sql-result-display.test.tsx` passes.
- `cd client && npx tsc --noEmit` passes.
