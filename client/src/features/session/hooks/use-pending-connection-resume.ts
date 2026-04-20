import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { createSession } from '@/services/api/session'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { useHasActiveModel } from './use-has-active-model'
import { invalidateSessionLists } from './use-sessions'
import { useSessions } from './use-sessions'

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
  const { data: sessions } = useSessions('all')

  const currentSessionConnectionId = activeSessionId
    ? sessions?.find((session) => session.id === activeSessionId)?.connectionId
    : null

  useEffect(() => {
    if (pendingConnectionPrompt || !pendingPrompt || !activeConnectionId || !hasActiveModel) return
    if (pendingAction?.kind !== 'send') return
    if (activeSessionId && sessions === undefined) return

    if (activeSessionId && currentSessionConnectionId === activeConnectionId) {
      setPendingAction(null)
      return
    }

    const initialTitle = pendingPrompt.slice(0, 50)
    let cancelled = false

    void createSession(activeConnectionId, initialTitle).then((session) => {
      if (cancelled) return
      invalidateSessionLists(queryClient)
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
    sessions,
    currentSessionConnectionId,
    queryClient,
    openSession,
    setPendingAction,
  ])
}
