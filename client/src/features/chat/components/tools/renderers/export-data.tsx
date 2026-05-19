import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'
import { resolveRisk } from '../../helpers/risk'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage, type LanguageOption, type MessageKey } from '@/i18n/messages'
import { DownloadIcon } from 'lucide-react'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { downloadFromUrl, formatFileSize, inferFilename } from '@/services/tauri/file-download'

type ExportOutput = {
  exportId?: string
  downloadUrl?: string
  rowCount?: number
  fileSize?: number
  format?: string
  status?: string
  warnings?: string[]
}

function getApiBaseUrl(): string {
  const env = (import.meta as any).env?.VITE_API_BASE_URL
  if (typeof env === 'string' && env.length > 0) return env.replace(/\/$/, '')
  return ''
}

function resolveDownloadUrl(url: string | undefined): string | null {
  if (!url) return null
  return url.startsWith('http') ? url : `${getApiBaseUrl()}${url}`
}

function resolveFormatLabel(format: string | undefined, lang: LanguageOption): string {
  if (!format) return ''
  const key = `export.format.${format}` as MessageKey
  const label = translateMessage(lang, key)
  return label === key ? format : label
}

export function ExportData(props: ToolRendererProps) {
  const { part, descriptor } = props
  const risk = resolveRisk(part, descriptor)
  const lang = getCurrentLanguage()

  const rawOutput = part.state.output
  const output = (
    typeof rawOutput === 'string'
      ? (() => { try { return JSON.parse(rawOutput) } catch { return undefined } })()
      : rawOutput
  ) as ExportOutput | undefined

  const status = output?.status ?? (part.state.status === 'error' ? 'error' : undefined)
  const isProcessing = status === 'processing' || part.state.status === 'running' || part.state.status === 'pending'
  const downloadUrl = resolveDownloadUrl(output?.downloadUrl)
  const formatLabel = resolveFormatLabel(output?.format, lang)

  const subtitle = isProcessing
    ? translateMessage(lang, 'export.statusProcessing')
    : output?.rowCount !== undefined
      ? `${output.rowCount} ${formatLabel}${output?.fileSize ? `, ${formatFileSize(output.fileSize)}` : ''}`
      : ''

  const handleDownload = () => {
    if (!downloadUrl || !output?.exportId) return
    const filename = inferFilename(output.exportId, output?.format ?? 'csv')
    downloadFromUrl(downloadUrl, filename)
  }

  return (
    <BasicTool
      icon="mcp"
      risk={risk}
      status={part.state.status}
      trigger={{
        title: translateMessage(lang, 'export.toolTitle'),
        subtitle,
        action: downloadUrl ? (
          <Tooltip>
            <TooltipTrigger
              render={
                <button
                  type="button"
                  aria-label={translateMessage(lang, 'export.downloadFile')}
                  disabled={isProcessing}
                  onClick={(event) => {
                    event.stopPropagation()
                    handleDownload()
                  }}
                  className="inline-flex size-6 items-center justify-center rounded-md text-foreground hover:bg-muted hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing"
                >
                  <DownloadIcon className="size-3.5" />
                </button>
              }
            />
            <TooltipContent>{translateMessage(lang, 'export.downloadFile')}</TooltipContent>
          </Tooltip>
        ) : undefined,
      }}
      hideDetails
    />
  )
}
