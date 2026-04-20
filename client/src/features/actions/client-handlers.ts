import { registerClientHandler } from './registry'
import { useOntologyStore } from '@/stores/ontology-store'

registerClientHandler('datatalk.pin_artifact', async (input, ctx) => {
  const { artifactId } = input as { artifactId: string }
  const map = useOntologyStore.getState().artifactsBySession.get(ctx.sessionId)
  const a = map?.get(artifactId)
  if (a) useOntologyStore.getState().upsertArtifact(ctx.sessionId, { ...a, pinned: true })
  return { pinned: true }
})

// Trigger registration of datatalk.ui.* handlers
import './ui-handlers'
