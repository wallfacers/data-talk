export interface QueryResultData {
  columns: string[]
  rows: Record<string, unknown>[]
  rowCount: number
  durationMs: number
}
