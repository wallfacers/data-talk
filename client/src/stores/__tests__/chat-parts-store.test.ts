import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useChatPartsStore } from '../chat-parts-store'
import type { MessageInfo, Part } from '@/services/channel/types'

describe('chat-parts-store', () => {
  beforeEach(() => {
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
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
})
