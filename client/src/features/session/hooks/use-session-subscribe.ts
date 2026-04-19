import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { buildEventSink, useChannelClient } from '@/services/channel/use-channel'
import { useChannelStore } from '@/stores/channel-store'
import { useConnectionStore } from '@/features/connection/store'

const subscribedSessions = new Set<string>()

export function useSessionSubscribe(sessionId: string | null) {
  const client = useChannelClient(sessionId)
  const queryClient = useQueryClient()
  const setConnected = useChannelStore((s) => s.setConnected)
  const connectionId = useConnectionStore((s) => s.activeConnectionId)

  useEffect(() => {
    if (!client || !sessionId) { setConnected(false); return }
    if (subscribedSessions.has(sessionId)) { setConnected(false); return }

    // Resume from the cursor we persisted in sessionStorage (per tab).
    // Read imperatively via getState() so this hook is NOT reactive to
    // cursor writes — the cursor is updated on every SSE frame, and we only
    // need its value at the moment we subscribe.
    const resumeFrom = useChannelStore.getState().lastEventIdBySession.get(sessionId)

    subscribedSessions.add(sessionId)
    const sink = buildEventSink(sessionId, client, queryClient, connectionId)
    setConnected(true)
    const unsub = client.subscribe(resumeFrom, sink)
    return () => { unsub(); subscribedSessions.delete(sessionId); setConnected(false) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client, sessionId, setConnected, queryClient])
}
