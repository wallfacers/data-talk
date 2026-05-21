import { beforeEach, describe, expect, it } from 'vitest'
import type { SessionDataContext } from '@/services/api/session-data-context'
import { useSessionStore } from './session-store'

const DRAFT_PREFIX = 'dt.draft.'

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
    // Clean up all draft keys
    const keysToRemove: string[] = []
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i)
      if (k?.startsWith(DRAFT_PREFIX)) keysToRemove.push(k)
    }
    keysToRemove.forEach((k) => localStorage.removeItem(k))
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

describe('composerDrafts independent localStorage key persistence', () => {
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
    const keysToRemove: string[] = []
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i)
      if (k?.startsWith(DRAFT_PREFIX)) keysToRemove.push(k)
    }
    keysToRemove.forEach((k) => localStorage.removeItem(k))
  })

  it('setComposerDraft writes to independent localStorage key', () => {
    useSessionStore.getState().setComposerDraft('s1', 'hello')
    expect(useSessionStore.getState().composerDrafts.s1).toBe('hello')
    expect(localStorage.getItem('dt.draft.s1')).toBe('hello')
  })

  it('setComposerDraft with empty string removes localStorage key', () => {
    useSessionStore.getState().setComposerDraft('s1', 'hello')
    expect(localStorage.getItem('dt.draft.s1')).toBe('hello')

    useSessionStore.getState().setComposerDraft('s1', '')
    expect(localStorage.getItem('dt.draft.s1')).toBeNull()
  })

  it('setComposerDraft for __nosession__ key', () => {
    useSessionStore.getState().setComposerDraft('__nosession__', 'no session text')
    expect(localStorage.getItem('dt.draft.__nosession__')).toBe('no session text')
    expect(useSessionStore.getState().composerDrafts['__nosession__']).toBe('no session text')
  })

  it('clearComposerDraft removes localStorage key and memory entry', () => {
    useSessionStore.getState().setComposerDraft('s1', 'hello')
    expect(localStorage.getItem('dt.draft.s1')).toBe('hello')

    useSessionStore.getState().clearComposerDraft('s1')
    expect(localStorage.getItem('dt.draft.s1')).toBeNull()
    expect(useSessionStore.getState().composerDrafts.s1).toBeUndefined()
  })

  it('clearComposerDraft on non-existent key is a no-op', () => {
    useSessionStore.getState().clearComposerDraft('nonexistent')
    expect(localStorage.getItem('dt.draft.nonexistent')).toBeNull()
  })

  it('hydrateComposerDraft reads from localStorage and writes to memory', () => {
    localStorage.setItem('dt.draft.s1', 'restored')
    const result = useSessionStore.getState().hydrateComposerDraft('s1')
    expect(result).toBe('restored')
    expect(useSessionStore.getState().composerDrafts.s1).toBe('restored')
  })

  it('hydrateComposerDraft returns null for missing key', () => {
    const result = useSessionStore.getState().hydrateComposerDraft('missing')
    expect(result).toBeNull()
  })

  it('send path: setComposerDraft then clearComposerDraft leaves localStorage empty', () => {
    // Simulate keystroke
    useSessionStore.getState().setComposerDraft('s1', 'hello world')
    expect(localStorage.getItem('dt.draft.s1')).toBe('hello world')

    // Simulate send: clear draft
    useSessionStore.getState().clearComposerDraft('s1')
    expect(localStorage.getItem('dt.draft.s1')).toBeNull()

    // Simulate CTRL+R: hydrate should return null
    const hydrated = useSessionStore.getState().hydrateComposerDraft('s1')
    expect(hydrated).toBeNull()
  })

  it('partialize still excludes composerDrafts', () => {
    useSessionStore.getState().setComposerDraft('s1', 'hello')
    const raw = localStorage.getItem('data-talk.session')
    if (raw) {
      const parsed = JSON.parse(raw)
      expect(parsed.state?.composerDrafts).toBeUndefined()
    }
  })
})
