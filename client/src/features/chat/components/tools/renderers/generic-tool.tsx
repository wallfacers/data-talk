import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'
import { resolveRisk } from '../../helpers/risk'

function summarizeInput(input: Record<string, any> | undefined): { subtitle?: string; args: string[] } {
  if (!input) return { args: [] }
  const keys = ['description', 'query', 'url', 'filePath', 'path', 'pattern', 'name', 'sql']
  const subtitle = keys
    .map((k) => input[k])
    .find((v): v is string => typeof v === 'string' && v.length > 0)
  const skip = new Set(keys)
  const args = Object.entries(input)
    .filter(([k]) => !skip.has(k))
    .flatMap(([k, v]) => {
      if (typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean') return [`${k}=${v}`]
      return []
    })
    .slice(0, 3)
  return { subtitle, args }
}

export function GenericTool(props: ToolRendererProps) {
  const { part, descriptor } = props
  const risk = resolveRisk(part, descriptor)
  const { subtitle, args } = summarizeInput(part.state.input)
  const variant = descriptor.category === 'question' ? 'question' : 'risk'
  return (
    <BasicTool
      icon="mcp"
      risk={risk}
      variant={variant}
      status={part.state.status}
      trigger={{ title: part.tool, subtitle, args }}
      defaultOpen={props.defaultOpen}
    >
      {part.state.output ? (
        <pre className="text-xs overflow-x-auto">
          {typeof part.state.output === 'string'
            ? part.state.output
            : JSON.stringify(part.state.output, null, 2)}
        </pre>
      ) : null}
    </BasicTool>
  )
}
