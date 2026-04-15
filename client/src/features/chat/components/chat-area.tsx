import { ScrollArea } from "@/components/ui/scroll-area"
import { MessageItem } from "@/features/chat/components/message-item"
import { ChatInput } from "@/features/chat/components/chat-input"
import type { ChatMessage } from "@/features/chat/types"
import { useEffect, useRef } from "react"

interface ChatAreaProps {
  messages: ChatMessage[]
  isLoading: boolean
  onSend: (value: string) => void
}

export function ChatArea({ messages, isLoading, onSend }: ChatAreaProps) {
  const messagesEndRef = useRef<HTMLDivElement>(null)

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages, isLoading])

  return (
    <div className="flex h-full flex-col bg-background">
      <ScrollArea className="flex-1">
        <div className="mx-auto flex w-full max-w-3xl flex-col gap-6 p-6">
          {messages.map((msg) => (
            <MessageItem
              key={msg.id}
              role={msg.role}
              content={msg.content}
            />
          ))}
          {isLoading && (
            <MessageItem role="ai" content="Thinking..." loading />
          )}
          <div ref={messagesEndRef} />
        </div>
      </ScrollArea>
      <div className="p-6">
        <div className="mx-auto w-full max-w-3xl">
          <ChatInput onSubmit={onSend} disabled={isLoading} />
        </div>
      </div>
    </div>
  )
}
