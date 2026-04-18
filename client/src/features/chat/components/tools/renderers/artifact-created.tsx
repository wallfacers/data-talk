import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'

const KIND_ICONS: Record<string, string> = { table: '📊', chart: '📈', erd: '🔗' }

export function ArtifactCreated(props: ToolRendererProps) {
  const { part } = props
  const output = part.state.output as { kind?: string; title?: string } | undefined
  const kind =
    output?.kind ??
    (part.state.metadata?.kind as string | undefined) ??
    'table'
  const title =
    output?.title ??
    (part.state.metadata?.title as string | undefined) ??
    `${kind} artifact`

  const openStage = () => {
    const sessionId = useSessionStore.getState().activeSessionId
    if (sessionId) useStageStore.getState().openStage(sessionId)
  }

  return (
    <BasicTool
      icon="artifact"
      risk="L1"
      status={part.state.status}
      trigger={{
        title: `${KIND_ICONS[kind] ?? '📦'} ${title}`,
        subtitle: '在 Stage 中查看 →',
        action: null,
      }}
      hideDetails
    >
      <button
        onClick={openStage}
        className="text-xs text-primary hover:underline"
      >
        打开 Stage
      </button>
    </BasicTool>
  )
}
