import { useState, useMemo } from 'react'
import { LayoutDashboardIcon, ExternalLinkIcon } from 'lucide-react'
import { dashboardSchema } from '@/features/dashboard/schema'
import type { Dashboard } from '@/features/dashboard/schema'
import { useDashboardTabsStore } from '@/features/dashboard/stores/dashboard-tabs-store'
import { useStageStore } from '@/stores/stage-store'
import { generateUuid } from '@/lib/uuid'

type DashboardBlockState = 'streaming' | 'preview' | 'error'

interface DashboardBlockProps {
  json: string
  streaming: boolean
  messageId?: string
  partId?: string
}

function parseDashboard(json: string): { ok: true; dashboard: Dashboard } | { ok: false; error: string } {
  let parsed: unknown
  try {
    parsed = JSON.parse(json)
  } catch {
    return { ok: false, error: 'Invalid JSON' }
  }
  const result = dashboardSchema.safeParse(parsed)
  if (result.success) {
    return { ok: true, dashboard: result.data }
  }
  return { ok: false, error: result.error.issues.map((i) => i.message).join('; ') }
}

function promoteDashboard(dashboard: Dashboard) {
  const tabId = `dashboard_${generateUuid()}`
  useDashboardTabsStore.getState().hydrateTab(tabId, dashboard)
  useStageStore.getState().openTab({
    tabId,
    type: 'dashboard',
    title: dashboard.title,
    payload: dashboard,
    createdAt: Date.now(),
  })
  useStageStore.getState().openStage()
}

export function DashboardBlock({ json, streaming }: DashboardBlockProps) {
  const [promoted, setPromoted] = useState(false)

  const state: DashboardBlockState = useMemo(() => {
    if (streaming) return 'streaming'
    const parsed = parseDashboard(json)
    if (parsed.ok) return 'preview'
    return 'error'
  }, [json, streaming])

  const parsedDashboard = useMemo(() => {
    if (state !== 'preview') return null
    const result = parseDashboard(json)
    return result.ok ? result.dashboard : null
  }, [json, state])

  if (state === 'streaming') {
    return (
      <div
        data-testid="dashboard-skeleton"
        className="flex items-center gap-2 p-4 rounded border border-[var(--dt-border)] bg-[var(--dt-muted)]"
      >
        <LayoutDashboardIcon className="h-5 w-5 animate-pulse text-[var(--dt-muted-foreground)]" />
        <span className="text-sm text-[var(--dt-muted-foreground)]">Generating dashboard...</span>
      </div>
    )
  }

  if (state === 'error') {
    return (
      <div
        data-testid="dashboard-error"
        className="p-4 rounded border border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-950"
      >
        <div className="flex items-center gap-2 text-sm text-red-600 dark:text-red-400">
          <LayoutDashboardIcon className="h-4 w-4" />
          <span className="font-medium">Dashboard Error</span>
        </div>
        <p className="mt-1 text-xs text-red-500">{parseDashboard(json).ok ? '' : (parseDashboard(json) as { error: string }).error}</p>
      </div>
    )
  }

  const dashboard = parsedDashboard!

  return (
    <div
      data-testid="dashboard-preview"
      className="p-4 rounded border border-[var(--dt-border)] bg-[var(--dt-card)]"
    >
      <div className="flex items-center gap-2 mb-2">
        <LayoutDashboardIcon className="h-4 w-4 text-[var(--dt-muted-foreground)]" />
        <span className="text-sm font-medium">{dashboard.title}</span>
        <span className="text-xs text-[var(--dt-muted-foreground)]">
          {dashboard.widgets.length} widget{dashboard.widgets.length !== 1 ? 's' : ''}
        </span>
      </div>
      {!promoted && (
        <button
          type="button"
          className="flex items-center gap-1 text-xs px-2 py-1 rounded border border-[var(--dt-border)] hover:bg-[var(--dt-accent)] transition-colors"
          onClick={() => { promoteDashboard(dashboard); setPromoted(true) }}
        >
          <ExternalLinkIcon className="h-3 w-3" />
          Open to workbench
        </button>
      )}
      {promoted && (
        <span className="text-xs text-[var(--dt-muted-foreground)]">Opened in workbench</span>
      )}
    </div>
  )
}
