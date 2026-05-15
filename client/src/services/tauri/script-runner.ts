import { invoke } from '@tauri-apps/api/core'
import { listen, type UnlistenFn } from '@tauri-apps/api/event'

export interface EnvInfo {
  python: string | null
  node: string | null
}

export interface ScriptOutputEvent {
  run_id: string
  channel: 'stdout' | 'stderr'
  data: string
}

export interface ScriptCompletedEvent {
  run_id: string
  exit_code: number
}

export function detectScriptEnv(): Promise<EnvInfo> {
  return invoke<EnvInfo>('detect_script_env')
}

export function runScript(
  runId: string,
  scriptContent: string,
  language: 'python' | 'javascript',
  envVars?: Record<string, string>,
): Promise<void> {
  return invoke('run_script', {
    runId,
    scriptContent,
    language,
    envVars: envVars ?? null,
  })
}

export function stopScript(runId: string): Promise<void> {
  return invoke('stop_script', { runId })
}

export function onScriptOutput(
  runId: string,
  handler: (event: ScriptOutputEvent) => void,
): Promise<UnlistenFn> {
  return listen<ScriptOutputEvent>('script-output', (e) => {
    if (e.payload.run_id === runId) {
      handler(e.payload)
    }
  })
}

export function onScriptCompleted(
  runId: string,
  handler: (event: ScriptCompletedEvent) => void,
): Promise<UnlistenFn> {
  return listen<ScriptCompletedEvent>('script-completed', (e) => {
    if (e.payload.run_id === runId) {
      handler(e.payload)
    }
  })
}
