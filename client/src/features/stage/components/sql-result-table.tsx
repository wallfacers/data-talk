import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { SqlExecuteResultItem } from '@/services/api/sql'
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger,
} from '@/components/ui/context-menu'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select'
import { useI18n } from '@/i18n/use-i18n'
import { copyToClipboard } from '@/lib/utils'
import { Download, Copy, Maximize2Icon, XIcon, SearchIcon, ArrowUpIcon, ArrowDownIcon } from 'lucide-react'
import { toast } from 'sonner'
import {
  buildSqlResultExportFilename,
  selectSqlResultExportRows,
  toSqlResultCsv,
  toSqlResultDownloadCsv,
  toSqlResultJson,
  type SqlResultExportScope,
} from '../utils/sql-result-export'
import '@/features/chat/components/markdown/markdown.css'

type SqlResultTableProps = {
  result: SqlExecuteResultItem
  scrollPosition?: ResultScrollPosition
  onScrollPositionChange?: (position: ResultScrollPosition) => void
}

const stickyHeaderCellClass = 'sticky top-0 z-20 h-8 border-b border-border/50 bg-muted px-3'

export type ResultScrollPosition = {
  scrollTop: number
  scrollLeft: number
}

type ResultContextTarget = {
  cellValue?: unknown
  row?: unknown[]
  column?: string
  rowNumber?: number
}

type SortState = {
  column: string | null
  direction: 'asc' | 'desc' | null
}

