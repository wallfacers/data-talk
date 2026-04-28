import { Component, memo, useCallback, useMemo, useRef, useState, type ReactNode } from 'react'
import { CheckIcon, CopyIcon, ExternalLinkIcon, Loader2Icon, Maximize2Icon } from 'lucide-react'
import { ChartRenderer } from './chart-renderer'
import { ChartExpandModal } from './chart-expand-modal'
import { useOntologyStore } from '@/stores/ontology-store'
import { useSessionStore } from '@/stores/session-store'
import { useTimelineStore } from '@/stores/timeline-store'
import { promoteChartToStage } from '@/services/artifacts/promote-chart'
import { normalizeError, showErrorToast } from '@/services/http-error'
import { useI18n } from '@/i18n/use-i18n'
import { copyToClipboard } from '@/lib/utils'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'

type ChartBlockProps = {
  json: string
  streaming: boolean
  messageId: string
  blockIndex: number
  partId?: string
  sourceArtifactId?: string
}

type ParsedChartOption =
  | { ok: true; option: Record<string, unknown> }
  | { ok: false; error: string; code?: 'too_large' }

type PromoteState = 'idle' | 'loading'
const MAX_CHART_JSON_BYTES = 256 * 1024
const MAX_CHART_JSON_LABEL = '256 KB'
const EMPTY_ARTIFACTS: Map<string, { id: string; kind: string; originMessageId?: string; originPartId?: string }> = new Map()

function utf8ByteLength(text: string): number {
  return new TextEncoder().encode(text).byteLength
}

function parseChartOption(json: string): ParsedChartOption {
  if (utf8ByteLength(json) > MAX_CHART_JSON_BYTES) {
    return { ok: false, code: 'too_large', error: `chart option JSON exceeds ${MAX_CHART_JSON_LABEL}` }
  }

  try {
    const parsed = JSON.parse(json)
    if (typeof parsed !== 'object' || parsed === null) {
      return { ok: false, error: 'chart option must be an object' }
    }
    return { ok: true, option: parsed as Record<string, unknown> }
  } catch (error) {
    return {
      ok: false,
      error: error instanceof Error ? error.message : String(error),
    }
  }
}

function findMatchedArtifact(
  artifacts: Map<string, { id: string; kind: string; originMessageId?: string; originPartId?: string }>,
  messageId: string,
  partId?: string,
) {
  const expectedPartId = partId ?? ''
  for (const artifact of artifacts.values()) {
    if (artifact.kind !== 'chart') continue
    if ((artifact.originMessageId ?? '') !== messageId) continue
    if ((artifact.originPartId ?? '') !== expectedPartId) continue
    return artifact
  }
  return null
}

function ChartSkeleton({ label }: { label: string }) {
  const heights = [20, 34, 48, 34, 22]
  return (
    <div
      data-testid="chart-skeleton"
      className="my-2 flex h-[320px] items-center justify-center gap-2 overflow-hidden rounded-lg border border-[var(--dt-border-subtle)] bg-[var(--dt-bg-panel)] px-4"
      role="img"
      aria-label={label}
    >
      {heights.map((height, index) => (
        <span
          key={`${height}-${index}`}
          className="chart-bar-pulse inline-flex w-3 rounded-t bg-[var(--dt-accent-primary)] opacity-45"
          style={{ height, animationDelay: `-${(index * 0.18).toFixed(2)}s` }}
        />
      ))}
    </div>
  )
}

function ChartError({ json, title, message }: { json: string; title: string; message: string }) {
  return (
    <div data-testid="chart-error" className="my-2 overflow-hidden rounded-lg border border-[var(--dt-status-danger)]">
      <div className="border-b border-[var(--dt-status-danger)] bg-[var(--dt-status-danger-surface)] px-3 py-2 text-[13px] leading-[18px] text-[var(--dt-status-danger)]">
        {title}: {message}
      </div>
      <pre className="max-h-[220px] overflow-auto bg-[var(--dt-bg-panel)] px-3 py-2 font-mono text-[13px] leading-[18px] text-[var(--dt-text-muted)]">
        {json}
      </pre>
    </div>
  )
}

function toErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}

type ChartErrorBoundaryProps = {
  children: ReactNode
  json: string
  resetKey: string
  title: string
}

type ChartErrorBoundaryState = {
  error: unknown
}

class ChartErrorBoundary extends Component<ChartErrorBoundaryProps, ChartErrorBoundaryState> {
  state: ChartErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: unknown): ChartErrorBoundaryState {
    return { error }
  }

  componentDidUpdate(prevProps: ChartErrorBoundaryProps) {
    if (this.state.error && prevProps.resetKey !== this.props.resetKey) {
      this.setState({ error: null })
    }
  }

  render() {
    if (this.state.error) {
      return (
        <ChartError
          json={this.props.json}
          title={this.props.title}
          message={toErrorMessage(this.state.error)}
        />
      )
    }
    return this.props.children
  }
}

