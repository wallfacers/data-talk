import { describe, expect, it } from 'vitest'
import {
  buildSqlResultExportFilename,
  selectSqlResultExportRows,
  toSqlResultCsv,
  toSqlResultDownloadCsv,
  toSqlResultJson,
} from './sql-result-export'

describe('sql-result-export', () => {
  const columns = ['id', 'status', 'status', '']
  const rows = [
    [1, 'paid', 'ok', null],
    [2, 'needs,quote', 'line\nbreak', 'plain'],
  ]

  it('selects either the current page rows or all returned rows', () => {
    expect(selectSqlResultExportRows(rows, rows.slice(0, 1), 'page')).toEqual([rows[0]])
    expect(selectSqlResultExportRows(rows, rows.slice(0, 1), 'result')).toEqual(rows)
  })

  it('serializes SQL result rows to CSV with escaped cells', () => {
    expect(toSqlResultCsv(columns, rows)).toBe(
      'id,status,status,\r\n1,paid,ok,NULL\r\n2,"needs,quote","line\nbreak",plain',
    )
  })

  it('serializes SQL result rows to JSON with stable duplicate column keys', () => {
    expect(toSqlResultJson(columns, rows)).toBe(
      JSON.stringify([
        { id: 1, status: 'paid', status_2: 'ok', column_4: null },
        { id: 2, status: 'needs,quote', status_2: 'line\nbreak', column_4: 'plain' },
      ], null, 2),
    )
  })

  it('adds a UTF-8 BOM for downloaded CSV', () => {
    expect(toSqlResultDownloadCsv(columns, rows).startsWith('﻿')).toBe(true)
  })

  it('builds a filesystem-safe UTC filename', () => {
    expect(buildSqlResultExportFilename('结果集 1 / users', new Date('2026-04-25T09:08:07Z'))).toBe(
      'sql-result-1-users-20260425-090807.csv',
    )
  })
})
