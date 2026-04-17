import { create } from 'zustand'

export type SessionMode = 'NOSESS' | 'HERO' | 'SPLIT'

type SessionState = {
  activeSessionId: string | null
  modeBySession: Map<string, SessionMode>
  hasEverSentBySession: Map<string, boolean>
  pendingPrompt: string | null
  pendingModelPrompt: boolean

  openSession: (id: string, hasEverSent: boolean) => void
  closeSession: () => void
  enterSplit: (id: string) => void
  setPendingPrompt: (text: string | null) => void
  setPendingModelPrompt: (on: boolean) => void
}

export const useSessionStore = create<SessionState>((set) => ({
  activeSessionId: null,
  modeBySession: new Map(),
  hasEverSentBySession: new Map(),
  pendingPrompt: null,
  pendingModelPrompt: false,

  openSession: (id, hasEverSent) => set((s) => {
    // Cache wins: once we've observed hasEverSent=true locally, never demote.
    const cachedSent = s.hasEverSentBySession.get(id) ?? false
    const effectiveSent = cachedSent || hasEverSent
    const mode: SessionMode = effectiveSent ? 'SPLIT' : 'HERO'
    const modes = new Map(s.modeBySession); modes.set(id, mode)
    const sent = new Map(s.hasEverSentBySession); sent.set(id, effectiveSent)
    return { activeSessionId: id, modeBySession: modes, hasEverSentBySession: sent }
  }),

  closeSession: () => set({ activeSessionId: null }),

  enterSplit: (id) => set((s) => {
    const modes = new Map(s.modeBySession); modes.set(id, 'SPLIT')
    const sent = new Map(s.hasEverSentBySession); sent.set(id, true)
    return { modeBySession: modes, hasEverSentBySession: sent }
  }),

  setPendingPrompt: (text) => set({ pendingPrompt: text }),
  setPendingModelPrompt: (on) => set({ pendingModelPrompt: on }),
}))
