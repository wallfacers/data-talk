import { Loader2 } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
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
        <Tooltip>
          <TooltipTrigger render={<p className="text-xs text-muted-foreground max-w-[400px] truncate" />}>
            {job.sourceUrl}
          </TooltipTrigger>
          <TooltipContent side="bottom" sideOffset={4}>{job.sourceUrl}</TooltipContent>
        </Tooltip>
      )}
    </div>
  )
}
