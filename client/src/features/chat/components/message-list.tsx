import { useChatStore } from '../store'
import { MessageItem } from './message-item'

export function MessageList() {
  const messages = useChatStore((s) => s.messages)

  if (messages.length === 0) {
    return (
      <div className="flex flex-1 items-center justify-center text-muted-foreground">
        <p className="text-sm">问点什么，比如"查询用户表最近一周的注册趋势"</p>
      </div>
    )
  }

  return (
    <div className="flex flex-1 flex-col gap-3 overflow-y-auto p-4">
      {messages.map((m) => (
        <MessageItem key={m.id} message={m} />
      ))}
    </div>
  )
}
