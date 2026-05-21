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
      setLastEventId: (sessionId, id) => {
        let nextMap: Map<string, number> | null = null
        set((s) => {
          const prev = s.lastEventIdBySession.get(sessionId) ?? 0
          if (id <= prev) return {}
          const next = new Map(s.lastEventIdBySession)
          next.set(sessionId, id)
          nextMap = next
          return { lastEventIdBySession: next }
        })
        // Sync-persist the cursor to sessionStorage so a CTRL+R refresh
        // mid-stream can resume from the latest event id rather than from
        // cursor 0. The async Zustand `persist` middleware flush is not
        // guaranteed to land before a page reload, which would otherwise
        // make the backend GET /subscribe replay the entire SessionBus
        // buffer (including a stale SessionIdle from the previous turn,
        // flipping the stop button back to "send"). Mirror the partialize
        // shape: Map → plain object.
        if (nextMap && typeof window !== 'undefined') {
          try {
            const raw = sessionStorage.getItem('data-talk.channel')
            const parsed = raw ? JSON.parse(raw) : null
            const wrapped =
              parsed && typeof parsed === 'object' && 'state' in parsed
                ? (parsed as { state: Record<string, unknown>; version?: number })
                : { state: {} as Record<string, unknown>, version: 0 }
            wrapped.state = wrapped.state ?? {}
            wrapped.state.lastEventIdBySession = Object.fromEntries(nextMap)
            sessionStorage.setItem('data-talk.channel', JSON.stringify(wrapped))
          } catch { /* non-critical path, fall back silently */ }
        }
      },
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
