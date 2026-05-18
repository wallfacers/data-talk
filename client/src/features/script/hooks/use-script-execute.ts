import { useCallback, useEffect, useRef } from 'react'
import { useScriptWorkbenchStore } from '@/features/script/stores/script-workbench-store'
import { scriptApi } from '@/features/script/api/script-api'
import {
  detectScriptEnv,
  runScript as tauriRunScript,
  stopScript as tauriStopScript,
  onScriptOutput,
  onScriptCompleted,
} from '@/services/tauri/script-runner'

export function useScriptExecute(tabId: string) {
  const unlistenRefs = useRef<(() => void)[]>([])
  const tab = useScriptWorkbenchStore((s) => s.tabsById[tabId])
  const envInfo = useScriptWorkbenchStore((s) => s.envInfo)
  const envChecked = useScriptWorkbenchStore((s) => s.envChecked)
  const setRunning = useScriptWorkbenchStore((s) => s.setRunning)
  const appendConsoleOutput = useScriptWorkbenchStore((s) => s.appendConsoleOutput)
  const setCompleted = useScriptWorkbenchStore((s) => s.setCompleted)
  const setError = useScriptWorkbenchStore((s) => s.setError)
  const setEnvInfo = useScriptWorkbenchStore((s) => s.setEnvInfo)

  // Detect environment on first mount
  useEffect(() => {
    if (envChecked) return
    detectScriptEnv()
      .then((info) => {
        setEnvInfo({
          python: info.python,
          node: info.node,
        })
      })
      .catch(() => {
        setEnvInfo({ python: null, node: null })
      })
  }, [envChecked, setEnvInfo])

  const execute = useCallback(async () => {
    if (!tab || !tab.connectionId || tab.executeStatus === 'running') return

    try {
      // Step 1: Prepare run on backend
      const { runId, token } = await scriptApi.runPrepare({
        scriptContent: tab.scriptText,
        language: tab.language,
        connectionId: tab.connectionId,
        createdByKind: 'user',
      })

      // Step 2: Update store state
      setRunning(tabId, runId, token)

      // Step 3: Set up event listeners
      const unlistenOutput = await onScriptOutput(runId, (event) => {
        appendConsoleOutput(tabId, {
          channel: event.channel as 'stdout' | 'stderr',
          text: event.data,
          timestamp: Date.now(),
        })
      })

      const unlistenCompleted = await onScriptCompleted(runId, async (event) => {
        // Step 5: Notify backend of completion
        const output = useScriptWorkbenchStore.getState().tabsById[tabId]?.consoleOutput
          .map((e) => e.text)
          .join('\n') ?? ''

        try {
          await scriptApi.runComplete(runId, {
            exitCode: event.exit_code,
            stdoutText: output,
          })
        } catch {
          // Backend notification failed, still update local state
        }

        setCompleted(tabId, event.exit_code)
        cleanupListeners()
      })

      unlistenRefs.current = [unlistenOutput, unlistenCompleted]

      // Step 4: Run script via Tauri
      await tauriRunScript(runId, tab.scriptText, tab.language, {
        DT_BACKEND_URL: window.location.origin,
        DT_SCRIPT_TOKEN: token,
        DT_CONNECTION_ID: tab.connectionId,
      })
    } catch (e) {
      setError(tabId, e instanceof Error ? e.message : String(e))
    }
  }, [tab, tabId, setRunning, appendConsoleOutput, setCompleted, setError])

  const stop = useCallback(async () => {
    if (!tab?.currentRunId) return
    try {
      await tauriStopScript(tab.currentRunId)
    } catch {
      // Ignore Tauri stop errors
    }

    // Sync CANCELLED status to backend
    try {
      const output = useScriptWorkbenchStore.getState().tabsById[tabId]?.consoleOutput
        .map((e) => e.text)
        .join('\n') ?? ''
      await scriptApi.runComplete(tab.currentRunId, {
        exitCode: -1,
        stdoutText: output,
      })
    } catch {
      appendConsoleOutput(tabId, {
        channel: 'stderr',
        text: 'Failed to notify server of cancellation',
        timestamp: Date.now(),
      })
    }
  }, [tab?.currentRunId, tabId, appendConsoleOutput])

  const cleanupListeners = useCallback(() => {
    unlistenRefs.current.forEach((fn) => fn())
    unlistenRefs.current = []
  }, [])

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      cleanupListeners()
    }
  }, [cleanupListeners])

  const isRunning = tab?.executeStatus === 'running'
  const canRun = tab?.connectionId != null && !isRunning && envChecked

  return {
    execute,
    stop,
    isRunning,
    canRun,
    envInfo,
  }
}
