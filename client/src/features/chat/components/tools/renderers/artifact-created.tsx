import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage } from '@/i18n/messages'
import { EyeIcon } from 'lucide-react'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'

const KIND_ICONS: Record<string, string> = { table: '📊', chart: '📈' }

// Charts are rendered inline by the markdown ChartBlock (the single in-chat
// chart surface). This tool card is a compact reference only — it never draws
// a second canvas; the eye opens the artifact in the workbench.
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

  const openArtifact = () => {
    if (!sessionId) return
    if (artifactId && part.state.status === 'completed') {
      useStageStore.getState().openArtifactPreviewTab(sessionId, artifactId, title)
    } else {
      useStageStore.getState().openStage()
    }
  }

  return (
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
  )
}
