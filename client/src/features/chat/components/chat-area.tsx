import { ScrollArea } from "@/components/ui/scroll-area"
import { MessageItem } from "@/features/chat/components/message-item"
import { ChatInput } from "@/features/chat/components/chat-input"
import type { ChatMessage } from "@/features/chat/types"

interface ChatAreaProps {
  messages: ChatMessage[]
  isLoading: boolean
  onSend: (value: string) => void
}

export function ChatArea({ messages, isLoading, onSend }: ChatAreaProps) {
  return (
    <div className="flex h-full flex-col">
      <ScrollArea className="flex-1 p-4">
        <div className="flex flex-col gap-4">
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
        </div>
      </ScrollArea>
      <div className="border-t p-4">
        <ChatInput onSubmit={onSend} disabled={isLoading} />
      </div>
    </div>
  )
}
