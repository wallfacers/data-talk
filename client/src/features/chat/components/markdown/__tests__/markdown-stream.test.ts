import { describe, it, expect } from 'vitest'
import { stream } from '../markdown-stream'

describe('markdown-stream', () => {
  it('live=false returns single full block', () => {
    const result = stream('# hello\nworld', false)
    expect(result).toHaveLength(1)
    expect(result[0].mode).toBe('full')
    expect(result[0].src).toBe('# hello\nworld')
  })

  it('live=true with unclosed code block separates tail', () => {
    const text = '# title\n\n```js\nconst x ='
    const result = stream(text, true)
    expect(result.length).toBeGreaterThan(0)
    expect(result.at(-1)?.raw).toContain('```js')
  })

  it('handles empty text', () => {
    const result = stream('', true)
    expect(result.length).toBe(1)
  })
})
