import { describe, it, expect, beforeEach, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { http } from '@/services/http'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'
import { useSessionHistory } from '../use-session-history'

function wrapper(qc: QueryClient) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
}

function mockEmptyHttp() {
  vi.spyOn(http, 'get').mockImplementation(((input: any) => ({
    json: async () =>
      String(input).endsWith('/messages') ? [] : { artifacts: [] },
  })) as any)
}

const SID = 'sess-1'

describe('useSessionHistory — replace guard', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),
    } as any)
    useOntologyStore.setState({ artifactsBySession: new Map() } as any)
    useTimelineStore.setState((s) => s)
  })

  it('skips replaceSession while session is streaming (preserves pending user + streamed parts)', async () => {
    mockEmptyHttp()
    // Simulate a mid-stream state: pending user message present, streaming flag on.
    useChatPartsStore.getState().upsertPendingUser(SID, '你好')
    useChatPartsStore.getState().setStreaming(SID, true)
    const beforeSize = useChatPartsStore.getState().infoBySession.get(SID)?.size ?? 0
    expect(beforeSize).toBe(1)

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => useSessionHistory(SID), { wrapper: wrapper(qc) })

    await waitFor(() => {
      // Give react-query a tick to resolve & the effect a chance to run.
      expect(http.get).toHaveBeenCalled()
    })
    await new Promise((r) => setTimeout(r, 20))

    // Pending user must still be there — nothing was wiped.
    const after = useChatPartsStore.getState().infoBySession.get(SID)?.size ?? 0
    expect(after).toBe(1)
  })

  it('skips replaceSession when server returns empty but store already has messages (post-stream remount)', async () => {
    mockEmptyHttp()
    // Simulate: streaming finished, store populated by SSE, a late re-subscriber mounts.
    useChatPartsStore.getState().upsertInfo(SID, {
      id: 'msg_real',
      role: 'user',
      sessionID: SID,
      time: { created: 1 },
    })
    useChatPartsStore.getState().setStreaming(SID, false)
    expect(useChatPartsStore.getState().infoBySession.get(SID)?.size).toBe(1)

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => useSessionHistory(SID), { wrapper: wrapper(qc) })

    await waitFor(() => expect(http.get).toHaveBeenCalled())
    await new Promise((r) => setTimeout(r, 20))

    expect(useChatPartsStore.getState().infoBySession.get(SID)?.size).toBe(1)
  })

  it('applies server history normally on a fresh session (empty store)', async () => {
    vi.spyOn(http, 'get').mockImplementation(((input: any) => ({
      json: async () =>
        String(input).endsWith('/messages')
          ? [
              {
                info: { id: 'm1', role: 'assistant', sessionID: SID, time: { created: 1 } },
                parts: [{ type: 'text', id: 'p1', sessionID: SID, messageID: 'm1', text: 'hi', metadata: {} }],
              },
            ]
          : { artifacts: [] },
    })) as any)

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => useSessionHistory(SID), { wrapper: wrapper(qc) })

    await waitFor(() => {
      expect(useChatPartsStore.getState().infoBySession.get(SID)?.size).toBe(1)
    })
  })

  it('preserves bang query metadata when hydrating synthetic user history', async () => {
    vi.spyOn(http, 'get').mockImplementation(((input: any) => ({
      json: async () =>
        String(input).endsWith('/messages')
          ? [
              {
                info: { id: 'sqm-1', role: 'user', sessionID: SID, time: { created: 1 } },
                parts: [
                  {
                    type: 'text',
                    id: 'p1',
                    sessionID: SID,
                    messageID: 'sqm-1',
                    text: '!select 1',
                    metadata: { displayKind: 'bang_query_user', queryMode: 'direct_sql' },
                  },
                ],
              },
            ]
          : { artifacts: [] },
    })) as any)

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => useSessionHistory(SID), { wrapper: wrapper(qc) })

    await waitFor(() => {
      expect(useChatPartsStore.getState().infoBySession.get(SID)?.size).toBe(1)
    })

    const parts = useChatPartsStore.getState().partsBySession.get(SID)?.get('sqm-1') ?? []
    expect(parts[0]).toMatchObject({
      type: 'text',
      text: '!select 1',
      metadata: { displayKind: 'bang_query_user', queryMode: 'direct_sql' },
    })
  })
})
