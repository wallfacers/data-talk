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

  it('defaults createdAt/updatedAt when system metadata is omitted', () => {
    const result = dashboardSchema.safeParse({
      schemaVersion: 3, id: 'dash_aaaa', title: 'x',
      theme: 'industry-default', renderer: 'bezel',
      parameters: [], widgets: [],
      layout: { engine: 'free', template: 'grid-equal' },
      version: 1,
    })
    expect(result.success).toBe(true)
    if (result.success) {
      expect(typeof result.data.createdAt).toBe('number')
      expect(typeof result.data.updatedAt).toBe('number')
    }
  })

  it('defaults widget query paramRefs to {} when omitted', () => {
    const result = dashboardSchema.safeParse({
      schemaVersion: 3, id: 'dash_aaaa', title: 'x',
      theme: 'industry-default', renderer: 'bezel',
      parameters: [],
      widgets: [{
        id: 'kpi_w_gmv00001', type: 'kpi', slot: 'kpi-bar', title: 'GMV',
        patternId: 'kpi.single', options: {},
        query: { sql: 'SELECT 1' },
      }],
      layout: { engine: 'free', template: 'top-kpi-bottom-charts' },
      version: 1, createdAt: 0, updatedAt: 0,
    })
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.widgets[0].query?.paramRefs).toEqual({})
    }
  })
})
