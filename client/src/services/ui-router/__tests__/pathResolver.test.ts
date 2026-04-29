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

  it('matches [id=X] against any <word> placeholder, not only <n>', () => {
    expect(matchPathPattern('/tables[id=t_42]', '/tables[id=<id>]')).toBe(true)
    expect(matchPathPattern('/tables[id=t_42]/columns[id=c_1]',
                             '/tables[id=<tid>]/columns[id=<cid>]')).toBe(true)
  })

  it('matches whole-segment placeholders', () => {
    expect(matchPathPattern('/positions/users', '/positions/<table>')).toBe(true)
    expect(matchPathPattern('/notes/orders', '/notes/<table>')).toBe(true)
  })

  it('whole-segment placeholder does not match empty actual segment', () => {
    expect(matchPathPattern('/positions/', '/positions/<table>')).toBe(false)
  })

  it('rejects mismatched key in [key=...] wildcard', () => {
    expect(matchPathPattern('/tables[id=5]', '/tables[name=<n>]')).toBe(false)
  })

  it('rejects mismatched tail', () => {
    expect(matchPathPattern('/content', '/database')).toBe(false)
  })

  it('rejects length mismatch', () => {
    expect(matchPathPattern('/content/foo', '/content')).toBe(false)
  })
})
