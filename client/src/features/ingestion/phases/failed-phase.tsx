import { AlertCircle } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'
import type { IngestionJobView } from '../api/ingestion-api'

interface FailedPhaseProps {
  job: IngestionJobView
}

export function FailedPhase({ job }: FailedPhaseProps) {
  const { t } = useI18n()
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-12">
      <AlertCircle className="size-8 text-status-danger" />
      <p className="text-sm font-medium">{t('ingestion.job.phase.failed')}</p>
      {job.errorMessage && (
        <div className="max-w-[500px] rounded-md border border-border bg-muted p-3">
          <p className="text-xs whitespace-pre-wrap break-all">{job.errorMessage}</p>
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
