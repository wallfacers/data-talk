import type { Part } from '@/services/channel/types'
import { TextPart } from './text-part'
import { ReasoningPart } from './reasoning-part'
import { ToolPartRenderer, type ToolPart } from './tool-part-renderer'
import { StepDivider } from './step-divider'

export function PartRenderer({ part }: { part: Part }) {
  switch (part.type) {
    case 'text':         return <TextPart part={part} />
    case 'reasoning':    return <ReasoningPart part={part} />
    case 'tool':         return <ToolPartRenderer part={part as ToolPart} />
    case 'step-start':
    case 'step-finish':  return <StepDivider />
    default:             return null
  }
}
