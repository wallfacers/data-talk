import { useCallback, useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import { ChannelClient } from './channel-client'
import type { StreamEvent, Part } from './types'
import { generateUuid } from '@/lib/uuid'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'
import { useSessionStore } from '@/stores/session-store'
import { useConnectionStore } from '@/features/connection/store'
import { getClientHandler } from '@/features/actions/registry'
import { normalizeError, normalizeRole, showErrorToast } from '@/services/http-error'

function getApiBaseUrl(): string {
  const env = (import.meta as any).env?.VITE_API_BASE_URL
  if (typeof env === 'string' && env.length > 0) return env.replace(/\/$/, '')
  return ''
}

export function buildEventSink(sessionId: string, client: ChannelClient | null, queryClient: QueryClient, connectionId: string | null = null) {
  return (evt: StreamEvent) => {
    const { event, data } = evt
    if (event === 'message.created') {
      const m = (data as any).message
      useChatPartsStore.getState().upsertMeta(sessionId, {
        id: m.id,
        role: normalizeRole(m.role),
        createdAt: Number(m.createdAt ?? Date.now()),
      })
    } else if (event === 'session.meta.updated') {
      queryClient.invalidateQueries({ queryKey: ['sessions', connectionId] })
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
  const clientId = useMemo(() => generateUuid(), [])
  return useMemo(() => {
    if (!sessionId) return null
    return new ChannelClient({ baseUrl: getApiBaseUrl(), sessionId, clientId })
  }, [sessionId, clientId])
}

export function useChannel() {
  const [isStreaming, setIsStreaming] = useState(false)
  const queryClient = useQueryClient()
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const enterSplit = useSessionStore((s) => s.enterSplit)
  const client = useChannelClient(sessionId)
  const connectionId = useConnectionStore((s) => s.activeConnectionId)

  const sendMessage = useCallback(
    async (parts: any[]) => {
      if (!client || !sessionId) return
      setIsStreaming(true)
      enterSplit(sessionId)
      const sink = buildEventSink(sessionId, client, queryClient, connectionId)
      try {
        await client.sendMessage(parts, sink)
      } catch (err) {
        showErrorToast(normalizeError(err))
      } finally {
        setIsStreaming(false)
      }
    },
    [client, sessionId, enterSplit, queryClient, connectionId],
  )

  const abort = useCallback(async () => {
    if (!client) return
    await client.abort()
  }, [client])

  return { sendMessage, abort, isStreaming, client }
}
