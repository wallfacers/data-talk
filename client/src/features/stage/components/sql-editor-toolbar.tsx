import type { ReactNode } from 'react'
import { PlayIcon, SquareIcon, SparklesIcon, SearchCodeIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { SqlLimitSelect, type SqlLimitValue } from './sql-limit-select'

type SqlEditorToolbarProps = {
  contextChip: ReactNode
  canRun: boolean
  isRunning: boolean
  onRun: () => void
  onCancel: () => void
  onFormat: () => void
  canExplain?: boolean
  onExplain?: () => void
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
  canExplain,
  onExplain,
  limit,
  onLimitChange,
}: SqlEditorToolbarProps) {
  const { t } = useI18n()

  return (
    <div
      data-testid="sql-editor-toolbar"
      className="flex min-h-11 flex-wrap items-center justify-between gap-3 border-b border-border/50 px-3 py-2"
    >
      <div className="flex items-center gap-2">
        <Button
          size="sm"
          variant={isRunning ? 'destructive' : 'default'}
          onClick={isRunning ? onCancel : onRun}
          disabled={isRunning ? false : !canRun}
          className="min-w-20 justify-center"
        >
          {isRunning ? <SquareIcon /> : <PlayIcon />}
          {isRunning ? t('stage.toolbar.cancel') : t('stage.toolbar.run')}
        </Button>
        <Button size="sm" variant="ghost" onClick={onFormat}>
          <SparklesIcon />
          {t('stage.toolbar.format')}
        </Button>
        {onExplain && (
          <Button
            size="sm"
            variant="ghost"
            onClick={onExplain}
            disabled={!canExplain}
            aria-label={t('stage.toolbar.explain')}
          >
            <SearchCodeIcon />
            {t('stage.toolbar.explain')}
          </Button>
        )}
      </div>

      <div className="flex flex-wrap items-center justify-end gap-2">
        {contextChip}
        <SqlLimitSelect value={limit} onValueChange={onLimitChange} />
      </div>
    </div>
  )
}
