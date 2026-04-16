import { useEffect } from 'react'
import { buildEventSink, useChannelClient } from '@/services/channel/use-channel'
import { useChannelStore } from '@/stores/channel-store'

export function useSessionSubscribe(sessionId: string | null) {
  const client = useChannelClient(sessionId)
  const setConnected = useChannelStore((s) => s.setConnected)
  const lastEventId = useChannelStore((s) => s.lastEventId)

  useEffect(() => {
    if (!client || !sessionId) { setConnected(false); return }
    const sink = buildEventSink(sessionId, client)
    setConnected(true)
    const unsub = client.subscribe(lastEventId, sink)
    return () => { unsub(); setConnected(false) }
    // Deliberately omit lastEventId from deps: we don't want to tear down the
    // stream every time a frame arrives and bumps lastEventId. The initial
    // value at mount is the resume cursor.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client, sessionId, setConnected])
}
