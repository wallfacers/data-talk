import { http, HTTPError } from '@/services/http'

export type AuthScheme = 'none' | 'bearer' | 'api_key_header' | 'api_key_query' | 'basic'

export interface CredentialView {
  id: string
  name: string
  authScheme: AuthScheme
  configNonSecret: Record<string, string>
  hasSecret: boolean
  createdAt: number
  updatedAt: number
}

export interface CredentialCreateRequest {
  name: string
  authScheme: AuthScheme
  configNonSecret: Record<string, string>
  secret: string | null
}

export interface CredentialDeleteBlocked {
  credentialId: string
  referencingJobCount: number
}

export async function listCredentials(): Promise<CredentialView[]> {
  const data = await http.get('ingestion/credentials').json<{ items: CredentialView[] }>()
  return data.items
}

export async function createCredential(req: CredentialCreateRequest): Promise<{ id: string }> {
  return http.post('ingestion/credentials', { json: req }).json<{ id: string }>()
}

export async function updateCredential(id: string, req: CredentialCreateRequest): Promise<{ id: string }> {
  return http.put(`ingestion/credentials/${id}`, { json: req }).json<{ id: string }>()
}

export async function deleteCredential(id: string, force = false): Promise<CredentialDeleteBlocked | void> {
  try {
    await http.delete(`ingestion/credentials/${id}${force ? '?force=true' : ''}`)
  } catch (error: unknown) {
    if (error instanceof HTTPError && error.response.status === 409) {
      return error.response.json() as Promise<CredentialDeleteBlocked>
    }
    throw error
  }
}

export const credentialsKey = ['ingestion-credentials'] as const
