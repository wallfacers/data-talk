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
  sessionId?: string | null
  database?: string | null
  schema?: string | null
}

export function executeQuery(input: ExecuteQueryInput) {
  const json: ExecuteQueryInput = { connectionId: input.connectionId, sql: input.sql }
  if (input.sessionId != null) json.sessionId = input.sessionId
  if (input.database != null) json.database = input.database
  if (input.schema != null) json.schema = input.schema
  return http.post('query', { json }).json<QueryResult>()
}
