import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { render, act, fireEvent, screen } from '@testing-library/react'
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
const originalScrollTo = HTMLElement.prototype.scrollTo
let scrollToSpy: ReturnType<typeof vi.fn>

function wrapper({ children }: { children: React.ReactNode }) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

function findStagePanel(container: HTMLElement): HTMLElement {
  const el = container.querySelector('[data-stage-panel]') as HTMLElement | null
  if (!el) throw new Error('stage panel not found')
  return el
}

function estimateTurnHeight(turn: HTMLElement | null): number {
  if (!turn) return 0

  let height = 16

  if (turn.querySelector('.bg-primary')) {
    height += 56
  }

  const textParts = Array.from(turn.querySelectorAll('[data-component="text-part"]'))
  for (const textPart of textParts) {
    height += 48
    if (textPart.querySelector('.mt-1.flex.items-center.gap-2.text-xs.text-muted-foreground')) {
      height += 16
    }
  }

  if (turn.querySelector('[aria-label="思考中…"]')) {
    height += 36
  }

  if (turn.querySelector('.min-h-9') && textParts.length === 0 && !turn.querySelector('[aria-label="思考中…"]')) {
    height += 36
  }

  return height
}

function estimateChatScrollHeight(scroller: HTMLElement): number {
  return 32 + Array.from(scroller.querySelectorAll('[data-component="session-turn"]'))
    .reduce((sum, turn) => sum + estimateTurnHeight(turn as HTMLElement), 0)
}

function attachDynamicScrollMetrics(el: HTMLDivElement, metrics: { clientHeight: number; scrollTop: number }) {
  Object.defineProperty(el, 'clientHeight', {
    configurable: true,
    get: () => metrics.clientHeight,
  })
  Object.defineProperty(el, 'scrollHeight', {
    configurable: true,
    get: () => estimateChatScrollHeight(el),
  })
  Object.defineProperty(el, 'scrollTop', {
    configurable: true,
    get: () => metrics.scrollTop,
    set: (value: number) => {
      metrics.scrollTop = value
    },
  })
}

function seedTextTurn(ids: {
  userId: string
  userText: string
  userCreated: number
  assistantId: string
  assistantText: string
  assistantCreated: number
}) {
  const store = useChatPartsStore.getState()
  store.upsertInfo('s1', {
    id: ids.userId,
    role: 'user',
    sessionID: 's1',
    time: { created: ids.userCreated },
  })
  store.upsertPart('s1', {
    type: 'text',
    id: `${ids.userId}-text`,
    sessionID: 's1',
    messageID: ids.userId,
    text: ids.userText,
    metadata: {},
  } as any)
  store.upsertInfo('s1', {
    id: ids.assistantId,
    role: 'assistant',
    sessionID: 's1',
    modelID: 'deepseek-chat',
    time: { created: ids.assistantCreated },
  })
  store.upsertPart('s1', {
    type: 'text',
    id: `${ids.assistantId}-text`,
    sessionID: 's1',
    messageID: ids.assistantId,
    text: ids.assistantText,
    metadata: {},
  } as any)
}

