import { create } from 'zustand'
import {
  useSqlWorkbenchStore,
  type SqlWorkbenchEditResult,
  type SqlWorkbenchTextEdit,
} from '@/features/stage/stores/sql-workbench-store'
import { resolveUniqueTabTitle } from '@/features/stage/utils/unique-tab-title'
import { generateUuid } from '@/lib/uuid'

type RevealOrigin = { x: number; y: number }

export type SidebarSelection =
  | { kind: 'tool'; tool: 'sql' | 'er' | 'report' | 'dashboard' }
  | { kind: 'connection'; connectionId: string }
  | { kind: 'database'; connectionId: string; database: string }
  | { kind: 'schema'; connectionId: string; database?: string | null; schema: string }
  | {
      kind: 'resource_tool'
      tool: 'sql' | 'er'
      connectionId: string
      database?: string | null
      schema?: string | null
    }

export type RailPanel = 'schema' | 'history' | 'outline' | 'diagnostics'

export type QueryEditorOpenMode = 'always_new' | 'reuse_by_resource_context'

export type QueryEditorOpenInput = {
  sessionId: string | null
  scope: 'workspace' | 'session'
  baseTitle: string
  openMode: QueryEditorOpenMode
  entryMode: 'blank' | 'direct_sql' | 'resource_sql' | 'ui_exec' | 'ai_open'
  initialContent?: string
  autoRun?: boolean
  connectionId?: string | null
  connectionName?: string | null
  database?: string | null
  schema?: string | null
}

export type QueryEditorTextEdit = SqlWorkbenchTextEdit

export type QueryEditorEditResult = SqlWorkbenchEditResult

export interface StageTab {
  tabId: string
  type: string
  title: string
  connectionId?: string
  connectionName?: string
  database?: string
  schema?: string
  originSessionId?: string
  scope: 'session' | 'workspace'
  pinned?: boolean
  archived?: boolean
  payload: unknown
  payloadVersion?: number
  lastTouchedAt?: number
  createdAt: number
}

export type StageState = {
  openBySession: Map<string, boolean>
  autoOpenedSessions: Set<string>
  maximizedBySession: Map<string, boolean>
  revealOrigin: RevealOrigin | null
  sidebarCollapsedBySession: Map<string, boolean>
  sidebarSelectionBySession: Map<string, SidebarSelection | null>
  resourceTreeExpandedBySession: Map<string, string[]>
  activeRailPanelBySession: Map<string, RailPanel | null>

  workspaceTabs: StageTab[]
  tabsBySession: Map<string, StageTab[]>
  activeWorkspaceTabId: string | null
  activeTabIdBySession: Map<string, string | null>

  // Workset (Phase 2)
  openTabIds: Set<string>
  openTabIdsOrdered: string[]
  leftRailWidth: number
  leftRailCollapsed: boolean

  ensureOpenInWorkset: (tabId: string) => void
  detachFromWorkset: (tabId: string) => void
  trashTab: (tabId: string) => Promise<void>
  setLeftRailWidth: (px: number) => void
  toggleLeftRailCollapsed: () => void

  openStage: (sessionId: string) => void
  closeStage: (sessionId: string) => void
  toggleStage: (sessionId: string) => void
  toggleMaximized: (sessionId: string) => void
  setRevealOrigin: (origin: RevealOrigin | null) => void
  notifyArtifactArrived: (sessionId: string) => void
  syncCollapsed: (sessionId: string, collapsed: boolean) => void
  toggleSidebarCollapsed: (sessionId: string) => void
  setSidebarSelection: (sessionId: string, selection: SidebarSelection | null) => void
  toggleResourceExpanded: (sessionId: string, nodeId: string) => void
  setResourceExpanded: (sessionId: string, nodeIds: string[]) => void
  setActiveRailPanel: (sessionId: string, panel: RailPanel | null) => void
  toggleRailPanel: (sessionId: string, panel: RailPanel) => void
  clear: (sessionId: string) => void
  clearAllSessionState: () => void
  focusWorkspaceTabForSession: (sessionId: string) => void

  // Tab CRUD（新）
  openTab: (tab: StageTab) => void
  /**
   * @deprecated Phase 2: closeTab is now an alias of detachFromWorkset.
   * Use detachFromWorkset directly.
   */
  closeTab: (tabId: string) => void
  focusTab: (tabId: string) => void
  listTabs: (sessionId: string | null) => StageTab[]
  updateTabPayload: (tabId: string, updater: (prev: unknown) => unknown) => void
  openArtifactPreviewTab: (sessionId: string, artifactId: string, title: string) => void
  openQueryEditor: (input: QueryEditorOpenInput) => { tabId: string; created: boolean }
  setQueryEditorContext: (
    tabId: string,
    context: {
      connectionId?: string | null
      connectionName?: string | null
      database?: string | null
      schema?: string | null
    },
  ) => void
  replaceQueryEditorContent: (tabId: string, content: string, baseVersion: number) => QueryEditorEditResult
  applyQueryEditorTextEdits: (
    tabId: string,
    params: { baseVersion: number; edits: QueryEditorTextEdit[] },
  ) => QueryEditorEditResult
  setQueryEditorCursor: (tabId: string, cursor: { line: number; column: number }) => void

  // Persistence mutation API
  findTab: (tabId: string) => StageTab | null
  __hydrateAll: (items: StageTab[]) => void
  __hydrateWorkspaceTabs: (items: StageTab[]) => void
  __hydrateSessionTabs: (sessionId: string, items: StageTab[]) => void
  __hydratePayload: (tabId: string, payload: unknown, version: number) => void
  __setPayloadVersion: (tabId: string, version: number) => void
  archiveTab: (id: string, archived: boolean) => void
  setTabPinned: (id: string, pinned: boolean) => void
  setTabTitle: (id: string, title: string) => void
}

