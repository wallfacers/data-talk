import { useTimelineStore } from '@/stores/timeline-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useSessionStore } from '@/stores/session-store'
import { ArtifactDispatcher } from './artifact-dispatcher'
import { useI18n } from '@/i18n/use-i18n'

export function ArtifactCanvas() {
  const { t } = useI18n()
  const sessionId = useSessionStore(s => s.activeSessionId)
  const id = useTimelineStore(s => sessionId ? s.activeBySession.get(sessionId) : null)
  const artifacts = useOntologyStore(s => (sessionId ? s.artifactsBySession.get(sessionId) : undefined))
  const a = id && artifacts ? artifacts.get(id) : null
  if (!a) return <div className="flex h-full items-center justify-center text-sm text-muted-foreground">{t('artifact.preparing')}</div>
  return <ArtifactDispatcher artifact={a} />
}
