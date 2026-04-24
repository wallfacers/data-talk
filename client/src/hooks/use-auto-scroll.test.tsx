import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, fireEvent, act } from '@testing-library/react'
import { useAutoScroll } from './use-auto-scroll'

type HarnessProps = {
  version: number
  text?: string
}

function Harness({ version, text = String(version) }: HarnessProps) {
  const { ref } = useAutoScroll<HTMLDivElement>([version])

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

describe('useAutoScroll', () => {
  const originalScrollTo = HTMLElement.prototype.scrollTo
  let scrollToSpy: ReturnType<typeof vi.fn>

  beforeEach(() => {
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

  it('batches repeated streaming text mutations into a single follow on the next animation frame', async () => {
    const metrics = { clientHeight: 100, scrollHeight: 1000, scrollTop: 900 }
    const queuedFrames: FrameRequestCallback[] = []
    const requestAnimationFrameSpy = vi
      .spyOn(window, 'requestAnimationFrame')
      .mockImplementation((callback: FrameRequestCallback) => {
        queuedFrames.push(callback)
        return queuedFrames.length
      })
    const cancelAnimationFrameSpy = vi
      .spyOn(window, 'cancelAnimationFrame')
      .mockImplementation(() => {})

    const view = render(<Harness version={0} text="a" />)
    const root = view.getByTestId('scroll-root') as HTMLDivElement
    const streamText = view.getByTestId('stream-text')

    attachScrollMetrics(root, metrics)
    syncAtBottom(root)
    scrollToSpy.mockClear()

    metrics.scrollHeight = 1040
    act(() => {
      streamText.textContent = 'ab'
    })
    await new Promise((resolve) => setTimeout(resolve, 0))

    metrics.scrollHeight = 1080
    act(() => {
      streamText.textContent = 'abc'
    })
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(queuedFrames).toHaveLength(1)
    expect(scrollToSpy).not.toHaveBeenCalled()

    act(() => {
      queuedFrames.shift()?.(16)
    })

    expect(scrollToSpy).toHaveBeenCalledTimes(1)
    expect(metrics.scrollTop).toBe(1080)

    requestAnimationFrameSpy.mockRestore()
    cancelAnimationFrameSpy.mockRestore()
  })
})
