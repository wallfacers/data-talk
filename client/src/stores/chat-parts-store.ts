import { create } from 'zustand'
import type { Part } from '@/services/channel/types'

type ChatPartsState = {
  partsBySession: Map<string, Map<string, Part[]>>  // sessionId → messageId → Part[]
  upsertPart: (sessionId: string, part: Part) => void
  upsertMany: (sessionId: string, parts: Part[]) => void
  removePart: (sessionId: string, messageId: string, partId: string) => void
  clearSession: (sessionId: string) => void
  getParts: (sessionId: string) => Part[]
}

export const useChatPartsStore = create<ChatPartsState>((set, get) => ({
  partsBySession: new Map(),

  upsertPart: (sessionId, part) => set((s) => {
    const bySession = new Map(s.partsBySession)
    const byMessage = new Map(bySession.get(sessionId) ?? new Map())
    const list = [...(byMessage.get(part.messageID) ?? [])]
    const idx = list.findIndex((p) => p.id === part.id)
    if (idx >= 0) list[idx] = part
    else list.push(part)
    byMessage.set(part.messageID, list)
    bySession.set(sessionId, byMessage)
    return { partsBySession: bySession }
  }),

  upsertMany: (sessionId, parts) => {
    for (const p of parts) get().upsertPart(sessionId, p)
  },

  removePart: (sessionId, messageId, partId) => set((s) => {
    const bySession = new Map(s.partsBySession)
    const byMessage = new Map(bySession.get(sessionId) ?? new Map())
    const list = (byMessage.get(messageId) ?? []).filter((p: Part) => p.id !== partId)
    byMessage.set(messageId, list)
    bySession.set(sessionId, byMessage)
    return { partsBySession: bySession }
  }),

  clearSession: (sessionId) => set((s) => {
    const bySession = new Map(s.partsBySession)
    bySession.delete(sessionId)
    return { partsBySession: bySession }
  }),

  getParts: (sessionId) => {
    const byMessage = get().partsBySession.get(sessionId)
    if (!byMessage) return []
    return Array.from(byMessage.values()).flat()
  },
}))
