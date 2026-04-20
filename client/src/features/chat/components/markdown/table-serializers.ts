import type { TableModel } from './table-model'

const UTF8_BOM = '\uFEFF'

function escapeCsvCell(value: string): string {
  if (!/[,"\r\n]/.test(value)) return value
  return `"${value.replace(/"/g, '""')}"`
}

function flattenTsvCell(value: string): string {
  return value.replace(/\r?\n/g, ' ')
}

function escapeMarkdownCell(value: string): string {
  return value.replace(/\|/g, '\\|').replace(/\r?\n/g, '<br />')
}

function withHeaders(model: TableModel): string[][] {
  return [model.headers, ...model.rows]
}

function normalizeHeaderKeys(headers: string[]): string[] {
  const seen = new Map<string, number>()
  return headers.map((header, index) => {
    const base = header.trim() || `column_${index + 1}`
    const count = (seen.get(base) ?? 0) + 1
    seen.set(base, count)
    return count === 1 ? base : `${base}_${count}`
  })
}

export function toCsv(model: TableModel): string {
  return withHeaders(model)
    .map((row) => row.map(escapeCsvCell).join(','))
    .join('\r\n')
}

export function toTsv(model: TableModel): string {
  return withHeaders(model)
    .map((row) => row.map(flattenTsvCell).join('\t'))
    .join('\n')
}

export function toMarkdownTable(model: TableModel): string {
  const header = `| ${model.headers.map(escapeMarkdownCell).join(' | ')} |`
  const separator = `| ${Array.from({ length: model.columnCount }, () => '---').join(' | ')} |`
  const rows = model.rows.map((row) => `| ${row.map(escapeMarkdownCell).join(' | ')} |`)
  return [header, separator, ...rows].join('\n')
}

export function toJson(model: TableModel): string {
  const keys = normalizeHeaderKeys(model.headers)
  const rows = model.rows.map((row) => Object.fromEntries(keys.map((key, index) => [key, row[index] ?? ''])))
  return JSON.stringify(rows)
}

export function toDownloadableCsv(model: TableModel): string {
  return `${UTF8_BOM}${toCsv(model)}`
}

function padNumber(value: number): string {
  return value.toString().padStart(2, '0')
}

export function getDownloadFilename(now = new Date()): string {
  const yyyy = now.getUTCFullYear()
  const mm = padNumber(now.getUTCMonth() + 1)
  const dd = padNumber(now.getUTCDate())
  const hh = padNumber(now.getUTCHours())
  const mi = padNumber(now.getUTCMinutes())
  const ss = padNumber(now.getUTCSeconds())
  return `table-${yyyy}${mm}${dd}-${hh}${mi}${ss}.csv`
}
