import { create } from 'zustand'

type TimelineState = {
  orderBySession: Map<string, string[]>
  activeBySession: Map<string, string | null>
  manualBySession: Map<string, boolean>

  addArtifact: (sessionId: string, artifactId: string, supersedesId?: string) => void
  setActive: (sessionId: string, artifactId: string) => void
  clear: (sessionId: string) => void
}

export const useTimelineStore = create<TimelineState>((set) => ({
  orderBySession: new Map(),
  activeBySession: new Map(),
  manualBySession: new Map(),

  addArtifact: (sessionId, artifactId, supersedesId) => set(s => {
    const order = new Map(s.orderBySession)
    const list = [...(order.get(sessionId) ?? [])]
    if (!list.includes(artifactId)) list.push(artifactId)
    order.set(sessionId, list)

    const currentActive = s.activeBySession.get(sessionId) ?? null
    const manual = s.manualBySession.get(sessionId) ?? false
    const activeMap = new Map(s.activeBySession)

    if (!manual) {
      if (!supersedesId) {
        activeMap.set(sessionId, artifactId)
      } else if (currentActive === supersedesId) {
        activeMap.set(sessionId, artifactId)
      }
    }
    return { orderBySession: order, activeBySession: activeMap }
  }),

  setActive: (sessionId, artifactId) => set(s => {
    const list = s.orderBySession.get(sessionId) ?? []
    const isNewest = list[list.length - 1] === artifactId
    const manual = new Map(s.manualBySession); manual.set(sessionId, !isNewest)
    const active = new Map(s.activeBySession); active.set(sessionId, artifactId)
    return { manualBySession: manual, activeBySession: active }
  }),

  clear: (sessionId) => set(s => {
    const order = new Map(s.orderBySession); order.delete(sessionId)
    const active = new Map(s.activeBySession); active.delete(sessionId)
    const manual = new Map(s.manualBySession); manual.delete(sessionId)
    return { orderBySession: order, activeBySession: active, manualBySession: manual }
  }),
}))
