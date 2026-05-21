import { describe, it, expect } from 'vitest'
import { extractHeading } from '../reasoning-heading'

describe('reasoning-heading', () => {
  it('extracts ATX heading', () => {
    expect(extractHeading('## Analyzing query')).toBe('Analyzing query')
  })
  it('extracts setext heading', () => {
    expect(extractHeading('Checking schema\n===')).toBe('Checking schema')
  })
  it('extracts bold line', () => {
    expect(extractHeading('**Thinking**')).toBe('Thinking')
  })
  it('extracts HTML h1', () => {
    expect(extractHeading('<h1>Parse</h1>')).toBe('Parse')
  })
  it('returns undefined for empty', () => {
    expect(extractHeading('')).toBeUndefined()
    expect(extractHeading('  \n')).toBeUndefined()
  })
})
