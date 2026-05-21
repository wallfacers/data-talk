import { BanIcon, DatabaseIcon, SquareMousePointerIcon, ZoomOutIcon, type LucideIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'

export type ErEmptyReason = 'dialect_unsupported' | 'empty_selection' | 'empty_designer' | 'oversized'

export interface ErEmptyStateProps {
  reason: ErEmptyReason
  dialect?: string
  actionLabel?: string
  onAction?: () => void
  tabId?: string
}

const REASON_ICONS: Record<ErEmptyReason, LucideIcon> = {
  dialect_unsupported: BanIcon,
  empty_selection: SquareMousePointerIcon,
  empty_designer: DatabaseIcon,
  oversized: ZoomOutIcon,
}

function resolveCopy(reason: ErEmptyReason, dialect: string | undefined, t: ReturnType<typeof useI18n>['t']) {
  const normalizedDialect = dialect?.toLowerCase()

  if (reason === 'dialect_unsupported') {
    if (normalizedDialect === 'oracle' || normalizedDialect === 'sqlserver' || normalizedDialect === 'mssql') {
      return {
        title: t('erCanvas.empty.unsupported.title'),
        body: t('erCanvas.empty.oracle'),
      }
    }
    return {
      title: t('erCanvas.empty.unsupported.title'),
      body: t('erCanvas.empty.unsupported'),
    }
  }

  if (reason === 'empty_selection') {
    return {
      title: t('erCanvas.empty.selection.title'),
      body: t('erCanvas.empty.selection'),
    }
  }

  if (reason === 'empty_designer') {
    return {
      title: t('erCanvas.empty.designer.title'),
      body: t('erCanvas.empty.designer'),
    }
  }

  return {
    title: t('erCanvas.empty.oversized.title'),
    body: t('erCanvas.empty.oversized'),
  }
}

export function ErEmptyState({ reason, dialect, actionLabel, onAction, tabId }: ErEmptyStateProps) {
  const { t } = useI18n()
  const { title, body } = resolveCopy(reason, dialect, t)
  const Icon = REASON_ICONS[reason]

  return (
    <div className="flex h-full w-full items-center justify-center bg-bg-canvas px-6 py-8" data-er-tab-id={tabId}>
      <div className="flex max-w-md flex-col items-center text-center">
        <div className="flex h-12 w-12 items-center justify-center rounded-lg border border-border-subtle bg-bg-subtle text-text-muted">
          <Icon
            aria-hidden="true"
            className="h-6 w-6"
            data-er-empty-reason={reason}
            data-testid="er-empty-icon"
          />
        </div>
        <h2 className="mt-4 text-base font-semibold text-text-strong">{title}</h2>
        <p className="mt-2 text-sm leading-6 text-text-muted">{body}</p>
        {actionLabel && onAction ? (
          <Button
            type="button"
            size="sm"
            variant="outline"
            className="mt-5 border-accent-primary text-accent-primary hover:bg-accent-primary-surface"
            onClick={onAction}
          >
            {actionLabel}
          </Button>
        ) : null}
      </div>
    </div>
  )
}
