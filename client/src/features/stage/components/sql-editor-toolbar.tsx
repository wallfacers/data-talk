import type { ReactNode } from 'react'
import { PlayIcon, SquareIcon, SparklesIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { SqlLimitSelect, type SqlLimitValue } from './sql-limit-select'

type SqlEditorToolbarProps = {
  contextChip: ReactNode
  canRun: boolean
  isRunning: boolean
  onRun: () => void
  onCancel: () => void
  onFormat: () => void
  limit: SqlLimitValue
  onLimitChange: (value: SqlLimitValue) => void
}

export function SqlEditorToolbar({
  contextChip,
  canRun,
  isRunning,
  onRun,
  onCancel,
  onFormat,
  limit,
  onLimitChange,
}: SqlEditorToolbarProps) {
  return (
    <div
      data-testid="sql-editor-toolbar"
      className="flex flex-wrap items-center justify-between gap-3 border-b border-border/50 px-3 py-2.5"
    >
      <div className="flex flex-wrap items-center gap-2">
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
        <Button size="sm" variant="ghost" onClick={onFormat}>
          <SparklesIcon />
          Format
        </Button>
      </div>

      <div className="flex flex-wrap items-center justify-end gap-2">
        {contextChip}
        <SqlLimitSelect value={limit} onValueChange={onLimitChange} />
      </div>
    </div>
  )
}
