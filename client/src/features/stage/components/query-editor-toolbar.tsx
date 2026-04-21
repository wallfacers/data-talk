import { PlayIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

type QueryEditorToolbarProps = {
  entryLabel: string
  connectionLabel: string
  detailLabel: string | null
  contextNotice: string | null
  runLabel: string
  isRunning: boolean
  showRunButton: boolean
  onRun: () => void
}

export function QueryEditorToolbar({
  entryLabel,
  connectionLabel,
  detailLabel,
  contextNotice,
  runLabel,
  isRunning,
  showRunButton,
  onRun,
}: QueryEditorToolbarProps) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/60 px-4 py-3">
      <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
        <Badge variant="secondary">{entryLabel}</Badge>
        <span>{connectionLabel}</span>
        {detailLabel ? <span>{detailLabel}</span> : null}
        {contextNotice ? <Badge variant="outline">{contextNotice}</Badge> : null}
      </div>
      {showRunButton ? (
        <Button size="sm" onClick={onRun} disabled={isRunning}>
          <PlayIcon className="size-3.5" />
          {runLabel}
        </Button>
      ) : null}
    </div>
  )
}