describe('SplitView stage panel', () => {
  beforeEach(() => {
    queryClient.clear()
    vi.useFakeTimers()
    scrollToSpy = vi.fn(function scrollTo(this: HTMLElement, options?: ScrollToOptions | number) {
      if (typeof options === 'object' && options && typeof options.top === 'number') {
        ;(this as HTMLDivElement).scrollTop = options.top
      }
    })
    Object.defineProperty(HTMLElement.prototype, 'scrollTo', {
      configurable: true,
      writable: true,
      value: scrollToSpy,
    })
    useOpencodeHealthMock.mockReturnValue({
      data: { status: 'ok', timestamp: '2026-04-24T00:00:00Z', message: 'OpenCode MCP bridge ready', reason: null },
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
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),
      pendingDeltasBySession: new Map(),
      version: 0,
      layoutVersion: 0,
      userSendVersion: 0,
    })
  })

  afterEach(() => {
    vi.useRealTimers()
    Object.defineProperty(HTMLElement.prototype, 'scrollTo', {
      configurable: true,
      writable: true,
      value: originalScrollTo,
    })
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

  it('leaves browser scroll anchoring enabled on the chat scroller', () => {
    // Scroll anchoring must stay at its default (auto) so the browser
    // compensates scrollTop when content above the viewport shrinks
    // (reasoning collapse, code→chart fence transition, SQL action bar
    // appearing on fence close). Our scrollToBottom still wins at the
    // bottom because it runs after layout.
    useSessionStore.setState({
      activeSessionId: 's1',
      modeBySession: new Map([['s1', 'SPLIT']]),
      hasEverSentBySession: new Map([['s1', true]]),
      pendingPrompt: null,
    })

    const { container } = render(<SplitView />, { wrapper })
    const scroller = container.querySelector('.flex-1.overflow-y-auto') as HTMLElement | null

    expect(scroller).not.toBeNull()
    // Empty string means the inline style is not set; the computed value
    // will fall back to the browser default "auto".
    expect(scroller?.style.overflowAnchor ?? '').not.toBe('none')
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

  it('shows a floating button to return to the bottom after scrolling away', () => {
    seedTextTurn({
      userId: 'u_scroll_1',
      userText: 'scroll question 1',
      userCreated: 1,
      assistantId: 'a_scroll_1',
      assistantText: 'scroll answer 1',
      assistantCreated: 2,
    })
    seedTextTurn({
      userId: 'u_scroll_2',
      userText: 'scroll question 2',
      userCreated: 3,
      assistantId: 'a_scroll_2',
      assistantText: 'scroll answer 2',
      assistantCreated: 4,
    })
    seedTextTurn({
      userId: 'u_scroll_3',
      userText: 'scroll question 3',
      userCreated: 5,
      assistantId: 'a_scroll_3',
      assistantText: 'scroll answer 3',
      assistantCreated: 6,
    })
    useSessionStore.setState({
      activeSessionId: 's1',
      modeBySession: new Map([['s1', 'SPLIT']]),
      hasEverSentBySession: new Map([['s1', true]]),
      pendingPrompt: null,
    })

    const { container } = render(<SplitView />, { wrapper })
    const scroller = container.querySelector('.flex-1.overflow-y-auto') as HTMLDivElement | null
    expect(scroller).not.toBeNull()

    const metrics = { clientHeight: 120, scrollTop: 0 }
    attachDynamicScrollMetrics(scroller!, metrics)
    metrics.scrollTop = Math.max(0, scroller!.scrollHeight - metrics.clientHeight)
    fireEvent.scroll(scroller!)
    expect(screen.queryByRole('button', { name: '回到底部' })).toBeNull()
    scrollToSpy.mockClear()

    metrics.scrollTop = 0
    fireEvent.scroll(scroller!)

    const button = screen.getByRole('button', { name: '回到底部' })
    expect(button).toBeVisible()

    fireEvent.click(button)

    expect(scrollToSpy).toHaveBeenCalledWith({
      top: scroller!.scrollHeight,
      behavior: 'smooth',
    })
    expect(screen.queryByRole('button', { name: '回到底部' })).toBeNull()
  })

  it('scrolls only by the new pending turn height on the second send when the chat is already scrollable', () => {
    const store = useChatPartsStore.getState()
    const seedTurn = (ids: {
      userId: string
      userText: string
      userCreated: number
      assistantId: string
      assistantText: string
      assistantCreated: number
      assistantCompleted?: number
    }) => {
      store.upsertInfo('s1', {
        id: ids.userId,
        role: 'user',
        sessionID: 's1',
        time: { created: ids.userCreated },
      })
      store.upsertPart('s1', {
        type: 'text',
        id: `${ids.userId}-text`,
        sessionID: 's1',
        messageID: ids.userId,
        text: ids.userText,
        metadata: {},
      } as any)
      store.upsertInfo('s1', {
        id: ids.assistantId,
        role: 'assistant',
        sessionID: 's1',
        modelID: 'deepseek-chat',
        time: {
          created: ids.assistantCreated,
          completed: ids.assistantCompleted,
        },
      })
      store.upsertPart('s1', {
        type: 'text',
        id: `${ids.assistantId}-text`,
        sessionID: 's1',
        messageID: ids.assistantId,
        text: ids.assistantText,
        metadata: {},
      } as any)
    }

    seedTurn({
      userId: 'u_hist_1',
      userText: 'history question 1',
      userCreated: 1,
      assistantId: 'a_hist_1',
      assistantText: 'history answer 1',
      assistantCreated: 2,
      assistantCompleted: 3,
    })
    seedTurn({
      userId: 'u_hist_2',
      userText: 'history question 2',
      userCreated: 4,
      assistantId: 'a_hist_2',
      assistantText: 'history answer 2',
      assistantCreated: 5,
      assistantCompleted: 6,
    })
    seedTurn({
      userId: 'u_first_send',
      userText: 'first send question',
      userCreated: 7,
      assistantId: 'a_first_send',
      assistantText: 'first send answer',
      assistantCreated: 8,
    })

    useSessionStore.setState({
      activeSessionId: 's1',
      modeBySession: new Map([['s1', 'SPLIT']]),
      hasEverSentBySession: new Map([['s1', true]]),
      pendingPrompt: null,
    })

    const { container } = render(<SplitView />, { wrapper })
    const scroller = container.querySelector('.flex-1.overflow-y-auto') as HTMLDivElement | null

    expect(scroller).not.toBeNull()

    const metrics = { clientHeight: 180, scrollTop: 0 }
    attachDynamicScrollMetrics(scroller!, metrics)
    metrics.scrollTop = scroller!.scrollHeight
    fireEvent.scroll(scroller!)
    scrollToSpy.mockClear()

    const scrollHeightBefore = scroller!.scrollHeight

    act(() => {
      store.upsertPendingUser('s1', 'second send question')
    })

    const pendingTurn = screen.getByText('second send question').closest('[data-component="session-turn"]') as HTMLElement | null

    expect(container.querySelectorAll('[data-pending-user-motion="true"]')).toHaveLength(1)
    expect(metrics.scrollTop - scrollHeightBefore).toBe(estimateTurnHeight(pendingTurn))
  })

  it('does not run the structural scroll path for streamed text growth in an existing part', () => {
    const store = useChatPartsStore.getState()
    store.upsertInfo('s1', {
      id: 'u_stream',
      role: 'user',
      sessionID: 's1',
      time: { created: 1 },
    })
    store.upsertPart('s1', {
      type: 'text',
      id: 'u_stream_text',
      sessionID: 's1',
      messageID: 'u_stream',
      text: 'write a ts helper',
      metadata: {},
    } as any)
    store.upsertInfo('s1', {
      id: 'a_stream',
      role: 'assistant',
      sessionID: 's1',
      modelID: 'deepseek-chat',
      time: { created: 2 },
    })
    store.upsertPart('s1', {
      type: 'text',
      id: 'a_stream_text',
      sessionID: 's1',
      messageID: 'a_stream',
      text: '```ts\nconst a = 1',
      metadata: {},
    } as any)

    useSessionStore.setState({
      activeSessionId: 's1',
      modeBySession: new Map([['s1', 'SPLIT']]),
      hasEverSentBySession: new Map([['s1', true]]),
      pendingPrompt: null,
    })

    const { container } = render(<SplitView />, { wrapper })
    const scroller = container.querySelector('.flex-1.overflow-y-auto') as HTMLDivElement | null
    expect(scroller).not.toBeNull()

    const metrics = { clientHeight: 180, scrollTop: 0 }
    attachDynamicScrollMetrics(scroller!, metrics)
    metrics.scrollTop = scroller!.scrollHeight
    fireEvent.scroll(scroller!)
    scrollToSpy.mockClear()

    act(() => {
      store.appendPartDelta('s1', 'a_stream_text', 'text', '\nconst b = 2')
    })

    expect(scrollToSpy).not.toHaveBeenCalled()
  })
})
