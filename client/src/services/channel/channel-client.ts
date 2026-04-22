import { createParser, type EventSourceMessage } from 'eventsource-parser'
import type { RpcRequest, StreamEvent, Part } from './types'
import { generateUuid } from '@/lib/uuid'
import { API_PREFIX } from '../api-prefix'

export type ChannelClientOptions = {
  baseUrl: string
  sessionId: string
  clientId: string
  clientRev?: number
}

/**
 * `baseUrl` must be an origin only (e.g. `http://host:port`, or `''` for
 * same-origin). The `/api` path prefix is appended internally from
 * `API_PREFIX` — do not pre-concatenate it in callers or env vars.
 */
export class ChannelClient {
  private readonly baseUrl: string
  private readonly sessionId: string
  private readonly clientId: string
  private readonly clientRev: number

  constructor(opts: ChannelClientOptions) {
    this.baseUrl = opts.baseUrl.replace(/\/$/, '')
    this.sessionId = opts.sessionId
    this.clientId = opts.clientId
    this.clientRev = opts.clientRev ?? 1
  }

  async sendMessage(parts: Part[], onEvent: (e: StreamEvent) => void): Promise<void> {
    const body: RpcRequest = {
      jsonrpc: '2.0', id: generateUuid(),
      method: 'send_message',
      params: { parts },
    }
    await this.streamingPost(body, onEvent)
  }

  async actionResult(callId: string, ok: boolean, output?: unknown, error?: unknown): Promise<void> {
    const body: RpcRequest = {
      jsonrpc: '2.0', id: generateUuid(),
      method: 'action_result',
      params: { callId, ok, output, error },
    }
    await this.plainPost(body)
  }

  async abort(): Promise<boolean> {
    const body: RpcRequest = {
      jsonrpc: '2.0', id: generateUuid(),
      method: 'abort', params: {},
    }
    const response = await this.plainPost(body)
    return parseAbortResult(response)
  }

  subscribe(lastEventId: number | undefined, onEvent: (e: StreamEvent) => void): () => void {
    const ctrl = new AbortController()
    void (async () => {
      try {
        const res = await fetch(this.url(), {
          method: 'GET',
          signal: ctrl.signal,
          headers: {
            'DataTalk-Session-Id': this.clientId,
            'DataTalk-Client-Rev': String(this.clientRev),
            ...(lastEventId !== undefined ? { 'Last-Event-ID': String(lastEventId) } : {}),
          },
        })
        await consumeSseStream(res, onEvent)
      } catch (err) {
        // Expected when the caller calls the returned disposer: session switch,
        // component unmount, or HMR. Anything else is a real network failure.
        if (ctrl.signal.aborted) return
        console.warn('[channel-client] subscribe stream failed', err)
      }
    })()
    return () => ctrl.abort()
  }

  private async plainPost(body: RpcRequest): Promise<unknown> {
    const res = await fetch(this.url(), {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'DataTalk-Session-Id': this.clientId,
        'DataTalk-Client-Rev': String(this.clientRev),
      },
      body: JSON.stringify(body),
    })
    if (!res.ok) throw new Error(`channel RPC failed: ${res.status}`)
    const ct = res.headers.get('content-type') ?? ''
    if (ct.includes('application/json')) return res.json()
    return res.text()
  }

  private async streamingPost(body: RpcRequest, onEvent: (e: StreamEvent) => void): Promise<void> {
    const res = await fetch(this.url(), {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'DataTalk-Session-Id': this.clientId,
        'DataTalk-Client-Rev': String(this.clientRev),
      },
      body: JSON.stringify(body),
    })
    if (!res.ok) throw new Error(`send_message failed: ${res.status}`)
    await consumeSseStream(res, onEvent)
  }

  private url() { return `${this.baseUrl}${API_PREFIX}/sessions/${this.sessionId}/channel` }
}

function parseAbortResult(response: unknown): boolean {
  if (typeof response !== 'object' || response === null) return true
  const envelope = response as { result?: unknown; error?: unknown }
  if (envelope.error != null) throw new Error(`abort RPC returned error: ${JSON.stringify(envelope.error)}`)
  if (typeof envelope.result === 'boolean') return envelope.result
  if (typeof envelope.result === 'object' && envelope.result !== null) {
    const aborted = (envelope.result as { aborted?: unknown }).aborted
    if (typeof aborted === 'boolean') return aborted
  }
  // Backward compatibility with older backend ack shape: {"result":{}}
  return true
}

async function consumeSseStream(res: Response, onEvent: (e: StreamEvent) => void): Promise<void> {
  const body = res.body
  if (!body) return
  const parser = createParser({
    onEvent(msg: EventSourceMessage) {
      const id = msg.id ? Number(msg.id) : 0
      let data: unknown = {}
      try { data = msg.data ? JSON.parse(msg.data) : {} } catch { /* ignore malformed */ }
      onEvent({ id, event: msg.event ?? 'message', data })
    },
  })
  const reader = body.getReader()
  const decoder = new TextDecoder()
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    parser.feed(decoder.decode(value, { stream: true }))
  }
}
