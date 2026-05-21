import { createParser, type EventSourceMessage } from 'eventsource-parser'
import { API_PREFIX } from '@/services/api-prefix'

export interface OpLogSseEvent {
  event: string
  data: unknown
}

export interface OpLogSseSubscription {
  dispose: () => void
}

/**
 * Subscribe to operation log SSE events for a connection.
 * Returns a control object with a `dispose` method to unsubscribe.
 */
export function subscribeOpLogStream(
  connectionId: string,
  onEvent: (e: OpLogSseEvent) => void,
): OpLogSseSubscription {
  const ctrl = new AbortController()
  const url = `${API_PREFIX}/connections/${connectionId}/op-logs/stream`

  void (async () => {
    try {
      const res = await fetch(url, {
        method: 'GET',
        signal: ctrl.signal,
        headers: { accept: 'text/event-stream' },
      })
      if (!res.ok) {
        console.warn('[op-log-sse] subscription failed:', res.status)
        return
      }
      const body = res.body
      if (!body) return

      const parser = createParser({
        onEvent(msg: EventSourceMessage) {
          let data: unknown = {}
          try { data = msg.data ? JSON.parse(msg.data) : {} } catch { /* ignore malformed */ }
          onEvent({ event: msg.event ?? 'message', data })
        },
      })

      const reader = body.getReader()
      const decoder = new TextDecoder()
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        parser.feed(decoder.decode(value, { stream: true }))
      }
    } catch (err) {
      if (ctrl.signal.aborted) return
      console.warn('[op-log-sse] stream error:', err)
    }
  })()

  return { dispose: () => ctrl.abort() }
}
