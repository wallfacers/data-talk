import { create } from 'zustand'
import type { Provider, ModelConfigState } from './types'
import { MOCK_PROVIDERS } from './mock-data'

interface ModelConfigStore extends ModelConfigState {
  connectProvider: (providerId: string, apiKey: string) => void
  disconnectProvider: (providerId: string) => void
  setModelVisibility: (providerId: string, modelId: string, visible: boolean) => void
  setCurrentModel: (providerId: string, modelId: string) => void
  addCustomProvider: (provider: Provider) => void
  removeCustomProvider: (providerId: string) => void
}

export const useModelConfigStore = create<ModelConfigStore>((set) => ({
  providers: MOCK_PROVIDERS,

  connectProvider: (providerId, apiKey) =>
    set((state) => ({
      providers: state.providers.map((p) =>
        p.id === providerId ? { ...p, connected: true, apiKey, source: 'api' as const } : p
      ),
    })),

  disconnectProvider: (providerId) =>
    set((state) => ({
      providers: state.providers.map((p) =>
        p.id === providerId ? { ...p, connected: false, apiKey: undefined, source: undefined } : p
      ),
    })),

  setModelVisibility: (providerId, modelId, visible) =>
    set((state) => ({
      providers: state.providers.map((p) =>
        p.id === providerId
          ? {
              ...p,
              models: p.models.map((m) => (m.id === modelId ? { ...m, visible } : m)),
            }
          : p
      ),
    })),

  setCurrentModel: (providerId, modelId) =>
    set({ currentModel: { providerId, modelId } }),

  addCustomProvider: (provider) =>
    set((state) => ({
      providers: [...state.providers, provider],
    })),

  removeCustomProvider: (providerId) =>
    set((state) => ({
      providers: state.providers.filter((p) => p.id !== providerId),
    })),
}))
