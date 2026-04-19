import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { NavSessions } from '../nav-sessions'
import { SidebarProvider } from '@/components/ui/sidebar'
import { useSessionStore } from '@/stores/session-store'
import * as api from '@/services/api/session'

vi.mock('@/features/connection/store', () => ({
  useConnectionStore: (sel: any) => sel({ activeConnectionId: 'c1' }),
}))

function renderWithCache(initial: api.Session[]) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  qc.setQueryData(['sessions', 'c1'], initial)
  render(
    <QueryClientProvider client={qc}>
      <SidebarProvider>
        <NavSessions />
      </SidebarProvider>
    </QueryClientProvider>,
  )
  return qc
}

const mkSession = (over: Partial<api.Session>): api.Session => ({
  id: 'x', connectionId: 'c1', title: 't', hasEverSent: true,
  createdAt: Date.now(), updatedAt: Date.now(),
  titleLocked: false, reusedEmpty: false,
  ...over,
})

describe('NavSessions — 删除当前活跃会话', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    useSessionStore.setState({
      activeSessionId: 'cur',
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      pendingPrompt: null,
      pendingModelPrompt: false,
    })
  })

  it('删除当前会话 → 复用缓存中的空白会话', async () => {
    const delSpy = vi.spyOn(api, 'deleteSession').mockResolvedValue(undefined)
    const createSpy = vi.spyOn(api, 'createSession')
    renderWithCache([
      mkSession({ id: 'cur', title: '当前' }),
      mkSession({ id: 'empty', title: '新会话', hasEverSent: false }),
    ])

    fireEvent.click(screen.getAllByRole('button', { name: '更多' })[0])
    fireEvent.click(await screen.findByText('删除'))
    fireEvent.click(screen.getByText('删除', { selector: 'button' }))

    await waitFor(() => expect(delSpy).toHaveBeenCalledWith('cur'))
    await waitFor(() =>
      expect(useSessionStore.getState().activeSessionId).toBe('empty'),
    )
    expect(createSpy).not.toHaveBeenCalled()
  })

  it('删除当前会话且无空白 → 触发 createSession', async () => {
    const delSpy = vi.spyOn(api, 'deleteSession').mockResolvedValue(undefined)
    const createSpy = vi.spyOn(api, 'createSession').mockResolvedValue(
      mkSession({ id: 'fresh', hasEverSent: false }),
    )
    renderWithCache([mkSession({ id: 'cur', title: '当前' })])

    fireEvent.click(screen.getByRole('button', { name: '更多' }))
    fireEvent.click(await screen.findByText('删除'))
    fireEvent.click(screen.getByText('删除', { selector: 'button' }))

    await waitFor(() => expect(delSpy).toHaveBeenCalledWith('cur'))
    await waitFor(() => expect(createSpy).toHaveBeenCalledTimes(1))
    await waitFor(() =>
      expect(useSessionStore.getState().activeSessionId).toBe('fresh'),
    )
  })

  it('删除非当前会话 → 不动 activeSessionId、不创建空白', async () => {
    const delSpy = vi.spyOn(api, 'deleteSession').mockResolvedValue(undefined)
    const createSpy = vi.spyOn(api, 'createSession')
    renderWithCache([
      mkSession({ id: 'cur', title: '当前' }),
      mkSession({ id: 'other', title: '别的' }),
    ])

    const moreBtns = screen.getAllByRole('button', { name: '更多' })
    fireEvent.click(moreBtns[1])
    fireEvent.click(await screen.findByText('删除'))
    fireEvent.click(screen.getByText('删除', { selector: 'button' }))

    await waitFor(() => expect(delSpy).toHaveBeenCalledWith('other'))
    expect(useSessionStore.getState().activeSessionId).toBe('cur')
    expect(createSpy).not.toHaveBeenCalled()
  })
})
