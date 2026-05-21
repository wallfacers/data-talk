import { useCallback, useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useSessionStore } from '@/stores/session-store'
import {
  getSessionDataContext,
  listConnectionTargets as listConnectionTargetsApi,
  resolveUseTarget as resolveUseTargetApi,
  setSessionDataContext as setSessionDataContextApi,
  validateSessionDataContext as validateSessionDataContextApi,
  type ConnectionTargetsResponse,
  type ResolveUseTargetResponse,
  type SessionDataContext,
  type SessionDataContextUpdateRequest,
} from '@/services/api/session-data-context'
import { HTTPError } from '@/services/http'
import { translateMessage } from '@/i18n/messages'
import { getCurrentLanguage } from '@/stores/ui-settings-store'

const queryKey = (sessionId: string | null) => ['session-data-context', sessionId] as const

function noSessionError() {
  return new Error(translateMessage(getCurrentLanguage(), 'session.noActiveSession'))
}

function buildOptimisticContext(
  sessionId: string,
  previous: SessionDataContext | null,
  update: SessionDataContextUpdateRequest,
): SessionDataContext {
  const connectionId = update.connectionId === undefined
    ? previous?.connectionId ?? null
    : update.connectionId ?? null
  const database = update.database === undefined
    ? previous?.database ?? null
    : update.database ?? null
  const schema = update.schema === undefined
    ? previous?.schema ?? null
    : update.schema ?? null
  const selectedLevel = update.selectedLevel === undefined
    ? previous?.selectedLevel ?? null
    : update.selectedLevel ?? null

  return {
    sessionId,
    connectionId,
    connectionNameSnapshot: previous?.connectionId === connectionId ? previous.connectionNameSnapshot : null,
    database,
    schema,
    selectedLevel,
    updatedAt: Date.now(),
  }
}

export function useSessionDataContext(sessionId: string | null) {
  const queryClient = useQueryClient()
  const cachedContext = useSessionStore((s) => (sessionId ? s.dataContextBySession.get(sessionId) ?? null : null))
  const setCachedContext = useSessionStore((s) => s.setSessionDataContext)
  const clearCachedContext = useSessionStore((s) => s.clearSessionDataContext)

  const clearStaleSession = useCallback(() => {
    if (!sessionId) return
    const store = useSessionStore.getState()
    if (store.activeSessionId === sessionId) {
      store.closeSession()
    }
    store.clearSessionDataContext(sessionId)
    queryClient.removeQueries({ queryKey: queryKey(sessionId), exact: true })
  }, [sessionId, queryClient])

  const query = useQuery({
    queryKey: queryKey(sessionId),
    enabled: !!sessionId,
    staleTime: 0,
    retry: (failureCount, error) => {
      if (error instanceof HTTPError && error.response.status === 404) return false
      return failureCount < 1
    },
    queryFn: () => getSessionDataContext(sessionId ?? ''),
  })

  useEffect(() => {
    if (query.error instanceof HTTPError && query.error.response.status === 404) {
      clearStaleSession()
    }
  }, [query.error, clearStaleSession])

  useEffect(() => {
    if (!sessionId || !query.data) return
    setCachedContext(query.data)
  }, [query.data, sessionId, setCachedContext])

  const context = query.data ?? cachedContext

  const resolveUseTarget = useCallback(
    async (target: string, sessionIdOverride?: string | null): Promise<ResolveUseTargetResponse> => {
      const sid = sessionIdOverride ?? sessionId
      if (!sid) throw noSessionError()
      return resolveUseTargetApi(sid, target)
    },
    [sessionId],
  )

  const listConnectionTargets = useCallback(
    async (connectionId?: string | null, sessionIdOverride?: string | null): Promise<ConnectionTargetsResponse> => {
      const sid = sessionIdOverride ?? sessionId
      if (!sid) throw noSessionError()
      return listConnectionTargetsApi(sid, connectionId)
    },
    [sessionId],
  )

  const setSessionDataContext = useCallback(
    async (update: SessionDataContextUpdateRequest, sessionIdOverride?: string | null): Promise<SessionDataContext> => {
      const sid = sessionIdOverride ?? sessionId
      if (!sid) throw noSessionError()
      const previous = (queryClient.getQueryData(queryKey(sid)) as SessionDataContext | undefined)
        ?? (sid === sessionId ? cachedContext : useSessionStore.getState().dataContextBySession.get(sid))
        ?? null
      const optimistic = buildOptimisticContext(sid, previous, update)

      setCachedContext(optimistic)
      queryClient.setQueryData(queryKey(sid), optimistic)

      try {
        const next = await setSessionDataContextApi(sid, update)
        setCachedContext(next)
        queryClient.setQueryData(queryKey(sid), next)
        return next
      } catch (error) {
        if (previous) {
          setCachedContext(previous)
          queryClient.setQueryData(queryKey(sid), previous)
        } else {
          clearCachedContext(sid)
          queryClient.removeQueries({ queryKey: queryKey(sid), exact: true })
        }
        throw error
      }
    },
    [cachedContext, clearCachedContext, queryClient, sessionId, setCachedContext],
  )

  const validateSessionDataContext = useCallback(
    async (sessionIdOverride?: string | null): Promise<SessionDataContext> => {
      const sid = sessionIdOverride ?? sessionId
      if (!sid) throw noSessionError()
      const next = await validateSessionDataContextApi(sid)
      setCachedContext(next)
      queryClient.setQueryData(queryKey(sid), next)
      return next
    },
    [queryClient, sessionId, setCachedContext],
  )

  const refresh = useCallback(async () => {
    if (!sessionId) return undefined
    return query.refetch()
  }, [query, sessionId])

  return {
    context,
    isLoading: query.isLoading,
    error: query.error ?? null,
    refresh,
    listConnectionTargets,
    resolveUseTarget,
    setSessionDataContext,
    validateSessionDataContext,
  }
}
