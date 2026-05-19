import { useEffect, useMemo, useRef, useState } from 'react'
import { useReport, useSystemStatus, reportDownloadUrl } from '../api'
import { useI18n } from '@/i18n/use-i18n'
import type { StageTab } from '@/stores/stage-store'
import { coordinator } from '@/features/stage/persistence/stage-persistence-bootstrap'
import { TabContentLoader } from '@/features/stage/components/tab-content-loader'

export interface ReportViewerTabProps {
  tab: StageTab
}

export function ReportViewerTab({ tab }: ReportViewerTabProps) {
  const { t } = useI18n()
  const iframeRef = useRef<HTMLIFrameElement>(null)
  const [iframeLoaded, setIframeLoaded] = useState(false)

  const payload = (tab.payload ?? {}) as { reportId?: string }
  const reportId = payload.reportId

  const [loading, setLoading] = useState(!reportId)

  const { data: report, isLoading } = useReport(reportId)
  const { data: systemStatus } = useSystemStatus()

  useEffect(() => {
    if (reportId) return
    coordinator.ensureHydrated(tab.tabId).catch(() => {
      setLoading(false)
    })
  }, [tab.tabId, reportId])

  useEffect(() => {
    if (reportId && loading) setLoading(false)
  }, [reportId, loading])

  const iframeSrc = useMemo(
    () => reportId ? `${reportDownloadUrl(reportId, 'html')}?_t=${Date.now()}` : '',
    [reportId],
  )

  if (loading) {
    return <TabContentLoader />
  }

  if (!reportId) {
    return (
      <div className="flex items-center justify-center h-full text-sm text-text-muted">
        {t('report.viewer.notFound')}
      </div>
    )
  }

  const chromiumReady = systemStatus?.chromiumReady ?? false
  const pdfStatus = report?.pdfStatus ?? 'processing'
  const mdStatus = report?.mdStatus ?? 'processing'

  const pdfDisabled = !chromiumReady || pdfStatus !== 'ready'
  const mdDisabled = !chromiumReady || mdStatus !== 'ready'

  const pdfTooltip = !chromiumReady
    ? t('report.tooltip.chromiumNotReady')
    : pdfStatus === 'processing'
      ? t('report.tooltip.derivingPdf')
      : pdfStatus === 'failed'
        ? `${t('report.tooltip.failed')}: ${report?.pdfFailReason ?? ''}`
        : ''

  const mdTooltip = !chromiumReady
    ? t('report.tooltip.chromiumNotReady')
    : mdStatus === 'processing'
      ? t('report.tooltip.derivingMd')
      : mdStatus === 'failed'
        ? `${t('report.tooltip.failed')}: ${report?.mdFailReason ?? ''}`
        : ''

  const downloadFile = (format: 'pdf' | 'md' | 'html') => {
    window.open(reportDownloadUrl(reportId, format), '_blank')
  }

  return (
    <div className="flex flex-col h-full bg-bg-canvas">
      <header className="flex items-center justify-between gap-3 min-h-11 px-3 py-2 border-b border-border/50 bg-bg-soft">
        <div className="flex-1 min-w-0">
          <div className="text-text-strong font-medium truncate">
            {report?.title ?? t('report.viewer.loading')}
          </div>
          {report?.subtitle ? (
            <div className="text-text-muted text-xs truncate">{report.subtitle}</div>
          ) : null}
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <button
            type="button"
            title={pdfTooltip}
            disabled={pdfDisabled}
            onClick={() => downloadFile('pdf')}
            className={[
              'px-2.5 py-1 text-sm rounded-md',
              'border border-border-default text-text-base',
              'hover:bg-interaction-hover hover:text-text-strong',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
              'disabled:opacity-50 disabled:cursor-not-allowed',
              'transition-[background,color,opacity] duration-[180ms] ease-[var(--easing-standard)]',
            ].join(' ')}
          >
            {pdfStatus === 'processing' ? `${t('report.button.exportPdf')}…` : t('report.button.exportPdf')}
          </button>
          <button
            type="button"
            title={mdTooltip}
            disabled={mdDisabled}
            onClick={() => downloadFile('md')}
            className={[
              'px-2.5 py-1 text-sm rounded-md',
              'border border-border-default text-text-base',
              'hover:bg-interaction-hover hover:text-text-strong',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
              'disabled:opacity-50 disabled:cursor-not-allowed',
              'transition-[background,color,opacity] duration-[180ms] ease-[var(--easing-standard)]',
            ].join(' ')}
          >
            {mdStatus === 'processing' ? `${t('report.button.exportMd')}…` : t('report.button.exportMd')}
          </button>
        </div>
      </header>

      <div className="relative flex-1 overflow-hidden">
        {!iframeLoaded && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-bg-canvas z-10">
            <svg
              className="h-5 w-5 animate-spin text-text-soft"
              viewBox="0 0 24 24"
              fill="none"
            >
              <circle
                className="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                strokeWidth="3"
              />
              <path
                className="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
              />
            </svg>
            <span className="text-sm text-text-soft">
              {isLoading ? t('report.viewer.loading') : t('report.viewer.preparing')}
            </span>
          </div>
        )}
        {reportId && (
          <iframe
            ref={iframeRef}
            src={iframeSrc}
            sandbox="allow-scripts"
            referrerPolicy="no-referrer"
            className="w-full h-full border-0"
            title={`report ${reportId}`}
            onLoad={() => setIframeLoaded(true)}
          />
        )}
      </div>
    </div>
  )
}
