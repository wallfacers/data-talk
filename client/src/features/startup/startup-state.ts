export type BackendPhase =
  | { state: 'starting' }
  | { state: 'ready' }
  | { state: 'failed'; message: string; log_path: string }

export type GateView = 'welcome' | 'app' | 'error'

export function selectGateView(phase: BackendPhase): GateView {
  switch (phase.state) {
    case 'ready':
      return 'app'
    case 'failed':
      return 'error'
    case 'starting':
    default:
      return 'welcome'
  }
}

export function isTauriRuntime(): boolean {
  return typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window
}
