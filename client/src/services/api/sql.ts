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
  confirmed?: boolean
  riskAck?: 'L1' | 'L2' | 'L3'
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

export type SqlConfirmationPayload = {
  level: 'L2' | 'L3'
  reason: string
  affectedObjects: string[]
  sqlPreview: string
}

export type SqlConfirmationInvalid = {
  reason: 'risk_ack_insufficient'
  ackedRisk: 'L1' | 'L2' | 'L3' | null
  currentRisk: 'L2' | 'L3'
  message: string
}

export type SqlExecuteResponse =
  | { status: 'executed'; resolvedContext: ResolvedDataContext | null; contextNotice?: string | null; results: SqlExecuteResultItem[] }
  | { status: 'requires_confirmation'; resolvedContext: ResolvedDataContext | null; contextNotice?: string | null; confirmation: SqlConfirmationPayload }
  | { status: 'confirmation_invalid'; resolvedContext: ResolvedDataContext | null; contextNotice?: string | null; invalidConfirmation: SqlConfirmationInvalid }

export type SqlResult = SqlExecuteResponse

export async function executeSql(req: SqlExecuteRequest, signal?: AbortSignal): Promise<SqlExecuteResponse> {
  const json: SqlExecuteRequest = { connectionId: req.connectionId, sql: req.sql, source: req.source }
  if (req.sessionId != null) json.sessionId = req.sessionId
  if (req.database != null) json.database = req.database
  if (req.schema != null) json.schema = req.schema
  if (req.confirmed != null) json.confirmed = req.confirmed
  if (req.riskAck != null) json.riskAck = req.riskAck
  const res = await fetch(`${BASE}/api/sql/execute`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(json),
    signal,
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: 'SQL execution failed' }))
    throw new Error((err as any).message ?? 'SQL execution failed')
  }
  return res.json() as Promise<SqlExecuteResponse>
}
