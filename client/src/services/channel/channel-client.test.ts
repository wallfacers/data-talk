import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ChannelClient } from './channel-client'

describe('ChannelClient', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('sends action_result as plain POST and returns ack', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      headers: { get: (k: string) => k === 'content-type' ? 'application/json' : null },
      json: async () => ({ jsonrpc: '2.0', id: 'r1', result: {} }),
    })
    global.fetch = fetchMock as any

    const client = new ChannelClient({ baseUrl: 'http://test', sessionId: 's-1', clientId: 'c-1' })
    await client.actionResult('call-1', true, { reversed: 'olleh' })

    expect(fetchMock).toHaveBeenCalled()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://test/api/sessions/s-1/channel')
    const body = JSON.parse(init.body)
    expect(body.method).toBe('action_result')
    expect(body.params.callId).toBe('call-1')
  })

  it('streams SSE events from send_message and invokes onEvent per frame', async () => {
    const sseBody =
      'id: 1\nevent: connected\ndata: {"sessionId":"s-1","serverRev":1}\n\n' +
      'id: 2\nevent: message.part.delta\ndata: {"partId":"p1","field":"text","delta":"hi"}\n\n'
    const encoder = new TextEncoder()
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode(sseBody))
        controller.close()
      },
    })
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      headers: { get: (k: string) => k === 'content-type' ? 'text/event-stream' : null },
      body: stream,
    }) as any

    const events: any[] = []
    const client = new ChannelClient({ baseUrl: 'http://test', sessionId: 's-1', clientId: 'c-1' })
    await client.sendMessage([{ type: 'text', id: 'p1', sessionID: 's-1', messageID: 'm1', text: 'hi', metadata: {} }],
      e => events.push(e))

    expect(events).toHaveLength(2)
    expect(events[0].event).toBe('connected')
    expect(events[1].event).toBe('message.part.delta')
    expect(events[1].data.delta).toBe('hi')
  })
})
