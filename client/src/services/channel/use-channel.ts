import { useCallback, useMemo } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import { ChannelClient } from './channel-client'
import type { StreamEvent, Part, MessageInfo } from './types'
import { generateUuid } from '@/lib/uuid'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'
import { useSessionStore } from '@/stores/session-store'
import { useConnectionStore } from '@/features/connection/store'
import { useChannelStore } from '@/stores/channel-store'
import { getClientHandler } from '@/features/actions/registry'
import { normalizeError, showErrorToast } from '@/services/http-error'
import { toast } from 'sonner'
import {
  invalidateSessionLists,
  patchCachedSessionLists,
} from '@/features/session/hooks/use-sessions'

function getApiBaseUrl(): string {
  const env = (import.meta as any).env?.VITE_API_BASE_URL
  if (typeof env === 'string' && env.length > 0) return env.replace(/\/$/, '')
  return ''
}

function getPendingUserText(sessionId: string, pendingUserId: string | null): string | null {
  if (!pendingUserId) return null
  const pendingParts = useChatPartsStore.getState().partsBySession.get(sessionId)?.get(pendingUserId) ?? []
  const pendingTextPart = pendingParts.find((part) => part.type === 'text') as Part | undefined
  const text = typeof (pendingTextPart as { text?: unknown } | undefined)?.text === 'string'
    ? (pendingTextPart as { text: string }).text
    : null
  return text
}

function shouldPromotePendingUserFromPart(
  sessionId: string,
  pendingUserId: string | null,
  part: Part | undefined,
): boolean {
  if (!pendingUserId || !part || part.type !== 'text' || part.messageID.startsWith('pending_')) return false
  const pendingText = getPendingUserText(sessionId, pendingUserId)
  if (pendingText === null) return false
  return part.text === pendingText
}

