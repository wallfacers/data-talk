import type { SessionDataContext } from '@/services/api/session-data-context'

type TabDataContextLike = {
  originSessionId?: string | null
  connectionId?: string | null
  connectionName?: string | null
  database?: string | null
  schema?: string | null
  payload?: {
    connectionId?: string | null
    connectionName?: string | null
    database?: string | null
    schema?: string | null
  } | null
}

export type ResolveTabDataContextOptions = {
  inheritSessionContext?: boolean
  preferSessionContext?: boolean
  fallbackConnectionId?: string | null
  connectionNameLookup?: (connectionId: string) => string | null | undefined
}

export type ResolvedTabDataContext = {
  sessionId: string | null
  connectionId: string | null
  connectionName: string | null
  database: string | null
  schema: string | null
  selectedLevel: 'connection' | 'database' | 'schema' | null
}

function pickField(
  tabValue: string | null | undefined,
  payloadValue: string | null | undefined,
  sessionValue: string | null | undefined,
  inheritSessionContext: boolean,
  preferSessionContext: boolean,
) {
  if (preferSessionContext && inheritSessionContext && sessionValue != null && sessionValue !== '') return sessionValue
  if (tabValue != null && tabValue !== '') return tabValue
  if (payloadValue != null && payloadValue !== '') return payloadValue
  if (inheritSessionContext && sessionValue != null && sessionValue !== '') return sessionValue
  return null
}

export function resolveTabDataContext(
  tab: TabDataContextLike,
  sessionContext: SessionDataContext | null,
  options: ResolveTabDataContextOptions = {},
): ResolvedTabDataContext {
  const inheritSessionContext = options.inheritSessionContext ?? false
  const preferSessionContext = options.preferSessionContext ?? false
  const payload = tab.payload ?? {}

  const connectionId =
    pickField(tab.connectionId, payload.connectionId, sessionContext?.connectionId, inheritSessionContext, preferSessionContext)
    ?? options.fallbackConnectionId
    ?? null
  const database = pickField(tab.database, payload.database, sessionContext?.database, inheritSessionContext, preferSessionContext)
  const schema = pickField(tab.schema, payload.schema, sessionContext?.schema, inheritSessionContext, preferSessionContext)

  const connectionName =
    (connectionId ? options.connectionNameLookup?.(connectionId) : null)
    ?? pickField(
      tab.connectionName,
      payload.connectionName,
      sessionContext?.connectionNameSnapshot,
      inheritSessionContext,
      preferSessionContext,
    )

  const selectedLevel = schema
    ? 'schema'
    : database
      ? 'database'
      : connectionId
        ? 'connection'
        : null

  return {
    sessionId: tab.originSessionId ?? sessionContext?.sessionId ?? null,
    connectionId,
    connectionName,
    database,
    schema,
    selectedLevel,
  }
}
