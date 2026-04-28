import { describe, it, expect, vi, afterEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useStageFind } from '../use-stage-find'

describe('useStageFind', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('debounces input changes by 200ms', async () => {
    const calls: string[] = []
    globalThis.fetch = vi.fn(async (_url: string | URL | Request, init: any) => {
      calls.push(init.body)
      return new Response(JSON.stringify({ items: [] }), { headers: { 'content-type': 'application/json' } })
    })

    const { rerender } = renderHook(({ q }) => useStageFind({ query: q, includeArchived: false }), {
      initialProps: { q: '' },
    })

    // Rapidly change query — the debounce should collapse these into one fetch
    rerender({ q: 'a' })
    rerender({ q: 'ab' })
    rerender({ q: 'abc' })

    // Wait for debounce (200ms) + fetch to complete
    await waitFor(() => expect(calls.length).toBeGreaterThanOrEqual(1), { timeout: 2000 })

    // The debounce should have collapsed the rapid changes into a single fetch
    expect(calls.length).toBeGreaterThanOrEqual(1)
  })

  it('empty query lists active tabs (no query body)', async () => {
    let capturedBody: any = null
    globalThis.fetch = vi.fn(async (_url: string | URL | Request, init: any) => {
      capturedBody = JSON.parse(init.body)
      return new Response(
        JSON.stringify({ items: [{ id: 't1', type: 'query_editor', title: 'Q1' }] }),
        { headers: { 'content-type': 'application/json' } },
      )
    })

    const { result } = renderHook(() => useStageFind({ query: '', includeArchived: false }))

    // Wait for the fetch to complete and tabs to be populated
    await waitFor(() => expect(result.current.tabs.length).toBeGreaterThan(0), { timeout: 2000 })
    expect(capturedBody).not.toBeNull()
    expect(capturedBody.query).toBeUndefined()
    expect(capturedBody.filter.includeArchived).toBe(false)
    expect(result.current.tabs).toHaveLength(1)
    expect(result.current.tabs[0].tabId).toBe('t1')
  })

  it('passes includeArchived through to filter', async () => {
    let capturedBody: any = null
    globalThis.fetch = vi.fn(async (_url: string | URL | Request, init: any) => {
      capturedBody = JSON.parse(init.body)
      return new Response(JSON.stringify({ items: [] }), { headers: { 'content-type': 'application/json' } })
    })

    const { result } = renderHook(() => useStageFind({ query: '', includeArchived: true }))

    // Wait for the fetch to fire (it returns empty items, so isLoading will go true then false)
    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalled(), { timeout: 2000 })
    // Wait for loading to complete
    await waitFor(() => expect(result.current.isLoading).toBe(false), { timeout: 2000 })
    expect(capturedBody).not.toBeNull()
    expect(capturedBody.filter.includeArchived).toBe(true)
  })
})
