import { useState } from 'react'
import { BasicTool } from '../basic-tool'
import { Markdown } from '../../markdown/markdown'
import type { ToolRendererProps } from '../tool-registry'
import { resolveRisk } from '../../helpers/risk'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage } from '@/i18n/messages'
import { useChannel } from '@/services/channel/use-channel'
import { SqlConfirmationCard } from '@/features/sql-confirmation/sql-confirmation-card'
import type { SqlRisk } from '@/features/sql-confirmation/sql-confirmation-card'
import { useI18n } from '@/i18n/use-i18n'

type ExecuteSqlOutput = {
  rows?: unknown[]
  rowCount?: number
  columns?: string[]
  status?: string
  risk?: { level: 'L2' | 'L3'; reason: string; affectedObjects: string[] }
  sqlPreview?: string
  reason?: string
  ackedRisk?: string
  currentRisk?: string
}

export function ExecuteSql(props: ToolRendererProps) {
  const { part, descriptor } = props
  const output = part.state.output as ExecuteSqlOutput | undefined
  const status = output?.status
  const callID = part.callID ?? part.id
  const { client } = useChannel()
  const [decided, setDecided] = useState<'confirmed' | 'cancelled' | null>(null)

  // Confirmation-required branch: L2/L3 SQL needs explicit user approval
  if (status === 'requires_confirmation' && output?.risk) {
    const risk: SqlRisk = output.risk as SqlRisk
    const sqlPreview = String(output.sqlPreview ?? '')
    const lang = getCurrentLanguage()

    return (
      <BasicTool
        icon="code"
        risk={risk.level}
        status={part.state.status}
        trigger={{
          title: risk.level === 'L3'
            ? translateMessage(lang, 'chat.confirmSql')
            : translateMessage(lang, 'chat.executeSql'),
        }}
        forceOpen
      >
        {decided ? (
          <div className="text-xs text-muted-foreground">
            {decided === 'confirmed'
              ? translateMessage(lang, 'chat.confirmedExecute')
              : translateMessage(lang, 'chat.cancelled')}
          </div>
        ) : (
          <SqlConfirmationCard
            risk={risk}
            sqlPreview={sqlPreview}
            onCancel={() => {
              setDecided('cancelled')
              client?.actionResult(callID, true, { confirmed: false })
            }}
            onExecute={() => {
              setDecided('confirmed')
              client?.actionResult(callID, true, { confirmed: true, riskAck: risk.level })
            }}
          />
        )}
      </BasicTool>
    )
  }

  // Confirmation-invalid branch: risk changed between confirmation and execution
  if (status === 'confirmation_invalid') {
    return <ConfirmationInvalid output={output} part={part} descriptor={descriptor} />
  }

  // Legacy success rendering
  return renderExecutedSqlResult(props)
}

function ConfirmationInvalid({
  output,
  part,
  descriptor,
}: {
  output: ExecuteSqlOutput | undefined
  part: ToolRendererProps['part']
  descriptor: ToolRendererProps['descriptor']
}) {
  const { t } = useI18n()
  const risk = resolveRisk(part, descriptor)
  const reason = output?.reason ?? ''
  const ackedRisk = output?.ackedRisk ?? ''
  const currentRisk = output?.currentRisk ?? ''

  return (
    <BasicTool
      icon="code"
      risk={risk}
      status={part.state.status}
      trigger={{ title: t('sqlConfirmation.invalid.title') }}
      forceOpen
    >
      <div className="text-sm text-muted-foreground">
        {reason || t('sqlConfirmation.invalid.message', { currentRisk, ackedRisk })}
      </div>
    </BasicTool>
  )
}

function renderExecutedSqlResult(props: ToolRendererProps) {
  const { part, descriptor } = props
  const sql = (part.state.input?.sql as string | undefined) ?? ''
  const risk = resolveRisk(part, descriptor)
  const output = part.state.output as ExecuteSqlOutput | undefined

  const lang = getCurrentLanguage()
  const subtitle = output?.rowCount !== undefined
    ? translateMessage(lang, 'chat.rowsAffected', { count: output.rowCount })
    : ''

  return (
    <BasicTool
      icon="code"
      risk={risk}
      status={part.state.status}
      trigger={{ title: translateMessage(lang, 'chat.executeSql'), subtitle }}
      defaultOpen={props.defaultOpen}
    >
      {sql && <Markdown text={'```sql\n' + sql + '\n```'} cacheKey={`${part.id}:sql`} />}
      {output?.rows && output.rows.length > 0 && (
        <div className="mt-2 text-xs">
          <div className="text-muted-foreground">
            {translateMessage(lang, 'chat.topRows', { count: Math.min(5, output.rows.length) })}
          </div>
          <pre className="overflow-x-auto">{JSON.stringify(output.rows.slice(0, 5), null, 2)}</pre>
        </div>
      )}
    </BasicTool>
  )
}
