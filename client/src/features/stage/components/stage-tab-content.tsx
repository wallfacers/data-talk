import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { BangQueryTab } from './bang-query-tab'

export function StageTabContent() {
  const sid = useSessionStore((s) => s.activeSessionId)
  const activeTabId = useStageStore((s) => {
    if (!sid) return s.activeWorkspaceTabId
    return s.activeTabIdBySession.get(sid) ?? s.activeWorkspaceTabId
  })
  const tab = useStageStore((s) => {
    if (!activeTabId) return null
    return s.workspaceTabs.find((t) => t.tabId === activeTabId)
      ?? (sid ? s.tabsBySession.get(sid)?.find((t) => t.tabId === activeTabId) : undefined)
      ?? null
  })

  if (!tab) return null
  switch (tab.type) {
    case 'bang_query': return <BangQueryTab tabId={tab.tabId} />
    default: return <div className="p-4 text-xs text-muted-foreground">Unknown tab type: {tab.type}</div>
  }
}
