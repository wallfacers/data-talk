import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createElement, type ReactNode } from 'react'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useChannelStore } from '@/stores/channel-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'

const { httpGetMock } = vi.hoisted(() => ({
  httpGetMock: vi.fn(),
}))

const unsubMock     = vi.fn()
const subscribeMock = vi.fn(() => unsubMock)
const clientMock    = {
  subscribe:    subscribeMock,
  actionResult: vi.fn(),
  sendMessage:  vi.fn(),
  abort:        vi.fn(),
}

vi.mock('@/services/channel/use-channel', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/services/channel/use-channel')>()
  return {
    ...original,
    useChannelClient: vi.fn(() => clientMock),
  }
})

vi.mock('@/services/http', () => ({
  http: {
    get: httpGetMock,
  },
}))

function wrapper({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return createElement(QueryClientProvider, { client: qc }, children)
}

// Use unique IDs per test to avoid module-level Set contamination
let counter = 0
function freshId() { return `bg-session-${++counter}` }

describe('useBackgroundSessionSubscribe', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useChannelStore.setState({ lastEventIdBySession: new Map(), isConnected: false })
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),
    })
    httpGetMock.mockReturnValue({
      json: vi.fn().mockResolvedValue([
        {
          info: {
            id: 'm1',
            role: 'assistant',
            sessionID: 'bg-default',
            time: { created: 1 },
          },
          parts: [],
        },
      ]),
    })
  })

  it('subscribes to the given session on mount', async () => {
    const { useBackgroundSessionSubscribe } = await import('../use-background-session-subscribe')
    renderHook(() => useBackgroundSessionSubscribe(freshId()), { wrapper })
    await waitFor(() => expect(subscribeMock).toHaveBeenCalledOnce())
  })

  it('unsubscribes on unmount', async () => {
    const { useBackgroundSessionSubscribe } = await import('../use-background-session-subscribe')
    const { unmount } = renderHook(() => useBackgroundSessionSubscribe(freshId()), { wrapper })
    await waitFor(() => expect(subscribeMock).toHaveBeenCalledOnce())
    unmount()
    expect(unsubMock).toHaveBeenCalledOnce()
  })

  it('does not call useChannelStore.setConnected', async () => {
    const setConnectedSpy = vi.spyOn(useChannelStore.getState(), 'setConnected')
    const { useBackgroundSessionSubscribe } = await import('../use-background-session-subscribe')
    renderHook(() => useBackgroundSessionSubscribe(freshId()), { wrapper })
    await waitFor(() => expect(httpGetMock).toHaveBeenCalled())
    expect(setConnectedSpy).not.toHaveBeenCalled()
  })

  it('does not subscribe when history shows the turn already completed', async () => {
    const sessionId = freshId()
    useChatPartsStore.getState().setStreaming(sessionId, true)
    httpGetMock.mockReturnValueOnce({
      json: vi.fn().mockResolvedValue([
        {
          info: {
            id: 'm1',
            role: 'assistant',
            sessionID: sessionId,
            time: { created: 1, completed: 2 },
          },
          parts: [],
        },
      ]),
    })

    const { useBackgroundSessionSubscribe } = await import('../use-background-session-subscribe')
    renderHook(() => useBackgroundSessionSubscribe(sessionId), { wrapper })

    await waitFor(() => {
      expect(httpGetMock).toHaveBeenCalled()
      expect(useChatPartsStore.getState().streamingBySession.has(sessionId)).toBe(false)
    })
    expect(subscribeMock).not.toHaveBeenCalled()
  })

  it('subscribes when history still shows an incomplete assistant turn', async () => {
    const sessionId = freshId()
    useChatPartsStore.getState().setStreaming(sessionId, true)
    httpGetMock.mockReturnValueOnce({
      json: vi.fn().mockResolvedValue([
        {
          info: {
            id: 'm1',
            role: 'assistant',
            sessionID: sessionId,
            time: { created: 1 },
          },
          parts: [],
        },
      ]),
    })

    const { useBackgroundSessionSubscribe } = await import('../use-background-session-subscribe')
    renderHook(() => useBackgroundSessionSubscribe(sessionId), { wrapper })

    await waitFor(() => expect(subscribeMock).toHaveBeenCalledOnce())
    expect(useChatPartsStore.getState().streamingBySession.has(sessionId)).toBe(true)
  })
})
