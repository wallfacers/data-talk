import { create } from 'zustand'
import type { Part } from '@/services/channel/types'

export type MessageMeta = {
  id: string
  role: 'user' | 'assistant' | 'system'
  createdAt: number
}

type ChatPartsState = {
  partsBySession: Map<string, Map<string, Part[]>>
  metaBySession: Map<string, Map<string, MessageMeta>>
  upsertPart: (sessionId: string, part: Part) => void
  upsertMeta: (sessionId: string, meta: MessageMeta) => void
  upsertMany: (sessionId: string, parts: Part[]) => void
  removePart: (sessionId: string, messageId: string, partId: string) => void
  clearSession: (sessionId: string) => void
  getParts: (sessionId: string) => Part[]
}

export const useChatPartsStore = create<ChatPartsState>((set, get) => ({
  partsBySession: new Map(),
  metaBySession: new Map(),

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

  upsertMeta: (sessionId, meta) => set((s) => {
    const bySession = new Map(s.metaBySession)
    const map = new Map(bySession.get(sessionId) ?? new Map())
    map.set(meta.id, { ...(map.get(meta.id) ?? meta), ...meta })
    bySession.set(sessionId, map)
    return { metaBySession: bySession }
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
    const parts = new Map(s.partsBySession); parts.delete(sessionId)
    const meta = new Map(s.metaBySession); meta.delete(sessionId)
    return { partsBySession: parts, metaBySession: meta }
  }),

  getParts: (sessionId) => {
    const byMessage = get().partsBySession.get(sessionId)
    if (!byMessage) return []
    return Array.from(byMessage.values()).flat()
  },
}))
