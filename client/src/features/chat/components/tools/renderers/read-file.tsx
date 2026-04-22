import { BasicTool } from '../basic-tool'
import { GenericTool } from './generic-tool'
import type { ToolRendererProps } from '../tool-registry'
import { buildReadFilePreviewPayload } from './read-file-output'
import { openOrFocusFilePreviewTab } from '@/features/stage/utils/open-or-focus-file-preview-tab'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage } from '@/i18n/messages'
import { resolveRisk } from '../../helpers/risk'
import { Button } from '@/components/ui/button'

export function ReadFile(props: ToolRendererProps) {
  const { part, descriptor } = props
  if (part.state.status !== 'completed') {
    return <GenericTool {...props} defaultOpen />
  }

  const payload = buildReadFilePreviewPayload({
    partId: part.id,
    messageId: part.messageID,
    callID: part.callID,
    output: typeof part.state.output === 'string' ? part.state.output : '',
    metadata: part.state.metadata,
  })

  if (!payload) {
    return <GenericTool {...props} defaultOpen />
  }

  const sessionId = part.sessionID.trim().length > 0
    ? part.sessionID
    : useSessionStore.getState().activeSessionId

  if (!sessionId || sessionId.trim().length === 0) {
    return <GenericTool {...props} defaultOpen />
  }

  const openInStage = () => {
    const stage = useStageStore.getState()
    stage.openStage(sessionId)
    openOrFocusFilePreviewTab({
      getState: useStageStore.getState,
      sessionId,
      payload,
    })
  }

  const language = getCurrentLanguage()
  const risk = resolveRisk(part, descriptor)

  return (
    <BasicTool
      icon="file"
      risk={risk}
      status={part.state.status}
      trigger={{
        title: payload.filename,
        subtitle: payload.fileType,
      }}
      forceOpen
      locked
    >
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <div className="text-xs font-medium text-muted-foreground truncate">
            {payload.filePath ?? payload.filename}
          </div>
          <div className="text-xs text-muted-foreground">
            {translateMessage(language, 'chat.viewInStage')}
          </div>
        </div>
        <Button type="button" size="sm" variant="outline" onClick={openInStage}>
          {translateMessage(language, 'chat.openStage')}
        </Button>
      </div>
    </BasicTool>
  )
}
