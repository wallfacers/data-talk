import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, fireEvent, act } from '@testing-library/react'
import { useAutoScroll } from './use-auto-scroll'

type HarnessProps = {
  version: number
  text?: string
  resetVersion?: number
}

function Harness({ version, text = String(version), resetVersion = 0 }: HarnessProps) {
  const { ref } = useAutoScroll<HTMLDivElement>([version], [resetVersion])

  return (
    <div ref={ref} data-testid="scroll-root">
      <div data-testid="stream-text">{text}</div>
    </div>
  )
}

type ScrollMetrics = {
  clientHeight: number
  scrollHeight: number
  scrollTop: number
}

function attachScrollMetrics(el: HTMLDivElement, metrics: ScrollMetrics) {
  Object.defineProperty(el, 'clientHeight', {
    configurable: true,
    get: () => metrics.clientHeight,
  })
  Object.defineProperty(el, 'scrollHeight', {
    configurable: true,
    get: () => metrics.scrollHeight,
  })
  Object.defineProperty(el, 'scrollTop', {
    configurable: true,
    get: () => metrics.scrollTop,
    set: (value: number) => {
      metrics.scrollTop = value
    },
  })
}

function syncAtBottom(el: HTMLDivElement) {
  fireEvent.scroll(el)
}

function flushAnimationFrameQueue(queue: FrameRequestCallback[]) {
  while (queue.length > 0) {
    const callback = queue.shift()
    callback?.(16)
  }
}

class MockMutationObserver {
  static instances: MockMutationObserver[] = []

  callback: MutationCallback

  constructor(callback: MutationCallback) {
    this.callback = callback
    MockMutationObserver.instances.push(this)
  }

  observe() {}

  disconnect() {}

  takeRecords() {
    return []
  }

  trigger(records: MutationRecord[] = []) {
    this.callback(records, this as unknown as MutationObserver)
  }

  static reset() {
    MockMutationObserver.instances = []
  }
}

class MockResizeObserver {
  static instances: MockResizeObserver[] = []

  callback: ResizeObserverCallback

  constructor(callback: ResizeObserverCallback) {
    this.callback = callback
    MockResizeObserver.instances.push(this)
  }

  observe() {}

  unobserve() {}

  disconnect() {}

  trigger(entries: ResizeObserverEntry[] = []) {
    this.callback(entries, this as unknown as ResizeObserver)
  }

  static reset() {
    MockResizeObserver.instances = []
  }
}

