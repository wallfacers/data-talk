import { API_PREFIX } from '../api-prefix'
import type { ExplainResult, IndexHintsResponse } from '@/features/stage/types/diagnostics'

const BASE = (() => {
  const env = (import.meta as any).env?.VITE_API_BASE_URL
  return typeof env === 'string' && env.length > 0 ? env.replace(/\/$/, '') : ''
})()

export async function fetchExplainPlan(sessionId: string, sql: string): Promise<ExplainResult> {
  const res = await fetch(`${BASE}${API_PREFIX}/sessions/${sessionId}/diagnostics/explain`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sql }),
  })
  if (!res.ok) throw new Error(`Explain failed: ${res.status}`)
  return res.json()
}

export async function fetchIndexHints(sessionId: string, sql: string): Promise<IndexHintsResponse> {
  const res = await fetch(`${BASE}${API_PREFIX}/sessions/${sessionId}/diagnostics/index-hints`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sql }),
  })
  if (!res.ok) throw new Error(`Index hints failed: ${res.status}`)
  return res.json()
}
