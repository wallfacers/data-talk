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

type SqlDmlSummaryPanelProps = {
  result: SqlExecuteResultItem
}

export function SqlDmlSummaryPanel({ result }: SqlDmlSummaryPanelProps) {
  const { t } = useI18n()
  return (
    <div className="h-full overflow-auto">
      <Table className="text-xs">
        <TableHeader className="sticky top-0 bg-muted/40">
          <TableRow className="hover:bg-transparent">
            <TableHead className="h-8 w-14 border-b border-border/50 px-3 text-center">
              {t('stage.queryEditor.result.rowNumber')}
            </TableHead>
            <TableHead className="h-8 border-b border-border/50 px-3">
              {t('stage.queryEditor.result.action')}
            </TableHead>
            <TableHead className="h-8 border-b border-border/50 px-3">
              {t('stage.queryEditor.result.affectedRows')}
            </TableHead>
            <TableHead className="h-8 border-b border-border/50 px-3">
              {t('stage.queryEditor.result.duration')}
            </TableHead>
            <TableHead className="h-8 border-b border-border/50 px-3">
              {t('stage.queryEditor.result.sql')}
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow className="border-b border-border/30">
            <TableCell className="px-3 py-1.5 text-center text-muted-foreground">1</TableCell>
            <TableCell className="px-3 py-1.5">{result.title}</TableCell>
            <TableCell className="px-3 py-1.5">{result.affectedRows ?? 0}</TableCell>
            <TableCell className="px-3 py-1.5">{result.executionMs}ms</TableCell>
            <TableCell className="max-w-[640px] truncate px-3 py-1.5">{result.statementText}</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
  )
}
