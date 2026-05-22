import { API_PREFIX, getApiBaseUrl } from '../api-prefix'
import type { ExplainResult, IndexHintsResponse } from '@/features/stage/types/diagnostics'

export async function fetchExplainPlan(sessionId: string, sql: string): Promise<ExplainResult> {
  const res = await fetch(`${getApiBaseUrl()}${API_PREFIX}/sessions/${sessionId}/diagnostics/explain`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sql }),
  })
  if (!res.ok) throw new Error(`Explain failed: ${res.status}`)
  return res.json()
}

export async function fetchIndexHints(sessionId: string, sql: string): Promise<IndexHintsResponse> {
  const res = await fetch(`${getApiBaseUrl()}${API_PREFIX}/sessions/${sessionId}/diagnostics/index-hints`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sql }),
  })
  if (!res.ok) throw new Error(`Index hints failed: ${res.status}`)
  return res.json()
}
