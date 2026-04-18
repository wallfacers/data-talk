import type { ComponentType } from 'react'
import type { ActionDescriptor } from '@/features/actions/registry'
import type { ToolPart } from '@/services/channel/types'

export type ToolRendererProps = {
  part: ToolPart
  descriptor: ActionDescriptor
  defaultOpen?: boolean
}

export type ToolRenderer = ComponentType<ToolRendererProps>

const registry = new Map<string, ToolRenderer>()

export const ToolRegistry = {
  register(name: string, renderer: ToolRenderer) {
    registry.set(name, renderer)
  },
  get(name: string): ToolRenderer | undefined {
    return registry.get(name)
  },
}
