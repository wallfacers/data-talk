import { describe, it, expect } from 'vitest'
import { resolveRisk, classifySqlRisk } from '../risk'

describe('risk', () => {
  it('classifySqlRisk L1 for SELECT', () => {
    expect(classifySqlRisk('SELECT * FROM t')).toBe('L1')
    expect(classifySqlRisk('  -- comment\nEXPLAIN SELECT x')).toBe('L1')
  })
  it('classifySqlRisk L2 for INSERT / UPDATE', () => {
    expect(classifySqlRisk('INSERT INTO t VALUES(1)')).toBe('L2')
    expect(classifySqlRisk('UPDATE t SET x=1')).toBe('L2')
    expect(classifySqlRisk('CREATE INDEX i ON t(x)')).toBe('L2')
  })
  it('classifySqlRisk L3 for DELETE / DROP', () => {
    expect(classifySqlRisk('DELETE FROM t')).toBe('L3')
    expect(classifySqlRisk('DROP TABLE t')).toBe('L3')
    expect(classifySqlRisk('TRUNCATE t')).toBe('L3')
  })
  it('classifySqlRisk returns null for WITH / unknown', () => {
    expect(classifySqlRisk('WITH x AS (SELECT 1) UPDATE y SET a=1')).toBeNull()
    expect(classifySqlRisk('gibberish')).toBeNull()
  })
  it('resolveRisk prefers part-level over descriptor', () => {
    expect(resolveRisk({ state: { metadata: { riskLevel: 'L3' } } } as any, { riskLevel: 'L1' } as any)).toBe('L3')
  })
  it('resolveRisk falls back to descriptor', () => {
    expect(resolveRisk({ state: { metadata: {} } } as any, { riskLevel: 'L2' } as any)).toBe('L2')
  })
  it('resolveRisk returns null when nothing available', () => {
    expect(resolveRisk({ state: {} } as any, {} as any)).toBeNull()
  })
})
