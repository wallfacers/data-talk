import { http } from '@/services/http'

// ── Types ────────────────────────────────────────────────────────────────

export interface OpLogItem {
  id: string
  sessionId: string
  sessionTitle: string | null
  connectionId: string
  databaseName: string | null
  schemaName: string | null
  tableName: string
  operation: 'INSERT' | 'UPDATE' | 'DELETE'
  affectedRows: number
  undoable: boolean
  status: 'pending' | 'active' | 'undone' | 'expired'
  expiresAt: number
  createdAt: number
  undoneAt: number | null
}

export interface OpLogDetail extends OpLogItem {
  originalSql: string
  inverseSql: string | null
  beforeState: string | null
}

export interface OpLogListResponse {
  items: OpLogItem[]
  total: number
  page: number
  size: number
}

export interface BatchUndoResult {
  id: string
  status: 'undone' | 'expired' | 'already_undone' | 'not_found' | 'error'
  affectedRows: number
  errorMessage: string | null
}

export interface OpLogFilters {
  status?: string[]
  operation?: string[]
  from?: number
  to?: number
  q?: string
}

// ── API Functions ────────────────────────────────────────────────────────

export async function listOpLogs(
  connectionId: string,
  page: number = 0,
  size: number = 50,
  filters?: OpLogFilters,
): Promise<OpLogListResponse> {
  const searchParams: Record<string, string> = {
    page: String(page),
    size: String(size),
  }
  if (filters?.status?.length) searchParams.status = filters.status.join(',')
  if (filters?.operation?.length) searchParams.operation = filters.operation.join(',')
  if (filters?.from != null) searchParams.from = String(filters.from)
  if (filters?.to != null) searchParams.to = String(filters.to)
  if (filters?.q) searchParams.q = filters.q

  return http.get(`connections/${connectionId}/op-logs`, { searchParams }).json<OpLogListResponse>()
}

export async function getOpLogDetail(
  connectionId: string,
  undoLogId: string,
): Promise<OpLogDetail> {
  return http.get(`connections/${connectionId}/op-logs/${undoLogId}`).json<OpLogDetail>()
}

export async function batchUndoOpLogs(
  connectionId: string,
  undoLogIds: string[],
): Promise<{ results: BatchUndoResult[] }> {
  return http.post(`connections/${connectionId}/op-logs/batch-undo`, {
    json: { undoLogIds },
  }).json<{ results: BatchUndoResult[] }>()
}
