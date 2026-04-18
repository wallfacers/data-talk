import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { http } from '@/services/http'
import { useActionRegistryStore } from '@/stores/action-registry-store'
import type { ActionDescriptor } from '@/features/actions/registry'

const bootstrapQueryKeys = {
  actions: ['bootstrap', 'actions'] as const,
}

export function useBootstrapActions() {
  const setDescriptors = useActionRegistryStore(s => s.setDescriptors)

  const { data } = useQuery({
    queryKey: bootstrapQueryKeys.actions,
    queryFn: () => http.get('actions').json<{ actions?: ActionDescriptor[] }>(),
    staleTime: Infinity,
  })

  useEffect(() => {
    if (!data) return
    const byId: Record<string, ActionDescriptor> = {}
    for (const d of data.actions ?? []) byId[d.id] = d
    setDescriptors(byId)
  }, [data, setDescriptors])
}
