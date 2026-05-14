import type { SessionDataContext } from '@/services/api/session-data-context'
import type {
  NormalizedQueryEditorContextOverride,
  QueryEditorEffectiveContext,
} from './normalize-query-editor-payload'

export type { QueryEditorContextSource, QueryEditorEffectiveContext } from './normalize-query-editor-payload'

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
    contextOverride?: NormalizedQueryEditorContextOverride
    contextPinMode?: 'session' | null
    useSessionContext?: boolean
  } | null
}

export type ResolveTabDataContextOptions = {
  inheritSessionContext?: boolean
  preferSessionContext?: boolean
  fallbackConnectionId?: string | null
  connectionNameLookup?: (connectionId: string) => string | null | undefined
}

export type ResolvedTabDataContext = QueryEditorEffectiveContext & {
  selectedLevel: 'connection' | 'database' | 'schema' | null
}

const EMPTY_CONTEXT_SELECT_VALUE = '__empty__'

function normalizeContextValue(value: string | null | undefined) {
  if (value == null) return null
  const trimmed = value.trim()
  if (trimmed.length === 0 || trimmed === EMPTY_CONTEXT_SELECT_VALUE) return null
  return trimmed
}

function pickField(
  tabValue: string | null | undefined,
  payloadValue: string | null | undefined,
  sessionValue: string | null | undefined,
  inheritSessionContext: boolean,
  preferSessionContext: boolean,
) {
  const normalizedTabValue = normalizeContextValue(tabValue)
  const normalizedPayloadValue = normalizeContextValue(payloadValue)
  const normalizedSessionValue = normalizeContextValue(sessionValue)
  if (preferSessionContext && inheritSessionContext && normalizedSessionValue != null) return normalizedSessionValue
  if (normalizedTabValue != null) return normalizedTabValue
  if (normalizedPayloadValue != null) return normalizedPayloadValue
  if (inheritSessionContext && normalizedSessionValue != null) return normalizedSessionValue
  return null
}

function hasContextSnapshot(tab: TabDataContextLike, payload: NonNullable<TabDataContextLike['payload']>) {
  return [
    tab.connectionId,
    tab.connectionName,
    tab.database,
    tab.schema,
    payload.connectionId,
    payload.connectionName,
    payload.database,
    payload.schema,
  ].some((value) => normalizeContextValue(value) != null)
}

function resolveUseSessionContext(
  tab: TabDataContextLike,
  payload: NonNullable<TabDataContextLike['payload']>,
  inheritSessionContext: boolean,
  preferSessionContext: boolean,
) {
  if (typeof payload?.useSessionContext === 'boolean') return payload.useSessionContext
  if (preferSessionContext && inheritSessionContext) return true
  if ('contextOverride' in payload) return payload.contextOverride == null
  if (hasContextSnapshot(tab, payload)) return false
  return payload?.contextOverride == null
}

function resolveSelectedLevel(connectionId: string | null, database: string | null, schema: string | null) {
  return schema
    ? 'schema'
    : database
      ? 'database'
      : connectionId
        ? 'connection'
        : null
}

function resolveConnectionName(
  connectionId: string | null,
  fallbackName: string | null,
  options: ResolveTabDataContextOptions,
) {
  return (connectionId ? options.connectionNameLookup?.(connectionId) : null) ?? fallbackName
}

export function resolveTabDataContext(
  tab: TabDataContextLike,
  sessionContext: SessionDataContext | null,
  options: ResolveTabDataContextOptions = {},
): ResolvedTabDataContext {
  const inheritSessionContext = options.inheritSessionContext ?? false
  const preferSessionContext = options.preferSessionContext ?? false
  const payload = tab.payload ?? {}
  const useSessionContext = resolveUseSessionContext(tab, payload, inheritSessionContext, preferSessionContext)

  const sessionConnectionId = normalizeContextValue(sessionContext?.connectionId)
  if (useSessionContext && inheritSessionContext && sessionConnectionId != null) {
    const connectionId = sessionConnectionId
    // BUG-0042: session-follow editors must surface the connection-default database
    // that openQueryEditor wrote into StageTab.database / payload.database when the
    // session itself did not pin one. Session value still wins when present.
    const database = pickField(tab.database, payload.database, sessionContext?.database, true, true)
    const schema = normalizeContextValue(sessionContext?.schema)
    const connectionName = resolveConnectionName(
      connectionId,
      normalizeContextValue(sessionContext?.connectionNameSnapshot),
      options,
    )

    return {
      useSessionContext: true,
      sessionId: tab.originSessionId ?? sessionContext?.sessionId ?? null,
      connectionId,
      connectionName,
      database,
      schema,
      contextSource: 'session',
      selectedLevel: resolveSelectedLevel(connectionId, database, schema),
    }
  }

  if (!useSessionContext && payload.contextOverride) {
    const connectionId = normalizeContextValue(payload.contextOverride.connectionId)
    const database = normalizeContextValue(payload.contextOverride.database)
    const schema = normalizeContextValue(payload.contextOverride.schema)
    const fallbackName = connectionId === normalizeContextValue(payload.connectionId)
      ? payload.connectionName
      : connectionId === normalizeContextValue(tab.connectionId) ? tab.connectionName : null
    const connectionName = resolveConnectionName(connectionId, normalizeContextValue(fallbackName), options)

    return {
      useSessionContext: false,
      sessionId: tab.originSessionId ?? sessionContext?.sessionId ?? null,
      connectionId,
      connectionName,
      database,
      schema,
      contextSource: 'override',
      selectedLevel: resolveSelectedLevel(connectionId, database, schema),
    }
  }

  const connectionId =
    pickField(tab.connectionId, payload.connectionId, sessionContext?.connectionId, inheritSessionContext, preferSessionContext)
    ?? options.fallbackConnectionId
    ?? null
  const database = pickField(tab.database, payload.database, sessionContext?.database, inheritSessionContext, preferSessionContext)
  const schema = pickField(tab.schema, payload.schema, sessionContext?.schema, inheritSessionContext, preferSessionContext)

  const connectionName = resolveConnectionName(
    connectionId,
    pickField(
      tab.connectionName,
      payload.connectionName,
      sessionContext?.connectionNameSnapshot,
      inheritSessionContext,
      preferSessionContext,
    ),
    options,
  )

  return {
    useSessionContext,
    sessionId: tab.originSessionId ?? sessionContext?.sessionId ?? null,
    connectionId,
    connectionName,
    database,
    schema,
    contextSource: 'tab',
    selectedLevel: resolveSelectedLevel(connectionId, database, schema),
  }
}
