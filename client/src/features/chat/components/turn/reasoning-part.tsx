import type { PartComponentProps } from './part-dispatcher'
import { Markdown } from '../markdown/markdown'
import { PacedMarkdown } from '../effects/paced-markdown'
import type { ReasoningPart as RPartType } from '@/services/channel/types'

export function ReasoningPart(props: PartComponentProps) {
  const part = props.part as RPartType
  const streaming = props.info.role === 'assistant' && typeof props.info.time.completed !== 'number'
  const text = (part.text ?? '').trim()
  if (!text) return null
  return (
    <div data-component="reasoning-part" className="my-1 rounded border-l-2 border-muted pl-3 text-sm text-muted-foreground">
      {streaming ? (
        <PacedMarkdown text={text} cacheKey={part.id} streaming />
      ) : (
        <Markdown text={text} cacheKey={part.id} />
      )}
    </div>
  )
}
