import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useChatPartsStore } from '../chat-parts-store'
import type { MessageInfo, Part } from '@/services/channel/types'

describe('chat-parts-store', () => {
  beforeEach(() => {
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),
    })
  })

  it('upsertInfo writes info to infoBySession', () => {
    const info: MessageInfo = {
      id: 'msg_1', role: 'user', sessionID: 'ses_a',
      time: { created: 1000 },
    }
    useChatPartsStore.getState().upsertInfo('ses_a', info)
    const map = useChatPartsStore.getState().infoBySession.get('ses_a')
    expect(map?.get('msg_1')).toEqual(info)
  })

  it('upsertPart uses OpenCode-native messageID / id', () => {
    const part: Part = {
      type: 'text', id: 'prt_1', sessionID: 'ses_a', messageID: 'msg_1',
      text: 'hello', metadata: {},
    } as Part
    useChatPartsStore.getState().upsertPart('ses_a', part)
    const entry = useChatPartsStore.getState().findPart('ses_a', 'prt_1')
    expect((entry as any)?.text).toBe('hello')
  })

  it('replaceSession atomically replaces all parts and info in one set', () => {
    const store = useChatPartsStore.getState()
    const renderSpy = vi.fn()
    const unsub = useChatPartsStore.subscribe(renderSpy)

    store.replaceSession('ses_a', [
      {
        info: { id: 'msg_1', role: 'user', sessionID: 'ses_a', time: { created: 1000 } },
        parts: [
          { type: 'text', id: 'prt_1', sessionID: 'ses_a', messageID: 'msg_1', text: 'hi', metadata: {} } as Part,
        ],
      },
      {
        info: { id: 'msg_2', role: 'assistant', sessionID: 'ses_a', time: { created: 2000 } },
        parts: [
          { type: 'text', id: 'prt_2', sessionID: 'ses_a', messageID: 'msg_2', text: 'hello', metadata: {} } as Part,
        ],
      },
    ])

    expect(useChatPartsStore.getState().infoBySession.get('ses_a')?.size).toBe(2)
    expect(useChatPartsStore.getState().partsBySession.get('ses_a')?.size).toBe(2)
    expect(renderSpy).toHaveBeenCalledTimes(1)
    unsub()
  })

  it('upsertPendingUser writes pending info + text part, returns pendingId', () => {
    const store = useChatPartsStore.getState()
    const pendingId = store.upsertPendingUser('ses_a', 'hello')
    expect(pendingId).toMatch(/^pending_/)

    const info = useChatPartsStore.getState().infoBySession.get('ses_a')?.get(pendingId)
    expect(info?.__pending).toBe(true)
    expect(info?.role).toBe('user')

    const parts = useChatPartsStore.getState().partsBySession.get('ses_a')?.get(pendingId)
    expect(parts).toHaveLength(1)
    expect((parts?.[0] as any).text).toBe('hello')
  })

  it('promotePendingUser renames pendingId → realId preserving content', () => {
    const store = useChatPartsStore.getState()
    const pendingId = store.upsertPendingUser('ses_a', 'hello')
    store.promotePendingUser('ses_a', pendingId, 'msg_real_1')

    const byInfo = useChatPartsStore.getState().infoBySession.get('ses_a')!
    expect(byInfo.get(pendingId)).toBeUndefined()
    expect(byInfo.get('msg_real_1')?.__pending).toBeFalsy()

    const parts = useChatPartsStore.getState().partsBySession.get('ses_a')?.get('msg_real_1')
    expect((parts?.[0] as any).text).toBe('hello')
  })

  it('markPendingUserFailed sets __failed flag', () => {
    const store = useChatPartsStore.getState()
    const pendingId = store.upsertPendingUser('ses_a', 'hello')
    store.markPendingUserFailed('ses_a', pendingId, 'network error')
    const info = useChatPartsStore.getState().infoBySession.get('ses_a')?.get(pendingId)
    expect(info?.__failed).toBe(true)
    expect(info?.__failReason).toBe('network error')
  })

  it('removePendingUser removes info and parts', () => {
    const store = useChatPartsStore.getState()
    const pendingId = store.upsertPendingUser('ses_a', 'hello')
    store.removePendingUser('ses_a', pendingId)
    expect(useChatPartsStore.getState().infoBySession.get('ses_a')?.get(pendingId)).toBeUndefined()
    expect(useChatPartsStore.getState().partsBySession.get('ses_a')?.get(pendingId)).toBeUndefined()
  })

  describe('streamingBySession', () => {
    it('setStreaming(on=true) marks the session as streaming', () => {
      useChatPartsStore.getState().setStreaming('ses_a', true)
      expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(true)
    })

    it('setStreaming(on=false) clears the session', () => {
      useChatPartsStore.getState().setStreaming('ses_a', true)
      useChatPartsStore.getState().setStreaming('ses_a', false)
      expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(false)
    })

    it('streaming flags for different sessions are independent', () => {
      const { setStreaming } = useChatPartsStore.getState()
      setStreaming('ses_a', true)
      setStreaming('ses_b', true)
      expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(true)
      expect(useChatPartsStore.getState().streamingBySession.has('ses_b')).toBe(true)
      setStreaming('ses_a', false)
      expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(false)
      expect(useChatPartsStore.getState().streamingBySession.has('ses_b')).toBe(true)
    })

    it('clearSession also removes the streaming flag', () => {
      useChatPartsStore.getState().setStreaming('ses_a', true)
      useChatPartsStore.getState().clearSession('ses_a')
      expect(useChatPartsStore.getState().streamingBySession.has('ses_a')).toBe(false)
    })
  })
})
