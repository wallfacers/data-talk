import { useMemo } from 'react'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useSessionStore } from '@/stores/session-store'
import { PartRenderer } from './part-renderer'
import { cn } from '@/lib/utils'

export function MessageStream() {
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const partsByMessage = useChatPartsStore((s) => sessionId ? s.partsBySession.get(sessionId) : undefined)
  const metaMap = useChatPartsStore((s) => sessionId ? s.metaBySession.get(sessionId) : undefined)

  const groups = useMemo(() => {
    if (!sessionId || !partsByMessage) return []

    const entries = Array.from(partsByMessage.entries()).map(([messageId, parts]) => ({
      messageId,
      parts,
      meta: metaMap?.get(messageId),
    }))
    entries.sort((a, b) => (a.meta?.createdAt ?? 0) - (b.meta?.createdAt ?? 0))
    return entries
  }, [sessionId, partsByMessage, metaMap])

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
