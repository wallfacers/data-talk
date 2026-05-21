import { http } from '@/services/http'
import { HTTPError } from 'ky'
import type { Session } from '@/types/generated/api'

export type { Session } from '@/types/generated/api'

export function listSessions(connectionId?: string) {
  const search = connectionId ? { connectionId } : undefined
  return http.get('sessions', { searchParams: search }).json<Session[]>()
}

export function createSession(connectionId?: string, title?: string) {
  return http.post('sessions', { json: { connectionId, title } }).json<Session>()
}

export function renameSession(id: string, title: string) {
  return http.patch(`sessions/${id}`, { json: { title } }).json<Session>()
}

export interface BlockedByCandidates {
  sessionId: string
  candidates: Array<{
    id: string
    filename: string
    kind: string
    sizeBytes: number
    title: string | null
    summary: string | null
  }>
}

export async function deleteSession(id: string, options?: { force?: boolean }): Promise<BlockedByCandidates | void> {
  const params = options?.force ? { force: 'true' } : undefined
  try {
    await http.delete(`sessions/${id}`, { searchParams: params })
  } catch (error: unknown) {
    if (error instanceof HTTPError && error.response.status === 409) {
      return error.response.json() as Promise<BlockedByCandidates>
    }
    throw error
  }
}

export function clearAllSessions() {
  return http.delete('sessions').then(() => undefined)
}
