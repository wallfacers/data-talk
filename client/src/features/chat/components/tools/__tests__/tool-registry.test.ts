import { describe, it, expect } from 'vitest'
import { ToolRegistry, type ToolRenderer } from '../tool-registry'

describe('ToolRegistry', () => {
  it('register/get roundtrip', () => {
    const r = (() => null) as unknown as ToolRenderer
    ToolRegistry.register('x1', r)
    expect(ToolRegistry.get('x1')).toBe(r)
  })
  it('miss returns undefined', () => {
    expect(ToolRegistry.get('__unknown__')).toBeUndefined()
  })
  it('override replaces existing', () => {
    const a = (() => null) as unknown as ToolRenderer
    const b = (() => null) as unknown as ToolRenderer
    ToolRegistry.register('x2', a)
    ToolRegistry.register('x2', b)
    expect(ToolRegistry.get('x2')).toBe(b)
  })
})
