import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { useScriptExecute } from '@/features/script/hooks/use-script-execute'
import { useScriptWorkbenchStore } from '@/features/script/stores/script-workbench-store'
import { scriptApi } from '@/features/script/api/script-api'
import {
  detectScriptEnv,
  runScript as tauriRunScript,
  stopScript as tauriStopScript,
  onScriptOutput,
  onScriptCompleted,
} from '@/services/tauri/script-runner'

// --- Hoisted mocks ---

const { mockDetectScriptEnv, mockRunScript, mockStopScript, mockOnScriptOutput, mockOnScriptCompleted } = vi.hoisted(() => ({
  mockDetectScriptEnv: vi.fn().mockResolvedValue({ python: '3.11', node: 'v20' }),
  mockRunScript: vi.fn(),
  mockStopScript: vi.fn(),
  mockOnScriptOutput: vi.fn(),
  mockOnScriptCompleted: vi.fn(),
}))

// --- Mocks ---

vi.mock('@/features/script/api/script-api', () => ({
  scriptApi: {
    runPrepare: vi.fn(),
    runComplete: vi.fn(),
    listRuns: vi.fn(),
    dataWrite: vi.fn(),
    batchWrite: vi.fn(),
    batchClose: vi.fn(),
  },
}))

vi.mock('@/services/tauri/script-runner', () => ({
  detectScriptEnv: mockDetectScriptEnv,
  runScript: mockRunScript,
  stopScript: mockStopScript,
  onScriptOutput: mockOnScriptOutput,
  onScriptCompleted: mockOnScriptCompleted,
}))

// --- Helpers ---

function resetStore() {
  useScriptWorkbenchStore.setState({
    tabsById: {},
    envInfo: null,
    envChecked: false,
  })
}

function getOutputHandler() {
  const calls = (onScriptOutput as ReturnType<typeof vi.fn>).mock.calls
  return calls.length > 0 ? (calls[0][1] as (event: any) => void) : undefined
}

function getCompletedHandler() {
  const calls = (onScriptCompleted as ReturnType<typeof vi.fn>).mock.calls
  return calls.length > 0 ? (calls[0][1] as (event: any) => void) : undefined
}

