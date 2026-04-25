import { useEffect } from 'react'
import { createPortal } from 'react-dom'
import { ChartRenderer } from './chart-renderer'
import { useI18n } from '@/i18n/use-i18n'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'

type ChartExpandModalProps = {
  option: Record<string, unknown>
  onClose: () => void
}

function getModalChartHeight() {
  if (typeof window === 'undefined') return 520
  return Math.max(360, Math.floor(window.innerHeight * 0.65))
}

export function ChartExpandModal({ option, onClose }: ChartExpandModalProps) {
  const { t } = useI18n()

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  return createPortal(
    <div
      className="fixed inset-0 z-[300] flex items-center justify-center bg-[var(--dt-bg-overlay)] p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div className="flex max-h-[92vh] w-[92vw] max-w-6xl flex-col overflow-hidden rounded-xl border border-[var(--dt-border-subtle)] bg-[var(--dt-bg-panel)] shadow-2xl">
        <div className="flex items-center justify-between border-b border-[var(--dt-border-subtle)] px-4 py-2">
          <span className="font-mono text-[13px] leading-[18px] text-[var(--dt-text-muted)]">chart</span>
          <Tooltip>
            <TooltipTrigger
              render={
                <button
                  type="button"
                  aria-label={t('stage.close')}
                  onClick={onClose}
                  className="h-7 rounded-md px-2 text-sm text-[var(--dt-text-muted)] transition-colors hover:bg-[var(--dt-hover)] hover:text-[var(--dt-text-strong)]"
                >
                  ×
                </button>
              }
            />
            <TooltipContent>{t('stage.close')}</TooltipContent>
          </Tooltip>
        </div>
        <div className="min-h-0 flex-1 p-3">
          <ChartRenderer option={option} height={getModalChartHeight()} />
        </div>
      </div>
    </div>,
    document.body,
  )
}
