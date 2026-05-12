import { useQuery } from '@tanstack/react-query'
import { listIngestionJobs, ingestionJobsKey } from '../api/ingestion-api'

export function useIngestionJobsQuery(params?: {
  status?: string
  connectionId?: string
  limit?: number
  offset?: number
}) {
  return useQuery({
    queryKey: [...ingestionJobsKey, params],
    queryFn: () => listIngestionJobs(params),
    refetchInterval: 5000,
  })
}
