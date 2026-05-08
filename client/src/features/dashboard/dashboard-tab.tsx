import { useEffect, useState } from 'react'
import { useDashboardTabsStore } from './stores/dashboard-tabs-store'
import { DashboardCanvas } from './dashboard-canvas'
import { fetchDashboard } from './services/dashboard-api'

interface DashboardTabProps {
  tabId: string
}

export function DashboardTab({ tabId }: DashboardTabProps) {
  const tab = useDashboardTabsStore((s) => s.tabs.get(tabId))
  const [mode, setMode] = useState<'viewer' | 'editor'>('viewer')
  const [loading, setLoading] = useState(!tab)

  useEffect(() => {
    if (tab) return
    let cancelled = false
    setLoading(true)
    fetchDashboard(tabId).then((dash) => {
      if (cancelled) return
      if (dash) {
        useDashboardTabsStore.getState().hydrateTab(tabId, dash)
      }
      setLoading(false)
    })
    return () => { cancelled = true }
  }, [tabId, tab])

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full text-sm text-[var(--dt-muted-foreground)]">
        Loading dashboard...
      </div>
    )
  }

  if (!tab) {
    return (
      <div className="flex items-center justify-center h-full text-sm text-[var(--dt-muted-foreground)]">
        Dashboard not found
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-2 px-4 py-2 border-b border-[var(--dt-border)]">
        <h2 className="text-sm font-medium truncate flex-1">{tab.dashboard.title}</h2>
        <button
          type="button"
          className="text-xs px-2 py-1 rounded border border-[var(--dt-border)] hover:bg-[var(--dt-accent)]"
          onClick={() => setMode(mode === 'viewer' ? 'editor' : 'viewer')}
        >
          {mode === 'viewer' ? 'Edit' : 'Done'}
        </button>
      </div>
      <div className="flex-1 min-h-0 overflow-auto">
        <DashboardCanvas tabId={tabId} mode={mode} />
      </div>
    </div>
  )
}
