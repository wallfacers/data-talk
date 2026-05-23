import { type ReactNode } from 'react'
import { useBackendStatus } from './use-backend-status'
import { selectGateView } from './startup-state'
import { WelcomeScreen } from './welcome-screen'
import { BackendErrorScreen } from './backend-error-screen'

/**
 * Two-phase reveal gate: shows the welcome surface while the bundled backend is
 * starting, the failure surface if startup failed, and the main app once the
 * backend HTTP server is healthy. Outside Tauri the backend is assumed up, so
 * children render immediately.
 */
export function AppGate({ children }: { children: ReactNode }) {
  const phase = useBackendStatus()
  const view = selectGateView(phase)

  if (view === 'welcome') return <WelcomeScreen />
  if (view === 'error' && phase.state === 'failed') {
    return <BackendErrorScreen message={phase.message} logPath={phase.log_path} />
  }
  return <>{children}</>
}
