import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createElement, type ReactNode } from 'react'
import { renderHook, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { buildEventSink, useChannel } from './use-channel'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useChannelStore } from '@/stores/channel-store'
import { useSessionStore } from '@/stores/session-store'

describe('buildEventSink · session.meta.updated', () => {
  let qc: QueryClient
  beforeEach(() => {
    qc = new QueryClient()
  })

  it('updates sessions cache in place without triggering a refetch', () => {
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    qc.setQueryData(['sessions', 'conn-1'], [
      { id: 's1', connectionId: 'conn-1', title: '旧标题', hasEverSent: true, createdAt: 1, updatedAt: 1, titleLocked: false, reusedEmpty: false },
      { id: 's2', connectionId: 'conn-1', title: '别的', hasEverSent: true, createdAt: 2, updatedAt: 2, titleLocked: false, reusedEmpty: false },
    ])
    const sink = buildEventSink('s1', null, qc, 'conn-1')
    sink({ event: 'session.meta.updated', data: { sessionId: 's1', title: 'AI 自动命名', titleLocked: false, version: 2 } } as any)

    const cached = qc.getQueryData<Array<{ id: string; title: string }>>(['sessions', 'conn-1'])!
    expect(cached.find((s) => s.id === 's1')?.title).toBe('AI 自动命名')
    expect(cached.find((s) => s.id === 's2')?.title).toBe('别的')
    expect(invalidateSpy).not.toHaveBeenCalled()
  })

  it('is a no-op when the session is not in cache', () => {
    qc.setQueryData(['sessions', 'conn-1'], [
      { id: 'other', connectionId: 'conn-1', title: 'x', hasEverSent: true, createdAt: 1, updatedAt: 1, titleLocked: false, reusedEmpty: false },
    ])
    const sink = buildEventSink('missing', null, qc, 'conn-1')
    sink({ event: 'session.meta.updated', data: { sessionId: 'missing', title: 'x', titleLocked: false, version: 1 } } as any)

    const cached = qc.getQueryData<Array<{ id: string }>>(['sessions', 'conn-1'])!
    expect(cached).toHaveLength(1)
  })
})

describe('buildEventSink · message lifecycle', () => {
  let qc: QueryClient
  beforeEach(() => {
    qc = new QueryClient()
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),
    })
  })

  it('message.updated merges error onto existing assistant info', () => {
    const sink = buildEventSink('s1', null, qc, null)
    sink({ event: 'message.created', data: { info: { id: 'm1', role: 'assistant', sessionID: 's1', time: { created: 100 } } } } as any)
    sink({ event: 'message.updated', data: { info: { id: 'm1', role: 'assistant', sessionID: 's1', time: { created: 100 }, error: { name: 'ProviderAuthError', data: { message: 'Invalid access token or token expired' } } } } } as any)

    const info = useChatPartsStore.getState().infoBySession.get('s1')?.get('m1')
    expect(info?.error?.name).toBe('ProviderAuthError')
    expect(info?.error?.data?.message).toBe('Invalid access token or token expired')
  })
})

describe('useChannel.isStreaming (per-session)', () => {
  const wrapper = ({ children }: { children: ReactNode }) => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return createElement(QueryClientProvider, { client: qc }, children)
  }

  beforeEach(() => {
    useChatPartsStore.setState({ streamingBySession: new Set<string>() })
    useSessionStore.setState({ activeSessionId: null })
  })

  it('reflects the active session and flips when active switches', () => {
    const { result } = renderHook(() => useChannel(), { wrapper })

    act(() => { useSessionStore.setState({ activeSessionId: 'A' }) })
    expect(result.current.isStreaming).toBe(false)

    act(() => { useChatPartsStore.getState().setStreaming('A', true) })
    expect(result.current.isStreaming).toBe(true)

    act(() => { useSessionStore.setState({ activeSessionId: 'B' }) })
    expect(result.current.isStreaming).toBe(false)

    act(() => { useChatPartsStore.getState().setStreaming('B', true) })
    expect(result.current.isStreaming).toBe(true)

    act(() => { useSessionStore.setState({ activeSessionId: 'A' }) })
    expect(result.current.isStreaming).toBe(true)
  })

  it('returns false when activeSessionId is null regardless of store state', () => {
    useChatPartsStore.getState().setStreaming('A', true)
    const { result } = renderHook(() => useChannel(), { wrapper })
    expect(result.current.isStreaming).toBe(false)
  })
})

describe('buildEventSink → lastEventId tracking', () => {
  beforeEach(() => {
    useChannelStore.setState({ lastEventIdBySession: new Map(), isConnected: false })
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),
    })
  })

  it('updates lastEventIdBySession on every processed event', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildEventSink('ses_a', null, qc, null, null)

    sink({ id: 5, event: 'message.created', data: { message: { id: 'm1', role: 'assistant', sessionId: 'oc', time: { created: 1 } } } })
    sink({ id: 7, event: 'message.part.delta', data: { partId: 'p1', field: 'text', delta: 'hi' } })

    expect(useChannelStore.getState().lastEventIdBySession.get('ses_a')).toBe(7)
  })

  it('does not move cursor backward if an out-of-order event slips through', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildEventSink('ses_a', null, qc, null, null)

    sink({ id: 20, event: 'message.created', data: { message: { id: 'm1', role: 'assistant', sessionId: 'oc', time: { created: 1 } } } })
    sink({ id: 5,  event: 'message.created', data: { message: { id: 'm2', role: 'assistant', sessionId: 'oc', time: { created: 2 } } } })

    expect(useChannelStore.getState().lastEventIdBySession.get('ses_a')).toBe(20)
  })
})

describe('buildEventSink → turn-done clears streamingBySession', () => {
  beforeEach(() => {
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(['ses_a']),
    })
  })

  it('clears streamingBySession on session.idle event', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildEventSink('ses_a', null, qc, null, null)

    sink({ id: 1, event: 'session.idle', data: { sessionId: 'ses_a' } })

    expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(false)
  })

  it('clears streamingBySession on session.status=idle event', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildEventSink('ses_a', null, qc, null, null)

    sink({ id: 1, event: 'session.status', data: { status: 'idle', retryInfo: {} } })

    expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(false)
  })

  it('does NOT clear streamingBySession on session.status=busy', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildEventSink('ses_a', null, qc, null, null)

    sink({ id: 1, event: 'session.status', data: { status: 'busy', retryInfo: {} } })

    expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(true)
  })

  it('does not touch other sessions when one goes idle', () => {
    useChatPartsStore.setState({
      streamingBySession: new Set<string>(['ses_a', 'ses_b']),
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildEventSink('ses_a', null, qc, null, null)

    sink({ id: 1, event: 'session.idle', data: { sessionId: 'ses_a' } })

    expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(false)
    expect(useChatPartsStore.getState().streamingBySession.has('ses_b')).toBe(true)
  })
})
