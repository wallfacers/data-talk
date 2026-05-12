import { cn } from '@/lib/utils'

interface DdlPreviewProps {
  ddl: string | null
  className?: string
}

export function DdlPreview({ ddl, className }: DdlPreviewProps) {
  if (!ddl) return null
  return (
    <div data-testid="ingestion-ddl-preview" className={cn('rounded-md border border-border-default bg-bg-subtle p-3 overflow-auto max-h-[200px]', className)}>
      <pre className="text-ui-xs font-mono text-text-base whitespace-pre-wrap break-all">{ddl}</pre>
    </div>
  )
}
