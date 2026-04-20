import { BasicTool } from '../basic-tool'
import { Markdown } from '../../markdown/markdown'
import type { ToolRendererProps } from '../tool-registry'
import { resolveRisk } from '../../helpers/risk'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage } from '@/i18n/messages'

type ExecuteSqlOutput = {
  rows?: unknown[]
  rowCount?: number
  columns?: string[]
}

export function ExecuteSql(props: ToolRendererProps) {
  const { part, descriptor } = props
  const sql = (part.state.input?.sql as string | undefined) ?? ''
  const risk = resolveRisk(part, descriptor)
  const output = part.state.output as ExecuteSqlOutput | undefined

  const subtitle = output?.rowCount !== undefined
    ? translateMessage(getCurrentLanguage(), 'chat.rowsAffected', { count: output.rowCount })
    : ''

  return (
    <BasicTool
      icon="code"
      risk={risk}
      status={part.state.status}
      trigger={{ title: translateMessage(getCurrentLanguage(), 'chat.executeSql'), subtitle }}
      defaultOpen={props.defaultOpen}
    >
      {sql && <Markdown text={'```sql\n' + sql + '\n```'} cacheKey={`${part.id}:sql`} />}
      {output?.rows && output.rows.length > 0 && (
        <div className="mt-2 text-xs">
          <div className="text-muted-foreground">{translateMessage(getCurrentLanguage(), 'chat.topRows', { count: Math.min(5, output.rows.length) })}</div>
          <pre className="overflow-x-auto">{JSON.stringify(output.rows.slice(0, 5), null, 2)}</pre>
        </div>
      )}
    </BasicTool>
  )
}
