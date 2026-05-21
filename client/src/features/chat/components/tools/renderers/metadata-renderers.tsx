import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage } from '@/i18n/messages'

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
      trigger={{ title: translateMessage(getCurrentLanguage(), 'chat.describeTable'), subtitle: table }}
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
      trigger={{ title: translateMessage(getCurrentLanguage(), 'chat.listTables') }}
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
      trigger={{ title: translateMessage(getCurrentLanguage(), 'chat.showSchema') }}
    >
      {renderOutput(part.state.output)}
    </BasicTool>
  )
}
