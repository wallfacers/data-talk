import { cn } from '@/lib/utils'
import type { ChatMessage } from '../types'

export function MessageItem({ message }: { message: ChatMessage }) {
  const isUser = message.role === 'user'
  return (
    <div className={cn('flex w-full', isUser ? 'justify-end' : 'justify-start')}>
      <div
        className={cn(
          'max-w-[80%] rounded-lg px-4 py-2 text-sm',
          isUser ? 'bg-primary text-primary-foreground' : 'bg-muted',
          message.error && 'border border-destructive',
        )}
      >
        {message.error ? `⚠️ ${message.error}` : message.content}
      </div>
    </div>
  )
}
