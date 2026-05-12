import { useQuery } from '@tanstack/react-query'
import { getIngestionJob, ingestionJobKey } from '../api/ingestion-api'

export function useIngestionJobQuery(id: string | null | undefined) {
  return useQuery({
    queryKey: ingestionJobKey(id!),
    queryFn: () => getIngestionJob(id!),
    enabled: !!id,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      if (status === 'fetched' || status === 'mapped' || status === 'confirmed' || status === 'writing') return 3000
      return false
    },
  })
}
