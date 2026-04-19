import { create } from 'zustand'
import type { Part, MessageInfo } from '@/services/channel/types'
import { generateUuid } from '@/lib/uuid'

type ChatPartsState = {
  partsBySession: Map<string, Map<string, Part[]>>
  infoBySession: Map<string, Map<string, MessageInfo>>
  partIndexBySession: Map<string, Map<string, { messageId: string; idx: number }>>

  upsertPart: (sessionId: string, part: Part) => void
  upsertInfo: (sessionId: string, info: MessageInfo) => void
  upsertMany: (sessionId: string, parts: Part[]) => void
  replaceSession: (sessionId: string, list: Array<{ info: MessageInfo; parts: Part[] }>) => void
  removePart: (sessionId: string, messageId: string, partId: string) => void
  clearSession: (sessionId: string) => void
  getParts: (sessionId: string) => Part[]
  findPart: (sessionId: string, partId: string) => Part | null

  upsertPendingUser: (sessionId: string, text: string) => string
  promotePendingUser: (sessionId: string, pendingId: string, realId: string) => void
  markPendingUserFailed: (sessionId: string, pendingId: string, reason: string) => void
  removePendingUser: (sessionId: string, pendingId: string) => void
}

export const useChatPartsStore = create<ChatPartsState>((set, get) => ({
  partsBySession: new Map(),
  infoBySession: new Map(),
  partIndexBySession: new Map(),

  upsertPart: (sessionId, part) => set((s) => {
    const existingParts = s.partsBySession.get(sessionId)?.get(part.messageID)
    const existing = existingParts?.find((p) => p.id === part.id)
    if (existing && JSON.stringify(existing) === JSON.stringify(part)) return {}

    const bySession = new Map(s.partsBySession)
    const byMessage = new Map(bySession.get(sessionId) ?? new Map())
    const index = new Map(s.partIndexBySession.get(sessionId) ?? new Map())
    const list = [...(byMessage.get(part.messageID) ?? [])]
    const idx = existing && existingParts ? existingParts.indexOf(existing) : list.findIndex((p) => p.id === part.id)
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

  upsertMany: (sessionId, parts) => set((s) => {
    const partsBySession = new Map(s.partsBySession)
    const byMessage = new Map(partsBySession.get(sessionId) ?? new Map())
    const index = new Map(s.partIndexBySession.get(sessionId) ?? new Map())

    for (const part of parts) {
      const list = [...(byMessage.get(part.messageID) ?? [])]
      const existingIdx = list.findIndex((p) => p.id === part.id)
      if (existingIdx >= 0) {
        list[existingIdx] = part
      } else {
        list.push(part)
        index.set(part.id, { messageId: part.messageID, idx: list.length - 1 })
      }
      byMessage.set(part.messageID, list)
    }

    partsBySession.set(sessionId, byMessage)
    const indexBySession = new Map(s.partIndexBySession)
    indexBySession.set(sessionId, index)
    return { partsBySession, partIndexBySession: indexBySession }
  }),

  replaceSession: (sessionId, list) => set((s) => {
    const partsBySession = new Map(s.partsBySession)
    const infoBySession = new Map(s.infoBySession)
    const partIndexBySession = new Map(s.partIndexBySession)

    const byMessage = new Map<string, Part[]>()
    const byInfo = new Map<string, MessageInfo>()
    const index = new Map<string, { messageId: string; idx: number }>()

    for (const { info, parts } of list) {
      byInfo.set(info.id, info)
      byMessage.set(info.id, [...parts])
      parts.forEach((p, i) => index.set(p.id, { messageId: info.id, idx: i }))
    }

    partsBySession.set(sessionId, byMessage)
    infoBySession.set(sessionId, byInfo)
    partIndexBySession.set(sessionId, index)
    return { partsBySession, infoBySession, partIndexBySession }
  }),

  removePart: (sessionId, messageId, partId) => set((s) => {
    const index = s.partIndexBySession.get(sessionId)
    if (!index?.has(partId)) return {}
    const bySession = new Map(s.partsBySession)
    const byMessage = new Map(bySession.get(sessionId) ?? new Map())
    const indexBySession = new Map(s.partIndexBySession)
    const idx = new Map(indexBySession.get(sessionId) ?? new Map())
    const list = (byMessage.get(messageId) ?? []).filter((p: Part) => p.id !== partId)
    idx.delete(partId)
    byMessage.set(messageId, list)
    bySession.set(sessionId, byMessage)
    indexBySession.set(sessionId, idx)
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

  upsertPendingUser: (sessionId, text) => {
    const pendingId = `pending_${generateUuid()}`
    const partId = `pending_prt_${generateUuid()}`
    get().upsertInfo(sessionId, {
      id: pendingId,
      role: 'user',
      sessionID: sessionId,
      time: { created: Date.now() },
      __pending: true,
    })
    get().upsertPart(sessionId, {
      type: 'text',
      id: partId,
      sessionID: sessionId,
      messageID: pendingId,
      text,
      metadata: {},
    } as Part)
    return pendingId
  },

  promotePendingUser: (sessionId, pendingId, realId) => set((s) => {
    const byInfo = new Map(s.infoBySession.get(sessionId) ?? new Map<string, MessageInfo>())
    const pendingInfo = byInfo.get(pendingId)
    if (!pendingInfo) return {}

    const realInfo: MessageInfo = { ...pendingInfo, id: realId }
    delete realInfo.__pending
    delete realInfo.__failed
    delete realInfo.__failReason
    delete realInfo.__retrying
    byInfo.delete(pendingId)
    byInfo.set(realId, realInfo)

    const byMsg = new Map(s.partsBySession.get(sessionId) ?? new Map<string, Part[]>())
    const pendingParts = byMsg.get(pendingId) ?? []
    byMsg.delete(pendingId)
    byMsg.set(realId, pendingParts.map((p) => ({ ...p, messageID: realId })))

    const index = new Map(s.partIndexBySession.get(sessionId) ?? new Map())
    for (const [pid, entry] of index.entries()) {
      if (entry.messageId === pendingId) index.set(pid, { ...entry, messageId: realId })
    }

    const infoBySession = new Map(s.infoBySession); infoBySession.set(sessionId, byInfo)
    const partsBySession = new Map(s.partsBySession); partsBySession.set(sessionId, byMsg)
    const partIndexBySession = new Map(s.partIndexBySession); partIndexBySession.set(sessionId, index)
    return { infoBySession, partsBySession, partIndexBySession }
  }),

  markPendingUserFailed: (sessionId, pendingId, reason) => set((s) => {
    const byInfo = new Map(s.infoBySession.get(sessionId) ?? new Map<string, MessageInfo>())
    const info = byInfo.get(pendingId)
    if (!info) return {}
    byInfo.set(pendingId, { ...info, __failed: true, __failReason: reason, __retrying: false })
    const infoBySession = new Map(s.infoBySession); infoBySession.set(sessionId, byInfo)
    return { infoBySession }
  }),

  removePendingUser: (sessionId, pendingId) => set((s) => {
    const byInfo = new Map(s.infoBySession.get(sessionId) ?? new Map<string, MessageInfo>())
    byInfo.delete(pendingId)
    const byMsg = new Map(s.partsBySession.get(sessionId) ?? new Map<string, Part[]>())
    byMsg.delete(pendingId)
    const index = new Map(s.partIndexBySession.get(sessionId) ?? new Map())
    for (const [pid, entry] of Array.from(index.entries())) {
      if (entry.messageId === pendingId) index.delete(pid)
    }
    const infoBySession = new Map(s.infoBySession); infoBySession.set(sessionId, byInfo)
    const partsBySession = new Map(s.partsBySession); partsBySession.set(sessionId, byMsg)
    const partIndexBySession = new Map(s.partIndexBySession); partIndexBySession.set(sessionId, index)
    return { infoBySession, partsBySession, partIndexBySession }
  }),
}))
