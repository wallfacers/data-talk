import { useActionRegistryStore } from '@/stores/action-registry-store'
import { getRenderers, type ActionDescriptor, type LeftCardProps } from '@/features/actions/registry'
import { GenericToolCard } from './generic-tool-card'
import type { Part } from '@/services/channel/types'

export type ToolPart = Part & {
  type: 'tool'
  tool: string
  state?: { status?: 'pending' | 'running' | 'completed' | 'error' }
}

function fallbackDescriptor(id: string): ActionDescriptor {
  return { id, executor: 'SERVER', description: id, inputSchema: {}, outputSchema: {},
    produces: [], sideEffects: [], requiresConnection: false, timeoutMs: 30000 }
}

export function ToolPartRenderer({ part }: { part: ToolPart }) {
  const descriptor = useActionRegistryStore(s => s.descriptors[part.tool])
  const custom = getRenderers(part.tool)
  const desc = descriptor ?? fallbackDescriptor(part.tool)
  if (custom?.leftCard) {
    const Custom = custom.leftCard
    return <Custom part={part as LeftCardProps['part']} descriptor={desc} />
  }
  return <GenericToolCard part={part} descriptor={desc} />
}
