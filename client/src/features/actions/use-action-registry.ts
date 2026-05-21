import { useActionRegistryStore } from '@/stores/action-registry-store'

export function useActionRegistry() {
  return useActionRegistryStore(s => ({
    descriptors: s.descriptors,
  }))
}
