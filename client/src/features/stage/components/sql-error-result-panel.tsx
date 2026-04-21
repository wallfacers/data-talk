import type { SqlExecuteResultItem } from '@/services/api/sql'

type SqlErrorResultPanelProps = {
  result: SqlExecuteResultItem
}

export function SqlErrorResultPanel({ result }: SqlErrorResultPanelProps) {
  return (
    <div className="flex h-full flex-col justify-center gap-2 px-4 py-5">
      <p className="text-sm font-medium text-destructive">
        {result.errorMessage ?? 'SQL execution failed'}
      </p>
      <p className="text-xs text-muted-foreground">{result.statementText}</p>
    </div>
  )
}
