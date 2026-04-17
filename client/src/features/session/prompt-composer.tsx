"use client"

import { useLayoutEffect, useState, type FormEvent, type KeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { ArrowUpIcon, Loader2Icon } from 'lucide-react'
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
import { useStageStore } from '@/stores/stage-store'
import { useChannel } from '@/services/channel/use-channel'
import { createTextPart } from '@/services/channel/types'
import { StageToggleButton } from '@/features/stage/components/stage-toggle-button'

function useComposerSlot(): HTMLElement | null {
  const [slot, setSlot] = useState<HTMLElement | null>(null)

  // No dependency array: re-check after every render so we pick up the new
  // composer-slot when HeroView ↔ SplitView swaps the DOM node.
  useLayoutEffect(() => {
    const el = document.getElementById('composer-slot')
    if (el !== slot) setSlot(el)
  })

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
  const addDemoMessage = useStageStore((s) => s.addDemoMessage)

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const t = text.trim()
    if (!t || isStreaming) return

    if (!activeSessionId) {
      setText('')
      addDemoMessage('user', t)
      setTimeout(() => addDemoMessage('assistant', '好的，我来处理你的请求。（演示模式）'), 600)
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
