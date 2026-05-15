import type { Dashboard } from '../schema'
import { dashboardSchema } from '../schema'

export async function fetchDashboard(id: string): Promise<Dashboard | null> {
  if (!id || id === 'undefined' || id === 'null') return null
  const response = await fetch(`/api/dashboards/${encodeURIComponent(id)}`)
  if (!response.ok) return null
  const data = await response.json()
  const parsed = dashboardSchema.safeParse(data)
  return parsed.success ? parsed.data : null
}

export async function promoteDashboard(
  payload: unknown,
  html?: string,
  sessionId?: string | null,
): Promise<{ id: string; version: number } | null> {
  const body: Record<string, unknown> = { dashboard: payload }
  if (html != null && html.length > 0) body.html = html
  const headers: Record<string, string> = { 'content-type': 'application/json' }
  // Server-side enrichment backstop: when the AI-emitted JSON omits
  // defaultDatabase/defaultSchema, the backend uses this session's data-context
  // to fill them in before persisting. Required for multi-database connections
  // whose connection record has no default databaseName configured.
  if (sessionId) headers['X-DataTalk-Session-Id'] = sessionId
  const response = await fetch('/api/dashboards/promote', {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  })
  if (!response.ok) return null
  return response.json()
}

export async function patchDashboard(
  id: string,
  baseVersion: number,
  ops: unknown[],
): Promise<{ ok: boolean; version?: number; error?: string }> {
  if (!id || id === 'undefined' || id === 'null') return { ok: false, error: 'invalid dashboard id' }
  const response = await fetch(`/api/dashboards/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ baseVersion, ops }),
  })
  if (!response.ok) {
    if (response.status === 409) {
      const detail = await response.json().catch(() => ({})) as { message?: string }
      return { ok: false, error: detail.message ?? 'version conflict' }
    }
    const detail = await response.json().catch(() => ({})) as { message?: string }
    return { ok: false, error: detail.message ?? `patch failed: ${response.status}` }
  }
  const result = (await response.json()) as { version?: number }
  return { ok: true, version: result.version }
}

export async function fetchDashboardHtml(id: string): Promise<string | null> {
  if (!id || id === 'undefined' || id === 'null') return null
  const response = await fetch(`/api/dashboards/${encodeURIComponent(id)}/html`)
  if (!response.ok) return null
  return await response.text()
}
