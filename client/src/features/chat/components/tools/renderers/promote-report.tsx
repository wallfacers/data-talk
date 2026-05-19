import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'
import { resolveRisk } from '../../helpers/risk'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage } from '@/i18n/messages'
import { ReportCard } from '@/features/report/components/report-card'

type PromoteReportOutput = {
  reportId?: string
  groupId?: string
  version?: number
  artifactPaths?: Record<string, string | null>
  pdfStatus?: 'processing' | 'ready' | 'failed'
  mdStatus?: 'processing' | 'ready' | 'failed'
  error?: string
  errorCode?: string
}

export function PromoteReport(props: ToolRendererProps) {
  const { part, descriptor } = props
  const risk = resolveRisk(part, descriptor) ?? 'L1'
  const lang = getCurrentLanguage()

  const rawOutput = part.state.output
  const output = (
    typeof rawOutput === 'string'
      ? (() => { try { return JSON.parse(rawOutput) } catch { return undefined } })()
      : rawOutput
  ) as PromoteReportOutput | undefined

  const reportId = output?.reportId
  const error = output?.error
  const inputRecord = (part.state.input ?? {}) as { report?: { meta?: { title?: string; subtitle?: string }} }
  const title = inputRecord.report?.meta?.title ?? translateMessage(lang, 'report.viewer.titleFallback')
  const subtitle = inputRecord.report?.meta?.subtitle ?? null

  if (!reportId) {
    return (
      <BasicTool
        icon="mcp"
        risk={risk}
        status={part.state.status}
        trigger={{
          title: translateMessage(lang, 'report.viewer.titleFallback'),
          subtitle: error ?? translateMessage(lang, 'report.viewer.preparing'),
        }}
        hideDetails
      />
    )
  }

  return (
    <BasicTool
      icon="mcp"
      risk={risk}
      status={part.state.status}
      trigger={{
        title: translateMessage(lang, 'report.viewer.titleFallback'),
        subtitle: title,
      }}
      hideDetails
    >
      <ReportCard reportId={reportId} title={title} subtitle={subtitle} generatedAt={Date.now()} />
    </BasicTool>
  )
}
