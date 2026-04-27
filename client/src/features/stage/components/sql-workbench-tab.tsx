import { useCallback, useEffect, useMemo, useRef, useState, type MouseEvent as ReactMouseEvent } from 'react'
import { useShallow } from 'zustand/react/shallow'
import type { StageTab } from '@/stores/stage-store'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionDataContext } from '@/features/session/hooks/use-session-data-context'
import { cn } from '@/lib/utils'
import { listConnections } from '@/services/api/connection'
import { resolveTabDataContext } from '@/features/stage/utils/resolve-tab-data-context'
import { parseSqlOutline, resolveCurrentSqlOutlineStatement } from '../utils/parse-sql-outline'
import { normalizeQueryEditorPayload } from '../utils/normalize-query-editor-payload'
import {
  formatQueryEditorSql,
  runQueryEditorSql,
  setQueryEditorContext,
  confirmQueryEditorSql,
  cancelQueryEditorConfirmation,
} from '../utils/query-editor-actions'
import { useSqlWorkbenchStore } from '../stores/sql-workbench-store'
import type { SqlMonacoEditorHandle } from './sql-monaco-editor'
import { SqlContextChip } from './sql-context-chip'
import type { SqlContextConnectionTargets } from './sql-context-chip'
import type { SqlContextValue } from './sql-context-chip'
import { SqlEditorToolbar } from './sql-editor-toolbar'
import { SqlMonacoEditor } from './sql-monaco-editor'
import { SqlResultTabs } from './sql-result-tabs'
import { SqlResultPanel } from './sql-result-panel'
import type { ResultScrollPosition } from './sql-result-table'
import { StageActivityRail } from './activity-rail/stage-activity-rail'
import { useI18n } from '@/i18n/use-i18n'
import { AlertDialog, AlertDialogContent, AlertDialogHeader, AlertDialogTitle } from '@/components/ui/alert-dialog'
import { SqlConfirmationCard } from '@/features/sql-confirmation/sql-confirmation-card'

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
  selection: null,
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
const QUERY_EDITOR_RUN_CONTROLLERS_KEY = '__data_talk_query_editor_run_controllers__'
const DEFAULT_RESULT_SCROLL_POSITION: ResultScrollPosition = { scrollTop: 0, scrollLeft: 0 }

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

function getQueryEditorRunControllers() {
  const globalState = globalThis as typeof globalThis & {
    [QUERY_EDITOR_RUN_CONTROLLERS_KEY]?: Map<string, AbortController>
  }
  if (!globalState[QUERY_EDITOR_RUN_CONTROLLERS_KEY]) {
    globalState[QUERY_EDITOR_RUN_CONTROLLERS_KEY] = new Map<string, AbortController>()
  }
  return globalState[QUERY_EDITOR_RUN_CONTROLLERS_KEY]
}

