import { useState, useCallback } from 'react'
import { executeSql } from '@/services/api/sql'
import type {
  SqlConfirmationInvalid,
  SqlConfirmationPayload,
  SqlExecuteRequest,
  SqlExecuteResponse,
  SqlExecuteResultItem,
} from '@/services/api/sql'

export type SqlExecuteState =
  | { kind: 'idle' }
  | { kind: 'running' }
  | { kind: 'success'; results: SqlExecuteResultItem[] }
  | { kind: 'error'; message: string }
  | { kind: 'requires_confirmation'; confirmation: SqlConfirmationPayload; lastRequest: SqlExecuteRequest }
  | { kind: 'confirming' }
  | { kind: 'confirmation_invalid'; invalid: SqlConfirmationInvalid; lastRequest: SqlExecuteRequest }

export interface UseSqlExecuteReturn {
  state: SqlExecuteState
  execute: (
    sql: string,
    connectionId: string,
    source: 'ai' | 'user',
    context?: { sessionId?: string | null; database?: string | null; schema?: string | null },
    signal?: AbortSignal,
  ) => Promise<SqlExecuteResponse>
  confirmAndRun: (level: 'L2' | 'L3') => Promise<void>
  cancelConfirmation: () => void
  reset: () => void
}

function handleResponse(
  req: SqlExecuteRequest,
  response: SqlExecuteResponse,
  setState: (state: SqlExecuteState) => void,
) {
  if (response.status === 'executed') {
    setState({ kind: 'success', results: response.results })
  } else if (response.status === 'requires_confirmation') {
    setState({ kind: 'requires_confirmation', confirmation: response.confirmation, lastRequest: req })
  } else if (response.status === 'confirmation_invalid') {
    setState({ kind: 'confirmation_invalid', invalid: response.invalidConfirmation, lastRequest: req })
  }
}

export function useSqlExecute(): UseSqlExecuteReturn {
  const [state, setState] = useState<SqlExecuteState>({ kind: 'idle' })

  const execute = useCallback(async (
    sql: string,
    connectionId: string,
    source: 'ai' | 'user',
    context?: { sessionId?: string | null; database?: string | null; schema?: string | null },
    signal?: AbortSignal,
  ) => {
    setState({ kind: 'running' })
    try {
      const req: SqlExecuteRequest = { sql, connectionId, source }
      if (context?.sessionId != null) req.sessionId = context.sessionId
      if (context?.database != null) req.database = context.database
      if (context?.schema != null) req.schema = context.schema
      const data = signal ? await executeSql(req, signal) : await executeSql(req)
      handleResponse(req, data, setState)
      return data
    } catch (err: unknown) {
      if ((err instanceof DOMException && err.name === 'AbortError') || (err instanceof Error && err.name === 'AbortError')) {
        setState({ kind: 'idle' })
        throw err
      }
      setState({ kind: 'error', message: err instanceof Error ? err.message : 'Unknown error' })
      throw err
    }
  }, [])

  const confirmAndRun = useCallback(async (level: 'L2' | 'L3') => {
    if (state.kind !== 'requires_confirmation' && state.kind !== 'confirmation_invalid') return
    const req = { ...state.lastRequest, confirmed: true as const, riskAck: level }
    setState({ kind: 'confirming' })
    try {
      const res = await executeSql(req)
      handleResponse(req, res, setState)
    } catch (err: unknown) {
      setState({ kind: 'error', message: err instanceof Error ? err.message : 'Unknown error' })
    }
  }, [state])

  const cancelConfirmation = useCallback(() => {
    setState({ kind: 'idle' })
  }, [])

  const reset = useCallback(() => {
    setState({ kind: 'idle' })
  }, [])

  return { state, execute, confirmAndRun, cancelConfirmation, reset }
}
