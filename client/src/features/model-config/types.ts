export interface Provider {
  id: string
  name: string
  type: 'builtin' | 'custom'
  apiKey?: string
  baseURL?: string
  headers?: Record<string, string>
  models: Model[]
  source?: 'env' | 'api' | 'config'
  connected: boolean
}

export interface Model {
  id: string
  name: string
  providerId: string
  visible: boolean
  latest?: boolean
}

export interface ModelConfigState {
  providers: Provider[]
  currentModel?: { providerId: string; modelId: string }
}
