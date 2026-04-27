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
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useI18n } from '@/i18n/use-i18n'
import { copyToClipboard } from '@/lib/utils'
import { Download, Copy } from 'lucide-react'
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

function serializeResultValue(value: unknown) {
  if (value == null) return 'NULL'
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
  const pageCount = Math.max(1, Math.ceil(result.rows.length / pageSize))
  const pageStart = (page - 1) * pageSize
  const visibleRows = useMemo(
    () => result.rows.slice(pageStart, pageStart + pageSize),
    [pageStart, result.rows],
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
  }, [result.resultId])

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

  return (
    <div className="flex h-full min-h-0 flex-col">
      <ContextMenu>
        <ContextMenuTrigger
          render={
            <div
              ref={scrollContainerRef}
              data-testid="sql-result-table-scroll"
              data-result-scrollbar="header-offset"
              className="min-h-0 flex-1 overflow-auto"
              onContextMenuCapture={() => setContextTarget(null)}
              onScroll={handleScroll}
            >
              <Table scrollContainer={false} className="min-w-max text-xs">
                <TableHeader className="bg-muted">
                  <TableRow className="hover:bg-transparent">
                    <TableHead className={`${stickyHeaderCellClass} w-14 text-center`}>
                      {t('stage.queryEditor.result.rowNumber')}
                    </TableHead>
                    {result.columns.map((column, columnIndex) => (
                      <TableHead
                        key={`${column}-${columnIndex}`}
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
                      {row.map((cell, cellIndex) => {
                        const cellLabel = serializeResultValue(cell)
                        return (
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
                            <Tooltip>
                              <TooltipTrigger
                                render={
                                  <span className="block truncate">
                                    {cell == null ? (
                                      <span className="italic text-muted-foreground/70">
                                        {t('stage.queryEditor.cell.null')}
                                      </span>
                                    ) : (
                                      String(cell)
                                    )}
                                  </span>
                                }
                              />
                              <TooltipContent>
                                <span className="block max-w-xs truncate">{cellLabel}</span>
                              </TooltipContent>
                            </Tooltip>
                          </TableCell>
                        )
                      })}
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
  )
}
