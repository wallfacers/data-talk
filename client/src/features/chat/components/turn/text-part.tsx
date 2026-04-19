import { useState } from 'react'
import { CopyIcon, CheckIcon } from 'lucide-react'
import type { PartComponentProps } from './part-dispatcher'
import { Markdown } from '../markdown/markdown'
import { PacedMarkdown } from '../effects/paced-markdown'
import type { TextPart as TextPartType } from '@/services/channel/types'

export function TextPart(props: PartComponentProps) {
  const part = props.part as TextPartType
  const streaming = props.info.role === 'assistant' && typeof props.info.time.completed !== 'number'
  const text = (part.text ?? '').trim()
  const [copied, setCopied] = useState(false)

  if (!text) return null

  const handleCopy = async () => {
    await navigator.clipboard?.writeText?.(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div data-component="text-part" className="my-1">
      {streaming ? (
        <PacedMarkdown text={text} cacheKey={part.id} streaming />
      ) : (
        <Markdown text={text} cacheKey={part.id} />
      )}
      {props.showCopy && (
        <div className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
          <button onClick={handleCopy} className="flex items-center hover:text-foreground" aria-label="Copy">
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
