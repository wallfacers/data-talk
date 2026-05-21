import { describe, expect, it } from 'vitest'
import {
  buildSqlResultExportFilename,
  selectSqlResultExportRows,
  toSqlInsert,
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

describe('toSqlInsert', () => {
  const columns = ['id', 'name']
  const rows = [[1, 'Alice'], [2, null]] as unknown[][]

  it('uses double-quote identifiers by default (ANSI SQL)', () => {
    const sql = toSqlInsert(columns, rows, 'users')
    expect(sql).toContain('INSERT INTO "users" ("id", "name") VALUES')
  })

  it('uses backtick identifiers for MySQL', () => {
    const sql = toSqlInsert(columns, rows, 'users', 'mysql')
    expect(sql).toContain('INSERT INTO `users` (`id`, `name`) VALUES')
  })

  it('uses backtick identifiers for MariaDB', () => {
    const sql = toSqlInsert(columns, rows, 'users', 'mariadb')
    expect(sql).toContain('INSERT INTO `users` (`id`, `name`) VALUES')
  })

  it('uses backtick identifiers for TiDB', () => {
    const sql = toSqlInsert(columns, rows, 'users', 'tidb')
    expect(sql).toContain('INSERT INTO `users` (`id`, `name`) VALUES')
  })

  it('uses backtick identifiers for StarRocks', () => {
    const sql = toSqlInsert(columns, rows, 'users', 'starrocks')
    expect(sql).toContain('INSERT INTO `users` (`id`, `name`) VALUES')
  })

  it('uses bracket identifiers for SQL Server', () => {
    const sql = toSqlInsert(columns, rows, 'users', 'sqlserver')
    expect(sql).toContain('INSERT INTO [users] ([id], [name]) VALUES')
  })

  it('uses double-quote identifiers for PostgreSQL', () => {
    const sql = toSqlInsert(columns, rows, 'users', 'postgresql')
    expect(sql).toContain('INSERT INTO "users" ("id", "name") VALUES')
  })

  it('uses double-quote identifiers for Oracle', () => {
    const sql = toSqlInsert(columns, rows, 'users', 'oracle')
    expect(sql).toContain('INSERT INTO "users" ("id", "name") VALUES')
  })

  it('escapes backticks inside names for MySQL', () => {
    const sql = toSqlInsert(['col`name'], [[1]], 't', 'mysql')
    expect(sql).toContain('`col``name`')
  })

  it('escapes brackets inside names for SQL Server', () => {
    const sql = toSqlInsert(['col]name'], [[1]], 't', 'sqlserver')
    expect(sql).toContain('[col]]name]')
  })

  it('escapes double quotes inside names for ANSI', () => {
    const sql = toSqlInsert(['col"name'], [[1]], 't')
    expect(sql).toContain('"col""name"')
  })

  it('serializes NULL values correctly', () => {
    const sql = toSqlInsert(['id', 'val'], [[1, null]], 't', 'mysql')
    expect(sql).toContain("('1', NULL)")
  })

  it('batches rows into groups of 100', () => {
    const manyRows = Array.from({ length: 250 }, (_, i) => [i, `val${i}`])
    const sql = toSqlInsert(['id', 'val'], manyRows, 't')
    const insertCount = sql.split('INSERT INTO').length - 1
    expect(insertCount).toBe(3)
  })
})
