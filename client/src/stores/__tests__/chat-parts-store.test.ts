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
      pendingDeltasBySession: new Map(),
      version: 0,
      layoutVersion: 0,
      userSendVersion: 0,
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

  it('upsertPendingUser updates info and part atomically in one store publish', () => {
    const store = useChatPartsStore.getState()
    const renderSpy = vi.fn()
    const unsub = useChatPartsStore.subscribe(renderSpy)

    const pendingId = store.upsertPendingUser('ses_a', 'hello')

    expect(renderSpy).toHaveBeenCalledTimes(1)
    expect(useChatPartsStore.getState().infoBySession.get('ses_a')?.get(pendingId)?.__pending).toBe(true)
    expect(useChatPartsStore.getState().partsBySession.get('ses_a')?.get(pendingId)?.[0]).toMatchObject({
      type: 'text',
      text: 'hello',
      messageID: pendingId,
    })
    unsub()
  })

  it('promotePendingUser renames pendingId → realId and keeps pending parts visible until SSE swaps them in place', () => {
    const store = useChatPartsStore.getState()
    const pendingId = store.upsertPendingUser('ses_a', 'hello')
    store.promotePendingUser('ses_a', pendingId, 'msg_real_1')

    const byInfo = useChatPartsStore.getState().infoBySession.get('ses_a')!
    expect(byInfo.get(pendingId)).toBeUndefined()
    expect(byInfo.get('msg_real_1')?.__pending).toBeFalsy()

    // Pending parts are kept under the real messageID so the bubble keeps
    // rendering text without an empty frame between promote and SSE echo.
    const promoted = useChatPartsStore.getState().partsBySession.get('ses_a')?.get('msg_real_1')
    expect(promoted).toHaveLength(1)
    expect((promoted?.[0] as any).text).toBe('hello')
    expect(promoted?.[0]?.id).toMatch(/^pending_prt_/)
    expect(promoted?.[0]?.messageID).toBe('msg_real_1')

    // SSE real text part replaces the pending text in place — no duplicate.
    store.upsertPart('ses_a', {
      type: 'text',
      id: 'prt_real_1',
      sessionID: 'ses_a',
      messageID: 'msg_real_1',
      text: 'hello',
      metadata: {},
    })
    const hydrated = useChatPartsStore.getState().partsBySession.get('ses_a')?.get('msg_real_1')
    expect(hydrated).toHaveLength(1)
    expect(hydrated?.[0]?.id).toBe('prt_real_1')
    expect((hydrated?.[0] as any).text).toBe('hello')
    // partIndex tracks the real id now, not the stale pending one.
    expect(useChatPartsStore.getState().findPart('ses_a', 'prt_real_1')).not.toBeNull()
  })

  it('image-with-dataUri path: pending file_upload (image/*) is replaced by SSE-echoed native FilePart, not duplicated', () => {
    const store = useChatPartsStore.getState()
    const pendingFileParts = [
      {
        type: 'file_upload',
        id: 'temp_id_ignored',
        sessionID: 'ses_a',
        messageID: '',
        fileId: 'file_img',
        filename: 'shot.png',
        mimeType: 'image/png',
        sizeBytes: 200,
        analysis: {},
        url: 'data:image/png;base64,iVBORw0KGgo=',
      },
    ] as Part[]

    const pendingId = store.upsertPendingUser('ses_a', 'see image', pendingFileParts)
    store.promotePendingUser('ses_a', pendingId, 'msg_real_img')

    // OpenCode echoes the image back as a native FilePart with type='file' +
    // mime='image/*' (ChannelService.partForWire rewrote it on the way in).
    store.upsertPart('ses_a', {
      type: 'file',
      id: 'prt_real_img',
      sessionID: 'ses_a',
      messageID: 'msg_real_img',
      mime: 'image/png',
      filename: 'shot.png',
      url: 'data:image/png;base64,iVBORw0KGgo=',
    } as unknown as Part)

    const hydrated = useChatPartsStore.getState().partsBySession.get('ses_a')?.get('msg_real_img')
    // 1 text + 1 image attachment — no duplicate from the cross-type echo.
    expect(hydrated).toHaveLength(2)
    const attachmentParts = hydrated?.filter((p) => p.type === 'file_upload' || p.type === 'file') ?? []
    expect(attachmentParts).toHaveLength(1)
    expect(attachmentParts[0]?.type).toBe('file')
    expect(attachmentParts[0]?.id).toBe('prt_real_img')
  })

  it('promotePendingUser preserves pending file_upload parts; SSE echo replaces by fileId', () => {
    const store = useChatPartsStore.getState()
    const pendingFileParts = [
      {
        type: 'file_upload',
        id: 'temp_id_ignored_1',
        sessionID: 'ses_a',
        messageID: '',
        fileId: 'file_a',
        filename: 'data.csv',
        mimeType: 'text/csv',
        sizeBytes: 10,
        analysis: {},
      },
      {
        type: 'file_upload',
        id: 'temp_id_ignored_2',
        sessionID: 'ses_a',
        messageID: '',
        fileId: 'file_b',
        filename: 'pic.png',
        mimeType: 'image/png',
        sizeBytes: 20,
        analysis: {},
      },
    ] as Part[]

    const pendingId = store.upsertPendingUser('ses_a', 'see attached', pendingFileParts)
    store.promotePendingUser('ses_a', pendingId, 'msg_real_2')

    const afterPromote = useChatPartsStore.getState().partsBySession.get('ses_a')?.get('msg_real_2')
    expect(afterPromote).toHaveLength(3)
    expect(afterPromote?.every((p) => p.id.startsWith('pending_prt_'))).toBe(true)
    expect(afterPromote?.every((p) => p.messageID === 'msg_real_2')).toBe(true)

    // SSE echoes the real file_upload parts back — match by fileId, replace
    // in place, no duplicates.
    store.upsertPart('ses_a', {
      type: 'file_upload',
      id: 'prt_real_file_b',
      sessionID: 'ses_a',
      messageID: 'msg_real_2',
      fileId: 'file_b',
      filename: 'pic.png',
      mimeType: 'image/png',
      sizeBytes: 20,
      analysis: {},
    } as Part)
    store.upsertPart('ses_a', {
      type: 'file_upload',
      id: 'prt_real_file_a',
      sessionID: 'ses_a',
      messageID: 'msg_real_2',
      fileId: 'file_a',
      filename: 'data.csv',
      mimeType: 'text/csv',
      sizeBytes: 10,
      analysis: {},
    } as Part)

    const hydrated = useChatPartsStore.getState().partsBySession.get('ses_a')?.get('msg_real_2')
    expect(hydrated).toHaveLength(3)
    const fileParts = hydrated?.filter((p) => p.type === 'file_upload') ?? []
    expect(fileParts).toHaveLength(2)
    expect(fileParts.map((p) => p.id).sort()).toEqual(['prt_real_file_a', 'prt_real_file_b'])
    // Original insertion order is preserved (file_a in slot 1, file_b in slot 2).
    expect(fileParts[0]?.id).toBe('prt_real_file_a')
    expect(fileParts[1]?.id).toBe('prt_real_file_b')
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

  describe('appendPartDelta (part-not-yet-arrived buffering)', () => {
    it('applies delta directly when the part already exists', () => {
      const store = useChatPartsStore.getState()
      store.upsertPart('ses_a', {
        type: 'text', id: 'prt_1', sessionID: 'ses_a', messageID: 'msg_1',
        text: 'hi ', metadata: {},
      } as Part)
      const before = useChatPartsStore.getState()
      store.appendPartDelta('ses_a', 'prt_1', 'text', 'there')
      const after = useChatPartsStore.getState()
      const part = after.findPart('ses_a', 'prt_1')
      expect((part as any)?.text).toBe('hi there')
      expect(after.version).toBe(before.version + 1)
      expect(after.layoutVersion).toBe(before.layoutVersion)
    })

    it('does not increment layoutVersion for pure reasoning text deltas on an existing part', () => {
      const store = useChatPartsStore.getState()
      store.upsertPart('ses_a', {
        type: 'reasoning', id: 'prt_reason', sessionID: 'ses_a', messageID: 'msg_1',
        text: '', thinking: 'step 1', metadata: {},
      } as unknown as Part)
      const before = useChatPartsStore.getState()

      store.appendPartDelta('ses_a', 'prt_reason', 'thinking', '\nstep 2')

      const after = useChatPartsStore.getState()
      expect((after.findPart('ses_a', 'prt_reason') as any)?.thinking).toBe('step 1\nstep 2')
      expect(after.version).toBe(before.version + 1)
      expect(after.layoutVersion).toBe(before.layoutVersion)
    })

    it('buffers delta when part not yet in store, drains on next upsertPart', () => {
      const store = useChatPartsStore.getState()
      store.appendPartDelta('ses_a', 'prt_late', 'text', '你')
      store.appendPartDelta('ses_a', 'prt_late', 'text', '好')
      // Part hasn't arrived yet — delta stays buffered, nothing renderable.
      expect(useChatPartsStore.getState().findPart('ses_a', 'prt_late')).toBeNull()

      store.upsertPart('ses_a', {
        type: 'text', id: 'prt_late', sessionID: 'ses_a', messageID: 'msg_1',
        text: '', metadata: {},
      } as Part)

      const part = useChatPartsStore.getState().findPart('ses_a', 'prt_late')
      expect((part as any)?.text).toBe('你好')
      // Buffer cleared after drain so the same partId doesn't double-apply
      // on a subsequent part.updated echo.
      expect(useChatPartsStore.getState().pendingDeltasBySession.get('ses_a')).toBeUndefined()
    })

    it('buffers deltas for multiple fields on the same pending part', () => {
      const store = useChatPartsStore.getState()
      store.appendPartDelta('ses_a', 'prt_r1', 'thinking', 'reasoning...')
      store.appendPartDelta('ses_a', 'prt_r1', 'text', 'answer')

      store.upsertPart('ses_a', {
        type: 'reasoning', id: 'prt_r1', sessionID: 'ses_a', messageID: 'msg_1',
        text: '', thinking: '', metadata: {},
      } as unknown as Part)

      const part = useChatPartsStore.getState().findPart('ses_a', 'prt_r1') as any
      expect(part?.thinking).toBe('reasoning...')
      expect(part?.text).toBe('answer')
    })

    it('clearSession drops buffered deltas for that session', () => {
      const store = useChatPartsStore.getState()
      store.appendPartDelta('ses_a', 'prt_x', 'text', 'lost')
      store.clearSession('ses_a')
      expect(useChatPartsStore.getState().pendingDeltasBySession.get('ses_a')).toBeUndefined()
    })

    it('replaceSession drops buffered deltas so history snapshot is authoritative', () => {
      const store = useChatPartsStore.getState()
      store.appendPartDelta('ses_a', 'prt_x', 'text', 'stale')
      store.replaceSession('ses_a', [])
      expect(useChatPartsStore.getState().pendingDeltasBySession.get('ses_a')).toBeUndefined()
    })

    it('empty delta is a no-op (no buffer growth)', () => {
      const store = useChatPartsStore.getState()
      store.appendPartDelta('ses_a', 'prt_x', 'text', '')
      expect(useChatPartsStore.getState().pendingDeltasBySession.get('ses_a')).toBeUndefined()
    })
  })

  describe('layoutVersion', () => {
    it('increments when a part is inserted for the first time', () => {
      const store = useChatPartsStore.getState()
      const before = store.layoutVersion

      store.upsertPart('ses_a', {
        type: 'text', id: 'prt_1', sessionID: 'ses_a', messageID: 'msg_1',
        text: 'hello', metadata: {},
      } as Part)

      expect(useChatPartsStore.getState().layoutVersion).toBe(before + 1)
    })

    it('increments when streaming starts and stops', () => {
      const store = useChatPartsStore.getState()

      store.setStreaming('ses_a', true)
      expect(useChatPartsStore.getState().layoutVersion).toBe(1)

      store.setStreaming('ses_a', false)
      expect(useChatPartsStore.getState().layoutVersion).toBe(2)
    })

    it('increments when a pending user is inserted, failed, promoted, and removed', () => {
      const store = useChatPartsStore.getState()

      const pendingId = store.upsertPendingUser('ses_a', 'hello')
      expect(useChatPartsStore.getState().layoutVersion).toBe(1)

      store.markPendingUserFailed('ses_a', pendingId, 'network error')
      expect(useChatPartsStore.getState().layoutVersion).toBe(2)

      store.promotePendingUser('ses_a', pendingId, 'msg_real_1')
      expect(useChatPartsStore.getState().layoutVersion).toBe(3)

      store.removePendingUser('ses_a', 'msg_real_1')
      expect(useChatPartsStore.getState().layoutVersion).toBe(4)
    })
  })

  describe('userSendVersion', () => {
    it('bumps only when the user submits a pending message', () => {
      const store = useChatPartsStore.getState()
      expect(useChatPartsStore.getState().userSendVersion).toBe(0)

      store.upsertPendingUser('ses_a', 'hello')
      expect(useChatPartsStore.getState().userSendVersion).toBe(1)

      store.upsertPendingUser('ses_a', 'second')
      expect(useChatPartsStore.getState().userSendVersion).toBe(2)
    })

    it('does not change on assistant part inserts, deltas, or streaming toggles', () => {
      const store = useChatPartsStore.getState()
      const baseline = useChatPartsStore.getState().userSendVersion

      store.upsertPart('ses_a', {
        type: 'text', id: 'prt_1', sessionID: 'ses_a', messageID: 'msg_1',
        text: 'hi', metadata: {},
      } as Part)
      store.appendPartDelta('ses_a', 'prt_1', 'text', ' more')
      store.setStreaming('ses_a', true)
      store.setStreaming('ses_a', false)

      expect(useChatPartsStore.getState().userSendVersion).toBe(baseline)
    })
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

// BUG-0046: streamingBySession 的权威源迁到 OpenCode 服务端
// (GET /api/sessions/{id}/status)。store 不再把它写入 sessionStorage —— 旧的
// 持久化路径在 fetch abort 场景下被错误清空，并让 shouldSkipReplace 永久卡死
// 历史加载。setStreaming 仍维护 in-memory state，但 reload 后需要由
// useSessionSubscribe 调用 fetchSessionStatus 来重新写入 true。
describe('streamingBySession (BUG-0046 — server-authoritative, no persistence)', () => {
  beforeEach(() => {
    useChatPartsStore.setState({
      partsBySession: new Map(),
      infoBySession: new Map(),
      partIndexBySession: new Map(),
      streamingBySession: new Set<string>(),
      pendingDeltasBySession: new Map(),
      version: 0,
      layoutVersion: 0,
      userSendVersion: 0,
    })
    sessionStorage.clear()
  })

  it('setStreaming does NOT write streamingBySession to sessionStorage', () => {
    useChatPartsStore.getState().setStreaming('ses_a', true)
    const raw = sessionStorage.getItem('data-talk.chat-parts')
    // persist 仍会写一个空 state 包装（schema 兼容），但 streamingBySession 必须缺席
    if (raw) {
      const parsed = JSON.parse(raw)
      expect(parsed.state?.streamingBySession).toBeUndefined()
    }
  })

  it('does NOT persist the big partsBySession / infoBySession maps', () => {
    useChatPartsStore.getState().upsertInfo('ses_a', {
      id: 'm1', role: 'user', sessionID: 'ses_a', time: { created: 1 },
    })
    const raw = sessionStorage.getItem('data-talk.chat-parts')
    if (raw) {
      const parsed = JSON.parse(raw)
      expect(parsed.state?.partsBySession).toBeUndefined()
      expect(parsed.state?.infoBySession).toBeUndefined()
    }
  })

  it('reload does NOT restore streamingBySession (no persistence)', async () => {
    useChatPartsStore.getState().setStreaming('ses_reload', true)

    // Simulate page reload: drop module cache so persist middleware
    // re-hydrates from (empty / non-streaming) sessionStorage.
    vi.resetModules()
    const mod = await import('../chat-parts-store')
    const reloaded = mod.useChatPartsStore

    const streaming = reloaded.getState().streamingBySession
    expect(streaming).toBeInstanceOf(Set)
    expect(streaming.has('ses_reload')).toBe(false)
  })
})
