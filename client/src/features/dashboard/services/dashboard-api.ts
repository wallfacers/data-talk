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
  sessionId?: string | null,
): Promise<{ id: string; version: number; html?: string } | null> {
  const headers: Record<string, string> = { 'content-type': 'application/json' }
  if (sessionId) headers['X-DataTalk-Session-Id'] = sessionId
  const response = await fetch('/api/dashboards/promote', {
    method: 'POST',
    headers,
    body: JSON.stringify({ dashboard: payload }),
  })
  if (!response.ok) return null
  return response.json()
}

export async function updateDashboard(
  id: string,
  dashboard: unknown,
  baseVersion: number,
): Promise<{ version: number; html?: string; changes?: unknown[] } | null> {
  if (!id || id === 'undefined' || id === 'null') return null
  const response = await fetch(`/api/dashboards/${encodeURIComponent(id)}/update`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ dashboard, baseVersion }),
  })
  if (!response.ok) {
    if (response.status === 409) return null
    return null
  }
  return response.json()
}

export async function previewDashboard(
  dashboard: unknown,
): Promise<{ html: string } | null> {
  const response = await fetch('/api/dashboards/preview', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ dashboard }),
  })
  if (!response.ok) return null
  return response.json()
}

export async function fetchDashboardHtml(id: string): Promise<string | null> {
  if (!id || id === 'undefined' || id === 'null') return null
  const response = await fetch(`/api/dashboards/${encodeURIComponent(id)}/html`)
  if (!response.ok) return null
  return await response.text()
}
