import { create } from 'zustand'

type RevealOrigin = { x: number; y: number }

export interface StageTab {
  tabId: string
  type: string
  title: string
  connectionId?: string
  database?: string
  schema?: string
  originSessionId?: string
  scope: 'session' | 'workspace'
  pinned?: boolean
  payload: unknown
  createdAt: number
}

type StageState = {
  openBySession: Map<string, boolean>
  autoOpenedSessions: Set<string>
  maximizedBySession: Map<string, boolean>
  revealOrigin: RevealOrigin | null

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
  clear: (sessionId: string) => void

  // Tab CRUD（新）
  openTab: (tab: StageTab) => void
  closeTab: (tabId: string) => void
  focusTab: (tabId: string) => void
  listTabs: (sessionId: string | null) => StageTab[]
  updateTabPayload: (tabId: string, updater: (prev: unknown) => unknown) => void
}

export const useStageStore = create<StageState>((set, get) => ({
  openBySession: new Map(),
  autoOpenedSessions: new Set(),
  maximizedBySession: new Map(),
  revealOrigin: null,

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
  clear: (sid) => set((s) => {
    const openMap = new Map(s.openBySession); openMap.delete(sid)
    const a = new Set(s.autoOpenedSessions); a.delete(sid)
    const maxMap = new Map(s.maximizedBySession); maxMap.delete(sid)
    const ts = new Map(s.tabsBySession); ts.delete(sid)
    const ats = new Map(s.activeTabIdBySession); ats.delete(sid)
    return { openBySession: openMap, autoOpenedSessions: a, maximizedBySession: maxMap, tabsBySession: ts, activeTabIdBySession: ats }
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
}))
