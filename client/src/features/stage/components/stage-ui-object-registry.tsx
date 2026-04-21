import { useEffect, useMemo } from 'react'
import { useUIObjectRegistry, uiRouter } from '@/services/ui-router'
import type { UIObject } from '@/services/ui-router'
import type { StageTab } from '@/stores/stage-store'
import { useStageStore } from '@/stores/stage-store'
import { QueryEditorAdapter } from '../adapters/QueryEditorAdapter'
import { WorkspaceAdapter } from '../adapters/WorkspaceAdapter'

function RegisteredInstance({ instance }: { instance: UIObject | null }) {
  useUIObjectRegistry(instance)
  return null
}

function RegisteredQueryEditor({ tabId, sessionId }: { tabId: string; sessionId: string | null }) {
  const instance = useMemo(() => new QueryEditorAdapter(tabId, () => sessionId), [tabId, sessionId])
  useUIObjectRegistry(instance)
  return null
}

export function StageUIObjectRegistry({ sessionId, tabs }: { sessionId: string | null; tabs: StageTab[] }) {
  const workspace = useMemo(() => new WorkspaceAdapter(() => sessionId), [sessionId])
  const activeTabId = useStageStore((s) => {
    if (!sessionId) return s.activeWorkspaceTabId
    return s.activeTabIdBySession.get(sessionId) ?? s.activeWorkspaceTabId ?? null
  })

  useEffect(() => {
    uiRouter.setActiveTabIdProvider(() => activeTabId ?? null)
    return () => uiRouter.setActiveTabIdProvider(() => null)
  }, [activeTabId])

  return (
    <>
      <RegisteredInstance instance={workspace} />
      {tabs
        .filter((tab) => tab.type === 'query_editor')
        .map((tab) => <RegisteredQueryEditor key={tab.tabId} tabId={tab.tabId} sessionId={sessionId} />)}
    </>
  )
}
