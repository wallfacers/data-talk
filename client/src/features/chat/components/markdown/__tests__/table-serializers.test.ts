import { describe, expect, it } from 'vitest'
import type { TableModel } from '../table-model'
import {
  getDownloadFilename,
  toCsv,
  toDownloadableCsv,
  toJson,
  toMarkdownTable,
  toTsv,
} from '../table-serializers'

describe('table serializers', () => {
  const model: TableModel = {
    headers: ['姓名', '备注'],
    rows: [['Alice', '含,逗号'], ['Bob', '双引号"与\n换行']],
    columnCount: 2,
    sourceHtml: '<table><tbody><tr><td>Alice</td></tr></tbody></table>',
  }

  it('serializes CSV with RFC-style escaping and CRLF line endings', () => {
    expect(toCsv(model)).toBe(
      '姓名,备注\r\nAlice,"含,逗号"\r\nBob,"双引号""与\n换行"',
    )
  })

  it('serializes TSV with embedded newlines flattened to spaces', () => {
    expect(toTsv(model)).toBe(
      '姓名\t备注\nAlice\t含,逗号\nBob\t双引号"与 换行',
    )
  })

  it('serializes markdown tables with escaped pipes and <br /> newlines', () => {
    const pipeModel: TableModel = {
      headers: ['列|1', '列2'],
      rows: [['A|B', '上\n下']],
      columnCount: 2,
      sourceHtml: '<table></table>',
    }

    expect(toMarkdownTable(pipeModel)).toBe(
      '| 列\\|1 | 列2 |\n| --- | --- |\n| A\\|B | 上<br />下 |',
    )
  })

  it('serializes JSON with normalized duplicate and blank keys', () => {
    const duplicateHeaders: TableModel = {
      headers: ['值', '值', ''],
      rows: [['1', '2', '3']],
      columnCount: 3,
      sourceHtml: '<table></table>',
    }

    expect(toJson(duplicateHeaders)).toBe('[{"值":"1","值_2":"2","column_3":"3"}]')
  })

  it('prepends a UTF-8 BOM for downloadable CSV', () => {
    expect(toDownloadableCsv(model).startsWith('\uFEFF')).toBe(true)
  })

  it('builds timestamped csv filenames', () => {
    expect(getDownloadFilename(new Date('2026-04-20T09:08:07Z'))).toBe('table-20260420-090807.csv')
  })
})
