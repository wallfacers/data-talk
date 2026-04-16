import { create } from 'zustand'
import type { Connection } from '@/services/api/connection'

type ConnectionState = {
  activeConnectionId: string | null
  connections: Connection[]
  setActive: (id: string | null) => void
  setConnections: (conns: Connection[]) => void
}

export const useConnectionStore = create<ConnectionState>((set) => ({
  activeConnectionId: null,
  connections: [],
  setActive: (id) => set({ activeConnectionId: id }),
  setConnections: (conns) => set({ connections: conns }),
}))
