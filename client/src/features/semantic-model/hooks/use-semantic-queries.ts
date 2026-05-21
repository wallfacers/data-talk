import { useQuery } from '@tanstack/react-query'
import { fetchPendingList, fetchVerifiedQueries, fetchDomains } from '../api/semantic-api'

export function useSemanticPendingQuery(connectionId: string | null) {
  return useQuery({
    queryKey: ['semantic-pending', connectionId],
    queryFn: () => fetchPendingList(connectionId!),
    enabled: !!connectionId,
    refetchInterval: 15_000,
  })
}

export function useSemanticVqQuery(connectionId: string | null, topK = 20) {
  return useQuery({
    queryKey: ['semantic-vq', connectionId, topK],
    queryFn: () => fetchVerifiedQueries(connectionId!, topK),
    enabled: !!connectionId,
  })
}

export function useSemanticDomainsQuery(connectionId: string | null) {
  return useQuery({
    queryKey: ['semantic-domains', connectionId],
    queryFn: () => fetchDomains(connectionId!),
    enabled: !!connectionId,
  })
}
