import { useCallback, useMemo, useState } from 'react'
import { ChannelClient } from './channel-client'
import type { StreamEvent, Part } from './types'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'
import { useSessionStore } from '@/stores/session-store'
import { getClientHandler } from '@/features/actions/registry'

function getApiBaseUrl(): string {
  const env = (import.meta as any).env?.VITE_API_BASE_URL
  if (typeof env === 'string' && env.length > 0) return env.replace(/\/$/, '')
  return ''
}

export function buildEventSink(sessionId: string, client: ChannelClient | null) {
  return (evt: StreamEvent) => {
    const { event, data } = evt
    if (event === 'message.created') {
      const m = (data as any).message
      useChatPartsStore.getState().upsertMeta(sessionId, {
        id: m.id,
        role: (m.role ?? 'assistant') as 'user' | 'assistant' | 'system',
        createdAt: Number(m.createdAt ?? Date.now()),
      })
    } else if (event === 'message.part.created' || event === 'message.part.updated') {
      useChatPartsStore.getState().upsertPart(sessionId, (data as any).part)
    } else if (event === 'message.part.delta') {
      const { partId, field, delta } = data as { partId: string; field: string; delta: string }
      const store = useChatPartsStore.getState()
      const existing = store.findPart(sessionId, partId)
      if (existing) {
        const prev = (existing as Record<string, unknown>)[field] ?? ''
        const next = { ...existing, [field]: String(prev) + delta }
        store.upsertPart(sessionId, next as Part)
      }
    } else if (event === 'message.part.removed') {
      const { partId } = data as any
      const store = useChatPartsStore.getState()
      const index = store.partIndexBySession.get(sessionId)
      if (index) {
        const entry = index.get(partId)
        if (entry) store.removePart(sessionId, entry.messageId, partId)
      }
    } else if (event === 'ontology.updated') {
      const d = data as any
      if (d.objectType === 'datatalk.artifact') {
        useOntologyStore.getState().upsertArtifact(sessionId, {
          id: d.id,
          version: d.patch?.version ?? 1,
          kind: d.patch?.kind ?? 'table',
          supersedesId: d.patch?.supersedesId,
          payload: d.patch,
        })
        useTimelineStore.getState().addArtifact(sessionId, d.id, d.patch?.supersedesId)
      }
    } else if (event === 'action.invoke' && client) {
      const { callId, actionId, input } = data as any
      const handler = getClientHandler(actionId)
      if (handler) {
        handler(input, { sessionId })
          .then((output) => client.actionResult(callId, true, output))
          .catch((err) => client.actionResult(callId, false, undefined,
            { code: 'client_action_error', message: String(err) }))
      }
    }
  }
}

export function useChannelClient(sessionId: string | null): ChannelClient | null {
  const clientId = useMemo(() => crypto.randomUUID(), [])
  return useMemo(() => {
    if (!sessionId) return null
    return new ChannelClient({ baseUrl: getApiBaseUrl(), sessionId, clientId })
  }, [sessionId, clientId])
}

export function useChannel() {
  const [isStreaming, setIsStreaming] = useState(false)
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const enterSplit = useSessionStore((s) => s.enterSplit)
  const client = useChannelClient(sessionId)

  const sendMessage = useCallback(
    async (parts: any[]) => {
      if (!client || !sessionId) return
      setIsStreaming(true)
      enterSplit(sessionId)
      const sink = buildEventSink(sessionId, client)
      try {
        await client.sendMessage(parts, sink)
      } finally {
        setIsStreaming(false)
      }
    },
    [client, sessionId, enterSplit],
  )

  const abort = useCallback(async () => {
    if (!client) return
    await client.abort()
  }, [client])

  return { sendMessage, abort, isStreaming, client }
}
