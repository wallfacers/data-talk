import { useEffect, useState, type FormEvent, type KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { Button } from '@/components/ui/button'
import { useSessionStore } from '@/stores/session-store'
import { useChannel } from '@/services/channel/use-channel'
import { classifyIntent } from '@/features/actions/classify-intent'
import { useConnectionStore } from '@/features/connection/store'
import { createTextPart } from '@/services/channel/types'

function useComposerSlot(): HTMLElement | null {
  const [slot, setSlot] = useState<HTMLElement | null>(
    typeof document !== 'undefined' ? document.getElementById('composer-slot') : null,
  )

  useEffect(() => {
    if (slot) return
    // Poll once per animation frame until the slot node mounts; stop as soon
    // as we find it. In practice this resolves within 1-2 frames of the
    // parent's first commit.
    let raf = 0
    const tick = () => {
      const el = document.getElementById('composer-slot')
      if (el) { setSlot(el); return }
      raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [slot])

  return slot
}

export function PromptComposer() {
  const slot = useComposerSlot()
  if (!slot) return null
  return createPortal(<Inner />, slot)
}

function Inner() {
  const [text, setText] = useState('')
  const { sendMessage, abort, isStreaming } = useChannel()
  const activeConn = useConnectionStore((s) => s.activeConnectionId)
  const setPendingPrompt = useSessionStore((s) => s.setPendingPrompt)
  const setPendingConnectionPrompt = useSessionStore((s) => s.setPendingConnectionPrompt)
  const activeSessionId = useSessionStore((s) => s.activeSessionId)

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const t = text.trim()
    if (!t || isStreaming || !activeSessionId) return
    setText('')
    if (classifyIntent(t) === 'db_related' && !activeConn) {
      setPendingPrompt(t)
      setPendingConnectionPrompt(true)
      return
    }
    await sendMessage([createTextPart(activeSessionId, t)])
  }

  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      void onSubmit(e as unknown as FormEvent)
    }
  }

  return (
    <form onSubmit={onSubmit} className="border-t bg-background p-3">
      <div className="flex items-end gap-2">
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKey}
          placeholder="Enter 发送，Shift+Enter 换行"
          rows={2}
          disabled={!activeSessionId}
          className="flex-1 resize-none rounded-md border bg-background px-3 py-2 text-sm
                     focus:outline-none focus:ring-2 focus:ring-ring"
        />
        {isStreaming ? (
          <Button type="button" variant="destructive" onClick={() => void abort()}>
            停止
          </Button>
        ) : (
          <Button type="submit" disabled={!text.trim() || !activeSessionId}>
            发送
          </Button>
        )}
      </div>
    </form>
  )
}
