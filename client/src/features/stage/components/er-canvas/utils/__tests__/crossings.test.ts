import { describe, expect, it } from 'vitest'

import { computeCrossings, pathToSegments, segmentIntersection } from '../crossings'

describe('pathToSegments', () => {
  it('parses M/L/H/V commands into segments', () => {
    const segs = pathToSegments('M 0,0 L 10,0 V 10 H 0')

    expect(segs).toHaveLength(3)
    expect(segs[0]).toEqual({ x1: 0, y1: 0, x2: 10, y2: 0 })
    expect(segs[1]).toEqual({ x1: 10, y1: 0, x2: 10, y2: 10 })
    expect(segs[2]).toEqual({ x1: 10, y1: 10, x2: 0, y2: 10 })
  })

  it('updates the current point when skipping curve commands', () => {
    const segs = pathToSegments('M 0,0 Q 5,5 10,0 L 20,0 C 25,5 30,5 35,0 V 10')

    expect(segs).toEqual([
      { x1: 10, y1: 0, x2: 20, y2: 0 },
      { x1: 35, y1: 0, x2: 35, y2: 10 },
    ])
  })
})

describe('segmentIntersection', () => {
  it('returns the crossing point for two crossing segments', () => {
    const p = segmentIntersection(
      { x1: 0, y1: 5, x2: 10, y2: 5 },
      { x1: 5, y1: 0, x2: 5, y2: 10 },
    )

    expect(p).toEqual({ x: 5, y: 5 })
  })

  it('returns null for parallel segments', () => {
    expect(segmentIntersection(
      { x1: 0, y1: 0, x2: 10, y2: 0 },
      { x1: 0, y1: 5, x2: 10, y2: 5 },
    )).toBeNull()
  })

  it('rejects intersections at endpoints', () => {
    expect(segmentIntersection(
      { x1: 0, y1: 0, x2: 10, y2: 0 },
      { x1: 10, y1: 0, x2: 10, y2: 10 },
    )).toBeNull()
  })
})

describe('computeCrossings', () => {
  it('finds crossings only against lower-z-order edges', () => {
    const myPath = 'M 0,4 L 20,4'
    const storeEdges = [
      { id: 'lower', source: 'sourceNode', target: 'targetNode', sourceHandle: null, targetHandle: null },
      { id: 'current', source: 'ignored', target: 'ignored' },
    ]
    const nodeLookup = new Map<string, unknown>([
      [
        'sourceNode',
        {
          internals: {
            handleBounds: { source: [{ id: null, x: 0, y: 0, width: 0, height: 0 }] },
            positionAbsolute: { x: -10, y: 0 },
          },
        },
      ],
      [
        'targetNode',
        {
          internals: {
            handleBounds: { target: [{ id: null, x: 0, y: 10, width: 0, height: 0 }] },
            positionAbsolute: { x: 30, y: 0 },
          },
        },
      ],
    ])

    const crossings = computeCrossings(myPath, 'current', storeEdges, nodeLookup as never)

    expect(crossings).toEqual([{ x: 10, y: 4, isHorizontal: true }])
  })

  it('ignores higher-z-order edges', () => {
    const myPath = 'M 0,4 L 20,4'
    const storeEdges = [
      { id: 'current', source: 'ignored', target: 'ignored' },
      { id: 'higher', source: 'sourceNode', target: 'targetNode', sourceHandle: null, targetHandle: null },
    ]
    const nodeLookup = new Map<string, unknown>([
      [
        'sourceNode',
        {
          internals: {
            handleBounds: { source: [{ id: null, x: 0, y: 0, width: 0, height: 0 }] },
            positionAbsolute: { x: -10, y: 0 },
          },
        },
      ],
      [
        'targetNode',
        {
          internals: {
            handleBounds: { target: [{ id: null, x: 0, y: 10, width: 0, height: 0 }] },
            positionAbsolute: { x: 30, y: 0 },
          },
        },
      ],
    ])

    expect(computeCrossings(myPath, 'current', storeEdges, nodeLookup as never)).toEqual([])
  })

  it('returns no crossings when a lower edge has incomplete node endpoints', () => {
    const nodeLookup = new Map<string, unknown>([
      [
        'sourceNode',
        {
          internals: {
            handleBounds: { source: [{ id: null, x: 10, y: 0, width: 0, height: 0 }] },
            positionAbsolute: { x: 0, y: 0 },
          },
        },
      ],
    ])

    expect(computeCrossings(
      'M 0,5 L 20,5',
      'current',
      [
        { id: 'lower', source: 'sourceNode', target: 'missingNode' },
        { id: 'current', source: 'ignored', target: 'ignored' },
      ],
      nodeLookup as never,
    )).toEqual([])
  })

  it('returns no crossings when the current edge has no lower-z-order edges', () => {
    expect(computeCrossings('M 0,5 L 20,5', 'current', [{ id: 'current' }], new Map())).toEqual([])
  })
})
