import { useQuery } from '@tanstack/react-query'
import { getHealth } from '@/services/api/health'

const healthQueryKey = ['system', 'opencode-health'] as const

export function useOpencodeHealth() {
  return useQuery({
    queryKey: healthQueryKey,
    queryFn: getHealth,
    staleTime: 30_000,
    retry: 1,
    // Poll fast while the bridge is still coming up so the "AI engine starting"
    // banner clears promptly once it reports ready; back off once settled.
    refetchInterval: (query) =>
      query.state.data?.status === 'ok' ? 30_000 : 2_000,
  })
}
