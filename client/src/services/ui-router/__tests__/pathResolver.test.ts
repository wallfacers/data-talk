import { describe, it, expect } from 'vitest'
import { matchPathPattern } from '../pathResolver'

describe('matchPathPattern', () => {
  it('matches exact path', () => {
    expect(matchPathPattern('/content', '/content')).toBe(true)
  })

  it('matches nested static path', () => {
    expect(matchPathPattern('/tables/users/dataType', '/tables/users/dataType')).toBe(true)
  })

  it('matches [id=X] segment against pattern wildcard', () => {
    expect(matchPathPattern('/tables[id=5]/columns[name=email]/dataType',
                             '/tables[id=<n>]/columns[name=<n>]/dataType')).toBe(true)
  })

  it('rejects mismatched tail', () => {
    expect(matchPathPattern('/content', '/database')).toBe(false)
  })

  it('rejects length mismatch', () => {
    expect(matchPathPattern('/content/foo', '/content')).toBe(false)
  })
})
