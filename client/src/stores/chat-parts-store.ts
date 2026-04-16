import { create } from 'zustand'
import type { Part } from '@/services/channel/types'

type ChatPartsState = {
  partsByMessage: Map<string, Part[]>
  upsertPart: (part: Part) => void
  removePart: (messageId: string, partId: string) => void
  clear: () => void
}

export const useChatPartsStore = create<ChatPartsState>((set) => ({
  partsByMessage: new Map(),
  upsertPart: (part) => set(s => {
    const next = new Map(s.partsByMessage)
    const list = [...(next.get(part.messageID) ?? [])]
    const idx = list.findIndex(p => p.id === part.id)
    if (idx >= 0) list[idx] = part
    else list.push(part)
    next.set(part.messageID, list)
    return { partsByMessage: next }
  }),
  removePart: (messageId, partId) => set(s => {
    const next = new Map(s.partsByMessage)
    next.set(messageId, (next.get(messageId) ?? []).filter(p => p.id !== partId))
    return { partsByMessage: next }
  }),
  clear: () => set({ partsByMessage: new Map() }),
}))
