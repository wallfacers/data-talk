import { describe, expect, it } from 'vitest'
import { computeDagreLayout } from './dagre-layout.worker'

describe('dagre layout - pure compute', () => {
  it('returns positions for every input node id', () => {
    const positions = computeDagreLayout({
      nodes: [
        { id: 'users', width: 280, height: 120 },
        { id: 'orders', width: 280, height: 160 },
      ],
      edges: [{ source: 'users', target: 'orders' }],
      config: { rankdir: 'LR', nodesep: 80, ranksep: 200 },
    })

    expect(Object.keys(positions)).toEqual(expect.arrayContaining(['users', 'orders']))
    expect(positions.users.x).toBeTypeOf('number')
    expect(positions.users.y).toBeTypeOf('number')
  })

  it('100 nodes complete within the interactive budget', () => {
    const nodes = Array.from({ length: 100 }, (_, i) => ({ id: `t${i}`, width: 280, height: 120 }))
    const edges = Array.from({ length: 50 }, (_, i) => ({ source: `t${i}`, target: `t${i + 1}` }))
    const start = performance.now()

    const positions = computeDagreLayout({
      nodes,
      edges,
      config: { rankdir: 'LR', nodesep: 80, ranksep: 200 },
    })
    const elapsed = performance.now() - start

    expect(Object.keys(positions)).toHaveLength(100)
    expect(elapsed).toBeLessThan(400)
  })

  it('returns empty for empty input', () => {
    expect(computeDagreLayout({ nodes: [], edges: [], config: { rankdir: 'LR', nodesep: 80, ranksep: 200 } }))
      .toEqual({})
  })
})