export const ChartBlock = memo(function ChartBlock({
  json,
  streaming,
  messageId,
  blockIndex,
  partId,
  sourceArtifactId,
}: ChartBlockProps) {
  const { t } = useI18n()
  const sessionId = useSessionStore((state) => state.activeSessionId)
  const artifacts = useOntologyStore((state) =>
    sessionId ? state.artifactsBySession.get(sessionId) ?? EMPTY_ARTIFACTS : EMPTY_ARTIFACTS,
  )
  const matched = useMemo(
    () => findMatchedArtifact(artifacts as Map<string, any>, messageId, partId),
    [artifacts, messageId, partId],
  )

  const lastValidOptionRef = useRef<Record<string, unknown> | null>(null)
  const parsed = useMemo(() => parseChartOption(json), [json])
  if (parsed.ok) {
    lastValidOptionRef.current = parsed.option
  }

  const [promoteState, setPromoteState] = useState<PromoteState>('idle')
  const [copied, setCopied] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const option = parsed.ok ? parsed.option : parsed.code === 'too_large' ? null : lastValidOptionRef.current
  const stable = !streaming
  const canPromote = stable && !!option && !!sessionId
  const openLabel = matched ? t('chart.alreadyInWorkbench') : t('chart.openInWorkbench')

  const onPromote = useCallback(async () => {
    if (!sessionId || !option || !stable || promoteState === 'loading') return

    if (matched) {
      useTimelineStore.getState().setActive(sessionId, matched.id)
      return
    }

    setPromoteState('loading')
    try {
      const created = await promoteChartToStage({
        sessionId,
        option,
        sourceArtifactId: sourceArtifactId ?? null,
        originMessageId: messageId,
        originPartId: partId ?? null,
      })
      useTimelineStore.getState().setActive(sessionId, created.artifactId)
    } catch (error) {
      showErrorToast(normalizeError(error))
    } finally {
      setPromoteState('idle')
    }
  }, [canPromote, matched, messageId, option, partId, promoteState, sessionId, sourceArtifactId, stable])

  const onCopy = useCallback(async () => {
    const copiedOk = await copyToClipboard(json)
    if (!copiedOk) return
    setCopied(true)
    window.setTimeout(() => setCopied(false), 2000)
  }, [json])

  if (!parsed.ok && parsed.code === 'too_large') {
    return <ChartError json={json} title={t('chart.jsonError')} message={t('chart.tooLarge')} />
  }

  if (!option && streaming) {
    return <ChartSkeleton label={t('chart.generating')} />
  }

  if (!option) {
    return <ChartError json={json} title={t('chart.jsonError')} message={parsed.ok ? t('chart.invalidOption') : parsed.error} />
  }

  return (
    <div
      data-component="chart-block"
      data-chart-block-index={String(blockIndex)}
      className="my-2 overflow-hidden rounded-lg border border-[var(--dt-border-subtle)]"
    >
      <div className="flex items-center justify-between border-b border-[var(--dt-border-subtle)] bg-[var(--dt-bg-subtle)] px-3 py-1.5">
        <span className="font-mono text-[13px] leading-[18px] text-[var(--dt-text-muted)]">{t('chart.label')}</span>
        <div className="flex items-center gap-1 text-[13px] leading-[18px]">
          <Tooltip>
            <TooltipTrigger
              render={
                <button
                  type="button"
                  onClick={onPromote}
                  disabled={!canPromote || promoteState === 'loading'}
                  aria-label={openLabel}
                  className="inline-flex h-7 w-7 items-center justify-center rounded text-[var(--dt-text-muted)] transition-colors hover:text-[var(--dt-text-strong)] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {promoteState === 'loading'
                    ? <Loader2Icon className="h-4 w-4 animate-spin motion-reduce:animate-none" aria-hidden="true" />
                    : <ExternalLinkIcon className="h-4 w-4" aria-hidden="true" />}
                </button>
              }
            />
            <TooltipContent>{openLabel}</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger
              render={
                <button
                  type="button"
                  onClick={() => setExpanded(true)}
                  aria-label={t('chart.expand')}
                  className="inline-flex h-7 w-7 items-center justify-center rounded text-[var(--dt-text-muted)] transition-colors hover:text-[var(--dt-text-strong)]"
                >
                  <Maximize2Icon className="h-4 w-4" aria-hidden="true" />
                </button>
              }
            />
            <TooltipContent>{t('chart.expand')}</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger
              render={
                <button
                  type="button"
                  onClick={onCopy}
                  aria-label={t('chart.copy')}
                  className="inline-flex h-7 w-7 items-center justify-center rounded text-[var(--dt-text-muted)] transition-colors hover:text-[var(--dt-text-strong)]"
                >
                  {copied
                    ? <CheckIcon className="h-4 w-4" aria-hidden="true" />
                    : <CopyIcon className="h-4 w-4" aria-hidden="true" />}
                </button>
              }
            />
            <TooltipContent>{t('chart.copy')}</TooltipContent>
          </Tooltip>
        </div>
      </div>
      <div data-testid="chart-canvas-host">
        <ChartErrorBoundary json={json} resetKey={json} title={t('chart.renderError')}>
          <ChartRenderer option={option} />
        </ChartErrorBoundary>
      </div>
      {expanded ? <ChartExpandModal option={option} onClose={() => setExpanded(false)} /> : null}
    </div>
  )
})
