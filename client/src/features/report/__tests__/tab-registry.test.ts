import { describe, expect, it } from 'vitest'
import { getTabTypeDescriptor, getScope } from '@/features/stage/registry/tab-type-registry'

describe('report tab registration', () => {
  it('registers report_library with workspace scope', () => {
    const d = getTabTypeDescriptor('report_library')
    expect(d.type).toBe('report_library')
    expect(d.scope).toBe('workspace')
    expect(d.persistent).toBe(true)
  })

  it('registers report_viewer with workspace scope', () => {
    const d = getTabTypeDescriptor('report_viewer')
    expect(d.type).toBe('report_viewer')
    expect(d.scope).toBe('workspace')
    expect(d.persistent).toBe(true)
  })

  it('report_viewer extractContent reads reportId from payload', () => {
    const d = getTabTypeDescriptor('report_viewer')
    expect(d.extractContent({ reportId: 'r-abc' })).toBe('r-abc')
    expect(d.extractContent({})).toBe('')
  })

  it('getScope returns workspace for both report tabs', () => {
    expect(getScope('report_library')).toBe('workspace')
    expect(getScope('report_viewer')).toBe('workspace')
  })
})
