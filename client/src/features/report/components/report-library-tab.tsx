import { useReportList } from '../api'
import { useReportStore } from '../store'
import { useStageStore } from '@/stores/stage-store'
import { useI18n } from '@/i18n/use-i18n'

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

  const onOpen = (reportId: string, title: string) => {
    setSelected(reportId)
    openTab({
      tabId: `report-viewer:${reportId}`,
      type: 'report_viewer',
      title,
      payload: { reportId },
      createdAt: Date.now(),
    })
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-full text-[var(--dt-muted-foreground)]">
        {t('report.library.loading')}
      </div>
    )
  }

  if (!reports || reports.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-full text-[var(--dt-muted-foreground)] gap-2">
        <div className="text-[var(--dt-fg-strong)] text-base">{t('report.library.empty.title')}</div>
        <div className="text-sm">{t('report.library.empty.hint')}</div>
      </div>
    )
  }

  return (
    <div className="h-full overflow-y-auto bg-[var(--dt-bg)]">
      <header className="sticky top-0 z-10 px-4 py-3 border-b border-[var(--dt-border)] bg-[var(--dt-bg)]">
        <div className="text-[var(--dt-fg-strong)] font-medium">{t('report.library.title')}</div>
      </header>
      <ul className="divide-y divide-[var(--dt-border)]">
        {reports.map((r) => {
          const isSelected = selected === r.id
          const ts = new Date(r.generatedAt)
          const tsLabel = `${ts.getFullYear()}-${(ts.getMonth() + 1).toString().padStart(2, '0')}-${ts.getDate().toString().padStart(2, '0')} ${ts.getHours().toString().padStart(2, '0')}:${ts.getMinutes().toString().padStart(2, '0')}`
          return (
            <li
              key={r.id}
              className={`px-4 py-3 cursor-pointer hover:bg-[var(--dt-bg-soft)] ${
                isSelected ? 'border-l-2 border-[var(--dt-accent)]' : 'border-l-2 border-transparent'
              }`}
              onClick={() => onOpen(r.id, r.title)}
            >
              <div className="flex items-center justify-between gap-2">
                <div className="text-[var(--dt-fg-strong)] font-medium truncate flex-1">
                  {r.title}
                  {r.groupSize && r.groupSize > 1 ? (
                    <span className="ml-2 text-xs px-1.5 py-0.5 rounded-sm border border-[var(--dt-border)] text-[var(--dt-muted-foreground)]">
                      v{r.version} / {r.groupSize}
                    </span>
                  ) : null}
                </div>
              </div>
              {r.subtitle ? (
                <div className="text-[var(--dt-muted-foreground)] text-xs truncate mt-0.5">{r.subtitle}</div>
              ) : null}
              <div className="text-[var(--dt-muted-foreground)] text-xs mt-1 flex items-center gap-3">
                <span>{tsLabel}</span>
                <span>·</span>
                <span>{r.templateId}</span>
                {r.pdfStatus !== 'ready' ? (
                  <span className="text-[var(--dt-warning)]">PDF: {r.pdfStatus}</span>
                ) : null}
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
