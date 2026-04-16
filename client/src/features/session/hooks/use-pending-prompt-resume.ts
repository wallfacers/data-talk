import { useEffect } from 'react'
import { useSessionStore } from '@/stores/session-store'
import { useConnectionStore } from '@/features/connection/store'
import { useChannel } from '@/services/channel/use-channel'

/**
 * When the user has typed a DB-related prompt but has no connection, we store
 * the draft in `pendingPrompt` and show `ConnectionOverlay`. Once they pick a
 * connection and `pendingConnectionPrompt` goes false, this hook re-fires the
 * draft as a real send_message and clears it.
 */
export function usePendingPromptResume() {
  const pendingPrompt = useSessionStore((s) => s.pendingPrompt)
  const overlayOn = useSessionStore((s) => s.pendingConnectionPrompt)
  const activeConn = useConnectionStore((s) => s.activeConnectionId)
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const setPendingPrompt = useSessionStore((s) => s.setPendingPrompt)
  const { sendMessage, isStreaming } = useChannel()

  useEffect(() => {
    if (overlayOn) return           // still asking the user
    if (!pendingPrompt) return      // nothing queued
    if (!activeConn) return         // user closed overlay without picking
    if (!activeSessionId) return
    if (isStreaming) return         // don't race another send

    const draft = pendingPrompt
    setPendingPrompt(null)          // clear first so we don't re-enter
    void sendMessage([
      { type: 'text', id: crypto.randomUUID(), sessionID: activeSessionId,
        messageID: '', text: draft, metadata: {} } as any,
    ])
  }, [overlayOn, pendingPrompt, activeConn, activeSessionId, isStreaming, setPendingPrompt, sendMessage])
}
