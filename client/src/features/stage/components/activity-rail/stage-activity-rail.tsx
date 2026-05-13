import { HistoryIcon, ListTreeIcon, SearchCodeIcon } from 'lucide-react'
import { useShallow } from 'zustand/react/shallow'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useI18n } from '@/i18n/use-i18n'
import { cn } from '@/lib/utils'
import { useStageStore, type RailPanel } from '@/stores/stage-store'
import { parseSqlOutline } from '../../utils/parse-sql-outline'
import { useSqlWorkbenchStore } from '../../stores/sql-workbench-store'
import { RailPanelShell } from './rail-panel-shell'
import { HistoryPanel } from './history-panel'
import { OutlinePanel } from './outline-panel'
import { DiagnosticsPanel } from './diagnostics-panel'

type Props = {
  className?: string
}

export function StageActivityRail({ className }: Props) {
  const { t } = useI18n()
  const activePanel = useStageStore((s) => s.activeRailPanel)
  const toggleRailPanel = useStageStore((s) => s.toggleRailPanel)
  const setActiveRailPanel = useStageStore((s) => s.setActiveRailPanel)

  const activeTab = useStageStore((state) => {
    const activeTabId = state.activeTabId

    if (!activeTabId) return null

    return state.tabs.find((tab) => tab.tabId === activeTabId) ?? null
  })

  const activeTabState = useSqlWorkbenchStore((state) => (activeTab ? state.tabsById[activeTab.tabId] ?? null : null))
  const { setSqlText, clearHistory, setCursor } = useSqlWorkbenchStore(
    useShallow((state) => ({
      setSqlText: state.setSqlText,
      clearHistory: state.clearHistory,
      setCursor: state.setCursor,
    })),
  )

  const panelMeta: Array<{ panel: RailPanel; label: string; Icon: typeof HistoryIcon }> = [
    { panel: 'history', label: t('stage.activityRail.history.title'), Icon: HistoryIcon },
    { panel: 'outline', label: t('stage.activityRail.outline.title'), Icon: ListTreeIcon },
    { panel: 'diagnostics', label: t('stage.activityRail.diagnostics.title'), Icon: SearchCodeIcon },
  ]

  const panelTitles: Record<RailPanel, string> = {
    history: t('stage.activityRail.history.title'),
    outline: t('stage.activityRail.outline.title'),
    diagnostics: t('stage.activityRail.diagnostics.title'),
  }

  const historyEntries = activeTab?.type === 'query_editor' ? activeTabState?.history ?? [] : []
  const outlineStatements = parseSqlOutline(activeTabState?.sqlText ?? '')

  function handlePanelClick(panel: RailPanel) {
    toggleRailPanel(panel)
  }

  function handleClosePanel() {
    setActiveRailPanel(null)
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
      className={cn('flex h-full shrink-0 items-stretch border-l border-border/40 bg-bg-soft', className)}
    >
      {activePanel ? (
        <RailPanelShell title={panelTitles[activePanel]} onClose={handleClosePanel}>
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

      <div className="flex w-7 shrink-0 flex-col items-stretch border-l border-border/40 py-1">
        {panelMeta.map(({ panel, label, Icon }) => {
          const isActive = activePanel === panel
          return (
            <Tooltip key={panel}>
              <TooltipTrigger
                render={
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    aria-label={label}
                    aria-pressed={isActive}
                    data-state={isActive ? 'active' : 'inactive'}
                    onClick={() => handlePanelClick(panel)}
                    className={cn(
                      'relative w-7 rounded-none border-0 text-muted-foreground transition-colors',
                      isActive
                        ? 'bg-bg-canvas text-accent-primary before:absolute before:inset-y-0 before:left-0 before:w-[2px] before:bg-accent-primary'
                        : 'hover:bg-muted hover:text-foreground',
                    )}
                  >
                    <Icon className="size-3.5" />
                  </Button>
                }
              />
              <TooltipContent>{label}</TooltipContent>
            </Tooltip>
          )
        })}
      </div>
    </div>
  )
}
