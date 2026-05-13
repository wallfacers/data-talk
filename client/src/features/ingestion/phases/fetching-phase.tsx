import { Loader2 } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'
import type { IngestionJobView } from '../api/ingestion-api'

interface FetchingPhaseProps {
  job: IngestionJobView
}

export function FetchingPhase({ job }: FetchingPhaseProps) {
  const { t } = useI18n()
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-12 text-muted-foreground">
      <Loader2 className="size-8 animate-spin text-primary" />
      <p className="text-sm">{t('ingestion.job.phase.fetching')}</p>
      {job.sourceUrl && (
        <p className="text-xs text-muted-foreground max-w-[400px] truncate" title={job.sourceUrl}>
          {job.sourceUrl}
        </p>
      )}
    </div>
  )
}
