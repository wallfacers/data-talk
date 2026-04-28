import { useEffect, useRef, useState } from 'react'
import {
  BarChart2Icon,
  ChevronDownIcon,
  DatabaseIcon,
  FileTextIcon,
  HomeIcon,
  NetworkIcon,
  SparklesIcon,
  XIcon,
} from 'lucide-react'
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuSeparator,
  ContextMenuTrigger,
} from '@/components/ui/context-menu'
import { cn } from '@/lib/utils'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useI18n } from '@/i18n/use-i18n'
import { StageTabBarAddButton } from './stage-tab-bar-add-button'

type TabItem = {
  tabId: string
  title: string
  type?: string
  dirty?: boolean
}

type StageTabBarProps = {
  tabs: TabItem[]
  activeId?: string
  onSelect?: (tabId: string) => void
  onClose?: (tabId: string) => void
  onCloseOthers?: (tabId: string) => void
  onCloseAll?: () => void
  onCloseLeft?: (tabId: string) => void
  onCloseRight?: (tabId: string) => void
  onOpenStartPage?: () => void
}

function getTabIcon(type?: string, isActive?: boolean) {
  const color = isActive ? 'text-accent-primary' : 'text-text-muted'
  switch (type) {
    case 'sql':
    case 'query_editor':
      return (
        <DatabaseIcon
          className={cn('size-4 transition-colors', color)}
        />
      )
    case 'er':
    case 'er_canvas':
      return (
        <NetworkIcon
          className={cn('size-4 transition-colors', color)}
        />
      )
    case 'file_preview':
      return (
        <FileTextIcon
          className={cn('size-4 transition-colors', color)}
        />
      )
    case 'artifact_preview':
      return (
        <BarChart2Icon
          className={cn('size-4 transition-colors', color)}
        />
      )
    default:
      return (
        <SparklesIcon
          className={cn('size-4 transition-colors', color)}
        />
      )
  }
}

