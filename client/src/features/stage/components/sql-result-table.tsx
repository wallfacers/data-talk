import type { SqlExecuteResultItem } from '@/services/api/sql'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { useI18n } from '@/i18n/use-i18n'

type SqlResultTableProps = {
  result: SqlExecuteResultItem
}

export function SqlResultTable({ result }: SqlResultTableProps) {
  const { t } = useI18n()
  return (
    <div className="h-full overflow-auto">
      <Table className="text-xs">
        <TableHeader className="sticky top-0 bg-muted/40">
          <TableRow className="hover:bg-transparent">
            {result.columns.map((column, columnIndex) => (
              <TableHead key={`${column}-${columnIndex}`} className="h-8 border-b border-border/50 px-3">
                {column}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {result.rows.map((row, rowIndex) => (
            <TableRow key={rowIndex} className="border-b border-border/30">
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
  )
}
