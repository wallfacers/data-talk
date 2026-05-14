import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createElement, type ReactNode } from 'react'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { buildEventSink, useChannel, __resetCallIdDispatchForTest } from './use-channel'
import { ChannelClient } from './channel-client'
import { createTextPart } from './types'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useChannelStore } from '@/stores/channel-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useSessionStore } from '@/stores/session-store'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionTurns } from '@/features/chat/components/helpers/use-session-turns'
import { uiRouter } from '@/services/ui-router'
import type { UIObject } from '@/services/ui-router'
import { getClientHandler } from '@/features/actions/registry'

vi.mock('@/features/stage/persistence/stage-persistence-bootstrap', () => ({
  coordinator: {
    ensureHydrated: vi.fn().mockResolvedValue(undefined),
    flush: vi.fn().mockResolvedValue(undefined),
    scheduleMetadataWrite: vi.fn(),
    scheduleContentWrite: vi.fn(),
  },
}))

// Reset the per-session event cursor before each test so the new id-based
// dedupe gate doesn't drop events in tests that reuse small ids like 1.
beforeEach(() => {
  useChannelStore.setState({ lastEventIdBySession: new Map(), isConnected: false })
})

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

describe('buildEventSink · session data context tool sync', () => {
  let qc: QueryClient

  beforeEach(() => {
    qc = new QueryClient()
    useSessionStore.setState({
      activeSessionId: 's1',
      dataContextBySession: new Map([['s1', {
        sessionId: 's1',
        connectionId: 'conn-old',
        connectionNameSnapshot: 'old-db',
        database: null,
        schema: null,
        selectedLevel: 'connection',
        updatedAt: 1,
      }]]),
    } as any)
    useConnectionStore.setState({ activeConnectionId: 'conn-old', connections: [] })
    qc.setQueryData(['session-data-context', 's1'], {
      sessionId: 's1',
      connectionId: 'conn-old',
      connectionNameSnapshot: 'old-db',
      database: null,
      schema: null,
      selectedLevel: 'connection',
      updatedAt: 1,
    })
  })

  it('updates the active session context from a completed select_connection tool part', () => {
    const sink = buildEventSink('s1', null, qc, 'conn-old')

    sink({
      event: 'message.part.updated',
      data: {
        part: {
          type: 'tool',
          id: 'prt-tool-1',
          sessionID: 's1',
          messageID: 'm1',
          tool: 'datatalk_select_connection',
          state: {
            status: 'completed',
            output: {
              sessionId: 's1',
              connectionId: 'conn-new',
              connectionNameSnapshot: 'pdt-dev',
              database: null,
              schema: null,
              selectedLevel: 'connection',
            },
          },
        },
      },
    } as any)

    expect(qc.getQueryData(['session-data-context', 's1'])).toMatchObject({
      sessionId: 's1',
      connectionId: 'conn-new',
      connectionNameSnapshot: 'pdt-dev',
      database: null,
      schema: null,
      selectedLevel: 'connection',
    })
    expect(useSessionStore.getState().dataContextBySession.get('s1')).toMatchObject({
      connectionId: 'conn-new',
      connectionNameSnapshot: 'pdt-dev',
    })
    expect(useConnectionStore.getState().activeConnectionId).toBe('conn-new')
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
    useOntologyStore.setState({ artifactsBySession: new Map() } as any)
  })

  it('message.updated merges error onto existing assistant info', () => {
    const sink = buildEventSink('s1', null, qc, null)
    sink({ event: 'message.created', data: { info: { id: 'm1', role: 'assistant', sessionID: 's1', time: { created: 100 } } } } as any)
    sink({ event: 'message.updated', data: { info: { id: 'm1', role: 'assistant', sessionID: 's1', time: { created: 100 }, error: { name: 'ProviderAuthError', data: { message: 'Invalid access token or token expired' } } } } } as any)

    const info = useChatPartsStore.getState().infoBySession.get('s1')?.get('m1')
    expect(info?.error?.name).toBe('ProviderAuthError')
    expect(info?.error?.data?.message).toBe('Invalid access token or token expired')
  })

  it('does not let an older echoed user event steal the latest pending user', () => {
    useChatPartsStore.getState().upsertInfo('s1', {
      id: 'u_old',
      role: 'user',
      sessionID: 's1',
      time: { created: 10 },
    })
    useChatPartsStore.getState().upsertPart('s1', {
      type: 'text',
      id: 'p_old',
      sessionID: 's1',
      messageID: 'u_old',
      text: '你好',
      metadata: {},
    } as any)
    const pendingId = useChatPartsStore.getState().upsertPendingUser('s1', 'hello')
    const sink = buildEventSink('s1', null, qc, null, pendingId)

    sink({
      event: 'message.updated',
      data: { info: { id: 'u_old', role: 'user', sessionID: 's1', time: { created: 10 } } },
    } as any)

    const infoMap = useChatPartsStore.getState().infoBySession.get('s1')
    expect(infoMap?.has(pendingId)).toBe(true)
    expect(infoMap?.get('u_old')?.id).toBe('u_old')
    expect(useChatPartsStore.getState().partsBySession.get('s1')?.get('u_old')?.[0]).toMatchObject({
      text: '你好',
    })
  })

  it('promotes the optimistic user only when the echoed user text part matches the pending text', () => {
    const pendingId = useChatPartsStore.getState().upsertPendingUser('s1', 'who are you')
    const sink = buildEventSink('s1', null, qc, null, pendingId)

    sink({
      event: 'message.created',
      data: { info: { id: 'u_real', role: 'user', sessionID: 's1', time: { created: 100 } } },
    } as any)
    sink({
      event: 'message.part.created',
      data: { part: { type: 'text', id: 'p_real', sessionID: 's1', messageID: 'u_real', text: 'who are you', metadata: {} } },
    } as any)

    const infoMap = useChatPartsStore.getState().infoBySession.get('s1')
    expect(infoMap?.has(pendingId)).toBe(false)
    expect(infoMap?.get('u_real')).toMatchObject({ id: 'u_real', role: 'user' })
    expect(useChatPartsStore.getState().partsBySession.get('s1')?.get('u_real')?.[0]).toMatchObject({
      text: 'who are you',
    })
  })

  it('does not open a second user turn when subscribe sees the echoed real user before the send sink', () => {
    const pendingId = useChatPartsStore.getState().upsertPendingUser('s1', 'hello')
    useChatPartsStore.getState().setStreaming('s1', true)
    const subscribeSink = buildEventSink('s1', null, qc, null)
    const sendSink = buildEventSink('s1', null, qc, null, pendingId)
    const { result } = renderHook(() => useSessionTurns('s1'))

    expect(result.current).toHaveLength(1)
    expect(result.current[0]).toMatchObject({
      renderKey: pendingId,
      userMessageId: pendingId,
    })

    act(() => {
      subscribeSink({
        event: 'message.created',
        data: { info: { id: 'u_real', role: 'user', sessionID: 's1', time: { created: 100 } } },
      } as any)
    })

    expect(result.current).toHaveLength(1)
    expect(result.current[0]).toMatchObject({
      renderKey: pendingId,
      userMessageId: 'u_real',
    })

    act(() => {
      sendSink({
        event: 'message.part.created',
        data: { part: { type: 'text', id: 'p_real', sessionID: 's1', messageID: 'u_real', text: 'hello', metadata: {} } },
      } as any)
    })

    expect(result.current).toHaveLength(1)
    expect(result.current[0]).toMatchObject({
      renderKey: pendingId,
      userMessageId: 'u_real',
    })
  })

  it('does not promote the pending user on an unrelated older user text part', () => {
    useChatPartsStore.getState().upsertInfo('s1', {
      id: 'u_old',
      role: 'user',
      sessionID: 's1',
      time: { created: 10 },
    })
    const pendingId = useChatPartsStore.getState().upsertPendingUser('s1', '今天天气怎么样')
    const sink = buildEventSink('s1', null, qc, null, pendingId)

    sink({
      event: 'message.part.updated',
      data: { part: { type: 'text', id: 'p_old', sessionID: 's1', messageID: 'u_old', text: '你好', metadata: {} } },
    } as any)

    const infoMap = useChatPartsStore.getState().infoBySession.get('s1')
    expect(infoMap?.has(pendingId)).toBe(true)
    expect(useChatPartsStore.getState().partsBySession.get('s1')?.get(pendingId)?.[0]).toMatchObject({
      text: '今天天气怎么样',
    })
  })

  it('propagates originMessageId / originPartId from ontology.updated patch to OntologyStore', () => {
    const sink = buildEventSink('s1', null, qc, null)

    sink({
      event: 'ontology.updated',
      data: {
        objectType: 'datatalk.artifact',
        id: 'art-1',
        patch: {
          version: 3,
          kind: 'chart',
          originMessageId: 'msg-7',
          originPartId: 'part-2',
        },
      },
    } as any)

    expect(useOntologyStore.getState().artifactsBySession.get('s1')?.get('art-1')).toMatchObject({
      id: 'art-1',
      version: 3,
      kind: 'chart',
      originMessageId: 'msg-7',
      originPartId: 'part-2',
    })
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
      pendingDeltasBySession: new Map(),
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

  it('drops a redelivered message.part.delta so text is not appended twice', () => {
    // The SessionBus fans events out to both the long-lived GET subscribe
    // sink and every POST send_message sink; a fresh POST subscription
    // also resumes from cursor 0. Without id-based dedupe, the same
    // delta would be appended twice and the user sees doubled content
    // like "sort sort()" during streaming.
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildEventSink('ses_a', null, qc, null, null)

    useChatPartsStore.getState().upsertPart('ses_a', {
      type: 'text', id: 'p1', sessionID: 'ses_a', messageID: 'm1', text: '', metadata: {},
    } as any)

    sink({ id: 11, event: 'message.part.delta', data: { partId: 'p1', field: 'text', delta: 'sort' } })
    // Redelivery of the exact same event id on the other stream.
    sink({ id: 11, event: 'message.part.delta', data: { partId: 'p1', field: 'text', delta: 'sort' } })

    const part = useChatPartsStore.getState().findPart('ses_a', 'p1') as any
    expect(part?.text).toBe('sort')
  })

  it('drops any replayed event whose id is at or below the cursor', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildEventSink('ses_a', null, qc, null, null)

    useChatPartsStore.getState().upsertPart('ses_a', {
      type: 'text', id: 'p1', sessionID: 'ses_a', messageID: 'm1', text: '', metadata: {},
    } as any)

    sink({ id: 20, event: 'message.part.delta', data: { partId: 'p1', field: 'text', delta: 'A' } })
    sink({ id: 21, event: 'message.part.delta', data: { partId: 'p1', field: 'text', delta: 'B' } })
    // Replay of id=20 from a cursor-0 POST resume must not apply again.
    sink({ id: 20, event: 'message.part.delta', data: { partId: 'p1', field: 'text', delta: 'A' } })

    const part = useChatPartsStore.getState().findPart('ses_a', 'p1') as any
    expect(part?.text).toBe('AB')
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

  // Build a sink whose subscribe baseline is already older than the 500ms
  // replay-suppression window. The dedicated replay-suppression tests below
  // cover the in-window behavior; these tests assert the steady-state path.
  function buildSinkPastReplayWindow(sessionId: string, qc: QueryClient) {
    const realNow = Date.now
    Date.now = () => realNow.call(Date) - 1000
    try {
      return buildEventSink(sessionId, null, qc, null, null)
    } finally {
      Date.now = realNow
    }
  }

  it('clears streamingBySession on session.idle event', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildSinkPastReplayWindow('ses_a', qc)

    sink({ id: 1, event: 'session.idle', data: { sessionId: 'ses_a' } })

    expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(false)
  })

  it('clears streamingBySession on session.status=idle event', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildSinkPastReplayWindow('ses_a', qc)

    sink({ id: 1, event: 'session.status', data: { status: 'idle', retryInfo: {} } })

    expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(false)
  })

  it('does NOT clear streamingBySession on session.status=busy', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildSinkPastReplayWindow('ses_a', qc)

    sink({ id: 1, event: 'session.status', data: { status: 'busy', retryInfo: {} } })

    expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(true)
  })

  it('does not touch other sessions when one goes idle', () => {
    useChatPartsStore.setState({
      streamingBySession: new Set<string>(['ses_a', 'ses_b']),
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const sink = buildSinkPastReplayWindow('ses_a', qc)

    sink({ id: 1, event: 'session.idle', data: { sessionId: 'ses_a' } })

    expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(false)
    expect(useChatPartsStore.getState().streamingBySession.has('ses_b')).toBe(true)
  })
})

describe('buildEventSink → replay suppression after CTRL+R (BUG-0037 follow-up)', () => {
  beforeEach(() => {
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(['ses_a']),
    })
  })

  it('ignores session.idle delivered within 500ms of subscribe (stale replay)', () => {
    vi.useFakeTimers()
    try {
      const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
      const sink = buildEventSink('ses_a', null, qc, null, null)

      // Immediately replayed idle from the previous turn — must be ignored
      // because the backend GET /subscribe replays from cursor 0 by default.
      sink({ id: 1, event: 'session.idle', data: { sessionId: 'ses_a' } })
      expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(true)

      sink({ id: 2, event: 'session.status', data: { status: 'idle' } })
      expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(true)

      // Beyond the 500ms window, a fresh idle is treated as real turn-done.
      vi.advanceTimersByTime(600)
      sink({ id: 3, event: 'session.idle', data: { sessionId: 'ses_a' } })
      expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })

  it('ignores session.error delivered within the 500ms window', () => {
    vi.useFakeTimers()
    try {
      const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
      const sink = buildEventSink('ses_a', null, qc, null, null)

      sink({ id: 1, event: 'session.error', data: { error: 'stale replayed error' } } as any)
      expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(true)

      vi.advanceTimersByTime(600)
      sink({ id: 2, event: 'session.error', data: { error: 'real error' } } as any)
      expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })
})

describe('buildEventSink → session.error (TD-014)', () => {
  beforeEach(() => {
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(['ses_a']),
    })
  })

  // Move the subscribe baseline past the 500ms replay-suppression window so
  // these steady-state assertions are not affected by the BUG-0037 follow-up.
  function buildSinkPastReplayWindow(sessionId: string, qc: QueryClient) {
    const realNow = Date.now
    Date.now = () => realNow.call(Date) - 1000
    try {
      return buildEventSink(sessionId, null, qc, null)
    } finally {
      Date.now = realNow
    }
  }

  it('clears streaming flag on session.error', () => {
    const qc = new QueryClient()
    const sink = buildSinkPastReplayWindow('ses_a', qc)
    sink({ event: 'session.error', data: { error: 'model unavailable' } } as any)
    expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(false)
  })

  it('renders a session.error in the current assistant turn without waiting for history refresh', () => {
    useChatPartsStore.getState().upsertInfo('ses_a', {
      id: 'user_1',
      role: 'user',
      sessionID: 'ses_a',
      time: { created: 100 },
    })
    const qc = new QueryClient()
    const sink = buildSinkPastReplayWindow('ses_a', qc)

    sink({ id: 42, event: 'session.error', data: { error: 'unknown certificate verification error' } } as any)

    const infoMap = useChatPartsStore.getState().infoBySession.get('ses_a')
    const assistant = Array.from(infoMap?.values() ?? []).find((info) => info.role === 'assistant')
    expect(assistant).toMatchObject({
      id: 'session_error_ses_a_42',
      role: 'assistant',
      sessionID: 'ses_a',
      error: {
        name: 'SessionError',
        data: { message: 'unknown certificate verification error' },
      },
    })
    expect(typeof assistant?.time.completed).toBe('number')

    const { result } = renderHook(() => useSessionTurns('ses_a'))
    expect(result.current).toHaveLength(1)
    expect(result.current[0]).toMatchObject({
      userMessageId: 'user_1',
      assistantMessageIds: ['session_error_ses_a_42'],
    })
  })

  it('attaches session.error to an existing assistant message instead of creating a separate error turn', () => {
    useChatPartsStore.getState().upsertInfo('ses_a', {
      id: 'user_1',
      role: 'user',
      sessionID: 'ses_a',
      time: { created: 100 },
    })
    useChatPartsStore.getState().upsertInfo('ses_a', {
      id: 'assistant_1',
      role: 'assistant',
      sessionID: 'ses_a',
      time: { created: 110 },
    })
    const qc = new QueryClient()
    const sink = buildSinkPastReplayWindow('ses_a', qc)

    sink({ id: 43, event: 'session.error', data: { error: 'unknown certificate verification error' } } as any)

    const infoMap = useChatPartsStore.getState().infoBySession.get('ses_a')
    expect(infoMap?.size).toBe(2)
    expect(infoMap?.get('assistant_1')).toMatchObject({
      error: {
        name: 'SessionError',
        data: { message: 'unknown certificate verification error' },
      },
    })
  })
})

