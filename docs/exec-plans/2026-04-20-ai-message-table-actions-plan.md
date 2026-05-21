# AI Message Table Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 为所有走统一 Markdown 渲染链路的表格增加表格级动作栏，并支持复制表格、CSV、TSV、Markdown、JSON 与下载 CSV。

**Architecture:** 保持现有 `markdown.tsx -> marked -> DOMPurify -> morphdom -> decorateTables()` 链路不变，在 `decorateTables()` 阶段把纯滚动壳升级为“表格卡片 + 动作栏”。所有格式导出都经由一个轻量 `TableModel` 与 serializer 层生成，避免把复制/下载逻辑散落在 DOM 事件处理里。交互和反馈延续现有代码块复制按钮的范式，覆盖 assistant、reasoning、tool 中所有复用 Markdown 的表格。

**Tech Stack:** React 19, marked, DOMPurify, morphdom, TypeScript, Vitest, Testing Library, browser Clipboard API

**Spec:** [../product-specs/2026-04-20-ai-message-table-actions-and-structured-format-design.md](../product-specs/2026-04-20-ai-message-table-actions-and-structured-format-design.md)

**Execution Status:** Completed on 2026-04-20. Implemented in the shared worktree without a commit because the worktree already contained unrelated in-flight changes. Fresh verification: `npx vitest run src/features/chat/components/markdown` reported `5 passed, 26 passed`, and `npx tsc --noEmit` exited with code `0`. Residual manual QA: narrow-width toolbar density and clipboard behavior in the packaged Tauri shell.

---

## File Structure

**Create:**
- `client/src/features/chat/components/markdown/table-model.ts` — 从渲染后 `<table>` DOM 提取统一 `TableModel`
- `client/src/features/chat/components/markdown/table-serializers.ts` — `HTML / CSV / TSV / Markdown / JSON / downloadable CSV` 序列化逻辑
- `client/src/features/chat/components/markdown/__tests__/table-model.test.ts` — DOM 提取与边界数据测试
- `client/src/features/chat/components/markdown/__tests__/table-serializers.test.ts` — 导出格式规则测试

**Modify:**
- `client/src/features/chat/components/markdown/markdown-table.ts` — 从纯滚动壳扩展为表格卡片 DOM 装饰、动作节点注入
- `client/src/features/chat/components/markdown/markdown.tsx` — 接入表格动作事件、复制/下载调度、成功反馈
- `client/src/features/chat/components/markdown/markdown.css` — 表格 header/action/menu 样式与小屏降级
- `client/src/features/chat/components/markdown/__tests__/markdown.test.tsx` — 表格动作栏渲染与点击回归

**Untouched but covered by shared pipeline:**
- `client/src/features/chat/components/turn/reasoning-part.tsx`
- `client/src/features/chat/components/tools/*`

---

### Task 1: Add red-state tests for table model extraction and serializer rules

**Files:**
- Create: `client/src/features/chat/components/markdown/__tests__/table-model.test.ts`
- Create: `client/src/features/chat/components/markdown/__tests__/table-serializers.test.ts`
- Modify: `client/src/features/chat/components/markdown/__tests__/markdown.test.tsx`

- [x] **Step 1: Write failing `TableModel` extraction tests**

```ts
it('extracts headers and rows from a rendered markdown table', () => {
  const root = document.createElement('div')
  root.innerHTML = `
    <table>
      <thead><tr><th>星期</th><th>主食</th></tr></thead>
      <tbody><tr><td>周一</td><td>米饭</td></tr></tbody>
    </table>
  `

  expect(extractTableModel(root.querySelector('table')!)).toEqual({
    headers: ['星期', '主食'],
    rows: [['周一', '米饭']],
    columnCount: 2,
    sourceHtml: expect.stringContaining('<table'),
  })
})

it('pads short rows and normalizes duplicate headers for JSON keys downstream', () => {
  const root = document.createElement('div')
  root.innerHTML = `
    <table>
      <thead><tr><th>值</th><th>值</th><th></th></tr></thead>
      <tbody><tr><td>1</td><td>2</td></tr></tbody>
    </table>
  `

  expect(extractTableModel(root.querySelector('table')!).rows).toEqual([['1', '2', '']])
})
```

