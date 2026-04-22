import { useConnectionStore } from '@/features/connection/store'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore, type StageTab } from '@/stores/stage-store'
import { translateMessage } from '@/i18n/messages'
import { resolveUniqueTabTitle } from './unique-tab-title'

interface Args {
  sessionId: string | null
  connectionId: string | null
  sql: string
}

export async function openDirectSqlQueryEditorTab({ sessionId, connectionId, sql }: Args): Promise<string> {
  if (!connectionId) throw new Error('No active connection — please select a data source')

  const sessionContext = sessionId ? useSessionStore.getState().dataContextBySession.get(sessionId) ?? null : null
  const connectionName =
    useConnectionStore.getState().connections.find((connection) => connection.id === connectionId)?.name
    ?? sessionContext?.connectionNameSnapshot
    ?? null
  const store = useStageStore.getState()
  const existingTitles = [
    ...store.workspaceTabs.map((tab) => tab.title),
    ...(sessionId ? (store.tabsBySession.get(sessionId) ?? []).map((tab) => tab.title) : []),
  ]
  const baseTitle = translateMessage(getCurrentLanguage(), 'stage.toolRow.sql')
  const payload = {
    entryMode: 'direct_sql' as const,
    initialSql: sql,
    source: 'user' as const,
    autoRun: true,
    connectionId,
    connectionName,
    database: sessionContext?.database ?? null,
    schema: sessionContext?.schema ?? null,
  }

  const tabId = `query_editor_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const tab: StageTab = {
    tabId,
    type: 'query_editor',
    title: resolveUniqueTabTitle(baseTitle, existingTitles),
    scope: 'session',
    originSessionId: sessionId ?? undefined,
    connectionId: payload.connectionId,
    connectionName: payload.connectionName ?? undefined,
    database: payload.database ?? undefined,
    schema: payload.schema ?? undefined,
    payload,
    createdAt: Date.now(),
  }

  store.openTab(tab)
  if (sessionId) store.openStage(sessionId)
  return tabId
}
