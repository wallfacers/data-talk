import { describe, expect, it } from 'vitest'
import { dashboardSchema } from '../schema'

describe('dashboardSchema', () => {
  it('accepts a minimal valid dashboard', () => {
    const result = dashboardSchema.safeParse({
      schemaVersion: 1, id: 'dash_aaaa', title: 'x',
      parameters: [], widgets: [],
      layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
      version: 1, createdAt: 0, updatedAt: 0,
    })
    expect(result.success).toBe(true)
  })

  it('rejects schemaVersion != 1', () => {
    const result = dashboardSchema.safeParse({
      schemaVersion: 2, id: 'dash_aaaa', title: 'x',
      parameters: [], widgets: [],
      layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
      version: 1, createdAt: 0, updatedAt: 0,
    })
    expect(result.success).toBe(false)
  })

  it('rejects non-https image src', () => {
    const result = dashboardSchema.safeParse({
      schemaVersion: 1, id: 'dash_aaaa', title: 'x',
      parameters: [], widgets: [{
        id: 'image_w_aaaa', type: 'image',
        position: { x: 0, y: 0, w: 4, h: 4 },
        options: { src: 'http://insecure', alt: 'x', fit: 'cover' },
      }],
      layout: { engine: 'grid', cols: 12, rowHeight: 32, gap: 8 },
      version: 1, createdAt: 0, updatedAt: 0,
    })
    expect(result.success).toBe(false)
  })
})
