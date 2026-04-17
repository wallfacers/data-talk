import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { render, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SplitView } from './split-view'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'

const DURATION = 400

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

function wrapper({ children }: { children: React.ReactNode }) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

function findStagePanel(container: HTMLElement): HTMLElement {
  const el = container.querySelector('[data-stage-panel]') as HTMLElement | null
  if (!el) throw new Error('stage panel not found')
  return el
}

describe('SplitView clip-path reveal', () => {
  beforeEach(() => {
    queryClient.clear()
    vi.useFakeTimers()
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
      pendingConnectionPrompt: false,
    })
    useChatPartsStore.setState({ partsBySession: new Map() })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('open=false 时 stage 容器 clip-path 为 circle(0px ...)', () => {
    const { container } = render(<SplitView />, { wrapper })
    const panel = findStagePanel(container)
    expect(panel.style.clipPath).toMatch(/circle\(0px/)
  })

  it('open=true + revealOrigin 有值 → clip-path 用 origin 坐标换算', () => {
    useStageStore.setState({
      openBySession: new Map([['s1', true]]),
      revealOrigin: { x: 100, y: 300 },
    })
    const { container } = render(<SplitView />, { wrapper })
    const panel = findStagePanel(container)
    expect(panel.style.clipPath).toMatch(/circle\(\d+(\.\d+)?px at -?\d+(\.\d+)?px -?\d+(\.\d+)?px\)/)
    const match = panel.style.clipPath.match(/circle\(([\d.]+)px/)
    expect(match).not.toBeNull()
    expect(Number(match![1])).toBeGreaterThanOrEqual(0)
  })

  it('open=true + revealOrigin=null → clip-path fallback 到 circle(2000px at 100% 100%)', () => {
    useStageStore.setState({ openBySession: new Map([['s1', true]]) })
    const { container } = render(<SplitView />, { wrapper })
    const panel = findStagePanel(container)
    expect(panel.style.clipPath).toBe('circle(2000px at 100% 100%)')
  })

  it('打开时 translateX 立即为 0，关闭后 DURATION ms 才切到 100%', () => {
    useStageStore.setState({ openBySession: new Map([['s1', true]]) })
    const { container, rerender } = render(<SplitView />, { wrapper })
    let panel = findStagePanel(container)
    expect(panel.style.transform).toBe('translateX(0px)')

    act(() => {
      useStageStore.setState({ openBySession: new Map([['s1', false]]) })
    })
    rerender(<SplitView />)
    panel = findStagePanel(container)
    expect(panel.style.transform).toBe('translateX(0px)')

    act(() => {
      vi.advanceTimersByTime(DURATION)
    })
    rerender(<SplitView />)
    panel = findStagePanel(container)
    expect(panel.style.transform).toBe('translateX(100%)')
  })
})
