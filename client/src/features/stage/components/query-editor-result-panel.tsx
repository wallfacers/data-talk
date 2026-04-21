import { AlertTriangleIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useI18n } from '@/i18n/use-i18n'

type QueryEditorDisplayResult = {
  columns: string[]
  rows: unknown[][]
  rowCount: number
  executionMs: number
  truncated: boolean
}

type QueryEditorResultPanelProps = {
  status: 'idle' | 'running' | 'success' | 'risk_blocked' | 'error'
  result: QueryEditorDisplayResult | null
  title: string
  summary: string | null
  idleLabel: string
  runningLabel: string
  errorLabel: string
  riskTitle: string
  riskReason: string | null
  cancelLabel: string
  sendToAiLabel: string
  onCancel: () => void
  onSendToAi: () => void
}

function ResultTable({ result }: { result: QueryEditorDisplayResult }) {
  const { t } = useI18n()

  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-auto">
        <table className="w-full border-collapse text-xs">
          <thead className="sticky top-0 bg-muted/50">
            <tr>
              {result.columns.map((column, columnIndex) => (
                <th
                  key={`${column}-${columnIndex}`}
                  className="whitespace-nowrap border-b border-border/50 px-3 py-2 text-left font-medium"
                >
                  {column}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {result.rows.map((row, rowIndex) => (
              <tr key={rowIndex} className="border-b border-border/30 last:border-0 hover:bg-muted/30">
                {row.map((cell, cellIndex) => (
                  <td key={cellIndex} className="max-w-[300px] truncate whitespace-nowrap px-3 py-1.5">
                    {cell === null ? (
                      <span className="italic text-muted-foreground/50">{t('stage.queryEditor.cell.null')}</span>
                    ) : (
                      String(cell)
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export function QueryEditorResultPanel({
  status,
  result,
  title,
  summary,
  idleLabel,
  runningLabel,
  errorLabel,
  riskTitle,
  riskReason,
  cancelLabel,
  sendToAiLabel,
  onCancel,
  onSendToAi,
}: QueryEditorResultPanelProps) {
  const showTable = result && (status === 'idle' || status === 'success')

  return (
    <Card className="h-full gap-0 rounded-none border-0 py-0 ring-0">
      <CardHeader className="flex-row items-center justify-between border-b border-border/60 py-3">
        <CardTitle className="text-sm">{title}</CardTitle>
        <div className="text-xs text-muted-foreground">{summary}</div>
      </CardHeader>
      <CardContent className="min-h-0 flex-1 overflow-hidden px-0 pb-0">
        {showTable ? (
          <ResultTable result={result} />
        ) : null}
        {!showTable && status === 'idle' ? (
          <div className="flex h-full items-center justify-center px-4 text-xs text-muted-foreground">
            {idleLabel}
          </div>
        ) : null}
        {!showTable && status === 'running' ? (
          <div className="flex h-full items-center justify-center px-4 text-xs text-muted-foreground">
            {runningLabel}
          </div>
        ) : null}
        {status === 'risk_blocked' ? (
          <div className="flex h-full flex-col items-center justify-center gap-4 p-6">
            <div className="flex items-center gap-2 text-amber-600 dark:text-amber-400">
              <AlertTriangleIcon className="size-5" />
              <span className="text-sm font-medium">{riskTitle}</span>
            </div>
            <p className="text-center text-sm text-muted-foreground">{riskReason}</p>
            <div className="flex gap-2">
              <Button size="sm" variant="outline" onClick={onCancel}>
                {cancelLabel}
              </Button>
              <Button size="sm" onClick={onSendToAi}>
                {sendToAiLabel}
              </Button>
            </div>
          </div>
        ) : null}
        {status === 'error' ? (
          <div className="flex h-full items-center justify-center px-4 text-xs text-destructive">
            {errorLabel}
          </div>
        ) : null}
      </CardContent>
    </Card>
  )
}
