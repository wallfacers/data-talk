import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { SessionDataContext } from '@/services/api/session-data-context'
import { useSessionStore } from './session-store'

describe('session-store', () => {
  beforeEach(() => {
    useSessionStore.setState({
      activeSessionId: null,
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      dataContextBySession: new Map(),
      pendingPrompt: null,
      composerRestoreDraft: null,
      composerDrafts: {},
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
    } as unknown as Record<string, unknown>)
  })

  it('setSessionMode is idempotent for the same mode', () => {
    const store = useSessionStore.getState()

    store.setSessionMode('s1', 'SPLIT')
    const firstMap = useSessionStore.getState().modeBySession

    store.setSessionMode('s1', 'SPLIT')

    expect(useSessionStore.getState().modeBySession).toBe(firstMap)
  })

  it('setSessionDataContext is idempotent for the same context payload', () => {
    const store = useSessionStore.getState()
    const context: SessionDataContext = {
      sessionId: 's1',
      connectionId: 'conn-1',
      connectionNameSnapshot: 'orders-prod',
      database: 'orders',
      schema: 'public',
      selectedLevel: 'schema',
      updatedAt: 123,
    }

    store.setSessionDataContext(context)
    const firstMap = useSessionStore.getState().dataContextBySession

    store.setSessionDataContext({ ...context })

    expect(useSessionStore.getState().dataContextBySession).toBe(firstMap)
  })

  it('setComposerRestoreDraft stores and clears transient composer recovery text', () => {
    const store = useSessionStore.getState()

    store.setComposerRestoreDraft({ sessionId: 's1', text: '你好' })
    expect(useSessionStore.getState().composerRestoreDraft).toEqual({ sessionId: 's1', text: '你好' })

    store.setComposerRestoreDraft(null)
    expect(useSessionStore.getState().composerRestoreDraft).toBeNull()
  })
})

// Regression: BUG-NEW-2 — 回车发送后立刻 CTRL+R 刷新，composer 又出现刚发送的文本。
// 根因：setComposerDraft 同步写 localStorage 时把草稿挂在 `data.composerDrafts`
// 根节点，而 Zustand persist hydrate 读的是 `data.state.composerDrafts`。
// 同步写完全失效，等价于没写。修复必须用 persist 规范 schema：
// `{ state: { composerDrafts: {...} }, version }`。
describe('composerDrafts persistence (BUG-NEW-2)', () => {
  beforeEach(() => {
    useSessionStore.setState({
      activeSessionId: null,
      modeBySession: new Map(),
      hasEverSentBySession: new Map(),
      dataContextBySession: new Map(),
      pendingPrompt: null,
      composerRestoreDraft: null,
      composerDrafts: {},
      pendingModelPrompt: false,
      pendingConnectionPrompt: false,
      pendingActionAfterConnectionPick: null,
    } as unknown as Record<string, unknown>)
    localStorage.clear()
  })

  it('setComposerDraft writes to data.state.composerDrafts (Zustand persist schema)', () => {
    useSessionStore.getState().setComposerDraft('key1', 'hello')
    const raw = localStorage.getItem('data-talk.session')
    expect(raw).toBeTruthy()
    const parsed = JSON.parse(raw!)
    // 必须写在 state 包装下，不能挂在根节点
    expect(parsed.state?.composerDrafts?.key1).toBe('hello')
    expect((parsed as Record<string, unknown>).composerDrafts).toBeUndefined()
  })

  it('clearing draft synchronously persists empty string (regression: CTRL+R after send)', () => {
    // 模拟用户输入 → 发送清空草稿的两步序列
    useSessionStore.getState().setComposerDraft('key1', 'hello')
    useSessionStore.getState().setComposerDraft('key1', '')

    const raw = localStorage.getItem('data-talk.session')
    expect(raw).toBeTruthy()
    const parsed = JSON.parse(raw!)
    // 关键断言：清空后必须同步落盘为 ''，CTRL+R hydrate 才能读到空草稿
    expect(parsed.state?.composerDrafts?.key1).toBe('')
  })

  it('hydration restores composerDrafts from localStorage after reload', async () => {
    // 1) 写入草稿
    useSessionStore.getState().setComposerDraft('key1', '')
    useSessionStore.getState().setComposerDraft('key2', 'keep-me')

    const raw = localStorage.getItem('data-talk.session')
    expect(raw).toBeTruthy()
    expect(JSON.parse(raw!).state.composerDrafts).toEqual({ key1: '', key2: 'keep-me' })

    // 2) 模拟刷新：drop module cache，重新 import 触发 persist hydrate
    vi.resetModules()
    const mod = await import('./session-store')
    const reloaded = mod.useSessionStore

    // 3) 验证 in-memory state 由 localStorage 正确还原
    const drafts = reloaded.getState().composerDrafts
    expect(drafts.key1).toBe('')
    expect(drafts.key2).toBe('keep-me')
  })
})
