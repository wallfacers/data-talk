import { useEffect } from 'react'
import { useActionRegistryStore } from '@/stores/action-registry-store'

export function useBootstrapActions() {
  const setDescriptors = useActionRegistryStore(s => s.setDescriptors)
  useEffect(() => {
    void fetch('/api/actions').then(r => r.json()).then(j => {
      const byId: Record<string, any> = {}
      for (const d of j.actions ?? []) byId[d.id] = d
      setDescriptors(byId)
    })
  }, [setDescriptors])
}
