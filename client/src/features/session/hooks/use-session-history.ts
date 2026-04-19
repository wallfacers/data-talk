import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { http } from '@/services/http'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'
import type { MessageInfo, Part } from '@/services/channel/types'
import type { Artifact } from '@/services/channel/event-reducer'

/**
 * Whether the server snapshot is materially emptier than what the store already
 * holds for this session. Used to avoid wiping optimistic pending messages and
 * in-flight SSE parts when a re-mounting subscriber triggers a background
 * refetch that returns before the backend has persisted the new turn.
 */
function shouldSkipReplace(sessionId: string, serverMsgCount: number, serverArtifactCount: number): boolean {
  const partsStore = useChatPartsStore.getState()
  if (partsStore.streamingBySession.has(sessionId)) return true
  const storeMsgCount = partsStore.infoBySession.get(sessionId)?.size ?? 0
  const storeArtifactCount = useOntologyStore.getState().artifactsBySession.get(sessionId)?.size ?? 0
  return (
    (serverMsgCount === 0 && storeMsgCount > 0) ||
    (serverArtifactCount === 0 && storeArtifactCount > 0)
  )
}

type HistoryItem = { info: MessageInfo; parts: Part[] }
type HistoryResponse =
  | HistoryItem[]
  | { messages: Array<{ id: string; role: string; parts: Part[]; createdAt?: number }> }

function normalizeHistory(raw: HistoryResponse): HistoryItem[] {
  if (Array.isArray(raw)) return raw
  // 向后兼容旧响应 {messages:[...]}（后端过渡期）
  return (raw.messages ?? []).map((m) => ({
    info: {
      id: m.id,
      role: m.role as 'user' | 'assistant' | 'system',
      sessionID: '', // 旧响应没有
      time: { created: Number(m.createdAt ?? Date.now()) },
    },
    parts: m.parts,
  }))
}

type ArtifactDto = {
  id: string
  version: number
  kind: 'table' | 'chart' | 'erd'
  sessionId?: string
  supersedesId?: string
  supersedesVersion?: number
  pinned?: boolean
  payload?: unknown
  createdAt?: number
}

const historyQueryKeys = {
  messages: (sessionId: string) => ['session-history', 'messages', sessionId] as const,
  artifacts: (sessionId: string) => ['session-history', 'artifacts', sessionId] as const,
}

export function useSessionHistory(sessionId: string | null) {
  const { data: messagesData, error: messagesError } = useQuery({
    queryKey: sessionId ? historyQueryKeys.messages(sessionId) : ['session-history', 'messages', null],
    queryFn: () =>
      http.get(`sessions/${sessionId}/messages`, { silent: true } as any).json<HistoryResponse>(),
    enabled: !!sessionId,
    staleTime: 0,
    retry: 1,
  })

  const { data: artifactsData } = useQuery({
    queryKey: sessionId ? historyQueryKeys.artifacts(sessionId) : ['session-history', 'artifacts', null],
    queryFn: () =>
      http.get(`sessions/${sessionId}/artifacts`, { silent: true } as any).json<{ artifacts: ArtifactDto[] }>(),
    enabled: !!sessionId,
    staleTime: 0,
    retry: 1,
  })

  useEffect(() => {
    if (!sessionId || !messagesData || !artifactsData) return

    const list = normalizeHistory(messagesData)
    const artifacts: Artifact[] = (artifactsData.artifacts ?? []).map((a) => ({
      id: a.id,
      version: a.version,
      kind: a.kind,
      supersedesId: a.supersedesId,
      supersedesVersion: a.supersedesVersion,
      pinned: a.pinned,
      payload: a.payload,
      createdAt: a.createdAt,
    }))

    // Guard: this hook has multiple subscribers (SessionCanvas + TurnList) with
    // staleTime: 0, so each remount re-fires this effect. Without the guard an
    // optimistic pending user message or in-flight streamed parts get clobbered
    // the moment a late subscriber appears with an older cached snapshot.
    if (shouldSkipReplace(sessionId, list.length, artifacts.length)) return

    useChatPartsStore.getState().replaceSession(sessionId, list)
    useOntologyStore.getState().replaceSession(sessionId, artifacts)

    const tApi = useTimelineStore.getState()
    tApi.clear(sessionId)
    for (const a of artifacts) tApi.addArtifact(sessionId, a.id, a.supersedesId)
  }, [sessionId, messagesData, artifactsData])

  return { error: messagesError }
}
