"use client"

import { useLayoutEffect, useState, type FormEvent, type KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { ArrowUpIcon, Loader2Icon } from 'lucide-react'
import { toast } from 'sonner'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import {
  InputGroup,
  InputGroupAddon,
  InputGroupText,
  InputGroupTextarea,
} from '@/components/ui/input-group'
import { ModelPicker } from './model-picker/model-picker'
import { Switch } from '@/components/ui/switch'
import { useSessionStore } from '@/stores/session-store'
import { useConnectionStore } from '@/features/connection/store'
import { useChannel } from '@/services/channel/use-channel'
import { createTextPart } from '@/services/channel/types'
import { createSession } from '@/services/api/session'
import { StageToggleButton } from '@/features/stage/components/stage-toggle-button'
import { useHasActiveModel } from './hooks/use-has-active-model'

function useComposerSlot(): HTMLElement | null {
  const [slot, setSlot] = useState<HTMLElement | null>(null)
  const activeSessionId = useSessionStore((s) => s.activeSessionId)

  useLayoutEffect(() => {
    const el = document.getElementById('composer-slot')
    if (el !== slot) setSlot(el)
  }, [activeSessionId, slot])

  return slot
}

export function PromptComposer() {
  const slot = useComposerSlot()
  if (!slot) return null
  return createPortal(<InnerComposer />, slot)
}

function InnerComposer() {
  const [text, setText] = useState('')
  const [autoMode, setAutoMode] = useState(true)
  const { sendMessage, abort, isStreaming } = useChannel()
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const openSession = useSessionStore((s) => s.openSession)
  const setPendingPrompt = useSessionStore((s) => s.setPendingPrompt)
  const setPendingModelPrompt = useSessionStore((s) => s.setPendingModelPrompt)
  const activeConnectionId = useConnectionStore((s) => s.activeConnectionId)
  const hasActiveModel = useHasActiveModel()
  const qc = useQueryClient()

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const t = text.trim()
    if (!t || isStreaming) return

    if (!activeSessionId) {
      if (!hasActiveModel) {
        setPendingPrompt(t)
        setPendingModelPrompt(true)
        return
      }
      if (!activeConnectionId) {
        toast.error('请先在侧边栏选择或创建连接')
        return
      }
      setText('')
      setPendingPrompt(t)
      try {
        const sess = await createSession(activeConnectionId, '新会话')
        qc.invalidateQueries({ queryKey: ['sessions', activeConnectionId] })
        openSession(sess.id, sess.hasEverSent)
        // resume hook 会在 activeSessionId 就绪后消费 pendingPrompt
      } catch (err) {
        setPendingPrompt(null)
        setText(t)
        toast.error(err instanceof Error ? err.message : '创建会话失败')
      }
      return
    }

    setText('')
    await sendMessage([createTextPart(activeSessionId, t)])
  }

  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      void onSubmit(e as unknown as FormEvent)
    }
  }

  const canSend = text.trim().length > 0 && !isStreaming

  return (
    <form onSubmit={onSubmit} className="w-full">
      <InputGroup
        className="rounded-2xl !border-foreground/20 shadow-sm transition-shadow focus-within:!border-foreground/40 focus-within:shadow-md dark:!border-white/25 dark:focus-within:!border-white/40"
        style={{ backgroundColor: 'var(--background)' }}
      >
        <InputGroupTextarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKey}
          placeholder="用自然语言查询你的数据库..."
          className="h-[90px] resize-none overflow-y-auto px-4 py-4 text-base leading-relaxed text-black dark:text-white [&::-webkit-scrollbar-track]:my-3"
          rows={3}
        />
        <InputGroupAddon align="block-end" className="pt-2">
          <div className="flex w-full items-center gap-2">
            {/* Model selector */}
            <ModelPicker />

            {/* Auto toggle */}
            <InputGroupText
              className="cursor-pointer gap-1.5 text-xs"
              style={{ color: 'var(--foreground)' }}
              onClick={(e) => {
                e.stopPropagation()
                if ((e.target as HTMLElement).closest('[data-slot=switch]')) return
                setAutoMode((v) => !v)
              }}
            >
              <Switch
                size="sm"
                checked={autoMode}
                onCheckedChange={setAutoMode}
                className="data-[size=sm]:h-[14px] data-[size=sm]:w-[24px]"
              />
              Auto
            </InputGroupText>

            {/* Stage 开关：手动打开/关闭右侧"电脑"窗体 */}
            <StageToggleButton />

            {/* Spacer */}
            <div className="flex-1" />

            {/* Send / Stop button */}
            {isStreaming ? (
              <Button
                type="button"
                variant="destructive"
                size="icon-xs"
                className="rounded-full"
                onClick={() => void abort()}
              >
                <Loader2Icon className="size-3.5 animate-spin" />
              </Button>
            ) : (
              <Button
                type="submit"
                size="icon-xs"
                data-disabled={!canSend || undefined}
                aria-disabled={!canSend}
                className="rounded-full bg-black text-white hover:bg-black/90 data-disabled:opacity-40"
              >
                <ArrowUpIcon className="size-3.5" />
              </Button>
            )}
          </div>
        </InputGroupAddon>
      </InputGroup>
    </form>
  )
}
