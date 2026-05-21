import { describe, expect, it } from 'vitest'
import { applyConnectionDefaultDatabase } from '../apply-connection-default-database'

const connections = [
  { id: 'A', databaseName: 'analytics' },
  { id: 'B', databaseName: 'warehouse' },
  { id: 'C', databaseName: null },
]

describe('applyConnectionDefaultDatabase', () => {
  it('(a) preserves explicit string database from patch', () => {
    const result = applyConnectionDefaultDatabase({
      patch: { connectionId: 'A', database: 'custom' },
      current: { connectionId: null, database: null },
      connections,
    })
    expect(result).toEqual({ database: 'custom', appliedFallback: false })
  })

  it('(b) preserves explicit null database from patch', () => {
    const result = applyConnectionDefaultDatabase({
      patch: { connectionId: 'A', database: null },
      current: { connectionId: null, database: null },
      connections,
    })
    expect(result).toEqual({ database: null, appliedFallback: false })
  })

  it('(c) connection changed + database omitted → connection databaseName', () => {
    const result = applyConnectionDefaultDatabase({
      patch: { connectionId: 'A' },
      current: { connectionId: 'B', database: 'warehouse' },
      connections,
    })
    expect(result).toEqual({ database: 'analytics', appliedFallback: true })
  })

  it('(d) connection changed + connection has null databaseName → null', () => {
    const result = applyConnectionDefaultDatabase({
      patch: { connectionId: 'C' },
      current: { connectionId: 'A', database: 'analytics' },
      connections,
    })
    expect(result).toEqual({ database: null, appliedFallback: false })
  })

  it('(e) connection unchanged + database omitted → keeps current.database', () => {
    const result = applyConnectionDefaultDatabase({
      patch: { connectionId: 'A' },
      current: { connectionId: 'A', database: 'previous' },
      connections,
    })
    expect(result).toEqual({ database: 'previous', appliedFallback: false })
  })

  it('(f) empty connections list → no-op (current.database preserved)', () => {
    const result = applyConnectionDefaultDatabase({
      patch: { connectionId: 'A' },
      current: { connectionId: 'B', database: 'warehouse' },
      connections: [],
    })
    expect(result).toEqual({ database: 'warehouse', appliedFallback: false })
  })

  it('(g) connection unknown → no-op (current.database preserved)', () => {
    const result = applyConnectionDefaultDatabase({
      patch: { connectionId: 'ZZ' },
      current: { connectionId: 'A', database: 'analytics' },
      connections,
    })
    expect(result).toEqual({ database: 'analytics', appliedFallback: false })
  })

  it('(h) current.connectionId = null + patch.connectionId set → treats as change, fires fallback', () => {
    const result = applyConnectionDefaultDatabase({
      patch: { connectionId: 'A' },
      current: { connectionId: null, database: null },
      connections,
    })
    expect(result).toEqual({ database: 'analytics', appliedFallback: true })
  })

  it('handles `{database: undefined}` (key present, value undefined) identically to omitted', () => {
    const patch: { connectionId?: string; database?: string | null } = { connectionId: 'A', database: undefined }
    const result = applyConnectionDefaultDatabase({
      patch,
      current: { connectionId: null, database: null },
      connections,
    })
    expect(result).toEqual({ database: 'analytics', appliedFallback: true })
  })

  it('patch.connectionId = null + database omitted → keeps current.database (no fallback)', () => {
    const result = applyConnectionDefaultDatabase({
      patch: { connectionId: null },
      current: { connectionId: 'A', database: 'analytics' },
      connections,
    })
    expect(result).toEqual({ database: 'analytics', appliedFallback: false })
  })
})
