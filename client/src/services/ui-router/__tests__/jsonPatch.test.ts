import { describe, it, expect } from 'vitest'
import { applyPatch } from '../jsonPatch'

describe('applyPatch', () => {
  it('replaces top-level field', () => {
    const state = { content: 'a', connectionId: 'c1' }
    const out = applyPatch(state, [{ op: 'replace', path: '/content', value: 'b' }])
    expect(out.content).toBe('b')
    expect(out.connectionId).toBe('c1')
    expect(state.content).toBe('a') // immutability
  })

  it('adds to array tail with /arr/-', () => {
    const state = { tags: ['a', 'b'] }
    const out = applyPatch(state, [{ op: 'add', path: '/tags/-', value: 'c' }])
    expect(out.tags).toEqual(['a', 'b', 'c'])
  })

  it('addresses array element by [name=X]', () => {
    const state = { columns: [{ name: 'id', dataType: 'BIGINT' }, { name: 'email', dataType: 'VARCHAR' }] }
    const out = applyPatch(state, [
      { op: 'replace', path: '/columns[name=email]/dataType', value: 'TEXT' },
    ])
    expect(out.columns[1].dataType).toBe('TEXT')
    expect(out.columns[0].dataType).toBe('BIGINT')
  })

  it('removes element', () => {
    const state = { tags: ['a', 'b', 'c'] }
    const out = applyPatch(state, [{ op: 'remove', path: '/tags/1' }])
    expect(out.tags).toEqual(['a', 'c'])
  })
})
