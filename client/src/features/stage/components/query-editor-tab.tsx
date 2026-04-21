import { useRef, useEffect, useCallback, useState, type MutableRefObject } from 'react'
import { EditorView, basicSetup } from 'codemirror'
import { sql } from '@codemirror/lang-sql'
import { keymap, type ViewUpdate } from '@codemirror/view'
import { Prec } from '@codemirror/state'
import { useShallow } from 'zustand/react/shallow'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { StageTab } from '@/stores/stage-store'
import { useStageStore } from '@/stores/stage-store'
import { useConnectionStore } from '@/features/connection/store'
import { useSqlExecute } from '../hooks/use-sql-execute'
import { useChannel } from '@/services/channel/use-channel'
import { useSessionDataContext } from '@/features/session/hooks/use-session-data-context'
import { resolveTabDataContext } from '@/features/stage/utils/resolve-tab-data-context'
import { useI18n } from '@/i18n/use-i18n'
import {
  isNormalizedQueryEditorPayload,
  normalizeQueryEditorPayload,
  type NormalizedQueryEditorPayload,
} from '../utils/normalize-query-editor-payload'
import { QueryEditorToolbar } from './query-editor-toolbar'
import { QueryEditorResultPanel } from './query-editor-result-panel'
import { QueryEditorInspector } from './query-editor-inspector'

