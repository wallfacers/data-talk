import { useRef, useState } from 'react'
import { useReport, useSystemStatus, reportDownloadUrl } from '../api'
import { useI18n } from '@/i18n/use-i18n'

export interface ReportViewerTabProps {
  reportId: string
}

export function ReportViewerTab({ reportId }: ReportViewerTabProps) {
  const { t } = useI18n()
  const iframeRef = useRef<HTMLIFrameElement>(null)
  const [iframeLoaded, setIframeLoaded] = useState(false)
  const { data: report, isLoading } = useReport(reportId)
  const { data: systemStatus } = useSystemStatus()

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
    <div className="flex flex-col h-full bg-[var(--dt-bg)]">
      <header className="flex items-center justify-between gap-3 px-4 py-3 border-b border-[var(--dt-border)]">
        <div className="flex-1 min-w-0">
          <div className="text-[var(--dt-fg-strong)] font-medium truncate">
            {report?.title ?? t('report.viewer.loading')}
          </div>
          {report?.subtitle ? (
            <div className="text-[var(--dt-muted-foreground)] text-xs truncate">{report.subtitle}</div>
          ) : null}
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <button
            type="button"
            title={pdfTooltip}
            disabled={pdfDisabled}
            onClick={() => downloadFile('pdf')}
            className="px-2 py-1 text-sm rounded-sm border border-[var(--dt-border)] disabled:opacity-50"
          >
            {pdfStatus === 'processing' ? `${t('report.button.exportPdf')}…` : t('report.button.exportPdf')}
          </button>
          <button
            type="button"
            title={mdTooltip}
            disabled={mdDisabled}
            onClick={() => downloadFile('md')}
            className="px-2 py-1 text-sm rounded-sm border border-[var(--dt-border)] disabled:opacity-50"
          >
            {mdStatus === 'processing' ? `${t('report.button.exportMd')}…` : t('report.button.exportMd')}
          </button>
        </div>
      </header>

      <div className="relative flex-1 overflow-hidden">
        {!iframeLoaded && (
          <div className="absolute inset-0 flex items-center justify-center bg-[var(--dt-bg)] z-10">
            <div className="text-[var(--dt-muted-foreground)] text-sm">
              {isLoading ? t('report.viewer.loading') : t('report.viewer.preparing')}
            </div>
          </div>
        )}
        <iframe
          ref={iframeRef}
          src={reportDownloadUrl(reportId, 'html')}
          sandbox="allow-scripts"
          referrerPolicy="no-referrer"
          className="w-full h-full border-0"
          title={`report ${reportId}`}
          onLoad={() => setIframeLoaded(true)}
        />
      </div>
    </div>
  )
}
