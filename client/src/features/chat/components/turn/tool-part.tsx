import { useEffect } from 'react'
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

  // E2E tap: expose tool name + full input to a window-side ring buffer so
  // Playwright tests can assert routing decisions without scraping nested
  // tool-card DOM (GenericTool only flattens primitive args). Cheap, no-op
  // outside a browser context.
  useEffect(() => {
    if (typeof window === 'undefined') return
    const w = window as unknown as {
      __dtToolPartTap?: (entry: {
        tool: string
        input: Record<string, unknown> | undefined
        status: string | undefined
        callID: string | undefined
        partId: string
      }) => void
    }
    if (typeof w.__dtToolPartTap !== 'function') return
    try {
      w.__dtToolPartTap({
        tool: part.tool,
        input: part.state?.input,
        status: part.state?.status,
        callID: part.callID,
        partId: part.id,
      })
    } catch {
      // tap must never break rendering
    }
  }, [part.id, part.tool, part.callID, part.state?.status, part.state?.input])

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
