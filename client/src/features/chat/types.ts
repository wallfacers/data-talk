import type { QueryResponse } from "@/services/api"

export interface ChatMessage {
  id: number
  role: "user" | "ai"
  content: string
}

export interface UseChatReturn {
  messages: ChatMessage[]
  isLoading: boolean
  error: string | null
  queryResult: QueryResponse | null
  sendMessage: (sql: string) => Promise<void>
}
