import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useI18n } from '@/i18n/use-i18n'

const stickyHeaderCellClass = 'sticky top-0 z-20 h-8 border-b border-border/50 bg-muted px-3'

interface PayloadPreviewTableProps {
  columns: string[]
  rows: Record<string, unknown>[]
  totalRows: number
}

export function PayloadPreviewTable({ columns, rows, totalRows }: PayloadPreviewTableProps) {
  const { t } = useI18n()

  return (
    <div className="flex flex-col border border-border rounded-md overflow-hidden">
      <div data-result-scrollbar="header-offset" className="min-h-0 flex-1 overflow-auto max-h-[300px]">
        <Table scrollContainer={false} className="min-w-max text-xs">
          <TableHeader className="bg-muted">
            <TableRow className="hover:bg-transparent">
              {columns.map((col) => (
                <TableHead key={col} className={`${stickyHeaderCellClass} font-medium`}>
                  {col}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row, i) => (
              <TableRow key={i} className="border-b border-border/30 hover:bg-muted/50">
                {columns.map((col) => (
                  <TableCell key={col} className="px-3 py-1.5 max-w-[200px] truncate">
                    {row[col] == null ? <span className="text-muted-foreground italic">{t('stage.queryEditor.cell.null')}</span> : String(row[col])}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <div className="flex shrink-0 items-center border-t border-border/50 px-3 py-2">
        <span className="text-xs text-muted-foreground">
          {t('ingestion.payload.showingRows', { shown: rows.length, total: totalRows })}
        </span>
      </div>
    </div>
  )
}
