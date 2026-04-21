import { useCallback, useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useSessionStore } from '@/stores/session-store'
import {
  getSessionDataContext,
  resolveUseTarget as resolveUseTargetApi,
  setSessionDataContext as setSessionDataContextApi,
  validateSessionDataContext as validateSessionDataContextApi,
  type ResolveUseTargetResponse,
  type SessionDataContext,
  type SessionDataContextUpdateRequest,
} from '@/services/api/session-data-context'

const queryKey = (sessionId: string | null) => ['session-data-context', sessionId] as const

function noSessionError() {
  return new Error('No active session')
}

export function useSessionDataContext(sessionId: string | null) {
  const queryClient = useQueryClient()
  const cachedContext = useSessionStore((s) => (sessionId ? s.dataContextBySession.get(sessionId) ?? null : null))
  const setCachedContext = useSessionStore((s) => s.setSessionDataContext)

  const query = useQuery({
    queryKey: queryKey(sessionId),
    enabled: !!sessionId,
    staleTime: 0,
    retry: 1,
    queryFn: () => getSessionDataContext(sessionId ?? ''),
  })

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

  const setSessionDataContext = useCallback(
    async (update: SessionDataContextUpdateRequest, sessionIdOverride?: string | null): Promise<SessionDataContext> => {
      const sid = sessionIdOverride ?? sessionId
      if (!sid) throw noSessionError()
      const next = await setSessionDataContextApi(sid, update)
      setCachedContext(next)
      queryClient.setQueryData(queryKey(sid), next)
      return next
    },
    [queryClient, sessionId, setCachedContext],
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
    resolveUseTarget,
    setSessionDataContext,
    validateSessionDataContext,
  }
}
