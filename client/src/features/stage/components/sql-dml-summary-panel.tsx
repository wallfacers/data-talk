import type { SqlExecuteResultItem } from '@/services/api/sql'

type SqlDmlSummaryPanelProps = {
  result: SqlExecuteResultItem
}

export function SqlDmlSummaryPanel({ result }: SqlDmlSummaryPanelProps) {
  return (
    <div className="flex h-full flex-col justify-center gap-2 px-4 py-5 text-sm">
      <p className="font-medium text-foreground">{result.title}</p>
      <p className="text-muted-foreground">{result.statementText}</p>
      <p className="text-muted-foreground">
        {result.affectedRows ?? 0} rows affected · {result.executionMs}ms
      </p>
    </div>
  )
}
