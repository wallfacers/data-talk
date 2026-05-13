import { CheckCircle2 } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'
import type { IngestionJobView } from '../api/ingestion-api'

interface CompletedPhaseProps {
  job: IngestionJobView
}

export function CompletedPhase({ job }: CompletedPhaseProps) {
  const { t } = useI18n()
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-12">
      <CheckCircle2 className="size-8 text-status-success" />
      <p className="text-sm font-medium">{t('ingestion.job.phase.completed')}</p>
      <div className="text-xs text-muted-foreground flex flex-col items-center gap-1">
        {job.targetTable && (
          <span>
            {t('ingestion.completed.rowsWrittenTo', {
              count: job.rowsInserted?.toLocaleString() ?? 0,
              table: `${job.targetSchema ? `${job.targetSchema}.` : ''}${job.targetTable}`,
            })}
          </span>
        )}
        {job.bytesFetched != null && <span>{t('ingestion.completed.bytesFetched', { bytes: formatBytes(job.bytesFetched) })}</span>}
        {job.completedAt && (
          <span className="text-xs text-muted-foreground">
            {t('ingestion.completed.completedAt', { date: new Date(job.completedAt).toLocaleString() })}
          </span>
        )}
      </div>
    </div>
  )
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
