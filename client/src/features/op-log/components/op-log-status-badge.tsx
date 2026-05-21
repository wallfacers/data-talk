import { useI18n } from '@/i18n/use-i18n'
import type { MessageKey } from '@/i18n/messages'

const STATUS_CONFIG: Record<string, { labelKey: MessageKey; textClass: string; bgClass: string }> = {
  pending: { labelKey: 'opLog.status.pending', textClass: 'text-status-info', bgClass: 'bg-status-infoSurface' },
  active: { labelKey: 'opLog.status.active', textClass: 'text-status-success', bgClass: 'bg-status-success-surface' },
  undone: { labelKey: 'opLog.status.undone', textClass: 'text-text-muted', bgClass: 'bg-bg-subtle' },
  expired: { labelKey: 'opLog.status.expired', textClass: 'text-status-warning', bgClass: 'bg-status-warning-surface' },
}

export function OpLogStatusBadge({ status }: { status: string }) {
  const { t } = useI18n()
  const config = STATUS_CONFIG[status] ?? STATUS_CONFIG.pending
  return (
    <span className={`inline-flex items-center rounded px-1.5 py-0.5 text-xs font-medium ${config.textClass} ${config.bgClass}`}>
      {t(config.labelKey)}
    </span>
  )
}