function serializeResultValue(value: unknown) {
  if (value == null) return 'NULL'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function serializeResultRow(row: unknown[]) {
  return row
    .map((value) => serializeResultValue(value).replace(/[\t\r\n]+/g, ' '))
    .join('\t')
}

function formatJson(raw: string) {
  const trimmed = raw.trim()
  if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return null
  try {
    return JSON.stringify(JSON.parse(trimmed), null, 2)
  } catch {
    return null
  }
}

function isValidXml(raw: string) {
  const trimmed = raw.trim()
  if (!trimmed.startsWith('<') || !trimmed.endsWith('>')) return false
  if (typeof DOMParser === 'undefined') return true
  const parsed = new DOMParser().parseFromString(trimmed, 'application/xml')
  return parsed.querySelector('parsererror') == null
}

function formatXml(raw: string) {
  if (!isValidXml(raw)) return null
  const compact = raw.trim().replace(/>\s*</g, '><')
  const tokens = compact.replace(/(>)(<)(\/*)/g, '$1\n$2$3').split('\n')
  let depth = 0

  return tokens
    .map((token) => {
      if (/^<\//.test(token)) depth = Math.max(0, depth - 1)
      const line = `${'  '.repeat(depth)}${token}`
      if (/^<[^!?/][^>]*[^/]?>$/.test(token) && !token.includes('</')) depth += 1
      return line
    })
    .join('\n')
}

function getFormattedContent(raw: string) {
  return formatJson(raw) ?? formatXml(raw)
}

function getContentLanguage(raw: string) {
  if (formatJson(raw)) return 'json'
  if (formatXml(raw)) return 'xml'
  return 'text'
}

export function SqlResultTable({
  result,
  scrollPosition,
  onScrollPositionChange,
}: SqlResultTableProps) {
  const { t } = useI18n()
  const scrollContainerRef = useRef<HTMLDivElement | null>(null)
  const [page, setPage] = useState(1)
  const [contextTarget, setContextTarget] = useState<ResultContextTarget | null>(null)
  const [detailTarget, setDetailTarget] = useState<ResultContextTarget | null>(null)
  const [detailFormatted, setDetailFormatted] = useState(false)
  const [detailWrap, setDetailWrap] = useState(true)
  const pageSize = 100
  const [exportScope, setExportScope] = useState<SqlResultExportScope>('page')
  const [copiedAction, setCopiedAction] = useState<'csv' | 'json' | null>(null)
  const [isExpanded, setIsExpanded] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [sortState, setSortState] = useState<SortState>({ column: null, direction: null })

  const filteredRows = useMemo(() => {
    if (!searchQuery.trim()) return result.rows
    const q = searchQuery.toLowerCase().trim()
    return result.rows.filter(row =>
      row.some(cell => serializeResultValue(cell).toLowerCase().includes(q)),
    )
  }, [result.rows, searchQuery])

  const sortedRows = useMemo(() => {
    if (!sortState.column || !sortState.direction) return filteredRows
    const colIndex = result.columns.indexOf(sortState.column)
    if (colIndex < 0) return filteredRows
    const dir = sortState.direction === 'asc' ? 1 : -1
    return [...filteredRows].sort((a, b) => {
      const va = a[colIndex], vb = b[colIndex]
      if (va == null && vb == null) return 0
      if (va == null) return 1
      if (vb == null) return -1
      if (typeof va === 'number' && typeof vb === 'number') return (va - vb) * dir
      const sa = String(va).toLowerCase(), sb = String(vb).toLowerCase()
      const cmp = sa < sb ? -1 : sa > sb ? 1 : 0
      return cmp * dir
    })
  }, [filteredRows, sortState, result.columns])

  const pageCount = Math.max(1, Math.ceil(sortedRows.length / pageSize))
  const pageStart = (page - 1) * pageSize
  const visibleRows = useMemo(
    () => sortedRows.slice(pageStart, pageStart + pageSize),
    [pageStart, sortedRows],
  )
  const exportRows = useMemo(
    () => selectSqlResultExportRows(result.rows, visibleRows, exportScope),
    [exportScope, result.rows, visibleRows],
  )

  useEffect(() => {
    setPage(1)
    setContextTarget(null)
    setDetailTarget(null)
    setDetailFormatted(false)
    setIsExpanded(false)
    setSearchQuery('')
    setSortState({ column: null, direction: null })
  }, [result.resultId])

  useEffect(() => {
    setPage(1)
  }, [searchQuery])

  const handleSortToggle = useCallback((column: string) => {
    setSortState(prev => {
      if (prev.column !== column) return { column, direction: 'asc' }
      if (prev.direction === 'asc') return { column, direction: 'desc' }
      return { column: null, direction: null }
    })
    setPage(1)
  }, [])

  useLayoutEffect(() => {
    const container = scrollContainerRef.current
    if (!container) return

    const nextScrollTop = scrollPosition?.scrollTop ?? 0
    const nextScrollLeft = scrollPosition?.scrollLeft ?? 0
    if (container.scrollTop !== nextScrollTop) {
      container.scrollTop = nextScrollTop
    }
    if (container.scrollLeft !== nextScrollLeft) {
      container.scrollLeft = nextScrollLeft
    }
  }, [result.resultId, scrollPosition?.scrollLeft, scrollPosition?.scrollTop])

  const handleScroll = useCallback(() => {
    const container = scrollContainerRef.current
    if (!container) return
    onScrollPositionChange?.({
      scrollTop: container.scrollTop,
      scrollLeft: container.scrollLeft,
    })
  }, [onScrollPositionChange])

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

  const summaryLabel = result.truncated
    ? t('stage.queryEditor.summary.truncated', {
        count: result.rowCount,
        executionMs: result.executionMs,
      })
    : t('stage.queryEditor.summary.rows', {
        count: result.rowCount,
        executionMs: result.executionMs,
      })

  const copyCell = useCallback(() => {
    if (!contextTarget || !('cellValue' in contextTarget)) return
    void copyToClipboard(serializeResultValue(contextTarget.cellValue))
  }, [contextTarget])

  const handleCellDoubleClick = useCallback((cell: unknown) => {
    const text = serializeResultValue(cell)
    void copyToClipboard(text).then((ok) => {
      if (ok) toast.success(t('stage.queryEditor.result.copied'))
    })
  }, [t])

  const copyRow = useCallback(() => {
    if (!contextTarget?.row) return
    void copyToClipboard(serializeResultRow(contextTarget.row))
  }, [contextTarget])

  const copyColumnName = useCallback(() => {
    if (!contextTarget?.column) return
    void copyToClipboard(contextTarget.column)
  }, [contextTarget])

  const openCellDetail = useCallback((target: ResultContextTarget | null) => {
    if (!target || !('cellValue' in target)) return
    setDetailTarget(target)
    setDetailFormatted(false)
  }, [])

  const detailRaw = detailTarget && 'cellValue' in detailTarget
    ? serializeResultValue(detailTarget.cellValue)
    : ''
  const formattedDetail = detailRaw ? getFormattedContent(detailRaw) : null
  const detailContent = detailFormatted && formattedDetail ? formattedDetail : detailRaw
  const detailLanguage = getContentLanguage(detailRaw)

  // --- Extracted shared JSX ---

  const resultTable = (keyPrefix: string) => (
    <Table scrollContainer={false} className="min-w-max text-xs">
      <TableHeader className="bg-muted">
        <TableRow className="hover:bg-transparent">
          <TableHead className={`${stickyHeaderCellClass} w-14 text-center`}>
            {t('stage.queryEditor.result.rowNumber')}
          </TableHead>
          {result.columns.map((column, columnIndex) => (
            <TableHead
              key={`${keyPrefix}-${column}-${columnIndex}`}
              className={`${stickyHeaderCellClass} cursor-pointer select-none`}
              onClick={() => handleSortToggle(column)}
              onContextMenu={() => setContextTarget({ column })}
            >
              <span className="inline-flex items-center gap-1">
                {column}
                {sortState.column === column && sortState.direction === 'asc' && (
                  <ArrowUpIcon className="size-3 text-muted-foreground" />
                )}
                {sortState.column === column && sortState.direction === 'desc' && (
                  <ArrowDownIcon className="size-3 text-muted-foreground" />
                )}
              </span>
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
                className="max-w-[360px] cursor-default px-3 py-1.5 select-text"
                onDoubleClick={() => handleCellDoubleClick(cell)}
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
  )

  const contextMenuItems = (
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
  )

  const searchBarContent = () => (
    <div className="flex shrink-0 items-center gap-2 border-t border-border/50 px-3 py-1.5">
      <div className="flex h-7 items-center gap-1.5 rounded-md border border-border bg-bg-canvas px-2 transition-colors focus-within:border-ring focus-within:ring-3 focus-within:ring-ring/50">
        <SearchIcon className="size-3.5 shrink-0 text-muted-foreground" aria-hidden />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder={t('stage.queryEditor.result.search.placeholder')}
          aria-label={t('stage.queryEditor.result.search.ariaLabel')}
          className="w-48 bg-transparent text-xs outline-none placeholder:text-muted-foreground"
        />
        {searchQuery ? (
          <button
            type="button"
            aria-label={t('stage.queryEditor.result.search.clear')}
            onClick={() => setSearchQuery('')}
            className="text-muted-foreground transition-colors hover:text-foreground"
          >
            <XIcon className="size-3.5" />
          </button>
        ) : null}
      </div>
      {searchQuery ? (
        <span className="text-xs text-muted-foreground">
          {t('stage.queryEditor.result.search.matchCount', {
            matched: filteredRows.length,
            total: result.rows.length,
          })}
        </span>
      ) : null}
    </div>
  )

  const toolbarContent = (showExpand = true) => (
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
        {showExpand ? (
          <Button size="sm" variant="outline" aria-label={t('stage.queryEditor.result.expandAria')} onClick={() => setIsExpanded(true)}>
            <Maximize2Icon className="size-3.5" />
            {t('stage.queryEditor.result.expand')}
          </Button>
        ) : null}
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
  )

  return (
    <div className="flex h-full min-h-0 flex-col bg-bg-soft">
      <ContextMenu>
        <ContextMenuTrigger
          render={
            <div
              ref={scrollContainerRef}
              data-testid="sql-result-table-scroll"
              data-result-scrollbar="header-offset"
              className="min-h-0 flex-1 overflow-auto bg-bg-canvas"
              onContextMenuCapture={() => setContextTarget(null)}
              onScroll={handleScroll}
            >
              {resultTable('embedded')}
            </div>
          }
        />
        {contextMenuItems}
      </ContextMenu>
      {searchBarContent()}
      <Dialog open={!!detailTarget} onOpenChange={(open) => {
        if (!open) setDetailTarget(null)
      }}>
        <DialogContent className="h-[min(720px,calc(100vh-4rem))] max-w-4xl !p-0 overflow-hidden">
          <DialogHeader className="border-b border-border/60 px-4 py-3 pr-12">
            <DialogTitle className="text-sm">
              {t('stage.queryEditor.result.cellDetailTitle')}
            </DialogTitle>
            <DialogDescription className="text-xs">
              {t('stage.queryEditor.result.cellDetailDescription', {
                row: detailTarget?.rowNumber ?? '-',
                column: detailTarget?.column ?? '-',
                length: detailRaw.length,
              })}
            </DialogDescription>
          </DialogHeader>
          <div className="min-h-0 flex-1 p-4">
            <div
              data-component="markdown-code"
              className="flex h-full min-h-0 flex-col"
              style={{ margin: 0 }}
            >
              <div data-slot="markdown-code-bar">
                <span data-slot="markdown-code-language">{detailLanguage}</span>
                <div data-slot="markdown-code-actions">
                  {formattedDetail ? (
                    <button
                      type="button"
                      aria-pressed={detailFormatted}
                      onClick={() => setDetailFormatted((current) => !current)}
                    >
                      {t('stage.queryEditor.result.formatContent')}
                    </button>
                  ) : null}
                  <button
                    type="button"
                    aria-pressed={detailWrap}
                    onClick={() => setDetailWrap((current) => !current)}
                  >
                    {t('stage.queryEditor.result.wrapContent')}
                  </button>
                  <button
                    type="button"
                    onClick={() => void copyToClipboard(detailRaw)}
                  >
                    {t('stage.queryEditor.result.copyCell')}
                  </button>
                </div>
              </div>
              <pre
                className="min-h-0 flex-1"
                style={{ overflow: 'auto' }}
              >
                <code
                  data-testid="sql-result-cell-detail-content"
                  style={{
                    whiteSpace: detailWrap ? 'pre-wrap' : 'pre',
                    overflowWrap: detailWrap ? 'break-word' : 'normal',
                    wordBreak: detailWrap ? 'break-word' : 'normal',
                  }}
                >
                  {detailContent}
                </code>
              </pre>
            </div>
          </div>
        </DialogContent>
      </Dialog>
      {toolbarContent()}
      <Dialog open={isExpanded} onOpenChange={(open) => { if (!open) setIsExpanded(false) }}>
        <DialogContent
          showCloseButton={false}
          className="flex max-h-[80vh] w-[70vw] max-w-none -translate-x-1/2 -translate-y-1/2 flex-col overflow-hidden !p-0 !rounded-xl"
        >
          <div className="flex items-center justify-between border-b border-border/60 px-4 py-2">
            <DialogTitle className="font-mono text-[13px] leading-[18px]">
              {result.title}
            </DialogTitle>
            <DialogClose
              aria-label={t('stage.close')}
              render={
                <button
                  type="button"
                  className="h-7 rounded-md px-2 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
                />
              }
            >
              <XIcon className="size-4" />
            </DialogClose>
          </div>
          <div className="flex min-h-0 flex-1 flex-col overflow-hidden bg-bg-soft">
            <ContextMenu>
              <ContextMenuTrigger
                render={
                  <div
                    className="min-h-0 flex-1 overflow-auto bg-bg-canvas"
                    onContextMenuCapture={() => setContextTarget(null)}
                  >
                    {resultTable('expanded')}
                  </div>
                }
              />
              {contextMenuItems}
            </ContextMenu>
            {searchBarContent()}
            {toolbarContent(false)}
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
