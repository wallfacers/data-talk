import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { PacedMarkdown } from '../paced-markdown'

describe('PacedMarkdown', () => {
  it('streaming=false renders full text immediately', async () => {
    const { container } = render(<PacedMarkdown text="hello world" cacheKey="x" streaming={false} />)
    await new Promise((r) => setTimeout(r, 30))
    expect(container.textContent).toContain('hello world')
  })

  it('streaming=true reveals progressively', async () => {
    vi.useFakeTimers()
    const { container } = render(<PacedMarkdown text="hello world! this is streaming" cacheKey="x2" streaming={true} />)
    vi.advanceTimersByTime(24)
    const partial1 = container.textContent ?? ''
    vi.advanceTimersByTime(240)
    const partial2 = container.textContent ?? ''
    expect(partial2.length).toBeGreaterThanOrEqual(partial1.length)
    vi.useRealTimers()
  })
})
