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
  archivedAt?: number | null
  payload: unknown
  payloadVersion?: number
  lastTouchedAt?: number
  createdAt: number
}

export type StageState = {
  // Global stage view state
  open: boolean
  maximized: boolean
  autoOpened: boolean
  sidebarCollapsed: boolean
  sidebarSelection: SidebarSelection | null
  resourceTreeExpanded: string[]
  activeRailPanel: RailPanel | null
  revealOrigin: RevealOrigin | null

  // Tabs (single list)
  tabs: StageTab[]
  openTabIds: Set<string>
  openTabIdsOrdered: string[]
  activeTabId: string | null

  // Left rail UI prefs
  leftRailWidth: number
  leftRailCollapsed: boolean

  // Actions (no sid params)
  openStage: () => void
  closeStage: () => void
  toggleStage: () => void
  toggleMaximized: () => void
  setRevealOrigin: (origin: RevealOrigin | null) => void
  notifyArtifactArrived: () => void
  syncCollapsed: (collapsed: boolean) => void
  toggleSidebarCollapsed: () => void
  setSidebarSelection: (selection: SidebarSelection | null) => void
  toggleResourceExpanded: (nodeId: string) => void
  setResourceExpanded: (ids: string[]) => void
  setActiveRailPanel: (panel: RailPanel | null) => void
  toggleRailPanel: (panel: RailPanel) => void

  // Tab CRUD
  ensureOpenInWorkset: (tabId: string) => void
  detachFromWorkset: (tabId: string) => void
  trashTab: (tabId: string) => Promise<void>
  setLeftRailWidth: (px: number) => void
  toggleLeftRailCollapsed: () => void

  openTab: (tab: StageTab) => void
  focusTab: (tabId: string) => void
  listTabs: () => StageTab[]
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

// On hydrate, seed the workset with non-archived hydrated tabs so the top tab
// bar is not empty after a cold restart. Existing workset entries are preserved
// in their current order; new tabs append to the tail.
function seedWorkset(
  currentIds: Set<string>,
  currentOrder: string[],
  hydratedTabs: StageTab[],
): { openTabIds: Set<string>; openTabIdsOrdered: string[] } {
  const nextIds = new Set(currentIds)
  const nextOrder = [...currentOrder]
  for (const tab of hydratedTabs) {
    if (tab.archived) continue
    if (nextIds.has(tab.tabId)) continue
    nextIds.add(tab.tabId)
    nextOrder.push(tab.tabId)
  }
  return { openTabIds: nextIds, openTabIdsOrdered: nextOrder }
}

export const useStageStore = create<StageState>((set, get) => ({
  open: false,
  maximized: false,
  autoOpened: false,
  sidebarCollapsed: false,
  sidebarSelection: null,
  resourceTreeExpanded: [],
  activeRailPanel: null,
  revealOrigin: null,

  tabs: [],
  openTabIds: new Set<string>(),
  openTabIdsOrdered: [],
  activeTabId: null,

  leftRailWidth: loadLeftRailWidth(),
  leftRailCollapsed: loadLeftRailCollapsed(),

  openStage: () => set({ open: true }),
  closeStage: () => set({ open: false, autoOpened: false }),
  toggleStage: () => set((s) => ({ open: !s.open, ...(s.open ? { autoOpened: false } : {}) })),
  toggleMaximized: () => set((s) => ({ maximized: !s.maximized })),
  setRevealOrigin: (origin) => set({ revealOrigin: origin }),

  notifyArtifactArrived: () => set((s) => {
    if (s.autoOpened) return s
    if (s.open) return s
    return { open: true, autoOpened: true }
  }),

  syncCollapsed: (collapsed) => set((s) => {
    const nextOpen = !collapsed
    if (s.open === nextOpen) return s
    return collapsed
      ? { open: false, autoOpened: false }
      : { open: true }
  }),

  toggleSidebarCollapsed: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),
  setSidebarSelection: (selection) => set({ sidebarSelection: selection }),

  toggleResourceExpanded: (nodeId) => set((s) => {
    const next = s.resourceTreeExpanded.includes(nodeId)
      ? s.resourceTreeExpanded.filter((id) => id !== nodeId)
      : [...s.resourceTreeExpanded, nodeId]
    return { resourceTreeExpanded: next }
  }),
  setResourceExpanded: (ids) => set({ resourceTreeExpanded: [...ids] }),

  setActiveRailPanel: (panel) => set({ activeRailPanel: panel }),
  toggleRailPanel: (panel) => set((s) => ({
    activeRailPanel: s.activeRailPanel === panel ? null : panel,
  })),

  // Tab CRUD
  openTab: (tab) => set((s) => ({
    tabs: [...s.tabs, tab],
    openTabIds: new Set([...s.openTabIds, tab.tabId]),
    openTabIdsOrdered: [...s.openTabIdsOrdered, tab.tabId],
    activeTabId: tab.tabId,
  })),

  ensureOpenInWorkset: (tabId) => set((s) => {
    if (s.openTabIds.has(tabId)) return s
    const target = s.tabs.find((t) => t.tabId === tabId)
    if (!target || target.archived) return s
    const next = new Set(s.openTabIds); next.add(tabId)
    return { openTabIds: next, openTabIdsOrdered: [...s.openTabIdsOrdered, tabId] }
  }),

  detachFromWorkset: (tabId) => set((s) => {
    if (!s.openTabIds.has(tabId)) return s
    const nextIds = new Set(s.openTabIds); nextIds.delete(tabId)
    const nextOrder = s.openTabIdsOrdered.filter((id) => id !== tabId)
    const nextActive = s.activeTabId === tabId ? (nextOrder[nextOrder.length - 1] ?? null) : s.activeTabId
    return { openTabIds: nextIds, openTabIdsOrdered: nextOrder, activeTabId: nextActive }
  }),

  archiveTab: (tabId, archived) => set((s) => {
    const idx = s.tabs.findIndex((t) => t.tabId === tabId)
    if (idx < 0) return s
    const updated = [...s.tabs]
    updated[idx] = { ...updated[idx], archived, archivedAt: archived ? Date.now() : null }
    if (!archived) return { tabs: updated }
    const nextIds = new Set(s.openTabIds); nextIds.delete(tabId)
    const nextOrder = s.openTabIdsOrdered.filter((id) => id !== tabId)
    const nextActive = s.activeTabId === tabId ? (nextOrder[nextOrder.length - 1] ?? null) : s.activeTabId
    return { tabs: updated, openTabIds: nextIds, openTabIdsOrdered: nextOrder, activeTabId: nextActive }
  }),

  trashTab: async (tabId) => {
    const before = get()
    const prevOpenIds = before.openTabIds
    const prevOpenOrder = before.openTabIdsOrdered
    const prevActive = before.activeTabId
    useStageStore.getState().detachFromWorkset(tabId)
    try {
      const { coordinator } = await import('@/features/stage/persistence/stage-persistence-bootstrap')
      await coordinator.delete(tabId)
    } catch (err) {
      set(() => ({
        openTabIds: prevOpenIds,
        openTabIdsOrdered: prevOpenOrder,
        activeTabId: prevActive,
      }))
      throw err
    }
    set((s) => ({ tabs: s.tabs.filter((t) => t.tabId !== tabId) }))
  },

  focusTab: (tabId) => set((s) => {
    const target = s.tabs.find((t) => t.tabId === tabId)
    if (!target) return s
    if (target.archived) return s
    const inWorkset = s.openTabIds.has(tabId)
    if (inWorkset) return { activeTabId: tabId }
    const nextIds = new Set(s.openTabIds); nextIds.add(tabId)
    return {
      openTabIds: nextIds,
      openTabIdsOrdered: [...s.openTabIdsOrdered, tabId],
      activeTabId: tabId,
    }
  }),

  setLeftRailWidth: (px) => {
    try { localStorage.setItem(LEFT_RAIL_WIDTH_KEY, String(px)) } catch {}
    set({ leftRailWidth: Math.min(320, Math.max(180, px)) })
  },
  toggleLeftRailCollapsed: () => set((s) => {
    const next = !s.leftRailCollapsed
    try { localStorage.setItem(LEFT_RAIL_COLLAPSED_KEY, String(next)) } catch {}
    return { leftRailCollapsed: next }
  }),

  listTabs: () => get().tabs,

  updateTabPayload: (tabId, updater) => set((s) => {
    const idx = s.tabs.findIndex((t) => t.tabId === tabId)
    if (idx < 0) return s
    const next = [...s.tabs]
    next[idx] = { ...next[idx], payload: updater(next[idx].payload) }
    return { tabs: next }
  }),

  openArtifactPreviewTab: (sessionId, artifactId, title) => {
    const existing = get().tabs.find(
      (t) => t.type === 'artifact_preview' && (t.payload as { artifactId?: string })?.artifactId === artifactId,
    )
    if (existing) {
      get().openStage()
      get().focusTab(existing.tabId)
      return
    }
    const tabId = `artifact_preview_${generateUuid()}`
    const tab: StageTab = {
      tabId, type: 'artifact_preview', title,
      originSessionId: sessionId,
      scope: 'session',
      payload: { artifactId, sessionId },
      createdAt: Date.now(),
    }
    get().openStage()
    get().openTab(tab)
  },

  openQueryEditor: (input) => {
    const latest = get()
    if (input.openMode === 'reuse_by_resource_context') {
      const existing = latest.tabs.find((t) =>
        t.type === 'query_editor' &&
        matchesNullable(t.connectionId, input.connectionId) &&
        matchesNullable(t.database, input.database) &&
        matchesNullable(t.schema, input.schema)
      )
      if (existing) {
        latest.focusTab(existing.tabId)
        return { tabId: existing.tabId, created: false }
      }
    }
    const visibleTitles = latest.tabs
      .filter((t) => t.type === 'query_editor')
      .map((t) => t.title)
    const tabId = `query_editor_${generateUuid()}`
    const payload = buildQueryEditorPayload(input)
    const tab: StageTab = {
      tabId, type: 'query_editor',
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
    useSqlWorkbenchStore.getState().ensureTab(tabId, {
      sqlText: input.initialContent ?? '',
      source: payload.source,
    })
    return { tabId, created: true }
  },

  setQueryEditorContext: (tabId, context) => set((s) => {
    const idx = s.tabs.findIndex((t) => t.tabId === tabId)
    if (idx < 0) return s
    const next = [...s.tabs]
    next[idx] = {
      ...next[idx],
      connectionId: context.connectionId ?? undefined,
      connectionName: context.connectionName ?? undefined,
      database: context.database ?? undefined,
      schema: context.schema ?? undefined,
      payload: updateQueryEditorPayload(next[idx].payload, {
        connectionId: context.connectionId ?? null,
        connectionName: context.connectionName ?? null,
        database: context.database ?? null,
        schema: context.schema ?? null,
      }),
    }
    return { tabs: next }
  }),

  replaceQueryEditorContent: (tabId, content, baseVersion) =>
    useSqlWorkbenchStore.getState().replaceSqlText(tabId, content, baseVersion),

  applyQueryEditorTextEdits: (tabId, params) =>
    useSqlWorkbenchStore.getState().applyTextEdits(tabId, params),

  setQueryEditorCursor: (tabId, cursor) =>
    useSqlWorkbenchStore.getState().setCursor(tabId, cursor.line, cursor.column),

  findTab: (tabId) => get().tabs.find((t) => t.tabId === tabId) ?? null,

  __hydrateAll: (items) => set((s) => {
    const incoming = new Map(items.map((t) => [t.tabId, t]))
    const merged = s.tabs.map((existing) => {
      const hydrated = incoming.get(existing.tabId)
      if (!hydrated) return existing
      return { ...existing, ...hydrated }
    })
    for (const item of items) {
      if (!merged.some((t) => t.tabId === item.tabId)) merged.push(item)
    }
    return { tabs: merged, ...seedWorkset(s.openTabIds, s.openTabIdsOrdered, items) }
  }),

  __hydratePayload: (tabId, payload, version) => set((s) => {
    const idx = s.tabs.findIndex((t) => t.tabId === tabId)
    if (idx < 0) return s
    const next = [...s.tabs]
    next[idx] = { ...next[idx], payload, payloadVersion: version }
    return { tabs: next }
  }),

  __setPayloadVersion: (tabId, version) => set((s) => {
    const idx = s.tabs.findIndex((t) => t.tabId === tabId)
    if (idx < 0) return s
    const next = [...s.tabs]
    next[idx] = { ...next[idx], payloadVersion: version }
    return { tabs: next }
  }),

  setTabPinned: (id, pinned) => set((s) => {
    const idx = s.tabs.findIndex((t) => t.tabId === id)
    if (idx < 0) return s
    const next = [...s.tabs]
    next[idx] = { ...next[idx], pinned }
    return { tabs: next }
  }),

  setTabTitle: (id, title) => set((s) => {
    const idx = s.tabs.findIndex((t) => t.tabId === id)
    if (idx < 0) return s
    const next = [...s.tabs]
    next[idx] = { ...next[idx], title }
    return { tabs: next }
  }),
}))
