import { create } from 'zustand'

type RevealOrigin = { x: number; y: number }

type StageState = {
  openBySession: Map<string, boolean>
  autoOpenedSessions: Set<string>
  maximizedBySession: Map<string, boolean>
  revealOrigin: RevealOrigin | null

  openStage: (sessionId: string) => void
  closeStage: (sessionId: string) => void
  toggleStage: (sessionId: string) => void
  toggleMaximized: (sessionId: string) => void
  setRevealOrigin: (origin: RevealOrigin | null) => void
  notifyArtifactArrived: (sessionId: string) => void
  syncCollapsed: (sessionId: string, collapsed: boolean) => void
  clear: (sessionId: string) => void
}

export const useStageStore = create<StageState>((set, get) => ({
  openBySession: new Map(),
  autoOpenedSessions: new Set(),
  maximizedBySession: new Map(),
  revealOrigin: null,

  openStage: (sid) => set((s) => {
    const m = new Map(s.openBySession); m.set(sid, true)
    return { openBySession: m }
  }),

  closeStage: (sid) => set((s) => {
    const m = new Map(s.openBySession); m.set(sid, false)
    const a = new Set(s.autoOpenedSessions); a.add(sid)
    return { openBySession: m, autoOpenedSessions: a }
  }),

  toggleStage: (sid) => {
    const cur = !!get().openBySession.get(sid)
    if (cur) get().closeStage(sid)
    else get().openStage(sid)
  },

  toggleMaximized: (sid) => set((s) => {
    const m = new Map(s.maximizedBySession)
    m.set(sid, !m.get(sid))
    return { maximizedBySession: m }
  }),

  setRevealOrigin: (origin) => set({ revealOrigin: origin }),

  notifyArtifactArrived: (sid) => set((s) => {
    if (s.autoOpenedSessions.has(sid)) return s
    if (s.openBySession.get(sid)) return s
    const m = new Map(s.openBySession); m.set(sid, true)
    const a = new Set(s.autoOpenedSessions); a.add(sid)
    return { openBySession: m, autoOpenedSessions: a }
  }),

  syncCollapsed: (sid, collapsed) => set((s) => {
    const cur = s.openBySession.get(sid)
    const next = !collapsed
    if (cur === next) return s
    const m = new Map(s.openBySession); m.set(sid, next)
    if (collapsed) {
      const a = new Set(s.autoOpenedSessions); a.add(sid)
      return { openBySession: m, autoOpenedSessions: a }
    }
    return { openBySession: m }
  }),

  clear: (sid) => set((s) => {
    const openMap = new Map(s.openBySession); openMap.delete(sid)
    const a = new Set(s.autoOpenedSessions); a.delete(sid)
    const maxMap = new Map(s.maximizedBySession); maxMap.delete(sid)
    return { openBySession: openMap, autoOpenedSessions: a, maximizedBySession: maxMap }
  }),
}))
