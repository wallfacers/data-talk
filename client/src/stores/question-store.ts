import { create } from 'zustand'
import type { QuestionRequest } from '@/services/api/question'

type QuestionState = {
  bySession: Map<string, QuestionRequest[]>

  /** Insert or replace a pending question (deduped by requestId). */
  upsert: (sessionId: string, request: QuestionRequest) => void
  /** Drop a pending question once it is replied / rejected. */
  removeByRequestId: (sessionId: string, requestId: string) => void
  /** Replace the whole pending set for a session (bootstrap / reconnect rebuild). */
  setForSession: (sessionId: string, requests: QuestionRequest[]) => void
}

export const useQuestionStore = create<QuestionState>((set) => ({
  bySession: new Map(),

  upsert: (sessionId, request) => set((s) => {
    const next = new Map(s.bySession)
    const list = next.get(sessionId) ?? []
    const idx = list.findIndex((q) => q.id === request.id)
    next.set(sessionId, idx >= 0
      ? list.map((q) => (q.id === request.id ? request : q))
      : [...list, request])
    return { bySession: next }
  }),

  removeByRequestId: (sessionId, requestId) => set((s) => {
    const list = s.bySession.get(sessionId)
    if (!list) return {}
    const next = new Map(s.bySession)
    const filtered = list.filter((q) => q.id !== requestId)
    if (filtered.length > 0) next.set(sessionId, filtered)
    else next.delete(sessionId)
    return { bySession: next }
  }),

  setForSession: (sessionId, requests) => set((s) => {
    const next = new Map(s.bySession)
    if (requests.length > 0) next.set(sessionId, requests)
    else next.delete(sessionId)
    return { bySession: next }
  }),
}))
