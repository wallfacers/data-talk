import { useEffect } from 'react'
import { useSessionStore } from '@/stores/session-store'
import { useConnectionStore } from '@/features/connection/store'
import { useChannel } from '@/services/channel/use-channel'
import { createTextPart } from '@/services/channel/types'

export function usePendingPromptResume() {
  const pendingPrompt = useSessionStore((s) => s.pendingPrompt)
  const overlayOn = useSessionStore((s) => s.pendingConnectionPrompt)
  const activeConn = useConnectionStore((s) => s.activeConnectionId)
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const setPendingPrompt = useSessionStore((s) => s.setPendingPrompt)
  const { sendMessage, isStreaming } = useChannel()

  useEffect(() => {
    if (overlayOn || !pendingPrompt || !activeConn || !activeSessionId || isStreaming) return

    const draft = pendingPrompt
    setPendingPrompt(null)
    void sendMessage([createTextPart(activeSessionId, draft)])
  }, [overlayOn, pendingPrompt, activeConn, activeSessionId, isStreaming, setPendingPrompt, sendMessage])
}
