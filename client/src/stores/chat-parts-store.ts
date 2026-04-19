import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import type { Part, MessageInfo } from '@/services/channel/types'
import { generateUuid } from '@/lib/uuid'

type ChatPartsState = {
  partsBySession: Map<string, Map<string, Part[]>>
  infoBySession: Map<string, Map<string, MessageInfo>>
  partIndexBySession: Map<string, Map<string, { messageId: string; idx: number }>>
  streamingBySession: Set<string>
  version: number

  upsertPart: (sessionId: string, part: Part) => void
  upsertInfo: (sessionId: string, info: MessageInfo) => void
  upsertMany: (sessionId: string, parts: Part[]) => void
  replaceSession: (sessionId: string, list: Array<{ info: MessageInfo; parts: Part[] }>) => void
  removePart: (sessionId: string, messageId: string, partId: string) => void
  clearSession: (sessionId: string) => void
  getParts: (sessionId: string) => Part[]
  findPart: (sessionId: string, partId: string) => Part | null
  setStreaming: (sessionId: string, on: boolean) => void

  upsertPendingUser: (sessionId: string, text: string) => string
  promotePendingUser: (sessionId: string, pendingId: string, realId: string) => void
  markPendingUserFailed: (sessionId: string, pendingId: string, reason: string) => void
  removePendingUser: (sessionId: string, pendingId: string) => void
}

export const useChatPartsStore = create<ChatPartsState>()(
  persist(
    (set, get) => ({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),
      version: 0,

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
        return { partsBySession: bySession, partIndexBySession: indexBySession, version: s.version + 1 }
      }),

      upsertInfo: (sessionId, info) => set((s) => {
        const bySession = new Map(s.infoBySession)
        const map = new Map(bySession.get(sessionId) ?? new Map())
        map.set(info.id, { ...(map.get(info.id) ?? info), ...info })
        bySession.set(sessionId, map)
        return { infoBySession: bySession, version: s.version + 1 }
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
        return { partsBySession, partIndexBySession: indexBySession, version: s.version + 1 }
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
        return { partsBySession, infoBySession, partIndexBySession, version: s.version + 1 }
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
        return { partsBySession: bySession, partIndexBySession: indexBySession, version: s.version + 1 }
      }),

      clearSession: (sessionId) => set((s) => {
        const parts = new Map(s.partsBySession); parts.delete(sessionId)
        const info = new Map(s.infoBySession); info.delete(sessionId)
        const index = new Map(s.partIndexBySession); index.delete(sessionId)
        const streaming = new Set(s.streamingBySession); streaming.delete(sessionId)
        return { partsBySession: parts, infoBySession: info, partIndexBySession: index, streamingBySession: streaming, version: s.version + 1 }
      }),

      setStreaming: (sessionId, on) => set((s) => {
        const has = s.streamingBySession.has(sessionId)
        if (on === has) return {}
        const next = new Set(s.streamingBySession)
        if (on) next.add(sessionId)
        else next.delete(sessionId)
        return { streamingBySession: next, version: s.version + 1 }
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
        const oldInfoMap = s.infoBySession.get(sessionId)
        if (!oldInfoMap || !oldInfoMap.has(pendingId)) return {}

        // Rebuild the info map to preserve insertion order
        const nextInfoMap = new Map<string, MessageInfo>()
        for (const [id, info] of oldInfoMap.entries()) {
          if (id === pendingId) {
            const realInfo: MessageInfo = { ...info, id: realId }
            delete realInfo.__pending
            delete realInfo.__failed
            delete realInfo.__failReason
            delete realInfo.__retrying
            nextInfoMap.set(realId, realInfo)
          } else {
            nextInfoMap.set(id, info)
          }
        }

        // Rebuild the parts map to preserve insertion order
        const oldPartsMap = s.partsBySession.get(sessionId)
        const nextPartsMap = new Map<string, Part[]>()
        if (oldPartsMap) {
          for (const [mid, parts] of oldPartsMap.entries()) {
            if (mid === pendingId) {
              nextPartsMap.set(realId, parts.map((p) => ({ ...p, messageID: realId })))
            } else {
              nextPartsMap.set(mid, parts)
            }
          }
        }

        const index = new Map(s.partIndexBySession.get(sessionId) ?? new Map())
        for (const [pid, entry] of index.entries()) {
          if (entry.messageId === pendingId) index.set(pid, { ...entry, messageId: realId })
        }

        const infoBySession = new Map(s.infoBySession); infoBySession.set(sessionId, nextInfoMap)
        const partsBySession = new Map(s.partsBySession); partsBySession.set(sessionId, nextPartsMap)
        const partIndexBySession = new Map(s.partIndexBySession); partIndexBySession.set(sessionId, index)
        return { infoBySession, partsBySession, partIndexBySession, version: s.version + 1 }
      }),

      markPendingUserFailed: (sessionId, pendingId, reason) => set((s) => {
        const byInfo = new Map(s.infoBySession.get(sessionId) ?? new Map<string, MessageInfo>())
        const info = byInfo.get(pendingId)
        if (!info) return {}
        byInfo.set(pendingId, { ...info, __failed: true, __failReason: reason, __retrying: false })
        const infoBySession = new Map(s.infoBySession); infoBySession.set(sessionId, byInfo)
        return { infoBySession, version: s.version + 1 }
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
        return { infoBySession, partsBySession, partIndexBySession, version: s.version + 1 }
      }),
    }),
    {
      name: 'data-talk.chat-parts',
      storage: createJSONStorage(() => sessionStorage),
      partialize: (s) => ({
        streamingBySession: Array.from(s.streamingBySession),
      }) as unknown as ChatPartsState,
      merge: (persisted, current) => {
        const p = persisted as { streamingBySession?: string[] } | undefined
        const arr = p?.streamingBySession ?? []
        return {
          ...current,
          streamingBySession: new Set<string>(arr),
        }
      },
    },
  ),
)
