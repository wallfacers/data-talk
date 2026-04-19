import { useMemo } from 'react'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import type { MessageInfo } from '@/services/channel/types'

export type Turn = {
  userMessageId?: string
  userInfo?: MessageInfo
  assistantMessageIds: string[]
}

export function useSessionTurns(sessionId: string | null): Turn[] {
  const infoMap = useChatPartsStore((s) => (sessionId ? s.infoBySession.get(sessionId) : undefined))

  return useMemo(() => {
    if (!sessionId || !infoMap) return []
    // Use insertion order from the Map instead of sorting by time.created.
    // This avoids issues where local clock skew makes user messages appear "after"
    // the assistant's response in time, causing a layout flip.
    const messages = Array.from(infoMap.values())
    const turns: Turn[] = []
    let current: Turn | null = null

    for (const info of messages) {
      if (info.role === 'user') {
        if (current) turns.push(current)
        current = { userMessageId: info.id, userInfo: info, assistantMessageIds: [] }
      } else if (info.role === 'assistant') {
        if (!current) current = { userMessageId: undefined, userInfo: undefined, assistantMessageIds: [] }
        current.assistantMessageIds.push(info.id)
      }
    }
    if (current) turns.push(current)
    return turns
  }, [sessionId, infoMap])
}
