import { ScrollArea } from "@/components/ui/scroll-area"
import { MessageItem } from "@/features/chat/components/message-item"
import { ChatInput } from "@/features/chat/components/chat-input"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { DatabaseIcon } from "lucide-react"
import type { ChatMessage } from "@/features/chat/types"

interface ChatAreaProps {
  messages: ChatMessage[]
  isLoading: boolean
  onSend: (value: string) => void
}

export function ChatArea({ messages, isLoading, onSend }: ChatAreaProps) {
  return (
    <div className="flex h-full flex-col bg-background">
      <ScrollArea className="flex-1">
        <div className="mx-auto flex w-full max-w-3xl flex-col gap-4 p-4 min-h-full">
          {messages.length === 0 ? (
            <div className="flex flex-1 flex-col items-center justify-center py-20">
              <Empty className="border-0">
                <EmptyHeader>
                  <div className="mb-4 flex size-16 items-center justify-center rounded-3xl bg-primary/5 text-primary shadow-sm ring-1 ring-primary/10">
                    <DatabaseIcon className="size-8" />
                  </div>
                  <EmptyTitle className="text-2xl font-bold tracking-tight">您好，我是数据对话助手</EmptyTitle>
                  <EmptyDescription className="text-base text-muted-foreground">
                    我可以帮您查询数据库、分析数据并提供洞察。<br />
                    试着输入：“查询最近一周的新用户”
                  </EmptyDescription>
                </EmptyHeader>
              </Empty>
            </div>
          ) : (
            <>
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
            </>
          )}
        </div>
      </ScrollArea>
      <div className="p-4 pt-0">
        <div className="mx-auto w-full max-w-3xl">
          <ChatInput onSubmit={onSend} disabled={isLoading} />
        </div>
      </div>
    </div>
  )
}
