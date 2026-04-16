import { useMemo } from 'react'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useSessionStore } from '@/stores/session-store'
import { PartRenderer } from './part-renderer'

export function MessageStream() {
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const partsBySession = useChatPartsStore((s) => s.partsBySession)
  const byMessage = sessionId ? partsBySession.get(sessionId) : undefined

  const groups = useMemo(() => {
    if (!byMessage) return []
    const entries = Array.from(byMessage.entries())
    // Parts for a single message come in the order reduced; messages come
    // in insertion order, which matches creation order from the reducer.
    return entries.map(([messageId, parts]) => ({ messageId, parts }))
  }, [byMessage])

  return (
    <div className="flex flex-col gap-4">
      {groups.map((g) => (
        <div key={g.messageId} className="flex flex-col gap-1">
          {g.parts.map((p) => <PartRenderer key={p.id} part={p} />)}
        </div>
      ))}
    </div>
  )
}
