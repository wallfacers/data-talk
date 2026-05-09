import { useEffect, useState } from 'react'
import type { StageTab } from '@/stores/stage-store'
import { useDashboardTabsStore } from './stores/dashboard-tabs-store'
import { DashboardCanvas } from './dashboard-canvas'
import { coordinator } from '@/features/stage/persistence/stage-persistence-bootstrap'
import { Button } from '@/components/ui/button'

interface DashboardTabProps {
  tab: StageTab
}

export function DashboardTab({ tab }: DashboardTabProps) {
  const tabState = useDashboardTabsStore((s) => s.tabs.get(tab.tabId))
  const [loading, setLoading] = useState(!tabState)

  useEffect(() => {
    if (tabState) return
    void coordinator.ensureHydrated(tab.tabId)
  }, [tab.tabId, tabState])

  useEffect(() => {
    if (tabState && loading) setLoading(false)
  }, [tabState, loading])

  const [mode, setMode] = useState<'viewer' | 'editor'>('viewer')

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full text-sm text-[var(--dt-muted-foreground)]">
        Loading dashboard...
      </div>
    )
  }

  if (!tabState) {
    return (
      <div className="flex items-center justify-center h-full text-sm text-[var(--dt-muted-foreground)]">
        Dashboard not found
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-2 px-4 py-2 border-b border-border/50">
        <h2 className="text-sm font-medium truncate flex-1">{tabState.dashboard.title}</h2>
        <Button
          size="sm"
          variant="outline"
          onClick={() => setMode(mode === 'viewer' ? 'editor' : 'viewer')}
        >
          {mode === 'viewer' ? 'Edit' : 'Done'}
        </Button>
      </div>
      <div className="flex-1 min-h-0 overflow-auto">
        <DashboardCanvas tabId={tab.tabId} mode={mode} />
      </div>
    </div>
  )
}
