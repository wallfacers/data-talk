import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useErHighlight } from '../useErHighlight'

describe('useErHighlight', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('transitions idle to pulse to residual to idle on triggerHighlight', () => {
    vi.useFakeTimers()
    const { result } = renderHook(() => useErHighlight('scope-1'))

    expect(result.current.phaseFor('table:users')).toBe('idle')

    act(() => result.current.triggerHighlight('table:users'))
    expect(result.current.phaseFor('table:users')).toBe('pulse')

    act(() => {
      vi.advanceTimersByTime(2400)
    })
    expect(result.current.phaseFor('table:users')).toBe('residual')

    act(() => {
      vi.advanceTimersByTime(8000 - 2400)
    })
    expect(result.current.phaseFor('table:users')).toBe('idle')
  })

  it('honors prefers-reduced-motion by skipping pulse', () => {
    const original = window.matchMedia
    window.matchMedia = vi.fn().mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }) as unknown as typeof window.matchMedia
    vi.useFakeTimers()
    const { result } = renderHook(() => useErHighlight('scope-1'))

    act(() => result.current.triggerHighlight('table:users'))

    expect(result.current.phaseFor('table:users')).toBe('residual')
    window.matchMedia = original
  })
})
