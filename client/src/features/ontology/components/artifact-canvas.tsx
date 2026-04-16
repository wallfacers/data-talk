import { useTimelineStore } from '@/stores/timeline-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useSessionStore } from '@/stores/session-store'
import { ArtifactDispatcher } from './artifact-dispatcher'

export function ArtifactCanvas() {
  const sessionId = useSessionStore(s => s.activeSessionId)
  const id = useTimelineStore(s => sessionId ? s.activeBySession.get(sessionId) : null)
  const a = useOntologyStore(s => id ? s.artifacts.get(id) : null)
  if (!a) return <div className="flex h-full items-center justify-center text-sm text-muted-foreground">AI 正在准备…</div>
  return <ArtifactDispatcher artifact={a} />
}
