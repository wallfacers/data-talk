import { describe, it, expect, vi } from 'vitest'
import { act, render } from '@testing-library/react'
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
    await act(async () => {
      vi.advanceTimersByTime(24)
    })
    const partial1 = container.textContent ?? ''
    await act(async () => {
      vi.advanceTimersByTime(240)
    })
    const partial2 = container.textContent ?? ''
    expect(partial2.length).toBeGreaterThanOrEqual(partial1.length)
    vi.useRealTimers()
  })

  it('streaming=true bypasses paced reveal once fenced code appears', async () => {
    vi.useFakeTimers()
    const text = 'before\n\n```sql\nselect * from orders\n```'
    const { container } = render(<PacedMarkdown text={text} cacheKey="code-stream" streaming={true} />)

    expect(container.textContent).toContain('before')
    expect(container.textContent).toContain('select * from orders')

    vi.useRealTimers()
  })

  it('preserves trailing newlines when bypassing paced reveal for streaming code', async () => {
    vi.useFakeTimers()
    const text = '```ts\nconst a = 1\n'
    const { container } = render(<PacedMarkdown text={text} cacheKey="code-newline" streaming={true} />)

    expect(container.querySelector('code')?.textContent).toBe('const a = 1\n')

    vi.useRealTimers()
  })
})
