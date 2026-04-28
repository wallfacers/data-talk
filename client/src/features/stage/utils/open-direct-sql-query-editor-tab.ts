import { useConnectionStore } from '@/features/connection/store'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore } from '@/stores/stage-store'
import { translateMessage } from '@/i18n/messages'

interface Args {
  sessionId: string | null
  connectionId: string | null
  sql: string
  autoRun?: boolean
}

export async function openDirectSqlQueryEditorTab({ sessionId, connectionId, sql, autoRun = true }: Args): Promise<string> {
  const language = getCurrentLanguage()
  if (!connectionId) throw new Error(translateMessage(language, 'error.connection.missing'))

  const sessionContext = sessionId ? useSessionStore.getState().dataContextBySession.get(sessionId) ?? null : null
  const connectionName =
    useConnectionStore.getState().connections.find((connection) => connection.id === connectionId)?.name
    ?? sessionContext?.connectionNameSnapshot
    ?? null
  const store = useStageStore.getState()
  const baseTitle = translateMessage(language, 'stage.toolRow.sql')
  const { tabId } = store.openQueryEditor({
    sessionId,
    scope: 'session',
    baseTitle,
    openMode: 'always_new',
    entryMode: 'direct_sql',
    initialContent: sql,
    autoRun,
    connectionId,
    connectionName,
    database: sessionContext?.database ?? null,
    schema: sessionContext?.schema ?? null,
  })
  if (sessionId) store.openStage()
  return tabId
}
