import { useState, useEffect } from 'react'
import { ChevronRightIcon } from 'lucide-react'
import type { PartComponentProps } from './part-dispatcher'
import { Markdown } from '../markdown/markdown'
import { PacedMarkdown } from '../effects/paced-markdown'
import { TextShimmer } from '../effects/text-shimmer'
import type { ReasoningPart as RPartType } from '@/services/channel/types'
import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'

export function ReasoningPart(props: PartComponentProps) {
  const { t } = useI18n()
  const part = props.part as RPartType
  const isMessageStreaming =
    props.info.role === 'assistant' && typeof props.info.time.completed !== 'number'
  const isPartStreaming = isMessageStreaming && !part.time?.end

  const text = (part.text ?? '').trim()
  if (!text && !isPartStreaming) return null

  const [open, setOpen] = useState(isPartStreaming)
  const [hasAutoCollapsed, setHasAutoCollapsed] = useState(false)

  useEffect(() => {
    if (!isPartStreaming && !hasAutoCollapsed) {
      setOpen(false)
      setHasAutoCollapsed(true)
    } else if (isPartStreaming) {
      setOpen(true)
      setHasAutoCollapsed(false)
    }
  }, [isPartStreaming, hasAutoCollapsed])

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
    <div data-component="reasoning-part" className="my-2 flex flex-col">
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
        <div className="mt-2 mb-2 ml-2 border-l-2 border-border pl-4 py-0.5 text-sm text-muted-foreground">
          {isPartStreaming ? (
            <PacedMarkdown text={text} cacheKey={part.id} streaming />
          ) : (
            <Markdown text={text} cacheKey={part.id} />
          )}
        </div>
      )}
    </div>
  )
}
