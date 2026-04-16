import { useState, type FormEvent, type KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { Button } from '@/components/ui/button'
import { useSessionStore } from '@/stores/session-store'
import { useChannel } from '@/services/channel/use-channel'
import { classifyIntent } from '@/features/actions/classify-intent'
import { useConnectionStore } from '@/features/connection/store'

export function PromptComposer() {
  const slot = typeof document !== 'undefined' ? document.getElementById('composer-slot') : null
  if (!slot) return null
  return createPortal(<Inner />, slot)
}

function Inner() {
  const [text, setText] = useState('')
  const { sendMessage, isStreaming } = useChannel()
  const activeConn = useConnectionStore(s => s.activeConnectionId)
  const setPendingPrompt = useSessionStore(s => s.setPendingPrompt)
  const setPendingConnectionPrompt = useSessionStore(s => s.setPendingConnectionPrompt)

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const t = text.trim()
    if (!t || isStreaming) return
    setText('')
    if (classifyIntent(t) === 'db_related' && !activeConn) {
      setPendingPrompt(t)
      setPendingConnectionPrompt(true)
      return
    }
    await sendMessage([{ type: 'text', id: crypto.randomUUID(),
      sessionID: '', messageID: '', text: t, metadata: {} } as any])
  }

  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); void onSubmit(e as unknown as FormEvent) }
  }

  return (
    <form onSubmit={onSubmit} className="border-t bg-background p-3">
      <div className="flex items-end gap-2">
        <textarea value={text} onChange={e => setText(e.target.value)} onKeyDown={onKey}
          placeholder="Enter 发送，Shift+Enter 换行" rows={2}
          className="flex-1 resize-none rounded-md border bg-background px-3 py-2 text-sm
                     focus:outline-none focus:ring-2 focus:ring-ring"/>
        <Button type="submit" disabled={!text.trim() || isStreaming}>发送</Button>
      </div>
    </form>
  )
}
