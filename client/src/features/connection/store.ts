import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import type { Connection } from '@/services/api/connection'

type ConnectionState = {
  activeConnectionId: string | null
  connections: Connection[]
  setActive: (id: string | null) => void
  setConnections: (conns: Connection[]) => void
}

export const useConnectionStore = create<ConnectionState>()(
  persist(
    (set) => ({
      activeConnectionId: null,
      connections: [],
      setActive: (id) => set({ activeConnectionId: id }),
      setConnections: (conns) => set({ connections: conns }),
    }),
    {
      name: 'data-talk.connection',
      storage: createJSONStorage(() => localStorage),
      partialize: (s) => ({ activeConnectionId: s.activeConnectionId }),
    },
  ),
)
