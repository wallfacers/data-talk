import { describe, it, expect } from 'vitest'

const markdownTableModuleId = '../markdown-table'
const loadMarkdownTable = () => import(markdownTableModuleId)

describe('normalizePipeTables', () => {
  it('exports normalizePipeTables', async () => {
    const mod = await loadMarkdownTable()
    expect(mod.normalizePipeTables).toBeTypeOf('function')
  })

  it('normalizes ai pipe tables with blank lines around the separator', async () => {
    const mod = await loadMarkdownTable()
    const input = '| Name | Value |\n\n| --- | --- |\n\n| JAVA_HOME | GraalVM |'
    const expected = '| Name | Value |\n| --- | --- |\n| JAVA_HOME | GraalVM |'
    expect(mod.normalizePipeTables(input)).toBe(expected)
  })

  it('does not rewrite code fences or plain paragraphs', async () => {
    const mod = await loadMarkdownTable()
    const input = '```sql\nselect a | b from t\n```\n\nA | B is plain text'
    expect(mod.normalizePipeTables(input)).toBe(input)
  })

  it('wraps tables in the markdown table shell', async () => {
    const mod = await loadMarkdownTable()
    const root = document.createElement('div')
    root.innerHTML = '<table><tbody><tr><td>1</td></tr></tbody></table>'

    mod.decorateTables(root)
    mod.decorateTables(root)

    expect(root.querySelector('[data-component="markdown-table"]')).not.toBeNull()
    expect(root.querySelector('[data-slot="markdown-table-scroll"] table')).not.toBeNull()
    expect(root.querySelectorAll('[data-component="markdown-table"]').length).toBe(1)
  })
})
