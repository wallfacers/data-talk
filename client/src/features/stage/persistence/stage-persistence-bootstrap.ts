import { stageTabApi } from './stage-tab-api'
import { StagePersistenceCoordinator } from './stage-persistence-coordinator'
import { useStageStore, type StageTab } from '@/stores/stage-store'
import { useSqlWorkbenchStore, type SqlWorkbenchTabState } from '@/features/stage/stores/sql-workbench-store'
import { TAB_TYPE_REGISTRY, isPersistent, getTabTypeDescriptor } from '@/features/stage/registry/tab-type-registry'
import { shallow } from 'zustand/shallow'

export const coordinator = new StagePersistenceCoordinator(stageTabApi)

coordinator.resolveTabSnapshot = (tabId) => {
  const tab = useStageStore.getState().findTab(tabId)
  if (!tab || !isPersistent(tab.type)) return null
  return {
    id: tab.tabId,
    type: tab.type,
    scope: (getTabTypeDescriptor(tab.type).scope ?? tab.scope) as 'workspace' | 'session',
    title: tab.title,
    connectionId: tab.connectionId ?? null,
    database: tab.database ?? null,
    schema: tab.schema ?? null,
    originSessionId: tab.originSessionId ?? null,
    pinned: tab.pinned ?? false,
    archived: tab.archived ?? false,
    createdAt: tab.createdAt,
    lastTouchedAt: tab.lastTouchedAt ?? Date.now(),
  }
}

coordinator.onHydrated = (items) => {
  useStageStore.getState().__hydrateWorkspaceTabs(items as Array<StageTab & Record<string, unknown>>)
}

coordinator.onPayloadHydrated = (tabId, payload, version) => {
  const tab = useStageStore.getState().findTab(tabId)
  if (!tab) return
  TAB_TYPE_REGISTRY[tab.type]?.rehydrate?.(tabId, payload)
  useStageStore.getState().__hydratePayload(tabId, payload, version)
}

// --- Subscription helpers ---

type TabSummary = {
  tabId: string
  type: string
  title: string
  connectionId?: string
  database?: string
  schema?: string
  pinned?: boolean
  lastTouchedAt?: number
}

function persistedTabSummaries(state: {
  workspaceTabs: StageTab[]
  tabsBySession: Map<string, StageTab[]>
}): TabSummary[] {
  const allTabs: TabSummary[] = []
  for (const tab of state.workspaceTabs) {
    if (!isPersistent(tab.type)) continue
    allTabs.push({
      tabId: tab.tabId,
      type: tab.type,
      title: tab.title,
      connectionId: tab.connectionId,
      database: tab.database,
      schema: tab.schema,
      pinned: tab.pinned,
      lastTouchedAt: tab.lastTouchedAt,
    })
  }
  for (const [, tabs] of state.tabsBySession) {
    for (const tab of tabs) {
      if (!isPersistent(tab.type)) continue
      allTabs.push({
        tabId: tab.tabId,
        type: tab.type,
        title: tab.title,
        connectionId: tab.connectionId,
        database: tab.database,
        schema: tab.schema,
        pinned: tab.pinned,
        lastTouchedAt: tab.lastTouchedAt,
      })
    }
  }
  return allTabs
}

function diffMetaAndSchedule(next: TabSummary[], prev: TabSummary[]): void {
  const prevMap = new Map(prev.map((s) => [s.tabId, s]))
  for (const nextTab of next) {
    const prevTab = prevMap.get(nextTab.tabId)
    if (!prevTab) continue
    const patch: Record<string, unknown> = {}
    let changed = false
    for (const key of ['title', 'connectionId', 'database', 'schema', 'pinned'] as const) {
      if ((nextTab as Record<string, unknown>)[key] !== (prevTab as Record<string, unknown>)[key]) {
        patch[key] = (nextTab as Record<string, unknown>)[key]
        changed = true
      }
    }
    if (changed) {
      coordinator.scheduleMetadataWrite(nextTab.tabId, patch)
    }
  }
}

function diffContentAndSchedule(
  next: Record<string, SqlWorkbenchTabState>,
  prev: Record<string, SqlWorkbenchTabState>,
): void {
  for (const tabId of Object.keys(next)) {
    const nextTab = next[tabId]
    const prevTab = prev[tabId]
    if (!prevTab || nextTab.sqlText === prevTab.sqlText) continue
    const tab = useStageStore.getState().findTab(tabId)
    if (!tab || !isPersistent(tab.type)) continue
    coordinator.scheduleContentWrite(tabId, {
      payload: { sqlText: nextTab.sqlText },
      contentText: nextTab.sqlText,
      expectedVersion: tab.payloadVersion,
    })
  }
}

// Subscribe metadata diffs (immediate write)
{
  let prevMeta = persistedTabSummaries(useStageStore.getState())
  useStageStore.subscribe((state) => {
    const next = persistedTabSummaries(state)
    if (!shallow(prevMeta, next)) {
      diffMetaAndSchedule(next, prevMeta)
      prevMeta = next
    }
  })
}

// Subscribe content diffs (debounce 1s)
{
  let prevTabs = useSqlWorkbenchStore.getState().tabsById
  useSqlWorkbenchStore.subscribe((state) => {
    const next = state.tabsById
    if (!shallow(prevTabs, next)) {
      diffContentAndSchedule(next, prevTabs)
      prevTabs = next
    }
  })
}

export function startStagePersistence() {
  return coordinator.start()
}
