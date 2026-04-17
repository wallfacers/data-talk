import { Table2Icon, LineChartIcon, NetworkIcon } from 'lucide-react'
import { useTimelineStore } from '@/stores/timeline-store'
import { useOntologyStore } from '@/stores/ontology-store'

export function useActiveArtifactTitle(sessionId: string | null) {
  const activeId = useTimelineStore(s => sessionId ? s.activeBySession.get(sessionId) ?? null : null)
  const artifact = useOntologyStore(s => {
    if (!sessionId || !activeId) return null
    return s.artifactsBySession.get(sessionId)?.get(activeId) ?? null
  })

  if (!artifact) return { Icon: null as null | typeof Table2Icon, label: 'Stage' }
  const Icon = artifact.kind === 'table' ? Table2Icon
            : artifact.kind === 'chart' ? LineChartIcon
            : NetworkIcon
  const kind = artifact.kind === 'table' ? '表'
            : artifact.kind === 'chart' ? '图' : 'ER'
  return { Icon, label: `Stage · ${kind} v${artifact.version}` }
}
