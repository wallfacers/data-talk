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
  const lastEventId = useChannelStore((s) => s.lastEventId)
  const connectionId = useConnectionStore((s) => s.activeConnectionId)

  useEffect(() => {
    if (!client || !sessionId) { setConnected(false); return }
    if (subscribedSessions.has(sessionId)) { setConnected(false); return }

    subscribedSessions.add(sessionId)
    const sink = buildEventSink(sessionId, client, queryClient, connectionId)
    setConnected(true)
    const unsub = client.subscribe(lastEventId, sink)
    return () => { unsub(); subscribedSessions.delete(sessionId); setConnected(false) }
    // Deliberately omit lastEventId from deps: we don't want to tear down the
    // stream every time a frame arrives and bumps lastEventId. The initial
    // value at mount is the resume cursor.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client, sessionId, setConnected, queryClient])
}
