import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import type { Part, MessageInfo } from '@/services/channel/types'
import { generateUuid } from '@/lib/uuid'

const nextLayoutVersion = (state: Pick<ChatPartsState, 'layoutVersion'>, increment = 1) =>
  (state.layoutVersion ?? 0) + increment

type ChatPartsState = {
  partsBySession: Map<string, Map<string, Part[]>>
  infoBySession: Map<string, Map<string, MessageInfo>>
  partIndexBySession: Map<string, Map<string, { messageId: string; idx: number }>>
  streamingBySession: Set<string>
  /**
   * Buffer for `message.part.delta` events that arrive before the part itself
   * is in the store. Drained into the part on the next `upsertPart` that
   * matches the same partId. Shape: sessionId → partId → field → accumulated.
   */
  pendingDeltasBySession: Map<string, Map<string, Record<string, string>>>
  version: number
  layoutVersion: number
  /**
   * Bumps only when the user submits a new message. Drives auto-scroll's
   * "re-follow on user send" behavior independently of structural layout
   * changes caused by assistant deltas.
   */
  userSendVersion: number

  upsertPart: (sessionId: string, part: Part) => void
  upsertInfo: (sessionId: string, info: MessageInfo) => void
  upsertMany: (sessionId: string, parts: Part[]) => void
  replaceSession: (sessionId: string, list: Array<{ info: MessageInfo; parts: Part[] }>) => void
  removePart: (sessionId: string, messageId: string, partId: string) => void
  clearSession: (sessionId: string) => void
  getParts: (sessionId: string) => Part[]
  findPart: (sessionId: string, partId: string) => Part | null
  setStreaming: (sessionId: string, on: boolean) => void
  appendPartDelta: (sessionId: string, partId: string, field: string, delta: string) => void

  upsertPendingUser: (sessionId: string, text: string) => string
  promotePendingUser: (sessionId: string, pendingId: string, realId: string) => void
  markPendingUserFailed: (sessionId: string, pendingId: string, reason: string) => void
  removePendingUser: (sessionId: string, pendingId: string) => void
  markSessionTurnCompleted: (sessionId: string) => void
}

