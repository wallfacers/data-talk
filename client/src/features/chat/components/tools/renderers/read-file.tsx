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
import { EyeIcon } from 'lucide-react'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'

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

  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const sessionId = activeSessionId?.trim().length
    ? activeSessionId
    : part.sessionID.trim().length > 0
      ? part.sessionID
      : null

  if (!sessionId || sessionId.trim().length === 0) {
    return <GenericTool {...props} defaultOpen />
  }

  const openInStage = () => {
    const stage = useStageStore.getState()
    stage.openStage()
    openOrFocusFilePreviewTab({
      getState: useStageStore.getState,
      sessionId,
      payload,
    })
  }

  const language = getCurrentLanguage()
  const openStageLabel = translateMessage(language, 'chat.openStage')
  const viewInStageLabel = translateMessage(language, 'chat.viewInStage')
  const risk = resolveRisk(part, descriptor)

  return (
    <BasicTool
      icon="file"
      risk={risk}
      status={part.state.status}
      trigger={{
        title: payload.filename,
        subtitle: payload.fileType,
        action: (
          <Tooltip>
            <TooltipTrigger
              render={
                <button
                  type="button"
                  aria-label={openStageLabel}
                  onClick={(event) => {
                    event.stopPropagation()
                    openInStage()
                  }}
                  className="inline-flex size-6 items-center justify-center rounded-md text-foreground hover:bg-muted hover:text-foreground"
                >
                  <EyeIcon className="size-3.5" />
                </button>
              }
            />
            <TooltipContent>{viewInStageLabel}</TooltipContent>
          </Tooltip>
        ),
      }}
      forceOpen
      locked
    >
      <div className="min-w-0 text-xs font-medium text-muted-foreground truncate">
        {payload.filePath ?? payload.filename}
      </div>
    </BasicTool>
  )
}