function mockExecuteDefaults() {
  ;(scriptApi.runPrepare as ReturnType<typeof vi.fn>).mockResolvedValue({
    runId: 'run-1',
    token: 'token-abc',
  })
  ;(scriptApi.runComplete as ReturnType<typeof vi.fn>).mockResolvedValue({
    runId: 'run-1',
    status: 'completed',
  })
  ;(tauriRunScript as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
  ;(tauriStopScript as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
  ;(onScriptOutput as ReturnType<typeof vi.fn>).mockImplementation(
    (_runId: string, _handler: (event: any) => void) => Promise.resolve(vi.fn()),
  )
  ;(onScriptCompleted as ReturnType<typeof vi.fn>).mockImplementation(
    (_runId: string, _handler: (event: any) => void) => Promise.resolve(vi.fn()),
  )
}

function setupTab(tabId: string, overrides?: Partial<{ scriptText: string; language: 'python' | 'javascript'; connectionId: string | null }>) {
  useScriptWorkbenchStore.getState().ensureTab(tabId, {
    scriptText: 'print("hello")',
    language: 'python',
    connectionId: 'conn-1',
    ...overrides,
  })
}

// --- Tests ---

describe('useScriptExecute', () => {
  beforeEach(() => {
    resetStore()
    vi.clearAllMocks()
    mockDetectScriptEnv.mockResolvedValue({ python: '3.11', node: 'v20' })
    mockExecuteDefaults()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('environment detection', () => {
    it('detects script environment on first mount and sets envInfo', async () => {
      ;(detectScriptEnv as ReturnType<typeof vi.fn>).mockResolvedValue({
        python: '/usr/bin/python3',
        node: '/usr/bin/node',
      })

      renderHook(() => useScriptExecute('tab-1'))

      await waitFor(() => {
        expect(detectScriptEnv).toHaveBeenCalledTimes(1)
      })

      const state = useScriptWorkbenchStore.getState()
      expect(state.envInfo).toEqual({ python: '/usr/bin/python3', node: '/usr/bin/node' })
      expect(state.envChecked).toBe(true)
    })

    it('sets null env when detection fails', async () => {
      ;(detectScriptEnv as ReturnType<typeof vi.fn>).mockRejectedValue(
        new Error('Python not found'),
      )

      renderHook(() => useScriptExecute('tab-1'))

      await waitFor(() => {
        const state = useScriptWorkbenchStore.getState()
        expect(state.envChecked).toBe(true)
        expect(state.envInfo).toEqual({ python: null, node: null })
      })
    })

    it('skips detection when envChecked is already true', () => {
      useScriptWorkbenchStore.setState({
        envChecked: true,
        envInfo: { python: '3.10', node: 'v18' },
      })

      renderHook(() => useScriptExecute('tab-1'))

      expect(detectScriptEnv).not.toHaveBeenCalled()
    })
  })

  describe('execute - guard conditions', () => {
    beforeEach(() => {
      useScriptWorkbenchStore.setState({
        envChecked: true,
        envInfo: { python: '3.11', node: 'v20' },
      })
    })

    it('does nothing when tab does not exist', async () => {
      const { result } = renderHook(() => useScriptExecute('nonexistent'))

      await act(async () => {
        await result.current.execute()
      })

      expect(scriptApi.runPrepare).not.toHaveBeenCalled()
    })

    it('does nothing when connectionId is null', async () => {
      setupTab('tab-no-conn', { connectionId: null })

      const { result } = renderHook(() => useScriptExecute('tab-no-conn'))

      await act(async () => {
        await result.current.execute()
      })

      expect(scriptApi.runPrepare).not.toHaveBeenCalled()
    })

    it('does nothing when already running', async () => {
      setupTab('tab-1')
      useScriptWorkbenchStore.getState().setRunning('tab-1', 'existing-run', 'existing-token')

      const { result } = renderHook(() => useScriptExecute('tab-1'))

      await act(async () => {
        await result.current.execute()
      })

      expect(scriptApi.runPrepare).not.toHaveBeenCalled()
    })
  })

  describe('execute - full success flow', () => {
    beforeEach(() => {
      useScriptWorkbenchStore.setState({
        envChecked: true,
        envInfo: { python: '3.11', node: 'v20' },
      })
      setupTab('tab-1')
    })

    it('prepares run, sets running, registers listeners, and invokes Tauri', async () => {
      const { result } = renderHook(() => useScriptExecute('tab-1'))

      await act(async () => {
        await result.current.execute()
      })

      // Step 1: runPrepare called with correct params
      expect(scriptApi.runPrepare).toHaveBeenCalledWith({
        scriptContent: 'print("hello")',
        language: 'python',
        connectionId: 'conn-1',
        createdByKind: 'user',
      })

      // Step 2: store is in running state, console cleared
      let state = useScriptWorkbenchStore.getState()
      const tab = state.tabsById['tab-1']
      expect(tab.executeStatus).toBe('running')
      expect(tab.currentRunId).toBe('run-1')
      expect(tab.currentToken).toBe('token-abc')
      expect(tab.consoleOutput).toEqual([])
      expect(tab.errorMessage).toBeNull()

      // Step 3: event listeners registered
      expect(onScriptOutput).toHaveBeenCalledWith('run-1', expect.any(Function))
      expect(onScriptCompleted).toHaveBeenCalledWith('run-1', expect.any(Function))

      // Step 4: Tauri runScript called
      expect(tauriRunScript).toHaveBeenCalledWith(
        'run-1',
        'print("hello")',
        'python',
        {
          DT_BACKEND_URL: expect.any(String),
          DT_SCRIPT_TOKEN: 'token-abc',
          DT_CONNECTION_ID: 'conn-1',
        },
      )
    })

    it('appends console output from onScriptOutput events', async () => {
      const { result } = renderHook(() => useScriptExecute('tab-1'))

      await act(async () => {
        await result.current.execute()
      })

      const handler = getOutputHandler()
      expect(handler).toBeDefined()

      await act(async () => {
        handler!({ channel: 'stdout', data: 'hello world', run_id: 'run-1' })
        handler!({ channel: 'stderr', data: 'warning!', run_id: 'run-1' })
      })

      const output = useScriptWorkbenchStore.getState().tabsById['tab-1'].consoleOutput
      expect(output).toHaveLength(2)
      expect(output[0]).toMatchObject({ channel: 'stdout', text: 'hello world' })
      expect(output[1]).toMatchObject({ channel: 'stderr', text: 'warning!' })
      expect(output[0].timestamp).toBeGreaterThan(0)
    })

    it('calls runComplete and transitions to success on exit_code=0', async () => {
      const { result } = renderHook(() => useScriptExecute('tab-1'))

      await act(async () => {
        await result.current.execute()
      })

      // Add some output first
      const outputHandler = getOutputHandler()
      await act(async () => {
        outputHandler!({ channel: 'stdout', data: 'result', run_id: 'run-1' })
      })

      // Fire completion event
      const completedHandler = getCompletedHandler()
      expect(completedHandler).toBeDefined()

      await act(async () => {
        completedHandler!({ exit_code: 0, run_id: 'run-1' })
      })

      // runComplete should be called with the collected output
      await waitFor(() => {
        expect(scriptApi.runComplete).toHaveBeenCalledWith('run-1', {
          exitCode: 0,
          stdoutText: 'result',
        })
      })

      // Store should be in success state
      const state = useScriptWorkbenchStore.getState()
      const tab = state.tabsById['tab-1']
      expect(tab.executeStatus).toBe('success')
      expect(tab.currentRunId).toBeNull()
      expect(tab.currentToken).toBeNull()
      expect(tab.errorMessage).toBeNull()
    })

    it('transitions to error on non-zero exit_code', async () => {
      const { result } = renderHook(() => useScriptExecute('tab-1'))

      await act(async () => {
        await result.current.execute()
      })

      const completedHandler = getCompletedHandler()

      await act(async () => {
        completedHandler!({ exit_code: 1, run_id: 'run-1' })
      })

      await waitFor(() => {
        const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
        expect(tab.executeStatus).toBe('error')
        expect(tab.errorMessage).toBe('Process exited with code 1')
      })
    })

    it('calls cleanupListeners (unlisten functions) after completion', async () => {
      const unlistenOutput = vi.fn()
      const unlistenCompleted = vi.fn()
      ;(onScriptOutput as ReturnType<typeof vi.fn>).mockResolvedValue(unlistenOutput)
      ;(onScriptCompleted as ReturnType<typeof vi.fn>).mockResolvedValue(unlistenCompleted)

      const { result, unmount } = renderHook(() => useScriptExecute('tab-1'))

      await act(async () => {
        await result.current.execute()
      })

      const completedHandler = getCompletedHandler()
      await act(async () => {
        completedHandler!({ exit_code: 0, run_id: 'run-1' })
      })

      await waitFor(() => {
        expect(unlistenOutput).toHaveBeenCalled()
        expect(unlistenCompleted).toHaveBeenCalled()
      })

      unmount()
    })

    it('still transitions to success even when runComplete throws', async () => {
      ;(scriptApi.runComplete as ReturnType<typeof vi.fn>).mockRejectedValue(
        new Error('Backend down'),
      )

      const { result } = renderHook(() => useScriptExecute('tab-1'))

      await act(async () => {
        await result.current.execute()
      })

      const completedHandler = getCompletedHandler()

      // This should not throw
      await act(async () => {
        completedHandler!({ exit_code: 0, run_id: 'run-1' })
      })

      // State should still transition to success (local state is independent)
      await waitFor(() => {
        const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
        expect(tab.executeStatus).toBe('success')
      })
    })
  })

  describe('execute - error paths', () => {
    beforeEach(() => {
      useScriptWorkbenchStore.setState({
        envChecked: true,
        envInfo: { python: '3.11', node: 'v20' },
      })
      setupTab('tab-1')
    })

    it('sets error state when runPrepare fails', async () => {
      ;(scriptApi.runPrepare as ReturnType<typeof vi.fn>).mockRejectedValue(
        new Error('Network error'),
      )

      const { result } = renderHook(() => useScriptExecute('tab-1'))

      await act(async () => {
        await result.current.execute()
      })

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.executeStatus).toBe('error')
      expect(tab.errorMessage).toBe('Network error')
    })

    it('sets error state when Tauri runScript fails', async () => {
      ;(tauriRunScript as ReturnType<typeof vi.fn>).mockRejectedValue(
        new Error('Tauri shell error'),
      )

      const { result } = renderHook(() => useScriptExecute('tab-1'))

      await act(async () => {
        await result.current.execute()
      })

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.executeStatus).toBe('error')
      expect(tab.errorMessage).toBe('Tauri shell error')
    })

    it('converts non-Error exceptions to string', async () => {
      // eslint-disable-next-line no-throw-literal
      ;(scriptApi.runPrepare as ReturnType<typeof vi.fn>).mockRejectedValue('string error')

      const { result } = renderHook(() => useScriptExecute('tab-1'))

      await act(async () => {
        await result.current.execute()
      })

      const tab = useScriptWorkbenchStore.getState().tabsById['tab-1']
      expect(tab.executeStatus).toBe('error')
      expect(tab.errorMessage).toBe('string error')
    })
  })

  describe('stop', () => {
    beforeEach(() => {
      useScriptWorkbenchStore.setState({
        envChecked: true,
        envInfo: { python: '3.11', node: 'v20' },
      })
      setupTab('tab-1')
    })

    it('does nothing when there is no current run', async () => {
      const { result } = renderHook(() => useScriptExecute('tab-1'))

      await act(async () => {
        await result.current.stop()
      })

      expect(tauriStopScript).not.toHaveBeenCalled()
    })

    it('stops the running script and notifies backend', async () => {
      // Put tab in running state
      useScriptWorkbenchStore.getState().setRunning('tab-1', 'run-active', 'token-active')

      const { result } = renderHook(() => useScriptExecute('tab-1'))

      await act(async () => {
        await result.current.stop()
      })

      // Should call Tauri stop
      expect(tauriStopScript).toHaveBeenCalledWith('run-active')

      // Should call runComplete with exitCode=-1
      expect(scriptApi.runComplete).toHaveBeenCalledWith('run-active', {
        exitCode: -1,
        stdoutText: '',
      })
    })

    it('appends stderr message when runComplete fails during stop', async () => {
      useScriptWorkbenchStore.getState().setRunning('tab-1', 'run-active', 'token-active')
      ;(scriptApi.runComplete as ReturnType<typeof vi.fn>).mockRejectedValue(
        new Error('notification failed'),
      )

      const { result } = renderHook(() => useScriptExecute('tab-1'))

      await act(async () => {
        await result.current.stop()
      })

      // Should have appended a warning to the console
      const output = useScriptWorkbenchStore.getState().tabsById['tab-1'].consoleOutput
      const lastEntry = output[output.length - 1]
      expect(lastEntry.channel).toBe('stderr')
      expect(lastEntry.text).toContain('Failed to notify server')
    })

    it('handles Tauri stop errors gracefully', async () => {
      useScriptWorkbenchStore.getState().setRunning('tab-1', 'run-active', 'token-active')
      ;(tauriStopScript as ReturnType<typeof vi.fn>).mockRejectedValue(
        new Error('Tauri stop failed'),
      )

      const { result } = renderHook(() => useScriptExecute('tab-1'))

      // Should not throw
      await act(async () => {
        await result.current.stop()
      })

      // Still should have attempted to call runComplete
      expect(scriptApi.runComplete).toHaveBeenCalledWith('run-active', {
        exitCode: -1,
        stdoutText: '',
      })
    })
  })

  describe('computed flags', () => {
    beforeEach(() => {
      useScriptWorkbenchStore.setState({
        envChecked: true,
        envInfo: { python: '3.11', node: 'v20' },
      })
    })

    it('canRun is true when connectionId is set, not running, and env is checked', () => {
      setupTab('tab-1', { connectionId: 'conn-1' })

      const { result } = renderHook(() => useScriptExecute('tab-1'))

      expect(result.current.canRun).toBe(true)
      expect(result.current.isRunning).toBe(false)
    })

    it('canRun is false when connectionId is null', () => {
      setupTab('tab-1', { connectionId: null })

      const { result } = renderHook(() => useScriptExecute('tab-1'))

      expect(result.current.canRun).toBe(false)
    })

    it('canRun is false when env is not yet checked', () => {
      useScriptWorkbenchStore.setState({
        envChecked: false,
        envInfo: null,
      })
      setupTab('tab-1', { connectionId: 'conn-1' })

      const { result } = renderHook(() => useScriptExecute('tab-1'))

      expect(result.current.canRun).toBe(false)
    })

    it('isRunning reflects the running state', () => {
      setupTab('tab-1')
      useScriptWorkbenchStore.getState().setRunning('tab-1', 'r1', 't1')

      const { result } = renderHook(() => useScriptExecute('tab-1'))

      expect(result.current.isRunning).toBe(true)
      expect(result.current.canRun).toBe(false)
    })

    it('returns envInfo from the store', () => {
      const { result } = renderHook(() => useScriptExecute('tab-1'))

      expect(result.current.envInfo).toEqual({ python: '3.11', node: 'v20' })
    })
  })
})
