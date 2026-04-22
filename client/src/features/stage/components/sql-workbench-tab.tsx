import { useCallback, useEffect, useMemo, useRef, useState, type MouseEvent as ReactMouseEvent } from 'react'
import { useShallow } from 'zustand/react/shallow'
import type { StageTab } from '@/stores/stage-store'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionDataContext } from '@/features/session/hooks/use-session-data-context'
import { useI18n } from '@/i18n/use-i18n'
import { cn } from '@/lib/utils'
import { SqlRiskError } from '@/services/api/sql'
import { resolveTabDataContext } from '@/features/stage/utils/resolve-tab-data-context'
import { formatSql } from '../utils/format-sql'
import { parseSqlOutline, resolveCurrentSqlOutlineStatement } from '../utils/parse-sql-outline'
import { normalizeQueryEditorPayload } from '../utils/normalize-query-editor-payload'
import { useSqlExecute } from '../hooks/use-sql-execute'
import { useSqlWorkbenchStore } from '../stores/sql-workbench-store'
import type { SqlMonacoEditorHandle } from './sql-monaco-editor'
import { SqlContextChip } from './sql-context-chip'
import type { SqlContextValue } from './sql-context-chip'
import { SqlEditorToolbar } from './sql-editor-toolbar'
import { SqlMonacoEditor } from './sql-monaco-editor'
import { SqlResultTabs } from './sql-result-tabs'
import { SqlResultPanel } from './sql-result-panel'
import { SqlWorkbenchStatusBar } from './sql-workbench-status-bar'
import type { SqlLimitValue } from './sql-limit-select'

type TabExecutionContext = {
  sessionId: string | null
  connectionId: string | null
  connectionName: string | null
  database: string | null
  schema: string | null
}

export type SqlWorkbenchTabActions = {
  insertAtCursor: (text: string) => void
  replaceSelection: (text: string) => void
}

const tabActionsById = new Map<string, SqlWorkbenchTabActions>()

const EMPTY_WORKBENCH_STATE = {
  sqlText: '',
  source: 'user' as const,
  executeStatus: 'idle' as const,
  results: [],
  activeResultId: null,
  resolvedContext: null,
  contextNotice: null,
  risk: null,
  errorMessage: null,
  override: null,
  savedSqlText: '',
  limit: 100 as const,
  cursor: { line: 1, column: 1 },
}

const DRAFT_STORAGE_PREFIX = 'data-talk:sql-workbench:draft:'
const RESULT_PANE_MIN_PERCENT = 22
const RESULT_PANE_MAX_PERCENT = 64
const RESULT_PANE_DEFAULT_PERCENT = 38

export function getSqlWorkbenchTabActions(tabId: string) {
  return tabActionsById.get(tabId) ?? null
}

export function registerSqlWorkbenchTabActions(tabId: string, actions: SqlWorkbenchTabActions) {
  tabActionsById.set(tabId, actions)
}

export function unregisterSqlWorkbenchTabActions(tabId: string) {
  tabActionsById.delete(tabId)
}

function isAbortError(error: unknown) {
  return (error instanceof DOMException && error.name === 'AbortError') || (error instanceof Error && error.name === 'AbortError')
}

function injectLimit(sql: string, limit: SqlLimitValue) {
  if (limit == null) return sql
  const trimmed = sql.trim()
  if (!trimmed) return sql
  if (!/^\s*(with\b|select\b)/i.test(trimmed)) return sql
  if (/\blimit\b/i.test(trimmed)) return sql

  const hasTrailingSemicolon = trimmed.endsWith(';')
  const body = hasTrailingSemicolon ? trimmed.slice(0, -1).trimEnd() : trimmed
  return `${body} LIMIT ${limit}${hasTrailingSemicolon ? ';' : ''}`
}

