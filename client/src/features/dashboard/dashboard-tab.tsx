import { useEffect, useState } from 'react'
import type { StageTab } from '@/stores/stage-store'
import { useDashboardTabsStore } from './stores/dashboard-tabs-store'
import { DashboardIframeShell } from './iframe-shell'
import { coordinator } from '@/features/stage/persistence/stage-persistence-bootstrap'
import { useI18n } from '@/i18n/use-i18n'

interface DashboardTabProps {
  tab: StageTab
}

export function DashboardTab({ tab }: DashboardTabProps) {
  const { t } = useI18n()
  const tabState = useDashboardTabsStore((s) => s.tabs.get(tab.tabId))
  const [loading, setLoading] = useState(!tabState)

  useEffect(() => {
    if (tabState) return
    coordinator.ensureHydrated(tab.tabId)
      .catch(() => {
        setLoading(false)
      })
  }, [tab.tabId, tabState])

  useEffect(() => {
    if (tabState && loading) setLoading(false)
  }, [tabState, loading])

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full text-sm text-[var(--dt-muted-foreground)]">
        {t('dashboard.loading')}
      </div>
    )
  }

  if (!tabState) {
    return (
      <div className="flex items-center justify-center h-full text-sm text-[var(--dt-muted-foreground)]">
        {t('dashboard.notFound')}
      </div>
    )
  }

  const dashboardId = tabState.dashboard.id

  return (
    <div className="flex flex-col h-full bg-[var(--dt-canvas)]">
      <DashboardIframeShell
        dashboardId={dashboardId}
        onError={(e) => console.error('[bezel widget error]', e)}
      />
    </div>
  )
}
