import { useState, type FormEvent, type KeyboardEvent } from 'react'
import { Button } from '@/components/ui/button'
import { useChat } from '../hooks/use-chat'

export function ChatInput() {
  const [value, setValue] = useState('')
  const { sendMessage, isStreaming } = useChat()

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const text = value.trim()
    if (!text || isStreaming) return
    setValue('')
    await sendMessage(text)
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      void handleSubmit(e as unknown as FormEvent)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="border-t bg-background p-3">
      <div className="flex items-end gap-2">
        <textarea
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Enter 发送，Shift+Enter 换行"
          rows={2}
          className="flex-1 resize-none rounded-md border bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
        />
        <Button type="submit" disabled={!value.trim() || isStreaming}>
          发送
        </Button>
      </div>
    </form>
  )
}
