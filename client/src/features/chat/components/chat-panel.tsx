import { MessageList } from './message-list'
import { ChatInput } from './chat-input'

export function ChatPanel() {
  return (
    <div className="flex h-full flex-col">
      <header className="border-b px-4 py-3">
        <h2 className="text-sm font-semibold">对话</h2>
      </header>
      <MessageList />
      <ChatInput />
    </div>
  )
}