describe('buildEventSink → session.created / session.deleted (TD-015)', () => {
  it('invalidates sessions cache on session.created', () => {
    const qc = new QueryClient()
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const sink = buildEventSink('s1', null, qc, 'c1')
    sink({ event: 'session.created', data: { sessionId: 's2', title: 'new', version: 1 } } as any)
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['sessions'] })
  })

  it('invalidates sessions cache on session.deleted', () => {
    const qc = new QueryClient()
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    const sink = buildEventSink('s1', null, qc, 'c1')
    sink({ event: 'session.deleted', data: { sessionId: 's1' } } as any)
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['sessions'] })
  })
})

describe('buildEventSink → session.compacted (TD-016)', () => {
  it('does not throw on session.compacted event', () => {
    const qc = new QueryClient()
    const sink = buildEventSink('s1', null, qc, null)
    expect(() =>
      sink({ event: 'session.compacted', data: { sessionId: 's1' } } as any)
    ).not.toThrow()
  })
})

describe('buildEventSink → session.diff (TD-017)', () => {
  it('does not throw on session.diff event with unknown payload', () => {
    const qc = new QueryClient()
    const sink = buildEventSink('s1', null, qc, null)
    expect(() =>
      sink({ event: 'session.diff', data: { sessionId: 's1', payload: { unknown: true } } } as any)
    ).not.toThrow()
  })
})

