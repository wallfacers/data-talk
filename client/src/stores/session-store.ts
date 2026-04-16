import { create } from 'zustand'

export type SessionMode = 'NOSESS' | 'HERO' | 'SPLIT'

type SessionState = {
  activeSessionId: string | null
  modeBySession: Map<string, SessionMode>
  hasEverSentBySession: Map<string, boolean>
  pendingPrompt: string | null
  pendingConnectionPrompt: boolean

  setActive: (id: string | null) => void
  enterSplit: (id: string) => void
  seedFromServer: (id: string, hasEverSent: boolean) => void
  setPendingPrompt: (text: string | null) => void
  setPendingConnectionPrompt: (on: boolean) => void
}

export const useSessionStore = create<SessionState>((set) => ({
  activeSessionId: null,
  modeBySession: new Map(),
  hasEverSentBySession: new Map(),
  pendingPrompt: null,
  pendingConnectionPrompt: false,

  setActive: (id) => set(s => {
    if (!id) return { activeSessionId: null }
    const mode = s.modeBySession.get(id)
        ?? (s.hasEverSentBySession.get(id) ? 'SPLIT' : 'HERO')
    const next = new Map(s.modeBySession); next.set(id, mode)
    return { activeSessionId: id, modeBySession: next }
  }),

  enterSplit: (id) => set(s => {
    const next = new Map(s.modeBySession); next.set(id, 'SPLIT')
    const sent = new Map(s.hasEverSentBySession); sent.set(id, true)
    return { modeBySession: next, hasEverSentBySession: sent }
  }),

  seedFromServer: (id, hasEverSent) => set(s => {
    const mode = hasEverSent ? 'SPLIT' : 'HERO'
    const next = new Map(s.modeBySession); next.set(id, mode)
    const sent = new Map(s.hasEverSentBySession); sent.set(id, hasEverSent)
    return { modeBySession: next, hasEverSentBySession: sent }
  }),

  setPendingPrompt: (text) => set({ pendingPrompt: text }),
  setPendingConnectionPrompt: (on) => set({ pendingConnectionPrompt: on }),
}))
