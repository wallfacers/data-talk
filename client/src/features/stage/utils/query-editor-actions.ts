import { useConnectionStore } from '@/features/connection/store'
import { getCurrentLanguage } from '@/stores/ui-settings-store'
import { executeSql } from '@/services/api/sql'
import type { SqlExecuteRequest } from '@/services/api/sql'
import type { SessionDataContext } from '@/services/api/session-data-context'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore } from '@/stores/stage-store'
import { translateMessage } from '@/i18n/messages'
import { toast } from 'sonner'
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

function readDollarQuoteTag(sql: string, index: number) {
  if (sql[index] !== '$') return null
  if (sql[index + 1] === '$') return '$$'

  const end = sql.indexOf('$', index + 1)
  if (end <= index + 1) return null
  const tag = sql.slice(index, end + 1)
  return /^\$[A-Za-z_][A-Za-z0-9_]*\$$/.test(tag) ? tag : null
}

function splitSqlStatementsForLimit(sql: string) {
  const statements: string[] = []
  let current = ''
  let inSingleQuote = false
  let inDoubleQuote = false
  let inBacktick = false
  let inLineComment = false
  let blockCommentDepth = 0
  let inDollarQuote: string | null = null

  for (let i = 0; i < sql.length; i += 1) {
    const ch = sql[i]
    const next = sql[i + 1]

    if (inLineComment) {
      current += ch
      if (ch === '\n') inLineComment = false
      continue
    }

    if (blockCommentDepth > 0) {
      current += ch
      if (ch === '/' && sql[i - 1] === '*') {
        blockCommentDepth = Math.max(0, blockCommentDepth - 1)
      } else if (ch === '*' && next === '/') {
        current += next
        blockCommentDepth = Math.max(0, blockCommentDepth - 1)
        i += 1
      } else if (ch === '/' && next === '*') {
        current += next
        blockCommentDepth += 1
        i += 1
      }
      continue
    }

    if (inDollarQuote) {
      if (sql.startsWith(inDollarQuote, i)) {
        current += inDollarQuote
        i += inDollarQuote.length - 1
        inDollarQuote = null
        continue
      }
      current += ch
      continue
    }

    if (inSingleQuote) {
      current += ch
      if (ch === "'" && next === "'") {
        current += next
        i += 1
        continue
      }
      if (ch === "'") inSingleQuote = false
      continue
    }

    if (inDoubleQuote) {
      current += ch
      if (ch === '"' && next === '"') {
        current += next
        i += 1
        continue
      }
      if (ch === '"') inDoubleQuote = false
      continue
    }

    if (inBacktick) {
      current += ch
      if (ch === '`' && next === '`') {
        current += next
        i += 1
        continue
      }
      if (ch === '`') inBacktick = false
      continue
    }

    if (ch === '-' && next === '-') {
      current += ch + next
      inLineComment = true
      i += 1
      continue
    }
    if (ch === '/' && next === '*') {
      current += ch + next
      blockCommentDepth += 1
      i += 1
      continue
    }

    const dollarQuoteTag = readDollarQuoteTag(sql, i)
    if (dollarQuoteTag) {
      current += dollarQuoteTag
      inDollarQuote = dollarQuoteTag
      i += dollarQuoteTag.length - 1
      continue
    }

    if (ch === "'") {
      current += ch
      inSingleQuote = true
      continue
    }
    if (ch === '"') {
      current += ch
      inDoubleQuote = true
      continue
    }
    if (ch === '`') {
      current += ch
      inBacktick = true
      continue
    }

    current += ch
    if (ch === ';') {
      statements.push(current)
      current = ''
    }
  }

  if (current.length > 0) {
    statements.push(current)
  }
  return statements
}

function injectLimitIntoSingleStatement(statement: string, limit: 10 | 100 | 1000) {
  const trimmed = statement.trim()
  if (!trimmed) return statement

  const hasTrailingSemicolon = /;\s*$/.test(trimmed)
  const trimmedBody = hasTrailingSemicolon ? trimmed.replace(/;\s*$/, '') : trimmed

  if (!/^\s*(with\b|select\b)/i.test(trimmedBody)) return statement
  if (/\blimit\b/i.test(trimmedBody)) return statement

  const leadingWhitespace = statement.match(/^\s*/)?.[0] ?? ''
  const trailingWhitespace = statement.match(/\s*$/)?.[0] ?? ''
  const bodyWithoutOuterWhitespace = trimmedBody
  return `${leadingWhitespace}${bodyWithoutOuterWhitespace} LIMIT ${limit}${hasTrailingSemicolon ? ';' : ''}${trailingWhitespace}`
}

function injectLimit(sql: string, limit: 10 | 100 | 1000 | null) {
  if (limit == null) return sql
  const trimmed = sql.trim()
  if (!trimmed) return sql
  const statements = splitSqlStatementsForLimit(sql)
  if (statements.length <= 1) {
    return injectLimitIntoSingleStatement(sql, limit)
  }
  return statements.map((statement) => injectLimitIntoSingleStatement(statement, limit)).join('')
}

