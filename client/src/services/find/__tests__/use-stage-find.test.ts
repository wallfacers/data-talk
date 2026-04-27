import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { useStageFind } from '../use-stage-find'

describe('useStageFind', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    const { fetch } = createMockFetch()
    globalThis.fetch = fetch
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('debounces input changes by 200ms', async () => {
    const calls: string[] = []
    globalThis.fetch = vi.fn(async (_url: string, init: any) => {
      calls.push(init.body)
      return new Response(JSON.stringify({ items: [] }), { headers: { 'content-type': 'application/json' } })
    })

    const { result } = renderHook(({ q }) => useStageFind({ query: q, includeArchived: false }), {
      initialProps: { q: '' },
    })

    act(() => {
      result.current // access to trigger re-render
    })

    // Rapidly change query — should only fire once after debounce
    const { rerender } = renderHook(({ q }) => useStageFind({ query: q, includeArchived: false }), {
      initialProps: { q: 'a' },
    })
    rerender({ q: 'ab' })
    rerender({ q: 'abc' })

    expect(calls).toHaveLength(0)
    act(() => { vi.advanceTimersByTime(200) })

    await waitFor(() => expect(calls.length).toBeGreaterThanOrEqual(1))
  })

  it('empty query lists active tabs (no query body)', async () => {
    let capturedBody: any = null
    globalThis.fetch = vi.fn(async (_url: string, init: any) => {
      capturedBody = JSON.parse(init.body)
      return new Response(
        JSON.stringify({ items: [{ id: 't1', type: 'query_editor', title: 'Q1', scope: 'workspace' }] }),
        { headers: { 'content-type': 'application/json' } },
      )
    })

    const { result } = renderHook(() => useStageFind({ query: '', includeArchived: false }))
    act(() => { vi.advanceTimersByTime(200) })

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(capturedBody.query).toBeUndefined()
    expect(capturedBody.filter.includeArchived).toBe(false)
    expect(result.current.tabs).toHaveLength(1)
    expect(result.current.tabs[0].tabId).toBe('t1')
  })

  it('passes includeArchived through to filter', async () => {
    let capturedBody: any = null
    globalThis.fetch = vi.fn(async (_url: string, init: any) => {
      capturedBody = JSON.parse(init.body)
      return new Response(JSON.stringify({ items: [] }), { headers: { 'content-type': 'application/json' } })
    })

    const { result } = renderHook(() => useStageFind({ query: '', includeArchived: true }))
    act(() => { vi.advanceTimersByTime(200) })

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(capturedBody.filter.includeArchived).toBe(true)
  })
})

function createMockFetch() {
  return {
    fetch: vi.fn(async () =>
      new Response(JSON.stringify({ items: [] }), { headers: { 'content-type': 'application/json' } })
    ),
  }
}
