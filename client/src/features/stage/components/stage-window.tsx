import type { ReactNode } from 'react'
import { SquareIcon, CopyIcon, XIcon } from 'lucide-react'
import { useShallow } from 'zustand/react/shallow'
import { Button } from '@/components/ui/button'
import { useStageStore } from '@/stores/stage-store'
import { useActiveArtifactTitle } from '../use-active-artifact-title'
import { StageDock } from './stage-dock'
import { StageTabBar } from './stage-tab-bar'
import { StageTabContent } from './stage-tab-content'
import { useI18n } from '@/i18n/use-i18n'

type Props = {
  sessionId?: string
  children: ReactNode
}

export function StageWindow({ sessionId, children }: Props) {
  const { t } = useI18n()
  const closeStage = useStageStore((s) => s.closeStage)
  const maximized = useStageStore((s) => (sessionId ? !!s.maximizedBySession.get(sessionId) : false))
  const toggleMaximized = useStageStore((s) => s.toggleMaximized)
  const { Icon, label } = useActiveArtifactTitle(sessionId ?? '')

  const tabs = useStageStore(
    useShallow((s) => {
      const sessionTabs = sessionId ? (s.tabsBySession.get(sessionId) ?? []) : []
      return [...s.workspaceTabs, ...sessionTabs]
    })
  )
  const activeTabId = useStageStore((s) => {
    if (!sessionId) return s.activeWorkspaceTabId
    return s.activeTabIdBySession.get(sessionId) ?? null
  })
  const focusTab = useStageStore((s) => s.focusTab)
  const closeTab = useStageStore((s) => s.closeTab)

  function handleClose() {
    if (sessionId) closeStage(sessionId)
  }
  function handleToggleMaximized() {
    if (sessionId) toggleMaximized(sessionId)
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
    <div className="flex h-full w-full flex-col overflow-hidden rounded-xl bg-background shadow-[0_16px_40px_rgb(0,0,0,0.12)] ring-1 ring-border/50 transition-all duration-200">
      <div className="flex flex-col bg-muted/30">
        {/* 标题栏 */}
        <div className="group flex h-10 shrink-0 select-none items-center justify-between">
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

        {/* 标签栏：仅在有 tab 时显示 */}
        {tabs.length > 0 && (
          <div
            onClick={(e) => {
              const target = e.target as HTMLElement
              const tabId = target.closest('[data-tab-id]')?.getAttribute('data-tab-id')
              if (tabId) focusTab(tabId)
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
      </div>

      {/* 内容区域 */}
      <div className="relative flex flex-1 min-h-0 flex-col overflow-hidden bg-background">
        <div className="mx-3 mt-4 mb-[110px] flex flex-1 min-h-0 flex-col overflow-hidden rounded-xl border border-border/60 bg-card shadow-[0_4px_20px_rgb(0,0,0,0.05),inset_0_1px_3px_rgb(0,0,0,0.02)] relative z-0">
          <div className="relative flex-1 overflow-auto">
            {activeTabId ? <StageTabContent /> : children}
          </div>
        </div>
        <div className="absolute bottom-8 left-1/2 z-10 -translate-x-1/2">
          <StageDock sessionId={sessionId} />
        </div>
      </div>
    </div>
  )
}
