import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

interface PayloadPreviewTableProps {
  columns: string[]
  rows: Record<string, unknown>[]
  totalRows: number
}

export function PayloadPreviewTable({ columns, rows, totalRows }: PayloadPreviewTableProps) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-ui-xs text-text-muted">
        Showing {rows.length} of {totalRows} rows
      </span>
      <div className="border border-border-default rounded-md overflow-auto max-h-[300px]">
        <Table>
          <TableHeader>
            <TableRow className="bg-bg-subtle hover:bg-bg-subtle">
              {columns.map((col) => (
                <TableHead key={col} className="text-ui-xs font-medium text-text-muted h-8 px-2">
                  {col}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row, i) => (
              <TableRow key={i} className="hover:bg-interaction-hover">
                {columns.map((col) => (
                  <TableCell key={col} className="text-ui-xs px-2 py-1 max-w-[200px] truncate">
                    {row[col] == null ? <span className="text-text-soft italic">null</span> : String(row[col])}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
