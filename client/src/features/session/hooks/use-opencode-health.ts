import { useQuery } from '@tanstack/react-query'
import { getHealth } from '@/services/api/health'

const healthQueryKey = ['system', 'opencode-health'] as const

export function useOpencodeHealth() {
  return useQuery({
    queryKey: healthQueryKey,
    queryFn: getHealth,
    staleTime: 30_000,
    retry: 1,
    refetchInterval: 30_000,
  })
}
