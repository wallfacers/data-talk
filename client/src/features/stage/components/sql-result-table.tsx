import { useEffect, useMemo, useState } from 'react'
import type { SqlExecuteResultItem } from '@/services/api/sql'
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

type SqlResultTableProps = {
  result: SqlExecuteResultItem
}

export function SqlResultTable({ result }: SqlResultTableProps) {
  const { t } = useI18n()
  const [page, setPage] = useState(1)
  const pageSize = 100
  const pageCount = Math.max(1, Math.ceil(result.rows.length / pageSize))
  const pageStart = (page - 1) * pageSize
  const visibleRows = useMemo(
    () => result.rows.slice(pageStart, pageStart + pageSize),
    [pageStart, result.rows],
  )

  useEffect(() => {
    setPage(1)
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

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div data-testid="sql-result-table-scroll" className="min-h-0 flex-1 overflow-auto">
        <Table className="text-xs">
          <TableHeader className="sticky top-0 bg-muted/40">
            <TableRow className="hover:bg-transparent">
              <TableHead className="h-8 w-14 border-b border-border/50 px-3 text-center">
                {t('stage.queryEditor.result.rowNumber')}
              </TableHead>
              {result.columns.map((column, columnIndex) => (
                <TableHead key={`${column}-${columnIndex}`} className="h-8 border-b border-border/50 px-3">
                  {column}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {visibleRows.map((row, rowIndex) => (
              <TableRow key={rowIndex} className="border-b border-border/30">
                <TableCell className="px-3 py-1.5 text-center text-muted-foreground">
                  {pageStart + rowIndex + 1}
                </TableCell>
                {row.map((cell, cellIndex) => (
                  <TableCell key={cellIndex} className="max-w-[360px] truncate px-3 py-1.5">
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