describe('useAutoScroll', () => {
  const originalScrollTo = HTMLElement.prototype.scrollTo
  const originalMutationObserver = globalThis.MutationObserver
  const originalResizeObserver = globalThis.ResizeObserver
  const originalRequestAnimationFrame = globalThis.requestAnimationFrame
  const originalCancelAnimationFrame = globalThis.cancelAnimationFrame
  let scrollToSpy: ReturnType<typeof vi.fn>
  let rafQueue: FrameRequestCallback[]

  beforeEach(() => {
    MockMutationObserver.reset()
    MockResizeObserver.reset()
    globalThis.MutationObserver = MockMutationObserver as unknown as typeof MutationObserver
    globalThis.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver
    rafQueue = []
    globalThis.requestAnimationFrame = vi.fn((cb: FrameRequestCallback) => {
      rafQueue.push(cb)
      return rafQueue.length
    })
    globalThis.cancelAnimationFrame = vi.fn((id: number) => {
      const idx = id - 1
      if (idx >= 0 && idx < rafQueue.length) rafQueue[idx] = () => {}
    })
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
  })

  afterEach(() => {
    Object.defineProperty(HTMLElement.prototype, 'scrollTo', {
      configurable: true,
      writable: true,
      value: originalScrollTo,
    })
    globalThis.MutationObserver = originalMutationObserver
    globalThis.ResizeObserver = originalResizeObserver
    globalThis.requestAnimationFrame = originalRequestAnimationFrame
    globalThis.cancelAnimationFrame = originalCancelAnimationFrame
  })

  it('auto-scrolls when content changes while the user is at the bottom', () => {
    const metrics = { clientHeight: 100, scrollHeight: 1000, scrollTop: 900 }
    const view = render(<Harness version={0} />)
    const root = view.getByTestId('scroll-root') as HTMLDivElement

    attachScrollMetrics(root, metrics)
    syncAtBottom(root)
    scrollToSpy.mockClear()

    metrics.scrollHeight = 1120
    act(() => {
      view.rerender(<Harness version={1} />)
    })

    expect(scrollToSpy).toHaveBeenCalledTimes(1)
    expect(metrics.scrollTop).toBe(1120)
  })

  it('stops auto-scroll after the user manually scrolls upward even if still near the bottom', () => {
    const metrics = { clientHeight: 100, scrollHeight: 1000, scrollTop: 900 }
    const view = render(<Harness version={0} />)
    const root = view.getByTestId('scroll-root') as HTMLDivElement

    attachScrollMetrics(root, metrics)
    syncAtBottom(root)
    scrollToSpy.mockClear()

    metrics.scrollTop = 860
    fireEvent.scroll(root)

    metrics.scrollHeight = 1040
    act(() => {
      view.rerender(<Harness version={1} />)
    })

    expect(scrollToSpy).not.toHaveBeenCalled()
    expect(metrics.scrollTop).toBe(860)
  })

  it('resumes auto-scroll only after the user returns to the bottom', () => {
    const metrics = { clientHeight: 100, scrollHeight: 1000, scrollTop: 900 }
    const view = render(<Harness version={0} />)
    const root = view.getByTestId('scroll-root') as HTMLDivElement

    attachScrollMetrics(root, metrics)
    syncAtBottom(root)
    scrollToSpy.mockClear()

    metrics.scrollTop = 860
    fireEvent.scroll(root)

    metrics.scrollHeight = 1040
    act(() => {
      view.rerender(<Harness version={1} />)
    })

    scrollToSpy.mockClear()
    metrics.scrollTop = 940
    fireEvent.scroll(root)

    metrics.scrollHeight = 1100
    act(() => {
      view.rerender(<Harness version={2} />)
    })

    expect(scrollToSpy).toHaveBeenCalledTimes(1)
    expect(metrics.scrollTop).toBe(1100)
  })

  it('suppresses repeated mutation callbacks from the same append until the next frame', () => {
    const metrics = { clientHeight: 100, scrollHeight: 1000, scrollTop: 900 }
    const view = render(<Harness version={0} />)
    const root = view.getByTestId('scroll-root') as HTMLDivElement

    attachScrollMetrics(root, metrics)
    syncAtBottom(root)
    act(() => {
      flushAnimationFrameQueue(rafQueue)
    })
    scrollToSpy.mockClear()

    metrics.scrollHeight = 1040
    act(() => {
      view.rerender(<Harness version={1} />)
    })

    expect(scrollToSpy).toHaveBeenCalledTimes(1)
    scrollToSpy.mockClear()

    const observer = MockMutationObserver.instances[0]
    expect(observer).toBeDefined()

    act(() => {
      observer.trigger()
      observer.trigger()
      observer.trigger()
    })

    expect(scrollToSpy).not.toHaveBeenCalled()

    const frameCallback = rafQueue.shift()
    expect(frameCallback).toBeTypeOf('function')
    act(() => {
      frameCallback?.(16)
    })

    metrics.scrollHeight = 1080
    act(() => {
      observer.trigger()
    })

    expect(scrollToSpy).not.toHaveBeenCalled()

    const followFrame = rafQueue.shift()
    expect(followFrame).toBeTypeOf('function')
    act(() => {
      followFrame?.(16)
    })

    expect(scrollToSpy).toHaveBeenCalledTimes(1)
    expect(metrics.scrollTop).toBe(1080)
  })

  it('batches repeated streaming text mutations into a single follow on the next animation frame', () => {
    const metrics = { clientHeight: 100, scrollHeight: 1000, scrollTop: 900 }
    const view = render(<Harness version={0} text="a" />)
    const root = view.getByTestId('scroll-root') as HTMLDivElement

    attachScrollMetrics(root, metrics)
    syncAtBottom(root)
    act(() => {
      flushAnimationFrameQueue(rafQueue)
    })
    scrollToSpy.mockClear()

    const observer = MockMutationObserver.instances[0]
    expect(observer).toBeDefined()

    metrics.scrollHeight = 1040
    act(() => {
      observer.trigger()
    })

    metrics.scrollHeight = 1080
    act(() => {
      observer.trigger()
    })

    expect(scrollToSpy).not.toHaveBeenCalled()
    expect(rafQueue).toHaveLength(1)

    act(() => {
      rafQueue.shift()?.(16)
    })

    expect(scrollToSpy).toHaveBeenCalledTimes(1)
    expect(metrics.scrollTop).toBe(1080)
  })

  it('does not layout-scroll for streamed content growth when deps do not change', () => {
    const metrics = { clientHeight: 100, scrollHeight: 1000, scrollTop: 900 }
    const view = render(<Harness version={0} text="```ts\nconst a = 1" />)
    const root = view.getByTestId('scroll-root') as HTMLDivElement

    attachScrollMetrics(root, metrics)
    syncAtBottom(root)
    act(() => {
      flushAnimationFrameQueue(rafQueue)
    })
    scrollToSpy.mockClear()

    metrics.scrollHeight = 1040
    act(() => {
      view.rerender(<Harness version={0} text="```ts\nconst a = 1\nconst b = 2" />)
    })

    expect(scrollToSpy).not.toHaveBeenCalled()

    const observer = MockMutationObserver.instances[0]
    expect(observer).toBeDefined()

    act(() => {
      observer.trigger()
    })
    expect(scrollToSpy).not.toHaveBeenCalled()

    act(() => {
      rafQueue.shift()?.(16)
    })

    expect(scrollToSpy).toHaveBeenCalledTimes(1)
    expect(metrics.scrollTop).toBe(1040)
  })

  it('batches resize-driven content growth into a single follow on the next animation frame', () => {
    const metrics = { clientHeight: 100, scrollHeight: 1000, scrollTop: 900 }
    const view = render(<Harness version={0} text="```ts\nconst a = 1" />)
    const root = view.getByTestId('scroll-root') as HTMLDivElement

    attachScrollMetrics(root, metrics)
    syncAtBottom(root)
    act(() => {
      flushAnimationFrameQueue(rafQueue)
    })
    scrollToSpy.mockClear()

    const observer = MockResizeObserver.instances[0]
    expect(observer).toBeDefined()

    metrics.scrollHeight = 1040
    act(() => {
      observer.trigger()
      observer.trigger()
    })

    expect(scrollToSpy).not.toHaveBeenCalled()
    expect(rafQueue).toHaveLength(1)

    act(() => {
      rafQueue.shift()?.(16)
    })

    expect(scrollToSpy).toHaveBeenCalledTimes(1)
    expect(metrics.scrollTop).toBe(1040)
  })

  it('resetDeps change re-enables follow and scrolls to bottom even after user scrolled up', () => {
    const metrics = { clientHeight: 100, scrollHeight: 1000, scrollTop: 900 }
    const view = render(<Harness version={0} resetVersion={0} />)
    const root = view.getByTestId('scroll-root') as HTMLDivElement

    attachScrollMetrics(root, metrics)
    syncAtBottom(root)
    act(() => {
      flushAnimationFrameQueue(rafQueue)
    })
    scrollToSpy.mockClear()

    // User scrolls upward — follow should be disabled.
    metrics.scrollTop = 500
    fireEvent.scroll(root)

    // Assistant streaming content grows — follow must stay disabled.
    metrics.scrollHeight = 1200
    act(() => {
      view.rerender(<Harness version={1} resetVersion={0} />)
    })
    expect(scrollToSpy).not.toHaveBeenCalled()
    expect(metrics.scrollTop).toBe(500)

    // User sends a new message — resetVersion bumps, follow must reset.
    metrics.scrollHeight = 1400
    act(() => {
      view.rerender(<Harness version={2} resetVersion={1} />)
    })

    expect(scrollToSpy).toHaveBeenCalledTimes(1)
    expect(metrics.scrollTop).toBe(1400)
  })

  it('does not follow resize-driven content growth after the user scrolls upward', () => {
    const metrics = { clientHeight: 100, scrollHeight: 1000, scrollTop: 900 }
    const view = render(<Harness version={0} text="```ts\nconst a = 1" />)
    const root = view.getByTestId('scroll-root') as HTMLDivElement

    attachScrollMetrics(root, metrics)
    syncAtBottom(root)
    act(() => {
      flushAnimationFrameQueue(rafQueue)
    })
    scrollToSpy.mockClear()

    metrics.scrollTop = 820
    fireEvent.scroll(root)

    const observer = MockResizeObserver.instances[0]
    expect(observer).toBeDefined()

    metrics.scrollHeight = 1040
    act(() => {
      observer.trigger()
    })
    act(() => {
      rafQueue.shift()?.(16)
    })

    expect(scrollToSpy).not.toHaveBeenCalled()
    expect(metrics.scrollTop).toBe(820)
  })
})
