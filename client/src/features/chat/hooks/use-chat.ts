import { useCallback } from 'react'
import { useChatStore } from '../store'
import type { ChatMessage } from '../types'

function uid() {
  return Math.random().toString(36).slice(2, 10)
}

export function useChat() {
  const messages = useChatStore((s) => s.messages)
  const isStreaming = useChatStore((s) => s.isStreaming)
  const addMessage = useChatStore((s) => s.addMessage)

  const sendMessage = useCallback(
    async (content: string) => {
      const userMsg: ChatMessage = {
        id: uid(),
        role: 'user',
        content,
        createdAt: Date.now(),
      }
      addMessage(userMsg)
      // 真正的 AI 调用等后端 OpenCode 接通后再接；骨架期先留空。
    },
    [addMessage],
  )

  return { messages, isStreaming, sendMessage }
}
