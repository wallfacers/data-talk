export type TableModel = {
  headers: string[]
  rows: string[][]
  sourceHtml: string
  columnCount: number
}

function normalizeCellText(text: string): string {
  return text.replace(/\s+/g, ' ').trim()
}

function readCellText(cell: Element): string {
  return normalizeCellText(cell.textContent ?? '')
}

function pad(values: string[], length: number): string[] {
  if (values.length >= length) return [...values]
  return [...values, ...Array.from({ length: length - values.length }, () => '')]
}

export function extractTableModel(table: HTMLTableElement): TableModel {
  const headers = Array.from(table.querySelectorAll('thead th')).map(readCellText)
  const rows = Array.from(table.querySelectorAll('tbody tr')).map((row) =>
    Array.from(row.querySelectorAll('td, th')).map(readCellText),
  )
  const columnCount = Math.max(headers.length, ...rows.map((row) => row.length), 0)

  return {
    headers: pad(headers, columnCount),
    rows: rows.map((row) => pad(row, columnCount)),
    sourceHtml: table.outerHTML,
    columnCount,
  }
}
