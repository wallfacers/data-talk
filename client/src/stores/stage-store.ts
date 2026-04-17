import { create } from 'zustand'

type StageState = {
  openBySession: Map<string, boolean>
  autoOpenedSessions: Set<string>
  // TODO(test): 无 session 时的全局预览开关，正式 session 流程接通后可移除
  globalOpen: boolean
  maximized: boolean
  demoMessages: { role: 'user' | 'assistant'; text: string }[]

  openStage: (sessionId: string) => void
  closeStage: (sessionId: string) => void
  toggleStage: (sessionId: string) => void
  toggleGlobal: () => void
  toggleMaximized: () => void
  addDemoMessage: (role: 'user' | 'assistant', text: string) => void
  notifyArtifactArrived: (sessionId: string) => void
  syncCollapsed: (sessionId: string, collapsed: boolean) => void
  clear: (sessionId: string) => void
}

export const useStageStore = create<StageState>((set, get) => ({
  openBySession: new Map(),
  autoOpenedSessions: new Set(),
  globalOpen: false,
  maximized: false,
  demoMessages: [],

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

  toggleGlobal: () => set((s) => ({ globalOpen: !s.globalOpen })),
  toggleMaximized: () => set((s) => ({ maximized: !s.maximized })),

  addDemoMessage: (role, text) => set((s) => ({
    demoMessages: [...s.demoMessages, { role, text }],
  })),

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
