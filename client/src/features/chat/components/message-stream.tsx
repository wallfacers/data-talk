import { useMemo } from 'react'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useSessionStore } from '@/stores/session-store'
import { PartRenderer } from './part-renderer'
import { cn } from '@/lib/utils'

export function MessageStream() {
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const partsBySession = useChatPartsStore((s) => s.partsBySession)
  const metaBySession = useChatPartsStore((s) => s.metaBySession)

  const groups = useMemo(() => {
    if (!sessionId) return []
    const byMessage = partsBySession.get(sessionId)
    const metaMap = metaBySession.get(sessionId)
    if (!byMessage) return []

    const entries = Array.from(byMessage.entries()).map(([messageId, parts]) => ({
      messageId,
      parts,
      meta: metaMap?.get(messageId),
    }))
    entries.sort((a, b) => (a.meta?.createdAt ?? 0) - (b.meta?.createdAt ?? 0))
    return entries
  }, [sessionId, partsBySession, metaBySession])

  return (
    <div className="flex flex-col gap-4">
      {groups.map((g) => {
        const role = g.meta?.role ?? 'assistant'
        const align = role === 'user' ? 'items-end' : 'items-start'
        const bubble = role === 'user'
          ? 'bg-primary text-primary-foreground'
          : 'bg-muted'
        return (
          <div key={g.messageId} className={cn('flex flex-col gap-1', align)}>
            <div className={cn('max-w-[85%] rounded-lg px-3 py-2 text-sm', bubble)}>
              {g.parts.map((p) => <PartRenderer key={p.id} part={p} />)}
            </div>
          </div>
        )
      })}
    </div>
  )
}
