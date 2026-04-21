import { XIcon, SparklesIcon, DatabaseIcon, NetworkIcon } from 'lucide-react'
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuSeparator,
  ContextMenuTrigger,
} from '@/components/ui/context-menu'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'

type TabItem = {
  tabId: string
  title: string
  type?: string
}

type StageTabBarProps = {
  tabs: TabItem[]
  activeId?: string
  onClose?: (tabId: string) => void
  onCloseOthers?: (tabId: string) => void
  onCloseAll?: () => void
  onCloseLeft?: (tabId: string) => void
  onCloseRight?: (tabId: string) => void
}

function getTabIcon(type?: string, isActive?: boolean) {
  switch (type) {
    case 'sql':
    case 'query_editor':
      return (
        <DatabaseIcon
          className={cn('size-4 transition-colors', isActive ? 'text-blue-500' : 'text-muted-foreground')}
        />
      )
    case 'er':
    case 'er_canvas':
      return (
        <NetworkIcon
          className={cn('size-4 transition-colors', isActive ? 'text-emerald-500' : 'text-muted-foreground')}
        />
      )
    default:
      return (
        <SparklesIcon
          className={cn('size-4 transition-colors', isActive ? 'text-purple-500' : 'text-muted-foreground')}
        />
      )
  }
}

export function StageTabBar({
  tabs, activeId, onClose, onCloseOthers, onCloseAll, onCloseLeft, onCloseRight,
}: StageTabBarProps) {
  const { t } = useI18n()
  return (
    <div className="flex shrink-0 items-end border-b border-border/40 bg-transparent px-2 pt-2">
      <Tabs value={activeId} className="w-full min-w-0">
        <TabsList className="flex w-full items-end justify-start gap-0 rounded-none bg-transparent p-0">
          {tabs.map((tab) => {
            const isActive = tab.tabId === activeId
            return (
              <ContextMenu key={tab.tabId}>
                <ContextMenuTrigger
                  render={
                    <div
                      data-tab-id={tab.tabId}
                      data-state={isActive ? 'active' : 'inactive'}
                      className={cn('group/tab relative -mb-px flex', isActive ? 'z-20' : 'z-10')}
                    >
                      <TabsTrigger
                        value={tab.tabId}
                        data-state={isActive ? 'active' : 'inactive'}
                        className={cn(
                          'relative flex h-10 min-w-0 items-center gap-1.5 border border-transparent border-b-transparent px-3 pb-1.5 pt-2 text-[13px] font-medium transition-all duration-200 ease-out select-none',
                          'data-[state=active]:z-20 data-[state=active]:-mb-px data-[state=active]:rounded-t-[14px] data-[state=active]:border-border/50 data-[state=active]:border-b-background data-[state=active]:bg-background data-[state=active]:text-foreground data-[state=active]:shadow-[0_-1px_0_rgba(255,255,255,0.7),0_1px_10px_rgba(15,23,42,0.07)]',
                          'data-[state=inactive]:mt-[2px] data-[state=inactive]:rounded-t-[12px] data-[state=inactive]:border-border/30 data-[state=inactive]:bg-muted/35 data-[state=inactive]:text-muted-foreground hover:data-[state=inactive]:bg-muted/60 hover:data-[state=inactive]:text-foreground',
                          'after:content-none focus-visible:outline-none focus-visible:ring-0 [&_svg]:shrink-0',
                        )}
                      >
                        {getTabIcon(tab.type, isActive)}
                        <span className="min-w-0 truncate">{tab.title}</span>
                        <div
                          role="button"
                          aria-label={t('stage.menu.close')}
                          className={cn(
                            'ml-0.5 flex size-5 items-center justify-center rounded-md transition-all',
                            isActive
                              ? 'opacity-90 hover:bg-muted/80 hover:opacity-100'
                              : 'opacity-0 group-hover/tab:opacity-100 hover:bg-muted/70',
                          )}
                          onClick={(e) => {
                            e.stopPropagation()
                            onClose?.(tab.tabId)
                          }}
                        >
                          <XIcon className="size-3.5" />
                        </div>
                      </TabsTrigger>
                    </div>
                  }
                />
                <ContextMenuContent className="w-48 font-sans text-xs">
                  <ContextMenuItem onSelect={() => onClose?.(tab.tabId)}>
                    {t('stage.menu.close')}
                  </ContextMenuItem>
                  <ContextMenuSeparator />
                  <ContextMenuItem onSelect={() => onCloseOthers?.(tab.tabId)}>
                    {t('stage.menu.closeOthers')}
                  </ContextMenuItem>
                  <ContextMenuItem onSelect={() => onCloseAll?.()}>
                    {t('stage.menu.closeAll')}
                  </ContextMenuItem>
                  <ContextMenuSeparator />
                  <ContextMenuItem onSelect={() => onCloseLeft?.(tab.tabId)}>
                    {t('stage.menu.closeLeft')}
                  </ContextMenuItem>
                  <ContextMenuItem onSelect={() => onCloseRight?.(tab.tabId)}>
                    {t('stage.menu.closeRight')}
                  </ContextMenuItem>
                </ContextMenuContent>
              </ContextMenu>
            )
          })}
        </TabsList>
      </Tabs>
    </div>
  )
}