const LEFT_RAIL_WIDTH_KEY = 'stage.leftRail.width'
const LEFT_RAIL_COLLAPSED_KEY = 'stage.leftRail.collapsed'

function loadLeftRailWidth(): number {
  try {
    const v = localStorage.getItem(LEFT_RAIL_WIDTH_KEY)
    if (v) {
      const n = parseInt(v, 10)
      if (!isNaN(n) && n >= 180 && n <= 320) return n
    }
  } catch {}
  return 240
}

function loadLeftRailCollapsed(): boolean {
  try {
    return localStorage.getItem(LEFT_RAIL_COLLAPSED_KEY) === 'true'
  } catch {
    return false
  }
}

function archiveDetach(s: StageState, tabId: string) {
  const nextIds = new Set(s.openTabIds); nextIds.delete(tabId)
  const nextOrder = s.openTabIdsOrdered.filter((id) => id !== tabId)
  const wasActive = s.activeWorkspaceTabId === tabId
  const nextActive = wasActive ? (nextOrder[nextOrder.length - 1] ?? null) : s.activeWorkspaceTabId
  return { openTabIds: nextIds, openTabIdsOrdered: nextOrder, activeWorkspaceTabId: nextActive }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function buildQueryEditorPayload(input: QueryEditorOpenInput) {
  const source: 'ai' | 'user' = input.entryMode === 'ai_open' ? 'ai' : 'user'
  return {
    entryMode: input.entryMode,
    source,
    autoRun: input.autoRun === true,
    connectionId: input.connectionId ?? null,
    connectionName: input.connectionName ?? null,
    database: input.database ?? null,
    schema: input.schema ?? null,
  }
}

function updateQueryEditorPayload(
  payload: unknown,
  patch: Partial<ReturnType<typeof buildQueryEditorPayload>>,
) {
  if (isRecord(payload)) {
    return {
      ...payload,
      ...patch,
    }
  }
  return patch
}

function matchesNullable(left?: string | null, right?: string | null) {
  return (left ?? null) === (right ?? null)
}

function updateTabMeta(
  tabs: StageTab[],
  tabId: string,
  updater: (tab: StageTab) => StageTab,
) {
  const index = tabs.findIndex((tab) => tab.tabId === tabId)
  if (index < 0) return null
  const next = [...tabs]
  next[index] = updater(next[index])
  return next
}

function mergeHydratedWorkspaceTabs(existingTabs: StageTab[], items: StageTab[]) {
  const incomingMap = new Map(items.map((t) => [t.tabId, t]))
  const merged = existingTabs.map((existing) => {
    const hydrated = incomingMap.get(existing.tabId)
    if (!hydrated) return existing
    return { ...existing, ...hydrated }
  })
  for (const item of items) {
    if (!merged.some((t) => t.tabId === item.tabId)) {
      merged.push(item)
    }
  }
  return merged
}

export const useStageStore = create<StageState>((set, get) => ({
  openBySession: new Map(),
  autoOpenedSessions: new Set(),
  maximizedBySession: new Map(),
  revealOrigin: null,
  sidebarCollapsedBySession: new Map(),
  sidebarSelectionBySession: new Map(),
  resourceTreeExpandedBySession: new Map(),
  activeRailPanelBySession: new Map(),

  workspaceTabs: [],
  tabsBySession: new Map(),
  activeWorkspaceTabId: null,
  activeTabIdBySession: new Map(),

  openTabIds: new Set<string>(),
  openTabIdsOrdered: [],
  leftRailWidth: loadLeftRailWidth(),
  leftRailCollapsed: loadLeftRailCollapsed(),

  openStage: (sid) => set((s) => { const m = new Map(s.openBySession); m.set(sid, true); return { openBySession: m } }),
  closeStage: (sid) => set((s) => { const m = new Map(s.openBySession); m.set(sid, false); const a = new Set(s.autoOpenedSessions); a.add(sid); return { openBySession: m, autoOpenedSessions: a } }),
  toggleStage: (sid) => { const cur = !!get().openBySession.get(sid); if (cur) get().closeStage(sid); else get().openStage(sid) },
  toggleMaximized: (sid) => set((s) => { const m = new Map(s.maximizedBySession); m.set(sid, !m.get(sid)); return { maximizedBySession: m } }),
  setRevealOrigin: (origin) => set({ revealOrigin: origin }),
  notifyArtifactArrived: (sid) => set((s) => {
    if (s.autoOpenedSessions.has(sid)) return s
    if (s.openBySession.get(sid)) return s
    const m = new Map(s.openBySession); m.set(sid, true)
    const a = new Set(s.autoOpenedSessions); a.add(sid)
    return { openBySession: m, autoOpenedSessions: a }
  }),
  syncCollapsed: (sid, collapsed) => set((s) => {
    const cur = s.openBySession.get(sid); const next = !collapsed
    if (cur === next) return s
    const m = new Map(s.openBySession); m.set(sid, next)
    if (collapsed) { const a = new Set(s.autoOpenedSessions); a.add(sid); return { openBySession: m, autoOpenedSessions: a } }
    return { openBySession: m }
  }),
  toggleSidebarCollapsed: (sid) => set((s) => {
    const map = new Map(s.sidebarCollapsedBySession)
    map.set(sid, !map.get(sid))
    return { sidebarCollapsedBySession: map }
  }),
  setSidebarSelection: (sid, selection) => set((s) => {
    const map = new Map(s.sidebarSelectionBySession)
    map.set(sid, selection)
    return { sidebarSelectionBySession: map }
  }),
  toggleResourceExpanded: (sid, nodeId) => set((s) => {
    const current = s.resourceTreeExpandedBySession.get(sid) ?? []
    const next = current.includes(nodeId)
      ? current.filter((id) => id !== nodeId)
      : [...current, nodeId]
    const map = new Map(s.resourceTreeExpandedBySession)
    map.set(sid, next)
    return { resourceTreeExpandedBySession: map }
  }),
  setResourceExpanded: (sid, nodeIds) => set((s) => {
    const map = new Map(s.resourceTreeExpandedBySession)
    map.set(sid, [...nodeIds])
    return { resourceTreeExpandedBySession: map }
  }),
  setActiveRailPanel: (sid, panel) => set((s) => {
    const map = new Map(s.activeRailPanelBySession)
    map.set(sid, panel)
    return { activeRailPanelBySession: map }
  }),
  toggleRailPanel: (sid, panel) => set((s) => {
    const current = s.activeRailPanelBySession.get(sid) ?? null
    const next = current === panel ? null : panel
    const map = new Map(s.activeRailPanelBySession)
    map.set(sid, next)
    return { activeRailPanelBySession: map }
  }),
  clear: (sid) => set((s) => {
    const openMap = new Map(s.openBySession); openMap.delete(sid)
    const a = new Set(s.autoOpenedSessions); a.delete(sid)
    const maxMap = new Map(s.maximizedBySession); maxMap.delete(sid)
    const collapsedMap = new Map(s.sidebarCollapsedBySession); collapsedMap.delete(sid)
    const selectionMap = new Map(s.sidebarSelectionBySession); selectionMap.delete(sid)
    const expandedMap = new Map(s.resourceTreeExpandedBySession); expandedMap.delete(sid)
    const railMap = new Map(s.activeRailPanelBySession); railMap.delete(sid)
    const ts = new Map(s.tabsBySession); ts.delete(sid)
    const ats = new Map(s.activeTabIdBySession); ats.delete(sid)
    return {
      openBySession: openMap,
      autoOpenedSessions: a,
      maximizedBySession: maxMap,
      sidebarCollapsedBySession: collapsedMap,
      sidebarSelectionBySession: selectionMap,
      resourceTreeExpandedBySession: expandedMap,
      activeRailPanelBySession: railMap,
      tabsBySession: ts,
      activeTabIdBySession: ats,
    }
  }),

  clearAllSessionState: () => set({
    openBySession: new Map(),
    autoOpenedSessions: new Set(),
    maximizedBySession: new Map(),
    sidebarCollapsedBySession: new Map(),
    sidebarSelectionBySession: new Map(),
    resourceTreeExpandedBySession: new Map(),
    activeRailPanelBySession: new Map(),
    tabsBySession: new Map(),
    activeTabIdBySession: new Map(),
  }),

  focusWorkspaceTabForSession: (sessionId) => set((s) => {
    const activeTabIdBySession = new Map(s.activeTabIdBySession)
    activeTabIdBySession.set(sessionId, null)
    return { activeTabIdBySession }
  }),

  openTab: (tab) => set((s) => {
    if (tab.scope === 'workspace') {
      return { workspaceTabs: [...s.workspaceTabs, tab], activeWorkspaceTabId: tab.tabId }
    }
    const sid = tab.originSessionId
    if (!sid) throw new Error('session-scoped tab requires originSessionId')
    const existing = s.tabsBySession.get(sid) ?? []
    const next = new Map(s.tabsBySession); next.set(sid, [...existing, tab])
    const active = new Map(s.activeTabIdBySession); active.set(sid, tab.tabId)
    return { tabsBySession: next, activeTabIdBySession: active }
  }),

  closeTab: (tabId) => {
    // Phase 2 semantic: close = detach from workset (not DB delete).
    useStageStore.getState().detachFromWorkset(tabId)
  },

  focusTab: (tabId) => {
    useStageStore.getState().ensureOpenInWorkset(tabId)
    set((s) => {
      if (s.workspaceTabs.some((t) => t.tabId === tabId)) {
        return { activeWorkspaceTabId: tabId }
      }
      for (const [sid, arr] of s.tabsBySession.entries()) {
        if (arr.some((t) => t.tabId === tabId)) {
          const map = new Map(s.activeTabIdBySession); map.set(sid, tabId)
          return { activeTabIdBySession: map, activeWorkspaceTabId: tabId }
        }
      }
      return s
    })
  },

  ensureOpenInWorkset: (tabId) => set((s) => {
    if (s.openTabIds.has(tabId)) return s
    const allTabs = [...s.workspaceTabs, ...[...s.tabsBySession.values()].flat()]
    const target = allTabs.find((t) => t.tabId === tabId)
    if (!target) return s
    if (target.archived) return s
    const next = new Set(s.openTabIds); next.add(tabId)
    return { openTabIds: next, openTabIdsOrdered: [...s.openTabIdsOrdered, tabId] }
  }),

  detachFromWorkset: (tabId) => set((s) => {
    if (!s.openTabIds.has(tabId)) return s
    const nextIds = new Set(s.openTabIds); nextIds.delete(tabId)
    const nextOrder = s.openTabIdsOrdered.filter((id) => id !== tabId)
    const wasActive = s.activeWorkspaceTabId === tabId
    const nextActive = wasActive ? (nextOrder[nextOrder.length - 1] ?? null) : s.activeWorkspaceTabId
    return { openTabIds: nextIds, openTabIdsOrdered: nextOrder, activeWorkspaceTabId: nextActive }
  }),

  trashTab: async (tabId) => {
    useStageStore.getState().detachFromWorkset(tabId)
    const { coordinator } = await import('@/features/stage/persistence/stage-persistence-bootstrap')
    await coordinator.delete(tabId)
    set((s) => {
      const wsRemoved = s.workspaceTabs.filter((t) => t.tabId !== tabId)
      if (wsRemoved.length !== s.workspaceTabs.length) return { workspaceTabs: wsRemoved }
      for (const [sid, list] of s.tabsBySession.entries()) {
        if (list.some((t) => t.tabId === tabId)) {
          const next = list.filter((t) => t.tabId !== tabId)
          const map = new Map(s.tabsBySession); map.set(sid, next)
          return { tabsBySession: map }
        }
      }
      return s
    })
  },

  setLeftRailWidth: (px) => set(() => {
    try { localStorage.setItem(LEFT_RAIL_WIDTH_KEY, String(px)) } catch {}
    return { leftRailWidth: Math.min(320, Math.max(180, px)) }
  }),

  toggleLeftRailCollapsed: () => set((s) => {
    const next = !s.leftRailCollapsed
    try { localStorage.setItem(LEFT_RAIL_COLLAPSED_KEY, String(next)) } catch {}
    return { leftRailCollapsed: next }
  }),

  listTabs: (sid) => {
    const s = get()
    const sessionTabs = sid ? (s.tabsBySession.get(sid) ?? []) : []
    return [...s.workspaceTabs, ...sessionTabs]
  },

  updateTabPayload: (tabId, updater) => set((s) => {
    const wsIdx = s.workspaceTabs.findIndex((t) => t.tabId === tabId)
    if (wsIdx >= 0) {
      const next = [...s.workspaceTabs]
      next[wsIdx] = { ...next[wsIdx], payload: updater(next[wsIdx].payload) }
      return { workspaceTabs: next }
    }
    for (const [sid, arr] of s.tabsBySession.entries()) {
      const i = arr.findIndex((t) => t.tabId === tabId)
      if (i < 0) continue
      const nextArr = [...arr]; nextArr[i] = { ...nextArr[i], payload: updater(nextArr[i].payload) }
      const map = new Map(s.tabsBySession); map.set(sid, nextArr)
      return { tabsBySession: map }
    }
    return s
  }),

  openArtifactPreviewTab: (sessionId, artifactId, title) => {
    const existing = get().tabsBySession.get(sessionId)?.find(
      (t) => t.type === 'artifact_preview' && (t.payload as { artifactId?: string })?.artifactId === artifactId,
    )
    if (existing) {
      get().openStage(sessionId)
      get().focusTab(existing.tabId)
      return
    }
    const tabId = `artifact_preview_${generateUuid()}`
    const tab: StageTab = {
      tabId,
      type: 'artifact_preview',
      title,
      originSessionId: sessionId,
      scope: 'session',
      payload: { artifactId, sessionId },
      createdAt: Date.now(),
    }
    get().openStage(sessionId)
    get().openTab(tab)
  },

  openQueryEditor: (input) => {
    if (input.scope === 'session' && !input.sessionId) {
      throw new Error('session-scoped query editor requires sessionId')
    }

    const latest = get()
    const sessionTabs = input.sessionId ? (latest.tabsBySession.get(input.sessionId) ?? []) : []
    if (input.scope === 'session' && input.openMode === 'reuse_by_resource_context') {
      const existing = sessionTabs.find((tab) =>
        tab.type === 'query_editor' &&
        matchesNullable(tab.connectionId, input.connectionId) &&
        matchesNullable(tab.database, input.database) &&
        matchesNullable(tab.schema, input.schema)
      )
      if (existing) {
        latest.focusTab(existing.tabId)
        return { tabId: existing.tabId, created: false }
      }
    }

    const visibleTitles = [
      ...latest.workspaceTabs,
      ...sessionTabs,
    ]
      .filter((tab) => tab.type === 'query_editor')
      .map((tab) => tab.title)
    const tabId = `query_editor_${generateUuid()}`
    const payload = buildQueryEditorPayload(input)
    const tab: StageTab = {
      tabId,
      type: 'query_editor',
      title: resolveUniqueTabTitle(input.baseTitle, visibleTitles),
      connectionId: input.connectionId ?? undefined,
      connectionName: input.connectionName ?? undefined,
      database: input.database ?? undefined,
      schema: input.schema ?? undefined,
      originSessionId: input.sessionId ?? undefined,
      scope: input.scope,
      payload,
      createdAt: Date.now(),
    }

    latest.openTab(tab)
    if (input.scope === 'workspace' && input.sessionId) {
      set((state) => {
        const activeTabIdBySession = new Map(state.activeTabIdBySession)
        activeTabIdBySession.set(input.sessionId!, null)
        return { activeTabIdBySession }
      })
    }
    useSqlWorkbenchStore.getState().ensureTab(tabId, {
      sqlText: input.initialContent ?? '',
      source: payload.source,
    })
    return { tabId, created: true }
  },

  setQueryEditorContext: (tabId, context) => set((state) => {
    const workspaceTabs = updateTabMeta(state.workspaceTabs, tabId, (tab) => ({
      ...tab,
      connectionId: context.connectionId ?? undefined,
      connectionName: context.connectionName ?? undefined,
      database: context.database ?? undefined,
      schema: context.schema ?? undefined,
      payload: updateQueryEditorPayload(tab.payload, {
        connectionId: context.connectionId ?? null,
        connectionName: context.connectionName ?? null,
        database: context.database ?? null,
        schema: context.schema ?? null,
      }),
    }))
    if (workspaceTabs) {
      return { workspaceTabs }
    }

    for (const [sessionId, tabs] of state.tabsBySession.entries()) {
      const nextTabs = updateTabMeta(tabs, tabId, (tab) => ({
        ...tab,
        connectionId: context.connectionId ?? undefined,
        connectionName: context.connectionName ?? undefined,
        database: context.database ?? undefined,
        schema: context.schema ?? undefined,
        payload: updateQueryEditorPayload(tab.payload, {
          connectionId: context.connectionId ?? null,
          connectionName: context.connectionName ?? null,
          database: context.database ?? null,
          schema: context.schema ?? null,
        }),
      }))
      if (!nextTabs) continue
      const tabsBySession = new Map(state.tabsBySession)
      tabsBySession.set(sessionId, nextTabs)
      return { tabsBySession }
    }

    return state
  }),

  replaceQueryEditorContent: (tabId, content, baseVersion) => {
    return useSqlWorkbenchStore.getState().replaceSqlText(tabId, content, baseVersion)
  },

  applyQueryEditorTextEdits: (tabId, params) => {
    return useSqlWorkbenchStore.getState().applyTextEdits(tabId, params)
  },

  setQueryEditorCursor: (tabId, cursor) => {
    useSqlWorkbenchStore.getState().setCursor(tabId, cursor.line, cursor.column)
  },

  findTab: (tabId) => {
    const s = get()
    const ws = s.workspaceTabs.find((t) => t.tabId === tabId)
    if (ws) return ws
    for (const [, arr] of s.tabsBySession.entries()) {
      const found = arr.find((t) => t.tabId === tabId)
      if (found) return found
    }
    return null
  },

  __hydrateAll: (items) => set((s) => ({
    workspaceTabs: mergeHydratedWorkspaceTabs(s.workspaceTabs, items),
  })),

  __hydrateWorkspaceTabs: (items) => set((s) => ({
    workspaceTabs: mergeHydratedWorkspaceTabs(s.workspaceTabs, items),
  })),

  __hydrateSessionTabs: (_sessionId, items) => set((s) => ({
    workspaceTabs: mergeHydratedWorkspaceTabs(s.workspaceTabs, items),
  })),

  __hydratePayload: (tabId, payload, version) => set((s) => {
    const wsIdx = s.workspaceTabs.findIndex((t) => t.tabId === tabId)
    if (wsIdx >= 0) {
      const next = [...s.workspaceTabs]
      next[wsIdx] = { ...next[wsIdx], payload, payloadVersion: version }
      return { workspaceTabs: next }
    }
    for (const [sid, arr] of s.tabsBySession.entries()) {
      const i = arr.findIndex((t) => t.tabId === tabId)
      if (i < 0) continue
      const nextArr = [...arr]
      nextArr[i] = { ...nextArr[i], payload, payloadVersion: version }
      const map = new Map(s.tabsBySession)
      map.set(sid, nextArr)
      return { tabsBySession: map }
    }
    return s
  }),

  __setPayloadVersion: (tabId, version) => set((s) => {
    const wsIdx = s.workspaceTabs.findIndex((t) => t.tabId === tabId)
    if (wsIdx >= 0) {
      const next = [...s.workspaceTabs]
      next[wsIdx] = { ...next[wsIdx], payloadVersion: version }
      return { workspaceTabs: next }
    }
    for (const [sid, arr] of s.tabsBySession.entries()) {
      const i = arr.findIndex((t) => t.tabId === tabId)
      if (i < 0) continue
      const nextArr = [...arr]
      nextArr[i] = { ...nextArr[i], payloadVersion: version }
      const map = new Map(s.tabsBySession)
      map.set(sid, nextArr)
      return { tabsBySession: map }
    }
    return s
  }),

  archiveTab: (id, archived) => set((s) => {
    const updateInList = (list: StageTab[]): StageTab[] | null => {
      const idx = list.findIndex((t) => t.tabId === id)
      if (idx < 0) return null
      const next = [...list]
      next[idx] = { ...next[idx], archived }
      return next
    }
    const ws = updateInList(s.workspaceTabs)
    if (ws) {
      const detached = archived ? archiveDetach(s, id) : { openTabIds: s.openTabIds, openTabIdsOrdered: s.openTabIdsOrdered, activeWorkspaceTabId: s.activeWorkspaceTabId }
      return { workspaceTabs: ws, ...detached }
    }
    for (const [sid, list] of s.tabsBySession.entries()) {
      const updated = updateInList(list)
      if (updated) {
        const map = new Map(s.tabsBySession); map.set(sid, updated)
        const detached = archived ? archiveDetach(s, id) : {}
        return { tabsBySession: map, ...detached }
      }
    }
    return s
  }),

  setTabPinned: (id, pinned) => set((s) => {
    const wsIdx = s.workspaceTabs.findIndex((t) => t.tabId === id)
    if (wsIdx >= 0) {
      const next = [...s.workspaceTabs]
      next[wsIdx] = { ...next[wsIdx], pinned }
      return { workspaceTabs: next }
    }
    for (const [sid, arr] of s.tabsBySession.entries()) {
      const i = arr.findIndex((t) => t.tabId === id)
      if (i < 0) continue
      const nextArr = [...arr]
      nextArr[i] = { ...nextArr[i], pinned }
      const map = new Map(s.tabsBySession)
      map.set(sid, nextArr)
      return { tabsBySession: map }
    }
    return s
  }),

  setTabTitle: (id, title) => set((s) => {
    const wsIdx = s.workspaceTabs.findIndex((t) => t.tabId === id)
    if (wsIdx >= 0) {
      const next = [...s.workspaceTabs]
      next[wsIdx] = { ...next[wsIdx], title }
      return { workspaceTabs: next }
    }
    for (const [sid, arr] of s.tabsBySession.entries()) {
      const i = arr.findIndex((t) => t.tabId === id)
      if (i < 0) continue
      const nextArr = [...arr]
      nextArr[i] = { ...nextArr[i], title }
      const map = new Map(s.tabsBySession)
      map.set(sid, nextArr)
      return { tabsBySession: map }
    }
    return s
  }),
}))
