import { Loader2 } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'
import type { IngestionJobView } from '../api/ingestion-api'

interface WritingPhaseProps {
  job: IngestionJobView
}

export function WritingPhase({ job }: WritingPhaseProps) {
  const { t } = useI18n()
  const progress = job.rowCount && job.rowCount > 0 && job.rowsInserted != null
    ? Math.round((job.rowsInserted / job.rowCount) * 100)
    : 0

  return (
    <div className="flex flex-col items-center justify-center gap-4 py-12">
      <Loader2 className="h-8 w-8 animate-spin text-accent-primary" />
      <p className="text-ui-md">{t('ingestion.job.phase.writing')}</p>
      <div className="w-64 h-2 rounded-full bg-bg-subtle overflow-hidden">
        <div
          className="h-full bg-accent-primary rounded-full transition-all duration-300"
          style={{ width: `${progress}%` }}
        />
      </div>
      <p className="text-ui-xs text-text-muted">
        {job.rowsInserted?.toLocaleString() ?? 0} / {job.rowCount?.toLocaleString() ?? '?'} rows
      </p>
      {job.targetTable && (
        <p className="text-ui-xs text-text-soft">
          → {job.targetSchema ? `${job.targetSchema}.` : ''}{job.targetTable}
        </p>
      )}
    </div>
  )
}
