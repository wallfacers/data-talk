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
