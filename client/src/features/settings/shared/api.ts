import { http } from '@/services/http'
import type { AiModelsDto, AiProviderDto, AiModelDto } from '@/types/generated/api'

export type { AiProviderDto, AiModelDto } from '@/types/generated/api'

// Aliases matching frontend naming conventions
export type ProvidersDto = AiModelsDto
export type ProviderDto = AiProviderDto
export type ModelDto = AiModelDto

// Provider list (raw OpenCode response)
export type ProviderListItem = { id: string; name: string }
export type ProvidersListDto = { all: ProviderListItem[]; connected: string[] }

export async function fetchProviders(): Promise<ProvidersListDto> {
  return http.get('ai/providers').json<ProvidersListDto>()
}

export async function fetchProviderAuth(): Promise<Record<string, { type: string; label?: string }[]>> {
  return http.get('ai/providers/auth').json<Record<string, { type: string; label?: string }[]>>()
}

export async function putCredentials(providerId: string, payload: unknown): Promise<void> {
  await http.put(`ai/providers/${providerId}/credentials`, { json: payload })
}

export async function deleteCredentials(providerId: string): Promise<void> {
  await http.delete(`ai/providers/${providerId}/credentials`)
}

export async function fetchModels(): Promise<ProvidersDto> {
  return http.get('ai/models').json<ProvidersDto>()
}

export async function patchModelEnabled(providerId: string, modelId: string, enabled: boolean): Promise<void> {
  await http.patch(`ai/models/${providerId}/${modelId}`, { json: { enabled } })
}

export async function getCurrentModel(): Promise<{ modelId: string | null }> {
  return http.get('ai/current-model').json<{ modelId: string | null }>()
}

export async function setCurrentModel(modelId: string | null): Promise<void> {
  await http.patch('ai/current-model', { json: { modelId } })
}

export const aiQueryKeys = {
  providers: ['ai', 'providers'] as const,
  providerAuth: ['ai', 'providers', 'auth'] as const,
  models: ['ai', 'models'] as const,
  currentModel: ['ai', 'current-model'] as const,
}