import { stageTabApi } from './stage-tab-api'
import { StagePersistenceCoordinator } from './stage-persistence-coordinator'
import { useStageStore, type StageTab } from '@/stores/stage-store'
import { useSqlWorkbenchStore, type SqlWorkbenchTabState } from '@/features/stage/stores/sql-workbench-store'
import { useErTabsStore } from '@/features/stage/stores/er-tabs-store'
import type { ErDesignerPayload, ErInspectorPayload } from '@/features/stage/stores/er-tabs-payload-types'
import { TAB_TYPE_REGISTRY, isPersistent } from '@/features/stage/registry/tab-type-registry'
import { normalizeQueryEditorPayload } from '@/features/stage/utils/normalize-query-editor-payload'
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

type StageLocalPayloadSummary = {
  tabId: string
  type: string
  payload: unknown
  payloadJson: string
  payloadVersion?: number
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

function stageLocalPayloadSummaries(state: { tabs: StageTab[] }): StageLocalPayloadSummary[] {
  return state.tabs
    .filter((tab) => {
      const descriptor = TAB_TYPE_REGISTRY[tab.type]
      return descriptor?.persistent && descriptor.payloadSource === 'stage_tab'
    })
    .map((tab) => ({
      tabId: tab.tabId,
      type: tab.type,
      payload: tab.payload,
      payloadJson: JSON.stringify(tab.payload ?? {}),
      payloadVersion: tab.payloadVersion,
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
    const tab = useStageStore.getState().findTab(tabId)
    if (!tab || !isPersistent(tab.type)) continue
    if (prevTab && nextTab.sqlText === prevTab.sqlText && sameQueryEditorOverride(nextTab, prevTab)) continue
    coordinator.scheduleContentWrite(tabId, {
      payload: buildPersistedQueryEditorPayload(tab, nextTab, prevTab),
      contentText: nextTab.sqlText,
      expectedVersion: tab.payloadVersion,
    })
  }
}

function diffStageLocalPayloadAndSchedule(next: StageLocalPayloadSummary[], prev: StageLocalPayloadSummary[]): void {
  for (const nextTab of next) {
    if (isHydrationPlaceholderPayload(nextTab)) continue

    const prevTab = prev.find((item) => item.tabId === nextTab.tabId)
    if (prevTab) {
      if (prevTab.payloadJson === nextTab.payloadJson) continue
      if (prevTab.payloadVersion !== nextTab.payloadVersion) continue
    }

    const descriptor = TAB_TYPE_REGISTRY[nextTab.type]
    coordinator.scheduleContentWrite(nextTab.tabId, {
      payload: nextTab.payload,
      contentText: descriptor?.extractContent?.(nextTab.payload) ?? '',
      expectedVersion: nextTab.payloadVersion,
    })
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function isHydrationPlaceholderPayload(tab: StageLocalPayloadSummary): boolean {
  if (coordinator.phase !== 'hydrating') return false
  if (tab.payloadVersion == null) return false
  if (!isRecord(tab.payload)) return false
  return Object.keys(tab.payload).length === 0
}

function sameNullableString(left: string | null | undefined, right: string | null | undefined) {
  return (left ?? null) === (right ?? null)
}

function sameQueryEditorOverride(
  left: Pick<SqlWorkbenchTabState, 'override' | 'useSessionContext'>,
  right: Pick<SqlWorkbenchTabState, 'override' | 'useSessionContext'>,
) {
  return left.useSessionContext === right.useSessionContext
    && sameNullableString(left.override?.connectionId, right.override?.connectionId)
    && sameNullableString(left.override?.database, right.override?.database)
    && sameNullableString(left.override?.schema, right.override?.schema)
}

function buildPersistedQueryEditorPayload(
  tab: StageTab,
  nextTab: SqlWorkbenchTabState,
  prevTab?: SqlWorkbenchTabState,
) {
  const basePayload = isRecord(tab.payload) ? tab.payload : {}
  const { contextPinMode: _contextPinMode, ...persistableBasePayload } = basePayload
  const normalizedPayload = normalizeQueryEditorPayload(tab.payload)
  const overrideChanged = !prevTab || !sameQueryEditorOverride(nextTab, prevTab)
  const contextOverride = overrideChanged
    ? (nextTab.override
      ? {
          connectionId: nextTab.override.connectionId,
          database: nextTab.override.database ?? null,
          schema: nextTab.override.schema ?? null,
        }
      : null)
    : normalizedPayload.contextOverride

  return {
    ...persistableBasePayload,
    initialSql: nextTab.sqlText,
    sqlText: nextTab.sqlText,
    contextOverride,
    useSessionContext: nextTab.useSessionContext,
  }
}

function diffErContentAndSchedule(
  next: { inspectors: Map<string, ErInspectorPayload>; designers: Map<string, ErDesignerPayload> },
  prev: { inspectors: Map<string, ErInspectorPayload>; designers: Map<string, ErDesignerPayload> },
): void {
  scheduleErChanges(next.inspectors, prev.inspectors)
  scheduleErChanges(next.designers, prev.designers)
}

function scheduleErChanges<P>(next: Map<string, P>, prev: Map<string, P>): void {
  for (const [tabId, payload] of next) {
    const prevPayload = prev.get(tabId)
    if (prevPayload === payload) continue
    const tab = useStageStore.getState().findTab(tabId)
    if (!tab || !isPersistent(tab.type)) continue
    const descriptor = TAB_TYPE_REGISTRY[tab.type]
    coordinator.scheduleContentWrite(tabId, {
      payload: payload as Record<string, unknown>,
      contentText: descriptor?.extractContent?.(payload) ?? '',
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

// Subscribe payload diffs for persistent tabs whose content lives directly on
// StageTab.payload instead of an auxiliary store.
{
  let prevPayloads = stageLocalPayloadSummaries(useStageStore.getState())
  useStageStore.subscribe((state) => {
    const next = stageLocalPayloadSummaries(state)
    if (!shallow(prevPayloads, next)) {
      diffStageLocalPayloadAndSchedule(next, prevPayloads)
      prevPayloads = next
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

// Subscribe ER content diffs (debounce 1s)
{
  let prevEr = {
    inspectors: useErTabsStore.getState().inspectors,
    designers: useErTabsStore.getState().designers,
  }
  useErTabsStore.subscribe((state) => {
    const next = { inspectors: state.inspectors, designers: state.designers }
    if (next.inspectors !== prevEr.inspectors || next.designers !== prevEr.designers) {
      diffErContentAndSchedule(next, prevEr)
      prevEr = next
    }
  })
}

export function startStagePersistence() {
  return coordinator.start()
}
