import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, act } from '@testing-library/react'
import { ScriptConsolePanel } from '@/features/script/components/script-console-panel'
import { useScriptWorkbenchStore } from '@/features/script/stores/script-workbench-store'

// --- Hoisted mock instances (available before vi.mock hoisting) ---

const { mockTermInstance, mockFitAddonInstance } = vi.hoisted(() => {
  const term = {
    open: vi.fn(),
    loadAddon: vi.fn(),
    writeln: vi.fn(),
    dispose: vi.fn(),
    options: { theme: {} as any },
  }
  const fit = {
    fit: vi.fn(),
  }
  return {
    mockTermInstance: term,
    mockFitAddonInstance: fit,
  }
})

// --- Mocks ---

vi.mock('@xterm/xterm', () => ({
  Terminal: vi.fn((options: any) => {
    // Store options for assertions
    ;(Terminal as any)._lastOptions = options
    return mockTermInstance
  }),
}))

vi.mock('@xterm/addon-fit', () => ({
  FitAddon: vi.fn(() => mockFitAddonInstance),
}))

vi.mock('@xterm/xterm/css/xterm.css', () => ({}))

vi.mock('@/stores/theme-store', () => ({
  useThemeStore: vi.fn((selector?: any) => {
    const state = { theme: 'dark' }
    if (typeof selector === 'function') {
      return selector(state)
    }
    return state
  }),
}))

vi.mock('@/features/script/stores/script-workbench-store', () => {
  let state: any = {
    tabsById: {},
    envInfo: null,
    envChecked: false,
    setRunning: vi.fn(),
    appendConsoleOutput: vi.fn(),
    setCompleted: vi.fn(),
    setError: vi.fn(),
    setEnvInfo: vi.fn(),
    clearConsole: vi.fn(),
    setConnectionId: vi.fn(),
    setLanguage: vi.fn(),
    ensureTab: vi.fn(),
    hydrateTab: vi.fn(),
    setScriptText: vi.fn(),
    replaceScriptText: vi.fn(),
    cleanupTabs: vi.fn(),
  }

  return {
    useScriptWorkbenchStore: vi.fn((selector?: any) => {
      if (typeof selector === 'function') {
        return selector(state)
      }
      return state
    }),
  }
})

import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'

// --- Setup ResizeObserver mock ---

let resizeCallback: ResizeObserverCallback | null = null

class MockResizeObserver {
  constructor(callback: ResizeObserverCallback) {
    resizeCallback = callback
  }
  observe() {}
  unobserve() {}
  disconnect() {}
}

// --- Tests ---

