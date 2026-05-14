import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { buildEventSink, useChannelClient } from '@/services/channel/use-channel'
import { fetchSessionStatus } from '@/services/channel/session-status'
import { useChannelStore } from '@/stores/channel-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'
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

    let cancelled = false
    let unsub: (() => void) | null = null

    // BUG-0046: reconcile streaming flag against authoritative OpenCode status
    // BEFORE attaching the SSE sink. Order matters: setStreaming(true) must
    // happen after history has been loaded by useSessionHistory's effect (the
    // mount-time streamingBySession is empty, so shouldSkipReplace lets the
    // history replace through), and before the SSE pipeline can dispatch a
    // late `session.idle` that would otherwise leave the button in the wrong
    // state. fetchSessionStatus fail-opens to `idle`, so a slow OpenCode never
    // blocks subscription.
    void (async () => {
      try {
        const status = await fetchSessionStatus(sessionId)
        if (cancelled) return
        if (status.type === 'busy' || status.type === 'retry') {
          useChatPartsStore.getState().setStreaming(sessionId, true)
        }
      } catch { /* fail-open */ }
      if (cancelled) return
      const sink = buildEventSink(sessionId, client, queryClient, connectionId)
      setConnected(true)
      unsub = client.subscribe(resumeFrom, sink)
    })()

    return () => {
      cancelled = true
      if (unsub) unsub()
      subscribedSessions.delete(sessionId)
      setConnected(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client, sessionId, setConnected, queryClient])
}
