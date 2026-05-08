import type { Dashboard } from '../schema'
import { dashboardSchema } from '../schema'

export async function fetchDashboard(tabId: string): Promise<Dashboard | null> {
  const response = await fetch(`/api/dashboard/${tabId}`)
  if (!response.ok) return null
  const data = await response.json()
  const parsed = dashboardSchema.safeParse(data)
  return parsed.success ? parsed.data : null
}

export async function patchDashboard(tabId: string, ops: unknown[]): Promise<{ ok: boolean; version?: number; error?: string }> {
  const response = await fetch(`/api/dashboard/${tabId}/patch`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ ops }),
  })
  if (!response.ok) {
    const detail = await response.json().catch(() => ({})) as { message?: string }
    return { ok: false, error: detail.message ?? `patch failed: ${response.status}` }
  }
  const result = await response.json() as { version?: number }
  return { ok: true, version: result.version }
}
