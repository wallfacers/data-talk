import { useEffect, useRef, useState } from 'react'
import { ChevronRightIcon } from 'lucide-react'
import type { PartComponentProps } from './part-dispatcher'
import { Markdown } from '../markdown/markdown'
import { PacedMarkdown } from '../effects/paced-markdown'
import { TextShimmer } from '../effects/text-shimmer'
import type { ReasoningPart as RPartType } from '@/services/channel/types'
import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'
import { useUISettingsStore } from '@/stores/ui-settings-store'

export function ReasoningPart(props: PartComponentProps) {
  const { t } = useI18n()
  const part = props.part as RPartType
  const autoExpandReasoning = useUISettingsStore((s) => s.autoExpandReasoning)
  const isMessageStreaming =
    props.info.role === 'assistant' && typeof props.info.time.completed !== 'number'
  const isPartStreaming = isMessageStreaming && !part.time?.end

  const text = (part.text ?? '').trim()
  if (!text) return null

  const [open, setOpen] = useState(() => isPartStreaming && autoExpandReasoning)
  const wasPartStreamingRef = useRef(isPartStreaming)

  useEffect(() => {
    if (isPartStreaming && !wasPartStreamingRef.current) {
      setOpen(autoExpandReasoning)
    }
    if (!isPartStreaming && wasPartStreamingRef.current) {
      setOpen(false)
    }
    wasPartStreamingRef.current = isPartStreaming
  }, [autoExpandReasoning, isPartStreaming])

  let durationText = ''
  if (!isPartStreaming) {
    const start = part.time?.start
    const end = part.time?.end || props.info.time.completed
    if (start && end) {
      const secs = Math.ceil((end - start) / 1000)
      if (secs > 0) durationText = t('chat.seconds', { seconds: secs })
    }
  }

  return (
    <div data-component="reasoning-part" className="my-2 flex flex-col gap-2">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="flex w-fit items-center gap-1.5 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
      >
        <ChevronRightIcon
          size={16}
          className={cn('transition-transform duration-200', open && 'rotate-90')}
        />
        {isPartStreaming ? (
          <TextShimmer text={t('chat.thinking')} active />
        ) : (
          <span>{t('chat.deepThought', { duration: durationText })}</span>
        )}
      </button>
      {open && (
        <div className="rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 text-sm text-muted-foreground shadow-sm">
          {isPartStreaming ? (
            <PacedMarkdown
              text={text}
              cacheKey={part.id}
              streaming
              messageId={part.messageID}
              partId={part.id}
            />
          ) : (
            <Markdown text={text} cacheKey={part.id} messageId={part.messageID} partId={part.id} />
          )}
        </div>
      )}
    </div>
  )
}
