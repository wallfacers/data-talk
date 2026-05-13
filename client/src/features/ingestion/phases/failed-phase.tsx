import { AlertCircle, Copy } from 'lucide-react'
import { useCallback, useState } from 'react'
import { useI18n } from '@/i18n/use-i18n'
import type { IngestionJobView } from '../api/ingestion-api'

interface FailedPhaseProps {
  job: IngestionJobView
}

const CLEANUP_HINT_PREFIXES = [
  'stopped by user',
  'server restarted while running',
  'task heartbeat lost',
]

function shouldShowCleanupSql(job: IngestionJobView): boolean {
  if (!job.errorMessage || !job.targetTable) return false
  return CLEANUP_HINT_PREFIXES.some((prefix) => job.errorMessage!.startsWith(prefix))
}

function buildDropSql(job: IngestionJobView): string {
  const qualified = job.targetSchema && job.targetTable
    ? `${job.targetSchema}.${job.targetTable}`
    : job.targetTable ?? ''
  return `DROP TABLE IF EXISTS ${qualified};`
}

export function FailedPhase({ job }: FailedPhaseProps) {
  const { t } = useI18n()
  const [copied, setCopied] = useState(false)

  const showCleanupSql = shouldShowCleanupSql(job)
  const dropSql = showCleanupSql ? buildDropSql(job) : ''

  const handleCopy = useCallback(async () => {
    if (!dropSql) return
    try {
      await navigator.clipboard.writeText(dropSql)
      setCopied(true)
      setTimeout(() => setCopied(false), 2_000)
    } catch {
      /* clipboard not available — user can select+copy manually */
    }
  }, [dropSql])

  return (
    <div className="flex flex-col items-center justify-center gap-3 py-12">
      <AlertCircle className="size-8 text-status-danger" />
      <p className="text-sm font-medium">{t('ingestion.job.phase.failed')}</p>
      {job.errorMessage && (
        <div className="max-w-[500px] rounded-md border border-border bg-muted p-3">
          <p className="text-xs whitespace-pre-wrap break-all">{job.errorMessage}</p>
        </div>
      )}
      {showCleanupSql && (
        <div className="flex max-w-[500px] flex-col gap-1.5" data-testid="ingestion-cleanup-sql-block">
          <p className="text-xs text-muted-foreground">{t('ingestion.dropTable.cleanupFailed')}</p>
          <div className="flex items-center gap-2 rounded-md border border-border bg-muted px-2 py-1.5">
            <code className="flex-1 font-mono text-xs">{dropSql}</code>
            <button
              type="button"
              data-testid="ingestion-cleanup-sql-copy"
              onClick={() => void handleCopy()}
              className="rounded p-1 text-muted-foreground hover:bg-background hover:text-foreground"
              aria-label="Copy"
            >
              <Copy className="size-3.5" />
            </button>
          </div>
          {copied && <span className="text-[11px] text-status-success">Copied</span>}
        </div>
      )}
      {job.sourceUrl && (
        <p className="text-xs text-muted-foreground max-w-[400px] truncate" title={job.sourceUrl}>
          {job.sourceUrl}
        </p>
      )}
    </div>
  )
}
