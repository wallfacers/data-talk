import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import type { SqlWorkbenchExecuteStatus } from '../stores/sql-workbench-store'

type SqlWorkbenchStatusBarProps = {
  status: SqlWorkbenchExecuteStatus
  cursor: { line: number; column: number }
  riskReason?: string | null
  errorMessage?: string | null
}

const STATUS_META: Record<SqlWorkbenchExecuteStatus, { label: string; className: string }> = {
  idle: { label: 'Idle', className: 'bg-secondary text-secondary-foreground' },
  running: { label: 'Running', className: 'bg-primary text-primary-foreground' },
  success: { label: 'Success', className: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300' },
  error: { label: 'Error', className: 'bg-destructive/10 text-destructive' },
  risk_blocked: { label: 'Risk blocked', className: 'bg-amber-500/15 text-amber-700 dark:text-amber-300' },
}

export function SqlWorkbenchStatusBar({
  status,
  cursor,
  riskReason,
  errorMessage,
}: SqlWorkbenchStatusBarProps) {
  const meta = STATUS_META[status]
  const detail = status === 'error' ? errorMessage : status === 'risk_blocked' ? riskReason : null

  return (
    <div
      data-testid="sql-workbench-status-bar"
      className="flex min-h-10 items-center justify-between gap-3 border-t border-border/50 bg-muted/15 px-3 py-2 text-xs"
    >
      <div className="flex min-w-0 items-center gap-2">
        <Badge variant="outline" className={cn('border-transparent', meta.className)}>
          {meta.label}
        </Badge>
        {detail ? <span className="truncate text-muted-foreground">{detail}</span> : null}
      </div>
      <div className="shrink-0 text-muted-foreground">
        Ln {cursor.line}, Col {cursor.column}
      </div>
    </div>
  )
}
