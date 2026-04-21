const BASE = (() => {
  const env = (import.meta as any).env?.VITE_API_BASE_URL
  return typeof env === 'string' && env.length > 0 ? env.replace(/\/$/, '') : ''
})()

export interface SqlExecuteRequest {
  connectionId: string
  sql: string
  source: 'ai' | 'user'
  sessionId?: string | null
  database?: string | null
  schema?: string | null
}

export interface ResolvedDataContext {
  connectionId: string
  connectionName: string
  database: string | null
  schema: string | null
  selectedLevel: 'connection' | 'database' | 'schema'
}

export interface SqlExecuteResultItem {
  resultId: string
  kind: 'result_set' | 'dml_summary' | 'error'
  title: string
  statementIndex: number
  statementText: string
  columns: string[]
  rows: unknown[][]
  rowCount: number
  executionMs: number
  truncated: boolean
  affectedRows?: number | null
  errorMessage?: string | null
}

export interface SqlExecuteResponse {
  resolvedContext: ResolvedDataContext | null
  contextNotice: string | null
  results: SqlExecuteResultItem[]
}

export interface SqlRiskBlocked {
  riskLevel: string
  riskReason: string
}

export class SqlRiskError extends Error {
  constructor(public readonly risk: SqlRiskBlocked) {
    super('risk_blocked')
    this.name = 'SqlRiskError'
  }
}

export type SqlResult = SqlExecuteResponse

export async function executeSql(req: SqlExecuteRequest): Promise<SqlExecuteResponse> {
  const json: SqlExecuteRequest = { connectionId: req.connectionId, sql: req.sql, source: req.source }
  if (req.sessionId != null) json.sessionId = req.sessionId
  if (req.database != null) json.database = req.database
  if (req.schema != null) json.schema = req.schema
  const res = await fetch(`${BASE}/api/sql/execute`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(json),
  })
  if (res.status === 422) {
    const risk: SqlRiskBlocked = await res.json()
    throw new SqlRiskError(risk)
  }
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: 'SQL execution failed' }))
    throw new Error((err as any).message ?? 'SQL execution failed')
  }
  return res.json() as Promise<SqlExecuteResponse>
}
