import { exportDataFile } from '@/services/api/data-export'
import { quoteIdentifier, resolveIdentifierQuoteStyle } from '@/features/stage/utils/sql-result-export'
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

export function toSqlInsert(model: TableModel, tableName = 'exported_table', connectionKind?: string | null): string {
  const style = resolveIdentifierQuoteStyle(connectionKind)
  const columns = model.headers.map((h) => quoteIdentifier(h, style)).join(', ')
  const quotedTable = quoteIdentifier(tableName, style)
  const batchSize = 100
  const batches: string[] = []

  for (let i = 0; i < model.rows.length; i += batchSize) {
    const batch = model.rows.slice(i, i + batchSize)
    const values = batch
      .map((row) =>
        row
          .map((cell) => {
            if (cell == null) return 'NULL'
            return `'${cell.replace(/'/g, "''")}'`
          })
          .join(', '),
      )
      .map((row) => `(${row})`)
      .join(',\n')
    batches.push(`INSERT INTO ${quotedTable} (${columns}) VALUES\n${values};`)
  }

  return batches.join('\n\n')
}

export async function toDownloadableXlsx(model: TableModel): Promise<Blob> {
  return exportDataFile({
    columns: model.headers,
    rows: model.rows,
    format: 'xlsx',
    tableName: 'exported_table',
  })
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
