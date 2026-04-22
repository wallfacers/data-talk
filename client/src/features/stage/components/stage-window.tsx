import { CopyIcon, SquareIcon, XIcon } from 'lucide-react'
import { useShallow } from 'zustand/react/shallow'
import { Button } from '@/components/ui/button'
import { useStageStore } from '@/stores/stage-store'
import { useActiveArtifactTitle } from '../use-active-artifact-title'
import { openOrFocusStageToolTab } from '../utils/open-or-focus-stage-tool-tab'
import { StageTabBar } from './stage-tab-bar'
import { StageTabContent } from './stage-tab-content'
import { StageUIObjectRegistry } from './stage-ui-object-registry'
import { StageWorkbenchEmptyState } from './stage-workbench-empty-state'
import { useI18n } from '@/i18n/use-i18n'

type Props = {
  sessionId?: string
}

export function StageWindow({ sessionId }: Props) {
  const { t } = useI18n()
  const closeStage = useStageStore((s) => s.closeStage)
  const maximized = useStageStore((s) => (sessionId ? !!s.maximizedBySession.get(sessionId) : false))
  const toggleMaximized = useStageStore((s) => s.toggleMaximized)
  const workspaceTabs = useStageStore((s) => s.workspaceTabs)
  const { Icon, label } = useActiveArtifactTitle(sessionId ?? '')

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

  function focusWorkspaceTabInStage() {
    if (!sessionId) return
    const activeTabs = new Map(useStageStore.getState().activeTabIdBySession)
    activeTabs.set(sessionId, null)
    useStageStore.setState({ activeTabIdBySession: activeTabs })
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

  function handleOpenSqlEditor() {
    openOrFocusStageToolTab({
      getState: useStageStore.getState,
      sessionId: null,
      target: {
        kind: 'global_tool',
        tool: 'sql',
        title: t('stage.toolRow.sql'),
      },
    })
  }

  return (
    <div className="flex h-full w-full flex-col overflow-hidden rounded-[20px] border border-border/70 bg-muted/20 shadow-[0_18px_42px_rgba(15,23,42,0.10)] ring-1 ring-black/5 transition-all duration-200">
      <StageUIObjectRegistry sessionId={sessionId ?? null} tabs={tabs} />

      <div className="flex flex-col bg-transparent">
        <div className="group flex h-10 shrink-0 select-none items-center justify-between border-b border-border/55 bg-background/55">
          <div className="flex items-center gap-2 pl-3 pr-2">
            {Icon ? <Icon className="size-4 text-primary" /> : <div className="size-2 rounded-full bg-primary" />}
            <span className="text-xs font-medium tracking-wide text-foreground/80">{label || t('stage.workspace')}</span>
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

      <div className="flex min-h-0 flex-1 overflow-hidden bg-background/88">
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

          <div data-testid="stage-workspace-pane" className="flex min-h-0 flex-1 flex-col overflow-hidden bg-background">
            {activeTabId ? (
              <div className="flex min-h-0 flex-1 overflow-hidden">
                <StageTabContent />
              </div>
            ) : (
              <div className="flex min-h-0 flex-1 overflow-hidden">
                <StageWorkbenchEmptyState
                  onOpenSqlEditor={handleOpenSqlEditor}
                />
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  )
}
