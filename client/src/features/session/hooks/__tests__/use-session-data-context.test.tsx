import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useSessionStore } from '@/stores/session-store'
import * as api from '@/services/api/session-data-context'
import { useSessionDataContext } from '../use-session-data-context'

vi.mock('@/services/api/session-data-context', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/services/api/session-data-context')>()
  return {
    ...actual,
    getSessionDataContext: vi.fn(),
    setSessionDataContext: vi.fn(),
    resolveUseTarget: vi.fn(),
    validateSessionDataContext: vi.fn(),
  }
})

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  }
}

describe('useSessionDataContext', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useSessionStore.setState({ dataContextBySession: new Map() } as any)
  })

  it('loads the current session data context and caches it in the store', async () => {
    const context: api.SessionDataContext = {
      sessionId: 'sess-1',
      connectionId: 'conn-1',
      connectionNameSnapshot: 'orders-prod',
      database: 'orders',
      schema: 'public',
      selectedLevel: 'schema',
      updatedAt: 123,
    }
    vi.mocked(api.getSessionDataContext).mockResolvedValue(context)

    const { result } = renderHook(() => useSessionDataContext('sess-1'), { wrapper: wrapper() })

    await waitFor(() => expect(result.current.context).toEqual(context))
    expect(api.getSessionDataContext).toHaveBeenCalledWith('sess-1')
    expect(useSessionStore.getState().dataContextBySession.get('sess-1')).toEqual(context)
  })

  it('resolves use targets and persists updates back to the API', async () => {
    const initial: api.SessionDataContext = {
      sessionId: 'sess-1',
      connectionId: 'conn-1',
      connectionNameSnapshot: 'orders-prod',
      database: 'orders',
      schema: null,
      selectedLevel: 'database',
      updatedAt: 123,
    }
    const resolved: api.ResolveUseTargetResponse = {
      status: 'matched',
      context: {
        sessionId: 'sess-1',
        connectionId: 'conn-1',
        connectionNameSnapshot: 'orders-prod',
        database: 'orders',
        schema: 'public',
        selectedLevel: 'schema',
        updatedAt: 456,
      },
      matchedTarget: { level: 'schema', label: 'public' },
      candidates: [],
      suggestions: [],
      message: null,
    }
    const updated = resolved.context as api.SessionDataContext
    vi.mocked(api.getSessionDataContext).mockResolvedValue(initial)
    vi.mocked(api.resolveUseTarget).mockResolvedValue(resolved)
    vi.mocked(api.setSessionDataContext).mockResolvedValue(updated)
    vi.mocked(api.validateSessionDataContext).mockResolvedValue(updated)

    const { result } = renderHook(() => useSessionDataContext('sess-1'), { wrapper: wrapper() })
    await waitFor(() => expect(result.current.context).toEqual(initial))

    await act(async () => {
      const match = await result.current.resolveUseTarget('public')
      expect(match.status).toBe('matched')
      await result.current.setSessionDataContext({
        connectionId: match.context?.connectionId ?? null,
        database: match.context?.database ?? null,
        schema: match.context?.schema ?? null,
        selectedLevel: match.context?.selectedLevel ?? null,
      })
      await result.current.validateSessionDataContext()
    })

    expect(api.resolveUseTarget).toHaveBeenCalledWith('sess-1', 'public')
    expect(api.setSessionDataContext).toHaveBeenCalledWith('sess-1', {
      connectionId: 'conn-1',
      database: 'orders',
      schema: 'public',
      selectedLevel: 'schema',
    })
    expect(api.validateSessionDataContext).toHaveBeenCalledWith('sess-1')
    expect(useSessionStore.getState().dataContextBySession.get('sess-1')).toEqual(updated)
  })
})
