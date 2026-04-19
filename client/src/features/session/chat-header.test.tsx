import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ChatHeader } from './chat-header'
import { useSessionStore } from '@/stores/session-store'
import * as api from '@/services/api/session'

// Mock useSidebar since ChatHeader uses it
vi.mock('@/components/ui/sidebar', () => ({
  useSidebar: () => ({ state: 'expanded' }),
}))

function renderWithClient(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

describe('ChatHeader', () => {
  beforeEach(() => {
    useSessionStore.setState({
      activeSessionId: 's1',
      modeBySession: new Map([['s1', 'SPLIT']]),
      hasEverSentBySession: new Map([['s1', true]]),
      pendingPrompt: null,
    })
    vi.restoreAllMocks()
  })

  it('重命名 → 调用 renameSession PATCH', async () => {
    const spy = vi.spyOn(api, 'renameSession').mockResolvedValue({
      id: 's1', connectionId: 'c1', title: '新名', hasEverSent: true,
      createdAt: 0, updatedAt: 1, titleLocked: false, reusedEmpty: false,
    })

    renderWithClient(<ChatHeader />)
    fireEvent.click(screen.getByRole('button'))
    fireEvent.click(screen.getByText('重命名'))

    // Inline editing: type new title, press Enter to commit
    const input = await screen.findByRole('textbox')
    fireEvent.change(input, { target: { value: '新名' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    await waitFor(() => expect(spy).toHaveBeenCalledWith('s1', '新名'))
  })

  it('删除 → 调用 deleteSession DELETE，并跳转到新建空白会话', async () => {
    const delSpy = vi.spyOn(api, 'deleteSession').mockResolvedValue(undefined)
    const createSpy = vi.spyOn(api, 'createSession').mockResolvedValue({
      id: 'new', connectionId: null, title: '新会话', hasEverSent: false,
      createdAt: 0, updatedAt: 0, titleLocked: false, reusedEmpty: false,
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    renderWithClient(<ChatHeader />)
    fireEvent.click(screen.getByRole('button'))
    fireEvent.click(screen.getByText('删除'))

    await waitFor(() => expect(delSpy).toHaveBeenCalledWith('s1'))
    await waitFor(() => expect(createSpy).toHaveBeenCalledTimes(1))
    await waitFor(() =>
      expect(useSessionStore.getState().activeSessionId).toBe('new'),
    )
  })

  it('删除 → 缓存中已有空白会话则复用，不触发 createSession', async () => {
    const delSpy = vi.spyOn(api, 'deleteSession').mockResolvedValue(undefined)
    const createSpy = vi.spyOn(api, 'createSession')
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    qc.setQueryData(['sessions', null], [
      { id: 's1', connectionId: null, title: 'a', hasEverSent: true,
        createdAt: 0, updatedAt: 0, titleLocked: false, reusedEmpty: false },
      { id: 'empty', connectionId: null, title: '新会话', hasEverSent: false,
        createdAt: 0, updatedAt: 0, titleLocked: false, reusedEmpty: false },
    ])
    render(<QueryClientProvider client={qc}><ChatHeader /></QueryClientProvider>)

    fireEvent.click(screen.getByRole('button'))
    fireEvent.click(screen.getByText('删除'))

    await waitFor(() => expect(delSpy).toHaveBeenCalledWith('s1'))
    await waitFor(() =>
      expect(useSessionStore.getState().activeSessionId).toBe('empty'),
    )
    expect(createSpy).not.toHaveBeenCalled()
  })

  it('取消重命名 → 不调用 API', async () => {
    const spy = vi.spyOn(api, 'renameSession')

    renderWithClient(<ChatHeader />)
    fireEvent.click(screen.getByRole('button'))
    fireEvent.click(screen.getByText('重命名'))

    // Open inline editing, then press Escape to cancel
    const input = await screen.findByRole('textbox')
    fireEvent.keyDown(input, { key: 'Escape' })

    expect(spy).not.toHaveBeenCalled()
  })

  it('取消删除 → 不调用 API', async () => {
    const spy = vi.spyOn(api, 'deleteSession')
    vi.spyOn(window, 'confirm').mockReturnValue(false)

    renderWithClient(<ChatHeader />)
    fireEvent.click(screen.getByRole('button'))
    fireEvent.click(screen.getByText('删除'))

    expect(spy).not.toHaveBeenCalled()
  })

  it('reflects updated session title when useSessions returns new title', async () => {
    const initialSession: api.Session = {
      id: 's1',
      connectionId: null,
      title: '新会话',
      hasEverSent: true,
      createdAt: 0,
      updatedAt: 0,
      titleLocked: false,
      reusedEmpty: false,
    }

    const listSpy = vi.spyOn(api, 'listSessions').mockResolvedValue([initialSession])

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={qc}>
        <ChatHeader />
      </QueryClientProvider>
    )

    // Wait for initial render with title "新会话"
    await waitFor(() => expect(screen.getByText('新会话')).toBeInTheDocument())

    // Simulate API returning updated title (after invalidate + refetch)
    const updatedSession: api.Session = {
      ...initialSession,
      title: 'AI 标题',
    }
    listSpy.mockResolvedValue([updatedSession])

    // Invalidate the query to trigger refetch
    await act(async () => {
      await qc.invalidateQueries({ queryKey: ['sessions', null] })
    })

    // Title should update to "AI 标题"
    await waitFor(() => expect(screen.getByText('AI 标题')).toBeInTheDocument())

    listSpy.mockRestore()
  })
})
