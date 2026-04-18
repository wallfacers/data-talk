import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { http } from '@/services/http'
import { normalizeRole } from '@/services/http-error'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'
import type { Part } from '@/services/channel/types'
import type { Artifact } from '@/services/channel/event-reducer'

type MessageDto = { id: string; role: string; parts: Part[] }
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
  const { data: messagesData } = useQuery({
    queryKey: sessionId ? historyQueryKeys.messages(sessionId) : ['session-history', 'messages', null],
    queryFn: () =>
      http.get(`sessions/${sessionId}/messages`, { silent: true } as any).json<{ messages: MessageDto[] }>(),
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

    const partsApi = useChatPartsStore.getState()
    partsApi.clearSession(sessionId)
    for (const m of messagesData.messages ?? []) {
      partsApi.upsertMeta(sessionId, {
        id: m.id,
        role: normalizeRole(m.role),
        createdAt: Number((m as any).createdAt ?? Date.now()),
      })
      for (const part of m.parts ?? []) partsApi.upsertPart(sessionId, part)
    }

    const ontApi = useOntologyStore.getState()
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
    ontApi.replaceSession(sessionId, artifacts)

    const tApi = useTimelineStore.getState()
    tApi.clear(sessionId)
    for (const a of artifacts) tApi.addArtifact(sessionId, a.id, a.supersedesId)
  }, [sessionId, messagesData, artifactsData])
}
