import { describe, expect, it } from 'vitest'
import { dashboardSchema } from '../schema'

describe('dashboardSchema', () => {
  it('accepts a minimal valid dashboard', () => {
    const result = dashboardSchema.safeParse({
      schemaVersion: 3, id: 'dash_aaaa', title: 'x',
      theme: 'industry-default', renderer: 'bezel',
      parameters: [], widgets: [],
      layout: { engine: 'free', template: 'grid-equal' },
      version: 1, createdAt: 0, updatedAt: 0,
    })
    expect(result.success).toBe(true)
  })

  it('rejects schemaVersion != 3', () => {
    const result = dashboardSchema.safeParse({
      schemaVersion: 2, id: 'dash_aaaa', title: 'x',
      theme: 'industry-default', renderer: 'bezel',
      parameters: [], widgets: [],
      layout: { engine: 'free', template: 'grid-equal' },
      version: 1, createdAt: 0, updatedAt: 0,
    })
    expect(result.success).toBe(false)
  })

  it('rejects invalid theme format', () => {
    const result = dashboardSchema.safeParse({
      schemaVersion: 3, id: 'dash_aaaa', title: 'x',
      theme: 'bad-theme', renderer: 'bezel',
      parameters: [], widgets: [],
      layout: { engine: 'free', template: 'grid-equal' },
      version: 1, createdAt: 0, updatedAt: 0,
    })
    expect(result.success).toBe(false)
  })

  it('rejects renderer != bezel', () => {
    const result = dashboardSchema.safeParse({
      schemaVersion: 3, id: 'dash_aaaa', title: 'x',
      theme: 'industry-default', renderer: 'canvas',
      parameters: [], widgets: [],
      layout: { engine: 'free', template: 'grid-equal' },
      version: 1, createdAt: 0, updatedAt: 0,
    })
    expect(result.success).toBe(false)
  })

  it('rejects layout without template', () => {
    const result = dashboardSchema.safeParse({
      schemaVersion: 3, id: 'dash_aaaa', title: 'x',
      theme: 'industry-default', renderer: 'bezel',
      parameters: [], widgets: [],
      layout: { engine: 'free' },
      version: 1, createdAt: 0, updatedAt: 0,
    })
    expect(result.success).toBe(false)
  })

  it('accepts widget with required v3 fields', () => {
    const result = dashboardSchema.safeParse({
      schemaVersion: 3, id: 'dash_aaaa', title: 'x',
      theme: 'industry-default', renderer: 'bezel',
      parameters: [],
      widgets: [{
        id: 'chart_w_test1234', type: 'chart', slot: 'main', title: 'Test',
        patternId: 'test.pattern', options: {},
      }],
      layout: { engine: 'free', template: 'grid-equal' },
      version: 1, createdAt: 0, updatedAt: 0,
    })
    expect(result.success).toBe(true)
  })
})
