import { useEffect, useRef, useState } from 'react'
import { CopyIcon, SquareIcon, XIcon } from 'lucide-react'
import { useShallow } from 'zustand/react/shallow'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useStageStore, type StageTab } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useActiveArtifactTitle } from '../use-active-artifact-title'
import { StageTabBar } from './stage-tab-bar'
import { StageTabContent } from './stage-tab-content'
import { StageUIObjectRegistry } from './stage-ui-object-registry'
import { StageWorkbenchEmptyState } from './stage-workbench-empty-state'
import { StageLeftRail } from './left-rail/stage-left-rail'
import { useI18n } from '@/i18n/use-i18n'

export function StageWindow() {
  const { t } = useI18n()
  const [showStartPage, setShowStartPage] = useState(false)
  const closeStage = useStageStore((s) => s.closeStage)
  const maximized = useStageStore((s) => s.maximized)
  const toggleMaximized = useStageStore((s) => s.toggleMaximized)
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const { Icon, label } = useActiveArtifactTitle(activeSessionId)

  const tabs = useStageStore(useShallow((s) => s.tabs))
  const openTabsOrdered = useStageStore(
    useShallow((s) => s.openTabIdsOrdered.map((id) => s.tabs.find((t) => t.tabId === id)).filter(Boolean) as StageTab[]),
  )
  const activeTabId = useStageStore((s) => s.activeTabId)
  const focusTab = useStageStore((s) => s.focusTab)
  const leftRailWidth = useStageStore((s) => s.leftRailWidth)
  const leftRailCollapsed = useStageStore((s) => s.leftRailCollapsed)
  const setLeftRailWidth = useStageStore((s) => s.setLeftRailWidth)

  useEffect(() => {
    if (openTabsOrdered.length > 0) return
    setShowStartPage(false)
  }, [openTabsOrdered.length])

  useEffect(() => {
    if (activeTabId) setShowStartPage(false)
  }, [activeTabId])

  function handleClose() {
    closeStage()
  }

  function handleToggleMaximized() {
    toggleMaximized()
  }

  const handleCloseTab = (tabId: string) => useStageStore.getState().detachFromWorkset(tabId)
  const handleCloseOthers = (tabId: string) =>
    openTabsOrdered.filter((t) => t.tabId !== tabId).forEach((t) => useStageStore.getState().detachFromWorkset(t.tabId))
  const handleCloseAll = () =>
    openTabsOrdered.forEach((t) => useStageStore.getState().detachFromWorkset(t.tabId))
  const handleCloseLeft = (tabId: string) => {
    const idx = openTabsOrdered.findIndex((t) => t.tabId === tabId)
    openTabsOrdered.slice(0, idx).forEach((t) => useStageStore.getState().detachFromWorkset(t.tabId))
  }
  const handleCloseRight = (tabId: string) => {
    const idx = openTabsOrdered.findIndex((t) => t.tabId === tabId)
    openTabsOrdered.slice(idx + 1).forEach((t) => useStageStore.getState().detachFromWorkset(t.tabId))
  }

  const handleSelectTab = (tabId: string) => {
    focusTab(tabId)
    setShowStartPage(false)
  }

  const dividerStateRef = useRef<{
    active: boolean; startX: number; startWidth: number; pointerId: number; target: HTMLElement | null
  }>({ active: false, startX: 0, startWidth: 0, pointerId: -1, target: null })

  function handleDividerPointerDown(e: React.PointerEvent) {
    const target = e.currentTarget as HTMLElement
    dividerStateRef.current = {
      active: true, startX: e.clientX, startWidth: leftRailWidth,
      pointerId: e.pointerId, target,
    }
    try { target.setPointerCapture(e.pointerId) } catch {}
  }
  function handleDividerPointerMove(e: React.PointerEvent) {
    if (!dividerStateRef.current.active) return
    const next = dividerStateRef.current.startWidth + (e.clientX - dividerStateRef.current.startX)
    setLeftRailWidth(next)
  }
  function endDividerDrag() {
    const { target, pointerId } = dividerStateRef.current
    if (target && pointerId >= 0) {
      try { target.releasePointerCapture(pointerId) } catch {}
    }
    dividerStateRef.current = { active: false, startX: 0, startWidth: 0, pointerId: -1, target: null }
  }
  function handleDividerPointerUp() { endDividerDrag() }
  function handleDividerPointerCancel() { endDividerDrag() }
  function handleDividerLostCapture() { endDividerDrag() }

  function handleOpenSqlEditor() {
    setShowStartPage(false)
    useStageStore.getState().openQueryEditor({
      sessionId: null,
      baseTitle: t('stage.toolRow.sql'),
      openMode: 'always_new',
      entryMode: 'blank',
    })
  }

  return (
    <div className="flex h-full w-full flex-col overflow-hidden rounded-[22px] border border-border/75 bg-muted/25 shadow-[0_24px_56px_rgba(15,23,42,0.14)] ring-1 ring-black/5 transition-all duration-200">
      <StageUIObjectRegistry tabs={tabs} />

      <div className="flex flex-col bg-transparent">
        <div className="group flex h-10 shrink-0 select-none items-center justify-between border-b border-border/65 bg-background/72 shadow-[inset_0_-1px_0_rgba(148,163,184,0.12)]">
          <div className="flex items-center gap-2 pl-3 pr-2">
            {Icon ? <Icon className="size-4 text-primary" /> : <div className="size-2 rounded-full bg-primary" />}
            <span className="text-xs font-medium tracking-wide text-foreground/80">{label || t('stage.workspace')}</span>
          </div>
          <div className="flex h-full items-center">
            <Tooltip>
              <TooltipTrigger
                render={
                  <Button
                    type="button"
                    variant="ghost"
                    className="h-full w-11 rounded-none text-muted-foreground hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing"
                    aria-label={maximized ? t('stage.restore') : t('stage.maximize')}
                    onClick={handleToggleMaximized}
                  >
                    {maximized ? (
                      <CopyIcon className="size-4 rotate-180" strokeWidth={1.5} />
                    ) : (
                      <SquareIcon className="size-4" strokeWidth={1.5} />
                    )}
                  </Button>
                }
              />
              <TooltipContent>{maximized ? t('stage.restore') : t('stage.maximize')}</TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger
                render={
                  <Button
                    type="button"
                    variant="ghost"
                    className="h-full w-11 rounded-none text-muted-foreground transition-colors hover:bg-[#e81123] hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing"
                    aria-label={t('stage.close')}
                    onClick={handleClose}
                  >
                    <XIcon className="size-4" strokeWidth={1.5} />
                  </Button>
                }
              />
              <TooltipContent>{t('stage.close')}</TooltipContent>
            </Tooltip>
          </div>
        </div>
      </div>

      <div className="flex min-h-0 flex-1 overflow-hidden bg-background/88">
        {/* Left rail (library) */}
        <div
          style={{ width: leftRailCollapsed ? 36 : leftRailWidth }}
          className="shrink-0 transition-[width] duration-[180ms]"
        >
          <StageLeftRail />
        </div>

        {/* Resizable divider (only when not collapsed) */}
        {!leftRailCollapsed ? (
          <div
            className="w-1 cursor-col-resize hover:bg-accent-primary/20 transition-colors group"
            onPointerDown={handleDividerPointerDown}
            onPointerMove={handleDividerPointerMove}
            onPointerUp={handleDividerPointerUp}
            onPointerCancel={handleDividerPointerCancel}
            onLostPointerCapture={handleDividerLostCapture}
          >
            <div className="h-full w-px bg-border-subtle group-hover:bg-accent-primary/50" />
          </div>
        ) : null}

        {/* Right pane: top tab bar + content */}
        <section className="flex min-w-0 flex-1 flex-col overflow-hidden">
          {openTabsOrdered.length > 0 && (
            <StageTabBar
              tabs={openTabsOrdered.map((t) => ({ tabId: t.tabId, title: t.title, type: t.type }))}
              activeId={activeTabId ?? undefined}
              onSelect={handleSelectTab}
              onClose={handleCloseTab}
              onCloseOthers={handleCloseOthers}
              onCloseAll={handleCloseAll}
              onCloseLeft={handleCloseLeft}
              onCloseRight={handleCloseRight}
              onOpenStartPage={() => setShowStartPage(true)}
            />
          )}

          <div data-testid="stage-workspace-pane" className="flex min-h-0 flex-1 flex-col overflow-hidden bg-background">
            {activeTabId && !showStartPage ? (
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
