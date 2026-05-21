import { describe, expect, it } from 'vitest'

import { buildSelfRefPath } from '../self-ref-path'

describe('buildSelfRefPath', () => {
  it('produces a 5-segment orthogonal path that loops above when handles are below midline', () => {
    const [d, lx, ly] = buildSelfRefPath(100, 200, 80, 220, 50, 250)

    expect(d).toMatch(/^M 100,200/)
    expect(d.split(/L /)).toHaveLength(6)
    expect(ly).toBeLessThan(50)
    expect(lx).toEqual(90)
  })

  it('loops below when handles are above the node midline', () => {
    const [, , ly] = buildSelfRefPath(100, 70, 80, 80, 50, 250)

    expect(ly).toBeGreaterThan(250)
  })
})