- [x] **Step 2: Write failing serializer tests for CSV / TSV / Markdown / JSON**

```ts
const model: TableModel = {
  headers: ['姓名', '备注'],
  rows: [['Alice', '含,逗号'], ['Bob', '双引号"与\n换行']],
  columnCount: 2,
  sourceHtml: '<table>...</table>',
}

expect(toCsv(model)).toContain('"双引号""与\n换行"')
expect(toTsv(model)).toContain('Alice\t含,逗号')
expect(toMarkdownTable(model)).toContain('| 姓名 | 备注 |')
expect(toJson(model)).toBe('[{"姓名":"Alice","备注":"含,逗号"},{"姓名":"Bob","备注":"双引号\\"与\\n换行"}]')
```

- [x] **Step 3: Extend rendered Markdown tests to assert the table action bar is injected**

```tsx
it('renders a table action bar with copy, csv, and more controls', async () => {
  const markdown = '| name | value |\n| --- | --- |\n| JAVA_HOME | graalvm |'
  const { container } = render(<Markdown text={markdown} cacheKey="table-actions-1" />)
  await waitFor(() => expect(screen.getByRole('table')).toBeInTheDocument())
  expect(container.querySelector('[data-slot="markdown-table-bar"]')).not.toBeNull()
  expect(container.querySelector('[data-slot="markdown-table-copy"]')).not.toBeNull()
  expect(container.querySelector('[data-slot="markdown-table-csv"]')).not.toBeNull()
  expect(container.querySelector('[data-slot="markdown-table-more"]')).not.toBeNull()
})
```

- [x] **Step 4: Run tests to confirm the new expectations fail**

Run:

```bash
cd client && npx vitest run \
  src/features/chat/components/markdown/__tests__/table-model.test.ts \
  src/features/chat/components/markdown/__tests__/table-serializers.test.ts \
  src/features/chat/components/markdown/__tests__/markdown.test.tsx
```

Expected: missing module / missing export / missing DOM slot failures.

---

### Task 2: Implement `TableModel` extraction and deterministic serializers

**Files:**
- Create: `client/src/features/chat/components/markdown/table-model.ts`
- Create: `client/src/features/chat/components/markdown/table-serializers.ts`
- Test: `client/src/features/chat/components/markdown/__tests__/table-model.test.ts`
- Test: `client/src/features/chat/components/markdown/__tests__/table-serializers.test.ts`

- [x] **Step 1: Add `extractTableModel(table)`**

```ts
export type TableModel = {
  headers: string[]
  rows: string[][]
  sourceHtml: string
  columnCount: number
}

export function extractTableModel(table: HTMLTableElement): TableModel {
  const headers = Array.from(table.querySelectorAll('thead th')).map(readCellText)
  const bodyRows = Array.from(table.querySelectorAll('tbody tr')).map((row) =>
    Array.from(row.querySelectorAll('td, th')).map(readCellText),
  )
  const columnCount = Math.max(headers.length, ...bodyRows.map((row) => row.length), 0)
  return {
    headers: pad(headers, columnCount),
    rows: bodyRows.map((row) => pad(row, columnCount)),
    sourceHtml: table.outerHTML,
    columnCount,
  }
}
```

- [x] **Step 2: Add serializer helpers**

```ts
export function toCsv(model: TableModel): string
export function toTsv(model: TableModel): string
export function toMarkdownTable(model: TableModel): string
export function toJson(model: TableModel): string
export function toDownloadableCsv(model: TableModel): string
export function getDownloadFilename(now = new Date()): string
```

Rules:
- CSV uses `\r\n`, RFC-style quoting, includes headers
- TSV uses `\n`, replaces embedded newlines with spaces
- Markdown escapes `|` and uses `<br />` for newlines
- JSON emits array-of-objects with normalized fallback keys `column_1`, `column_2`, ... and duplicate suffixes `_2`, `_3`
- downloadable CSV prepends UTF-8 BOM

- [x] **Step 3: Run focused serializer tests until green**

Run:

```bash
cd client && npx vitest run \
  src/features/chat/components/markdown/__tests__/table-model.test.ts \
  src/features/chat/components/markdown/__tests__/table-serializers.test.ts
```

Expected: PASS.

---

### Task 3: Upgrade table decoration from scroll shell to action-enabled table card

