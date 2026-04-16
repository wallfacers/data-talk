import { create } from 'zustand'
import type { Artifact } from '@/services/channel/event-reducer'

type OntologyState = {
  artifactsBySession: Map<string, Map<string, Artifact>>
  upsertArtifact: (sessionId: string, a: Artifact) => void
  replaceSession: (sessionId: string, artifacts: Artifact[]) => void
  removeArtifact: (sessionId: string, id: string) => void
  clearSession: (sessionId: string) => void
  getArtifacts: (sessionId: string) => Map<string, Artifact>
}

const EMPTY: Map<string, Artifact> = new Map()

export const useOntologyStore = create<OntologyState>((set, get) => ({
  artifactsBySession: new Map(),

  upsertArtifact: (sessionId, a) => set((s) => {
    const bySession = new Map(s.artifactsBySession)
    const map = new Map(bySession.get(sessionId) ?? new Map())
    map.set(a.id, { ...(map.get(a.id) ?? ({} as Artifact)), ...a })
    bySession.set(sessionId, map)
    return { artifactsBySession: bySession }
  }),

  replaceSession: (sessionId, artifacts) => set((s) => {
    const bySession = new Map(s.artifactsBySession)
    const map = new Map<string, Artifact>()
    for (const a of artifacts) map.set(a.id, a)
    bySession.set(sessionId, map)
    return { artifactsBySession: bySession }
  }),

  removeArtifact: (sessionId, id) => set((s) => {
    const bySession = new Map(s.artifactsBySession)
    const map = new Map(bySession.get(sessionId) ?? new Map())
    map.delete(id)
    bySession.set(sessionId, map)
    return { artifactsBySession: bySession }
  }),

  clearSession: (sessionId) => set((s) => {
    const bySession = new Map(s.artifactsBySession)
    bySession.delete(sessionId)
    return { artifactsBySession: bySession }
  }),

  getArtifacts: (sessionId) => get().artifactsBySession.get(sessionId) ?? EMPTY,
}))
