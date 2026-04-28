import { BasicTool } from '../basic-tool'
import { Markdown } from '../../markdown/markdown'
import type { ToolRendererProps } from '../tool-registry'
import { resolveRisk } from '../../helpers/risk'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { translateMessage } from '@/i18n/messages'
import { Button } from '@/components/ui/button'
import { useStageStore } from '@/stores/stage-store'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { useI18n } from '@/i18n/use-i18n'

type BlockedInChatRisk = { level: 'L2' | 'L3'; reason: string; affectedObjects: string[] }

type ExecuteSqlOutput = {
  rows?: unknown[]
  rowCount?: number
  columns?: string[]
  status?: string
  risk?: BlockedInChatRisk
  sqlPreview?: string
}

export function ExecuteSql(props: ToolRendererProps) {
  const { part } = props
  const output = part.state.output as ExecuteSqlOutput | undefined
  const status = output?.status

  if (status === 'blocked_in_chat' && output?.risk) {
    return <BlockedInChatCard part={part} risk={output.risk} sqlPreview={String(output.sqlPreview ?? '')} />
  }

  return renderExecutedSqlResult(props)
}

function BlockedInChatCard({
  part,
  risk,
  sqlPreview,
}: {
  part: ToolRendererProps['part']
  risk: BlockedInChatRisk
  sqlPreview: string
}) {
  const { t } = useI18n()
  const lang = getCurrentLanguage()
  const sessionId = part.sessionID
  const inputConnectionId = (part.state.input?.connectionId as string | undefined) ?? null

  const handleOpenInWorkbench = () => {
    const sessionContext = useSessionStore.getState().dataContextBySession.get(sessionId) ?? null
    const connectionId = inputConnectionId ?? sessionContext?.connectionId ?? null
    const connectionName = connectionId
      ? useConnectionStore.getState().connections.find((c) => c.id === connectionId)?.name
        ?? sessionContext?.connectionNameSnapshot
        ?? null
      : null
    const stage = useStageStore.getState()
    stage.openQueryEditor({
      sessionId,
      scope: 'session',
      baseTitle: translateMessage(lang, 'stage.toolRow.sql'),
      openMode: 'always_new',
      entryMode: 'ai_open',
      initialContent: sqlPreview,
      autoRun: false,
      connectionId,
      connectionName,
      database: sessionContext?.database ?? null,
      schema: sessionContext?.schema ?? null,
    })
    stage.openStage()
  }

  return (
    <BasicTool
      icon="code"
      risk={risk.level}
      status={part.state.status}
      trigger={{ title: t('chat.blockedInChat.title') }}
      forceOpen
    >
      <div className="space-y-3">
        <div className="text-sm text-muted-foreground">
          {t('chat.blockedInChat.message')}
        </div>
        {risk.affectedObjects.length > 0 && (
          <div className="text-xs font-mono text-foreground">
            {risk.affectedObjects.join(', ')}
          </div>
        )}
        {sqlPreview && (
          <Markdown text={'```sql\n' + sqlPreview + '\n```'} cacheKey={`${part.id}:sql`} />
        )}
        <div className="flex justify-end">
          <Button size="sm" onClick={handleOpenInWorkbench}>
            {t('chat.openInWorkbench')}
          </Button>
        </div>
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
