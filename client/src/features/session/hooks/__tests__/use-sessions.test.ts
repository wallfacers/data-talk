import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getSessionsQueryKey, useSessions } from '../use-sessions'
import * as sessionApi from '@/services/api/session'

vi.mock('@/features/connection/store', () => ({
  useConnectionStore: (selector: (state: { activeConnectionId: string | null }) => unknown) =>
    selector({ activeConnectionId: 'conn-1' }),
}))

vi.mock('@/services/api/session', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/services/api/session')>()
  return {
    ...actual,
    listSessions: vi.fn(),
  }
})

function wrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return {
    qc,
    Wrapper({ children }: { children: ReactNode }) {
      return createElement(QueryClientProvider, { client: qc }, children)
    },
  }
}

const session = (overrides: Partial<sessionApi.Session>): sessionApi.Session => ({
  id: 'session-1',
  connectionId: 'conn-1',
  title: 'Real session',
  hasEverSent: true,
  createdAt: 1,
  updatedAt: 1,
  titleLocked: false,
  reusedEmpty: false,
  ...overrides,
})

describe('useSessions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('filters internal stage-ai sessions before they reach the cache or consumer', async () => {
    vi.mocked(sessionApi.listSessions).mockResolvedValue([
      session({ id: 'visible-1', title: 'Real session' }),
      session({ id: 'hidden-1', title: '_stage-ai_Orders SQL' }),
      session({ id: 'visible-2', title: 'Another session' }),
    ])

    const { qc, Wrapper } = wrapper()
    const { result } = renderHook(() => useSessions('active'), { wrapper: Wrapper })

    await waitFor(() => expect(result.current.data?.map((item) => item.id)).toEqual(['visible-1', 'visible-2']))
    expect(qc.getQueryData(getSessionsQueryKey('conn-1'))).toEqual([
      session({ id: 'visible-1', title: 'Real session' }),
      session({ id: 'visible-2', title: 'Another session' }),
    ])
    expect(vi.mocked(sessionApi.listSessions)).toHaveBeenCalledWith('conn-1')
  })
})
