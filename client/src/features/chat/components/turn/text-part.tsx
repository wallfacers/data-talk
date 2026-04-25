import { useState } from 'react'
import { CopyIcon, CheckIcon } from 'lucide-react'
import type { PartComponentProps } from './part-dispatcher'
import { PacedMarkdown } from '../effects/paced-markdown'
import type { TextPart as TextPartType } from '@/services/channel/types'
import { copyToClipboard } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'

export function TextPart(props: PartComponentProps) {
  const { t } = useI18n()
  const part = props.part as TextPartType
  const streaming = props.info.role === 'assistant' && typeof props.info.time.completed !== 'number'
  const rawText = part.text ?? ''
  const visibleText = rawText.trim()
  const [copied, setCopied] = useState(false)

  if (!visibleText) return null

  const handleCopy = async () => {
    const success = await copyToClipboard(rawText)
    if (success) {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  // Use PacedMarkdown for both streaming and completed states so React keeps
  // the inner Markdown tree mounted when `streaming` flips to false. A
  // component-type swap (PacedMarkdown → Markdown) unmounted the renderer for
  // one frame and caused the code block to briefly collapse, which shifted
  // content below it upward — visible as a scroll jump at stream end.
  return (
    <div data-component="text-part" className="my-1">
      <PacedMarkdown
        text={rawText}
        cacheKey={part.id}
        streaming={streaming}
        messageId={part.messageID}
        partId={part.id}
      />
      {props.showCopy && (
        <div className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
          <button onClick={handleCopy} className="flex items-center hover:text-foreground" aria-label={t('common.copy')}>
            {copied ? <CheckIcon className="size-3.5" /> : <CopyIcon className="size-3.5" />}
          </button>
          {props.info.role === 'assistant' && props.info.modelID && <span>· {props.info.modelID}</span>}
          {props.turnDurationMs !== undefined && props.turnDurationMs >= 0 && (
            <span>· {Math.round(props.turnDurationMs / 1000)}s</span>
          )}
        </div>
      )}
    </div>
  )
}
