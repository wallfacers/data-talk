import type { StreamEvent, Part } from './types'

export type Artifact = {
  id: string
  version: number
  kind: 'table' | 'chart'
  sessionId?: string
  producedBy?: string
  supersedesId?: string
  supersedesVersion?: number
  pinned?: boolean
  payload?: unknown
  createdAt?: number
  originMessageId?: string
  originPartId?: string
}

export type ReducerState = {
  messages: Map<string, { id: string; role: string; createdAt: number }>
  parts: Map<string, Part[]>
  artifacts: Map<string, Artifact>
  pendingClientCalls: Map<string, { actionId: string; input: unknown; timeoutMs: number }>
}

export function reduceEvent(state: ReducerState, evt: StreamEvent): ReducerState {
  switch (evt.event) {
    case 'message.created': {
      const m = (evt.data as any).message
      const next = new Map(state.messages)
      next.set(m.id, { id: m.id, role: m.role, createdAt: m.createdAt })
      return { ...state, messages: next }
    }
    case 'message.part.created': {
      const part = (evt.data as any).part as Part
      const nextParts = new Map(state.parts)
      const list = nextParts.get(part.messageID) ?? []
      nextParts.set(part.messageID, [...list, part])
      return { ...state, parts: nextParts }
    }
    case 'message.part.updated': {
      const part = (evt.data as any).part as Part
      const nextParts = new Map(state.parts)
      const list = (nextParts.get(part.messageID) ?? []).map(p => p.id === part.id ? part : p)
      if (!list.some(p => p.id === part.id)) list.push(part)
      nextParts.set(part.messageID, list)
      return { ...state, parts: nextParts }
    }
    case 'message.part.delta': {
      const { partId, field, delta } = evt.data as any
      const nextParts = new Map(state.parts)
      for (const [mid, list] of nextParts) {
        const idx = list.findIndex(p => p.id === partId)
        if (idx >= 0) {
          const before = list[idx] as any
          const updated = { ...before, [field]: (before[field] ?? '') + delta }
          const newList = [...list]
          newList[idx] = updated
          nextParts.set(mid, newList)
          break
        }
      }
      return { ...state, parts: nextParts }
    }
    case 'message.part.removed': {
      const { partId } = evt.data as any
      const nextParts = new Map(state.parts)
      for (const [mid, list] of nextParts) {
        nextParts.set(mid, list.filter(p => p.id !== partId))
      }
      return { ...state, parts: nextParts }
    }
    case 'ontology.updated': {
      const { objectType, id, op, patch } = evt.data as any
      if (objectType !== 'datatalk.artifact') return state
      const nextArts = new Map(state.artifacts)
      if (op === 'delete') { nextArts.delete(id); return { ...state, artifacts: nextArts } }
      const prev = nextArts.get(id)
      nextArts.set(id, { ...(prev ?? { id, version: 1, kind: 'table' }), ...patch, id })
      return { ...state, artifacts: nextArts }
    }
    case 'artifact.snapshot': {
      const { artifacts } = evt.data as any
      const next = new Map(state.artifacts)
      for (const a of artifacts) next.set(a.id, a)
      return { ...state, artifacts: next }
    }
    case 'action.invoke': {
      const { callId, actionId, input, timeoutMs } = evt.data as any
      const next = new Map(state.pendingClientCalls)
      next.set(callId, { actionId, input, timeoutMs })
      return { ...state, pendingClientCalls: next }
    }
    case 'action.cancel': {
      const { callId } = evt.data as any
      const next = new Map(state.pendingClientCalls)
      next.delete(callId)
      return { ...state, pendingClientCalls: next }
    }
    default: return state
  }
}
