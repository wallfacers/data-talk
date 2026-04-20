import { useState } from 'react'
import { BasicTool } from '../basic-tool'
import { Markdown } from '../../markdown/markdown'
import { Button } from '@/components/ui/button'
import type { ToolRendererProps } from '../tool-registry'
import { resolveRisk } from '../../helpers/risk'
import { useChannel } from '@/services/channel/use-channel'
import { useI18n } from '@/i18n/use-i18n'

export function PreviewSql(props: ToolRendererProps) {
  const { t } = useI18n()
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
        title: risk === 'L3' ? t('chat.confirmSql') : t('chat.previewSql'),
        subtitle: impactRows !== undefined ? t('chat.rowsAffected', { count: impactRows }) : '',
      }}
      forceOpen
      locked={locked}
    >
      {sql && <Markdown text={'```sql\n' + sql + '\n```'} cacheKey={`${part.id}:sql`} />}
      {impactRows !== undefined && (
        <div className="mt-2 text-lg font-semibold">{t('chat.willAffectRows', { count: impactRows })}</div>
      )}
      {!decided && (
        <div className="mt-3 flex gap-2">
          <Button size="sm" variant="default" onClick={() => decide(true)}>
            {t('chat.executeSql')}
          </Button>
          <Button size="sm" variant="outline" onClick={() => decide(false)}>
            {t('common.cancel')}
          </Button>
        </div>
      )}
      {decided && (
        <div className="mt-2 text-xs text-muted-foreground">
          {decided === 'confirmed' ? t('chat.confirmedExecute') : t('chat.cancelled')}
        </div>
      )}
    </BasicTool>
  )
}
