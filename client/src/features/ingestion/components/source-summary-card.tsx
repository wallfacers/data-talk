import { Globe, Clock, HardDrive, Table2 } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'

interface SourceSummaryCardProps {
  sourceUrl: string
  payloadFormat: string | null
  bytesFetched: number | null
  rowCount: number | null
}

export function SourceSummaryCard({ sourceUrl, payloadFormat, bytesFetched, rowCount }: SourceSummaryCardProps) {
  const { t } = useI18n()

  return (
    <div data-testid="ingestion-source-summary" className="rounded-md border border-border/50 bg-muted/50 p-3">
      <div className="flex items-start gap-2 mb-2">
        <Globe className="size-3.5 shrink-0 mt-0.5 text-muted-foreground" />
        <span className="text-xs break-all text-foreground select-all">{sourceUrl}</span>
      </div>
      <div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
        {payloadFormat && (
          <span className="flex items-center gap-1.5">
            <Table2 className="size-3.5" />
            <span className="uppercase font-medium">{payloadFormat}</span>
          </span>
        )}
        {bytesFetched != null && (
          <span className="flex items-center gap-1.5">
            <HardDrive className="size-3.5" />
            {formatBytes(bytesFetched)}
          </span>
        )}
        {rowCount != null && (
          <span className="flex items-center gap-1.5">
            <Clock className="size-3.5" />
            {rowCount.toLocaleString()} {t('ingestion.library.columns.rows').toLowerCase()}
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
