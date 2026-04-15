export type DataGridColumn = {
  key: string
  header: string
}

export type DataGridProps = {
  columns: DataGridColumn[]
  rows: Array<Record<string, unknown>>
}
