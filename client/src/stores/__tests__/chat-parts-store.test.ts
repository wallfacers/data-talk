import { describe, it, expect, beforeEach } from 'vitest'
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
})