function insertTextAtPosition(value: string, insertedText: string, lineNumber: number, column: number) {
  const lines = value.split(/\r\n|\r|\n/)
  while (lines.length < lineNumber) {
    lines.push('')
  }

  const lineIndex = Math.max(0, lineNumber - 1)
  const line = lines[lineIndex] ?? ''
  const before = line.slice(0, Math.max(0, column - 1))
  const after = line.slice(Math.max(0, column - 1))
  const insertedLines = insertedText.split(/\r\n|\r|\n/)

  if (insertedLines.length === 1) {
    lines[lineIndex] = `${before}${insertedText}${after}`
    return lines.join('\n')
  }

  const firstLine = `${before}${insertedLines[0]}`
  const lastLine = `${insertedLines[insertedLines.length - 1]}${after}`
  const middleLines = insertedLines.slice(1, -1)

  lines.splice(lineIndex, 1, firstLine, ...middleLines, lastLine)
  return lines.join('\n')
}

function replaceSqlStatementRange(value: string, startLine: number, endLine: number, insertedText: string) {
  const lines = value.split(/\r\n|\r|\n/)
  while (lines.length < endLine) {
    lines.push('')
  }

  const insertedLines = insertedText.split(/\r\n|\r|\n/)
  const next = [
    ...lines.slice(0, Math.max(0, startLine - 1)),
    ...insertedLines,
    ...lines.slice(Math.max(0, endLine)),
  ]
  return next.join('\n')
}

function toContextValue(
  connectionId: string | null,
  connectionName: string | null,
  database: string | null,
  schema: string | null,
) {
  if (!connectionId) return null
  return { connectionId, connectionName, database, schema }
}

