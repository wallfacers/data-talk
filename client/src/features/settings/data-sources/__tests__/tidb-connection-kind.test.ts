import { describe, expect, it } from 'vitest'
import { DATABASE_TYPES } from '../connection-form-dialog'
import type { DatabaseKind } from '../connection-form-dialog'

describe('DATABASE_TYPES — TiDB registration', () => {
  const kinds = Object.keys(DATABASE_TYPES) as DatabaseKind[]

  it('includes TiDB in DATABASE_TYPES', () => {
    expect(kinds).toContain('tidb')
  })

  it('TiDB default port is 4000', () => {
    expect(DATABASE_TYPES.tidb.port).toBe(4000)
  })

  it('TiDB label is "TiDB"', () => {
    expect(DATABASE_TYPES.tidb.label).toBe('TiDB')
  })

  it('TiDB appears after MariaDB in the kind order', () => {
    const mariadbIdx = kinds.indexOf('mariadb')
    const tidbIdx = kinds.indexOf('tidb')
    expect(mariadbIdx).toBeGreaterThan(-1)
    expect(tidbIdx).toBeGreaterThan(-1)
    expect(tidbIdx).toBeGreaterThan(mariadbIdx)
  })
})