export function buildEventSink(
  sessionId: string,
  client: ChannelClient | null,
  queryClient: QueryClient,
  _connectionId: string | null = null,
  pendingUserId: string | null = null,
) {
  return (evt: StreamEvent) => {
    const { event, data } = evt
    if (typeof evt.id === 'number' && evt.id > 0) {
      useChannelStore.getState().setLastEventId(sessionId, evt.id)
    }
    if (event === 'message.created' || event === 'message.updated') {
      const m = (data as any).info ?? (data as any).message ?? (data as any)
      if (!m?.id) return

      const mid = m.id
      const store = useChatPartsStore.getState()
      const existing = store.infoBySession.get(sessionId)?.get(mid)

      const role = (m.role ? String(m.role).toLowerCase() : existing?.role) as MessageInfo['role']
      const modelID = m.modelID ?? existing?.modelID
      const providerID = m.providerID ?? existing?.providerID

      const nextTime = {
        created: m.time?.created ?? existing?.time.created ?? Date.now(),
        completed: m.time?.completed ?? existing?.time.completed,
      }

      const info: MessageInfo = {
        id: mid,
        role: role || 'assistant',
        sessionID: m.sessionID ?? existing?.sessionID ?? sessionId,
        time: nextTime,
        providerID,
        modelID,
        parentID: m.parentID ?? existing?.parentID,
        agent: m.agent ?? existing?.agent,
        mode: m.mode ?? existing?.mode,
        error: m.error ?? existing?.error,
        finish: m.finish ?? existing?.finish,
        tokens: m.tokens ?? existing?.tokens,
      }
      store.upsertInfo(sessionId, info)
    } else if (event === 'session.idle' || (event === 'session.status' && (data as any)?.status === 'idle')) {
      // Turn-done signals: OpenCode's native `session.idle` (DtEvent.SessionIdle),
      // plus the backend's composite `session.status=idle` which ChannelController
      // now publishes only AFTER observing real turn completion. Consume both so
      // we clear the streaming flag on whichever arrives first via the bus. The
      // composer flips back to the send button and the thinking indicator exits.
      // Message-level time.completed (set via message.updated) drives per-message
      // UI state separately.
      useChatPartsStore.getState().markSessionTurnCompleted(sessionId)
      useChatPartsStore.getState().setStreaming(sessionId, false)
    } else if (event === 'session.meta.updated') {
      const { sessionId: sid, title, titleLocked } = data as { sessionId: string; title: string; titleLocked: boolean }
      patchCachedSessionLists(queryClient, sid, { title, titleLocked })
    } else if (event === 'message.part.created' || event === 'message.part.updated') {
      const part = (data as any).part
      useChatPartsStore.getState().upsertPart(sessionId, part)

      // Only promote the optimistic user when the echoed real user text part
      // matches the pending text. Older replayed user events must not steal it.
      if (part?.messageID && shouldPromotePendingUserFromPart(sessionId, pendingUserId, part)) {
        const infoMap = useChatPartsStore.getState().infoBySession.get(sessionId)
        const info = infoMap?.get(part.messageID)
        if (info?.role === 'user' && pendingUserId) {
          useChatPartsStore.getState().promotePendingUser(sessionId, pendingUserId, part.messageID)
        }
      }
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
    } else if (event === 'session.error') {
      const { error } = data as { error?: string }
      useChatPartsStore.getState().markSessionTurnCompleted(sessionId)
      useChatPartsStore.getState().setStreaming(sessionId, false)
      showErrorToast(normalizeError(new Error(error ?? 'Session error')))
    } else if (event === 'session.created' || event === 'session.deleted') {
      invalidateSessionLists(queryClient)
    } else if (event === 'session.compacted') {
      toast.info('AI 上下文已压缩，早期消息可能不再可用')
    } else if (event === 'session.diff') {
      // payload semantics undocumented in OpenCode 1.4.7 — safely ignored
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
  const queryClient = useQueryClient()
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const isStreaming = useChatPartsStore((s) =>
    sessionId ? s.streamingBySession.has(sessionId) : false,
  )
  const enterSplit = useSessionStore((s) => s.enterSplit)
  const markSessionSent = useSessionStore((s) => s.markSessionSent)
  const client = useChannelClient(sessionId)
  const connectionId = useConnectionStore((s) => s.activeConnectionId)

  const sendMessage = useCallback(
    async (parts: any[]) => {
      if (!client || !sessionId) return false

      // 抽取首个 text part 的 text 作为 pending 文本
      const firstText = parts.find((p) => p?.type === 'text') as { text?: string } | undefined
      const pendingText = typeof firstText?.text === 'string' ? firstText.text : ''
      const pendingId = useChatPartsStore.getState().upsertPendingUser(sessionId, pendingText)

      useChatPartsStore.getState().setStreaming(sessionId, true)
      enterSplit(sessionId)
      markSessionSent(sessionId)
      // 发送消息后刷新会话列表，让 hasEverSent 更新
      invalidateSessionLists(queryClient)
      const sink = buildEventSink(sessionId, client, queryClient, connectionId, pendingId)
      try {
        await client.sendMessage(parts, sink)
        return true
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err)
        useChatPartsStore.getState().markPendingUserFailed(sessionId, pendingId, msg)
        showErrorToast(normalizeError(err))
        return false
      } finally {
        useChatPartsStore.getState().setStreaming(sessionId, false)
      }
    },
    [client, sessionId, enterSplit, markSessionSent, queryClient, connectionId],
  )

  const retryPendingUser = useCallback(
    async (pendingId: string, parts: any[]) => {
      if (!client || !sessionId) return false
      useChatPartsStore.setState((s) => {
        const byInfo = new Map(s.infoBySession.get(sessionId) ?? new Map())
        const info = byInfo.get(pendingId)
        if (!info) return {}
        byInfo.set(pendingId, { ...info, __failed: false, __retrying: true, __failReason: undefined })
        const infoBySession = new Map(s.infoBySession); infoBySession.set(sessionId, byInfo)
        return { infoBySession }
      })
      useChatPartsStore.getState().setStreaming(sessionId, true)
      const sink = buildEventSink(sessionId, client, queryClient, connectionId)
      try {
        await client.sendMessage(parts, sink)
        return true
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err)
        useChatPartsStore.getState().markPendingUserFailed(sessionId, pendingId, msg)
        showErrorToast(normalizeError(err))
        return false
      } finally {
        useChatPartsStore.getState().setStreaming(sessionId, false)
      }
    },
    [client, sessionId, queryClient, connectionId],
  )

  const removePendingUser = useCallback(
    (pendingId: string) => {
      if (!sessionId) return
      useChatPartsStore.getState().removePendingUser(sessionId, pendingId)
    },
    [sessionId],
  )

  const abort = useCallback(async () => {
    if (!client) return
    await client.abort()
  }, [client])

  return { sendMessage, abort, isStreaming, client, retryPendingUser, removePendingUser }
}
