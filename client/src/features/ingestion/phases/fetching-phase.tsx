import { Loader2 } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'
import type { IngestionJobView } from '../api/ingestion-api'

interface FetchingPhaseProps {
  job: IngestionJobView
}

export function FetchingPhase({ job }: FetchingPhaseProps) {
  const { t } = useI18n()
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-12 text-text-muted">
      <Loader2 className="h-8 w-8 animate-spin text-accent-primary" />
      <p className="text-ui-md">{t('ingestion.job.phase.fetching')}</p>
      {job.sourceUrl && (
        <p className="text-ui-xs text-text-soft max-w-[400px] truncate" title={job.sourceUrl}>
          {job.sourceUrl}
        </p>
      )}
    </div>
  )
}
