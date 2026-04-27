import { DatabaseIcon, HistoryIcon, ListTreeIcon, SearchCodeIcon } from 'lucide-react'
import { useShallow } from 'zustand/react/shallow'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { cn } from '@/lib/utils'
import { useStageStore, type RailPanel } from '@/stores/stage-store'
import { normalizeQueryEditorPayload } from '../../utils/normalize-query-editor-payload'
import { parseSqlOutline } from '../../utils/parse-sql-outline'
import { useSqlWorkbenchStore } from '../../stores/sql-workbench-store'
import { RailPanelShell } from './rail-panel-shell'
import { HistoryPanel } from './history-panel'
import { OutlinePanel } from './outline-panel'
import { SchemaPanel, type SchemaPanelItem, type SchemaPanelContext } from './schema-panel'
import { DiagnosticsPanel } from './diagnostics-panel'

type Props = {
  sessionId?: string | null
  className?: string
}

function buildSchemaItems(
  context: SchemaPanelContext | null,
  labels: { table: string; column: string },
): SchemaPanelItem[] {
  if (!context) return []

  const items: SchemaPanelItem[] = [
    {
      id: 'schema-connection',
      kind: 'connection',
      label: context.connectionName ?? context.connectionId,
      context: {
        connectionId: context.connectionId,
        connectionName: context.connectionName,
        database: null,
        schema: null,
      },
    },
  ]

  if (context.database) {
    items.push({
      id: 'schema-database',
      kind: 'database',
      label: context.database,
      context: {
        connectionId: context.connectionId,
        connectionName: context.connectionName,
        database: context.database,
        schema: null,
      },
    })
  }

  if (context.schema) {
    items.push({
      id: 'schema-schema',
      kind: 'schema',
      label: context.schema,
      context: {
        connectionId: context.connectionId,
        connectionName: context.connectionName,
        database: context.database,
        schema: context.schema,
      },
    })
  }

  items.push(
    { id: 'schema-table', kind: 'table', label: labels.table, insertText: labels.table },
    { id: 'schema-column', kind: 'column', label: labels.column, insertText: labels.column },
  )

  return items
}

