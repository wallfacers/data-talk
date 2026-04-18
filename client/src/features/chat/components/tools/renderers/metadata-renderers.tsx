import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'

function renderOutput(output: unknown) {
  if (output === undefined || output === null) return null
  const text = typeof output === 'string' ? output : JSON.stringify(output, null, 2)
  return <pre className="text-xs overflow-x-auto">{text}</pre>
}

export function DescribeTable(props: ToolRendererProps) {
  const { part } = props
  const table =
    (part.state.input?.table as string | undefined) ??
    (part.state.input?.name as string | undefined) ??
    ''
  return (
    <BasicTool
      icon="mcp"
      risk="L1"
      status={part.state.status}
      trigger={{ title: '查看表结构', subtitle: table }}
    >
      {renderOutput(part.state.output)}
    </BasicTool>
  )
}

export function ListTables(props: ToolRendererProps) {
  const { part } = props
  return (
    <BasicTool
      icon="mcp"
      risk="L1"
      status={part.state.status}
      trigger={{ title: '列出表' }}
    >
      {renderOutput(part.state.output)}
    </BasicTool>
  )
}

export function ShowSchema(props: ToolRendererProps) {
  const { part } = props
  return (
    <BasicTool
      icon="mcp"
      risk="L1"
      status={part.state.status}
      trigger={{ title: '查看 schema' }}
    >
      {renderOutput(part.state.output)}
    </BasicTool>
  )
}
