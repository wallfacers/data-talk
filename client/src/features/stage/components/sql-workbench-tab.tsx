import { useCallback, useEffect, useMemo, useRef } from 'react'
import { useShallow } from 'zustand/react/shallow'
import type { StageTab } from '@/stores/stage-store'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionDataContext } from '@/features/session/hooks/use-session-data-context'
import { SqlRiskError } from '@/services/api/sql'
import { useI18n } from '@/i18n/use-i18n'
import { resolveTabDataContext } from '@/features/stage/utils/resolve-tab-data-context'
import { normalizeQueryEditorPayload } from '../utils/normalize-query-editor-payload'
import { useSqlExecute } from '../hooks/use-sql-execute'
import { useSqlWorkbenchStore } from '../stores/sql-workbench-store'
import { SqlEditorHeader } from './sql-editor-header'
import { SqlMonacoEditor } from './sql-monaco-editor'
import { SqlResultTabs } from './sql-result-tabs'
import { SqlResultPanel } from './sql-result-panel'

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
}

export function SqlWorkbenchTab({ tab }: { tab: StageTab }) {
  const { t } = useI18n()
  const payload = normalizeQueryEditorPayload(tab.payload)
  const autoRunRef = useRef(false)
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
  } = useSqlWorkbenchStore(
    useShallow((state) => ({
      ensureTab: state.ensureTab,
      setSqlText: state.setSqlText,
      setActiveResult: state.setActiveResult,
      setRunning: state.setRunning,
      applyExecuteSuccess: state.applyExecuteSuccess,
      setRiskBlocked: state.setRiskBlocked,
      setError: state.setError,
    })),
  )

  const tabState = useSqlWorkbenchStore((state) => state.tabsById[tab.tabId]) ?? EMPTY_WORKBENCH_STATE
  const activeResult = useMemo(
    () => tabState.results.find((item) => item.resultId === tabState.activeResultId) ?? null,
    [tabState.activeResultId, tabState.results],
  )

  useEffect(() => {
    ensureTab(tab.tabId, {
      sqlText: payload.initialSql,
      source: payload.source,
    })
  }, [ensureTab, payload.initialSql, payload.source, tab.tabId])

  const effectiveContext = {
    sessionId: resolvedContext.sessionId ?? tab.originSessionId ?? null,
    connectionId: tabState.resolvedContext?.connectionId ?? resolvedContext.connectionId,
    connectionName: tabState.resolvedContext?.connectionName ?? resolvedContext.connectionName,
    database: tabState.resolvedContext?.database ?? resolvedContext.database,
    schema: tabState.resolvedContext?.schema ?? resolvedContext.schema,
  }
  const detailLabel = [effectiveContext.database, effectiveContext.schema].filter(Boolean).join(' / ') || null
  const canRun = Boolean(effectiveContext.connectionId) && tabState.sqlText.trim().length > 0

  const runSql = useCallback(async () => {
    if (!effectiveContext.connectionId || !tabState.sqlText.trim()) return

    setRunning(tab.tabId)
    try {
      const response = await execute(tabState.sqlText, effectiveContext.connectionId, tabState.source, {
        sessionId: effectiveContext.sessionId ?? undefined,
        database: effectiveContext.database,
        schema: effectiveContext.schema,
      })
      applyExecuteSuccess(tab.tabId, response)
    } catch (error) {
      if (error instanceof SqlRiskError) {
        setRiskBlocked(tab.tabId, error.risk)
        return
      }
      setError(tab.tabId, error instanceof Error ? error.message : t('stage.queryEditor.runFailed'))
    }
  }, [
    applyExecuteSuccess,
    effectiveContext.connectionId,
    effectiveContext.database,
    effectiveContext.schema,
    effectiveContext.sessionId,
    execute,
    setError,
    setRiskBlocked,
    setRunning,
    t,
    tab.tabId,
    tabState.source,
    tabState.sqlText,
  ])

  useEffect(() => {
    if (!payload.autoRun || autoRunRef.current || !effectiveContext.connectionId) return
    autoRunRef.current = true
    void runSql()
  }, [effectiveContext.connectionId, payload.autoRun, runSql])

  const entryLabel = (() => {
    switch (payload.entryMode) {
      case 'resource':
        return t('stage.queryEditor.entry.resource')
      case 'direct_sql':
        return t('chat.directQueryMode')
      case 'ai_generated':
        return t('stage.queryEditor.entry.aiGenerated')
      case 'manual':
      default:
        return t('stage.queryEditor.entry.manual')
    }
  })()

  return (
    <div data-testid="sql-workbench-tab" className="flex h-full w-full min-h-0 min-w-0 flex-1 flex-col bg-background">
      <section className="flex min-h-0 flex-[3] flex-col border-b border-border/40">
        <SqlEditorHeader
          entryLabel={entryLabel}
          connectionLabel={effectiveContext.connectionName ?? effectiveContext.connectionId ?? t('stage.queryEditor.connection.unselected')}
          detailLabel={detailLabel}
          contextNotice={tabState.contextNotice}
          runLabel={t('stage.queryEditor.run')}
          canRun={canRun}
          isRunning={tabState.executeStatus === 'running'}
          onRun={() => void runSql()}
        />
        <div className="min-h-0 flex-1 bg-background px-2 pb-2 pt-1">
          <SqlMonacoEditor
            value={tabState.sqlText}
            onChange={(next) => setSqlText(tab.tabId, next)}
            onRun={() => void runSql()}
          />
        </div>
      </section>

      <section className="flex min-h-0 flex-[2] flex-col">
        <SqlResultTabs
          results={tabState.results}
          activeResultId={tabState.activeResultId}
          onSelect={(resultId) => setActiveResult(tab.tabId, resultId)}
        />
        <div className="min-h-0 flex-1 overflow-hidden">
          <SqlResultPanel
            executeStatus={tabState.executeStatus}
            activeResult={activeResult}
            risk={tabState.risk}
            errorMessage={tabState.errorMessage}
          />
        </div>
      </section>
    </div>
  )
}
