import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import type { SessionDataContext } from '@/services/api/session-data-context'

export type SessionMode = 'NOSESS' | 'HERO' | 'SPLIT'

function sameSessionDataContext(a: SessionDataContext | undefined, b: SessionDataContext) {
  return !!a
    && a.sessionId === b.sessionId
    && a.connectionId === b.connectionId
    && a.connectionNameSnapshot === b.connectionNameSnapshot
    && a.database === b.database
    && a.schema === b.schema
    && a.selectedLevel === b.selectedLevel
    && a.updatedAt === b.updatedAt
}

type SessionState = {
  activeSessionId: string | null
  modeBySession: Map<string, SessionMode>
  hasEverSentBySession: Map<string, boolean>
  dataContextBySession: Map<string, SessionDataContext>
  pendingPrompt: string | null
  composerRestoreDraft: { sessionId: string; text: string } | null
  composerDrafts: Record<string, string>
  pendingModelPrompt: boolean
  pendingConnectionPrompt: boolean
  pendingActionAfterConnectionPick: { kind: 'send' } | null
  composerInsertText: { sessionId: string; text: string } | null

  openSession: (id: string, hasEverSent: boolean) => void
  closeSession: () => void
  enterSplit: (id: string) => void
  setSessionMode: (id: string, mode: SessionMode) => void
  markSessionSent: (id: string) => void
  setSessionDataContext: (context: SessionDataContext) => void
  clearSessionDataContext: (sessionId: string) => void
  setPendingPrompt: (text: string | null) => void
  setComposerRestoreDraft: (draft: { sessionId: string; text: string } | null) => void
  setComposerDraft: (key: string, text: string) => void
  setPendingModelPrompt: (on: boolean) => void
  setPendingConnectionPrompt: (on: boolean) => void
  setPendingActionAfterConnectionPick: (action: { kind: 'send' } | null) => void
  setComposerInsertText: (draft: { sessionId: string; text: string } | null) => void
}

export const useSessionStore = create<SessionState>()(
  persist(
    (set) => ({
      activeSessionId: null,
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      dataContextBySession: new Map(),
      pendingPrompt: null,
      composerRestoreDraft: null,
      composerDrafts: {},
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
      composerInsertText: null,

      openSession: (id, hasEverSent) => set((s) => {
        // Cache wins: once we've observed hasEverSent=true locally, never demote.
        const cachedSent = s.hasEverSentBySession.get(id) ?? false
        const effectiveSent = cachedSent || hasEverSent
        const mode: SessionMode = effectiveSent ? 'SPLIT' : 'HERO'
        if (
          s.activeSessionId === id &&
          s.modeBySession.get(id) === mode &&
          s.hasEverSentBySession.get(id) === effectiveSent
        ) return s
        const modes = new Map(s.modeBySession); modes.set(id, mode)
        const sent = new Map(s.hasEverSentBySession); sent.set(id, effectiveSent)
        return { activeSessionId: id, modeBySession: modes, hasEverSentBySession: sent }
      }),

      closeSession: () => set({ activeSessionId: null }),

      enterSplit: (id) => set((s) => {
        const modes = new Map(s.modeBySession); modes.set(id, 'SPLIT')
        return { modeBySession: modes }
      }),

      setSessionMode: (id, mode) => set((s) => {
        if ((s.modeBySession.get(id) ?? 'HERO') === mode) return s
        const modes = new Map(s.modeBySession); modes.set(id, mode)
        return { modeBySession: modes }
      }),

      markSessionSent: (id) => set((s) => {
        const sent = new Map(s.hasEverSentBySession); sent.set(id, true)
        return { hasEverSentBySession: sent }
      }),

      setSessionDataContext: (context) => set((s) => {
        const current = s.dataContextBySession.get(context.sessionId)
        if (sameSessionDataContext(current, context)) return s
        const next = new Map(s.dataContextBySession)
        next.set(context.sessionId, context)
        return { dataContextBySession: next }
      }),

      clearSessionDataContext: (id) => set((s) => {
        if (!s.dataContextBySession.has(id)) return s
        const next = new Map(s.dataContextBySession)
        next.delete(id)
        return { dataContextBySession: next }
      }),

      setPendingPrompt: (text) => set({ pendingPrompt: text }),
      setComposerRestoreDraft: (draft) => set({ composerRestoreDraft: draft }),
      setComposerDraft: (key, text) => set((s) => {
        if (s.composerDrafts[key] === text) return s
        return { composerDrafts: { ...s.composerDrafts, [key]: text } }
      }),
      setPendingModelPrompt: (on) => set({ pendingModelPrompt: on }),
      setPendingConnectionPrompt: (on) => set({ pendingConnectionPrompt: on }),
      setPendingActionAfterConnectionPick: (action) => set({ pendingActionAfterConnectionPick: action }),
      setComposerInsertText: (draft) => set({ composerInsertText: draft }),
    }),
    {
      name: 'data-talk.session',
      storage: createJSONStorage(() => localStorage),
      partialize: (s) => ({ activeSessionId: s.activeSessionId }),
    },
  ),
)
