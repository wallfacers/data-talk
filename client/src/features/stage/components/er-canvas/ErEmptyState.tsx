import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'

export interface ErEmptyStateProps {
  reason: 'dialect_unsupported' | 'empty_selection' | 'empty_designer' | 'oversized'
  dialect?: string
  actionLabel?: string
  onAction?: () => void
}

export function ErEmptyState({ reason, dialect, actionLabel, onAction }: ErEmptyStateProps) {
  const { t } = useI18n()
  const normalizedDialect = dialect?.toLowerCase()
  const message = (() => {
    if (reason === 'dialect_unsupported') {
      if (normalizedDialect === 'oracle' || normalizedDialect === 'sqlserver' || normalizedDialect === 'mssql') {
        return t('erCanvas.empty.oracle')
      }
      if (normalizedDialect === 'sqlite') return t('erCanvas.empty.sqlite')
      return t('erCanvas.empty.unsupported')
    }
    if (reason === 'empty_selection') return t('erCanvas.empty.selection')
    if (reason === 'empty_designer') return t('erCanvas.empty.designer')
    if (reason === 'oversized') return t('erCanvas.empty.oversized')
    return t('erCanvas.empty.unknown')
  })()

  return (
    <div className="flex h-full w-full items-center justify-center bg-bg-canvas">
      <div className="max-w-md px-6 py-8 text-center">
        <p className="text-sm text-text-muted">{message}</p>
        {actionLabel && onAction ? (
          <div className="mt-4">
            <Button type="button" size="sm" onClick={onAction}>
              {actionLabel}
            </Button>
          </div>
        ) : null}
      </div>
    </div>
  )
}
