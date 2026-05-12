import { Globe, Clock, HardDrive } from 'lucide-react'

interface SourceSummaryCardProps {
  sourceUrl: string
  payloadFormat: string | null
  bytesFetched: number | null
  rowCount: number | null
}

export function SourceSummaryCard({ sourceUrl, payloadFormat, bytesFetched, rowCount }: SourceSummaryCardProps) {
  return (
    <div data-testid="ingestion-source-summary" className="flex items-center gap-4 px-3 py-2 bg-bg-subtle rounded-md text-ui-sm">
      <span className="flex items-center gap-1.5 text-text-muted">
        <Globe className="h-3.5 w-3.5" />
        <span className="truncate max-w-[200px]" title={sourceUrl}>{sourceUrl}</span>
      </span>
      {payloadFormat && (
        <span className="px-1.5 py-0.5 rounded bg-bg-subtle text-text-muted text-ui-xs uppercase">{payloadFormat}</span>
      )}
      {bytesFetched != null && (
        <span className="flex items-center gap-1 text-text-muted">
          <HardDrive className="h-3.5 w-3.5" />
          {formatBytes(bytesFetched)}
        </span>
      )}
      {rowCount != null && (
        <span className="flex items-center gap-1 text-text-muted">
          <Clock className="h-3.5 w-3.5" />
          {rowCount.toLocaleString()} rows
        </span>
      )}
    </div>
  )
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
