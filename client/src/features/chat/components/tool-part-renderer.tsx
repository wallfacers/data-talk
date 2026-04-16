import { useActionRegistryStore } from '@/stores/action-registry-store'
import { getRenderers } from '@/features/actions/registry'
import { GenericToolCard } from './generic-tool-card'

export function ToolPartRenderer({ part }: { part: any }) {
  const descriptor = useActionRegistryStore(s => s.descriptors[part.tool])
  const custom = getRenderers(part.tool)
  if (custom?.leftCard) {
    const Custom = custom.leftCard
    return <Custom part={part} descriptor={descriptor ?? fallbackDescriptor(part.tool)} />
  }
  return <GenericToolCard part={part} descriptor={descriptor ?? fallbackDescriptor(part.tool)} />
}

function fallbackDescriptor(id: string): any {
  return { id, executor: 'SERVER', description: id, inputSchema: {}, outputSchema: {},
    produces: [], sideEffects: [], requiresConnection: false, timeoutMs: 30000 }
}
