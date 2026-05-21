import { useCallback, useMemo, useState } from 'react'
import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage } from '@/i18n/messages'
import { ChartRenderer } from '@/features/chat/components/markdown/chart-renderer'
import { ChartExpandModal } from '@/features/chat/components/markdown/chart-expand-modal'
import { CheckIcon, CopyIcon, EyeIcon, Maximize2Icon } from 'lucide-react'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { copyToClipboard } from '@/lib/utils'

const KIND_ICONS: Record<string, string> = { table: '📊', chart: '📈' }

export function ArtifactCreated(props: ToolRendererProps) {
  const { part } = props
  const language = getCurrentLanguage()
  const rawOutput = part.state.output
  const output = (
    typeof rawOutput === 'string'
      ? (() => { try { return JSON.parse(rawOutput) } catch { return undefined } })()
      : rawOutput
  ) as { kind?: string; title?: string; artifactId?: string } | undefined
  const kind = (() => {
    if (output?.kind) return output.kind
    const metaKind = part.state.metadata?.kind as string | undefined
    if (metaKind) return metaKind
    if (part.tool === 'datatalk_render_chart') return 'chart'
    return 'table'
  })()
  const fallbackKindLabel =
    kind === 'chart'
      ? translateMessage(language, 'chat.artifactKind.chart')
      : kind === 'table'
        ? translateMessage(language, 'chat.artifactKind.table')
        : kind
  const title =
    output?.title ??
    (part.state.metadata?.title as string | undefined) ??
    translateMessage(language, 'chat.artifactFallbackTitle', { kind: fallbackKindLabel })
  const artifactId = output?.artifactId ?? null
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const sessionId = activeSessionId?.trim().length
    ? activeSessionId
    : part.sessionID.trim().length > 0
      ? part.sessionID
      : null

  const artifact = useOntologyStore((s) => {
    if (!sessionId || !artifactId || kind !== 'chart') return null
    return s.artifactsBySession.get(sessionId)?.get(artifactId) ?? null
  })

  const echartsOption = useMemo(() => {
    const payload = artifact?.payload as { echartsOption?: unknown } | undefined
    const opt = payload?.echartsOption
    if (!opt || typeof opt !== 'object') return null
    return opt as Record<string, unknown>
  }, [artifact])

  const optionJson = useMemo(() => {
    if (!echartsOption) return null
    try {
      return JSON.stringify(echartsOption)
    } catch {
      return null
    }
  }, [echartsOption])

  const [expanded, setExpanded] = useState(false)
  const [copied, setCopied] = useState(false)

  const onCopy = useCallback(async () => {
    if (!optionJson) return
    const ok = await copyToClipboard(optionJson)
    if (!ok) return
    setCopied(true)
    window.setTimeout(() => setCopied(false), 2000)
  }, [optionJson])

  const expandLabel = translateMessage(language, 'chart.expand')
  const copyLabel = translateMessage(language, 'chart.copy')

  const openArtifact = () => {
    if (!sessionId) return
    if (artifactId && part.state.status === 'completed') {
      useStageStore.getState().openArtifactPreviewTab(sessionId, artifactId, title)
    } else {
      useStageStore.getState().openStage()
    }
  }

  return (
    <>
      <BasicTool
        icon="artifact"
        risk="L1"
        status={part.state.status}
        trigger={{
          title: `${KIND_ICONS[kind] ?? '📦'} ${title}`,
          action: (
            <Tooltip>
              <TooltipTrigger
                render={
                  <button
                    type="button"
                    aria-label={translateMessage(language, 'chat.openStage')}
                    disabled={!sessionId || !artifactId || part.state.status !== 'completed'}
                    onClick={(event) => {
                      event.stopPropagation()
                      openArtifact()
                    }}
                    className="inline-flex size-6 items-center justify-center rounded-md text-foreground hover:bg-muted hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing"
                  >
                    <EyeIcon className="size-3.5" />
                  </button>
                }
              />
              <TooltipContent>{translateMessage(language, 'chat.viewInStage')}</TooltipContent>
            </Tooltip>
          ),
        }}
        hideDetails
      />
      {echartsOption && (
        <div className="mb-2 w-full min-w-0 max-w-full overflow-hidden rounded-lg border border-[var(--dt-border-subtle)]">
          <div className="flex items-center justify-end border-b border-[var(--dt-border-subtle)] bg-[var(--dt-bg-subtle)] px-3 py-1.5">
            <div className="flex items-center gap-1 text-[13px] leading-[18px]">
              <Tooltip>
                <TooltipTrigger
                  render={
                    <button
                      type="button"
                      onClick={() => setExpanded(true)}
                      aria-label={expandLabel}
                      className="inline-flex h-7 w-7 items-center justify-center rounded text-[var(--dt-text-muted)] transition-colors hover:text-[var(--dt-text-strong)]"
                    >
                      <Maximize2Icon className="h-4 w-4" aria-hidden="true" />
                    </button>
                  }
                />
                <TooltipContent>{expandLabel}</TooltipContent>
              </Tooltip>
              <Tooltip>
                <TooltipTrigger
                  render={
                    <button
                      type="button"
                      onClick={onCopy}
                      disabled={!optionJson}
                      aria-label={copyLabel}
                      className="inline-flex h-7 w-7 items-center justify-center rounded text-[var(--dt-text-muted)] transition-colors hover:text-[var(--dt-text-strong)] disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {copied
                        ? <CheckIcon className="h-4 w-4" aria-hidden="true" />
                        : <CopyIcon className="h-4 w-4" aria-hidden="true" />}
                    </button>
                  }
                />
                <TooltipContent>{copyLabel}</TooltipContent>
              </Tooltip>
            </div>
          </div>
          <div data-testid="artifact-chart-canvas-host" className="w-full min-w-0 max-w-full p-3">
            <ChartRenderer option={echartsOption} />
          </div>
        </div>
      )}
      {expanded && echartsOption ? <ChartExpandModal option={echartsOption} onClose={() => setExpanded(false)} /> : null}
    </>
  )
}
