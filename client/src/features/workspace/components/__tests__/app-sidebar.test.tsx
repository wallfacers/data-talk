import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AppSidebar } from '../app-sidebar'
import { SidebarProvider } from '@/components/ui/sidebar'

vi.mock('@/services/api/session', () => ({
  createSession: vi.fn(),
}))
vi.mock('@/features/connection/store', () => ({
  useConnectionStore: (sel: any) => sel({ activeConnectionId: 'c1' }),
}))
vi.mock('@/stores/session-store', () => ({
  useSessionStore: (sel: any) => sel({ openSession: vi.fn() }),
}))
vi.mock('@/features/session/hooks/use-has-active-model', () => ({
  useHasActiveModel: () => true,
}))
vi.mock('../nav-sessions', () => ({ NavSessions: () => null }))
vi.mock('../nav-tabs', () => ({ NavTabs: () => null }))
vi.mock('../nav-user', () => ({ NavUser: () => null }))

import { createSession } from '@/services/api/session'

function renderWithProviders(initialSessions: any[] = [], sidebarOpen = true) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  qc.setQueryData(['sessions', 'c1'], initialSessions)
  render(
    <QueryClientProvider client={qc}>
      <SidebarProvider open={sidebarOpen} onOpenChange={vi.fn()}>
        <AppSidebar />
      </SidebarProvider>
    </QueryClientProvider>,
  )
  return { qc }
}

describe('AppSidebar — 创建会话', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('本地缓存无空白 session → 点击触发 createSession HTTP', async () => {
    (createSession as any).mockResolvedValueOnce({
      id: 'new_1', connectionId: 'c1', title: '新会话',
      hasEverSent: false, createdAt: 1, updatedAt: 1, titleLocked: false,
      reusedEmpty: false,
    })
    renderWithProviders([
      { id: 'old', hasEverSent: true, title: 'old', connectionId: 'c1',
        createdAt: 0, updatedAt: 0, titleLocked: false, reusedEmpty: false },
    ])

    fireEvent.click(screen.getByText('创建会话'))

    await waitFor(() => expect(createSession).toHaveBeenCalledTimes(1))
  })

  it('创建会话 CTA 是唯一强调动作', () => {
    renderWithProviders()

    const cta = screen.getByRole('button', { name: '创建会话' })
    const toggle = screen.getByRole('button', { name: 'Toggle Sidebar' })

    expect(cta).toHaveClass('bg-primary')
    expect(cta).toHaveClass('text-primary-foreground')
    expect(toggle).not.toHaveClass('bg-primary')
    expect(toggle).not.toHaveClass('text-primary-foreground')
  })

  it('品牌图标使用设计规范的主强调色语义 token', () => {
    renderWithProviders()

    const homeLink = screen.getByRole('link', { name: 'DataTalk' })
    const brandIcon = homeLink.querySelector('svg')

    if (!brandIcon) throw new Error('expected brand icon')

    expect(brandIcon).toHaveClass('text-primary')
  })

  it('收起后浮动控制壳使用更强的边框仪表盘样式', async () => {
    renderWithProviders([], false)

    const createButton = screen.getAllByRole('button', { name: '创建会话' })[0]
    const floatingShell = createButton.parentElement

    if (!floatingShell) throw new Error('expected floating shell container')

    expect(floatingShell).toContainElement(createButton)
    expect(floatingShell).toHaveClass('bg-sidebar/92')
    expect(floatingShell).toHaveClass('ring-1')
    expect(floatingShell).toHaveClass('ring-sidebar-border')
  })

  it('本地缓存已有空白 session → 点击不触发 HTTP', async () => {
    renderWithProviders([
      { id: 'empty_1', hasEverSent: false, title: '新会话', connectionId: 'c1',
        createdAt: 0, updatedAt: 0, titleLocked: false, reusedEmpty: false },
    ])

    fireEvent.click(screen.getByText('创建会话'))

    await waitFor(() => expect(createSession).not.toHaveBeenCalled(), { timeout: 100 })
  })

  it('isPending 期间多次点击仅触发一次 mutate', async () => {
    let resolveFn: (v: any) => void = () => {}
    ;(createSession as any).mockImplementationOnce(
      () => new Promise((r) => { resolveFn = r }),
    )
    renderWithProviders([])

    const btn = screen.getByText('创建会话')
    fireEvent.click(btn)
    fireEvent.click(btn)
    fireEvent.click(btn)

    await waitFor(() => expect(createSession).toHaveBeenCalledTimes(1))

    resolveFn({
      id: 'x', connectionId: 'c1', title: '新会话',
      hasEverSent: false, createdAt: 1, updatedAt: 1, titleLocked: false,
      reusedEmpty: false,
    })
  })
})
