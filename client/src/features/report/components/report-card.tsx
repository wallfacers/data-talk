import { useStageStore } from '@/stores/stage-store'
import { useI18n } from '@/i18n/use-i18n'
import type { TranslationFn } from '@/i18n/provider'

export interface ReportCardProps {
  reportId: string
  title: string
  subtitle?: string | null
  generatedAt?: number
}

function relativeTime(ts: number, t: TranslationFn): string {
  const diff = Date.now() - ts
  if (diff < 60_000) return t('common.justNow')
  if (diff < 3_600_000) return t('common.minutesAgo', { n: Math.floor(diff / 60_000) })
  if (diff < 86_400_000) return t('common.hoursAgo', { n: Math.floor(diff / 3_600_000) })
  const d = new Date(ts)
  return `${d.getMonth() + 1}-${d.getDate()} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

export function ReportCard({ reportId, title, subtitle, generatedAt }: ReportCardProps) {
  const { t } = useI18n()
  const openTab = useStageStore((s) => s.openTab)
  const focusTab = useStageStore((s) => s.focusTab)
  const tabs = useStageStore((s) => s.tabs)
  const openStage = useStageStore((s) => s.openStage)

  const onOpen = () => {
    const tabId = `report-viewer:${reportId}`
    const existing = tabs.find((t) => t.tabId === tabId)
    if (existing) {
      focusTab(tabId)
    } else {
      openTab({
        tabId,
        type: 'report_viewer',
        title: title || t('report.viewer.titleFallback'),
        payload: { reportId },
        createdAt: Date.now(),
      })
    }
    openStage()
  }

  const tsLabel = typeof generatedAt === 'number' ? relativeTime(generatedAt, t) : ''

  return (
    <div className="rounded-md border border-border-subtle bg-bg-soft p-3 my-2 flex items-center gap-3">
      <div className="flex-1 min-w-0">
        <div className="text-text-strong font-medium truncate">{title}</div>
        {subtitle ? (
          <div className="text-text-muted text-xs truncate">{subtitle}</div>
        ) : null}
        {tsLabel ? <div className="text-text-muted text-xs">{tsLabel}</div> : null}
      </div>
      <button
        type="button"
        onClick={onOpen}
        className={[
          'px-3 py-1.5 rounded-md text-sm font-medium',
          'bg-accent-primary text-text-inverse',
          'hover:bg-accent-primary-hover',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
          'transition-[background] duration-[180ms] ease-[var(--easing-standard)]',
        ].join(' ')}
      >
        {t('report.card.open')}
      </button>
    </div>
  )
}
