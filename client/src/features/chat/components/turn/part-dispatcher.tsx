import type { ComponentType } from 'react'
import type { Part, MessageInfo } from '@/services/channel/types'
import { TextPart } from './text-part'
import { ReasoningPart } from './reasoning-part'
import { ToolPart } from './tool-part'
import { UnknownPart } from './unknown-part'

export type PartComponentProps = {
  part: Part
  info: MessageInfo
  showCopy?: boolean
  turnDurationMs?: number
}

export type PartComponent = ComponentType<PartComponentProps>

const PART_MAPPING: Record<string, PartComponent> = {
  text: TextPart,
  reasoning: ReasoningPart,
  tool: ToolPart,
  'step-start': () => null,
  'step-finish': () => null,
  compaction: () => null,
}

export function PartDispatcher(props: PartComponentProps) {
  const Comp = PART_MAPPING[props.part.type]
  if (!Comp) return <UnknownPart part={props.part as any} />
  return <Comp {...props} />
}
