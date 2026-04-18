import { describe, it, expect, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useSessionTurns } from '../use-session-turns'

describe('useSessionTurns', () => {
  beforeEach(() => {
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
    })
  })

  it('splits on user messages', () => {
    const store = useChatPartsStore.getState()
    store.upsertInfo('s', { id: 'u1', role: 'user', sessionID: 's', time: { created: 1 } })
    store.upsertInfo('s', { id: 'a1', role: 'assistant', sessionID: 's', time: { created: 2 } })
    store.upsertInfo('s', { id: 'u2', role: 'user', sessionID: 's', time: { created: 3 } })
    store.upsertInfo('s', { id: 'a2', role: 'assistant', sessionID: 's', time: { created: 4 } })

    const { result } = renderHook(() => useSessionTurns('s'))
    expect(result.current).toHaveLength(2)
    expect(result.current[0].userMessageId).toBe('u1')
    expect(result.current[0].assistantMessageIds).toEqual(['a1'])
    expect(result.current[1].userMessageId).toBe('u2')
    expect(result.current[1].assistantMessageIds).toEqual(['a2'])
  })

  it('orphan assistant goes into user-less turn', () => {
    const store = useChatPartsStore.getState()
    store.upsertInfo('s', { id: 'a1', role: 'assistant', sessionID: 's', time: { created: 1 } })
    const { result } = renderHook(() => useSessionTurns('s'))
    expect(result.current).toHaveLength(1)
    expect(result.current[0].userMessageId).toBeUndefined()
    expect(result.current[0].assistantMessageIds).toEqual(['a1'])
  })
})