describe('ScriptConsolePanel', () => {
  const originalResizeObserver = globalThis.ResizeObserver

  beforeEach(() => {
    vi.clearAllMocks()
    resizeCallback = null
    globalThis.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver

    // Default store state
    ;(useScriptWorkbenchStore as unknown as ReturnType<typeof vi.fn>).mockImplementation((selector?: any) => {
      const state = {
        tabsById: {
          'tab-1': {
            scriptText: '',
            version: 1,
            language: 'python',
            executeStatus: 'idle' as const,
            consoleOutput: [],
            connectionId: null,
            currentRunId: null,
            currentToken: null,
            envInfo: null,
            envChecked: false,
            errorMessage: null,
          },
        },
        envInfo: null,
        envChecked: false,
      }
      if (typeof selector === 'function') {
        return selector(state)
      }
      return state
    })
  })

  afterEach(() => {
    globalThis.ResizeObserver = originalResizeObserver
    vi.restoreAllMocks()
  })

  describe('terminal initialization', () => {
    it('creates a Terminal on mount with default theme', () => {
      render(<ScriptConsolePanel tabId="tab-1" />)

      expect(Terminal).toHaveBeenCalledTimes(1)

      const options = (Terminal as any)._lastOptions
      expect(options).toBeDefined()
      expect(options.fontSize).toBe(13)
      expect(options.lineHeight).toBe(1.2)
      expect(options.scrollback).toBe(10000)
      expect(options.convertEol).toBe(true)
      expect(options.allowTransparency).toBe(false)
    })

    it('creates Terminal with dark theme colors', () => {
      render(<ScriptConsolePanel tabId="tab-1" />)

      const options = (Terminal as any)._lastOptions
      expect(options.theme).toBeDefined()
      expect(options.theme.background).toBe('#1a1a19')
      expect(options.theme.foreground).toBe('#f1f1ef')
      expect(options.theme.cursor).toBe('#b9b9b7')
      expect(options.theme.selectionBackground).toBe('#34322d')
    })

    it('creates FitAddon and loads it into the terminal', () => {
      render(<ScriptConsolePanel tabId="tab-1" />)

      expect(FitAddon).toHaveBeenCalledTimes(1)
      expect(mockTermInstance.loadAddon).toHaveBeenCalledWith(mockFitAddonInstance)
    })

    it('opens the terminal in the container div', () => {
      render(<ScriptConsolePanel tabId="tab-1" />)

      expect(mockTermInstance.open).toHaveBeenCalledTimes(1)
      // open should be called with a DOM element
      const openArg = mockTermInstance.open.mock.calls[0]?.[0]
      expect(openArg).toBeInstanceOf(HTMLElement)
    })

    it('calls fit on the FitAddon after opening', () => {
      render(<ScriptConsolePanel tabId="tab-1" />)

      expect(mockFitAddonInstance.fit).toHaveBeenCalled()
    })

    it('disposes the terminal on unmount', () => {
      const { unmount } = render(<ScriptConsolePanel tabId="tab-1" />)

      expect(mockTermInstance.dispose).not.toHaveBeenCalled()

      unmount()

      expect(mockTermInstance.dispose).toHaveBeenCalledTimes(1)
    })

    it('sets up ResizeObserver to call fit on resize', () => {
      render(<ScriptConsolePanel tabId="tab-1" />)

      expect(resizeCallback).not.toBeNull()

      // Trigger resize
      act(() => {
        resizeCallback!([], {} as ResizeObserver)
      })

      // fit should have been called at least twice (once on init, once on resize)
      expect(mockFitAddonInstance.fit).toHaveBeenCalledTimes(2)
    })
  })

  describe('console output writing', () => {
    it('writes stdout entries without color prefix', () => {
      const { rerender } = render(<ScriptConsolePanel tabId="tab-1" />)

      // Update store with stdout output
      ;(useScriptWorkbenchStore as unknown as ReturnType<typeof vi.fn>).mockImplementation((selector?: any) => {
        const state = {
          tabsById: {
            'tab-1': {
              scriptText: '',
              version: 1,
              language: 'python',
              executeStatus: 'running' as const,
              consoleOutput: [
                { channel: 'stdout' as const, text: 'hello', timestamp: 1000 },
              ],
              connectionId: null,
              currentRunId: null,
              currentToken: null,
              envInfo: null,
              envChecked: false,
              errorMessage: null,
            },
          },
          envInfo: null,
          envChecked: false,
        }
        if (typeof selector === 'function') return selector(state)
        return state
      })

      rerender(<ScriptConsolePanel tabId="tab-1" />)

      expect(mockTermInstance.writeln).toHaveBeenCalledWith('hello')
    })

    it('writes stderr entries with red ANSI color prefix', () => {
      render(<ScriptConsolePanel tabId="tab-1" />)

      // Update store with stderr output by re-rendering with new store state
      ;(useScriptWorkbenchStore as unknown as ReturnType<typeof vi.fn>).mockImplementation((selector?: any) => {
        const state = {
          tabsById: {
            'tab-1': {
              scriptText: '',
              version: 1,
              language: 'python',
              executeStatus: 'running' as const,
              consoleOutput: [
                { channel: 'stderr' as const, text: 'error occurred', timestamp: 1000 },
              ],
              connectionId: null,
              currentRunId: null,
              currentToken: null,
              envInfo: null,
              envChecked: false,
              errorMessage: null,
            },
          },
          envInfo: null,
          envChecked: false,
        }
        if (typeof selector === 'function') return selector(state)
        return state
      })

      const { rerender } = render(<ScriptConsolePanel tabId="tab-1" />)

      // Re-render with the new state
      rerender(<ScriptConsolePanel tabId="tab-1" />)

      // Should have red ANSI escape codes
      expect(mockTermInstance.writeln).toHaveBeenCalledWith('\x1b[31merror occurred\x1b[0m')
    })

    it('writes mixed stdout and stderr with correct coloring', () => {
      const entries = [
        { channel: 'stdout' as const, text: 'starting...', timestamp: 1000 },
        { channel: 'stderr' as const, text: 'warning', timestamp: 1001 },
        { channel: 'stdout' as const, text: 'done', timestamp: 1002 },
      ]

      ;(useScriptWorkbenchStore as unknown as ReturnType<typeof vi.fn>).mockImplementation((selector?: any) => {
        const state = {
          tabsById: {
            'tab-1': {
              scriptText: '',
              version: 1,
              language: 'python',
              executeStatus: 'running' as const,
              consoleOutput: entries,
              connectionId: null,
              currentRunId: null,
              currentToken: null,
              envInfo: null,
              envChecked: false,
              errorMessage: null,
            },
          },
          envInfo: null,
          envChecked: false,
        }
        if (typeof selector === 'function') return selector(state)
        return state
      })

      const { rerender } = render(<ScriptConsolePanel tabId="tab-1" />)

      rerender(<ScriptConsolePanel tabId="tab-1" />)

      expect(mockTermInstance.writeln).toHaveBeenCalledWith('starting...')
      expect(mockTermInstance.writeln).toHaveBeenCalledWith('\x1b[31mwarning\x1b[0m')
      expect(mockTermInstance.writeln).toHaveBeenCalledWith('done')
      expect(mockTermInstance.writeln).toHaveBeenCalledTimes(3)
    })

    it('only writes new entries (does not re-write previous output)', () => {
      // First render with 2 entries
      ;(useScriptWorkbenchStore as unknown as ReturnType<typeof vi.fn>).mockImplementation((selector?: any) => {
        const state = {
          tabsById: {
            'tab-1': {
              scriptText: '',
              version: 1,
              language: 'python',
              executeStatus: 'running' as const,
              consoleOutput: [
                { channel: 'stdout' as const, text: 'line 1', timestamp: 1000 },
                { channel: 'stdout' as const, text: 'line 2', timestamp: 1001 },
              ],
              connectionId: null,
              currentRunId: null,
              currentToken: null,
              envInfo: null,
              envChecked: false,
              errorMessage: null,
            },
          },
          envInfo: null,
          envChecked: false,
        }
        if (typeof selector === 'function') return selector(state)
        return state
      })

      const { rerender } = render(<ScriptConsolePanel tabId="tab-1" />)
      expect(mockTermInstance.writeln).toHaveBeenCalledTimes(2)

      // Re-render with 3 entries (1 new)
      ;(useScriptWorkbenchStore as unknown as ReturnType<typeof vi.fn>).mockImplementation((selector?: any) => {
        const state = {
          tabsById: {
            'tab-1': {
              scriptText: '',
              version: 1,
              language: 'python',
              executeStatus: 'running' as const,
              consoleOutput: [
                { channel: 'stdout' as const, text: 'line 1', timestamp: 1000 },
                { channel: 'stdout' as const, text: 'line 2', timestamp: 1001 },
                { channel: 'stderr' as const, text: 'line 3', timestamp: 1002 },
              ],
              connectionId: null,
              currentRunId: null,
              currentToken: null,
              envInfo: null,
              envChecked: false,
              errorMessage: null,
            },
          },
          envInfo: null,
          envChecked: false,
        }
        if (typeof selector === 'function') return selector(state)
        return state
      })

      rerender(<ScriptConsolePanel tabId="tab-1" />)

      // Should only write the new entry (line 3)
      expect(mockTermInstance.writeln).toHaveBeenCalledTimes(3)
      const calls = (mockTermInstance.writeln as ReturnType<typeof vi.fn>).mock.calls
      const line3Call = calls[2][0]
      expect(line3Call).toBe('\x1b[31mline 3\x1b[0m')
    })

    it('handles empty consoleOutput gracefully', () => {
      ;(useScriptWorkbenchStore as unknown as ReturnType<typeof vi.fn>).mockImplementation((selector?: any) => {
        const state = {
          tabsById: {
            'tab-1': {
              scriptText: '',
              version: 1,
              language: 'python',
              executeStatus: 'idle' as const,
              consoleOutput: [],
              connectionId: null,
              currentRunId: null,
              currentToken: null,
              envInfo: null,
              envChecked: false,
              errorMessage: null,
            },
          },
          envInfo: null,
          envChecked: false,
        }
        if (typeof selector === 'function') return selector(state)
        return state
      })

      render(<ScriptConsolePanel tabId="tab-1" />)

      expect(mockTermInstance.writeln).not.toHaveBeenCalled()
    })
  })

  describe('tab-specific output isolation', () => {
    it('renders different output for different tabIds', () => {
      ;(useScriptWorkbenchStore as unknown as ReturnType<typeof vi.fn>).mockImplementation((selector?: any) => {
        const state = {
          tabsById: {
            'tab-A': {
              scriptText: '',
              version: 1,
              language: 'python',
              executeStatus: 'running' as const,
              consoleOutput: [
                { channel: 'stdout' as const, text: 'output A', timestamp: 1000 },
              ],
              connectionId: null,
              currentRunId: null,
              currentToken: null,
              envInfo: null,
              envChecked: false,
              errorMessage: null,
            },
            'tab-B': {
              scriptText: '',
              version: 1,
              language: 'javascript',
              executeStatus: 'running' as const,
              consoleOutput: [
                { channel: 'stderr' as const, text: 'output B', timestamp: 1000 },
              ],
              connectionId: null,
              currentRunId: null,
              currentToken: null,
              envInfo: null,
              envChecked: false,
              errorMessage: null,
            },
          },
          envInfo: null,
          envChecked: false,
        }
        if (typeof selector === 'function') return selector(state)
        return state
      })

      render(<ScriptConsolePanel tabId="tab-A" />)

      // Should only write tab-A's output
      expect(mockTermInstance.writeln).toHaveBeenCalledWith('output A')
      expect(mockTermInstance.writeln).not.toHaveBeenCalledWith('\x1b[31moutput B\x1b[0m')
    })

    it('returns empty array for non-existent tab consoleOutput', () => {
      ;(useScriptWorkbenchStore as unknown as ReturnType<typeof vi.fn>).mockImplementation((selector?: any) => {
        const state = {
          tabsById: {},
          envInfo: null,
          envChecked: false,
        }
        if (typeof selector === 'function') return selector(state)
        return state
      })

      expect(() => {
        render(<ScriptConsolePanel tabId="nonexistent" />)
      }).not.toThrow()

      // No output should be written
      expect(mockTermInstance.writeln).not.toHaveBeenCalled()
    })
  })

  describe('theme', () => {
    it('uses a dark background theme', () => {
      render(<ScriptConsolePanel tabId="tab-1" />)

      const options = (Terminal as any)._lastOptions
      expect(options.theme.background).toBe('#1a1a19')
      expect(options.theme.foreground).toBe('#f1f1ef')
    })

    it('disables transparency', () => {
      render(<ScriptConsolePanel tabId="tab-1" />)

      const options = (Terminal as any)._lastOptions
      expect(options.allowTransparency).toBe(false)
    })

    it('configures scrollback buffer', () => {
      render(<ScriptConsolePanel tabId="tab-1" />)

      const options = (Terminal as any)._lastOptions
      expect(options.scrollback).toBe(10000)
    })

    it('converts line endings', () => {
      render(<ScriptConsolePanel tabId="tab-1" />)

      const options = (Terminal as any)._lastOptions
      expect(options.convertEol).toBe(true)
    })
  })
})
