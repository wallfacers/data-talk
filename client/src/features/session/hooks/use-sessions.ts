import { useQuery, type QueryClient } from '@tanstack/react-query'
import { listSessions, type Session } from '@/services/api/session'
import { useConnectionStore } from '@/features/connection/store'

export type SessionsScope = 'active' | 'all'
export const STAGE_AI_SESSION_TITLE_PREFIX = '_stage-ai_'

export function isInternalStageAiSession(session: Pick<Session, 'title'>) {
  return session.title.startsWith(STAGE_AI_SESSION_TITLE_PREFIX)
}

export function filterVisibleSessions(sessions: Session[]) {
  return sessions.filter((session) => !isInternalStageAiSession(session))
}

export function getSessionsQueryKey(connectionId?: string | null) {
  return ['sessions', connectionId ?? null] as const
}

export function invalidateSessionLists(queryClient: QueryClient) {
  return queryClient.invalidateQueries({ queryKey: ['sessions'] })
}

export function patchCachedSessionLists(
  queryClient: QueryClient,
  sessionId: string,
  patch: Pick<Session, 'title' | 'titleLocked'>,
) {
  queryClient.setQueriesData<Session[]>(
    { queryKey: ['sessions'] },
    (old) => {
      if (!old) return old
      let changed = false
      const next = old.map((session) => {
        if (session.id !== sessionId) return session
        if (session.title === patch.title && session.titleLocked === patch.titleLocked) {
          return session
        }
        changed = true
        return { ...session, ...patch }
      })
      return changed ? next : old
    },
  )
}

export function useSessions(scope: SessionsScope = 'active') {
  const connectionId = useConnectionStore((s) => s.activeConnectionId)
  const scopedConnectionId = scope === 'all' ? null : connectionId

  return useQuery({
    queryKey: getSessionsQueryKey(scopedConnectionId),
    queryFn: async () => filterVisibleSessions(await listSessions(scopedConnectionId ?? undefined)),
  })
}
