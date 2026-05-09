# SQL 结果集全屏查看 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 SQL 结果集工具栏添加全屏弹框按钮，点击后以 Portal 叠加层放大展示当前结果集表格。

**Architecture:** 在 `SqlResultTable` 组件内新增 `isExpanded` 状态和全屏按钮。弹框复用 `chart-expand-modal.tsx` 的 `createPortal` 模式，渲染与嵌入式表格共享同一份数据和状态（分页、导出范围等）。

**Tech Stack:** React 19, createPortal, lucide-react (Maximize2Icon), shadcn Button, CSS variables from DESIGN.md

---

### Task 1: 添加 i18n keys

**Files:**
- Modify: `client/src/i18n/messages.ts`

- [ ] **Step 1: 在中文消息区添加 3 个 key**

在 `stage.queryEditor.result.downloadCsvAria`（第 509 行）之后、`stage.queryEditor.result.copied`（第 510 行）之前插入：

```ts
'stage.queryEditor.result.expandAria': '全屏查看结果集',
'stage.queryEditor.result.expand': '全屏',
'stage.queryEditor.result.expandTitle': '查询结果',
```

- [ ] **Step 2: 在英文消息区添加对应的 3 个 key**

在英文区的 `stage.queryEditor.result.downloadCsvAria`（约第 1247 行）之后、`stage.queryEditor.result.copied` 之前插入：

```ts
'stage.queryEditor.result.expandAria': 'View result in fullscreen',
'stage.queryEditor.result.expand': 'Expand',
'stage.queryEditor.result.expandTitle': 'Query Result',
```

- [ ] **Step 3: 运行类型检查**

Run: `cd client && npx tsc --noEmit`
Expected: PASS，零类型错误

- [ ] **Step 4: Commit**

```bash
git add client/src/i18n/messages.ts
git commit -m "feat(i18n): add fullscreen expand keys for SQL result table"
```

---

### Task 2: 添加全屏按钮和弹框

**Files:**
- Modify: `client/src/features/stage/components/sql-result-table.tsx`

- [ ] **Step 1: 添加 import**

在文件顶部 import 区：
- 在 `react` 导入中追加 `createPortal`
- 将 lucide-react 导入从 `{ Download, Copy }` 改为 `{ Download, Copy, Maximize2Icon, XIcon }`

具体改动：

```ts
// 第 1 行
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, createPortal } from 'react'
```

```ts
// 第 28 行
import { Download, Copy, Maximize2Icon, XIcon } from 'lucide-react'
```

- [ ] **Step 2: 在组件内添加 isExpanded 状态**

在 `const [copiedAction, setCopiedAction] = useState<'csv' | 'json' | null>(null)` 之后（第 129 行）添加：

```ts
const [isExpanded, setIsExpanded] = useState(false)
```

同时在 `result.resultId` 的 useEffect（第 141-146 行）中追加 `setIsExpanded(false)`：

```ts
useEffect(() => {
  setPage(1)
  setContextTarget(null)
  setDetailTarget(null)
  setDetailFormatted(false)
  setIsExpanded(false)
}, [result.resultId])
```

- [ ] **Step 3: 添加 Esc 关闭 effect**

在组件内新增一个 useEffect（放在上面的 useEffect 之后）：

```ts
useEffect(() => {
  if (!isExpanded) return
  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') setIsExpanded(false)
  }
  document.addEventListener('keydown', onKeyDown)
  return () => document.removeEventListener('keydown', onKeyDown)
}, [isExpanded])
```

- [ ] **Step 4: 在工具栏下载 CSV 按钮后添加全屏按钮**

在第 428 行（下载 CSV 的 `</Button>` 之后），分页控件之前，插入全屏按钮：

```tsx
<Button size="sm" variant="outline" aria-label={t('stage.queryEditor.result.expandAria')} onClick={() => setIsExpanded(true)}>
  <Maximize2Icon className="size-3.5" />
  {t('stage.queryEditor.result.expand')}
</Button>
```

- [ ] **Step 5: 在组件 return 的最外层 `</div>` 之前添加 Portal 弹框**

在组件 return 的最后一个 `</div>`（第 444 行）之前插入：

