import { stageTabApi } from './stage-tab-api'
import { StagePersistenceCoordinator } from './stage-persistence-coordinator'
import { useStageStore, type StageTab } from '@/stores/stage-store'
import { useSqlWorkbenchStore, type SqlWorkbenchTabState } from '@/features/stage/stores/sql-workbench-store'
import { TAB_TYPE_REGISTRY, isPersistent } from '@/features/stage/registry/tab-type-registry'
import { shallow } from 'zustand/shallow'

export const coordinator = new StagePersistenceCoordinator(stageTabApi)

coordinator.resolveTabSnapshot = (tabId) => {
  const tab = useStageStore.getState().findTab(tabId)
  if (!tab || !isPersistent(tab.type)) return null
  return {
    id: tab.tabId,
    type: tab.type,
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
  useStageStore.getState().__hydrateAll(items.map(toStageTab))
}

coordinator.onPayloadHydrated = (tabId, payload, version) => {
  const tab = useStageStore.getState().findTab(tabId)
  if (!tab) return
  TAB_TYPE_REGISTRY[tab.type]?.rehydrate?.(tabId, payload)
  useStageStore.getState().__hydratePayload(tabId, payload, version)
}

coordinator.onPersisted = (tabId, version) => {
  useStageStore.getState().__setPayloadVersion(tabId, version)
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
  archived?: boolean
  lastTouchedAt?: number
}

function toStageTab(item: Record<string, unknown>): StageTab {
  return {
    tabId: String(item.tabId ?? item.id ?? item.objectId ?? ''),
    type: String(item.type ?? 'unknown'),
    title: String(item.title ?? '(untitled)'),
    connectionId: typeof item.connectionId === 'string' ? item.connectionId : undefined,
    database: typeof item.database === 'string'
      ? item.database
      : typeof item.databaseName === 'string' ? item.databaseName : undefined,
    schema: typeof item.schema === 'string'
      ? item.schema
      : typeof item.schemaName === 'string' ? item.schemaName : undefined,
    originSessionId: typeof item.originSessionId === 'string' ? item.originSessionId : undefined,
    pinned: item.pinned === true,
    archived: item.archived === true,
    payload: {},
    payloadVersion: typeof item.payloadVersion === 'number' ? item.payloadVersion : Number(item.payloadVersion) || 1,
    createdAt: typeof item.createdAt === 'number' ? item.createdAt : Number(item.createdAt) || Date.now(),
    lastTouchedAt: typeof item.lastTouchedAt === 'number' ? item.lastTouchedAt : Number(item.lastTouchedAt) || Date.now(),
  }
}

function persistedTabSummaries(state: { tabs: StageTab[] }): TabSummary[] {
  return state.tabs
    .filter((t) => isPersistent(t.type))
    .map((tab) => ({
      tabId: tab.tabId,
      type: tab.type,
      title: tab.title,
      connectionId: tab.connectionId,
      database: tab.database,
      schema: tab.schema,
      pinned: tab.pinned,
      archived: tab.archived,
      lastTouchedAt: tab.lastTouchedAt,
    }))
}

function diffMetaAndSchedule(next: TabSummary[], prev: TabSummary[]): void {
  if (coordinator.phase === 'hydrating') return
  const prevMap = new Map(prev.map((s) => [s.tabId, s]))
  const nextMap = new Map(next.map((s) => [s.tabId, s]))
  for (const nextTab of next) {
    const prevTab = prevMap.get(nextTab.tabId)
    if (!prevTab) {
      coordinator.scheduleMetadataWrite(nextTab.tabId, {})
      continue
    }
    const patch: Record<string, unknown> = {}
    let changed = false
    for (const key of ['title', 'connectionId', 'database', 'schema', 'pinned', 'archived'] as const) {
      if ((nextTab as Record<string, unknown>)[key] !== (prevTab as Record<string, unknown>)[key]) {
        patch[key] = (nextTab as Record<string, unknown>)[key]
        changed = true
      }
    }
    if (changed) {
      coordinator.scheduleMetadataWrite(nextTab.tabId, patch)
    }
  }
  for (const prevTab of prev) {
    if (!nextMap.has(prevTab.tabId)) {
      void coordinator.delete(prevTab.tabId).catch(() => undefined)
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
    if (prevTab && nextTab.sqlText === prevTab.sqlText) continue
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
