import { describe, it, expect, beforeEach } from 'vitest'
import { useScriptWorkbenchStore } from '@/features/script/stores/script-workbench-store'

function reset() {
  useScriptWorkbenchStore.setState({
    tabsById: {},
    envInfo: null,
    envChecked: false,
  })
}

describe('useScriptWorkbenchStore', () => {
  beforeEach(reset)

  describe('ensureTab', () => {
    it('creates a new tab with default values', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1')

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab).toBeDefined()
      expect(tab.scriptText).toBe('')
      expect(tab.version).toBe(1)
      expect(tab.language).toBe('python')
      expect(tab.executeStatus).toBe('idle')
      expect(tab.consoleOutput).toEqual([])
      expect(tab.connectionId).toBeNull()
      expect(tab.currentRunId).toBeNull()
      expect(tab.currentToken).toBeNull()
      expect(tab.envInfo).toBeNull()
      expect(tab.envChecked).toBe(false)
      expect(tab.errorMessage).toBeNull()
    })

    it('creates a new tab with initial values', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1', {
        scriptText: 'print("hello")',
        language: 'javascript',
        connectionId: 'conn-1',
      })

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.scriptText).toBe('print("hello")')
      expect(tab.language).toBe('javascript')
      expect(tab.connectionId).toBe('conn-1')
      expect(tab.version).toBe(1)
    })

    it('is idempotent: does not overwrite an existing tab', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1', {
        scriptText: 'original',
        language: 'python',
      })

      useScriptWorkbenchStore.getState().ensureTab('tab-1', {
        scriptText: 'overwrite',
        language: 'javascript',
      })

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.scriptText).toBe('original')
      expect(tab.language).toBe('python')
    })

    it('creates independent entries for different tab IDs', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1')
      useScriptWorkbenchStore.getState().ensureTab('tab-2')

      const state = useScriptWorkbenchStore.getState()
      expect(Object.keys(state.tabsById)).toHaveLength(2)
      expect(state.tabsById['tab-1']).toBeDefined()
      expect(state.tabsById['tab-2']).toBeDefined()
      expect(state.tabsById['tab-1']).not.toBe(state.tabsById['tab-2'])
    })
  })

  describe('hydrateTab', () => {
    it('hydrates a non-existing tab with script text and language', () => {
      useScriptWorkbenchStore.getState().hydrateTab('tab-1', {
        scriptText: 'hydrated code',
        language: 'javascript',
        connectionId: 'conn-xyz',
      })

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.scriptText).toBe('hydrated code')
      expect(tab.language).toBe('javascript')
      expect(tab.connectionId).toBe('conn-xyz')
      expect(tab.version).toBe(1)
    })

    it('hydrates a version-1 (fresh) tab', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1', {
        scriptText: 'old',
        language: 'python',
      })

      useScriptWorkbenchStore.getState().hydrateTab('tab-1', {
        scriptText: 'hydrated',
        language: 'javascript',
      })

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.scriptText).toBe('hydrated')
      expect(tab.language).toBe('javascript')
    })

    it('does NOT overwrite a tab with version > 1 (user already edited)', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1', {
        scriptText: 'initial',
      })
      // Simulate user edit by incrementing version
      useScriptWorkbenchStore.getState().setScriptText('tab-1', 'user edited')
      // version is now 2

      useScriptWorkbenchStore.getState().hydrateTab('tab-1', {
        scriptText: 'hydrated',
        language: 'javascript',
      })

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.scriptText).toBe('user edited')
      expect(tab.version).toBe(2)
      expect(tab.language).toBe('python') // unchanged from ensureTab default
    })

    it('partial hydrate: only updates provided fields', () => {
      useScriptWorkbenchStore.getState().hydrateTab('tab-1', {
        scriptText: 'code',
      })

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.scriptText).toBe('code')
      expect(tab.language).toBe('python') // default
      expect(tab.connectionId).toBeNull() // not set
    })
  })

  describe('setScriptText', () => {
    it('updates script text and increments version', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1', { scriptText: 'v1' })
      expect(useScriptWorkbenchStore.getState().tabsById['tab-1'].version).toBe(1)

      useScriptWorkbenchStore.getState().setScriptText('tab-1', 'v2')
      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.scriptText).toBe('v2')
      expect(tab.version).toBe(2)

      useScriptWorkbenchStore.getState().setScriptText('tab-1', 'v3')
      expect(useScriptWorkbenchStore.getState().tabsById['tab-1'].version).toBe(3)
    })

    it('is a no-op for non-existent tabs', () => {
      useScriptWorkbenchStore.getState().setScriptText('nonexistent', 'text')
      expect(useScriptWorkbenchStore.getState().tabsById['nonexistent']).toBeUndefined()
    })
  })

  describe('replaceScriptText', () => {
    it('replaces text when version matches and increments version', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1', { scriptText: 'v1' })
      // version is 1

      const result = useScriptWorkbenchStore.getState().replaceScriptText('tab-1', 'replaced', 1)
      expect(result.ok).toBe(true)
      expect(result.version).toBe(2)

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.scriptText).toBe('replaced')
      expect(tab.version).toBe(2)
    })

    it('returns ok=false and current version when version mismatches', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1', { scriptText: 'v1' })
      useScriptWorkbenchStore.getState().setScriptText('tab-1', 'v2') // version now 2

      const result = useScriptWorkbenchStore.getState().replaceScriptText('tab-1', 'stale-replace', 1)
      expect(result.ok).toBe(false)
      expect(result.version).toBe(2) // current version returned

      // Text should NOT have been replaced
      expect(useScriptWorkbenchStore.getState().tabsById['tab-1'].scriptText).toBe('v2')
    })

    it('returns ok=false for non-existent tab', () => {
      const result = useScriptWorkbenchStore.getState().replaceScriptText('missing', 'text', 1)
      expect(result.ok).toBe(false)
      expect(result.version).toBe(0)
    })
  })

  describe('setLanguage', () => {
    it('changes the language', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1', { language: 'python' })

      useScriptWorkbenchStore.getState().setLanguage('tab-1', 'javascript')
      expect(useScriptWorkbenchStore.getState().tabsById['tab-1'].language).toBe('javascript')
    })

    it('is a no-op for non-existent tabs', () => {
      useScriptWorkbenchStore.getState().setLanguage('nonexistent', 'javascript')
      // Should not throw, state unchanged
      expect(useScriptWorkbenchStore.getState().tabsById['nonexistent']).toBeUndefined()
    })
  })

  describe('setConnectionId', () => {
    it('sets the connection id', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1')
      expect(useScriptWorkbenchStore.getState().tabsById['tab-1'].connectionId).toBeNull()

      useScriptWorkbenchStore.getState().setConnectionId('tab-1', 'conn-abc')
      expect(useScriptWorkbenchStore.getState().tabsById['tab-1'].connectionId).toBe('conn-abc')
    })

    it('sets connection id to null', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1', { connectionId: 'conn-1' })
      useScriptWorkbenchStore.getState().setConnectionId('tab-1', null)
      expect(useScriptWorkbenchStore.getState().tabsById['tab-1'].connectionId).toBeNull()
    })
  })

  describe('executeStatus state machine', () => {
    it('transitions from idle to running via setRunning', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1')
      expect(useScriptWorkbenchStore.getState().tabsById['tab-1'].executeStatus).toBe('idle')

      useScriptWorkbenchStore.getState().setRunning('tab-1', 'run-123', 'token-abc')

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.executeStatus).toBe('running')
      expect(tab.currentRunId).toBe('run-123')
      expect(tab.currentToken).toBe('token-abc')
    })

    it('setRunning clears previous console output and error', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1')
      useScriptWorkbenchStore.getState().appendConsoleOutput('tab-1', {
        channel: 'stdout', text: 'old output', timestamp: 1,
      })
      useScriptWorkbenchStore.setState((s) => ({
        tabsById: {
          ...s.tabsById,
          'tab-1': { ...s.tabsById['tab-1'], errorMessage: 'previous error' },
        },
      }))

      useScriptWorkbenchStore.getState().setRunning('tab-1', 'run-1', 'token-1')

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.consoleOutput).toEqual([])
      expect(tab.errorMessage).toBeNull()
    })

    it('transitions from running to success via setCompleted(0)', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1')
      useScriptWorkbenchStore.getState().setRunning('tab-1', 'run-1', 'token-1')

      useScriptWorkbenchStore.getState().setCompleted('tab-1', 0)

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.executeStatus).toBe('success')
      expect(tab.currentRunId).toBeNull()
      expect(tab.currentToken).toBeNull()
      expect(tab.errorMessage).toBeNull()
    })

    it('transitions from running to error via setCompleted(non-zero)', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1')
      useScriptWorkbenchStore.getState().setRunning('tab-1', 'run-1', 'token-1')

      useScriptWorkbenchStore.getState().setCompleted('tab-1', 1)

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.executeStatus).toBe('error')
      expect(tab.errorMessage).toBe('Process exited with code 1')
    })

    it('transitions from running to error via setCompleted(-1)', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1')
      useScriptWorkbenchStore.getState().setRunning('tab-1', 'run-1', 'token-1')

      useScriptWorkbenchStore.getState().setCompleted('tab-1', -1)

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.executeStatus).toBe('error')
      expect(tab.errorMessage).toBe('Process exited with code -1')
    })

    it('transitions to error via setError with a custom message', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1')
      useScriptWorkbenchStore.getState().setRunning('tab-1', 'run-1', 'token-1')

      useScriptWorkbenchStore.getState().setError('tab-1', 'Connection refused')

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.executeStatus).toBe('error')
      expect(tab.currentRunId).toBeNull()
      expect(tab.currentToken).toBeNull()
      expect(tab.errorMessage).toBe('Connection refused')
    })

    it('calling setError on idle tab sets error state', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1')

      useScriptWorkbenchStore.getState().setError('tab-1', 'Something went wrong')

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.executeStatus).toBe('error')
      expect(tab.errorMessage).toBe('Something went wrong')
    })
  })

  describe('consoleOutput', () => {
    it('appendConsoleOutput adds entries', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1')

      useScriptWorkbenchStore.getState().appendConsoleOutput('tab-1', {
        channel: 'stdout', text: 'line 1', timestamp: 1000,
      })

      useScriptWorkbenchStore.getState().appendConsoleOutput('tab-1', {
        channel: 'stderr', text: 'error!', timestamp: 1001,
      })

      const output = useScriptWorkbenchStore.getState().tabsById['tab-1'].consoleOutput
      expect(output).toHaveLength(2)
      expect(output[0]).toEqual({ channel: 'stdout', text: 'line 1', timestamp: 1000 })
      expect(output[1]).toEqual({ channel: 'stderr', text: 'error!', timestamp: 1001 })
    })

    it('appendConsoleOutput does not throw for non-existent tab', () => {
      expect(() => {
        useScriptWorkbenchStore.getState().appendConsoleOutput('missing', {
          channel: 'stdout', text: 'test', timestamp: 1,
        })
      }).not.toThrow()
    })

    it('clearConsole empties the output array', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-1')
      useScriptWorkbenchStore.getState().appendConsoleOutput('tab-1', {
        channel: 'stdout', text: 'keep me', timestamp: 1,
      })
      useScriptWorkbenchStore.getState().appendConsoleOutput('tab-1', {
        channel: 'stderr', text: 'remove me', timestamp: 2,
      })

      useScriptWorkbenchStore.getState().clearConsole('tab-1')

      expect(useScriptWorkbenchStore.getState().tabsById['tab-1'].consoleOutput).toEqual([])
    })
  })

  describe('per-tab isolation', () => {
    it('modifying tab A does not affect tab B', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-A', {
        scriptText: 'code A',
        language: 'python',
      })
      useScriptWorkbenchStore.getState().ensureTab('tab-B', {
        scriptText: 'code B',
        language: 'javascript',
      })

      // Modify tab A
      useScriptWorkbenchStore.getState().setScriptText('tab-A', 'modified A')
      useScriptWorkbenchStore.getState().setRunning('tab-A', 'run-A', 'token-A')
      useScriptWorkbenchStore.getState().appendConsoleOutput('tab-A', {
        channel: 'stdout', text: 'A output', timestamp: 1,
      })

      // Tab B should be unchanged
      const tabB = useScriptWorkbenchStore.getState().tabsById['tab-B']
      expect(tabB.scriptText).toBe('code B')
      expect(tabB.language).toBe('javascript')
      expect(tabB.executeStatus).toBe('idle')
      expect(tabB.consoleOutput).toEqual([])
      expect(tabB.currentRunId).toBeNull()
    })

    it('each tab has independent executeStatus state', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-A')
      useScriptWorkbenchStore.getState().ensureTab('tab-B')

      useScriptWorkbenchStore.getState().setRunning('tab-A', 'r1', 't1')
      useScriptWorkbenchStore.getState().setCompleted('tab-A', 0)

      useScriptWorkbenchStore.getState().setRunning('tab-B', 'r2', 't2')
      useScriptWorkbenchStore.getState().setError('tab-B', 'failed')

      expect(useScriptWorkbenchStore.getState().tabsById['tab-A'].executeStatus).toBe('success')
      expect(useScriptWorkbenchStore.getState().tabsById['tab-B'].executeStatus).toBe('error')
    })

    it('setScriptText versions are independent per tab', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-A')
      useScriptWorkbenchStore.getState().ensureTab('tab-B')

      // Edit tab A twice
      useScriptWorkbenchStore.getState().setScriptText('tab-A', 'a1')
      useScriptWorkbenchStore.getState().setScriptText('tab-A', 'a2')

      // Edit tab B once
      useScriptWorkbenchStore.getState().setScriptText('tab-B', 'b1')

      expect(useScriptWorkbenchStore.getState().tabsById['tab-A'].version).toBe(3) // 1 + 2 edits
      expect(useScriptWorkbenchStore.getState().tabsById['tab-B'].version).toBe(2) // 1 + 1 edit
    })
  })

  describe('setEnvInfo', () => {
    it('sets environment info and marks envChecked', () => {
      expect(useScriptWorkbenchStore.getState().envChecked).toBe(false)

      useScriptWorkbenchStore.getState().setEnvInfo({
        python: '/usr/bin/python3',
        node: '/usr/bin/node',
      })

      const state = useScriptWorkbenchStore.getState()
      expect(state.envChecked).toBe(true)
      expect(state.envInfo).toEqual({
        python: '/usr/bin/python3',
        node: '/usr/bin/node',
      })
    })

    it('sets null values for missing runtimes', () => {
      useScriptWorkbenchStore.getState().setEnvInfo({
        python: null,
        node: '/usr/bin/node',
      })

      const state = useScriptWorkbenchStore.getState()
      expect(state.envInfo).toEqual({ python: null, node: '/usr/bin/node' })
    })
  })

  describe('cleanupTabs', () => {
    it('removes tabs not in the active list', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-A')
      useScriptWorkbenchStore.getState().ensureTab('tab-B')
      useScriptWorkbenchStore.getState().ensureTab('tab-C')

      useScriptWorkbenchStore.getState().cleanupTabs(['tab-A', 'tab-C'])

      const state = useScriptWorkbenchStore.getState()
      expect(state.tabsById['tab-A']).toBeDefined()
      expect(state.tabsById['tab-B']).toBeUndefined()
      expect(state.tabsById['tab-C']).toBeDefined()
    })

    it('handles empty active list', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-A')
      useScriptWorkbenchStore.getState().ensureTab('tab-B')

      useScriptWorkbenchStore.getState().cleanupTabs([])

      const state = useScriptWorkbenchStore.getState()
      expect(Object.keys(state.tabsById)).toHaveLength(0)
    })

    it('ignores active tab IDs not in store', () => {
      useScriptWorkbenchStore.getState().ensureTab('tab-A')

      useScriptWorkbenchStore.getState().cleanupTabs(['tab-A', 'nonexistent'])

      const state = useScriptWorkbenchStore.getState()
      expect(state.tabsById['tab-A']).toBeDefined()
      expect(Object.keys(state.tabsById)).toHaveLength(1)
    })
  })
})
