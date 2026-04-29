import { describe, expect, it } from 'vitest'

import { getPointOnSegments, resolveLabelPos } from '../label-positioning'

describe('getPointOnSegments', () => {
  it('returns midpoint at t=0.5 for a straight horizontal segment', () => {
    const p = getPointOnSegments([{ x1: 0, y1: 0, x2: 100, y2: 0 }], 0.5)

    expect(p).toEqual({ x: 50, y: 0 })
  })

  it('returns null for empty segment list', () => {
    expect(getPointOnSegments([], 0.5)).toBeNull()
  })
})

describe('resolveLabelPos', () => {
  it('picks a candidate position not within HitW/HitH of any placed point', () => {
    const segs = [{ x1: 0, y1: 0, x2: 100, y2: 0 }]
    const placed = [{ x: 50, y: 0 }]

    const p = resolveLabelPos(segs, placed)

    expect(p).not.toBeNull()
    expect([35, 65, 25, 75]).toContain(Math.round(p!.x))
  })

  it('falls back to midpoint when nothing fits', () => {
    const segs = [{ x1: 0, y1: 0, x2: 100, y2: 0 }]
    const placed = [
      { x: 50, y: 0 },
      { x: 35, y: 0 },
      { x: 65, y: 0 },
      { x: 25, y: 0 },
      { x: 75, y: 0 },
    ]

    const p = resolveLabelPos(segs, placed)

    expect(p).toEqual({ x: 50, y: 0 })
  })
})
