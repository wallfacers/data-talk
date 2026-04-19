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

  it('triggers sessions query invalidation with connectionId', () => {
    const spy = vi.spyOn(qc, 'invalidateQueries')
    const sink = buildEventSink('s1', null, qc, 'conn-1')
    sink({ event: 'session.meta.updated', data: { sessionId: 's1', title: 'AI', titleLocked: false, version: 2 } } as any)
    expect(spy).toHaveBeenCalledWith({ queryKey: ['sessions', 'conn-1'] })
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