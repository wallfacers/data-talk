import { useReportList } from '../api'
import { useReportStore } from '../store'
import { useStageStore } from '@/stores/stage-store'
import { useI18n } from '@/i18n/use-i18n'
import { TabContentLoader } from '@/features/stage/components/tab-content-loader'

export interface ReportLibraryTabProps {
  workspaceId: string
}

export function ReportLibraryTab({ workspaceId }: ReportLibraryTabProps) {
  const { t } = useI18n()
  const effectiveWsId = workspaceId || 'default'
  const { data: reports, isLoading } = useReportList(effectiveWsId)
  const selected = useReportStore((s) => s.selectedReportId)
  const setSelected = useReportStore((s) => s.setSelected)
  const openTab = useStageStore((s) => s.openTab)
  const focusTab = useStageStore((s) => s.focusTab)
  const tabs = useStageStore((s) => s.tabs)

  const onOpen = (reportId: string, title: string) => {
    setSelected(reportId)
    const tabId = `report-viewer:${reportId}`
    const existing = tabs.find((t) => t.tabId === tabId)
    if (existing) {
      focusTab(tabId)
    } else {
      openTab({
        tabId,
        type: 'report_viewer',
        title,
        payload: { reportId },
        createdAt: Date.now(),
      })
    }
  }

  if (isLoading) {
    return <TabContentLoader />
  }

  if (!reports || reports.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-full text-text-muted gap-2">
        <div className="text-text-strong text-base">{t('report.library.empty.title')}</div>
        <div className="text-sm">{t('report.library.empty.hint')}</div>
      </div>
    )
  }

  return (
    <div className="h-full overflow-y-auto bg-bg-canvas">
      <header className="sticky top-0 z-10 flex items-center min-h-11 px-3 py-2 border-b border-border/50 bg-bg-soft">
        <div className="text-text-strong font-medium">{t('report.library.title')}</div>
      </header>
      <ul className="divide-y divide-border-subtle">
        {reports.map((r) => {
          const isSelected = selected === r.id
          const ts = new Date(r.generatedAt)
          const tsLabel = `${ts.getFullYear()}-${(ts.getMonth() + 1).toString().padStart(2, '0')}-${ts.getDate().toString().padStart(2, '0')} ${ts.getHours().toString().padStart(2, '0')}:${ts.getMinutes().toString().padStart(2, '0')}`
          return (
            <li
              key={r.id}
              className={[
                'relative px-4 py-3 cursor-pointer',
                'transition-[background,color] duration-[180ms] ease-[var(--easing-standard)]',
                isSelected
                  ? 'bg-interaction-selected text-text-strong'
                  : 'hover:bg-interaction-hover text-text-base',
              ].join(' ')}
              onClick={() => onOpen(r.id, r.title)}
            >
              {isSelected && (
                <span aria-hidden className="absolute inset-y-1 left-0 w-0.5 rounded bg-accent-primary" />
              )}
              <div className="flex items-center justify-between gap-2">
                <div className="text-text-strong font-medium truncate flex-1">
                  {r.title}
                  {r.groupSize && r.groupSize > 1 ? (
                    <span className="ml-2 text-xs px-1.5 py-0.5 rounded-sm border border-border-subtle text-text-muted">
                      v{r.version} / {r.groupSize}
                    </span>
                  ) : null}
                </div>
              </div>
              {r.subtitle ? (
                <div className="text-text-muted text-xs truncate mt-0.5">{r.subtitle}</div>
              ) : null}
              <div className="text-text-muted text-xs mt-1 flex items-center gap-3">
                <span>{tsLabel}</span>
                <span>·</span>
                <span>{r.templateId}</span>
                {r.pdfStatus !== 'ready' ? (
                  <span className="text-accent-warn">PDF: {r.pdfStatus}</span>
                ) : null}
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
