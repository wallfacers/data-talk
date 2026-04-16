import { useEffect } from 'react'
import { http } from '@/services/http'
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

export function useSessionHistory(sessionId: string | null) {
  useEffect(() => {
    if (!sessionId) return
    let cancelled = false
    const load = async () => {
      try {
        const [mRes, aRes] = await Promise.all([
          http.get(`sessions/${sessionId}/messages`).json<{ messages: MessageDto[] }>(),
          http.get(`sessions/${sessionId}/artifacts`).json<{ artifacts: ArtifactDto[] }>(),
        ])
        if (cancelled) return

        const partsApi = useChatPartsStore.getState()
        partsApi.clearSession(sessionId)
        for (const m of mRes.messages ?? []) {
          for (const part of m.parts ?? []) partsApi.upsertPart(sessionId, part)
        }

        const ontApi = useOntologyStore.getState()
        const artifacts: Artifact[] = (aRes.artifacts ?? []).map((a) => ({
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
      } catch (err) {
        // Soft-fail: history load failures shouldn't block the active session
        console.warn('[use-session-history] load failed', err)
      }
    }
    void load()
    return () => { cancelled = true }
  }, [sessionId])
}
