import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createElement, type ReactNode } from 'react'
import { renderHook, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { buildEventSink, useChannel } from './use-channel'
import { useChatPartsStore } from '@/stores/chat-parts-store'
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

  it('message.completed sets time.completed when absent', () => {
    const sink = buildEventSink('s1', null, qc, null)
    sink({ event: 'message.created', data: { info: { id: 'm1', role: 'assistant', sessionID: 's1', time: { created: 100 } } } } as any)
    sink({ event: 'message.completed', data: { sessionId: 's1', messageId: 'm1' } } as any)

    const info = useChatPartsStore.getState().infoBySession.get('s1')?.get('m1')
    expect(typeof info?.time.completed).toBe('number')
  })

  it('message.completed is a no-op when info is missing', () => {
    const sink = buildEventSink('s1', null, qc, null)
    sink({ event: 'message.completed', data: { sessionId: 's1', messageId: 'ghost' } } as any)
    expect(useChatPartsStore.getState().infoBySession.get('s1')?.get('ghost')).toBeUndefined()
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