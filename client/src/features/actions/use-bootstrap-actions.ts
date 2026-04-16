import { useEffect } from 'react'
import { http } from '@/services/http'
import { useActionRegistryStore } from '@/stores/action-registry-store'
import type { ActionDescriptor } from '@/features/actions/registry'

export function useBootstrapActions() {
  const setDescriptors = useActionRegistryStore(s => s.setDescriptors)
  useEffect(() => {
    void http.get('actions').json<{ actions?: ActionDescriptor[] }>().then(j => {
      const byId: Record<string, ActionDescriptor> = {}
      for (const d of j.actions ?? []) byId[d.id] = d
      setDescriptors(byId)
    })
  }, [setDescriptors])
}
