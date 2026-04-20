import { executeQuery } from '@/services/api/query'
import { useConnectionStore } from '@/features/connection/store'
import { useStageStore, type StageTab } from '@/stores/stage-store'

interface Args {
  sessionId: string | null
  connectionId: string | null
  sql: string
}

export async function openBangQueryTab({ sessionId, connectionId, sql }: Args): Promise<string> {
  if (!connectionId) throw new Error('No active connection — please select a data source')
  const result = await executeQuery({ connectionId, sql })
  const connectionName = useConnectionStore.getState().connections.find((connection) => connection.id === connectionId)?.name
  const tabId = `bang_query_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const tab: StageTab = {
    tabId,
    type: 'bang_query',
    title: sql.length > 40 ? sql.slice(0, 40) + '…' : sql,
    scope: 'workspace',
    connectionId,
    connectionName,
    originSessionId: sessionId ?? undefined,
    payload: {
      sql,
      rows: result.rows,
      lastRun: {
        columns: result.columns,
        rowCount: result.rowCount,
        durationMs: result.durationMs,
        truncated: result.rows.length < result.rowCount,
      },
    },
    createdAt: Date.now(),
  }
  const store = useStageStore.getState()
  store.openTab(tab)
  if (sessionId) store.openStage(sessionId)
  return tabId
}
