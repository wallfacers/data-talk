import { create } from 'zustand'

type StageState = {
  openBySession: Map<string, boolean>
  autoOpenedSessions: Set<string>

  openStage: (sessionId: string) => void
  closeStage: (sessionId: string) => void
  toggleStage: (sessionId: string) => void
  notifyArtifactArrived: (sessionId: string) => void
  syncCollapsed: (sessionId: string, collapsed: boolean) => void
  clear: (sessionId: string) => void
}

export const useStageStore = create<StageState>((set, get) => ({
  openBySession: new Map(),
  autoOpenedSessions: new Set(),

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
    const m = new Map(s.openBySession); m.delete(sid)
    const a = new Set(s.autoOpenedSessions); a.delete(sid)
    return { openBySession: m, autoOpenedSessions: a }
  }),
}))