function SqlEditor({
  initialValue,
  editorRef,
  onRun,
  onChange,
}: {
  initialValue: string
  editorRef: MutableRefObject<EditorView | undefined>
  onRun: () => void
  onChange: (value: string) => void
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const onRunRef = useRef(onRun)
  const onChangeRef = useRef(onChange)
  onRunRef.current = onRun
  onChangeRef.current = onChange

  useEffect(() => {
    if (!containerRef.current) return
    const viewWithListener = EditorView as typeof EditorView & {
      updateListener?: {
        of: (listener: (update: ViewUpdate) => void) => unknown
      }
    }
    editorRef.current = new EditorView({
      doc: initialValue,
      extensions: [
        basicSetup,
        sql(),
        Prec.high(
          keymap.of([{
            key: 'Ctrl-Enter',
            mac: 'Cmd-Enter',
            run: () => { onRunRef.current(); return true },
          }])
        ),
        ...(viewWithListener.updateListener ? [viewWithListener.updateListener.of((update) => {
          if (!update.docChanged) return
          onChangeRef.current(update.state.doc.toString())
        })] : []),
      ],
      parent: containerRef.current,
    })
    return () => { editorRef.current?.destroy(); editorRef.current = undefined }
  }, []) // intentional: mount-only, refs keep values fresh

  return (
    <div
      ref={containerRef}
      className="h-full overflow-auto [&_.cm-editor]:h-full [&_.cm-editor]:outline-none [&_.cm-scroller]:font-mono [&_.cm-scroller]:text-sm"
    />
  )
}

function QueryEditorTabWorkbench({ tab }: { tab: StageTab }) {
  const { t } = useI18n()
  const payload = normalizeQueryEditorPayload(tab.payload)
  const editorRef = useRef<EditorView | undefined>(undefined)
  const [hasAutoRunFired, setHasAutoRunFired] = useState(false)
  const { activeConnectionId, connections } = useConnectionStore(
    useShallow((s) => ({
      activeConnectionId: s.activeConnectionId,
      connections: s.connections,
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
      connectionNameLookup: (connectionId) => connections.find((connection) => connection.id === connectionId)?.name ?? null,
    },
  )
  const { execute, result, risk, status, reset } = useSqlExecute()
  const { sendMessage } = useChannel()
  const effectiveContext = {
    sessionId: resolvedContext.sessionId ?? tab.originSessionId ?? null,
    connectionId: result?.resolvedContext?.connectionId ?? resolvedContext.connectionId,
    connectionName: result?.resolvedContext?.connectionName ?? resolvedContext.connectionName,
    database: result?.resolvedContext?.database ?? resolvedContext.database,
    schema: result?.resolvedContext?.schema ?? resolvedContext.schema,
  }
  const effectiveDetails = [effectiveContext.database, effectiveContext.schema].filter(Boolean).join(' / ') || null
  const displayedResult = status === 'success' && result ? result : payload.initialResult
  const latestRun = status === 'success' && result
    ? {
        columns: result.columns,
        rowCount: result.rowCount,
        executionMs: result.executionMs,
        truncated: result.truncated,
      }
    : payload.lastRun
  const contextNotice = result?.contextNotice ?? payload.contextNotice
  const hasConnection = Boolean(effectiveContext.connectionId)

  const updatePayload = useCallback((patch: Partial<NormalizedQueryEditorPayload>) => {
    useStageStore.getState().updateTabPayload(tab.tabId, (prev) => ({
      ...normalizeQueryEditorPayload(prev),
      ...patch,
    }))
  }, [tab.tabId])

  useEffect(() => {
    if (isNormalizedQueryEditorPayload(tab.payload)) return
    updatePayload({})
  }, [tab.payload, updatePayload])

  const persistRunResult = useCallback((sqlText: string, nextResult: NonNullable<typeof result>) => {
    updatePayload({
      initialSql: sqlText,
      autoRun: false,
      initialResult: {
        columns: nextResult.columns,
        rows: nextResult.rows,
        rowCount: nextResult.rowCount,
        executionMs: nextResult.executionMs,
        truncated: nextResult.truncated,
      },
      lastRun: {
        columns: nextResult.columns,
        rowCount: nextResult.rowCount,
        executionMs: nextResult.executionMs,
        truncated: nextResult.truncated,
      },
      contextNotice: nextResult.contextNotice ?? null,
      connectionId: nextResult.resolvedContext?.connectionId ?? effectiveContext.connectionId,
      connectionName: nextResult.resolvedContext?.connectionName ?? effectiveContext.connectionName,
      database: nextResult.resolvedContext?.database ?? effectiveContext.database,
      schema: nextResult.resolvedContext?.schema ?? effectiveContext.schema,
    })
  }, [effectiveContext.connectionId, effectiveContext.connectionName, effectiveContext.database, effectiveContext.schema, updatePayload])

  const runSql = useCallback((sqlText: string) => {
    if (!sqlText.trim() || !effectiveContext.connectionId) return
    updatePayload({ initialSql: sqlText, autoRun: false })
    void execute(sqlText, effectiveContext.connectionId, payload.source, {
      sessionId: effectiveContext.sessionId ?? undefined,
      database: effectiveContext.database,
      schema: effectiveContext.schema,
    })
      .then((nextResult) => {
        if (!nextResult) return
        persistRunResult(sqlText, nextResult)
      })
      .catch(() => undefined)
  }, [
    effectiveContext.connectionId,
    effectiveContext.database,
    effectiveContext.schema,
    effectiveContext.sessionId,
    execute,
    payload.source,
    persistRunResult,
    updatePayload,
  ])

  useEffect(() => {
    if (!payload.autoRun || hasAutoRunFired || !effectiveContext.connectionId) return
    setHasAutoRunFired(true)
    runSql(payload.initialSql)
  }, [effectiveContext.connectionId, hasAutoRunFired, payload.autoRun, payload.initialSql, runSql])

  const handleRun = useCallback(() => {
    runSql(editorRef.current?.state.doc.toString() ?? payload.initialSql)
  }, [payload.initialSql, runSql])

  const handleSendToAi = useCallback(() => {
    const sqlText = editorRef.current?.state.doc.toString() ?? ''
    sendMessage([{
      type: 'text',
      text: `请帮我检查这段 SQL 是否安全，如果可以执行请帮我执行：\n\`\`\`sql\n${sqlText}\n\`\`\``,
    }])
    reset()
  }, [sendMessage, reset])

  const handleEditorChange = useCallback((nextSql: string) => {
    updatePayload({ initialSql: nextSql })
  }, [updatePayload])

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

  const inspectorDescription = (() => {
    switch (payload.entryMode) {
      case 'resource':
        return t('stage.queryEditor.inspectorDescription.resource')
      case 'direct_sql':
        return t('stage.queryEditor.inspectorDescription.directSql')
      case 'ai_generated':
        return t('stage.queryEditor.inspectorDescription.aiGenerated')
      case 'manual':
      default:
        return t('stage.queryEditor.inspectorDescription.manual')
    }
  })()

  const connectionLabel = effectiveContext.connectionName ?? effectiveContext.connectionId ?? t('stage.queryEditor.connection.unselected')
  const resultSummary = displayedResult
    ? displayedResult.truncated
      ? t('stage.queryEditor.summary.truncated', { count: displayedResult.rowCount, executionMs: displayedResult.executionMs })
      : t('stage.queryEditor.summary.rows', { count: displayedResult.rowCount, executionMs: displayedResult.executionMs })
    : null
  const lastRunLabel = latestRun
    ? (latestRun.truncated
        ? t('stage.queryEditor.summary.truncated', { count: latestRun.rowCount, executionMs: latestRun.executionMs })
        : t('stage.queryEditor.summary.rows', { count: latestRun.rowCount, executionMs: latestRun.executionMs }))
    : t('stage.queryEditor.lastRun.none')

  return (
    <div className="grid h-full min-h-0 gap-4 p-4 lg:grid-cols-[minmax(0,1fr)_320px]">
      <Card className="min-h-0 gap-0 border-border/60 py-0">
        <QueryEditorToolbar
          entryLabel={entryLabel}
          connectionLabel={connectionLabel}
          detailLabel={effectiveDetails}
          contextNotice={contextNotice}
          runLabel={t('stage.queryEditor.run')}
          isRunning={status === 'running'}
          showRunButton={hasConnection}
          onRun={handleRun}
        />
        <div className="flex min-h-0 flex-1 flex-col">
          <div className="min-h-[320px] flex-1 overflow-hidden">
            {hasConnection ? (
              <SqlEditor
                initialValue={payload.initialSql}
                editorRef={editorRef}
                onRun={handleRun}
                onChange={handleEditorChange}
              />
            ) : (
              <div className="flex h-full items-center justify-center p-4">
                <Card className="max-w-md border-border/60 bg-muted/10">
                  <CardHeader>
                    <CardTitle className="text-base">{t('stage.queryEditor.noConnectionTitle')}</CardTitle>
                    <CardDescription>{t('stage.queryEditor.noConnectionDescription')}</CardDescription>
                  </CardHeader>
                  <CardContent className="text-xs text-muted-foreground">
                    {t('stage.queryEditor.connection.unselected')}
                  </CardContent>
                </Card>
              </div>
            )}
          </div>
          <div className="min-h-[260px] border-t border-border/60">
            <QueryEditorResultPanel
              status={status}
              result={displayedResult}
              title={t('stage.queryEditor.results')}
              summary={resultSummary}
              idleLabel={t('stage.queryEditor.runToSeeResults')}
              runningLabel={t('stage.queryEditor.running')}
              errorLabel={t('stage.queryEditor.runFailed')}
              riskTitle={t('stage.queryEditor.highRisk')}
              riskReason={risk?.riskReason ?? null}
              cancelLabel={t('common.cancel')}
              sendToAiLabel={t('stage.queryEditor.sendToAi')}
              onCancel={reset}
              onSendToAi={handleSendToAi}
            />
          </div>
        </div>
      </Card>
      <QueryEditorInspector
        title={t('stage.queryEditor.inspector')}
        description={inspectorDescription}
        connectionLabel={connectionLabel}
        databaseLabel={effectiveContext.database ?? '-'}
        schemaLabel={effectiveContext.schema ?? '-'}
        lastRunLabel={lastRunLabel}
        connectionFieldLabel={t('stage.queryEditor.field.connection')}
        databaseFieldLabel={t('stage.queryEditor.field.database')}
        schemaFieldLabel={t('stage.queryEditor.field.schema')}
        lastRunFieldLabel={t('stage.queryEditor.field.lastRun')}
      />
    </div>
  )
}

export function QueryEditorTab({ tab }: { tab: StageTab }) {
  return <QueryEditorTabWorkbench key={tab.tabId} tab={tab} />
}