export function StageTabBar({
  tabs,
  activeId,
  onSelect,
  onClose,
  onCloseOthers,
  onCloseAll,
  onCloseLeft,
  onCloseRight,
  onOpenStartPage,
}: StageTabBarProps) {
  const { t } = useI18n()
  const [overflowOpen, setOverflowOpen] = useState(false)
  const [hasOverflow, setHasOverflow] = useState(false)
  const tabScrollRef = useRef<HTMLDivElement | null>(null)
  const overflowRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    const updateOverflow = () => {
      const container = tabScrollRef.current
      if (!container) {
        setHasOverflow(false)
        return
      }
      setHasOverflow(container.scrollWidth > container.clientWidth + 1)
    }

    updateOverflow()
    const rafId = window.requestAnimationFrame(updateOverflow)
    window.addEventListener('resize', updateOverflow)
    return () => {
      window.cancelAnimationFrame(rafId)
      window.removeEventListener('resize', updateOverflow)
    }
  }, [tabs])

  useEffect(() => {
    if (!hasOverflow && overflowOpen) {
      setOverflowOpen(false)
    }
  }, [hasOverflow, overflowOpen])

  useEffect(() => {
    if (!overflowOpen) return
    const onPointerDown = (event: PointerEvent) => {
      const root = overflowRef.current
      if (!root) return
      if (root.contains(event.target as Node)) return
      setOverflowOpen(false)
    }
    window.addEventListener('pointerdown', onPointerDown)
    return () => window.removeEventListener('pointerdown', onPointerDown)
  }, [overflowOpen])

  useEffect(() => {
    if (!activeId) return
    const container = tabScrollRef.current
    if (!container) return

    const rafId = window.requestAnimationFrame(() => {
      const activeTab = Array.from(container.querySelectorAll<HTMLElement>('[data-tab-id]'))
        .find((element) => element.dataset.tabId === activeId)
      activeTab?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' })
    })

    return () => window.cancelAnimationFrame(rafId)
  }, [activeId, tabs.length])

  useEffect(() => {
    const container = tabScrollRef.current
    if (!container) return
    const onWheel = (e: WheelEvent) => {
      if (e.deltaY === 0) return
      e.preventDefault()
      container.scrollBy({ left: e.deltaY, behavior: 'smooth' })
    }
    container.addEventListener('wheel', onWheel, { passive: false })
    return () => container.removeEventListener('wheel', onWheel)
  }, [])

  const showControls = tabs.length > 0 && (hasOverflow || onOpenStartPage)

  return (
    <div className="flex min-h-10 shrink-0 items-end border-b border-border/40 bg-transparent px-2">
      <div ref={tabScrollRef} className="min-w-0 flex-1 overflow-x-auto overflow-y-hidden [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        <div role="tablist" aria-orientation="horizontal" className="flex min-w-max items-end justify-start gap-0">
          {tabs.map((tab, index) => {
            const isActive = tab.tabId === activeId
            const hasOtherTabs = tabs.length > 1
            const hasLeftTabs = index > 0
            const hasRightTabs = index < tabs.length - 1
            const canCloseCurrent = tabs.length > 0
            const canCloseAll = tabs.length > 0
            return (
              <ContextMenu key={tab.tabId}>
                <ContextMenuTrigger
                  render={
                    <div
                      data-tab-id={tab.tabId}
                      data-state={isActive ? 'active' : 'inactive'}
                      className={cn('group/tab relative -mb-px flex items-center', isActive ? 'z-20' : 'z-10')}
                    >
                      <button
                        type="button"
                        role="tab"
                        aria-selected={isActive}
                        data-state={isActive ? 'active' : 'inactive'}
                        className={cn(
                          'relative flex h-10 min-w-0 cursor-pointer items-center gap-1.5 border-b-2 border-b-transparent px-3 pb-1.5 pt-2 text-[13px] font-medium transition-colors duration-200 ease-out select-none',
                          'data-[state=active]:border-b-foreground data-[state=active]:text-foreground',
                          'data-[state=inactive]:text-muted-foreground hover:data-[state=inactive]:border-b-border/60 hover:data-[state=inactive]:text-foreground',
                          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing [&_svg]:shrink-0',
                        )}
                        onClick={() => onSelect?.(tab.tabId)}
                      >
                        {getTabIcon(tab.type, isActive)}
                        <span className="min-w-0 truncate">{tab.title}</span>
                        {tab.dirty ? (
                          <span
                            data-testid="dirty-indicator"
                            aria-hidden
                            className="size-1.5 shrink-0 rounded-full bg-accent-primary"
                          />
                        ) : null}
                      </button>
                      <Tooltip>
                        <TooltipTrigger
                          render={
                            <button
                              type="button"
                              aria-label={t('stage.menu.close')}
                              className={cn(
                                '-ml-0.5 flex size-5 items-center justify-center rounded-md transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
                                isActive
                                  ? 'opacity-100 hover:bg-muted/80'
                                  : 'opacity-0 group-hover/tab:opacity-100 hover:bg-muted/70',
                              )}
                              onClick={(event) => {
                                event.stopPropagation()
                                onClose?.(tab.tabId)
                              }}
                            >
                              <XIcon className="size-3.5" />
                            </button>
                          }
                        />
                        <TooltipContent>{t('stage.menu.close')}</TooltipContent>
                      </Tooltip>
                    </div>
                  }
                />
                <ContextMenuContent className="w-48 font-sans text-xs">
                  <ContextMenuItem
                    disabled={!canCloseCurrent}
                    onClick={() => onClose?.(tab.tabId)}
                  >
                    {t('stage.menu.close')}
                  </ContextMenuItem>
                  <ContextMenuSeparator />
                  <ContextMenuItem
                    disabled={!hasOtherTabs}
                    onClick={() => onCloseOthers?.(tab.tabId)}
                  >
                    {t('stage.menu.closeOthers')}
                  </ContextMenuItem>
                  <ContextMenuItem
                    disabled={!canCloseAll}
                    onClick={() => onCloseAll?.()}
                  >
                    {t('stage.menu.closeAll')}
                  </ContextMenuItem>
                  <ContextMenuSeparator />
                  <ContextMenuItem
                    disabled={!hasLeftTabs}
                    onClick={() => onCloseLeft?.(tab.tabId)}
                  >
                    {t('stage.menu.closeLeft')}
                  </ContextMenuItem>
                  <ContextMenuItem
                    disabled={!hasRightTabs}
                    onClick={() => onCloseRight?.(tab.tabId)}
                  >
                    {t('stage.menu.closeRight')}
                  </ContextMenuItem>
                </ContextMenuContent>
              </ContextMenu>
            )
          })}
        </div>
      </div>

      {showControls ? (
        <div ref={overflowRef} className="relative ml-2 flex h-10 shrink-0 items-center gap-1">
          {onOpenStartPage ? (
            <Tooltip>
              <TooltipTrigger
                render={
                  <button
                    type="button"
                    data-testid="stage-tab-start-button"
                    aria-label={t('stage.tabBar.startPage')}
                    onClick={onOpenStartPage}
                    className="inline-flex size-6 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing"
                  >
                    <HomeIcon className="size-3.5" />
                  </button>
                }
              />
              <TooltipContent>{t('stage.tabBar.startPage')}</TooltipContent>
            </Tooltip>
          ) : null}
          <StageTabBarAddButton />
          {hasOverflow ? (
            <button
              type="button"
              data-testid="stage-tab-overflow-trigger"
              aria-label={t('stage.tabBar.moreTabs')}
              aria-expanded={overflowOpen}
              onClick={() => setOverflowOpen((prev) => !prev)}
              className="inline-flex size-6 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing"
            >
              <ChevronDownIcon className="size-3.5" />
            </button>
          ) : null}

          {overflowOpen && hasOverflow ? (
            <div
              data-testid="stage-tab-overflow-menu"
              className="absolute right-0 top-[calc(100%+4px)] z-40 w-64 rounded-lg border border-border/70 bg-popover p-1 shadow-md"
            >
              <div className="max-h-64 overflow-y-auto">
                {tabs.map((tab) => {
                  const isActive = tab.tabId === activeId
                  return (
                    <div key={`overflow-${tab.tabId}`} className="group flex items-center gap-1">
                      <button
                        type="button"
                        className={cn(
                          'min-w-0 flex-1 rounded-md px-2 py-1 text-left text-xs transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
                          isActive
                            ? 'bg-accent/70 text-foreground'
                            : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                        )}
                        onClick={() => {
                          onSelect?.(tab.tabId)
                          setOverflowOpen(false)
                        }}
                      >
                        <span className="truncate">{tab.title}</span>
                      </button>
                      <Tooltip>
                        <TooltipTrigger
                          render={
                            <button
                              type="button"
                              aria-label={t('stage.menu.close')}
                              className="inline-flex size-6 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing"
                              onClick={(event) => {
                                event.stopPropagation()
                                onClose?.(tab.tabId)
                              }}
                            >
                              <XIcon className="size-3.5" />
                            </button>
                          }
                        />
                        <TooltipContent>{t('stage.menu.close')}</TooltipContent>
                      </Tooltip>
                    </div>
                  )
                })}
              </div>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
