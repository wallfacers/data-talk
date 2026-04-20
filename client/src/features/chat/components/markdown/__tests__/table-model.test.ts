import { describe, expect, it } from 'vitest'
import { extractTableModel } from '../table-model'

describe('extractTableModel', () => {
  it('extracts headers and rows from a rendered markdown table', () => {
    const root = document.createElement('div')
    root.innerHTML = `
      <table>
        <thead><tr><th>星期</th><th>主食</th></tr></thead>
        <tbody><tr><td>周一</td><td>米饭</td></tr></tbody>
      </table>
    `

    expect(extractTableModel(root.querySelector('table') as HTMLTableElement)).toEqual({
      headers: ['星期', '主食'],
      rows: [['周一', '米饭']],
      columnCount: 2,
      sourceHtml: expect.stringContaining('<table>'),
    })
  })

  it('pads short rows to the widest column count', () => {
    const root = document.createElement('div')
    root.innerHTML = `
      <table>
        <thead><tr><th>值</th><th>值</th><th></th></tr></thead>
        <tbody><tr><td>1</td><td>2</td></tr></tbody>
      </table>
    `

    expect(extractTableModel(root.querySelector('table') as HTMLTableElement)).toMatchObject({
      headers: ['值', '值', ''],
      rows: [['1', '2', '']],
      columnCount: 3,
    })
  })

  it('uses visible text content from inline formatting', () => {
    const root = document.createElement('div')
    root.innerHTML = `
      <table>
        <thead><tr><th>列</th></tr></thead>
        <tbody><tr><td><strong>粗体</strong> <code>code</code></td></tr></tbody>
      </table>
    `

    expect(extractTableModel(root.querySelector('table') as HTMLTableElement).rows).toEqual([
      ['粗体 code'],
    ])
  })
})
