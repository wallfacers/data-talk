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

export type RailPanel = 'schema' | 'history' | 'outline'

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
  payload: unknown
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

  // Tab CRUD（新）
  openTab: (tab: StageTab) => void
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
  replaceQueryEditorContent: (tabId: string, content: string) => { version: number }
  applyQueryEditorTextEdits: (
    tabId: string,
    params: { baseVersion: number; edits: QueryEditorTextEdit[] },
  ) => QueryEditorEditResult
  setQueryEditorCursor: (tabId: string, cursor: { line: number; column: number }) => void
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

  closeTab: (tabId) => set((s) => {
    const wsIdx = s.workspaceTabs.findIndex((t) => t.tabId === tabId)
    if (wsIdx >= 0) {
      const next = [...s.workspaceTabs]; next.splice(wsIdx, 1)
      const newActive = s.activeWorkspaceTabId === tabId ? (next.length ? next[next.length - 1].tabId : null) : s.activeWorkspaceTabId
      return { workspaceTabs: next, activeWorkspaceTabId: newActive }
    }
    // 按 session 搜索
    for (const [sid, arr] of s.tabsBySession.entries()) {
      const i = arr.findIndex((t) => t.tabId === tabId)
      if (i < 0) continue
      const nextArr = [...arr]; nextArr.splice(i, 1)
      const nextMap = new Map(s.tabsBySession); nextMap.set(sid, nextArr)
      const activeMap = new Map(s.activeTabIdBySession)
      if (activeMap.get(sid) === tabId) activeMap.set(sid, nextArr.length ? nextArr[nextArr.length - 1].tabId : null)
      return { tabsBySession: nextMap, activeTabIdBySession: activeMap }
    }
    return s
  }),

  focusTab: (tabId) => set((s) => {
    if (s.workspaceTabs.some((t) => t.tabId === tabId)) {
      return { activeWorkspaceTabId: tabId }
    }
    for (const [sid, arr] of s.tabsBySession.entries()) {
      if (arr.some((t) => t.tabId === tabId)) {
        const map = new Map(s.activeTabIdBySession); map.set(sid, tabId)
        return { activeTabIdBySession: map }
      }
    }
    return s
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

  replaceQueryEditorContent: (tabId, content) => {
    return useSqlWorkbenchStore.getState().replaceSqlText(tabId, content)
  },

  applyQueryEditorTextEdits: (tabId, params) => {
    return useSqlWorkbenchStore.getState().applyTextEdits(tabId, params)
  },

  setQueryEditorCursor: (tabId, cursor) => {
    useSqlWorkbenchStore.getState().setCursor(tabId, cursor.line, cursor.column)
  },
}))
