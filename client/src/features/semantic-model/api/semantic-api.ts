import { http } from '@/services/http'

export interface SemanticPendingItem {
  domain: string
}

export interface PendingListResponse {
  pending: SemanticPendingItem[]
}

export interface VerifiedQueryItem {
  id: string
  question: string
  sql: string
  modelRef: string
  hitCount: number
  stale: boolean
}

export interface DomainListResponse {
  domains: string[]
}

export async function fetchPendingList(connectionId: string): Promise<PendingListResponse> {
  return http.get(`/api/semantic/${connectionId}/pending`).json()
}

export async function acceptPending(connectionId: string, domain: string): Promise<{ status: string }> {
  return http.post(`/api/semantic/${connectionId}/pending/${domain}/accept`).json()
}

export async function rejectPending(connectionId: string, domain: string): Promise<{ status: string }> {
  return http.post(`/api/semantic/${connectionId}/pending/${domain}/reject`).json()
}

export async function fetchVerifiedQueries(connectionId: string, topK = 20): Promise<{ queries: VerifiedQueryItem[] }> {
  return http.get(`/api/semantic/${connectionId}/verified-queries?topK=${topK}`).json()
}

export async function recordVerifiedQuery(
  connectionId: string,
  body: { question: string; sql: string; modelRef: string }
): Promise<{ id: string }> {
  return http.post(`/api/semantic/${connectionId}/verified-query`, { json: body }).json()
}

export async function fetchDomains(connectionId: string): Promise<DomainListResponse> {
  return http.get(`/api/semantic/${connectionId}/domains`).json()
}

export async function createFromTemplate(
  connectionId: string,
  templateName: string
): Promise<{ domain: string; status: string }> {
  return http.post(`/api/semantic/${connectionId}/from-template`, { json: { templateName } }).json()
}
