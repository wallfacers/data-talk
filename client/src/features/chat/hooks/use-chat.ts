import { useCallback } from 'react'
import { useChatStore } from '../store'
import type { ChatMessage } from '../types'

export function useChat() {
  const messages = useChatStore((s) => s.messages)
  const isStreaming = useChatStore((s) => s.isStreaming)
  const addMessage = useChatStore((s) => s.addMessage)

  const sendMessage = useCallback(
    async (content: string) => {
      const userMsg: ChatMessage = {
        id: crypto.randomUUID(),
        role: 'user',
        content,
        createdAt: Date.now(),
      }
      addMessage(userMsg)
    },
    [addMessage],
  )

  return { messages, isStreaming, sendMessage }
}
