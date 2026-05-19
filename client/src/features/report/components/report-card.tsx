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
  const openStage = useStageStore((s) => s.openStage)

  const onOpen = () => {
    openTab({
      tabId: `report-viewer:${reportId}`,
      type: 'report_viewer',
      title: title || t('report.viewer.titleFallback'),
      payload: { reportId },
      createdAt: Date.now(),
    })
    openStage()
  }

  const tsLabel = typeof generatedAt === 'number' ? relativeTime(generatedAt, t) : ''

  return (
    <div className="rounded-md border border-[var(--dt-border)] bg-[var(--dt-bg-soft)] p-3 my-2 flex items-center gap-3">
      <div className="flex-1 min-w-0">
        <div className="text-[var(--dt-fg-strong)] font-medium truncate">{title}</div>
        {subtitle ? (
          <div className="text-[var(--dt-muted-foreground)] text-xs truncate">{subtitle}</div>
        ) : null}
        {tsLabel ? <div className="text-[var(--dt-muted-foreground)] text-xs">{tsLabel}</div> : null}
      </div>
      <button
        type="button"
        onClick={onOpen}
        className="px-3 py-1.5 rounded-sm text-sm bg-[var(--dt-accent)] text-[var(--dt-accent-foreground)] hover:opacity-90"
      >
        {t('report.card.open')}
      </button>
    </div>
  )
}
