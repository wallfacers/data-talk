import { create } from 'zustand'
import type { ActionDescriptor } from '@/features/actions/registry'

type ActionRegistryState = {
  descriptors: Record<string, ActionDescriptor>
  setDescriptors: (descriptors: Record<string, ActionDescriptor>) => void
  setDescriptor: (id: string, descriptor: ActionDescriptor) => void
}

export const useActionRegistryStore = create<ActionRegistryState>((set) => ({
  descriptors: {},
  setDescriptors: (descriptors) => set({ descriptors }),
  setDescriptor: (id, descriptor) => set(s => ({
    descriptors: { ...s.descriptors, [id]: descriptor }
  })),
}))
