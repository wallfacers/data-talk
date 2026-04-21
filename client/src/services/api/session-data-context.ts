import { http } from '@/services/http'

export type SessionDataContext = {
  sessionId: string
  connectionId: string | null
  connectionNameSnapshot: string | null
  database: string | null
  schema: string | null
  selectedLevel: 'connection' | 'database' | 'schema' | null
  updatedAt: number
}

export type SessionDataContextUpdateRequest = {
  connectionId?: string | null
  database?: string | null
  schema?: string | null
  selectedLevel?: 'connection' | 'database' | 'schema' | null
}

export type ResolveUseTargetRequest = {
  target: string
}

export type ResolveUseTargetResponse = {
  status: 'matched' | 'ambiguous' | 'not_found'
  context: SessionDataContext | null
  matchedTarget: ResolveUseTargetResponse.TargetOption | null
  candidates: ResolveUseTargetResponse.TargetOption[]
  suggestions: ResolveUseTargetResponse.TargetOption[]
  message: string | null
}

export namespace ResolveUseTargetResponse {
  export type TargetOption = {
    level: 'connection' | 'database' | 'schema'
    connectionId?: string | null
    connectionName?: string | null
    database?: string | null
    schema?: string | null
    label: string
  }
}

export function getSessionDataContext(sessionId: string) {
  return http.get(`sessions/${sessionId}/data-context`).json<SessionDataContext>()
}

export function setSessionDataContext(sessionId: string, update: SessionDataContextUpdateRequest) {
  return http.put(`sessions/${sessionId}/data-context`, { json: update }).json<SessionDataContext>()
}

export function resolveUseTarget(sessionId: string, target: string) {
  return http.post(`sessions/${sessionId}/data-context/resolve-use`, { json: { target } satisfies ResolveUseTargetRequest })
    .json<ResolveUseTargetResponse>()
}

export function validateSessionDataContext(sessionId: string) {
  return http.post(`sessions/${sessionId}/data-context/validate`).json<SessionDataContext>()
}
