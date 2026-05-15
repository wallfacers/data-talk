import { http } from '@/services/http'

export interface RunPrepareRequest {
  scriptContent: string
  language: 'python' | 'javascript'
  connectionId: string
  name?: string
  createdByKind?: 'user' | 'ai'
  sessionId?: string
}

export interface RunPrepareResponse {
  runId: string
  token: string
}

export interface RunCompleteRequest {
  exitCode: number
  stdoutText?: string
  errorMessage?: string
}

export interface DataWriteRequest {
  token: string
  connectionId: string
  tableName: string
  rows: Record<string, unknown>[]
  createTable: boolean
}

export interface DataWriteResponse {
  rowsInserted: number
  tableName: string
  columnsCreated: string[]
}

export interface BatchWriteRequest {
  token: string
  connectionId: string
  sessionId?: string
  tableName: string
  rows: Record<string, unknown>[]
  createTable: boolean
}

export interface BatchWriteResponse {
  sessionId: string
  rowsInserted: number
  totalRowsInserted: number
}

export interface BatchCloseResponse {
  totalRowsInserted: number
  tableName: string
}

export const scriptApi = {
  runPrepare: (req: RunPrepareRequest) =>
    http.post('script/run-prepare', { json: req }).json<RunPrepareResponse>(),

  runComplete: (runId: string, req: RunCompleteRequest) =>
    http.post(`script/${runId}/complete`, { json: req }).json<{ runId: string; status: string }>(),

  dataWrite: (req: DataWriteRequest) =>
    http.post('script-data/write', { json: req }).json<DataWriteResponse>(),

  batchWrite: (req: BatchWriteRequest) =>
    http.post('script-data/batch', { json: req }).json<BatchWriteResponse>(),

  batchClose: (sessionId: string) =>
    http.post('script-data/batch/close', { json: { sessionId } }).json<BatchCloseResponse>(),
}
