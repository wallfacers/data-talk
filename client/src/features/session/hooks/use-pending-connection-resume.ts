import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { createSession } from '@/services/api/session'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { useHasActiveModel } from './use-has-active-model'

export function usePendingConnectionResume() {
  const queryClient = useQueryClient()
  const pendingPrompt = useSessionStore((s) => s.pendingPrompt)
  const pendingConnectionPrompt = useSessionStore((s) => s.pendingConnectionPrompt)
  const pendingAction = useSessionStore((s) => s.pendingActionAfterConnectionPick)
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const openSession = useSessionStore((s) => s.openSession)
  const setPendingAction = useSessionStore((s) => s.setPendingActionAfterConnectionPick)
  const activeConnectionId = useConnectionStore((s) => s.activeConnectionId)
  const hasActiveModel = useHasActiveModel()

  useEffect(() => {
    if (pendingConnectionPrompt || !pendingPrompt || !activeConnectionId || activeSessionId || !hasActiveModel) return
    if (pendingAction?.kind !== 'send') return

    const initialTitle = pendingPrompt.slice(0, 50)
    let cancelled = false

    void createSession(activeConnectionId, initialTitle).then((session) => {
      if (cancelled) return
      queryClient.invalidateQueries({ queryKey: ['sessions', activeConnectionId] })
      openSession(session.id, session.hasEverSent)
      setPendingAction(null)
    })

    return () => {
      cancelled = true
    }
  }, [
    pendingConnectionPrompt,
    pendingPrompt,
    activeConnectionId,
    activeSessionId,
    hasActiveModel,
    pendingAction,
    queryClient,
    openSession,
    setPendingAction,
  ])
}
