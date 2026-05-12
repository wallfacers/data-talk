import { useQuery } from '@tanstack/react-query'
import { getPayloadPreview, payloadPreviewKey } from '../api/ingestion-api'

export function usePayloadPreviewQuery(jobId: string | null | undefined, limit = 100) {
  return useQuery({
    queryKey: payloadPreviewKey(jobId!),
    queryFn: () => getPayloadPreview(jobId!, limit),
    enabled: !!jobId,
  })
}
