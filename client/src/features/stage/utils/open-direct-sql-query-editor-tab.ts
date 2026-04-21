import { executeQuery } from '@/services/api/query'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore, type StageTab } from '@/stores/stage-store'

interface Args {
  sessionId: string | null
  connectionId: string | null
  sql: string
}

function mapRows(columns: string[], rows: Array<Record<string, unknown>>) {
  return rows.map((row) => columns.map((column) => row[column]))
}

function buildTruncated(rowCount: number, rows: Array<Record<string, unknown>>) {
  return rows.length < rowCount
}

export async function openDirectSqlQueryEditorTab({ sessionId, connectionId, sql }: Args): Promise<string> {
  if (!connectionId) throw new Error('No active connection — please select a data source')

  const sessionContext = sessionId ? useSessionStore.getState().dataContextBySession.get(sessionId) ?? null : null
  const result = await executeQuery({
    connectionId,
    sql,
    sessionId,
    database: sessionContext?.database,
    schema: sessionContext?.schema,
  })

  const resolvedContext = result.resolvedContext ?? null
  const connectionName =
    useConnectionStore.getState().connections.find((connection) => connection.id === connectionId)?.name
    ?? sessionContext?.connectionNameSnapshot
    ?? null
  const truncated = buildTruncated(result.rowCount, result.rows)
  const payload = {
    entryMode: 'direct_sql' as const,
    initialSql: sql,
    source: 'user' as const,
    autoRun: false,
    initialResult: {
      columns: result.columns,
      rows: mapRows(result.columns, result.rows),
      rowCount: result.rowCount,
      executionMs: result.durationMs,
      truncated,
    },
    lastRun: {
      columns: result.columns,
      rowCount: result.rowCount,
      executionMs: result.durationMs,
      truncated,
    },
    contextNotice: result.contextNotice ?? null,
    connectionId: resolvedContext?.connectionId ?? connectionId,
    connectionName: resolvedContext?.connectionName ?? connectionName,
    database: resolvedContext?.database ?? sessionContext?.database ?? null,
    schema: resolvedContext?.schema ?? sessionContext?.schema ?? null,
  }

  const tabId = `query_editor_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const tab: StageTab = {
    tabId,
    type: 'query_editor',
    title: 'SQL 编辑器',
    scope: 'session',
    originSessionId: sessionId ?? undefined,
    connectionId: payload.connectionId,
    connectionName: payload.connectionName ?? undefined,
    database: payload.database ?? undefined,
    schema: payload.schema ?? undefined,
    payload,
    createdAt: Date.now(),
  }

  const store = useStageStore.getState()
  store.openTab(tab)
  if (sessionId) store.openStage(sessionId)
  return tabId
}
