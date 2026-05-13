import { PlayIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

type SqlEditorHeaderProps = {
  entryLabel: string
  connectionLabel: string
  detailLabel: string | null
  contextNotice: string | null
  runLabel: string
  canRun: boolean
  isRunning: boolean
  onRun: () => void
}

export function SqlEditorHeader({
  entryLabel,
  connectionLabel,
  detailLabel,
  contextNotice,
  runLabel,
  canRun,
  isRunning,
  onRun,
}: SqlEditorHeaderProps) {
  return (
    <div className="flex min-h-11 flex-wrap items-center justify-between gap-3 border-b border-border/50 bg-bg-soft px-3 py-2">
      <div className="flex min-w-0 flex-wrap items-center gap-2 text-xs text-muted-foreground">
        <Badge variant="secondary">{entryLabel}</Badge>
        <span className="truncate">{connectionLabel}</span>
        {detailLabel ? <span className="truncate">{detailLabel}</span> : null}
        {contextNotice ? <Badge variant="outline">{contextNotice}</Badge> : null}
      </div>
      <Button
        size="sm"
        onClick={onRun}
        disabled={!canRun || isRunning}
      >
        <PlayIcon className="size-3.5" />
        {runLabel}
      </Button>
    </div>
  )
}
