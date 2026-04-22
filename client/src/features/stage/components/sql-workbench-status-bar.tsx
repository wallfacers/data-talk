import { Badge } from '@/components/ui/badge'
import { useI18n } from '@/i18n/use-i18n'
import { cn } from '@/lib/utils'
import type { SqlWorkbenchExecuteStatus } from '../stores/sql-workbench-store'

type SqlWorkbenchStatusBarProps = {
  status: SqlWorkbenchExecuteStatus
  riskReason?: string | null
  errorMessage?: string | null
}

export function SqlWorkbenchStatusBar({
  status,
  riskReason,
  errorMessage,
}: SqlWorkbenchStatusBarProps) {
  const { t } = useI18n()
  if (status === 'idle') return null

  const statusMeta: Record<SqlWorkbenchExecuteStatus, { label: string; className: string }> = {
    idle: { label: t('stage.status.idle'), className: 'bg-secondary text-secondary-foreground' },
    running: { label: t('stage.status.running'), className: 'bg-primary text-primary-foreground' },
    success: { label: t('stage.status.success'), className: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300' },
    error: { label: t('stage.status.error'), className: 'bg-destructive/10 text-destructive' },
    risk_blocked: {
      label: t('stage.status.riskBlocked'),
      className: 'bg-amber-500/15 text-amber-700 dark:text-amber-300',
    },
  }

  const meta = statusMeta[status]
  const detail = status === 'error' ? errorMessage : status === 'risk_blocked' ? riskReason : null

  return (
    <div
      data-testid="sql-workbench-status-bar"
      className="flex min-h-10 items-center gap-3 border-t border-border/50 bg-muted/15 px-3 py-2 text-xs"
    >
      <div className="flex min-w-0 items-center gap-2">
        <Badge variant="outline" className={cn('border-transparent', meta.className)}>
          {meta.label}
        </Badge>
        {detail ? <span className="truncate text-muted-foreground">{detail}</span> : null}
      </div>
    </div>
  )
}
