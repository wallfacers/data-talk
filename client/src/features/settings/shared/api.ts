export type ProviderDto = {
  id: string
  name: string
  connected: boolean
  models: { id: string; name: string; enabled: boolean }[]
}

export type ProvidersDto = { providers: ProviderDto[] }

export async function fetchProviders(): Promise<any> {
  const res = await fetch('/api/ai/providers')
  if (!res.ok) throw new Error(`fetchProviders ${res.status}`)
  return res.json()
}

export async function fetchProviderAuth(): Promise<Record<string, { type: string; label?: string }[]>> {
  const res = await fetch('/api/ai/providers/auth')
  if (!res.ok) throw new Error(`fetchProviderAuth ${res.status}`)
  return res.json()
}

export async function putCredentials(providerId: string, payload: unknown): Promise<void> {
  const res = await fetch(`/api/ai/providers/${providerId}/credentials`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!res.ok) throw new Error(`putCredentials ${res.status}: ${await res.text()}`)
}

export async function fetchModels(): Promise<ProvidersDto> {
  const res = await fetch('/api/ai/models')
  if (!res.ok) throw new Error(`fetchModels ${res.status}`)
  return res.json()
}

export async function patchModelEnabled(providerId: string, modelId: string, enabled: boolean): Promise<void> {
  const res = await fetch(`/api/ai/models/${providerId}/${modelId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  })
  if (!res.ok) throw new Error(`patchModelEnabled ${res.status}`)
}

export async function getCurrentModel(): Promise<{ modelId: string | null }> {
  const res = await fetch('/api/ai/current-model')
  if (!res.ok) throw new Error(`getCurrentModel ${res.status}`)
  return res.json()
}

export async function setCurrentModel(modelId: string | null): Promise<void> {
  const res = await fetch('/api/ai/current-model', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ modelId }),
  })
  if (!res.ok) throw new Error(`setCurrentModel ${res.status}`)
}

export const aiQueryKeys = {
  providers: ['ai', 'providers'] as const,
  providerAuth: ['ai', 'providers', 'auth'] as const,
  models: ['ai', 'models'] as const,
  currentModel: ['ai', 'current-model'] as const,
}
