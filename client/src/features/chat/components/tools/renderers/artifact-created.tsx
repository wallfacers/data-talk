import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage } from '@/i18n/messages'
import { EyeIcon } from 'lucide-react'

const KIND_ICONS: Record<string, string> = { table: '📊', chart: '📈', erd: '🔗' }

export function ArtifactCreated(props: ToolRendererProps) {
  const { part } = props
  const language = getCurrentLanguage()
  const output = part.state.output as { kind?: string; title?: string } | undefined
  const kind =
    output?.kind ??
    (part.state.metadata?.kind as string | undefined) ??
    'table'
  const title =
    output?.title ??
    (part.state.metadata?.title as string | undefined) ??
    `${kind} artifact`
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const sessionId = activeSessionId?.trim().length
    ? activeSessionId
    : part.sessionID.trim().length > 0
      ? part.sessionID
      : null

  const openStage = () => {
    if (sessionId) useStageStore.getState().openStage(sessionId)
  }

  return (
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
            title={translateMessage(language, 'chat.viewInStage')}
            disabled={!sessionId}
            onClick={(event) => {
              event.stopPropagation()
              openStage()
            }}
            className="inline-flex size-6 items-center justify-center rounded-md text-foreground hover:bg-muted hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
          >
            <EyeIcon className="size-3.5" />
          </button>
        ),
      }}
      hideDetails
    />
  )
}
