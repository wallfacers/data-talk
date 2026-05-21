import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import type { DataGridProps } from '../types'
import { useI18n } from '@/i18n/use-i18n'

export function DataGrid({ columns, rows }: DataGridProps) {
  const { t } = useI18n()
  if (columns.length === 0) {
    return <p className="p-4 text-sm text-muted-foreground">{t('dataGrid.noColumns')}</p>
  }

  return (
    <div className="h-full overflow-auto">
      <Table>
        <TableHeader className="sticky top-0 bg-background">
          <TableRow>
            {columns.map((c) => (
              <TableHead key={c.key}>{c.header}</TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={columns.length} className="text-center text-muted-foreground">
                {t('dataGrid.noData')}
              </TableCell>
            </TableRow>
          )}
          {rows.map((row, i) => (
            <TableRow key={i}>
              {columns.map((c) => (
                <TableCell key={c.key}>{(() => { const v = row[c.key]; return v == null ? '' : typeof v === 'object' ? JSON.stringify(v) : String(v) })()}</TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