describe('useChannel · BUG-0046 stream-lifecycle vs request-lifecycle', () => {
  const wrapper = ({ children }: { children: ReactNode }) => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return createElement(QueryClientProvider, { client: qc }, children)
  }

  beforeEach(() => {
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),
      pendingDeltasBySession: new Map(),
    })
    useSessionStore.setState({ activeSessionId: 's1' } as any)
    useChannelStore.setState({ lastEventIdBySession: new Map(), isConnected: false })
  })

  it('sendMessage clears streaming when POST fails before connected frame (pre-stream failure)', async () => {
    const sendSpy = vi.spyOn(ChannelClient.prototype, 'sendMessage')
      .mockImplementation(async () => { throw new Error('network down') })
    try {
      const { result } = renderHook(() => useChannel(), { wrapper })

      let ok: boolean | undefined
      await act(async () => {
        ok = await result.current.sendMessage([createTextPart('s1', 'hi')])
      })

      expect(ok).toBe(false)
      expect(useChatPartsStore.getState().streamingBySession.has('s1')).toBe(false)
    } finally {
      sendSpy.mockRestore()
    }
  })

  it('sendMessage preserves streaming when POST fails AFTER connected frame (Ctrl+R abort)', async () => {
    const sendSpy = vi.spyOn(ChannelClient.prototype, 'sendMessage')
      .mockImplementation(async (_parts, onEvent) => {
        onEvent({ event: 'connected', id: 1, data: { sessionId: 's1', serverRev: 1 } })
        throw new Error('aborted')
      })
    try {
      const { result } = renderHook(() => useChannel(), { wrapper })

      let ok: boolean | undefined
      await act(async () => {
        ok = await result.current.sendMessage([createTextPart('s1', 'hi')])
      })

      expect(ok).toBe(false)
      // The long-lived GET subscribe sink (not modeled in this test) is
      // expected to clear streaming on the eventual real session.idle.
      expect(useChatPartsStore.getState().streamingBySession.has('s1')).toBe(true)
    } finally {
      sendSpy.mockRestore()
    }
  })

  it('sendMessage clears streaming via session.idle on natural completion (not via finally)', async () => {
    const realNow = Date.now
    const sendSpy = vi.spyOn(ChannelClient.prototype, 'sendMessage')
      .mockImplementation(async (_parts, onEvent) => {
        onEvent({ event: 'connected', id: 1, data: { sessionId: 's1', serverRev: 1 } })
        // Skip past BUG-0038's 500ms replay-suppression window so the idle is
        // honoured by the sink.
        Date.now = () => realNow.call(Date) + 1000
        onEvent({ event: 'session.idle', id: 5, data: { sessionId: 's1' } })
      })
    try {
      const { result } = renderHook(() => useChannel(), { wrapper })

      let ok: boolean | undefined
      await act(async () => {
        ok = await result.current.sendMessage([createTextPart('s1', 'hi')])
      })

      expect(ok).toBe(true)
      expect(useChatPartsStore.getState().streamingBySession.has('s1')).toBe(false)
    } finally {
      Date.now = realNow
      sendSpy.mockRestore()
    }
  })

  it('retryPendingUser preserves streaming when POST aborts AFTER connected frame', async () => {
    const sendSpy = vi.spyOn(ChannelClient.prototype, 'sendMessage')
      .mockImplementation(async (_parts, onEvent) => {
        onEvent({ event: 'connected', id: 1, data: { sessionId: 's1', serverRev: 1 } })
        throw new Error('aborted')
      })
    try {
      const pendingId = useChatPartsStore.getState().upsertPendingUser('s1', 'retry me')
      // Simulate a previous failure so retryPendingUser has a target.
      useChatPartsStore.getState().markPendingUserFailed('s1', pendingId, 'previous error')

      const { result } = renderHook(() => useChannel(), { wrapper })

      let ok: boolean | undefined
      await act(async () => {
        ok = await result.current.retryPendingUser(pendingId, [createTextPart('s1', 'retry me')])
      })

      expect(ok).toBe(false)
      expect(useChatPartsStore.getState().streamingBySession.has('s1')).toBe(true)
    } finally {
      sendSpy.mockRestore()
    }
  })

  it('retryPendingUser clears streaming on pre-stream failure', async () => {
    const sendSpy = vi.spyOn(ChannelClient.prototype, 'sendMessage')
      .mockImplementation(async () => { throw new Error('network down') })
    try {
      const pendingId = useChatPartsStore.getState().upsertPendingUser('s1', 'retry me')
      useChatPartsStore.getState().markPendingUserFailed('s1', pendingId, 'previous error')

      const { result } = renderHook(() => useChannel(), { wrapper })

      let ok: boolean | undefined
      await act(async () => {
        ok = await result.current.retryPendingUser(pendingId, [createTextPart('s1', 'retry me')])
      })

      expect(ok).toBe(false)
      expect(useChatPartsStore.getState().streamingBySession.has('s1')).toBe(false)
    } finally {
      sendSpy.mockRestore()
    }
  })
})

