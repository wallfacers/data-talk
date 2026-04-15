import { useState, useCallback } from "react"
import { executeQuery } from "@/services/api"
import type { QueryResponse } from "@/services/api"
import type { ChatMessage, UseChatReturn } from "@/features/chat/types"

export function useChat(): UseChatReturn {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 0,
      role: "ai",
      content: "你好！我是数据库助手，请输入你的查询。",
    },
  ])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [queryResult, setQueryResult] = useState<QueryResponse | null>(null)

  const sendMessage = useCallback(async (sql: string) => {
    if (!sql.trim() || isLoading) return

    const userMsg: ChatMessage = {
      id: Date.now(),
      role: "user",
      content: sql,
    }
    setMessages((prev) => [...prev, userMsg])
    setIsLoading(true)
    setError(null)

    try {
      const result = await executeQuery({
        connectionId: "demo",
        sql,
      })

      setQueryResult(result)

      const aiMsg: ChatMessage = {
        id: Date.now() + 1,
        role: "ai",
        content: `查询完成，返回 ${result.rowCount} 行数据，耗时 ${result.durationMs}ms。`,
      }
      setMessages((prev) => [...prev, aiMsg])
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : "未知错误"
      setError(errorMessage)
      setQueryResult(null)

      const errorMsg: ChatMessage = {
        id: Date.now() + 1,
        role: "ai",
        content: `查询失败: ${errorMessage}`,
      }
      setMessages((prev) => [...prev, errorMsg])
    } finally {
      setIsLoading(false)
    }
  }, [isLoading])

  return { messages, isLoading, error, queryResult, sendMessage }
}
