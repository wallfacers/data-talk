import { http } from '@/services/http'

export type Session = {
  id: string
  connectionId: string
  title: string
  hasEverSent: boolean
  createdAt: number
  updatedAt: number
}

export function listSessions(connectionId?: string) {
  const search = connectionId ? { connectionId } : undefined
  return http.get('sessions', { searchParams: search }).json<Session[]>()
}

export function createSession(connectionId: string, title: string) {
  return http.post('sessions', { json: { connectionId, title } }).json<Session>()
}
