import { create } from 'zustand'
import type { Part, MessageInfo } from '@/services/channel/types'

type ChatPartsState = {
  partsBySession: Map<string, Map<string, Part[]>>
  infoBySession: Map<string, Map<string, MessageInfo>>
  partIndexBySession: Map<string, Map<string, { messageId: string; idx: number }>>

  upsertPart: (sessionId: string, part: Part) => void
  upsertInfo: (sessionId: string, info: MessageInfo) => void
  upsertMany: (sessionId: string, parts: Part[]) => void
  removePart: (sessionId: string, messageId: string, partId: string) => void
  clearSession: (sessionId: string) => void
  getParts: (sessionId: string) => Part[]
  findPart: (sessionId: string, partId: string) => Part | null
}

export const useChatPartsStore = create<ChatPartsState>((set, get) => ({
  partsBySession: new Map(),
  infoBySession: new Map(),
  partIndexBySession: new Map(),

  upsertPart: (sessionId, part) => set((s) => {
    const bySession = new Map(s.partsBySession)
    const byMessage = new Map(bySession.get(sessionId) ?? new Map())
    const index = new Map(s.partIndexBySession.get(sessionId) ?? new Map())
    const list = [...(byMessage.get(part.messageID) ?? [])]
    const existing = index.get(part.id)
    const idx = existing ? existing.idx : list.findIndex((p) => p.id === part.id)
    if (idx >= 0) {
      list[idx] = part
    } else {
      list.push(part)
      index.set(part.id, { messageId: part.messageID, idx: list.length - 1 })
    }
    byMessage.set(part.messageID, list)
    bySession.set(sessionId, byMessage)
    const indexBySession = new Map(s.partIndexBySession)
    indexBySession.set(sessionId, index)
    return { partsBySession: bySession, partIndexBySession: indexBySession }
  }),

  upsertInfo: (sessionId, info) => set((s) => {
    const bySession = new Map(s.infoBySession)
    const map = new Map(bySession.get(sessionId) ?? new Map())
    map.set(info.id, { ...(map.get(info.id) ?? info), ...info })
    bySession.set(sessionId, map)
    return { infoBySession: bySession }
  }),

  upsertMany: (sessionId, parts) => {
    for (const p of parts) get().upsertPart(sessionId, p)
  },

  removePart: (sessionId, messageId, partId) => set((s) => {
    const bySession = new Map(s.partsBySession)
    const byMessage = new Map(bySession.get(sessionId) ?? new Map())
    const indexBySession = new Map(s.partIndexBySession)
    const index = new Map(indexBySession.get(sessionId) ?? new Map())
    const list = (byMessage.get(messageId) ?? []).filter((p: Part) => p.id !== partId)
    index.delete(partId)
    byMessage.set(messageId, list)
    bySession.set(sessionId, byMessage)
    indexBySession.set(sessionId, index)
    return { partsBySession: bySession, partIndexBySession: indexBySession }
  }),

  clearSession: (sessionId) => set((s) => {
    const parts = new Map(s.partsBySession); parts.delete(sessionId)
    const info = new Map(s.infoBySession); info.delete(sessionId)
    const index = new Map(s.partIndexBySession); index.delete(sessionId)
    return { partsBySession: parts, infoBySession: info, partIndexBySession: index }
  }),

  getParts: (sessionId) => {
    const byMessage = get().partsBySession.get(sessionId)
    if (!byMessage) return []
    return Array.from(byMessage.values()).flat()
  },

  findPart: (sessionId, partId) => {
    const index = get().partIndexBySession.get(sessionId)
    if (!index) return null
    const entry = index.get(partId)
    if (!entry) return null
    const byMessage = get().partsBySession.get(sessionId)
    if (!byMessage) return null
    const list = byMessage.get(entry.messageId)
    return list?.[entry.idx] ?? null
  },
}))
