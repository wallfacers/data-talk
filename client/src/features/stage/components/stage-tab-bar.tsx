import { XIcon, SparklesIcon, DatabaseIcon, NetworkIcon } from 'lucide-react'
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuSeparator,
  ContextMenuTrigger,
} from '@/components/ui/context-menu'
import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'

type TabItem = {
  tabId: string
  title: string
  type?: string
  dirty?: boolean
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
    <div className="flex shrink-0 items-end overflow-x-auto overflow-y-hidden border-b border-border/40 bg-transparent px-2 pt-2">
      <div role="tablist" aria-orientation="horizontal" className="flex min-w-max items-end justify-start gap-0">
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
                    <button
                      type="button"
                      role="tab"
                      aria-selected={isActive}
                      data-state={isActive ? 'active' : 'inactive'}
                      className={cn(
                        'relative flex h-10 min-w-0 items-center gap-1.5 border-b-2 border-b-transparent px-3 pb-1.5 pt-2 text-[13px] font-medium transition-colors duration-200 ease-out select-none',
                        'data-[state=active]:border-b-foreground data-[state=active]:text-foreground',
                        'data-[state=inactive]:text-muted-foreground hover:data-[state=inactive]:border-b-border/60 hover:data-[state=inactive]:text-foreground',
                        'focus-visible:outline-none focus-visible:ring-0 [&_svg]:shrink-0',
                      )}
                    >
                      {getTabIcon(tab.type, isActive)}
                      {tab.dirty ? (
                        <span
                          data-testid="dirty-indicator"
                          aria-hidden="true"
                          className="size-1.5 shrink-0 rounded-full bg-amber-500"
                        />
                      ) : null}
                      <span className="min-w-0 truncate">{tab.title}</span>
                      <div
                        role="button"
                        aria-label={t('stage.menu.close')}
                        className={cn(
                          'ml-0.5 flex size-5 items-center justify-center rounded-md transition-all',
                          isActive
                            ? 'opacity-100 hover:bg-muted/80'
                            : 'opacity-0 group-hover/tab:opacity-100 hover:bg-muted/70',
                        )}
                        onClick={(e) => {
                          e.stopPropagation()
                          onClose?.(tab.tabId)
                        }}
                      >
                        <XIcon className="size-3.5" />
                      </div>
                    </button>
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
      </div>
    </div>
  )
}
