import { describe, expect, it } from 'vitest'
import { GridLayoutEngine } from '../grid-layout-engine'
import type { Widget, GridLayout } from '../../schema'

const LAYOUT: GridLayout = { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 }

function widget(overrides: Partial<Widget> & { id: string; type: Widget['type'] }): Widget {
  return {
    position: { x: 0, y: 0, w: 4, h: 4 },
    options: {},
    ...overrides,
  } as Widget
}

describe('GridLayoutEngine', () => {
  const engine = new GridLayoutEngine()

  it('flags overlapping widgets as errors', () => {
    const widgets: Widget[] = [
      widget({ id: 'chart_w_aaaa', type: 'chart', position: { x: 0, y: 0, w: 6, h: 4 } }),
      widget({ id: 'chart_w_bbbb', type: 'chart', position: { x: 3, y: 0, w: 6, h: 4 } }),
    ]
    const result = engine.validate(LAYOUT, widgets)
    expect(result.errors.length).toBeGreaterThan(0)
    expect(result.errors.some((e) => e.message.includes('overlap'))).toBe(true)
  })

  it('passes non-overlapping widgets', () => {
    const widgets: Widget[] = [
      widget({ id: 'chart_w_aaaa', type: 'chart', position: { x: 0, y: 0, w: 6, h: 4 } }),
      widget({ id: 'chart_w_bbbb', type: 'chart', position: { x: 6, y: 0, w: 6, h: 4 } }),
    ]
    const result = engine.validate(LAYOUT, widgets)
    expect(result.errors).toHaveLength(0)
  })

  it('returns chart default position', () => {
    const pos = engine.defaultPosition('chart')
    expect(pos.w).toBe(6)
    expect(pos.h).toBe(4)
  })

  it('returns markdown default position', () => {
    const pos = engine.defaultPosition('markdown')
    expect(pos.w).toBe(12)
    expect(pos.h).toBe(3)
  })

  it('autoPackPosition fits into gap on same row', () => {
    const existing: Widget[] = [
      widget({ id: 'chart_w_aaaa', type: 'chart', position: { x: 0, y: 0, w: 6, h: 4 } }),
    ]
    const pos = engine.autoPackPosition({ w: 6, h: 4 }, existing)
    expect(pos.x).toBe(6)
    expect(pos.y).toBe(0)
  })

  it('autoPackPosition wraps to next row when gap too small', () => {
    const existing: Widget[] = [
      widget({ id: 'chart_w_aaaa', type: 'chart', position: { x: 0, y: 0, w: 8, h: 4 } }),
    ]
    const pos = engine.autoPackPosition({ w: 6, h: 4 }, existing)
    expect(pos.x).toBe(0)
    expect(pos.y).toBe(4)
  })
})
