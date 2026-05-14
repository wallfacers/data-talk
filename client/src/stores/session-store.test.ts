import { beforeEach, describe, expect, it } from 'vitest'
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

// BUG-0046 — composerDrafts 不再持久化：CTRL+R 后 hydrate 不应回填草稿。
// 历史上 BUG-0037/0039 反复处理同步落盘 race，现在改为不持久化，让 reload
// 后输入框总是空，规避整条 race 链。partialize 也不再包含 composerDrafts。
describe('composerDrafts persistence removed (BUG-0046)', () => {
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

  it('setComposerDraft updates in-memory state only, never writes composerDrafts to localStorage', () => {
    useSessionStore.getState().setComposerDraft('key1', 'hello')
    expect(useSessionStore.getState().composerDrafts.key1).toBe('hello')

    const raw = localStorage.getItem('data-talk.session')
    if (raw) {
      const parsed = JSON.parse(raw)
      expect(parsed.state?.composerDrafts).toBeUndefined()
      expect((parsed as Record<string, unknown>).composerDrafts).toBeUndefined()
    }
  })

  it('partialize excludes composerDrafts so reload starts with empty drafts', () => {
    useSessionStore.getState().setComposerDraft('key1', 'hello')
    const raw = localStorage.getItem('data-talk.session')
    if (raw) {
      const parsed = JSON.parse(raw)
      expect(parsed.state?.composerDrafts).toBeUndefined()
    }
  })
})