function collectContextOptionValues(values: Array<string | null | undefined>) {
  const set = new Set<string>()
  for (const value of values) {
    if (value == null) continue
    const trimmed = value.trim()
    if (trimmed.length > 0) {
      set.add(trimmed)
    }
  }
  return Array.from(set)
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

export function SqlWorkbenchTab({ tab }: { tab: StageTab }) {
  const { t } = useI18n()
  const payload = normalizeQueryEditorPayload(tab.payload)
  const autoRunRef = useRef(false)
  const activeControllerRef = useRef<AbortController | null>(null)
  const monacoRef = useRef<SqlMonacoEditorHandle | null>(null)
  const splitLayoutRef = useRef<HTMLDivElement | null>(null)
  const resizeCleanupRef = useRef<(() => void) | null>(null)
  const [draftReady, setDraftReady] = useState(false)
  const [resultPanePercent, setResultPanePercent] = useState(RESULT_PANE_DEFAULT_PERCENT)
  const { execute } = useSqlExecute()
  const { activeConnectionId, connections } = useConnectionStore(
    useShallow((state) => ({
      activeConnectionId: state.activeConnectionId,
      connections: state.connections,
    })),
  )
  const sessionDataContext = useSessionDataContext(tab.originSessionId ?? null)

  const resolvedContext = resolveTabDataContext(
    {
      originSessionId: tab.originSessionId ?? null,
      connectionId: payload.connectionId ?? tab.connectionId ?? null,
      connectionName: payload.connectionName ?? tab.connectionName ?? null,
      database: payload.database ?? tab.database ?? null,
      schema: payload.schema ?? tab.schema ?? null,
    },
    sessionDataContext.context,
    {
      inheritSessionContext: true,
      fallbackConnectionId: activeConnectionId ?? null,
      connectionNameLookup: (connectionId) =>
        connections.find((connection) => connection.id === connectionId)?.name ?? null,
    },
  )

  const {
    ensureTab,
    setSqlText,
    setActiveResult,
    setRunning,
    applyExecuteSuccess,
    setRiskBlocked,
    setError,
    setTabContext,
    resetTabContext,
    appendHistoryEntry,
    setLimit,
    setCursor,
    closeResult,
    closeOtherResults,
    closeAllResults,
  } = useSqlWorkbenchStore(
    useShallow((state) => ({
      ensureTab: state.ensureTab,
      setSqlText: state.setSqlText,
      setActiveResult: state.setActiveResult,
      setRunning: state.setRunning,
      applyExecuteSuccess: state.applyExecuteSuccess,
      setRiskBlocked: state.setRiskBlocked,
      setError: state.setError,
      setTabContext: state.setTabContext,
      resetTabContext: state.resetTabContext,
      appendHistoryEntry: state.appendHistoryEntry,
      setLimit: state.setLimit,
      setCursor: state.setCursor,
      closeResult: state.closeResult,
      closeOtherResults: state.closeOtherResults,
      closeAllResults: state.closeAllResults,
    })),
  )

  const tabState = useSqlWorkbenchStore((state) => state.tabsById[tab.tabId]) ?? EMPTY_WORKBENCH_STATE
  const draftStorageKey = `${DRAFT_STORAGE_PREFIX}${tab.tabId}`
  const outline = useMemo(() => parseSqlOutline(tabState.sqlText), [tabState.sqlText])
  const totalLines = useMemo(() => Math.max(1, tabState.sqlText.split(/\r\n|\r|\n/).length), [tabState.sqlText])
  const currentStatement = useMemo(
    () => resolveCurrentSqlOutlineStatement(outline, tabState.cursor.line, totalLines),
    [outline, tabState.cursor.line, totalLines],
  )
  const displayResults = tabState.results
  const activeResult = useMemo(
    () => displayResults.find((item) => item.resultId === tabState.activeResultId) ?? displayResults[0] ?? null,
    [displayResults, tabState.activeResultId],
  )
  const showResultPane = displayResults.length > 0

  useEffect(() => {
    ensureTab(tab.tabId, {
      sqlText: payload.initialSql,
      source: payload.source,
    })
  }, [ensureTab, payload.initialSql, payload.source, tab.tabId])

  useEffect(() => {
    if (typeof window === 'undefined') return
    const draftSql = window.localStorage.getItem(draftStorageKey)
    if (draftSql != null) {
      setSqlText(tab.tabId, draftSql)
    }
    setDraftReady(true)
  }, [draftStorageKey, setSqlText, tab.tabId])

  useEffect(() => {
    if (!draftReady || typeof window === 'undefined') return
    try {
      window.localStorage.setItem(draftStorageKey, tabState.sqlText)
    } catch {
      // ignore storage failures
    }
  }, [draftReady, draftStorageKey, tabState.sqlText])

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && tabState.executeStatus === 'running') {
        activeControllerRef.current?.abort()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [tabState.executeStatus])

  const resolvedExecutionContext: TabExecutionContext = tabState.resolvedContext
    ? {
        sessionId: resolvedContext.sessionId ?? tab.originSessionId ?? null,
        connectionId: tabState.resolvedContext.connectionId,
        connectionName: tabState.resolvedContext.connectionName,
        database: tabState.resolvedContext.database,
        schema: tabState.resolvedContext.schema,
      }
    : {
        sessionId: resolvedContext.sessionId ?? tab.originSessionId ?? null,
        connectionId: resolvedContext.connectionId,
        connectionName: resolvedContext.connectionName,
        database: resolvedContext.database,
        schema: resolvedContext.schema,
      }
  const effectiveContext = tabState.override
    ? {
        sessionId: resolvedExecutionContext.sessionId ?? tab.originSessionId ?? null,
        connectionId: tabState.override.connectionId,
        connectionName: tabState.override.connectionName ?? resolvedExecutionContext.connectionName,
        database: tabState.override.database ?? resolvedExecutionContext.database,
        schema: tabState.override.schema ?? resolvedExecutionContext.schema,
      }
    : {
        sessionId: resolvedExecutionContext.sessionId ?? tab.originSessionId ?? null,
        connectionId: resolvedExecutionContext.connectionId,
        connectionName: resolvedExecutionContext.connectionName,
        database: resolvedExecutionContext.database,
        schema: resolvedExecutionContext.schema,
      }
  const canRun = Boolean(effectiveContext.connectionId) && tabState.sqlText.trim().length > 0
  const contextChipContext = toContextValue(
    effectiveContext.connectionId,
    effectiveContext.connectionName,
    effectiveContext.database,
    effectiveContext.schema,
  )
  const contextMode = tabState.override ? 'override' : 'session'
  const contextConnectionOptions = useMemo(
    () => connections.map((connection) => ({
      id: connection.id,
      name: connection.name,
      databaseName: connection.databaseName ?? null,
    })),
    [connections],
  )
  const contextDatabaseOptions = useMemo(() => collectContextOptionValues([
    effectiveContext.database,
    resolvedExecutionContext.database,
    resolvedContext.database,
    payload.database,
    tab.database,
    tabState.override?.database,
    sessionDataContext.context?.database,
    ...connections.map((connection) => connection.databaseName),
  ]), [
    connections,
    effectiveContext.database,
    payload.database,
    resolvedContext.database,
    resolvedExecutionContext.database,
    sessionDataContext.context?.database,
    tab.database,
    tabState.override?.database,
  ])
  const contextSchemaOptions = useMemo(() => collectContextOptionValues([
    effectiveContext.schema,
    resolvedExecutionContext.schema,
    resolvedContext.schema,
    payload.schema,
    tab.schema,
    tabState.override?.schema,
    sessionDataContext.context?.schema,
  ]), [
    effectiveContext.schema,
    payload.schema,
    resolvedContext.schema,
    resolvedExecutionContext.schema,
    sessionDataContext.context?.schema,
    tab.schema,
    tabState.override?.schema,
  ])
  const effectiveConnectionKind = useMemo(
    () => connections.find((connection) => connection.id === effectiveContext.connectionId)?.kind ?? null,
    [connections, effectiveContext.connectionId],
  )

  const applyIdleState = useCallback(() => resetTabExecutionState(tab.tabId), [tab.tabId])

  const stopResize = useCallback(() => {
    resizeCleanupRef.current?.()
    resizeCleanupRef.current = null
  }, [])

  const updateResultPanePercent = useCallback((clientY: number) => {
    const layout = splitLayoutRef.current
    if (!layout) return

    const rect = layout.getBoundingClientRect()
    if (rect.height <= 0) return

    const nextPercent = ((rect.bottom - clientY) / rect.height) * 100
    const clampedPercent = Math.min(
      RESULT_PANE_MAX_PERCENT,
      Math.max(RESULT_PANE_MIN_PERCENT, nextPercent),
    )
    setResultPanePercent(clampedPercent)
  }, [])

  const handleSplitterMouseDown = useCallback((event: ReactMouseEvent<HTMLDivElement>) => {
    event.preventDefault()
    stopResize()

    const onMouseMove = (moveEvent: MouseEvent) => {
      updateResultPanePercent(moveEvent.clientY)
    }
    const onMouseUp = () => {
      stopResize()
    }

    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp, { once: true })

    resizeCleanupRef.current = () => {
      window.removeEventListener('mousemove', onMouseMove)
      window.removeEventListener('mouseup', onMouseUp)
    }
  }, [stopResize, updateResultPanePercent])

  useEffect(() => stopResize, [stopResize])

  const handleRun = useCallback(async () => {
    if (!effectiveContext.connectionId || !tabState.sqlText.trim()) return

    const controller = new AbortController()
    activeControllerRef.current = controller
    setRunning(tab.tabId)
    const startedAt = Date.now()
    const executableSql = injectLimit(tabState.sqlText, tabState.limit)

    try {
      const response = await execute(
        executableSql,
        effectiveContext.connectionId,
        tabState.source,
        {
          sessionId: effectiveContext.sessionId ?? undefined,
          database: effectiveContext.database,
          schema: effectiveContext.schema,
        },
        controller.signal,
      )
      applyExecuteSuccess(tab.tabId, response)
      appendHistoryEntry(tab.tabId, {
        id: `history-${startedAt}-${Math.random().toString(36).slice(2, 8)}`,
        at: Date.now(),
        sql: executableSql,
        status: 'ok',
        resultCount: response.results.length,
        elapsedMs: Date.now() - startedAt,
        resultKinds: response.results.map((item) => item.kind),
      })
    } catch (error) {
      if (isAbortError(error)) {
        applyIdleState()
        return
      }
      if (error instanceof SqlRiskError) {
        setRiskBlocked(tab.tabId, error.risk)
        appendHistoryEntry(tab.tabId, {
          id: `history-${startedAt}-${Math.random().toString(36).slice(2, 8)}`,
          at: Date.now(),
          sql: executableSql,
          status: 'risk_blocked',
          elapsedMs: Date.now() - startedAt,
          errorSummary: error.risk.riskReason,
        })
        return
      }
      const errorMessage = error instanceof Error ? error.message : t('stage.queryEditor.runFailed')
      setError(tab.tabId, errorMessage, {
        resultId: `error-${startedAt}-${Math.random().toString(36).slice(2, 8)}`,
        kind: 'error',
        title: t('stage.status.error'),
        statementIndex: 0,
        statementText: executableSql,
        columns: [],
        rows: [],
        rowCount: 0,
        executionMs: Date.now() - startedAt,
        truncated: false,
        errorMessage,
      })
      appendHistoryEntry(tab.tabId, {
        id: `history-${startedAt}-${Math.random().toString(36).slice(2, 8)}`,
        at: Date.now(),
        sql: executableSql,
        status: 'error',
        elapsedMs: Date.now() - startedAt,
        errorSummary: errorMessage,
      })
    } finally {
      if (activeControllerRef.current === controller) {
        activeControllerRef.current = null
      }
    }
  }, [
    applyExecuteSuccess,
    applyIdleState,
    appendHistoryEntry,
    effectiveContext.connectionId,
    effectiveContext.database,
    effectiveContext.schema,
    effectiveContext.sessionId,
    execute,
    setError,
    setRiskBlocked,
    setRunning,
    tab.tabId,
    tabState.limit,
    tabState.source,
    tabState.sqlText,
    t,
  ])

  useEffect(() => {
    if (!payload.autoRun || autoRunRef.current || !effectiveContext.connectionId) return
    autoRunRef.current = true
    void handleRun()
  }, [effectiveContext.connectionId, handleRun, payload.autoRun])

  const handleFormat = useCallback(() => {
    const rawSql = tabState.sqlText
    if (!rawSql.trim()) return
    const formatted = formatSql(rawSql, effectiveConnectionKind)
    if (formatted !== rawSql) {
      setSqlText(tab.tabId, formatted)
    }
  }, [effectiveConnectionKind, setSqlText, tab.tabId, tabState.sqlText])

  const handleContextPin = useCallback((nextContext?: SqlContextValue) => {
    const contextToPin = nextContext ?? contextChipContext
    if (!contextToPin) return
    setTabContext(tab.tabId, {
      ...contextToPin,
      source: 'user_toolbar',
    })
  }, [contextChipContext, setTabContext, tab.tabId])

  const handleContextReset = useCallback(() => {
    resetTabContext(tab.tabId)
  }, [resetTabContext, tab.tabId])

  const handleCursorChange = useCallback(
    (cursor: { line: number; column: number }) => {
      setCursor(tab.tabId, cursor.line, cursor.column)
    },
    [setCursor, tab.tabId],
  )

  const handleSelectResult = useCallback((resultId: string) => {
    setActiveResult(tab.tabId, resultId)
  }, [setActiveResult, tab.tabId])

  const handleCloseResult = useCallback((resultId: string) => {
    closeResult(tab.tabId, resultId)
  }, [closeResult, tab.tabId])

  const handleCloseOtherResults = useCallback((resultId: string) => {
    closeOtherResults(tab.tabId, resultId)
  }, [closeOtherResults, tab.tabId])

  const handleCloseAllResults = useCallback(() => {
    closeAllResults(tab.tabId)
  }, [closeAllResults, tab.tabId])

  useEffect(() => {
    registerSqlWorkbenchTabActions(tab.tabId, {
      insertAtCursor: (text: string) => {
        if (monacoRef.current) {
          monacoRef.current.insertAtCursor(text)
          return
        }
        setSqlText(tab.tabId, insertTextAtPosition(tabState.sqlText, text, tabState.cursor.line, tabState.cursor.column))
      },
      replaceSelection: (text: string) => {
        if (currentStatement) {
          setSqlText(tab.tabId, replaceSqlStatementRange(tabState.sqlText, currentStatement.line, currentStatement.endLine, text))
          return
        }
        if (monacoRef.current) {
          monacoRef.current.insertAtCursor(text)
          return
        }
        setSqlText(tab.tabId, insertTextAtPosition(tabState.sqlText, text, tabState.cursor.line, tabState.cursor.column))
      },
    })

    return () => unregisterSqlWorkbenchTabActions(tab.tabId)
  }, [
    currentStatement,
    setSqlText,
    tab.tabId,
    tabState.cursor.column,
    tabState.cursor.line,
    tabState.sqlText,
  ])

  return (
    <div data-testid="sql-workbench-tab" className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-row bg-background">
      <div ref={splitLayoutRef} data-testid="sql-workbench-layout" className="flex min-h-0 min-w-0 flex-1 flex-col">
        <section
          className={cn(
            'flex min-h-0 flex-col',
            showResultPane ? 'flex-none' : 'flex-1',
          )}
          style={showResultPane ? { flexBasis: `${100 - resultPanePercent}%` } : undefined}
        >
          <SqlEditorToolbar
            canRun={canRun}
            isRunning={tabState.executeStatus === 'running'}
            onRun={() => void handleRun()}
            onCancel={() => activeControllerRef.current?.abort()}
            onFormat={handleFormat}
            limit={tabState.limit}
            onLimitChange={(value) => setLimit(tab.tabId, value)}
            contextChip={
              <SqlContextChip
                mode={contextMode}
                context={contextChipContext}
                connections={contextConnectionOptions}
                databaseOptions={contextDatabaseOptions}
                schemaOptions={contextSchemaOptions}
                onSetTabContext={handleContextPin}
                onResetTabContext={handleContextReset}
              />
            }
          />
          <div className={cn('min-h-0 flex-1 bg-background px-2 pt-1', showResultPane ? 'pb-0' : 'pb-2')}>
            <div className="flex h-full min-h-0 flex-col">
              <SqlMonacoEditor
                ref={monacoRef}
                value={tabState.sqlText}
                onChange={(next) => setSqlText(tab.tabId, next)}
                onRun={() => void handleRun()}
                onFormat={handleFormat}
                onCursorChange={handleCursorChange}
                currentStatementRange={
                  currentStatement
                    ? {
                        startLine: currentStatement.line,
                        endLine: currentStatement.endLine,
                      }
                    : null
                }
                shellMode={showResultPane ? 'connected' : 'standalone'}
              />
              {showResultPane ? (
                <div
                  className="relative -mt-px h-0 shrink-0 overflow-visible"
                >
                  <div
                    data-testid="sql-workbench-result-splitter"
                    role="separator"
                    aria-orientation="horizontal"
                    aria-label="Resize SQL result panel"
                    className="group absolute inset-x-0 -top-1 h-2 cursor-row-resize bg-transparent"
                    onMouseDown={handleSplitterMouseDown}
                  >
                    <div className="pointer-events-none absolute inset-x-0 top-1/2 -translate-y-1/2">
                      <div className="h-px w-full bg-border/65 transition-all duration-150 group-hover:h-1 group-hover:bg-primary/50 group-active:h-1 group-active:bg-primary/50" />
                    </div>
                  </div>
                </div>
              ) : null}
            </div>
          </div>
          <SqlWorkbenchStatusBar
            status={tabState.executeStatus}
            riskReason={tabState.risk?.riskReason ?? null}
          />
        </section>

        {showResultPane ? (
          <section
            data-testid="sql-result-pane"
            className="flex min-h-0 flex-none flex-col"
            style={{ flexBasis: `${resultPanePercent}%` }}
          >
            <div className="flex min-h-0 flex-1 flex-col bg-background px-2 pb-2">
              <div
                data-testid="sql-result-shell"
                className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-b-xl border-x border-b border-border/50 bg-background"
              >
                <SqlResultTabs
                  results={displayResults}
                  activeResultId={activeResult?.resultId ?? tabState.activeResultId}
                  onSelect={handleSelectResult}
                  onClose={handleCloseResult}
                  onCloseOthers={handleCloseOtherResults}
                  onCloseAll={handleCloseAllResults}
                />
                <div className="min-h-0 flex-1 overflow-hidden">
                  <SqlResultPanel
                    executeStatus={tabState.executeStatus}
                    activeResult={activeResult}
                    risk={tabState.risk}
                    errorMessage={tabState.errorMessage}
                  />
                </div>
              </div>
            </div>
          </section>
        ) : null}
      </div>
    </div>
  )
}
