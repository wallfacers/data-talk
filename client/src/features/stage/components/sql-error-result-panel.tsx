import type { SqlExecuteResultItem } from '@/services/api/sql'

type SqlErrorResultPanelProps = {
  result: SqlExecuteResultItem
}

export function SqlErrorResultPanel({ result }: SqlErrorResultPanelProps) {
  return (
    <div className="flex h-full items-center justify-center px-6 py-6 text-center">
      <p className="text-sm font-medium text-destructive">
        {result.errorMessage ?? 'SQL execution failed'}
      </p>
    </div>
  )
}
