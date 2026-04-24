import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { render, act, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SplitView } from './split-view'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'

const useOpencodeHealthMock = vi.hoisted(() => vi.fn())

vi.mock('@/components/ui/sidebar', () => ({
  useSidebar: () => ({ state: 'expanded' }),
}))

vi.mock('@/features/session/hooks/use-opencode-health', () => ({
  useOpencodeHealth: useOpencodeHealthMock,
}))


const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

function wrapper({ children }: { children: React.ReactNode }) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

function findStagePanel(container: HTMLElement): HTMLElement {
  const el = container.querySelector('[data-stage-panel]') as HTMLElement | null
  if (!el) throw new Error('stage panel not found')
  return el
}

describe('SplitView stage panel', () => {
  beforeEach(() => {
    queryClient.clear()
    vi.useFakeTimers()
    useOpencodeHealthMock.mockReturnValue({
      data: { status: 'ok', timestamp: '2026-04-24T00:00:00Z', message: 'OpenCode MCP bridge ready', reason: null },
    })
    Object.defineProperty(HTMLElement.prototype, 'scrollTo', {
      configurable: true,
      value: vi.fn(),
    })
    useStageStore.setState({
      openBySession: new Map(),
      autoOpenedSessions: new Set(),
      maximizedBySession: new Map(),
      revealOrigin: null,
    })
    useSessionStore.setState({
      activeSessionId: 's1',
      modeBySession: new Map([['s1', 'SPLIT']]),
      hasEverSentBySession: new Map(),
      pendingPrompt: null,
    })
    useChatPartsStore.setState({ partsBySession: new Map() })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('open=false 时 stage 容器 transform 为 translateX(100%)', () => {
    const { container } = render(<SplitView />, { wrapper })
    const panel = findStagePanel(container)
    // 关闭态：transform 滑出
    expect(panel.style.transform).toMatch(/100%/)
  })

  it('open=true + revealOrigin 有值 → 面板可见，style 正确', () => {
    useStageStore.setState({
      openBySession: new Map([['s1', true]]),
      revealOrigin: { x: 100, y: 300 },
    })
    const { container } = render(<SplitView />, { wrapper })
    const panel = findStagePanel(container)
    expect(panel.style.transform).toBe('translateX(0)')
    expect(panel.style.width).toBeDefined()
  })

  it('open=true + revealOrigin=null → 面板正常渲染', () => {
    useStageStore.setState({ openBySession: new Map([['s1', true]]) })
    const { container } = render(<SplitView />, { wrapper })
    const panel = findStagePanel(container)
    expect(panel.style.transform).toBe('translateX(0)')
  })

  it('关闭时 transform 切换到 100%，打开时切换回 0', () => {
    useStageStore.setState({ openBySession: new Map([['s1', true]]) })
    const { container, rerender } = render(<SplitView />, { wrapper })
    let panel = findStagePanel(container)
    expect(panel.style.transform).toBe('translateX(0)')

    act(() => {
      useStageStore.setState({ openBySession: new Map([['s1', false]]) })
    })
    rerender(<SplitView />)
    panel = findStagePanel(container)
    // style 立即更新，CSS transition 负责动画
    expect(panel.style.transform).toBe('translateX(100%)')

    // 重新打开
    act(() => {
      useStageStore.setState({ openBySession: new Map([['s1', true]]) })
    })
    rerender(<SplitView />)
    panel = findStagePanel(container)
    expect(panel.style.transform).toBe('translateX(0)')
  })

  it('disables browser scroll anchoring on the chat scroller', () => {
    useSessionStore.setState({
      activeSessionId: 's1',
      modeBySession: new Map([['s1', 'SPLIT']]),
      hasEverSentBySession: new Map([['s1', true]]),
      pendingPrompt: null,
    })

    const { container } = render(<SplitView />, { wrapper })
    const scroller = container.querySelector('.flex-1.overflow-y-auto') as HTMLElement | null

    expect(scroller).not.toBeNull()
    expect(scroller?.style.overflowAnchor).toBe('none')
  })

  it('renders a degraded bridge notice when MCP health is degraded', () => {
    useOpencodeHealthMock.mockReturnValue({
      data: {
        status: 'degraded',
        timestamp: '2026-04-24T00:00:00Z',
        message: 'OpenCode MCP bridge degraded',
        reason: 'plugin not loaded',
      },
    })

    render(<SplitView />, { wrapper })

    expect(screen.getByText('AI 工具桥未就绪')).toBeTruthy()
    expect(screen.getByText('原因：plugin not loaded')).toBeTruthy()
  })

  it('does not render a degraded bridge notice when MCP health is ok', () => {
    render(<SplitView />, { wrapper })

    expect(screen.queryByText('AI 工具桥未就绪')).toBeNull()
  })
})
