import { http } from '@/services/http'

export type BangQueryMessageCreateResponse = {
  id: string
  sessionId: string
  createdAt: number
  kind: string
}

export function createBangQueryMessage(sessionId: string, text: string, createdAt: number) {
  return http
    .post(`sessions/${sessionId}/messages/bang-query`, {
      json: { text, createdAt },
    })
    .json<BangQueryMessageCreateResponse>()
}
