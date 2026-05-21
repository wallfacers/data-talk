import type { ProviderDto } from './api'

export const SETTINGS_DIALOG_DIMENSIONS = 'w-[960px] h-[640px] max-w-[960px] max-h-[640px] sm:max-w-[960px]'

export function filterProvidersBySearch(
  providers: ProviderDto[],
  query: string,
  options?: { enabledOnly?: boolean }
): ProviderDto[] {
  const needle = query.trim().toLowerCase()
  return providers
    .filter(p => p.connected)
    .map(p => ({
      ...p,
      models: p.models
        .filter(m => options?.enabledOnly ? m.enabled : true)
        .filter(m => !needle || m.name.toLowerCase().includes(needle) || m.id.toLowerCase().includes(needle))
    }))
    .filter(p => p.models.length > 0)
}

export function parseModelId(id: string): { providerId: string; modelId: string } | null {
  const [providerId, ...rest] = id.split('/')
  const modelId = rest.join('/')
  if (!providerId || !modelId) return null
  return { providerId, modelId }
}

export function formatModelId(providerId: string, modelId: string): string {
  return `${providerId}/${modelId}`
}