describe('buildEventSink → action.invoke error payloads', () => {
  beforeEach(() => {
    useChannelStore.setState({ lastEventIdBySession: new Map(), isConnected: false })
    __resetCallIdDispatchForTest()
  })

  it('auto-registers built-in client handlers for action.invoke dispatch', () => {
    expect(getClientHandler('datatalk.ui.read')).toBeTypeOf('function')
    expect(getClientHandler('datatalk.ui.patch')).toBeTypeOf('function')
    expect(getClientHandler('datatalk.ui.exec')).toBeTypeOf('function')
  })

  it('preserves structured ui-router detail in action_result errors', async () => {
    uiRouter.registerInstance('query-1', {
      type: 'query_editor',
      objectId: 'query-1',
      title: 'Query 1',
      patchCapabilities: [{ pathPattern: '/content', ops: ['replace'] }],
      read: (mode: Parameters<UIObject['read']>[0]) => {
        if (mode === 'actions') {
          return [
            { name: 'apply_text_edits', description: '', paramsSchema: { type: 'object', properties: {} } },
            { name: 'set_context', description: '', paramsSchema: { type: 'object', properties: {} } },
          ]
        }
        return { content: 'select 1' }
      },
      patch: async () => ({ status: 'applied' }),
      exec: async () => ({ success: true }),
    } as any)

    const qc = new QueryClient()
    const client = {
      actionResult: vi.fn().mockResolvedValue(undefined),
    } as any
    const sink = buildEventSink('s1', client, qc, null)

    sink({
      event: 'action.invoke',
      data: {
        callId: 'call-1',
        actionId: 'datatalk.ui.patch',
        input: {
          object: 'query_editor',
          target: 'query-1',
          ops: [{ op: 'replace', path: '/title', value: 'bad' }],
        },
      },
    } as any)

    await waitFor(() => expect(client.actionResult).toHaveBeenCalledTimes(1))
    expect(client.actionResult).toHaveBeenCalledWith(
      'call-1',
      false,
      undefined,
      expect.objectContaining({
        code: 'unsupported_patch',
        message: expect.stringContaining('Unsupported'),
        details: expect.objectContaining({
          code: 'unsupported_patch',
          hint: expect.stringContaining('/content'),
          availableActions: ['apply_text_edits', 'set_context'],
        }),
      }),
    )
  })

  it('dispatches an action.invoke only once per callId even when delivered to multiple sinks', async () => {
    const handler = vi.fn().mockResolvedValue({ tabId: 'q1' })
    const { registerClientHandler } = await import('@/features/actions/registry')
    registerClientHandler('datatalk.test.dedupe', handler)

    const qc = new QueryClient()
    const client = { actionResult: vi.fn().mockResolvedValue(undefined) } as any
    // Two sinks for the same session, simulating the GET /subscribe stream and
    // a concurrent POST /send_message stream both receiving the bus event.
    const sinkA = buildEventSink('s1', client, qc, null)
    const sinkB = buildEventSink('s1', client, qc, null)

    const event = {
      event: 'action.invoke',
      data: { callId: 'call-dup', actionId: 'datatalk.test.dedupe', input: {} },
    } as any

    sinkA(event)
    sinkB(event)
    sinkA(event) // also covers a replay after POST resubscribes from cursor 0

    await waitFor(() => expect(client.actionResult).toHaveBeenCalledTimes(1))
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('does not replay an already-dispatched action.invoke after a browser refresh', async () => {
    const handler = vi.fn().mockResolvedValue({ tabId: 'q1' })
    const { registerClientHandler } = await import('@/features/actions/registry')
    registerClientHandler('datatalk.test.refresh_replay', handler)

    const qc = new QueryClient()
    const client = { actionResult: vi.fn().mockResolvedValue(undefined) } as any
    const event = {
      event: 'action.invoke',
      data: { callId: 'call-refresh-replay', actionId: 'datatalk.test.refresh_replay', input: {} },
    } as any

    buildEventSink('s1', client, qc, null)(event)
    await waitFor(() => expect(client.actionResult).toHaveBeenCalledTimes(1))
    expect(handler).toHaveBeenCalledTimes(1)

    __resetCallIdDispatchForTest({ keepPersisted: true })
    buildEventSink('s1', client, qc, null)(event)

    expect(handler).toHaveBeenCalledTimes(1)
    expect(client.actionResult).toHaveBeenCalledTimes(1)
  })

  it('returns an explicit error when a client action handler is not registered', async () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const qc = new QueryClient()
    const client = {
      actionResult: vi.fn().mockResolvedValue(undefined),
    } as any
    const sink = buildEventSink('s1', client, qc, null)

    sink({
      event: 'action.invoke',
      data: {
        callId: 'call-missing',
        actionId: 'datatalk.unknown.client_action',
        input: {},
      },
    } as any)

    await waitFor(() => expect(client.actionResult).toHaveBeenCalledTimes(1))
    expect(client.actionResult).toHaveBeenCalledWith(
      'call-missing',
      false,
      undefined,
      expect.objectContaining({
        code: 'client_action_not_registered',
        message: expect.stringContaining('datatalk.unknown.client_action'),
        details: expect.objectContaining({
          actionId: 'datatalk.unknown.client_action',
        }),
      }),
    )
    expect(warnSpy).toHaveBeenCalledWith(
      '[channel] missing client action handler',
      expect.objectContaining({
        actionId: 'datatalk.unknown.client_action',
        callId: 'call-missing',
      }),
    )
    warnSpy.mockRestore()
  })
})
