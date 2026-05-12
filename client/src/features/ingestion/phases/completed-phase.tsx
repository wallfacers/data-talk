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
      <CheckCircle2 className="h-8 w-8 text-status-success" />
      <p className="text-ui-md font-medium text-text-strong">{t('ingestion.job.phase.completed')}</p>
      <div className="text-ui-sm text-text-muted flex flex-col items-center gap-1">
        {job.targetTable && (
          <span>
            {job.rowsInserted?.toLocaleString() ?? 0} rows written to{' '}
            {job.targetSchema ? `${job.targetSchema}.` : ''}{job.targetTable}
          </span>
        )}
        {job.bytesFetched != null && <span>{formatBytes(job.bytesFetched)} fetched</span>}
        {job.completedAt && (
          <span className="text-ui-xs text-text-soft">
            Completed at {new Date(job.completedAt).toLocaleString()}
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
