import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'
import { resolveRisk } from '../../helpers/risk'

type SummarizedInput = {
  detailArgs: string[]
}

const isPrimitiveArg = (value: unknown): value is string | number | boolean =>
  typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean'

function summarizeInput(input: Record<string, any> | undefined): SummarizedInput {
  if (!input) return { detailArgs: [] }

  return {
    detailArgs: Object.entries(input).flatMap(([k, v]) => (isPrimitiveArg(v) ? [`${k}=${v}`] : [])),
  }
}

export function GenericTool(props: ToolRendererProps) {
  const { part, descriptor } = props
  const risk = resolveRisk(part, descriptor)
  const { detailArgs } = summarizeInput(part.state.input)
  const variant = descriptor.category === 'question' ? 'question' : 'risk'
  const outputText =
    part.state.output === undefined
      ? null
      : typeof part.state.output === 'string'
        ? part.state.output
        : JSON.stringify(part.state.output, null, 2)
  const detailContent = detailArgs.length > 0 ? (
    <pre
      data-slot="generic-tool-detail-args"
      className="whitespace-pre-wrap break-all text-xs font-mono text-muted-foreground"
    >
      {detailArgs.join('\n')}
    </pre>
  ) : null
  const outputContent = outputText ? (
    <pre className="overflow-x-auto text-xs">
      {outputText}
    </pre>
  ) : null

  return (
    <BasicTool
      icon="mcp"
      risk={risk}
      variant={variant}
      status={part.state.status}
      trigger={{ title: part.tool }}
      defaultOpen={props.defaultOpen}
    >
      {detailContent || outputContent ? (
        <div className="space-y-2">
          {detailContent}
          {outputContent}
        </div>
      ) : null}
    </BasicTool>
  )
}
