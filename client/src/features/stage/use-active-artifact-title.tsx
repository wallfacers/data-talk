import { Table2Icon, LineChartIcon, NetworkIcon } from 'lucide-react'
import { useTimelineStore } from '@/stores/timeline-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useI18n } from '@/i18n/use-i18n'

export function useActiveArtifactTitle(sessionId: string | null) {
  const { t } = useI18n()
  const activeId = useTimelineStore(s => sessionId ? s.activeBySession.get(sessionId) ?? null : null)
  const artifact = useOntologyStore(s => {
    if (!sessionId || !activeId) return null
    return s.artifactsBySession.get(sessionId)?.get(activeId) ?? null
  })

  if (!artifact) return { Icon: null as null | typeof Table2Icon, label: t('stage.title') }
  const Icon = artifact.kind === 'table' ? Table2Icon
            : artifact.kind === 'chart' ? LineChartIcon
            : NetworkIcon
  const kind = artifact.kind === 'table' ? t('stage.kind.table')
            : artifact.kind === 'chart' ? t('stage.kind.chart') : t('stage.kind.er')
  return { Icon, label: t('stage.titleWithKind', { kind, version: artifact.version }) }
}
