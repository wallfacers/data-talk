import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'

type ChannelState = {
  isConnected: boolean
  lastEventIdBySession: Map<string, number>
  setConnected: (on: boolean) => void
  setLastEventId: (sessionId: string, id: number) => void
}

type PersistedShape = {
  lastEventIdBySession: Record<string, number>
}

export const useChannelStore = create<ChannelState>()(
  persist(
    (set) => ({
      isConnected: false,
      lastEventIdBySession: new Map<string, number>(),
      setConnected: (on) => set({ isConnected: on }),
      setLastEventId: (sessionId, id) => set((s) => {
        const prev = s.lastEventIdBySession.get(sessionId) ?? 0
        if (id <= prev) return {}
        const next = new Map(s.lastEventIdBySession)
        next.set(sessionId, id)
        return { lastEventIdBySession: next }
      }),
    }),
    {
      name: 'data-talk.channel',
      storage: createJSONStorage(() => sessionStorage),
      // Persist only the cursor map. `isConnected` is transient.
      // Convert Map → plain object on write so JSON.stringify works.
      partialize: (s) => ({
        lastEventIdBySession: Object.fromEntries(s.lastEventIdBySession),
      }) as unknown as ChannelState,
      // Rebuild the Map on read. Zustand 5's `merge` hook runs after
      // `createJSONStorage` returns the parsed object.
      merge: (persisted, current) => {
        const p = persisted as Partial<PersistedShape> | undefined
        const raw = (p?.lastEventIdBySession ?? {}) as Record<string, number>
        return {
          ...current,
          lastEventIdBySession: new Map(Object.entries(raw)),
        }
      },
    },
  ),
)
