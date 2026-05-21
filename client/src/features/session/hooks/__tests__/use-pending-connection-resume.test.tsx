import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as sessionApi from '@/services/api/session'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { usePendingConnectionResume } from '../use-pending-connection-resume'

vi.mock('@/services/api/session')
vi.mock('../use-has-active-model', () => ({
  useHasActiveModel: () => true,
}))

function wrapper(qc: QueryClient) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
}

describe('usePendingConnectionResume', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.mocked(sessionApi.listSessions).mockResolvedValue([])
    useConnectionStore.setState({ activeConnectionId: null, connections: [] })
    useSessionStore.setState({
      activeSessionId: null,
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      pendingPrompt: null,
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
    } as any)
  })

  it('creates session after connection is chosen for a pending send on no active session', async () => {
    vi.mocked(sessionApi.createSession).mockResolvedValue({
      id: 's1',
      connectionId: 'c1',
      title: 'show me orders',
      hasEverSent: false,
      createdAt: 1,
      updatedAt: 1,
      titleLocked: false,
      reusedEmpty: false,
    })

    useConnectionStore.getState().setActive('c1')
    useSessionStore.getState().setPendingPrompt('show me orders')
    useSessionStore.getState().setPendingConnectionPrompt(false)
    useSessionStore.getState().setPendingActionAfterConnectionPick({ kind: 'send' })

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => usePendingConnectionResume(), { wrapper: wrapper(qc) })

    await waitFor(() => {
      expect(sessionApi.createSession).toHaveBeenCalledWith('c1', 'show me orders')
      expect(useSessionStore.getState().activeSessionId).toBe('s1')
    })
  })

  it('creates a rebound session when the current active session is not bound to the chosen connection', async () => {
    vi.mocked(sessionApi.createSession).mockResolvedValue({
      id: 's2',
      connectionId: 'c1',
      title: 'show me orders',
      hasEverSent: false,
      createdAt: 1,
      updatedAt: 1,
      titleLocked: false,
      reusedEmpty: false,
    })

    useConnectionStore.getState().setActive('c1')
    useSessionStore.setState((state) => ({
      ...state,
      activeSessionId: 'blank',
      pendingPrompt: 'show me orders',
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: { kind: 'send' },
    }))

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    qc.setQueryData(['sessions', null], [
      {
        id: 'blank',
        connectionId: null,
        title: '新会话',
        hasEverSent: false,
        createdAt: 1,
        updatedAt: 1,
        titleLocked: false,
        reusedEmpty: false,
      },
    ])

    renderHook(() => usePendingConnectionResume(), { wrapper: wrapper(qc) })

    await waitFor(() => {
      expect(sessionApi.createSession).toHaveBeenCalledWith('c1', 'show me orders')
      expect(useSessionStore.getState().activeSessionId).toBe('s2')
    })
  })
})
