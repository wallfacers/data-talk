import type { ReactNode } from 'react'
import { MoreHorizontalIcon, PlayIcon, SquareIcon, SparklesIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { SqlLimitSelect, type SqlLimitValue } from './sql-limit-select'

type SqlEditorToolbarProps = {
  entryLabel?: string | null
  connectionLabel?: string | null
  detailLabel?: string | null
  contextNotice?: string | null
  contextChip: ReactNode
  canRun: boolean
  isRunning: boolean
  onRun: () => void
  onCancel: () => void
  onFormat: () => void
  onSave: () => void
  limit: SqlLimitValue
  onLimitChange: (value: SqlLimitValue) => void
}

export function SqlEditorToolbar({
  entryLabel,
  connectionLabel,
  detailLabel,
  contextNotice,
  contextChip,
  canRun,
  isRunning,
  onRun,
  onCancel,
  onFormat,
  onSave,
  limit,
  onLimitChange,
}: SqlEditorToolbarProps) {
  return (
    <div
      data-testid="sql-editor-toolbar"
      className="flex flex-wrap items-center justify-between gap-3 border-b border-border/50 px-3 py-2.5"
    >
      <div className="flex min-w-0 flex-wrap items-center gap-2 text-xs text-muted-foreground">
        {entryLabel ? <Badge variant="secondary">{entryLabel}</Badge> : null}
        {connectionLabel ? <span className="truncate">{connectionLabel}</span> : null}
        {detailLabel ? <span className="truncate">{detailLabel}</span> : null}
        {contextNotice ? <Badge variant="outline">{contextNotice}</Badge> : null}
      </div>

      <div className={cn('flex flex-wrap items-center justify-end gap-2')}>
        {contextChip}
        <SqlLimitSelect value={limit} onValueChange={onLimitChange} />
        <Button size="sm" variant="ghost" onClick={onFormat}>
          <SparklesIcon />
          Format
        </Button>
        <Button size="sm" variant="outline" onClick={onSave}>
          Save
        </Button>
        <Button size="sm" onClick={onRun} disabled={!canRun || isRunning}>
          <PlayIcon />
          Run
        </Button>
        {isRunning ? (
          <Button size="sm" variant="destructive" onClick={onCancel}>
            <SquareIcon />
            Cancel
          </Button>
        ) : null}
        <Button size="sm" variant="ghost" disabled aria-label="More actions">
          <MoreHorizontalIcon />
        </Button>
      </div>
    </div>
  )
}
