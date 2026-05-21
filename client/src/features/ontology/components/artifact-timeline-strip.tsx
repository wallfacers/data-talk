import { useMemo } from 'react'
import type { Artifact } from '@/services/channel/event-reducer'
import { useTimelineStore } from '@/stores/timeline-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useSessionStore } from '@/stores/session-store'
import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'

// 稳定的空引用，避免 selector 每次返回 `?? []` / `?? new Map()` 导致
// useSyncExternalStore 认为 snapshot 永远在变，从而触发 "Maximum update depth".
const EMPTY_ORDER: string[] = []
const EMPTY_ARTIFACTS: Map<string, Artifact> = new Map()

export function ArtifactTimelineStrip() {
  const { t } = useI18n()
  const sessionId = useSessionStore(s => s.activeSessionId)
  const order = useTimelineStore(s => {
    if (!sessionId) return EMPTY_ORDER
    return s.orderBySession.get(sessionId) ?? EMPTY_ORDER
  })
  const active = useTimelineStore(s => sessionId ? s.activeBySession.get(sessionId) : null)
  const setActive = useTimelineStore(s => s.setActive)
  const artifacts = useOntologyStore(s => {
    if (!sessionId) return EMPTY_ARTIFACTS
    return s.artifactsBySession.get(sessionId) ?? EMPTY_ARTIFACTS
  })
  const supersededIds = useMemo(() => {
    const s = new Set<string>()
    for (const a of artifacts.values()) if (a.supersedesId) s.add(a.supersedesId)
    return s
  }, [artifacts])

  return (
    <div className="flex gap-1 overflow-x-auto p-2">
      {order.map(id => {
        const a = artifacts.get(id)
        if (!a) return null
        return (
          <button key={id} onClick={() => sessionId && setActive(sessionId, id)}
            className={cn(
              'rounded-full px-3 py-1 text-xs border',
              id === active ? 'bg-primary text-primary-foreground' : 'bg-background',
              supersededIds.has(id) && 'opacity-40'
            )}>
            {a.kind === 'table' ? t('stage.kind.table') : a.kind === 'chart' ? t('stage.kind.chart') : t('stage.kind.er')} · v{a.version}
          </button>
        )
      })}
    </div>
  )
}
