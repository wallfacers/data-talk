import type { ReactNode } from 'react'
import { SquareIcon, CopyIcon, XIcon } from 'lucide-react'
import { useShallow } from 'zustand/react/shallow'
import { Button } from '@/components/ui/button'
import { useStageStore } from '@/stores/stage-store'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { useActiveArtifactTitle } from '../use-active-artifact-title'
import { openOrFocusStageToolTab } from '../utils/open-or-focus-stage-tool-tab'
import { StageResourceBrowser } from './stage-resource-browser'
import { StageSidebar } from './stage-sidebar'
import { StageTabBar } from './stage-tab-bar'
import { StageTabContent } from './stage-tab-content'
import { StageToolRow } from './stage-tool-row'
import { useI18n } from '@/i18n/use-i18n'

type Props = {
  sessionId?: string
  children: ReactNode
}

const EMPTY_EXPANDED_NODES: string[] = []

function deriveSelectionFromContext(
  context: ReturnType<typeof useSessionStore.getState>['dataContextBySession'] extends Map<string, infer T> ? T | null : null
) {
  if (!context?.connectionId) return null
  if (context.selectedLevel === 'schema' && context.schema) {
    return {
      kind: 'schema' as const,
      connectionId: context.connectionId,
      database: context.database ?? undefined,
      schema: context.schema,
    }
  }
  if (context.selectedLevel === 'database' && context.database) {
    return {
      kind: 'database' as const,
      connectionId: context.connectionId,
      database: context.database,
    }
  }
  return {
    kind: 'connection' as const,
    connectionId: context.connectionId,
  }
}

