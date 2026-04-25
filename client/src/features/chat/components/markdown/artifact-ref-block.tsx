import { useMemo } from 'react'
import { useSessionStore } from '@/stores/session-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { ChartRenderer } from './chart-renderer'

export function ArtifactRefBlock({ artifactId }: { artifactId: string }) {
  const sessionId = useSessionStore((s) => s.activeSessionId)

  const artifact = useOntologyStore((s) => {
    if (!sessionId || !artifactId) return null
    return s.artifactsBySession.get(sessionId)?.get(artifactId) ?? null
  })

  const echartsOption = useMemo(() => {
    const payload = artifact?.payload as { echartsOption?: unknown } | undefined
    const opt = payload?.echartsOption
    if (!opt || typeof opt !== 'object') return null
    return opt as Record<string, unknown>
  }, [artifact])

  if (!echartsOption) return null

  return (
    <div className="my-2 overflow-hidden rounded-lg border border-[var(--dt-border-subtle)]">
      <ChartRenderer option={echartsOption} />
    </div>
  )
}