**Files:**
- Modify: `client/src/features/chat/components/markdown/markdown-table.ts`
- Modify: `client/src/features/chat/components/markdown/markdown.css`
- Test: `client/src/features/chat/components/markdown/__tests__/markdown.test.tsx`

- [x] **Step 1: Inject a stable table card DOM structure around each rendered `<table>`**

```ts
const shell = document.createElement('div')
shell.setAttribute('data-component', 'markdown-table')

const bar = document.createElement('div')
bar.setAttribute('data-slot', 'markdown-table-bar')

const label = document.createElement('span')
label.setAttribute('data-slot', 'markdown-table-label')
label.textContent = 'Table'

const actions = document.createElement('div')
actions.setAttribute('data-slot', 'markdown-table-actions')

actions.append(makeButton('markdown-table-copy', 'Copy table'))
actions.append(makeButton('markdown-table-csv', 'CSV'))
actions.append(makeButton('markdown-table-more', 'More'))

const scroll = document.createElement('div')
scroll.setAttribute('data-slot', 'markdown-table-scroll')
```

- [x] **Step 2: Style the bar/actions/menu so they visually match existing code-window chrome**

Add CSS for:
- bar spacing and border
- weak label chip
- action buttons
- success state via `data-copied="true"`
- a lightweight popover menu anchored under `more`
- narrow-screen collapse that hides the dedicated CSV button and keeps `copy + more`

- [x] **Step 3: Re-run rendered Markdown tests**

Run:

```bash
cd client && npx vitest run src/features/chat/components/markdown/__tests__/markdown.test.tsx
```

Expected: PASS for table action bar rendering assertions.

---

### Task 4: Wire action events for copy, format export, and CSV download

**Files:**
- Modify: `client/src/features/chat/components/markdown/markdown.tsx`
- Modify: `client/src/features/chat/components/markdown/markdown-table.ts`
- Test: `client/src/features/chat/components/markdown/__tests__/markdown.test.tsx`

- [x] **Step 1: Extend the existing click delegation to handle table actions**

Add handlers for:
- `markdown-table-copy`
- `markdown-table-csv`
- `markdown-table-action` menu items with `data-format="tsv|markdown|json|download-csv"`

Implementation outline:

```ts
const table = btn.closest('[data-component="markdown-table"]')?.querySelector('table')
const model = table ? extractTableModel(table as HTMLTableElement) : null
if (!model) return
```

- [x] **Step 2: Implement copy behavior**

Rules:
- `复制表格`: write both `text/html` and `text/plain` (TSV fallback)
- `CSV / TSV / Markdown / JSON`: copy plain text only
- `下载 CSV`: create `Blob`, object URL, synthetic anchor click, revoke URL

- [x] **Step 3: Reuse existing success feedback semantics**

```ts
btn.setAttribute('data-copied', 'true')
setTimeout(() => btn.removeAttribute('data-copied'), 2000)
```

Menu actions should mark the triggering item or the `more` button as succeeded long enough for users to see it.

- [x] **Step 4: Add regression tests for click behavior**

Cover:
- `copy table` calls clipboard with both html and plain text
- `csv` copies serialized CSV
- `more -> json` copies normalized JSON
- `download csv` creates a download named `table-*.csv`

- [x] **Step 5: Run the focused UI tests**

Run:

```bash
cd client && npx vitest run src/features/chat/components/markdown/__tests__/markdown.test.tsx
```

Expected: PASS.

---

### Task 5: Consolidated verification and plan/doc updates

**Files:**
- Modify: `docs/exec-plans/2026-04-20-ai-message-table-actions-plan.md`
- Modify: `docs/exec-plans/index.md`
- Modify: `docs/product-specs/index.md` only if spec summary needs a scope note after implementation

- [x] **Step 1: Run final verification**

Run:

```bash
cd client && npx vitest run src/features/chat/components/markdown
cd client && npx tsc --noEmit
```

Expected: all markdown-related tests pass and type-check exits `0`.

- [x] **Step 2: Mark completed checkboxes with notes for any deviations**

- [x] **Step 3: Move the plan entry from Active to Completed in `docs/exec-plans/index.md`**

- [x] **Step 4: Summarize residual manual QA**

Document any remaining human checks, limited to visual sanity items such as button density on narrow widths.
