import { useQuery } from '@tanstack/react-query'
import { listConnections } from '@/services/api/connection'

export function useConnections() {
  return useQuery({
    queryKey: ['connections'],
    queryFn: listConnections,
  })
}
