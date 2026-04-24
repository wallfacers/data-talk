import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, fireEvent, act } from '@testing-library/react'
import { useAutoScroll } from './use-auto-scroll'

type HarnessProps = {
  version: number
}

function Harness({ version }: HarnessProps) {
  const { ref } = useAutoScroll<HTMLDivElement>([version])

  return (
    <div ref={ref} data-testid="scroll-root">
      <div>{version}</div>
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

describe('useAutoScroll', () => {
  const originalScrollTo = HTMLElement.prototype.scrollTo
  const originalMutationObserver = globalThis.MutationObserver
  const originalRequestAnimationFrame = globalThis.requestAnimationFrame
  const originalCancelAnimationFrame = globalThis.cancelAnimationFrame
  let scrollToSpy: ReturnType<typeof vi.fn>
  let rafQueue: FrameRequestCallback[]

  beforeEach(() => {
    MockMutationObserver.reset()
    globalThis.MutationObserver = MockMutationObserver as unknown as typeof MutationObserver
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

    expect(scrollToSpy).toHaveBeenCalledTimes(1)
    expect(metrics.scrollTop).toBe(1080)
  })
})
