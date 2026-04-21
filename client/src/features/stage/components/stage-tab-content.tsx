import { useEffect, useMemo } from 'react'
import { useShallow } from 'zustand/react/shallow'
import type { StageTab } from '@/stores/stage-store'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useSqlWorkbenchStore } from '../stores/sql-workbench-store'
import { SqlWorkbenchTab } from './sql-workbench-tab'

const EMPTY_TABS: StageTab[] = []

export function StageTabContent() {
  const sid = useSessionStore((s) => s.activeSessionId)
  const cleanupTabs = useSqlWorkbenchStore((s) => s.cleanupTabs)
  const activeTabId = useStageStore((s) => {
    if (!sid) return s.activeWorkspaceTabId
    return s.activeTabIdBySession.get(sid) ?? s.activeWorkspaceTabId
  })
  const { workspaceTabs, sessionTabs } = useStageStore(
    useShallow((s) => ({
      workspaceTabs: s.workspaceTabs,
      sessionTabs: sid ? (s.tabsBySession.get(sid) ?? EMPTY_TABS) : EMPTY_TABS,
    })),
  )
  const sqlTabIds = useMemo(
    () => [...workspaceTabs, ...sessionTabs]
      .filter((candidate) => candidate.type === 'query_editor')
      .map((candidate) => candidate.tabId),
    [sessionTabs, workspaceTabs],
  )
  const tab = useStageStore((s) => {
    if (!activeTabId) return null
    return (
      s.workspaceTabs.find((t) => t.tabId === activeTabId) ??
      (sid ? s.tabsBySession.get(sid)?.find((t) => t.tabId === activeTabId) : undefined) ??
      null
    )
  })

  useEffect(() => {
    cleanupTabs(sqlTabIds)
  }, [cleanupTabs, sqlTabIds])

  if (!tab || tab.type !== 'query_editor') return null

  return (
    <div className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
      <SqlWorkbenchTab tab={tab} />
    </div>
  )
}