export function StageActivityRail({ sessionId, className }: Props) {
  const { t } = useI18n()
  const railScopeId = sessionId ?? 'workspace'
  const activePanel = useStageStore((s) => s.activeRailPanelBySession.get(railScopeId) ?? null)
  const toggleRailPanel = useStageStore((s) => s.toggleRailPanel)
  const setActiveRailPanel = useStageStore((s) => s.setActiveRailPanel)

  const activeTab = useStageStore((state) => {
    const activeTabId = sessionId
      ? state.activeTabIdBySession.get(sessionId) ?? state.activeWorkspaceTabId
      : state.activeWorkspaceTabId

    if (!activeTabId) return null

    return (
      state.workspaceTabs.find((tab) => tab.tabId === activeTabId)
      ?? (sessionId ? state.tabsBySession.get(sessionId)?.find((tab) => tab.tabId === activeTabId) : undefined)
      ?? null
    )
  })

  const activeTabState = useSqlWorkbenchStore((state) => (activeTab ? state.tabsById[activeTab.tabId] ?? null : null))
  const { setSqlText, setTabContext, clearHistory, setCursor } = useSqlWorkbenchStore(
    useShallow((state) => ({
      setSqlText: state.setSqlText,
      setTabContext: state.setTabContext,
      clearHistory: state.clearHistory,
      setCursor: state.setCursor,
    })),
  )

  const payloadContext = activeTab?.type === 'query_editor' ? normalizeQueryEditorPayload(activeTab.payload) : null
  const schemaContext: SchemaPanelContext | null = (() => {
    if (!activeTab || activeTab.type !== 'query_editor') return null

    const connectionId =
      activeTabState?.resolvedContext?.connectionId
      ?? payloadContext?.connectionId
      ?? activeTab.connectionId
      ?? null

    if (!connectionId) return null

    return {
      connectionId,
      connectionName:
        activeTabState?.resolvedContext?.connectionName
        ?? payloadContext?.connectionName
        ?? activeTab.connectionName
        ?? null,
      database:
        activeTabState?.resolvedContext?.database
        ?? payloadContext?.database
        ?? activeTab.database
        ?? null,
      schema:
        activeTabState?.resolvedContext?.schema
        ?? payloadContext?.schema
        ?? activeTab.schema
        ?? null,
    }
  })()

  const panelMeta: Array<{ panel: RailPanel; label: string; Icon: typeof DatabaseIcon }> = [
    { panel: 'schema', label: t('stage.activityRail.schema.title'), Icon: DatabaseIcon },
    { panel: 'history', label: t('stage.activityRail.history.title'), Icon: HistoryIcon },
    { panel: 'outline', label: t('stage.activityRail.outline.title'), Icon: ListTreeIcon },
    { panel: 'diagnostics', label: t('stage.activityRail.diagnostics.title'), Icon: SearchCodeIcon },
  ]

  const panelTitles: Record<RailPanel, string> = {
    schema: t('stage.activityRail.schema.title'),
    history: t('stage.activityRail.history.title'),
    outline: t('stage.activityRail.outline.title'),
    diagnostics: t('stage.activityRail.diagnostics.title'),
  }

  const schemaItems = buildSchemaItems(schemaContext, {
    table: t('stage.activityRail.schema.placeholder.table'),
    column: t('stage.activityRail.schema.placeholder.column'),
  })
  const historyEntries = activeTab?.type === 'query_editor' ? activeTabState?.history ?? [] : []
  const outlineStatements = parseSqlOutline(activeTabState?.sqlText ?? '')

  function handlePanelClick(panel: RailPanel) {
    toggleRailPanel(railScopeId, panel)
  }

  function handleClosePanel() {
    setActiveRailPanel(railScopeId, null)
  }

  function handleSetSchemaContext(context: SchemaPanelContext) {
    if (!activeTab || activeTab.type !== 'query_editor') return
    setTabContext(activeTab.tabId, {
      ...context,
      source: 'user_schema_panel',
    })
  }

  function handleAppendSql(sql: string) {
    if (!activeTab || activeTab.type !== 'query_editor' || !activeTabState) return
    const currentSqlText = activeTabState.sqlText.trimEnd()
    setSqlText(activeTab.tabId, currentSqlText ? `${currentSqlText}\n${sql}` : sql)
  }

  function handleClearHistory() {
    if (!activeTab || activeTab.type !== 'query_editor') return
    clearHistory(activeTab.tabId)
  }

  function handleJumpToLine(line: number) {
    if (!activeTab || activeTab.type !== 'query_editor') return
    setCursor(activeTab.tabId, line, 1)
  }

  return (
    <div
      data-testid="stage-activity-rail"
      className={cn('flex h-full shrink-0 items-stretch border-l border-border/40 bg-muted/10', className)}
    >
      {activePanel ? (
        <RailPanelShell title={panelTitles[activePanel]} onClose={handleClosePanel}>
          {activePanel === 'schema' ? (
            <SchemaPanel
              items={schemaItems}
              onSetTabContext={handleSetSchemaContext}
              onInsertText={handleAppendSql}
            />
          ) : null}
          {activePanel === 'history' ? (
            <HistoryPanel entries={historyEntries} onAppendSql={handleAppendSql} onClear={handleClearHistory} />
          ) : null}
          {activePanel === 'outline' ? (
            <OutlinePanel statements={outlineStatements} onJumpToLine={handleJumpToLine} />
          ) : null}
          {activePanel === 'diagnostics' ? (
            <DiagnosticsPanel entry={null} onOpenInWorkbench={() => {}} />
          ) : null}
        </RailPanelShell>
      ) : null}

      <div className="flex w-7 shrink-0 flex-col items-stretch border-l border-border/40 bg-background/70 py-1">
        {panelMeta.map(({ panel, label, Icon }) => {
          const isActive = activePanel === panel
          return (
            <Button
              key={panel}
              type="button"
              variant="ghost"
              size="icon-sm"
              aria-label={label}
              aria-pressed={isActive}
              data-state={isActive ? 'active' : 'inactive'}
              onClick={() => handlePanelClick(panel)}
              className={cn(
                'w-7 rounded-none border-0 text-muted-foreground transition-colors',
                isActive ? 'bg-background text-foreground' : 'hover:bg-muted hover:text-foreground',
              )}
            >
              <Icon className="size-3.5" />
            </Button>
          )
        })}
      </div>
    </div>
  )
}
