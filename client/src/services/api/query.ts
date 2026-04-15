import { http } from '@/services/http'

export type QueryResult = {
  columns: string[]
  rows: Array<Record<string, unknown>>
  durationMs: number
  rowCount: number
}

export type ExecuteQueryInput = {
  connectionId: string
  sql: string
}

export function executeQuery(input: ExecuteQueryInput) {
  return http.post('query', { json: input }).json<QueryResult>()
}
