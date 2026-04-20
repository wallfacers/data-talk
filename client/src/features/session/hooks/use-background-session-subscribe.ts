import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { buildEventSink, useChannelClient } from '@/services/channel/use-channel'
import type { MessageInfo, Part } from '@/services/channel/types'
import { http } from '@/services/http'
import { useChannelStore } from '@/stores/channel-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useConnectionStore } from '@/features/connection/store'

// Separate from useSessionSubscribe's module-level set — no interference.
const bgSubscribedSessions = new Set<string>()

type HistoryItem = { info: MessageInfo; parts: Part[] }
type HistoryResponse =
  | HistoryItem[]
  | { messages: Array<{ id: string; role: string; parts: Part[]; createdAt?: number }> }

function normalizeHistory(raw: HistoryResponse): HistoryItem[] {
  if (Array.isArray(raw)) return raw
  return (raw.messages ?? []).map((message) => ({
    info: {
      id: message.id,
      role: message.role as MessageInfo['role'],
      sessionID: '',
      time: { created: Number(message.createdAt ?? Date.now()) },
    },
    parts: message.parts,
  }))
}

async function hasLiveAssistantTurn(sessionId: string): Promise<boolean> {
  try {
    const raw = await http
      .get(`sessions/${sessionId}/messages`, { silent: true } as any)
      .json<HistoryResponse>()
    return normalizeHistory(raw).some(
      ({ info }) => info.role === 'assistant' && typeof info.time.completed !== 'number',
    )
  } catch {
    return false
  }
}

export function useBackgroundSessionSubscribe(sessionId: string) {
  const client = useChannelClient(sessionId)
  const queryClient = useQueryClient()
  const connectionId = useConnectionStore((s) => s.activeConnectionId)

  useEffect(() => {
    if (!client) return
    if (bgSubscribedSessions.has(sessionId)) return

    const resumeFrom = useChannelStore.getState().lastEventIdBySession.get(sessionId)
    const sink = buildEventSink(sessionId, client, queryClient, connectionId)
    let unsub: (() => void) | null = null
    let subscribed = false
    let cancelled = false

    void hasLiveAssistantTurn(sessionId).then((shouldResume) => {
      if (cancelled) return
      if (!shouldResume) {
        useChatPartsStore.getState().setStreaming(sessionId, false)
        return
      }
      if (bgSubscribedSessions.has(sessionId)) return

      bgSubscribedSessions.add(sessionId)
      subscribed = true
      unsub = client.subscribe(resumeFrom, sink)
    })

    return () => {
      cancelled = true
      unsub?.()
      if (subscribed) bgSubscribedSessions.delete(sessionId)
    }
  }, [client, sessionId, queryClient, connectionId])
}
