import { useState } from 'react'
import { BasicTool } from '../basic-tool'
import { Markdown } from '../../markdown/markdown'
import { Button } from '@/components/ui/button'
import type { ToolRendererProps } from '../tool-registry'
import { resolveRisk } from '../../helpers/risk'
import { useChannel } from '@/services/channel/use-channel'

export function PreviewSql(props: ToolRendererProps) {
  const { part, descriptor } = props
  const sql = (part.state.input?.sql as string | undefined) ?? ''
  const risk = resolveRisk(part, descriptor)
  const impactRows = part.state.metadata?.impactRows as number | undefined
  const callID = part.callID ?? part.id
  const { client } = useChannel()
  const [decided, setDecided] = useState<'confirmed' | 'cancelled' | null>(null)

  const status = part.state.status
  const locked = status === 'pending' || status === 'running'

  const decide = (ok: boolean) => {
    if (decided || !client) return
    setDecided(ok ? 'confirmed' : 'cancelled')
    client.actionResult(callID, true, { confirmed: ok })
  }

  return (
    <BasicTool
      icon="code"
      risk={risk}
      status={status}
      trigger={{
        title: risk === 'L3' ? '强确认 SQL' : '预览 SQL',
        subtitle: impactRows !== undefined ? `影响 ${impactRows} 行` : '',
      }}
      forceOpen
      locked={locked}
    >
      {sql && <Markdown text={'```sql\n' + sql + '\n```'} cacheKey={`${part.id}:sql`} />}
      {impactRows !== undefined && (
        <div className="mt-2 text-lg font-semibold">将影响 {impactRows} 行</div>
      )}
      {!decided && (
        <div className="mt-3 flex gap-2">
          <Button size="sm" variant="default" onClick={() => decide(true)}>
            执行
          </Button>
          <Button size="sm" variant="outline" onClick={() => decide(false)}>
            取消
          </Button>
        </div>
      )}
      {decided && (
        <div className="mt-2 text-xs text-muted-foreground">
          {decided === 'confirmed' ? '已确认执行' : '已取消'}
        </div>
      )}
    </BasicTool>
  )
}