function getStageTab(tabId: string) {
  const stageState = useStageStore.getState()
  return stageState.tabs.find((tab) => tab.tabId === tabId) ?? null
}

function updateQueryEditorPayloadContextOverride(
  tabId: string,
  contextOverride: { connectionId: string; database: string | null; schema: string | null } | null,
  contextPinMode: 'session' | null = null,
) {
  useStageStore.getState().updateTabPayload(tabId, (payload) => {
    const basePayload = isPlainObject(payload) ? payload : {}
    return {
      ...basePayload,
      contextOverride,
      contextPinMode,
    }
  })
}

function resetTabExecutionState(tabId: string) {
  useSqlWorkbenchStore.getState().resetExecutionState(tabId)
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

function resolveImplicitTabContextOverride(
  tab: ReturnType<typeof getStageTab>,
  payload: ReturnType<typeof normalizeQueryEditorPayload>,
  fallbackConnectionName: string | null,
) {
  const connectionId = payload.connectionId ?? tab?.connectionId ?? null
  if (!connectionId) return null

  const shouldTreatAsFixed =
    payload.entryMode !== 'blank'
    || !tab?.originSessionId

  if (!shouldTreatAsFixed) return null

  return buildRuntimeOverrideFromPayload({
    connectionId,
    database: payload.database ?? tab?.database ?? null,
    schema: payload.schema ?? tab?.schema ?? null,
    fallbackConnectionName: payload.connectionName ?? tab?.connectionName ?? fallbackConnectionName,
  })
}

function resolveImplicitSessionContextOverride(
  payload: ReturnType<typeof normalizeQueryEditorPayload>,
  sessionContext: SessionDataContext | null,
) {
  if (payload.contextPinMode === 'session') return null
  if (!sessionContext?.connectionId) return null

  return buildRuntimeOverrideFromPayload({
    connectionId: sessionContext.connectionId,
    database: sessionContext.database ?? null,
    schema: sessionContext.schema ?? null,
    fallbackConnectionName: sessionContext.connectionNameSnapshot ?? null,
  })
}

function resolveQueryEditorContexts(tabId: string, sessionIdOverride: string | null) {
  const tab = getStageTab(tabId)
  const payload = normalizeQueryEditorPayload(tab?.payload)
  const sqlWorkbenchState = useSqlWorkbenchStore.getState()
  const tabState = sqlWorkbenchState.tabsById[tabId] ?? null
  const connectionState = useConnectionStore.getState()
  const sessionId = sessionIdOverride ?? tab?.originSessionId ?? useSessionStore.getState().activeSessionId ?? null
  const sessionContext = sessionId
    ? useSessionStore.getState().dataContextBySession.get(sessionId) ?? null
    : null

  const resolvedContext = resolveTabDataContext(
    {
      originSessionId: sessionId,
    },
    sessionContext,
      {
        inheritSessionContext: true,
        preferSessionContext: true,
        fallbackConnectionId: connectionState.activeConnectionId ?? null,
        connectionNameLookup: (connectionId) =>
          connectionState.connections.find((connection) => connection.id === connectionId)?.name ?? null,
      },
    )

  const defaultContext: QueryEditorExecutionContext = {
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
    : (
        resolveImplicitTabContextOverride(tab, payload, defaultContext.connectionName)
        ?? resolveImplicitSessionContextOverride(payload, sessionContext)
      ))

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
  sqlOverride?: string | null
}): Promise<{ executeStatus: 'success' | 'error' | 'requires_confirmation' | 'confirmation_invalid'; activeResultId: string | null }> {
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
  const rawExecutableSql = params.sqlOverride?.trim()
    ? params.sqlOverride
    : tabState?.sqlText

  if (!tabState || !effectiveContext.connectionId || !rawExecutableSql?.trim()) {
    return { executeStatus: 'error', activeResultId: null }
  }

  const controller = new AbortController()
  const controllers = getQueryEditorRunControllers()
  controllers.set(tabId, controller)

  const sqlWorkbenchStore = useSqlWorkbenchStore.getState()
  sqlWorkbenchStore.setRunning(tabId)
  const startedAt = Date.now()
  const executableSql = injectLimit(rawExecutableSql, params.limit === undefined ? tabState.limit : params.limit)

  try {
    const request: SqlExecuteRequest = {
      sql: executableSql,
      connectionId: effectiveContext.connectionId,
      source: tabState.source,
    }
    if (effectiveContext.sessionId != null) request.sessionId = effectiveContext.sessionId
    if (effectiveContext.database != null) request.database = effectiveContext.database
    if (effectiveContext.schema != null) request.schema = effectiveContext.schema

    const response = await executeSql(request, controller.signal)

    if (response.status === 'requires_confirmation') {
      sqlWorkbenchStore.setRequiresConfirmation(tabId, response.confirmation, request)
      sqlWorkbenchStore.appendHistoryEntry(tabId, {
        id: `history-${startedAt}-${Math.random().toString(36).slice(2, 8)}`,
        at: Date.now(),
        sql: executableSql,
        status: 'requires_confirmation',
        elapsedMs: Date.now() - startedAt,
        confirmationReason: response.confirmation.reason,
      })
      return {
        executeStatus: 'requires_confirmation',
        activeResultId: null,
      }
    }

    if (response.status === 'confirmation_invalid') {
      sqlWorkbenchStore.setConfirmationInvalid(tabId, response.invalidConfirmation, request)
      return {
        executeStatus: 'confirmation_invalid',
        activeResultId: null,
      }
    }

    const results = response.results
    sqlWorkbenchStore.applyExecuteSuccess(tabId, response)
    sqlWorkbenchStore.appendHistoryEntry(tabId, {
      id: `history-${startedAt}-${Math.random().toString(36).slice(2, 8)}`,
      at: Date.now(),
      sql: executableSql,
      status: 'ok',
      resultCount: results.length,
      elapsedMs: Date.now() - startedAt,
      resultKinds: results.map((item) => item.kind),
    })

    return {
      executeStatus: 'success',
      activeResultId: useSqlWorkbenchStore.getState().tabsById[tabId]?.activeResultId ?? results[0]?.resultId ?? null,
    }
  } catch (error) {
    if (isAbortError(error)) {
      resetTabExecutionState(tabId)
      throw error
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

export async function confirmQueryEditorSql(params: {
  tabId: string
  sessionId: string | null
  level: 'L2' | 'L3'
}): Promise<{ executeStatus: 'success' | 'error' | 'confirmation_invalid'; activeResultId: string | null }> {
  const { tabId, level } = params
  const sqlWorkbenchStore = useSqlWorkbenchStore.getState()
  const tabState = sqlWorkbenchStore.tabsById[tabId]
  if (!tabState?.lastRequest) {
    return { executeStatus: 'error', activeResultId: null }
  }

  const controller = new AbortController()
  const controllers = getQueryEditorRunControllers()
  controllers.set(tabId, controller)

  const startedAt = Date.now()
  const request: SqlExecuteRequest = {
    ...tabState.lastRequest,
    confirmed: true,
    riskAck: level,
  }

  sqlWorkbenchStore.setConfirming(tabId)

  try {
    const response = await executeSql(request, controller.signal)

    if (response.status === 'confirmation_invalid') {
      sqlWorkbenchStore.setConfirmationInvalid(tabId, response.invalidConfirmation, request)
      return {
        executeStatus: 'confirmation_invalid',
        activeResultId: null,
      }
    }

    const results = response.status === 'executed' ? response.results : []
    sqlWorkbenchStore.applyExecuteSuccess(tabId, response)
    sqlWorkbenchStore.appendHistoryEntry(tabId, {
      id: `history-${startedAt}-${Math.random().toString(36).slice(2, 8)}`,
      at: Date.now(),
      sql: request.sql,
      status: 'ok',
      resultCount: results.length,
      elapsedMs: Date.now() - startedAt,
      resultKinds: results.map((item) => item.kind),
    })

    return {
      executeStatus: 'success',
      activeResultId: useSqlWorkbenchStore.getState().tabsById[tabId]?.activeResultId ?? results[0]?.resultId ?? null,
    }
  } catch (error) {
    if (isAbortError(error)) {
      resetTabExecutionState(tabId)
      throw error
    }

    const language = getCurrentLanguage()
    const errorMessage = error instanceof Error ? error.message : translateMessage(language, 'stage.queryEditor.runFailed')
    // Spec §9: confirmation request that fails to reach the server should
    // toast and not leave the AlertDialog stuck on `confirming`.
    toast.error(translateMessage(language, 'stage.queryEditor.confirmFailed'))
    sqlWorkbenchStore.setError(tabId, errorMessage, {
      resultId: `error-${startedAt}-${Math.random().toString(36).slice(2, 8)}`,
      kind: 'error',
      title: translateMessage(language, 'stage.status.error'),
      statementIndex: 0,
      statementText: request.sql,
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
      sql: request.sql,
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

export function cancelQueryEditorConfirmation(tabId: string): void {
  useSqlWorkbenchStore.getState().cancelConfirmation(tabId)
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

  const next = useStageStore.getState().replaceQueryEditorContent(tabId, formatted, tabState.version)
  if (!next.ok) {
    return {
      version: next.currentState.version,
      content: next.currentState.content,
    }
  }
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

  const { defaultContext, effectiveContext, persistedBaseContext } = resolveQueryEditorContexts(tabId, null)
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
    updateQueryEditorPayloadContextOverride(tabId, null, 'session')
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
  }, null)
}

export function resetQueryEditorContext(tabId: string): void {
  const stageTab = getStageTab(tabId)
  if (!stageTab) return

  useStageStore.getState().setQueryEditorContext(tabId, {
    connectionId: null,
    connectionName: null,
    database: null,
    schema: null,
  })
  useSqlWorkbenchStore.getState().resetTabContext(tabId)
  updateQueryEditorPayloadContextOverride(tabId, null, 'session')
}