```tsx
{isExpanded && createPortal(
  <div
    className="fixed inset-0 z-[300] flex items-center justify-center bg-[var(--dt-bg-overlay)] p-4"
    onMouseDown={(event) => {
      if (event.target === event.currentTarget) setIsExpanded(false)
    }}
  >
    <div className="flex h-[92vh] w-[92vw] max-w-7xl flex-col overflow-hidden rounded-xl border border-[var(--dt-border-subtle)] bg-[var(--dt-bg-panel)] shadow-2xl">
      <div className="flex items-center justify-between border-b border-[var(--dt-border-subtle)] px-4 py-2">
        <span className="font-mono text-[13px] leading-[18px] text-[var(--dt-text-strong)]">{t('stage.queryEditor.result.expandTitle')}</span>
        <button
          type="button"
          aria-label={t('stage.close')}
          onClick={() => setIsExpanded(false)}
          className="h-7 rounded-md px-2 text-sm text-[var(--dt-text-muted)] transition-colors hover:bg-[var(--dt-hover)] hover:text-[var(--dt-text-strong)]"
        >
          <XIcon className="size-4" />
        </button>
      </div>
      <div className="min-h-0 flex-1 overflow-hidden">
        <ContextMenu>
          <ContextMenuTrigger
            render={
              <div
                className="h-full overflow-auto"
                onContextMenuCapture={() => setContextTarget(null)}
              >
                <Table scrollContainer={false} className="min-w-max text-xs">
                  <TableHeader className="bg-muted">
                    <TableRow className="hover:bg-transparent">
                      <TableHead className={`${stickyHeaderCellClass} w-14 text-center`}>
                        {t('stage.queryEditor.result.rowNumber')}
                      </TableHead>
                      {result.columns.map((column, columnIndex) => (
                        <TableHead
                          key={`expanded-${column}-${columnIndex}`}
                          className={stickyHeaderCellClass}
                          onContextMenu={() => setContextTarget({ column })}
                        >
                          {column}
                        </TableHead>
                      ))}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {visibleRows.map((row, rowIndex) => (
                      <TableRow key={rowIndex} className="border-b border-border/30">
                        <TableCell
                          className="px-3 py-1.5 text-center text-muted-foreground"
                          onContextMenu={() => setContextTarget({ row, rowNumber: pageStart + rowIndex + 1 })}
                        >
                          {pageStart + rowIndex + 1}
                        </TableCell>
                        {row.map((cell, cellIndex) => (
                          <TableCell
                            key={cellIndex}
                            className="max-w-[360px] px-3 py-1.5"
                            onContextMenu={() =>
                              setContextTarget({
                                cellValue: cell,
                                row,
                                column: result.columns[cellIndex],
                                rowNumber: pageStart + rowIndex + 1,
                              })
                            }
                          >
                            <span className="block truncate">
                              {cell == null ? (
                                <span className="italic text-muted-foreground/70">
                                  {t('stage.queryEditor.cell.null')}
                                </span>
                              ) : typeof cell === 'object' ? (
                                JSON.stringify(cell)
                              ) : (
                                String(cell)
                              )}
                            </span>
                          </TableCell>
                        ))}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            }
          />
          <ContextMenuContent className="w-40 font-sans text-xs">
            <ContextMenuItem
              disabled={!contextTarget || !('cellValue' in contextTarget)}
              onClick={() => openCellDetail(contextTarget)}
            >
              {t('stage.queryEditor.result.viewCell')}
            </ContextMenuItem>
            <ContextMenuItem
              disabled={!contextTarget || !('cellValue' in contextTarget)}
              onClick={copyCell}
            >
              {t('stage.queryEditor.result.copyCell')}
            </ContextMenuItem>
            <ContextMenuItem
              disabled={!contextTarget?.row}
              onClick={copyRow}
            >
              {t('stage.queryEditor.result.copyRow')}
            </ContextMenuItem>
            <ContextMenuItem
              disabled={!contextTarget?.column}
              onClick={copyColumnName}
            >
              {t('stage.queryEditor.result.copyColumnName')}
            </ContextMenuItem>
          </ContextMenuContent>
        </ContextMenu>
        <div className="flex shrink-0 flex-wrap items-center justify-between gap-2 border-t border-border/50 px-3 py-2">
          <span className="text-xs text-muted-foreground">{summaryLabel}</span>
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
            {pageCount > 1 ? (
              <>
                <span className="text-xs text-muted-foreground">
                  {t('stage.queryEditor.result.pageIndicator', { current: page, total: pageCount })}
                </span>
                <Button size="sm" variant="outline" disabled={page === 1} onClick={() => setPage((current) => Math.max(1, current - 1))}>
                  {t('stage.queryEditor.result.previousPage')}
                </Button>
                <Button size="sm" variant="outline" disabled={page === pageCount} onClick={() => setPage((current) => Math.min(pageCount, current + 1))}>
                  {t('stage.queryEditor.result.nextPage')}
                </Button>
              </>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  </div>,
  document.body,
)}
```

- [ ] **Step 6: 运行类型检查**

Run: `cd client && npx tsc --noEmit`
Expected: PASS，零类型错误

- [ ] **Step 7: Commit**

```bash
git add client/src/features/stage/components/sql-result-table.tsx
git commit -m "feat(sql-result): add fullscreen expand button with portal overlay"
```
