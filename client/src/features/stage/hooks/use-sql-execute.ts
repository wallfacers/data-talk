import { useState, useCallback } from 'react'
import { executeSql, SqlRiskError } from '@/services/api/sql'
import type { SqlExecuteResponse, SqlRiskBlocked } from '@/services/api/sql'

type Status = 'idle' | 'running' | 'success' | 'risk_blocked' | 'error'

export interface UseSqlExecuteReturn {
  execute: (
    sql: string,
    connectionId: string,
    source: 'ai' | 'user',
    context?: { sessionId?: string | null; database?: string | null; schema?: string | null },
    signal?: AbortSignal,
  ) => Promise<SqlExecuteResponse>
  result: SqlExecuteResponse | null
  risk: SqlRiskBlocked | null
  status: Status
  errorMessage: string | null
  reset: () => void
}

export function useSqlExecute(): UseSqlExecuteReturn {
  const [status, setStatus] = useState<Status>('idle')
  const [result, setResult] = useState<SqlExecuteResponse | null>(null)
  const [risk, setRisk] = useState<SqlRiskBlocked | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const execute = useCallback(async (
    sql: string,
    connectionId: string,
    source: 'ai' | 'user',
    context?: { sessionId?: string | null; database?: string | null; schema?: string | null },
    signal?: AbortSignal,
  ) => {
    setStatus('running')
    setResult(null)
    setRisk(null)
    setErrorMessage(null)
    try {
      const req: Parameters<typeof executeSql>[0] = { sql, connectionId, source }
      if (context?.sessionId != null) req.sessionId = context.sessionId
      if (context?.database != null) req.database = context.database
      if (context?.schema != null) req.schema = context.schema
      const data = signal ? await executeSql(req, signal) : await executeSql(req)
      setResult(data)
      setStatus('success')
      return data
    } catch (err: unknown) {
      if ((err instanceof DOMException && err.name === 'AbortError') || (err instanceof Error && err.name === 'AbortError')) {
        setStatus('idle')
        setResult(null)
        setRisk(null)
        setErrorMessage(null)
        throw err
      }
      if (err instanceof SqlRiskError) {
        setRisk(err.risk)
        setStatus('risk_blocked')
      } else {
        setErrorMessage(err instanceof Error ? err.message : 'Unknown error')
        setStatus('error')
      }
      throw err
    }
  }, [])

  const reset = useCallback(() => {
    setStatus('idle')
    setResult(null)
    setRisk(null)
    setErrorMessage(null)
  }, [])

  return { execute, result, risk, status, errorMessage, reset }
}
