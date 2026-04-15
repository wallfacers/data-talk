export type MessageRole = 'user' | 'assistant' | 'system'

export type ChatMessage = {
  id: string
  role: MessageRole
  content: string
  createdAt: number
  pending?: boolean
  error?: string
}