export function StageWindow({ sessionId, children }: Props) {
  const { t } = useI18n()
  const closeStage = useStageStore((s) => s.closeStage)
  const maximized = useStageStore((s) => (sessionId ? !!s.maximizedBySession.get(sessionId) : false))
  const toggleMaximized = useStageStore((s) => s.toggleMaximized)
  const sessionContext = useSessionStore((s) => (sessionId ? s.dataContextBySession.get(sessionId) ?? null : null))
  const sidebarSelection = useStageStore((s) => (sessionId ? s.sidebarSelectionBySession.get(sessionId) ?? null : null))
  const resourceExpanded = useStageStore((s) =>
    sessionId ? s.resourceTreeExpandedBySession.get(sessionId) ?? EMPTY_EXPANDED_NODES : EMPTY_EXPANDED_NODES
  )
  const sidebarCollapsed = useStageStore((s) => {
    if (!sessionId) return false
    return s.sidebarCollapsedBySession.get(sessionId) ?? false
  })
  const workspaceTabs = useStageStore((s) => s.workspaceTabs)
  const toggleSidebarCollapsed = useStageStore((s) => s.toggleSidebarCollapsed)
  const setSidebarSelection = useStageStore((s) => s.setSidebarSelection)
  const setResourceExpanded = useStageStore((s) => s.setResourceExpanded)
  const { Icon, label } = useActiveArtifactTitle(sessionId ?? '')
  const connections = useConnectionStore((s) => s.connections)
  const effectiveSelection = deriveSelectionFromContext(sessionContext) ?? sidebarSelection

  const tabs = useStageStore(
    useShallow((s) => {
      const sessionTabs = sessionId ? (s.tabsBySession.get(sessionId) ?? []) : []
      return [...s.workspaceTabs, ...sessionTabs]
    })
  )
  const activeTabId = useStageStore((s) => {
    if (!sessionId) return s.activeWorkspaceTabId
    return s.activeTabIdBySession.get(sessionId) ?? s.activeWorkspaceTabId ?? null
  })
  const focusTab = useStageStore((s) => s.focusTab)
  const closeTab = useStageStore((s) => s.closeTab)

  function handleClose() {
    if (sessionId) closeStage(sessionId)
  }
  function handleToggleMaximized() {
    if (sessionId) toggleMaximized(sessionId)
  }
  function handleToggleSidebarCollapsed() {
    if (sessionId) toggleSidebarCollapsed(sessionId)
  }
  function handleSelectionChange(selection: Parameters<typeof setSidebarSelection>[1]) {
    if (sessionId) setSidebarSelection(sessionId, selection)
  }
  function handleExpandedChange(nodeId: string, expanded: boolean) {
    if (!sessionId) return
    const next = expanded
      ? Array.from(new Set([...resourceExpanded, nodeId]))
      : resourceExpanded.filter((id) => id !== nodeId)
    setResourceExpanded(sessionId, next)
  }
  function focusWorkspaceTabInStage() {
    if (!sessionId) return
    const activeTabs = new Map(useStageStore.getState().activeTabIdBySession)
    activeTabs.set(sessionId, null)
    useStageStore.setState({ activeTabIdBySession: activeTabs })
  }
  function handleResourceToolAction(action: {
    kind: 'resource_tool'
    tool: 'sql' | 'er'
    connectionId: string
    database: string
    schema?: string | null
  }) {
    if (!sessionId) return
    if (action.schema) {
      setSidebarSelection(sessionId, {
        kind: 'schema',
        connectionId: action.connectionId,
        database: action.database,
        schema: action.schema,
      })
    } else {
      setSidebarSelection(sessionId, {
        kind: 'database',
        connectionId: action.connectionId,
        database: action.database,
      })
    }
    openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId,
      target: {
        kind: 'resource_tool',
        tool: action.tool,
        title: action.tool === 'sql' ? t('stage.toolRow.sql') : t('stage.toolRow.er'),
        connectionId: action.connectionId,
        database: action.database,
        schema: action.schema ?? null,
      },
    })
  }

  const handleCloseTab = (tabId: string) => closeTab(tabId)
  const handleCloseOthers = (tabId: string) =>
    tabs.filter((t) => t.tabId !== tabId).forEach((t) => closeTab(t.tabId))
  const handleCloseAll = () => tabs.forEach((t) => closeTab(t.tabId))
  const handleCloseLeft = (tabId: string) => {
    const idx = tabs.findIndex((t) => t.tabId === tabId)
    tabs.slice(0, idx).forEach((t) => closeTab(t.tabId))
  }
  const handleCloseRight = (tabId: string) => {
    const idx = tabs.findIndex((t) => t.tabId === tabId)
    tabs.slice(idx + 1).forEach((t) => closeTab(t.tabId))
  }

  return (
    <div className="flex h-full w-full flex-col overflow-hidden rounded-[20px] border border-border/50 bg-muted/5 shadow-[0_18px_42px_rgba(15,23,42,0.08)] ring-1 ring-white/45 transition-all duration-200">
      <div className="flex flex-col bg-transparent">
        <div className="group flex h-10 shrink-0 select-none items-center justify-between border-b border-border/40 bg-background/35">
          <div className="flex items-center gap-2 pl-3 pr-2">
            {Icon ? (
              <Icon className="size-4 text-primary" />
            ) : (
              <div className="size-2 rounded-full bg-primary" />
            )}
            <span className="text-xs font-medium tracking-wide text-foreground/80">
              {label || t('stage.workspace')}
            </span>
          </div>
          <div className="flex h-full items-center">
            <Button
              type="button"
              variant="ghost"
              className="h-full w-11 rounded-none text-muted-foreground hover:bg-accent hover:text-accent-foreground focus-visible:ring-0"
              aria-label={maximized ? t('stage.restore') : t('stage.maximize')}
              onClick={handleToggleMaximized}
            >
              {maximized ? (
                <CopyIcon className="size-4 rotate-180" strokeWidth={1.5} />
              ) : (
                <SquareIcon className="size-4" strokeWidth={1.5} />
              )}
            </Button>
            <Button
              type="button"
              variant="ghost"
              className="h-full w-11 rounded-none text-muted-foreground transition-colors hover:bg-[#e81123] hover:text-white focus-visible:ring-0"
              aria-label={t('stage.close')}
              onClick={handleClose}
            >
              <XIcon className="size-4" strokeWidth={1.5} />
            </Button>
          </div>
        </div>

      </div>

      <div className="flex min-h-0 flex-1 overflow-hidden bg-background/80">
        {sessionId && (
          <StageSidebar
            sessionId={sessionId}
            collapsed={sidebarCollapsed}
            onToggleCollapsed={handleToggleSidebarCollapsed}
            toolRowSlot={<StageToolRow sessionId={sessionId} collapsed={sidebarCollapsed} />}
            resourceBrowserSlot={(
              <StageResourceBrowser
                sessionId={sessionId}
                connections={connections}
                expandedNodeIds={resourceExpanded}
                selection={effectiveSelection}
                onSelectionChange={handleSelectionChange}
                onExpandedChange={handleExpandedChange}
                onToolAction={handleResourceToolAction}
              />
            )}
          />
        )}

        <section className="flex min-w-0 flex-1 flex-col overflow-hidden">
          {tabs.length > 0 && (
            <div
              onClick={(e) => {
                const target = e.target as HTMLElement
                const tabId = target.closest('[data-tab-id]')?.getAttribute('data-tab-id')
                if (!tabId) return
                focusTab(tabId)
                if (workspaceTabs.some((tab) => tab.tabId === tabId)) {
                  focusWorkspaceTabInStage()
                }
              }}
            >
              <StageTabBar
                tabs={tabs.map((t) => ({ tabId: t.tabId, title: t.title, type: t.type }))}
                activeId={activeTabId ?? undefined}
                onClose={handleCloseTab}
                onCloseOthers={handleCloseOthers}
                onCloseAll={handleCloseAll}
                onCloseLeft={handleCloseLeft}
                onCloseRight={handleCloseRight}
              />
            </div>
          )}

          <div
            data-testid="stage-workspace-pane"
            className="flex min-h-0 flex-1 flex-col overflow-hidden bg-background/95"
          >
            {activeTabId ? (
              <div className="flex min-h-0 flex-1 overflow-hidden">
                <StageTabContent />
              </div>
            ) : (
              <div className="flex min-h-0 flex-1 overflow-auto">
                {children}
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  )
}
