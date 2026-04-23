import { useConnectionStore } from '@/features/connection/store'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { executeSql, SqlRiskError } from '@/services/api/sql'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore } from '@/stores/stage-store'
import { translateMessage } from '@/i18n/messages'
import { formatSql } from './format-sql'
import { normalizeQueryEditorPayload } from './normalize-query-editor-payload'
import { resolveTabDataContext } from './resolve-tab-data-context'
import { useSqlWorkbenchStore } from '../stores/sql-workbench-store'

type QueryEditorExecutionContext = {
  sessionId: string | null
  connectionId: string | null
  connectionName: string | null
  database: string | null
  schema: string | null
}

type RuntimeQueryEditorOverride = {
  connectionId: string
  connectionName: string | null
  database: string | null
  schema: string | null
  source: 'api'
  setAt: number
}

const QUERY_EDITOR_RUN_CONTROLLERS_KEY = '__data_talk_query_editor_run_controllers__'

function getQueryEditorRunControllers() {
  const globalState = globalThis as typeof globalThis & {
    [QUERY_EDITOR_RUN_CONTROLLERS_KEY]?: Map<string, AbortController>
  }
  if (!globalState[QUERY_EDITOR_RUN_CONTROLLERS_KEY]) {
    globalState[QUERY_EDITOR_RUN_CONTROLLERS_KEY] = new Map<string, AbortController>()
  }
  return globalState[QUERY_EDITOR_RUN_CONTROLLERS_KEY]
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function isAbortError(error: unknown) {
  return (error instanceof DOMException && error.name === 'AbortError') || (error instanceof Error && error.name === 'AbortError')
}

function injectLimit(sql: string, limit: 10 | 100 | 1000 | null) {
  if (limit == null) return sql
  const trimmed = sql.trim()
  if (!trimmed) return sql
  if (!/^\s*(with\b|select\b)/i.test(trimmed)) return sql
  if (/\blimit\b/i.test(trimmed)) return sql

  const hasTrailingSemicolon = trimmed.endsWith(';')
  const body = hasTrailingSemicolon ? trimmed.slice(0, -1).trimEnd() : trimmed
  return `${body} LIMIT ${limit}${hasTrailingSemicolon ? ';' : ''}`
}

function getStageTab(tabId: string) {
  const stageState = useStageStore.getState()
  return stageState.workspaceTabs.find((tab) => tab.tabId === tabId)
    ?? Array.from(stageState.tabsBySession.values()).flat().find((tab) => tab.tabId === tabId)
    ?? null
}

function updateQueryEditorPayloadContextOverride(
  tabId: string,
  contextOverride: { connectionId: string; database: string | null; schema: string | null } | null,
) {
  useStageStore.getState().updateTabPayload(tabId, (payload) => {
    const basePayload = isPlainObject(payload) ? payload : {}
    return {
      ...basePayload,
      contextOverride,
    }
  })
}

function resetTabExecutionState(tabId: string) {
  useSqlWorkbenchStore.setState((state) => {
    const current = state.tabsById[tabId]
    if (!current) return state
    return {
      tabsById: {
        ...state.tabsById,
        [tabId]: {
          ...current,
          executeStatus: 'idle',
          risk: null,
          errorMessage: null,
        },
      },
    }
  })
}

function buildRuntimeOverrideFromPayload(params: {
  connectionId: string
  database: string | null
  schema: string | null
  fallbackConnectionName: string | null
}) {
  const connectionName = useConnectionStore.getState().connections.find(
    (connection) => connection.id === params.connectionId,
  )?.name ?? params.fallbackConnectionName

  const override: RuntimeQueryEditorOverride = {
    connectionId: params.connectionId,
    connectionName,
    database: params.database,
    schema: params.schema,
    source: 'api',
    setAt: 0,
  }
  return override
}

function resolveQueryEditorContexts(tabId: string, sessionIdOverride: string | null) {
  const tab = getStageTab(tabId)
  const payload = normalizeQueryEditorPayload(tab?.payload)
  const sqlWorkbenchState = useSqlWorkbenchStore.getState()
  const tabState = sqlWorkbenchState.tabsById[tabId] ?? null
  const connectionState = useConnectionStore.getState()
  const sessionId = sessionIdOverride ?? tab?.originSessionId ?? null
  const sessionContext = sessionId
    ? useSessionStore.getState().dataContextBySession.get(sessionId) ?? null
    : null

  const resolvedContext = resolveTabDataContext(
    {
      originSessionId: sessionId,
      connectionId: payload.connectionId ?? tab?.connectionId ?? null,
      connectionName: payload.connectionName ?? tab?.connectionName ?? null,
      database: payload.database ?? tab?.database ?? null,
      schema: payload.schema ?? tab?.schema ?? null,
    },
    sessionContext,
    {
      inheritSessionContext: true,
      fallbackConnectionId: connectionState.activeConnectionId ?? null,
      connectionNameLookup: (connectionId) =>
        connectionState.connections.find((connection) => connection.id === connectionId)?.name ?? null,
    },
  )

  const defaultContext: QueryEditorExecutionContext = tabState?.resolvedContext
    ? {
        sessionId: resolvedContext.sessionId ?? sessionId,
        connectionId: tabState.resolvedContext.connectionId,
        connectionName: tabState.resolvedContext.connectionName,
        database: tabState.resolvedContext.database,
        schema: tabState.resolvedContext.schema,
      }
    : {
        sessionId: resolvedContext.sessionId ?? sessionId,
        connectionId: resolvedContext.connectionId,
        connectionName: resolvedContext.connectionName,
        database: resolvedContext.database,
        schema: resolvedContext.schema,
      }

  const currentOverride = tabState?.override ?? (payload.contextOverride
    ? buildRuntimeOverrideFromPayload({
        connectionId: payload.contextOverride.connectionId,
        database: payload.contextOverride.database,
        schema: payload.contextOverride.schema,
        fallbackConnectionName: payload.connectionName ?? tab?.connectionName ?? defaultContext.connectionName,
      })
    : null)

  const effectiveContext: QueryEditorExecutionContext = currentOverride
    ? {
        sessionId: defaultContext.sessionId,
        connectionId: currentOverride.connectionId,
        connectionName: currentOverride.connectionName ?? defaultContext.connectionName,
        database: currentOverride.database ?? defaultContext.database,
        schema: currentOverride.schema ?? defaultContext.schema,
      }
    : defaultContext

  const connectionKind = connectionState.connections.find(
    (connection) => connection.id === effectiveContext.connectionId,
  )?.kind ?? null

  return {
    tab,
    payload,
    tabState,
    defaultContext,
    currentOverride,
    effectiveContext,
    connectionKind,
    persistedBaseContext: {
      connectionId: payload.connectionId ?? tab?.connectionId ?? null,
      connectionName: payload.connectionName ?? tab?.connectionName ?? defaultContext.connectionName ?? null,
      database: payload.database ?? tab?.database ?? null,
      schema: payload.schema ?? tab?.schema ?? null,
    },
  }
}

export async function runQueryEditorSql(params: {
  tabId: string
  sessionId: string | null
  limit?: 10 | 100 | 1000 | null
}): Promise<{ executeStatus: 'success' | 'risk_blocked' | 'error'; activeResultId: string | null }> {
  const { tabId, sessionId } = params
  const stageTab = getStageTab(tabId)
  if (stageTab) {
    const payload = normalizeQueryEditorPayload(stageTab.payload)
    useSqlWorkbenchStore.getState().ensureTab(tabId, {
      sqlText: payload.initialSql,
      source: payload.source,
    })
  }

  const { tabState, effectiveContext } = resolveQueryEditorContexts(tabId, sessionId)
  if (!tabState || !effectiveContext.connectionId || !tabState.sqlText.trim()) {
    return { executeStatus: 'error', activeResultId: null }
  }

  const controller = new AbortController()
  const controllers = getQueryEditorRunControllers()
  controllers.set(tabId, controller)

  const sqlWorkbenchStore = useSqlWorkbenchStore.getState()
  sqlWorkbenchStore.setRunning(tabId)
  const startedAt = Date.now()
  const executableSql = injectLimit(tabState.sqlText, params.limit === undefined ? tabState.limit : params.limit)

  try {
    const request: Parameters<typeof executeSql>[0] = {
      sql: executableSql,
      connectionId: effectiveContext.connectionId,
      source: tabState.source,
    }
    if (effectiveContext.sessionId != null) request.sessionId = effectiveContext.sessionId
    if (effectiveContext.database != null) request.database = effectiveContext.database
    if (effectiveContext.schema != null) request.schema = effectiveContext.schema

    const response = await executeSql(request, controller.signal)
    sqlWorkbenchStore.applyExecuteSuccess(tabId, response)
    sqlWorkbenchStore.appendHistoryEntry(tabId, {
      id: `history-${startedAt}-${Math.random().toString(36).slice(2, 8)}`,
      at: Date.now(),
      sql: executableSql,
      status: 'ok',
      resultCount: response.results.length,
      elapsedMs: Date.now() - startedAt,
      resultKinds: response.results.map((item) => item.kind),
    })

    return {
      executeStatus: 'success',
      activeResultId: useSqlWorkbenchStore.getState().tabsById[tabId]?.activeResultId ?? response.results[0]?.resultId ?? null,
    }
  } catch (error) {
    if (isAbortError(error)) {
      resetTabExecutionState(tabId)
      throw error
    }
    if (error instanceof SqlRiskError) {
      sqlWorkbenchStore.setRiskBlocked(tabId, error.risk)
      sqlWorkbenchStore.appendHistoryEntry(tabId, {
        id: `history-${startedAt}-${Math.random().toString(36).slice(2, 8)}`,
        at: Date.now(),
        sql: executableSql,
        status: 'risk_blocked',
        elapsedMs: Date.now() - startedAt,
        errorSummary: error.risk.riskReason,
      })
      return {
        executeStatus: 'risk_blocked',
        activeResultId: useSqlWorkbenchStore.getState().tabsById[tabId]?.activeResultId ?? null,
      }
    }

    const language = getCurrentLanguage()
    const errorMessage = error instanceof Error ? error.message : translateMessage(language, 'stage.queryEditor.runFailed')
    sqlWorkbenchStore.setError(tabId, errorMessage, {
      resultId: `error-${startedAt}-${Math.random().toString(36).slice(2, 8)}`,
      kind: 'error',
      title: translateMessage(language, 'stage.status.error'),
      statementIndex: 0,
      statementText: executableSql,
      columns: [],
      rows: [],
      rowCount: 0,
      executionMs: Date.now() - startedAt,
      truncated: false,
      errorMessage,
    })
    sqlWorkbenchStore.appendHistoryEntry(tabId, {
      id: `history-${startedAt}-${Math.random().toString(36).slice(2, 8)}`,
      at: Date.now(),
      sql: executableSql,
      status: 'error',
      elapsedMs: Date.now() - startedAt,
      errorSummary: errorMessage,
    })
    return {
      executeStatus: 'error',
      activeResultId: useSqlWorkbenchStore.getState().tabsById[tabId]?.activeResultId ?? null,
    }
  } finally {
    if (controllers.get(tabId) === controller) {
      controllers.delete(tabId)
    }
  }
}

export function formatQueryEditorSql(tabId: string): { version: number; content: string } {
  const stageTab = getStageTab(tabId)
  if (stageTab) {
    const payload = normalizeQueryEditorPayload(stageTab.payload)
    useSqlWorkbenchStore.getState().ensureTab(tabId, {
      sqlText: payload.initialSql,
      source: payload.source,
    })
  }

  const { tabState, connectionKind } = resolveQueryEditorContexts(tabId, null)
  if (!tabState) {
    return { version: 1, content: '' }
  }

  const rawSql = tabState.sqlText
  if (!rawSql.trim()) {
    return { version: tabState.version, content: rawSql }
  }

  const formatted = formatSql(rawSql, connectionKind)
  if (formatted === rawSql) {
    return { version: tabState.version, content: rawSql }
  }

  const next = useStageStore.getState().replaceQueryEditorContent(tabId, formatted)
  return {
    version: next.version,
    content: formatted,
  }
}

export function setQueryEditorContext(params: {
  tabId: string
  connectionId?: string | null
  database?: string | null
  schema?: string | null
}): void {
  const { tabId } = params
  const stageTab = getStageTab(tabId)
  if (!stageTab) return

  const { currentOverride, defaultContext, effectiveContext, persistedBaseContext } = resolveQueryEditorContexts(tabId, null)
  const stageStore = useStageStore.getState()
  const sqlWorkbenchStore = useSqlWorkbenchStore.getState()

  stageStore.setQueryEditorContext(tabId, persistedBaseContext)

  const nextConnectionId = params.connectionId === undefined ? effectiveContext.connectionId : params.connectionId
  const nextDatabase = params.database === undefined ? effectiveContext.database : params.database
  const nextSchema = params.schema === undefined ? effectiveContext.schema : params.schema

  if (nextConnectionId == null) {
    stageStore.setQueryEditorContext(tabId, {
      connectionId: null,
      connectionName: null,
      database: nextDatabase,
      schema: nextSchema,
    })
    sqlWorkbenchStore.resetTabContext(tabId)
    updateQueryEditorPayloadContextOverride(tabId, null)
    return
  }

  const shouldResetOverride =
    nextConnectionId === defaultContext.connectionId
    && nextDatabase === defaultContext.database
    && nextSchema === defaultContext.schema

  const matchesPersistedBaseContext =
    nextConnectionId === persistedBaseContext.connectionId
    && nextDatabase === persistedBaseContext.database
    && nextSchema === persistedBaseContext.schema

  if (shouldResetOverride && (currentOverride || matchesPersistedBaseContext)) {
    sqlWorkbenchStore.resetTabContext(tabId)
    updateQueryEditorPayloadContextOverride(tabId, null)
    return
  }

  const connectionName = useConnectionStore.getState().connections.find(
    (connection) => connection.id === nextConnectionId,
  )?.name ?? effectiveContext.connectionName ?? defaultContext.connectionName ?? null

  sqlWorkbenchStore.setTabContext(tabId, {
    connectionId: nextConnectionId,
    connectionName,
    database: nextDatabase,
    schema: nextSchema,
    source: 'api',
  })
  updateQueryEditorPayloadContextOverride(tabId, {
    connectionId: nextConnectionId,
    database: nextDatabase,
    schema: nextSchema,
  })
}
