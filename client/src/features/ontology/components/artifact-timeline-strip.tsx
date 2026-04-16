import { useTimelineStore } from '@/stores/timeline-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useSessionStore } from '@/stores/session-store'
import { cn } from '@/lib/utils'

export function ArtifactTimelineStrip() {
  const sessionId = useSessionStore(s => s.activeSessionId)
  const order = useTimelineStore(s => sessionId ? (s.orderBySession.get(sessionId) ?? []) : [])
  const active = useTimelineStore(s => sessionId ? s.activeBySession.get(sessionId) : null)
  const setActive = useTimelineStore(s => s.setActive)
  const artifacts = useOntologyStore(s => s.artifacts)

  return (
    <div className="flex gap-1 overflow-x-auto border-b p-2">
      {order.map(id => {
        const a = artifacts.get(id)
        if (!a) return null
        const superseded = Array.from(artifacts.values()).some(x => x.supersedesId === id)
        return (
          <button key={id} onClick={() => sessionId && setActive(sessionId, id)}
            className={cn(
              'rounded-full px-3 py-1 text-xs border',
              id === active ? 'bg-primary text-primary-foreground' : 'bg-background',
              superseded && 'opacity-40'
            )}>
            {a.kind === 'table' ? '表' : a.kind === 'chart' ? '图' : 'ER'} · v{a.version}
          </button>
        )
      })}
    </div>
  )
}
