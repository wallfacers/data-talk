import type { MessageRole } from '@/services/api/chat'

export type { MessageRole }

export type ChatMessage = {
  id: string
  role: MessageRole
  content: string
  createdAt: number
  pending?: boolean
  error?: string
}
