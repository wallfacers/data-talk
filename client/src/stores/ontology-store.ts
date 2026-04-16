import { create } from 'zustand'
import type { Artifact } from '@/services/channel/event-reducer'

type OntologyState = {
  artifacts: Map<string, Artifact>
  upsertArtifact: (a: Artifact) => void
  removeArtifact: (id: string) => void
  clear: () => void
}

export const useOntologyStore = create<OntologyState>((set) => ({
  artifacts: new Map(),
  upsertArtifact: (a) => set(s => {
    const next = new Map(s.artifacts)
    next.set(a.id, { ...(next.get(a.id) ?? {} as Artifact), ...a })
    return { artifacts: next }
  }),
  removeArtifact: (id) => set(s => {
    const next = new Map(s.artifacts); next.delete(id); return { artifacts: next }
  }),
  clear: () => set({ artifacts: new Map() }),
}))
