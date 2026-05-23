import { useEffect, useState } from 'react'
import { type BackendPhase, isTauriRuntime } from './startup-state'

const STATUS_EVENT = 'backend://status'

/**
 * Tracks the bundled-backend startup phase exposed by the Tauri Rust layer.
 *
 * Outside Tauri (browser dev server, vitest) the backend runs separately and is
 * assumed up, so this reports `ready` immediately. Inside Tauri it subscribes to
 * the `backend://status` event AND pulls `get_backend_status` on mount — the pull
 * covers the race where `ready` was emitted before the listener attached.
 */
export function useBackendStatus(): BackendPhase {
  const [phase, setPhase] = useState<BackendPhase>(() =>
    isTauriRuntime() ? { state: 'starting' } : { state: 'ready' },
  )

  useEffect(() => {
    if (!isTauriRuntime()) return
    let cancelled = false
    let unlisten: (() => void) | undefined

    ;(async () => {
      const [{ invoke }, { listen }] = await Promise.all([
        import('@tauri-apps/api/core'),
        import('@tauri-apps/api/event'),
      ])
      // Subscribe before pulling so no transition is lost between the two.
      unlisten = await listen<BackendPhase>(STATUS_EVENT, (event) => {
        if (!cancelled) setPhase(event.payload)
      })
      try {
        const current = await invoke<BackendPhase>('get_backend_status')
        if (!cancelled) setPhase(current)
      } catch {
        // Keep the optimistic `starting` state; the event will correct it.
      }
    })()

    return () => {
      cancelled = true
      unlisten?.()
    }
  }, [])

  return phase
}

export async function retryBackendStartup(): Promise<void> {
  if (!isTauriRuntime()) return
  const { invoke } = await import('@tauri-apps/api/core')
  await invoke('restart_backend')
}
