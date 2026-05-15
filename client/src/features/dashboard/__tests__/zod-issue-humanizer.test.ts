import { describe, expect, it } from 'vitest'
import { z } from 'zod'
import { humanizeZodIssue, pathToHuman } from '../zod-issue-humanizer'
import { translateMessage } from '@/i18n/messages'

const t = ((key: Parameters<typeof translateMessage>[1], values?: Record<string, string | number>) =>
  translateMessage('zh-CN', key, values)) as Parameters<typeof humanizeZodIssue>[2]

const probeSchema = z.object({
  schemaVersion: z.literal(2),
  id: z.string().regex(/^dash_[a-zA-Z0-9_]{4,}$/),
  theme: z.string().regex(/^industry-[a-z-]+$/),
  version: z.number(),
  widgets: z.array(
    z.object({
      id: z.string().regex(/^[a-z]+_w_[a-zA-Z0-9_]{4,32}$/),
      patternId: z.string().regex(/^[a-z0-9-]+\.[a-z0-9-]+$/),
    }),
  ),
})

describe('pathToHuman', () => {
  it('formats array index as bracket notation', () => {
    expect(pathToHuman(['widgets', 0, 'id'])).toBe('widgets[0].id')
  })

  it('handles top-level scalar key', () => {
    expect(pathToHuman(['id'])).toBe('id')
  })

  it('returns empty string for empty path', () => {
    expect(pathToHuman([])).toBe('')
  })
})

describe('humanizeZodIssue', () => {
  it('humanizes widget id regex failure with suffix length', () => {
    const root = {
      schemaVersion: 2,
      id: 'dash_abcd',
      theme: 'industry-ecommerce',
      version: 1,
      widgets: [{ id: 'kpi_w_gmv', patternId: 'kpi.label' }],
    }
    const result = probeSchema.safeParse(root)
    expect(result.success).toBe(false)
    if (result.success) return
    const issue = result.error.issues.find((i) => i.path[i.path.length - 1] === 'id' && i.path[0] === 'widgets')
    expect(issue).toBeTruthy()
    const h = humanizeZodIssue(issue!, root, t)
    expect(h.path).toBe('widgets[0].id')
    expect(h.friendly).toContain('kpi_w_gmv')
    expect(h.friendly).toContain('3')
    expect(h.friendly).toContain('≥4')
  })

  it('humanizes dashboard id regex failure', () => {
    const root = {
      schemaVersion: 2,
      id: 'my-dashboard',
      theme: 'industry-ecommerce',
      version: 1,
      widgets: [],
    }
    const result = probeSchema.safeParse(root)
    if (result.success) throw new Error('expected failure')
    const issue = result.error.issues.find((i) => i.path.length === 1 && i.path[0] === 'id')
    expect(issue).toBeTruthy()
    const h = humanizeZodIssue(issue!, root, t)
    expect(h.path).toBe('id')
    expect(h.friendly).toContain('dash_')
    expect(h.friendly).toContain('my-dashboard')
  })

  it('humanizes patternId regex failure', () => {
    const root = {
      schemaVersion: 2,
      id: 'dash_abcd',
      theme: 'industry-ecommerce',
      version: 1,
      widgets: [{ id: 'kpi_w_orders01', patternId: 'NotKebab.Case' }],
    }
    const result = probeSchema.safeParse(root)
    if (result.success) throw new Error('expected failure')
    const issue = result.error.issues.find((i) => i.path[i.path.length - 1] === 'patternId')
    expect(issue).toBeTruthy()
    const h = humanizeZodIssue(issue!, root, t)
    expect(h.path).toBe('widgets[0].patternId')
    expect(h.friendly).toContain('NotKebab.Case')
    expect(h.friendly).toContain('kebab-case')
  })

  it('humanizes theme regex failure', () => {
    const root = {
      schemaVersion: 2,
      id: 'dash_abcd',
      theme: 'modern',
      version: 1,
      widgets: [],
    }
    const result = probeSchema.safeParse(root)
    if (result.success) throw new Error('expected failure')
    const issue = result.error.issues.find((i) => i.path[0] === 'theme')
    expect(issue).toBeTruthy()
    const h = humanizeZodIssue(issue!, root, t)
    expect(h.path).toBe('theme')
    expect(h.friendly).toContain('modern')
    expect(h.friendly).toContain('industry-')
  })

  it('falls back to issue.message for unknown issue codes', () => {
    const root = {
      schemaVersion: 2,
      id: 'dash_abcd',
      theme: 'industry-ecommerce',
      version: '1', // wrong type
      widgets: [],
    }
    const result = probeSchema.safeParse(root)
    if (result.success) throw new Error('expected failure')
    const issue = result.error.issues.find((i) => i.path[0] === 'version')
    expect(issue).toBeTruthy()
    const h = humanizeZodIssue(issue!, root, t)
    expect(h.path).toBe('version')
    expect(h.friendly.length).toBeGreaterThan(0)
    expect(h.friendly).toBe(issue!.message)
  })

  it('humanizes invalid_type on id field via stretch path', () => {
    const root = {
      schemaVersion: 2,
      id: 123, // wrong type
      theme: 'industry-ecommerce',
      version: 1,
      widgets: [],
    }
    const result = probeSchema.safeParse(root)
    if (result.success) throw new Error('expected failure')
    const issue = result.error.issues.find((i) => i.path[0] === 'id' && i.code === 'invalid_type')
    expect(issue).toBeTruthy()
    const h = humanizeZodIssue(issue!, root, t)
    expect(h.path).toBe('id')
    expect(h.friendly).toContain('id')
    expect(h.friendly).toContain('string')
  })
})
