import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ChatHeader } from './chat-header'
import { useSessionStore } from '@/stores/session-store'
import * as api from '@/services/api/session'

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
      pendingConnectionPrompt: false,
    })
    vi.restoreAllMocks()
  })

  it('重命名 → 调用 renameSession PATCH', async () => {
    const spy = vi.spyOn(api, 'renameSession').mockResolvedValue({
      id: 's1', connectionId: 'c1', title: '新名', hasEverSent: true,
      createdAt: 0, updatedAt: 1,
    })
    vi.spyOn(window, 'prompt').mockReturnValue('新名')

    renderWithClient(<ChatHeader />)
    fireEvent.click(screen.getByRole('button'))
    fireEvent.click(screen.getByText('重命名'))

    await waitFor(() => expect(spy).toHaveBeenCalledWith('s1', '新名'))
  })

  it('删除 → 调用 deleteSession DELETE', async () => {
    const spy = vi.spyOn(api, 'deleteSession').mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    renderWithClient(<ChatHeader />)
    fireEvent.click(screen.getByRole('button'))
    fireEvent.click(screen.getByText('删除'))

    await waitFor(() => expect(spy).toHaveBeenCalledWith('s1'))
  })

  it('取消重命名 → 不调用 API', async () => {
    const spy = vi.spyOn(api, 'renameSession')
    vi.spyOn(window, 'prompt').mockReturnValue(null)

    renderWithClient(<ChatHeader />)
    fireEvent.click(screen.getByRole('button'))
    fireEvent.click(screen.getByText('重命名'))

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
})
