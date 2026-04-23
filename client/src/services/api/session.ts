import { http } from '@/services/http'
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

export function deleteSession(id: string) {
  return http.delete(`sessions/${id}`).then(() => undefined)
}

export function clearAllSessions() {
  return http.delete('sessions').then(() => undefined)
}
