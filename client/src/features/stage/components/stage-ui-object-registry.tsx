import { useEffect, useMemo } from 'react'
import { useUIObjectRegistry, uiRouter } from '@/services/ui-router'
import type { UIObject } from '@/services/ui-router'
import type { StageTab } from '@/stores/stage-store'
import { useStageStore } from '@/stores/stage-store'
import { ErDesignerAdapter } from '../adapters/ErDesignerAdapter'
import { ErInspectorAdapter } from '../adapters/ErInspectorAdapter'
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

function RegisteredErInspector({ tabId, sessionId }: { tabId: string; sessionId: string | null }) {
  const instance = useMemo(() => new ErInspectorAdapter(tabId, () => sessionId), [tabId, sessionId])
  useUIObjectRegistry(instance)
  return null
}

function RegisteredErDesigner({ tabId, sessionId }: { tabId: string; sessionId: string | null }) {
  const instance = useMemo(() => new ErDesignerAdapter(tabId, () => sessionId), [tabId, sessionId])
  useUIObjectRegistry(instance)
  return null
}

export function StageUIObjectRegistry({ tabs }: { tabs: StageTab[] }) {
  const workspace = useMemo(() => new WorkspaceAdapter(() => null), [])
  const activeTabId = useStageStore((s) => s.activeTabId)

  useEffect(() => {
    uiRouter.setActiveTabIdProvider(() => activeTabId ?? null)
    return () => uiRouter.setActiveTabIdProvider(() => null)
  }, [activeTabId])

  return (
    <>
      <RegisteredInstance instance={workspace} />
      {tabs
        .filter((tab) => tab.type === 'query_editor')
        .map((tab) => <RegisteredQueryEditor key={tab.tabId} tabId={tab.tabId} sessionId={tab.originSessionId ?? null} />)}
      {tabs
        .filter((tab) => tab.type === 'er_inspector')
        .map((tab) => <RegisteredErInspector key={tab.tabId} tabId={tab.tabId} sessionId={tab.originSessionId ?? null} />)}
      {tabs
        .filter((tab) => tab.type === 'er_designer')
        .map((tab) => <RegisteredErDesigner key={tab.tabId} tabId={tab.tabId} sessionId={tab.originSessionId ?? null} />)}
    </>
  )
}
