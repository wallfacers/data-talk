// 预览用的 composer：不走 portal，直接渲染在当前容器里。
// 这样不依赖 #composer-slot 的跨子树迁移，避免任何 slot 侦测 / FLIP 的副作用。

import { useState, type FormEvent, type KeyboardEvent } from 'react'
import { Button } from '@/components/ui/button'

type Props = {
  onSubmit: (text: string) => void
  isStreaming: boolean
  placeholder?: string
}

export function PreviewComposer({ onSubmit, isStreaming, placeholder }: Props) {
  const [text, setText] = useState('')

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const t = text.trim()
    if (!t || isStreaming) return
    setText('')
    onSubmit(t)
  }

  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      submit(e as unknown as FormEvent)
    }
  }

  return (
    <form onSubmit={submit} className="border-t bg-background p-3">
      <div className="flex items-end gap-2">
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKey}
          placeholder={placeholder ?? 'Enter 发送，Shift+Enter 换行'}
          rows={2}
          className="flex-1 resize-none rounded-md border bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
        />
        <Button type="submit" disabled={!text.trim() || isStreaming}>
          {isStreaming ? '演示中…' : '发送'}
        </Button>
      </div>
    </form>
  )
}
