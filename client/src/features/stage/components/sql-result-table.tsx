import { useCallback, useEffect, useMemo, useState } from 'react'
import type { SqlExecuteResultItem } from '@/services/api/sql'
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuTrigger,
} from '@/components/ui/context-menu'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { copyToClipboard } from '@/lib/utils'

type SqlResultTableProps = {
  result: SqlExecuteResultItem
}

const stickyHeaderCellClass = 'sticky top-0 z-20 h-8 border-b border-border/50 bg-muted px-3'

type ResultContextTarget = {
  cellValue?: unknown
  row?: unknown[]
  column?: string
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

export function SqlResultTable({ result }: SqlResultTableProps) {
  const { t } = useI18n()
  const [page, setPage] = useState(1)
  const [contextTarget, setContextTarget] = useState<ResultContextTarget | null>(null)
  const pageSize = 100
  const pageCount = Math.max(1, Math.ceil(result.rows.length / pageSize))
  const pageStart = (page - 1) * pageSize
  const visibleRows = useMemo(
    () => result.rows.slice(pageStart, pageStart + pageSize),
    [pageStart, result.rows],
  )

  useEffect(() => {
    setPage(1)
    setContextTarget(null)
  }, [result.resultId])

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

  return (
    <div className="flex h-full min-h-0 flex-col">
      <ContextMenu>
        <ContextMenuTrigger
          render={
            <div
              data-testid="sql-result-table-scroll"
              className="min-h-0 flex-1 overflow-auto"
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
                        onContextMenu={() => setContextTarget({ row })}
                      >
                        {pageStart + rowIndex + 1}
                      </TableCell>
                      {row.map((cell, cellIndex) => (
                        <TableCell
                          key={cellIndex}
                          className="max-w-[360px] truncate px-3 py-1.5"
                          onContextMenu={() =>
                            setContextTarget({
                              cellValue: cell,
                              row,
                              column: result.columns[cellIndex],
                            })
                          }
                        >
                          {cell == null ? (
                            <span className="italic text-muted-foreground/70">
                              {t('stage.queryEditor.cell.null')}
                            </span>
                          ) : (
                            String(cell)
                          )}
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
      <div className="flex shrink-0 items-center justify-between gap-2 border-t border-border/50 px-3 py-2">
        <span className="text-xs text-muted-foreground">{summaryLabel}</span>
        {pageCount > 1 ? (
          <div className="flex items-center gap-2">
            <span className="text-xs text-muted-foreground">
              {t('stage.queryEditor.result.pageIndicator', { current: page, total: pageCount })}
            </span>
            <Button size="sm" variant="outline" disabled={page === 1} onClick={() => setPage((current) => Math.max(1, current - 1))}>
              {t('stage.queryEditor.result.previousPage')}
            </Button>
            <Button size="sm" variant="outline" disabled={page === pageCount} onClick={() => setPage((current) => Math.min(pageCount, current + 1))}>
              {t('stage.queryEditor.result.nextPage')}
            </Button>
          </div>
        ) : null}
      </div>
    </div>
  )
}
