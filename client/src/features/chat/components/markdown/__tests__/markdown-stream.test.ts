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

  it('identifies an open fenced code block as a streaming code block', () => {
    const result = stream('Before\n\n```ts\nconst answer =', true)

    expect(result).toHaveLength(2)
    expect(result[0]).toMatchObject({ mode: 'live' })
    expect(result[1] as any).toMatchObject({
      mode: 'stream-code',
      language: 'ts',
      code: 'const answer =',
    })
  })

  it('identifies a message that only contains an open fenced code block', () => {
    const result = stream('```sql\nselect 1', true)

    expect(result).toHaveLength(1)
    expect(result[0] as any).toMatchObject({
      mode: 'stream-code',
      language: 'sql',
      code: 'select 1',
    })
  })

  it('handles empty text', () => {
    const result = stream('', true)
    expect(result.length).toBe(1)
  })

  it('keeps reference-style text in a single live block', () => {
    const text = 'See [the docs][docs]\n\n[docs]: https://example.com/docs'
    const result = stream(text, true)
    expect(result).toHaveLength(1)
    expect(result[0].mode).toBe('live')
    expect(result[0].raw).toContain('[docs]: https://example.com/docs')
  })

  it('drops a partial closing backtick fence so the code height does not jitter', () => {
    // 1 of 3 backticks — looks like the LLM is about to close the fence.
    const one = stream('```ts\nconst a = 1\n`', true)
    expect(one).toHaveLength(1)
    expect(one[0] as any).toMatchObject({
      mode: 'stream-code',
      language: 'ts',
      code: 'const a = 1\n',
    })

    // 2 of 3 backticks — same situation, still partial.
    const two = stream('```ts\nconst a = 1\n``', true)
    expect(two).toHaveLength(1)
    expect(two[0] as any).toMatchObject({
      mode: 'stream-code',
      code: 'const a = 1\n',
    })
  })

  it('preserves genuine blank lines before a partial fence', () => {
    const result = stream('```ts\nconst a = 1\n\n``', true)
    expect(result).toHaveLength(1)
    expect(result[0] as any).toMatchObject({
      mode: 'stream-code',
      code: 'const a = 1\n\n',
    })
  })

  it('drops partial tilde fences symmetrically', () => {
    const result = stream('~~~ts\nconst a = 1\n~~', true)
    expect(result).toHaveLength(1)
    expect(result[0] as any).toMatchObject({
      mode: 'stream-code',
      fence: '~~~',
      code: 'const a = 1\n',
    })
  })

  it('does not strip legitimate backticks on the trailing line with other text', () => {
    const result = stream('```ts\nconst a = `t` // note', true)
    expect(result).toHaveLength(1)
    expect(result[0] as any).toMatchObject({
      mode: 'stream-code',
      code: 'const a = `t` // note',
    })
  })
})
