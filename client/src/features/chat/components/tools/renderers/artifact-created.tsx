import { useMemo } from 'react'
import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage } from '@/i18n/messages'
import { ChartRenderer } from '@/features/chat/components/markdown/chart-renderer'
import { EyeIcon } from 'lucide-react'

const KIND_ICONS: Record<string, string> = { table: '📊', chart: '📈', erd: '🔗' }

export function ArtifactCreated(props: ToolRendererProps) {
  const { part } = props
  const language = getCurrentLanguage()
  const output = part.state.output as { kind?: string; title?: string; artifactId?: string } | undefined
  const kind = (() => {
    if (output?.kind) return output.kind
    const metaKind = part.state.metadata?.kind as string | undefined
    if (metaKind) return metaKind
    if (part.tool === 'datatalk_render_chart') return 'chart'
    if (part.tool === 'datatalk_layout_erd') return 'erd'
    return 'table'
  })()
  const title =
    output?.title ??
    (part.state.metadata?.title as string | undefined) ??
    `${kind} artifact`
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

  const openArtifact = () => {
    if (!sessionId) return
    if (artifactId && part.state.status === 'completed') {
      useStageStore.getState().openArtifactPreviewTab(sessionId, artifactId, title)
    } else {
      useStageStore.getState().openStage(sessionId)
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
            <button
              type="button"
              aria-label={translateMessage(language, 'chat.openStage')}
              disabled={!sessionId || !artifactId || part.state.status !== 'completed'}
              onClick={(event) => {
                event.stopPropagation()
                openArtifact()
              }}
              className="inline-flex size-6 items-center justify-center rounded-md text-foreground hover:bg-muted hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
            >
              <EyeIcon className="size-3.5" />
            </button>
          ),
        }}
        hideDetails
      />
      {echartsOption && (
        <div className="mb-2 overflow-hidden rounded-lg border border-[var(--dt-border-subtle)]">
          <ChartRenderer option={echartsOption} />
        </div>
      )}
    </>
  )
}
