import { registerClientHandler } from './registry'
import { useOntologyStore } from '@/stores/ontology-store'

registerClientHandler('datatalk.pin_artifact', async (input) => {
  const { artifactId } = input as any
  const state = useOntologyStore.getState()
  const a = state.artifacts.get(artifactId)
  if (a) state.upsertArtifact({ ...a, pinned: true })
  return { pinned: true }
})
