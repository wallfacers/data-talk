import { useQuery } from '@tanstack/react-query'
import { listSessions } from '@/services/api/session'
import { useConnectionStore } from '@/features/connection/store'

export function useSessions() {
  const connectionId = useConnectionStore((s) => s.activeConnectionId)

  return useQuery({
    queryKey: ['sessions', connectionId],
    queryFn: () => listSessions(connectionId ?? undefined),
    enabled: connectionId !== null,
  })
}
