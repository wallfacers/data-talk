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
    <div className="flex shrink-0 items-center bg-transparent px-3 py-1">
      <Tabs value={activeId} className="w-auto">
        <TabsList className="flex h-10 w-fit items-center justify-start rounded-lg bg-muted/60 p-1 ring-1 ring-border/20">
          {tabs.map((tab) => {
            const isActive = tab.tabId === activeId
            return (
              <ContextMenu key={tab.tabId}>
                <ContextMenuTrigger
                  render={
                    <div data-tab-id={tab.tabId}>
                      <TabsTrigger
                        value={tab.tabId}
                        className={cn(
                          'group relative flex h-8 items-center gap-2 rounded-md px-4 text-sm font-medium transition-all duration-200 ease-out select-none',
                          'data-[state=active]:bg-background data-[state=active]:text-foreground data-[state=active]:shadow-sm data-[state=active]:ring-1 data-[state=active]:ring-border/50',
                          'data-[state=inactive]:text-muted-foreground hover:data-[state=inactive]:text-foreground',
                        )}
                      >
                        {getTabIcon(tab.type, isActive)}
                        <span>{tab.title}</span>
                        <div
                          role="button"
                          aria-label={t('stage.menu.close')}
                          className={cn(
                            'ml-1 flex size-[18px] items-center justify-center rounded-[4px] transition-all',
                            isActive
                              ? 'opacity-100 hover:bg-muted'
                              : 'opacity-0 group-hover:opacity-100 hover:bg-muted-foreground/10',
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
