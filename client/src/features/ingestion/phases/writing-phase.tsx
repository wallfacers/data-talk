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
      <Loader2 className="size-8 animate-spin text-primary" />
      <p className="text-sm">{t('ingestion.job.phase.writing')}</p>
      <div className="w-64 h-2 rounded-full bg-muted overflow-hidden">
        <div
          className="h-full bg-primary rounded-full transition-all duration-300"
          style={{ width: `${progress}%` }}
        />
      </div>
      <p className="text-xs text-muted-foreground">
        {t('ingestion.writing.progressRows', {
          inserted: job.rowsInserted?.toLocaleString() ?? 0,
          total: job.rowCount?.toLocaleString() ?? '?',
        })}
      </p>
      {job.targetTable && (
        <p className="text-xs text-muted-foreground">
          {t('ingestion.writing.writingTo', {
            table: `${job.targetSchema ? `${job.targetSchema}.` : ''}${job.targetTable}`,
          })}
        </p>
      )}
    </div>
  )
}
