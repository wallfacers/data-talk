export type SqlResultExportScope = 'page' | 'result'

const UTF8_BOM = '﻿'

function stringifyCsvValue(value: unknown): string {
  if (value == null) return 'NULL'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function escapeCsvCell(value: unknown): string {
  const text = stringifyCsvValue(value)
  if (!/[,"\r\n]/.test(text)) return text
  return `"${text.replace(/"/g, '""')}"`
}

function normalizeJsonKeys(columns: string[]): string[] {
  const seen = new Map<string, number>()
  return columns.map((column, index) => {
    const base = column.trim() || `column_${index + 1}`
    const count = (seen.get(base) ?? 0) + 1
    seen.set(base, count)
    return count === 1 ? base : `${base}_${count}`
  })
}

function padNumber(value: number): string {
  return value.toString().padStart(2, '0')
}

function safeFilenamePart(value: string): string {
  const ascii = value
    .normalize('NFKD')
    .replace(/[^\w\s-]/g, ' ')
    .trim()
    .replace(/\s+/g, '-')
    .toLowerCase()
  return ascii || 'result'
}

export function selectSqlResultExportRows(
  allRows: unknown[][],
  pageRows: unknown[][],
  scope: SqlResultExportScope,
): unknown[][] {
  return scope === 'page' ? pageRows : allRows
}

export function toSqlResultCsv(columns: string[], rows: unknown[][]): string {
  return [columns, ...rows]
    .map((row) => row.map(escapeCsvCell).join(','))
    .join('\r\n')
}

export function toSqlResultDownloadCsv(columns: string[], rows: unknown[][]): string {
  return `${UTF8_BOM}${toSqlResultCsv(columns, rows)}`
}

export function toSqlResultJson(columns: string[], rows: unknown[][]): string {
  const keys = normalizeJsonKeys(columns)
  return JSON.stringify(
    rows.map((row) => Object.fromEntries(keys.map((key, index) => [key, row[index] ?? null]))),
    null,
    2,
  )
}

export function buildSqlResultExportFilename(title: string, now = new Date(), ext = 'csv'): string {
  const yyyy = now.getUTCFullYear()
  const mm = padNumber(now.getUTCMonth() + 1)
  const dd = padNumber(now.getUTCDate())
  const hh = padNumber(now.getUTCHours())
  const mi = padNumber(now.getUTCMinutes())
  const ss = padNumber(now.getUTCSeconds())
  return `sql-result-${safeFilenamePart(title)}-${yyyy}${mm}${dd}-${hh}${mi}${ss}.${ext}`
}

export function toSqlInsert(columns: string[], rows: unknown[][], tableName = 'table'): string {
  const quotedColumns = columns.map((c) => `"${c.replace(/"/g, '""')}"`)
  const batchSize = 100
  const batches: string[] = []
  for (let i = 0; i < rows.length; i += batchSize) {
    const batch = rows.slice(i, i + batchSize)
    const values = batch.map((row) =>
      '(' + row.map((v) => {
        if (v == null) return 'NULL'
        const s = String(typeof v === 'object' ? JSON.stringify(v) : v)
        return `'${s.replace(/'/g, "''")}'`
      }).join(', ') + ')',
    )
    batches.push(
      `INSERT INTO "${tableName.replace(/"/g, '""')}" (${quotedColumns.join(', ')}) VALUES\n${values.join(',\n')};`,
    )
  }
  return batches.join('\n')
}
