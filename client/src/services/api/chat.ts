import { http } from '@/services/http'

export type MessageRole = 'user' | 'assistant' | 'system'

export type ChatMessage = {
  id: string
  sessionId: string
  role: MessageRole
  content: string
  createdAt: string
}

export function listMessages(sessionId: string) {
  return http.get(`sessions/${sessionId}/messages`).json<ChatMessage[]>()
}

export function sendMessage(sessionId: string, content: string) {
  return http
    .post(`sessions/${sessionId}/messages`, { json: { content } })
    .json<ChatMessage>()
}
