import { useEffect } from 'react'
import { useSessionStore } from '@/stores/session-store'
import { useConnectionStore } from '@/features/connection/store'
import { useChannel } from '@/services/channel/use-channel'
import { createTextPart } from '@/services/channel/types'
import { useHasActiveModel } from './use-has-active-model'

export function usePendingPromptResume() {
  const pendingPrompt = useSessionStore((s) => s.pendingPrompt)
  const modelOverlayOn = useSessionStore((s) => s.pendingModelPrompt)
  const activeConn = useConnectionStore((s) => s.activeConnectionId)
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const setPendingPrompt = useSessionStore((s) => s.setPendingPrompt)
  const { sendMessage, isStreaming } = useChannel()
  const hasActiveModel = useHasActiveModel()

  useEffect(() => {
    if (modelOverlayOn || !pendingPrompt || !hasActiveModel || !activeConn || !activeSessionId || isStreaming) return

    const draft = pendingPrompt
    setPendingPrompt(null)
    void sendMessage([createTextPart(activeSessionId, draft)])
  }, [modelOverlayOn, pendingPrompt, hasActiveModel, activeConn, activeSessionId, isStreaming, setPendingPrompt, sendMessage])
}