function abortQueryEditorRun(tabId: string) {
  getQueryEditorRunControllers().get(tabId)?.abort()
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

function resolveTextOffset(value: string, line: number, column: number) {
  let currentLine = 1
  let lineStart = 0

  for (let index = 0; index < value.length; index += 1) {
    const char = value[index]
    if (char !== '\n' && char !== '\r') continue

    if (currentLine === line) {
      const lineLength = index - lineStart
      return lineStart + Math.min(Math.max(0, column - 1), lineLength)
    }

    if (char === '\r' && value[index + 1] === '\n') {
      index += 1
    }
    currentLine += 1
    lineStart = index + 1
  }

  if (currentLine === line) {
    return lineStart + Math.min(Math.max(0, column - 1), value.length - lineStart)
  }
  return value.length
}

function getSelectedSqlText(
  value: string,
  selection: { startLine: number; startColumn: number; endLine: number; endColumn: number } | null,
) {
  if (!selection) return null
  const startOffset = resolveTextOffset(value, selection.startLine, selection.startColumn)
  const endOffset = resolveTextOffset(value, selection.endLine, selection.endColumn)
  if (startOffset === endOffset) return null

  const selectedText = value.slice(Math.min(startOffset, endOffset), Math.max(startOffset, endOffset))
  return selectedText.trim().length > 0 ? selectedText : null
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

export function SqlWorkbenchTab({ tab }: { tab: StageTab }) {
  const { t } = useI18n()
  const payload = normalizeQueryEditorPayload(tab.payload)
  const autoRunRef = useRef(false)
  const monacoRef = useRef<SqlMonacoEditorHandle | null>(null)
  const splitLayoutRef = useRef<HTMLDivElement | null>(null)
  const resizeCleanupRef = useRef<(() => void) | null>(null)
  const [draftLoadedTabId, setDraftLoadedTabId] = useState<string | null>(null)
  const [resultPanePercent, setResultPanePercent] = useState(RESULT_PANE_DEFAULT_PERCENT)
  const [resultScrollPositionsById, setResultScrollPositionsById] = useState<Record<string, ResultScrollPosition>>({})
  const [connectionTargetsByConnectionId, setConnectionTargetsByConnectionId] = useState<Record<string, SqlContextConnectionTargets>>({})
  const pendingConnectionTargetsRef = useRef<Set<string>>(new Set())
  const { activeConnectionId, connections, setConnections } = useConnectionStore(
    useShallow((state) => ({
      activeConnectionId: state.activeConnectionId,
      connections: state.connections,
      setConnections: state.setConnections,
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
    setLimit,
    setCursor,
    setSelection,
    closeResult,
    closeOtherResults,
    closeAllResults,
  } = useSqlWorkbenchStore(
    useShallow((state) => ({
      ensureTab: state.ensureTab,
      setSqlText: state.setSqlText,
      setActiveResult: state.setActiveResult,
      setLimit: state.setLimit,
      setCursor: state.setCursor,
      setSelection: state.setSelection,
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
  const activeResultId = activeResult?.resultId ?? null
  const activeResultScrollPosition = activeResultId
    ? resultScrollPositionsById[activeResultId] ?? DEFAULT_RESULT_SCROLL_POSITION
    : DEFAULT_RESULT_SCROLL_POSITION

  useEffect(() => {
    const resultIds = new Set(displayResults.map((result) => result.resultId))
    setResultScrollPositionsById((previous) => {
      let changed = false
      const next: Record<string, ResultScrollPosition> = {}
      for (const [resultId, position] of Object.entries(previous)) {
        if (!resultIds.has(resultId)) {
          changed = true
          continue
        }
        next[resultId] = position
      }
      return changed ? next : previous
    })
  }, [displayResults])

  useEffect(() => {
    ensureTab(tab.tabId, {
      sqlText: payload.initialSql,
      source: payload.source,
    })
  }, [ensureTab, payload.initialSql, payload.source, tab.tabId])

  useEffect(() => {
    autoRunRef.current = false
  }, [tab.tabId])

  useEffect(() => {
    if (typeof window === 'undefined') return
    const draftSql = window.localStorage.getItem(draftStorageKey)
    if (draftSql != null) {
      setSqlText(tab.tabId, draftSql)
    }
    setDraftLoadedTabId(tab.tabId)
  }, [draftStorageKey, setSqlText, tab.tabId])

  useEffect(() => {
    if (draftLoadedTabId !== tab.tabId || typeof window === 'undefined') return
    try {
      window.localStorage.setItem(draftStorageKey, tabState.sqlText)
    } catch {
      // ignore storage failures
    }
  }, [draftLoadedTabId, draftStorageKey, tab.tabId, tabState.sqlText])

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && tabState.executeStatus === 'running') {
        abortQueryEditorRun(tab.tabId)
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [tab.tabId, tabState.executeStatus])

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
  const hydratedOverride = tabState.override ?? (payload.contextOverride
    ? {
        connectionId: payload.contextOverride.connectionId,
        connectionName: connections.find((connection) => connection.id === payload.contextOverride?.connectionId)?.name
          ?? payload.connectionName
          ?? tab.connectionName
          ?? null,
        database: payload.contextOverride.database,
        schema: payload.contextOverride.schema,
      }
    : null)
  const effectiveContext = hydratedOverride
    ? {
        sessionId: resolvedExecutionContext.sessionId ?? tab.originSessionId ?? null,
        connectionId: hydratedOverride.connectionId,
        connectionName: hydratedOverride.connectionName ?? resolvedExecutionContext.connectionName,
        database: hydratedOverride.database ?? resolvedExecutionContext.database,
        schema: hydratedOverride.schema ?? resolvedExecutionContext.schema,
      }
    : {
        sessionId: resolvedExecutionContext.sessionId ?? tab.originSessionId ?? null,
        connectionId: resolvedExecutionContext.connectionId,
        connectionName: resolvedExecutionContext.connectionName,
        database: resolvedExecutionContext.database,
        schema: resolvedExecutionContext.schema,
      }
  const contextMode = hydratedOverride ? 'override' : 'session'

  useEffect(() => {
    const connectionId = effectiveContext.connectionId
    const hasResolvedName = connectionId
      ? connections.some((connection) => connection.id === connectionId && connection.name.trim().length > 0)
      : false
    const shouldHydrateSelectedConnection = Boolean(connectionId) && !hasResolvedName
    const shouldHydrateSelectableOptions = contextMode === 'session' && connections.length === 0
    if (!shouldHydrateSelectedConnection && !shouldHydrateSelectableOptions) return

    let cancelled = false
    void listConnections()
      .then((nextConnections) => {
        if (!cancelled) {
          setConnections(nextConnections)
        }
      })
      .catch(() => {
        // best-effort hydration for connection name display
      })

    return () => {
      cancelled = true
    }
  }, [connections, contextMode, effectiveContext.connectionId, setConnections])

  useEffect(() => {
    pendingConnectionTargetsRef.current.clear()
    setConnectionTargetsByConnectionId({})
  }, [tab.originSessionId])

  const fetchConnectionTargets = useCallback(async (connectionId: string | null | undefined) => {
    const normalizedConnectionId = connectionId?.trim()
    if (!normalizedConnectionId) return
    if (contextMode !== 'session' || !tab.originSessionId) return
    if (connectionTargetsByConnectionId[normalizedConnectionId]) return
    if (pendingConnectionTargetsRef.current.has(normalizedConnectionId)) return

    pendingConnectionTargetsRef.current.add(normalizedConnectionId)
    try {
      const targets = await sessionDataContext.listConnectionTargets(normalizedConnectionId)
      setConnectionTargetsByConnectionId((previous) => {
        if (previous[normalizedConnectionId]) return previous
        return {
          ...previous,
          [normalizedConnectionId]: { databases: targets.databases, schemas: targets.schemas },
        }
      })
    } catch {
      // Best-effort prefetch. Leave uncached so later UI interactions can retry.
    } finally {
      pendingConnectionTargetsRef.current.delete(normalizedConnectionId)
    }
  }, [
    connectionTargetsByConnectionId,
    contextMode,
    sessionDataContext,
    tab.originSessionId,
  ])

  useEffect(() => {
    if (contextMode !== 'session') return
    if (!tab.originSessionId) return

    const connectionIds = Array.from(new Set([
      effectiveContext.connectionId,
      ...connections.map((connection) => connection.id),
    ].filter((value): value is string => Boolean(value))))

    if (connectionIds.length === 0) return

    connectionIds.forEach((connectionId) => {
      void fetchConnectionTargets(connectionId)
    })
  }, [
    connections,
    contextMode,
    effectiveContext.connectionId,
    fetchConnectionTargets,
    tab.originSessionId,
  ])

  const canRun = Boolean(effectiveContext.connectionId) && tabState.sqlText.trim().length > 0
  const contextChipContext = toContextValue(
    effectiveContext.connectionId,
    effectiveContext.connectionName,
    effectiveContext.database,
    effectiveContext.schema,
  )
  const contextConnectionOptions = useMemo(
    () => connections.map((connection) => ({
      id: connection.id,
      name: connection.name,
      kind: connection.kind ?? null,
      databaseName: connection.databaseName ?? null,
    })),
    [connections],
  )

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
    const currentTabState = useSqlWorkbenchStore.getState().tabsById[tab.tabId]
    const selectedSqlText = currentTabState
      ? getSelectedSqlText(currentTabState.sqlText, currentTabState.selection)
      : null
    try {
      await runQueryEditorSql({
        tabId: tab.tabId,
        sessionId: tab.originSessionId ?? null,
        sqlOverride: selectedSqlText,
      })
    } catch (error) {
      if (isAbortError(error)) {
        return
      }
      throw error
    }
  }, [tab.originSessionId, tab.tabId])

  useEffect(() => {
    if (!payload.autoRun || autoRunRef.current || !effectiveContext.connectionId) return
    if (!tabState.sqlText.trim()) return
    autoRunRef.current = true
    void handleRun()
  }, [effectiveContext.connectionId, handleRun, payload.autoRun, tabState.sqlText])

  const handleFormat = useCallback(() => {
    formatQueryEditorSql(tab.tabId)
  }, [tab.tabId])

  const handleContextPin = useCallback((nextContext?: SqlContextValue) => {
    const contextToPin = nextContext ?? contextChipContext
    if (!contextToPin) return
    setQueryEditorContext({
      tabId: tab.tabId,
      connectionId: contextToPin.connectionId,
      database: contextToPin.database,
      schema: contextToPin.schema,
    })
  }, [contextChipContext, tab.tabId])

  const handleContextReset = useCallback(() => {
    setQueryEditorContext({
      tabId: tab.tabId,
      connectionId: resolvedExecutionContext.connectionId,
      database: resolvedExecutionContext.database,
      schema: resolvedExecutionContext.schema,
    })
  }, [
    resolvedExecutionContext.connectionId,
    resolvedExecutionContext.database,
    resolvedExecutionContext.schema,
    tab.tabId,
  ])

  const handleCursorChange = useCallback(
    (cursor: { line: number; column: number }) => {
      setCursor(tab.tabId, cursor.line, cursor.column)
    },
    [setCursor, tab.tabId],
  )

  const handleSelectionChange = useCallback(
    (selection: { startLine: number; startColumn: number; endLine: number; endColumn: number } | null) => {
      setSelection(tab.tabId, selection)
    },
    [setSelection, tab.tabId],
  )

  const handleSelectResult = useCallback((resultId: string) => {
    setActiveResult(tab.tabId, resultId)
  }, [setActiveResult, tab.tabId])

  const handleActiveResultScrollPositionChange = useCallback((position: ResultScrollPosition) => {
    if (!activeResultId) return
    setResultScrollPositionsById((previous) => {
      const current = previous[activeResultId]
      if (current?.scrollTop === position.scrollTop && current.scrollLeft === position.scrollLeft) {
        return previous
      }
      return {
        ...previous,
        [activeResultId]: position,
      }
    })
  }, [activeResultId])

  const handleCloseResult = useCallback((resultId: string) => {
    closeResult(tab.tabId, resultId)
  }, [closeResult, tab.tabId])

  const handleCloseOtherResults = useCallback((resultId: string) => {
    closeOtherResults(tab.tabId, resultId)
  }, [closeOtherResults, tab.tabId])

  const handleCloseAllResults = useCallback(() => {
    closeAllResults(tab.tabId)
  }, [closeAllResults, tab.tabId])

  const isPending = tabState.executeStatus === 'requires_confirmation'
    || tabState.executeStatus === 'confirmation_invalid'
    || tabState.executeStatus === 'confirming'

  const handleConfirmExecute = useCallback(async () => {
    if (!tabState.confirmation) return
    try {
      await confirmQueryEditorSql({
        tabId: tab.tabId,
        sessionId: tab.originSessionId ?? null,
        level: tabState.confirmation.level,
      })
    } catch (error) {
      if (isAbortError(error)) return
      throw error
    }
  }, [tab.tabId, tab.originSessionId, tabState.confirmation])

  const handleCancelConfirmation = useCallback(() => {
    cancelQueryEditorConfirmation(tab.tabId)
  }, [tab.tabId])

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
            onCancel={() => abortQueryEditorRun(tab.tabId)}
            onFormat={handleFormat}
            limit={tabState.limit}
            onLimitChange={(value) => setLimit(tab.tabId, value)}
            contextChip={
              <SqlContextChip
                mode={contextMode}
                context={contextChipContext}
                connections={contextConnectionOptions}
                connectionTargetsByConnectionId={connectionTargetsByConnectionId}
                onRequestConnectionTargets={fetchConnectionTargets}
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
                onSelectionChange={handleSelectionChange}
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
                    aria-label={t('stage.queryEditor.result.resizePanel')}
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
                    errorMessage={tabState.errorMessage}
                    activeScrollPosition={activeResultScrollPosition}
                    onActiveScrollPositionChange={handleActiveResultScrollPositionChange}
                  />
                </div>
              </div>
            </div>
          </section>
        ) : null}
      </div>
      <StageActivityRail sessionId={tab.originSessionId ?? null} />
      {isPending && tabState.confirmation ? (
        <AlertDialog open>
          <AlertDialogContent data-testid="sql-confirmation-dialog">
            <AlertDialogHeader>
              <AlertDialogTitle>{t('stage.queryEditor.confirmation.title')}</AlertDialogTitle>
            </AlertDialogHeader>
            <SqlConfirmationCard
              risk={{
                level: tabState.confirmation.level,
                reason: tabState.confirmation.reason,
                affectedObjects: tabState.confirmation.affectedObjects,
              }}
              sqlPreview={tabState.confirmation.sqlPreview}
              pending={tabState.executeStatus === 'confirming'}
              onCancel={handleCancelConfirmation}
              onExecute={() => void handleConfirmExecute()}
            />
            {tabState.confirmationInvalid && (
              <p data-testid="sql-confirmation-invalid-message" className="text-sm text-[var(--dt-status-danger)]">
                {tabState.confirmationInvalid.message}
              </p>
            )}
          </AlertDialogContent>
        </AlertDialog>
      ) : null}
    </div>
  )
}
