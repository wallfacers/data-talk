import { useCallback, useMemo, useState } from 'react'
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
import { useFileArtifactsStore } from '@/features/stage/stores/file-artifacts-store'
import { useChannelStore } from '@/stores/channel-store'
import { getClientHandler } from '@/features/actions/registry'
import { normalizeError, showErrorToast } from '@/services/http-error'
import { toast } from 'sonner'
import { translateMessage } from '@/i18n/messages'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import type { SessionDataContext } from '@/services/api/session-data-context'
import '@/features/actions/client-handlers'
import {
  invalidateSessionLists,
  patchCachedSessionLists,
} from '@/features/session/hooks/use-sessions'

type ActionResultErrorInfo = {
  code: string
  message: string
  retriable?: boolean
  details?: unknown
}

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

function resolvePendingUserCandidate(sessionId: string, preferredPendingUserId: string | null): string | null {
  const store = useChatPartsStore.getState()
  const infoMap = store.infoBySession.get(sessionId)
  if (!infoMap) return null

  const isPromotablePending = (messageId: string | null): messageId is string => {
    if (!messageId) return false
    const info = infoMap.get(messageId)
    return info?.role === 'user' && info.__pending === true
  }

  if (isPromotablePending(preferredPendingUserId)) return preferredPendingUserId

  if (!store.streamingBySession.has(sessionId)) return null

  let fallbackPendingUserId: string | null = null
  for (const info of infoMap.values()) {
    if (info.role !== 'user' || info.__pending !== true) continue
    if (fallbackPendingUserId) return null
    fallbackPendingUserId = info.id
  }
  return fallbackPendingUserId
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

const SESSION_DATA_CONTEXT_TOOL_NAMES = new Set([
  'datatalk_select_connection',
  'select_connection',
  'datatalk_set_data_context',
  'set_data_context',
  'datatalk_get_data_context',
  'get_data_context',
])

function isSessionDataContextTool(tool: unknown): tool is string {
  return typeof tool === 'string'
    && SESSION_DATA_CONTEXT_TOOL_NAMES.has(tool.replace(/\./g, '_'))
}

function objectRecord(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null
}

function nullableString(value: unknown): string | null {
  return typeof value === 'string' && value.length > 0 ? value : null
}

function selectedLevel(value: unknown, context: Pick<SessionDataContext, 'connectionId' | 'database' | 'schema'>) {
  if (value === 'connection' || value === 'database' || value === 'schema') return value
  if (context.schema) return 'schema'
  if (context.database) return 'database'
  if (context.connectionId) return 'connection'
  return null
}

function extractSessionDataContextOutput(output: unknown): Record<string, unknown> | null {
  const direct = objectRecord(output)
  if (direct) {
    const structured = objectRecord(direct.structuredContent)
    if (structured) return structured

    const content = Array.isArray(direct.content) ? direct.content : null
    const firstText = content
      ?.map((item) => objectRecord(item))
      .map((item) => item?.text)
      .find((text): text is string => typeof text === 'string')
    if (firstText) return extractSessionDataContextOutput(firstText)

    return direct
  }

  if (typeof output === 'string') {
    try {
      return extractSessionDataContextOutput(JSON.parse(output))
    } catch {
      return null
    }
  }

  return null
}

function syncSessionDataContextFromToolPart(
  fallbackSessionId: string,
  part: Part | undefined,
  queryClient: QueryClient,
) {
  if (!part || part.type !== 'tool') return
  const tool = (part as { tool?: unknown }).tool
  if (!isSessionDataContextTool(tool)) return

  const state = objectRecord((part as { state?: unknown }).state)
  if (state?.status !== 'completed') return
  const output = extractSessionDataContextOutput(state.output)
  if (!output) return

  const previousSessionId = nullableString(output.sessionId) ?? fallbackSessionId
  const connectionId = nullableString(output.connectionId)
  const database = nullableString(output.database)
  const schema = nullableString(output.schema)
  const context: SessionDataContext = {
    sessionId: previousSessionId,
    connectionId,
    connectionNameSnapshot: nullableString(output.connectionNameSnapshot) ?? nullableString(output.connectionName),
    database,
    schema,
    selectedLevel: selectedLevel(output.selectedLevel, { connectionId, database, schema }),
    updatedAt: typeof output.updatedAt === 'number' ? output.updatedAt : Date.now(),
  }

  useSessionStore.getState().setSessionDataContext(context)
  queryClient.setQueryData(['session-data-context', context.sessionId], context)

  if (useSessionStore.getState().activeSessionId === context.sessionId) {
    useConnectionStore.getState().setActive(context.connectionId)
  }
}

function upsertSessionErrorMessage(sessionId: string, eventId: number | undefined, message: string) {
  const store = useChatPartsStore.getState()
  const infoMap = store.infoBySession.get(sessionId)
  const now = Date.now()
  const error = { name: 'SessionError', data: { message } }
  const existingAssistant = Array.from(infoMap?.values() ?? [])
    .reverse()
    .find((info) => info.role === 'assistant')

  if (existingAssistant) {
    store.upsertInfo(sessionId, {
      ...existingAssistant,
      time: {
        ...existingAssistant.time,
        completed: existingAssistant.time.completed ?? now,
      },
      error: existingAssistant.error ?? error,
    })
    return
  }

  const suffix = typeof eventId === 'number' && eventId > 0 ? String(eventId) : 'latest'
  store.upsertInfo(sessionId, {
    id: `session_error_${sessionId}_${suffix}`,
    role: 'assistant',
    sessionID: sessionId,
    time: { created: now, completed: now },
    error,
  })
}

// Deduplication set for action.invoke callIds. See the buildEventSink comment
// where this is used — the SessionBus currently fans one event out to every
// active subscriber sink, and a POST stream's subscription resumes from cursor
// 0 so already-handled action.invoke events can be replayed on the next turn.
// Bounded FIFO keeps memory flat even on long-running sessions.
const DISPATCHED_CALL_ID_MAX = 512
const DISPATCHED_CALL_IDS_KEY = 'data-talk.dispatched-call-ids'
const dispatchedCallIds = new Set<string>()
let dispatchedCallIdsHydrated = false

function loadDispatchedCallIds(): void {
  if (dispatchedCallIdsHydrated) return
  dispatchedCallIdsHydrated = true
  try {
    const raw = sessionStorage.getItem(DISPATCHED_CALL_IDS_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    if (!Array.isArray(parsed)) return
    for (const callId of parsed) {
      if (typeof callId === 'string' && callId.length > 0) dispatchedCallIds.add(callId)
    }
  } catch {
    // Session replay dedupe is best-effort; malformed storage should not break streaming.
  }
}

function persistDispatchedCallIds(): void {
  try {
    sessionStorage.setItem(DISPATCHED_CALL_IDS_KEY, JSON.stringify([...dispatchedCallIds]))
  } catch {
    // Ignore storage failures; in-memory dedupe still protects the current runtime.
  }
}

function markCallIdDispatched(callId: string | null | undefined): boolean {
  if (!callId) return true
  loadDispatchedCallIds()
  if (dispatchedCallIds.has(callId)) return false
  if (dispatchedCallIds.size >= DISPATCHED_CALL_ID_MAX) {
    const oldest = dispatchedCallIds.values().next().value
    if (oldest) dispatchedCallIds.delete(oldest)
  }
  dispatchedCallIds.add(callId)
  persistDispatchedCallIds()
  return true
}

export function __resetCallIdDispatchForTest(options?: { keepPersisted?: boolean }) {
  dispatchedCallIds.clear()
  dispatchedCallIdsHydrated = false
  if (!options?.keepPersisted) {
    try { sessionStorage.removeItem(DISPATCHED_CALL_IDS_KEY) } catch {}
  }
}

function normalizeActionInvokeError(err: unknown): ActionResultErrorInfo {
  if (typeof err === 'object' && err !== null) {
    const candidate = err as {
      code?: unknown
      message?: unknown
      retriable?: unknown
      details?: unknown
    }
    if (typeof candidate.code === 'string' && typeof candidate.message === 'string') {
      return {
        code: candidate.code,
        message: candidate.message,
        retriable: typeof candidate.retriable === 'boolean' ? candidate.retriable : undefined,
        details: candidate.details,
      }
    }
  }

  return {
    code: 'client_action_error',
    message: err instanceof Error ? err.message : String(err),
  }
}

export function buildEventSink(
  sessionId: string,
  client: ChannelClient | null,
  queryClient: QueryClient,
  _connectionId: string | null = null,
  pendingUserId: string | null = null,
) {
  // Wall-clock baseline for "subscribe just opened". After CTRL+R the client
  // re-issues GET /api/sessions/{id}/channel; if no Last-Event-ID is sent
  // (or the cursor's async sessionStorage flush has not landed yet) the
  // backend ChannelController replays from cursor 0, which can include a
  // stale SessionIdle / session.status=idle / session.error from the
  // previous turn. Honoring those would immediately flip streamingBySession
  // off and the composer back to "send". Suppress turn-done signals during
  // the first 500ms after this sink is built — by then the live deltas of
  // the in-flight turn (if any) will have arrived past the replay tail and
  // the next genuine idle will be a real one.
  const subscribeAt = Date.now()
  return (evt: StreamEvent) => {
    const { event, data } = evt
    // Dedupe by event id. A live session has two subscribers at once
    // (long-lived GET /subscribe sink plus each POST /send_message sink),
    // and a fresh POST sink currently resumes from cursor 0 — so any
    // event with id <= already-seen cursor has already been applied and
    // applying it again doubles text in `message.part.delta` append
    // fields. Events without an id (`id === 0`) bypass this gate because
    // they have no deterministic way to be matched to a prior delivery.
    if (typeof evt.id === 'number' && evt.id > 0) {
      const prev = useChannelStore.getState().lastEventIdBySession.get(sessionId) ?? 0
      if (evt.id <= prev) return
      useChannelStore.getState().setLastEventId(sessionId, evt.id)
    }
    if (event === 'message.created' || event === 'message.updated') {
      const m = (data as any).info ?? (data as any).message ?? (data as any)
      if (!m?.id) return

      const mid = m.id
      const store = useChatPartsStore.getState()
      const existing = store.infoBySession.get(sessionId)?.get(mid)
      const pendingUserCandidateId = resolvePendingUserCandidate(sessionId, pendingUserId)

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
      // Any sink can receive the echoed real user first: the active GET
      // subscribe stream and the POST send_message stream race each other.
      // Resolve the current optimistic user at the session level, then promote
      // before inserting the real info so useSessionTurns never sees both ids
      // at once and never opens a transient duplicate user turn.
      if (info.role === 'user' && pendingUserCandidateId && mid !== pendingUserCandidateId && !existing) {
        const pendingInfo = store.infoBySession.get(sessionId)?.get(pendingUserCandidateId)
        if (pendingInfo?.__pending && pendingInfo.role === 'user') {
          store.promotePendingUser(sessionId, pendingUserCandidateId, mid)
        }
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
      if (Date.now() - subscribeAt < 500) {
        // Stale replay event from the SessionBus buffer (e.g. previous turn's
        // idle). After CTRL+R the backend GET /subscribe replays from cursor 0
        // by default and that old idle would flip the stop button back to send
        // mid-stream. See BUG-0037 follow-up.
        return
      }
      useChatPartsStore.getState().markSessionTurnCompleted(sessionId)
      useChatPartsStore.getState().setStreaming(sessionId, false)
    } else if (event === 'session.meta.updated') {
      const { sessionId: sid, title, titleLocked } = data as { sessionId: string; title: string; titleLocked: boolean }
      patchCachedSessionLists(queryClient, sid, { title, titleLocked })
    } else if (event === 'message.part.created' || event === 'message.part.updated') {
      const part = (data as any).part
      useChatPartsStore.getState().upsertPart(sessionId, part)
      syncSessionDataContextFromToolPart(sessionId, part, queryClient)
      const pendingUserCandidateId = resolvePendingUserCandidate(sessionId, pendingUserId)

      // Only promote the optimistic user when the echoed real user text part
      // matches the pending text. Older replayed user events must not steal it.
      if (part?.messageID && shouldPromotePendingUserFromPart(sessionId, pendingUserCandidateId, part)) {
        const infoMap = useChatPartsStore.getState().infoBySession.get(sessionId)
        const info = infoMap?.get(part.messageID)
        if (info?.role === 'user' && pendingUserCandidateId) {
          useChatPartsStore.getState().promotePendingUser(sessionId, pendingUserCandidateId, part.messageID)
        }
      }
    } else if (event === 'message.part.delta') {
      const { partId, field, delta } = data as { partId: string; field: string; delta: string }
      // Buffers if the part hasn't landed yet; drained on the next upsertPart
      // for the same partId. Prevents silent loss when deltas race ahead of
      // their part.created/updated (intermittent on fresh sessions).
      useChatPartsStore.getState().appendPartDelta(sessionId, partId, field, delta)
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
          originMessageId: d.patch?.originMessageId,
          originPartId: d.patch?.originPartId,
        })
        useTimelineStore.getState().addArtifact(sessionId, d.id, d.patch?.supersedesId)
      }
    } else if (event === 'session.error') {
      if (Date.now() - subscribeAt < 500) {
        // Same replay window as session.idle above — a stale session.error
        // from the previous turn's tail would otherwise clear streaming and
        // surface a misleading toast right after CTRL+R.
        return
      }
      const { error } = data as { error?: string }
      const language = getCurrentLanguage()
      const message = error ?? translateMessage(language, 'session.errorFallback')
      useChatPartsStore.getState().markSessionTurnCompleted(sessionId)
      upsertSessionErrorMessage(sessionId, evt.id, message)
      useChatPartsStore.getState().setStreaming(sessionId, false)
      showErrorToast(normalizeError(new Error(message)))
    } else if (event === 'session.created' || event === 'session.deleted') {
      invalidateSessionLists(queryClient)
    } else if (event === 'session.compacted') {
      toast.info(translateMessage(getCurrentLanguage(), 'session.contextCompacted'))
    } else if (event === 'session.diff') {
      // payload semantics undocumented in OpenCode 1.4.7 — safely ignored
    } else if (event === 'export.completed') {
      const d = data as {
        exportId: string
        downloadUrl: string
        rowCount: number
        format: string
        fileSizeBytes?: number
      }
      const language = getCurrentLanguage()
      const formatKey = `export.format.${d.format}` as Parameters<typeof translateMessage>[1]
      const formatLabel = translateMessage(language, formatKey)
      // translateMessage returns the key itself when the entry is missing; fall back to raw format.
      const displayFormat = formatLabel === formatKey ? d.format : formatLabel
      const downloadUrl = d.downloadUrl?.startsWith('http')
        ? d.downloadUrl
        : `${getApiBaseUrl()}${d.downloadUrl ?? ''}`
      toast.success(
        translateMessage(language, 'export.completed', { count: d.rowCount, format: displayFormat }),
        {
          duration: 30_000,
          action: downloadUrl
            ? {
                label: translateMessage(language, 'export.download'),
                onClick: () => {
                  window.open(downloadUrl, '_blank', 'noopener,noreferrer')
                },
              }
            : undefined,
        },
      )
    } else if (event === 'action.invoke' && client) {
      const { callId, actionId, input } = data as any
      // The backend publishes to a single SessionBus, but a live session has
      // TWO subscribers at once (the long-lived GET /subscribe sink plus each
      // POST /send_message sink), AND a new POST sink currently resumes from
      // cursor 0 — so the same action.invoke can reach buildEventSink two or
      // more times. Client handlers are not idempotent (e.g. workspace.open
      // would create a new tab on every replay), so dedupe on callId here.
      if (!markCallIdDispatched(callId)) return
      const handler = getClientHandler(actionId)
      if (!handler) {
        const error = {
          code: 'client_action_not_registered',
          message: `No client action handler registered for ${actionId}`,
          details: { actionId },
        }
        console.warn('[channel] missing client action handler', { actionId, callId })
        void client.actionResult(callId, false, undefined, error)
        return
      }
      handler(input, { sessionId })
        .then((output) => client.actionResult(callId, true, output))
        .catch((err) => client.actionResult(callId, false, undefined, normalizeActionInvokeError(err)))
    } else if (
      event === 'file_artifact.detected' ||
      event === 'file_artifact.archive_requested' ||
      event === 'file_artifact.archived' ||
      event === 'file_artifact.discarded' ||
      event === 'file_artifact.legacy_migrated'
    ) {
      useFileArtifactsStore.getState().applyDtEvent({
        type: event,
        data: data as Parameters<
          ReturnType<typeof useFileArtifactsStore.getState>['applyDtEvent']
        >[0]['data'],
      } as Parameters<ReturnType<typeof useFileArtifactsStore.getState>['applyDtEvent']>[0])
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
  const [isAborting, setIsAborting] = useState(false)

  const sendMessage = useCallback(
    async (parts: any[]) => {
      if (!client || !sessionId) return false

      // 抽取首个 text part 的 text 作为 pending 文本
      const firstText = parts.find((p) => p?.type === 'text') as { text?: string } | undefined
      const pendingText = typeof firstText?.text === 'string' ? firstText.text : ''
      // Extract file parts for optimistic rendering — avoids bubble jitter when
      // attachments arrive later via SSE.
      const fileParts = parts.filter(
        (p: any) => p?.type === 'file_upload' || p?.type === 'file',
      ) as Part[]
      const pendingId = useChatPartsStore.getState().upsertPendingUser(sessionId, pendingText, fileParts.length > 0 ? fileParts : undefined)

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
    if (!client || !sessionId || isAborting) return false
    setIsAborting(true)
    try {
      const aborted = await client.abort()
      if (!aborted) {
        // Backend/OpenCode already has no running turn; clear stale UI state.
        useChatPartsStore.getState().markSessionTurnCompleted(sessionId)
        useChatPartsStore.getState().setStreaming(sessionId, false)
      }
      return aborted
    } catch (err) {
      showErrorToast(normalizeError(err))
      return false
    } finally {
      setIsAborting(false)
    }
  }, [client, sessionId, isAborting])

  return {
    sendMessage,
    abort,
    isStreaming,
    isAborting,
    canAbort: isStreaming && !isAborting,
    client,
    retryPendingUser,
    removePendingUser,
  }
}
