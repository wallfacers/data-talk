import { describe, expect, it } from 'vitest'
import {
  reportListItemSchema,
  reportListSchema,
  reportSystemStatusSchema,
} from '../schema'

describe('report schema', () => {
  it('parses a valid list item', () => {
    const raw = {
      id: 'r-1',
      workspaceId: 'ws-1',
      groupId: 'g-1',
      version: 1,
      title: 'Hi',
      subtitle: null,
      templateId: 'ledger.monthly-business-review.v1',
      generatedAt: 1717000000000,
      pdfStatus: 'processing',
      mdStatus: 'ready',
      groupSize: 1,
    }
    const parsed = reportListItemSchema.parse(raw)
    expect(parsed.id).toBe('r-1')
    expect(parsed.pdfStatus).toBe('processing')
    expect(parsed.groupSize).toBe(1)
  })

  it('parses a list response', () => {
    const raw = { items: [] }
    const parsed = reportListSchema.parse(raw)
    expect(parsed.items).toEqual([])
  })

  it('rejects unknown pdf status', () => {
    expect(() =>
      reportListItemSchema.parse({
        id: 'r-1', workspaceId: 'ws', groupId: 'g', version: 1, title: 't',
        templateId: 'x', generatedAt: 1, pdfStatus: 'unknown', mdStatus: 'ready',
      })
    ).toThrow()
  })

  it('parses system status', () => {
    const parsed = reportSystemStatusSchema.parse({
      chromiumReady: true,
      fontsReady: true,
      skillReady: true,
    })
    expect(parsed.chromiumReady).toBe(true)
  })
})
