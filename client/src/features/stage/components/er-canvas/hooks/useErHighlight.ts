import { useCallback, useEffect, useRef, useState } from 'react'

export type HighlightPhase = 'idle' | 'pulse' | 'residual'

const PULSE_MS = 2400
const TOTAL_MS = 8000

function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

export function useErHighlight(_scopeId: string) {
  const [phases, setPhases] = useState<Map<string, HighlightPhase>>(new Map())
  const timers = useRef<Map<string, { pulse?: ReturnType<typeof setTimeout>; residual?: ReturnType<typeof setTimeout> }>>(
    new Map(),
  )

  useEffect(() => {
    return () => {
      for (const timer of timers.current.values()) {
        if (timer.pulse) clearTimeout(timer.pulse)
        if (timer.residual) clearTimeout(timer.residual)
      }
      timers.current.clear()
    }
  }, [])

  const triggerHighlight = useCallback((targetKey: string) => {
    const reduced = prefersReducedMotion()
    const existing = timers.current.get(targetKey)
    if (existing?.pulse) clearTimeout(existing.pulse)
    if (existing?.residual) clearTimeout(existing.residual)

    setPhases((prev) => {
      const next = new Map(prev)
      next.set(targetKey, reduced ? 'residual' : 'pulse')
      return next
    })

    const nextTimers: { pulse?: ReturnType<typeof setTimeout>; residual?: ReturnType<typeof setTimeout> } = {}
    if (!reduced) {
      nextTimers.pulse = setTimeout(() => {
        setPhases((prev) => {
          const next = new Map(prev)
          next.set(targetKey, 'residual')
          return next
        })
      }, PULSE_MS)
    }
    nextTimers.residual = setTimeout(() => {
      setPhases((prev) => {
        const next = new Map(prev)
        next.delete(targetKey)
        return next
      })
      timers.current.delete(targetKey)
    }, reduced ? TOTAL_MS - PULSE_MS : TOTAL_MS)
    timers.current.set(targetKey, nextTimers)
  }, [])

  const phaseFor = useCallback((targetKey: string): HighlightPhase => {
    return phases.get(targetKey) ?? 'idle'
  }, [phases])

  return { triggerHighlight, phaseFor }
}
