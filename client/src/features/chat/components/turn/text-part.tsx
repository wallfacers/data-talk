import { useState, useMemo } from 'react'
import { CopyIcon, CheckIcon } from 'lucide-react'
import type { PartComponentProps } from './part-dispatcher'
import { PacedMarkdown } from '../effects/paced-markdown'
import { ArtifactRefBlock } from '../markdown/artifact-ref-block'
import type { TextPart as TextPartType } from '@/services/channel/types'
import { copyToClipboard } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'

// Matches a standalone "chart:art-XXXX" line (multiline mode).
const ARTIFACT_REF_LINE_RE = /^chart:[A-Za-z0-9_-]+$/gm

export function TextPart(props: PartComponentProps) {
  const { t } = useI18n()
  const part = props.part as TextPartType
  const streaming = props.info.role === 'assistant' && typeof props.info.time.completed !== 'number'
  const rawText = part.text ?? ''
  const [copied, setCopied] = useState(false)

  const { cleanText, artifactIds } = useMemo(() => {
    const ids: string[] = []
    const clean = rawText.replace(ARTIFACT_REF_LINE_RE, (match) => {
      ids.push(match.replace(/^chart:/, ''))
      return ''
    })
    return { cleanText: clean, artifactIds: ids }
  }, [rawText])

  const visibleText = cleanText.trim()

  if (!visibleText && artifactIds.length === 0) return null

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
      {visibleText && (
        <PacedMarkdown
          text={cleanText}
          cacheKey={part.id}
          streaming={streaming}
          messageId={part.messageID}
          partId={part.id}
        />
      )}
      {artifactIds.map((artifactId) => (
        <ArtifactRefBlock key={artifactId} artifactId={artifactId} />
      ))}
      {props.showCopy && (
        <div className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
          <Tooltip>
            <TooltipTrigger
              render={
                <button onClick={handleCopy} className="flex items-center hover:text-foreground" aria-label={t('common.copy')}>
                  {copied ? <CheckIcon className="size-3.5" /> : <CopyIcon className="size-3.5" />}
                </button>
              }
            />
            <TooltipContent>{t('common.copy')}</TooltipContent>
          </Tooltip>
          {props.info.role === 'assistant' && props.info.modelID && <span>· {props.info.modelID}</span>}
          {props.turnDurationMs !== undefined && props.turnDurationMs >= 0 && (
            <span>· {Math.round(props.turnDurationMs / 1000)}s</span>
          )}
        </div>
      )}
    </div>
  )
}