export const useChatPartsStore = create<ChatPartsState>()(
  persist(
    (set, get) => ({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),
      pendingDeltasBySession: new Map(),
      version: 0,
      layoutVersion: 0,
      userSendVersion: 0,

      upsertPart: (sessionId, part) => set((s) => {
        // Drain any deltas that arrived before this part was in the store
        // (race between the POST send-stream and the GET subscribe-stream
        // delivering interleaved events to the same sink).
        const pendingByPart = s.pendingDeltasBySession.get(sessionId)
        const pending = pendingByPart?.get(part.id)
        let merged: Part = part
        if (pending) {
          const overlay: Record<string, unknown> = { ...part }
          for (const [field, delta] of Object.entries(pending)) {
            const prev = overlay[field]
            overlay[field] = (typeof prev === 'string' ? prev : '') + delta
          }
          merged = overlay as Part
        }

        const existingParts = s.partsBySession.get(sessionId)?.get(merged.messageID)
        const existing = existingParts?.find((p) => p.id === merged.id)
        if (!pending && existing && JSON.stringify(existing) === JSON.stringify(merged)) return {}

        const bySession = new Map(s.partsBySession)
        const byMessage = new Map(bySession.get(sessionId) ?? new Map())
        const index = new Map(s.partIndexBySession.get(sessionId) ?? new Map())
        const list = [...(byMessage.get(merged.messageID) ?? [])]
        const idx = existing && existingParts ? existingParts.indexOf(existing) : list.findIndex((p) => p.id === merged.id)
        const inserted = idx < 0
        if (idx >= 0) {
          list[idx] = merged
        } else {
          list.push(merged)
          index.set(merged.id, { messageId: merged.messageID, idx: list.length - 1 })
        }
        byMessage.set(merged.messageID, list)
        bySession.set(sessionId, byMessage)
        const indexBySession = new Map(s.partIndexBySession)
        indexBySession.set(sessionId, index)

        const patch: Partial<ChatPartsState> = {
          partsBySession: bySession,
          partIndexBySession: indexBySession,
          version: s.version + 1,
          layoutVersion: nextLayoutVersion(s, inserted ? 1 : 0),
        }
        if (pending && pendingByPart) {
          const nextByPart = new Map(pendingByPart)
          nextByPart.delete(merged.id)
          const nextPending = new Map(s.pendingDeltasBySession)
          if (nextByPart.size === 0) nextPending.delete(sessionId)
          else nextPending.set(sessionId, nextByPart)
          patch.pendingDeltasBySession = nextPending
        }
        return patch
      }),

      upsertInfo: (sessionId, info) => set((s) => {
        const bySession = new Map(s.infoBySession)
        const map = new Map(bySession.get(sessionId) ?? new Map())
        const existing = map.get(info.id)
        
        if (!existing) {
          map.set(info.id, info)
        } else {
          // 增量合并，避免 undefined 覆盖掉已有数据（如 modelID, providerID）
          const merged: MessageInfo = { ...existing }
          if (info.role) merged.role = info.role
          if (info.providerID !== undefined) merged.providerID = info.providerID
          if (info.modelID !== undefined) merged.modelID = info.modelID
          if (info.parentID !== undefined) merged.parentID = info.parentID
          if (info.agent !== undefined) merged.agent = info.agent
          if (info.mode !== undefined) merged.mode = info.mode
          if (info.error !== undefined) merged.error = info.error
          if (info.finish !== undefined) merged.finish = info.finish
          if (info.tokens !== undefined) merged.tokens = info.tokens
          
          // 合并时间
          merged.time = { 
            created: info.time?.created ?? existing.time.created,
            completed: info.time?.completed ?? existing.time.completed
          }
          
          map.set(info.id, merged)
        }

        bySession.set(sessionId, map)
        return {
          infoBySession: bySession,
          version: s.version + 1,
          layoutVersion: nextLayoutVersion(s, !existing ? 1 : 0),
        }
      }),

      upsertMany: (sessionId, parts) => set((s) => {
        const partsBySession = new Map(s.partsBySession)
        const byMessage = new Map(partsBySession.get(sessionId) ?? new Map())
        const index = new Map(s.partIndexBySession.get(sessionId) ?? new Map())
        let inserted = false

        for (const part of parts) {
          const list = [...(byMessage.get(part.messageID) ?? [])]
          const existingIdx = list.findIndex((p) => p.id === part.id)
          if (existingIdx >= 0) {
            list[existingIdx] = part
          } else {
            list.push(part)
            index.set(part.id, { messageId: part.messageID, idx: list.length - 1 })
            inserted = true
          }
          byMessage.set(part.messageID, list)
        }

        partsBySession.set(sessionId, byMessage)
        const indexBySession = new Map(s.partIndexBySession)
        indexBySession.set(sessionId, index)
        return {
          partsBySession,
          partIndexBySession: indexBySession,
          version: s.version + 1,
          layoutVersion: nextLayoutVersion(s, inserted ? 1 : 0),
        }
      }),

      replaceSession: (sessionId, list) => set((s) => {
        const partsBySession = new Map(s.partsBySession)
        const infoBySession = new Map(s.infoBySession)
        const partIndexBySession = new Map(s.partIndexBySession)
        const existingInfo = s.infoBySession.get(sessionId)

        const byMessage = new Map<string, Part[]>()
        const byInfo = new Map<string, MessageInfo>()
        const index = new Map<string, { messageId: string; idx: number }>()

        for (const { info, parts } of list) {
          const preservedRenderKey = existingInfo?.get(info.id)?.__renderKey
          byInfo.set(info.id, preservedRenderKey ? { ...info, __renderKey: preservedRenderKey } : info)
          byMessage.set(info.id, [...parts])
          parts.forEach((p, i) => index.set(p.id, { messageId: info.id, idx: i }))
        }

        partsBySession.set(sessionId, byMessage)
        infoBySession.set(sessionId, byInfo)
        partIndexBySession.set(sessionId, index)

        // A full history replace invalidates any stream-in-flight deltas for
        // this session — the snapshot is authoritative.
        const pendingDeltasBySession = new Map(s.pendingDeltasBySession)
        pendingDeltasBySession.delete(sessionId)

        return {
          partsBySession,
          infoBySession,
          partIndexBySession,
          pendingDeltasBySession,
          version: s.version + 1,
          layoutVersion: nextLayoutVersion(s),
        }
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

        const patch: Partial<ChatPartsState> = {
          partsBySession: bySession,
          partIndexBySession: indexBySession,
          version: s.version + 1,
          layoutVersion: nextLayoutVersion(s),
        }
        const pendingByPart = s.pendingDeltasBySession.get(sessionId)
        if (pendingByPart?.has(partId)) {
          const nextByPart = new Map(pendingByPart)
          nextByPart.delete(partId)
          const nextPending = new Map(s.pendingDeltasBySession)
          if (nextByPart.size === 0) nextPending.delete(sessionId)
          else nextPending.set(sessionId, nextByPart)
          patch.pendingDeltasBySession = nextPending
        }
        return patch
      }),

      clearSession: (sessionId) => set((s) => {
        const parts = new Map(s.partsBySession); parts.delete(sessionId)
        const info = new Map(s.infoBySession); info.delete(sessionId)
        const index = new Map(s.partIndexBySession); index.delete(sessionId)
        const streaming = new Set(s.streamingBySession); streaming.delete(sessionId)
        const pending = new Map(s.pendingDeltasBySession); pending.delete(sessionId)
        return {
          partsBySession: parts,
          infoBySession: info,
          partIndexBySession: index,
          streamingBySession: streaming,
          pendingDeltasBySession: pending,
          version: s.version + 1,
          layoutVersion: nextLayoutVersion(s),
        }
      }),

      appendPartDelta: (sessionId, partId, field, delta) => {
        if (!delta) return
        const store = get()
        const existing = store.findPart(sessionId, partId)
        if (existing) {
          const prev = (existing as Record<string, unknown>)[field]
          const next = { ...existing, [field]: (typeof prev === 'string' ? prev : '') + delta } as Part
          store.upsertPart(sessionId, next)
          return
        }
        // Part hasn't landed yet — buffer by (sessionId, partId, field) so
        // the next upsertPart can drain accumulated content into it.
        set((s) => {
          const nextPending = new Map(s.pendingDeltasBySession)
          const byPart = new Map(nextPending.get(sessionId) ?? new Map<string, Record<string, string>>())
          const fields = { ...(byPart.get(partId) ?? {}) }
          fields[field] = (fields[field] ?? '') + delta
          byPart.set(partId, fields)
          nextPending.set(sessionId, byPart)
          return { pendingDeltasBySession: nextPending, version: s.version + 1 }
        })
      },

      setStreaming: (sessionId, on) => {
        // BUG-0046: streaming 的权威源是 OpenCode `SessionStatus`（经
        // GET /api/sessions/{id}/status 透传），mount 时 reconcile。
        // 不再持久化到 sessionStorage —— 持久化曾在 fetch abort 场景下被
        // 错误清空（[[BUG-0046]]），且会让 shouldSkipReplace 卡死历史加载。
        set((s) => {
          const has = s.streamingBySession.has(sessionId)
          if (on === has) return {}
          const next = new Set(s.streamingBySession)
          if (on) next.add(sessionId)
          else next.delete(sessionId)
          return { streamingBySession: next, version: s.version + 1, layoutVersion: nextLayoutVersion(s) }
        })
      },

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
        const createdAt = Date.now()
        set((s) => {
          const infoBySession = new Map(s.infoBySession)
          const partsBySession = new Map(s.partsBySession)
          const partIndexBySession = new Map(s.partIndexBySession)

          const infoMap = new Map(infoBySession.get(sessionId) ?? new Map())
          infoMap.set(pendingId, {
            id: pendingId,
            role: 'user',
            sessionID: sessionId,
            time: { created: createdAt },
            __pending: true,
            __renderKey: pendingId,
          })
          infoBySession.set(sessionId, infoMap)

          const partsMap = new Map(partsBySession.get(sessionId) ?? new Map())
          const pendingPart = {
            type: 'text',
            id: partId,
            sessionID: sessionId,
            messageID: pendingId,
            text,
            metadata: {},
          } as Part
          partsMap.set(pendingId, [pendingPart])
          partsBySession.set(sessionId, partsMap)

          const indexMap = new Map(partIndexBySession.get(sessionId) ?? new Map())
          indexMap.set(partId, { messageId: pendingId, idx: 0 })
          partIndexBySession.set(sessionId, indexMap)

          return {
            infoBySession,
            partsBySession,
            partIndexBySession,
            version: s.version + 1,
            layoutVersion: nextLayoutVersion(s),
            userSendVersion: (s.userSendVersion ?? 0) + 1,
          }
        })
        return pendingId
      },

      promotePendingUser: (sessionId, pendingId, realId) => set((s) => {
        const oldInfoMap = s.infoBySession.get(sessionId)
        if (!oldInfoMap || !oldInfoMap.has(pendingId)) return {}
        const pendingInfo = oldInfoMap.get(pendingId)
        if (!pendingInfo) return {}
        const existingRealInfo = oldInfoMap.get(realId)

        const promotedInfo: MessageInfo = {
          ...pendingInfo,
          ...existingRealInfo,
          id: realId,
          __renderKey: pendingInfo.__renderKey ?? existingRealInfo?.__renderKey ?? pendingId,
        }
        delete promotedInfo.__pending
        delete promotedInfo.__failed
        delete promotedInfo.__failReason
        delete promotedInfo.__retrying

        // Rebuild the info map to preserve insertion order
        const nextInfoMap = new Map<string, MessageInfo>()
        let insertedRealInfo = false
        for (const [id, info] of oldInfoMap.entries()) {
          if (id === pendingId) {
            nextInfoMap.set(realId, promotedInfo)
            insertedRealInfo = true
            continue
          }
          if (id === realId) continue
          nextInfoMap.set(id, info)
        }
        if (!insertedRealInfo) nextInfoMap.set(realId, promotedInfo)

        // Rebuild the parts map to preserve insertion order
        const oldPartsMap = s.partsBySession.get(sessionId)
        const nextPartsMap = new Map<string, Part[]>()
        let insertedRealParts = false
        const promotedParts = oldPartsMap?.get(realId)
          ?? oldPartsMap?.get(pendingId)?.map((p) => ({ ...p, messageID: realId }))
          ?? []
        if (oldPartsMap) {
          for (const [mid, parts] of oldPartsMap.entries()) {
            if (mid === pendingId) {
              nextPartsMap.set(realId, promotedParts)
              insertedRealParts = true
              continue
            }
            if (mid === realId) continue
            nextPartsMap.set(mid, parts)
          }
        }
        if (!insertedRealParts && promotedParts.length > 0) nextPartsMap.set(realId, promotedParts)

        const index = new Map(s.partIndexBySession.get(sessionId) ?? new Map())
        for (const [pid, entry] of index.entries()) {
          if (entry.messageId === pendingId) index.set(pid, { ...entry, messageId: realId })
        }

        const infoBySession = new Map(s.infoBySession); infoBySession.set(sessionId, nextInfoMap)
        const partsBySession = new Map(s.partsBySession); partsBySession.set(sessionId, nextPartsMap)
        const partIndexBySession = new Map(s.partIndexBySession); partIndexBySession.set(sessionId, index)
        return {
          infoBySession,
          partsBySession,
          partIndexBySession,
          version: s.version + 1,
          layoutVersion: nextLayoutVersion(s),
        }
      }),

      markPendingUserFailed: (sessionId, pendingId, reason) => set((s) => {
        const byInfo = new Map(s.infoBySession.get(sessionId) ?? new Map<string, MessageInfo>())
        const info = byInfo.get(pendingId)
        if (!info) return {}
        byInfo.set(pendingId, { ...info, __failed: true, __failReason: reason, __retrying: false })
        const infoBySession = new Map(s.infoBySession); infoBySession.set(sessionId, byInfo)
        return { infoBySession, version: s.version + 1, layoutVersion: nextLayoutVersion(s) }
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
        return {
          infoBySession,
          partsBySession,
          partIndexBySession,
          version: s.version + 1,
          layoutVersion: nextLayoutVersion(s),
        }
      }),

      markSessionTurnCompleted: (sessionId) => set((s) => {
        const byInfo = new Map(s.infoBySession.get(sessionId) ?? new Map())
        let changed = false
        for (const [id, info] of byInfo.entries()) {
          if (info.role === 'assistant' && typeof info.time.completed !== 'number') {
            byInfo.set(id, { ...info, time: { ...info.time, completed: Date.now() } })
            changed = true
          }
        }
        if (!changed) return {}
        const infoBySession = new Map(s.infoBySession); infoBySession.set(sessionId, byInfo)
        return { infoBySession, version: s.version + 1 }
      }),
    }),
    {
      name: 'data-talk.chat-parts',
      storage: createJSONStorage(() => sessionStorage),
      // BUG-0046: streamingBySession 不再持久化（权威源迁到服务端
      // GET /api/sessions/{id}/status）。partialize 返回空对象使 persist
      // 不向 sessionStorage 写入任何 store 字段；保留 persist 包装本身以
      // 维持中间件 API 兼容（其它代码可能依赖 .persist 句柄）。
      partialize: () => ({}) as unknown as ChatPartsState,
      merge: (_persisted, current) => current,
    },
  ),
)
