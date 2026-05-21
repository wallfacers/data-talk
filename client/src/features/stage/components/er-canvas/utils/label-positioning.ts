// Ported from open-db-studio/src/components/ERDesigner/ERCanvas/EREdge.tsx
// (LABEL_T_CANDIDATES / LABEL_HIT_W / LABEL_HIT_H / resolveLabelPos).

export type Point = {
  x: number
  y: number
}

export type Segment = {
  x1: number
  y1: number
  x2: number
  y2: number
}

const LABEL_T_CANDIDATES = [0.5, 0.35, 0.65, 0.25, 0.75]
const LABEL_HIT_W = 48
const LABEL_HIT_H = 24
const LABEL_HIT_HALF_W = LABEL_HIT_W / 2
const LABEL_HIT_HALF_H = LABEL_HIT_H / 2

/** Return a point at fraction `t` of total segment length. */
export function getPointOnSegments(segs: Segment[], t: number): Point | null {
  if (segs.length === 0) return null

  let total = 0
  const lens = segs.map((s) => {
    const len = Math.hypot(s.x2 - s.x1, s.y2 - s.y1)
    total += len
    return len
  })

  if (total === 0) return null

  let remaining = t * total
  for (let i = 0; i < segs.length; i += 1) {
    const seg = segs[i]
    const len = lens[i]

    if (remaining <= len || i === segs.length - 1) {
      const ratio = len > 0 ? remaining / len : 0
      return {
        x: seg.x1 + ratio * (seg.x2 - seg.x1),
        y: seg.y1 + ratio * (seg.y2 - seg.y1),
      }
    }

    remaining -= len
  }

  const last = segs[segs.length - 1]
  return { x: last.x2, y: last.y2 }
}

export function resolveLabelPos(segs: Segment[], placed: Point[]): Point | null {
  for (const t of LABEL_T_CANDIDATES) {
    const pt = getPointOnSegments(segs, t)
    if (!pt) continue

    const overlaps = placed.some(
      (p) => Math.abs(pt.x - p.x) < LABEL_HIT_HALF_W && Math.abs(pt.y - p.y) < LABEL_HIT_HALF_H,
    )
    if (!overlaps) return pt
  }

  return getPointOnSegments(segs, 0.5)
}
