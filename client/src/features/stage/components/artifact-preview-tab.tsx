import { useEffect } from 'react'
import type { StageTab } from '@/stores/stage-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { ArtifactDispatcher } from '@/features/ontology/components/artifact-dispatcher'
import { useI18n } from '@/i18n/use-i18n'
import { coordinator } from '@/features/stage/persistence/stage-persistence-bootstrap'

type ArtifactPreviewPayload = {
  artifactId: string
  sessionId: string
}

function parsePayload(payload: unknown): ArtifactPreviewPayload | null {
  if (typeof payload !== 'object' || payload === null) return null
  const p = payload as Partial<ArtifactPreviewPayload>
  if (typeof p.artifactId !== 'string' || typeof p.sessionId !== 'string') return null
  return p as ArtifactPreviewPayload
}

export function ArtifactPreviewTab({ tab }: { tab: StageTab }) {
  const { t } = useI18n()
  const payload = parsePayload(tab.payload)

  useEffect(() => {
    if (payload || tab.payloadVersion == null) return
    void coordinator.ensureHydrated(tab.tabId)
  }, [payload, tab.payloadVersion, tab.tabId])

  const artifact = useOntologyStore((s) => {
    if (!payload) return null
    return s.artifactsBySession.get(payload.sessionId)?.get(payload.artifactId) ?? null
  })

  if (!payload) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
        {t('artifact.preparing')}
      </div>
    )
  }

  if (!artifact) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
        {t('artifact.preparing')}
      </div>
    )
  }

  return (
    <div className="flex h-full w-full min-h-0 min-w-0 items-center justify-center overflow-auto p-4">
      <div className="w-full">
        <ArtifactDispatcher artifact={artifact} />
      </div>
    </div>
  )
}
