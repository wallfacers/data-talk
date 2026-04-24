import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import type { Connection } from '@/services/api/connection'

function sameConnection(a: Connection, b: Connection) {
  return a.id === b.id
    && a.name === b.name
    && a.kind === b.kind
    && a.host === b.host
    && a.port === b.port
    && a.databaseName === b.databaseName
    && a.username === b.username
    && a.createdAt === b.createdAt
    && a.connectTimeout === b.connectTimeout
    && a.lastTestStatus === b.lastTestStatus
    && a.lastTestAt === b.lastTestAt
}

function sameConnections(a: Connection[], b: Connection[]) {
  return a.length === b.length && a.every((item, index) => sameConnection(item, b[index]))
}

type ConnectionState = {
  activeConnectionId: string | null
  connections: Connection[]
  setActive: (id: string | null) => void
  setConnections: (conns: Connection[]) => void
}

function nextActiveConnectionId(activeConnectionId: string | null, connections: Connection[]) {
  if (!activeConnectionId) return null
  return connections.some((connection) => connection.id === activeConnectionId)
    ? activeConnectionId
    : null
}

export const useConnectionStore = create<ConnectionState>()(
  persist(
    (set) => ({
      activeConnectionId: null,
      connections: [],
      setActive: (id) => set((s) => (s.activeConnectionId === id ? s : { activeConnectionId: id })),
      setConnections: (conns) => set((s) => {
        const nextActive = nextActiveConnectionId(s.activeConnectionId, conns)
        if (sameConnections(s.connections, conns) && s.activeConnectionId === nextActive) {
          return s
        }
        return {
          connections: conns,
          activeConnectionId: nextActive,
        }
      }),
    }),
    {
      name: 'data-talk.connection',
      storage: createJSONStorage(() => localStorage),
      partialize: (s) => ({ activeConnectionId: s.activeConnectionId }),
    },
  ),
)
