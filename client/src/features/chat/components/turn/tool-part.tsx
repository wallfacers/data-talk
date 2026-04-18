import type { PartComponentProps } from './part-dispatcher'
import type { ToolPart as ToolPartType } from '@/services/channel/types'
import { ToolRegistry } from '../tools/tool-registry'
import { GenericTool } from '../tools/renderers/generic-tool'
import { ToolErrorBoundary } from '../tools/tool-error-boundary'
import { useActionRegistryStore } from '@/stores/action-registry-store'
import { getRenderers } from '@/features/actions/registry'

export function ToolPart(props: PartComponentProps) {
  const part = props.part as ToolPartType
  const descriptor = useActionRegistryStore((s) => s.descriptors[part.tool]) ?? {
    id: part.tool,
    executor: 'SERVER',
    description: part.tool,
    inputSchema: {},
    outputSchema: {},
    produces: [],
    sideEffects: [],
    requiresConnection: false,
    timeoutMs: 30000,
  }

  // Priority: actions/registry customRenderer > ToolRegistry > GenericTool
  const custom = getRenderers(part.tool)
  const Renderer = ToolRegistry.get(part.tool) ?? GenericTool

  if (custom?.leftCard) {
    const Custom = custom.leftCard
    return (
      <ToolErrorBoundary fallback={<GenericTool part={part} descriptor={descriptor} />}>
        <Custom part={part as any} descriptor={descriptor} />
      </ToolErrorBoundary>
    )
  }

  return (
    <ToolErrorBoundary fallback={<GenericTool part={part} descriptor={descriptor} />}>
      <Renderer part={part} descriptor={descriptor} />
    </ToolErrorBoundary>
  )
}
