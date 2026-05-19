import type { ReactNode } from 'react'
import { PlayIcon, SquareIcon, SparklesIcon, SearchCodeIcon, FileUpIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useI18n } from '@/i18n/use-i18n'

type SqlEditorToolbarProps = {
  contextControls: ReactNode
  canRun: boolean
  isRunning: boolean
  onRun: () => void
  onCancel: () => void
  onFormat: () => void
  canExplain?: boolean
  onExplain?: () => void
  onImportFile?: () => void
}

export function SqlEditorToolbar({
  contextControls,
  canRun,
  isRunning,
  onRun,
  onCancel,
  onFormat,
  canExplain,
  onExplain,
  onImportFile,
}: SqlEditorToolbarProps) {
  const { t } = useI18n()

  return (
    <div
      data-testid="sql-editor-toolbar"
      className="flex min-h-11 flex-wrap items-center justify-between gap-3 border-b border-border/50 bg-bg-soft px-3 py-2"
    >
      <div className="flex items-center gap-1">
        <Tooltip>
          <TooltipTrigger render={
            <Button
              size="sm"
              variant={isRunning ? 'destructive' : 'default'}
              onClick={isRunning ? onCancel : onRun}
              disabled={isRunning ? false : !canRun}
              aria-label={isRunning ? t('stage.toolbar.cancel') : t('stage.toolbar.run')}
              className="justify-center"
            >
              {isRunning ? <SquareIcon /> : <PlayIcon />}
            </Button>
          } />
          <TooltipContent side="bottom" sideOffset={4}>
            {isRunning ? t('stage.toolbar.cancel') : t('stage.toolbar.run')}
          </TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger render={
            <Button size="sm" variant="ghost" onClick={onFormat} aria-label={t('stage.toolbar.format')}>
              <SparklesIcon />
            </Button>
          } />
          <TooltipContent side="bottom" sideOffset={4}>
            {t('stage.toolbar.format')}
          </TooltipContent>
        </Tooltip>
        {onExplain && (
          <Tooltip>
            <TooltipTrigger render={
              <Button
                size="sm"
                variant="ghost"
                onClick={onExplain}
                disabled={!canExplain}
                aria-label={t('stage.toolbar.explain')}
              >
                <SearchCodeIcon />
              </Button>
            } />
            <TooltipContent side="bottom" sideOffset={4}>
              {t('stage.toolbar.explain')}
            </TooltipContent>
          </Tooltip>
        )}
        {onImportFile && (
          <Tooltip>
            <TooltipTrigger render={
              <Button
                size="sm"
                variant="ghost"
                onClick={onImportFile}
                aria-label={t('stage.toolbar.importFile')}
              >
                <FileUpIcon />
              </Button>
            } />
            <TooltipContent side="bottom" sideOffset={4}>
              {t('stage.toolbar.importFile')}
            </TooltipContent>
          </Tooltip>
        )}
      </div>

      <div className="flex flex-wrap items-center justify-end gap-2">
        {contextControls}
      </div>
    </div>
  )
}
