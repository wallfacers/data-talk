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

// 之后可以从 stage-workspace-store 获取
type TabItem = {
  id: string
  title: string
  type?: 'artifact' | 'sql' | 'er'
}

type StageTabBarProps = {
  tabs: TabItem[]
  activeId?: string
}

function getTabIcon(type?: string, isActive?: boolean) {
  switch (type) {
    case 'sql':
      return <DatabaseIcon className={cn("size-4 transition-colors", isActive ? "text-blue-500" : "text-muted-foreground")} />
    case 'er':
      return <NetworkIcon className={cn("size-4 transition-colors", isActive ? "text-emerald-500" : "text-muted-foreground")} />
    default:
      return <SparklesIcon className={cn("size-4 transition-colors", isActive ? "text-purple-500" : "text-muted-foreground")} />
  }
}

export function StageTabBar({ tabs, activeId }: StageTabBarProps) {
  return (
    // 背景色透明，移除外层 Padding 以完全融入标题栏下方
    <div className="flex shrink-0 items-center bg-transparent px-3 py-1">
      <Tabs value={activeId} className="w-auto">
        {/* 核心胶囊容器：药丸形状 */}
        <TabsList className="flex h-10 w-fit items-center justify-start rounded-lg bg-muted/60 p-1 ring-1 ring-border/20">
          {tabs.map((tab) => {
            const isActive = tab.id === activeId
            return (
              <ContextMenu key={tab.id}>
                <ContextMenuTrigger
                  render={
                    <div data-tab-id={tab.id}>
                      <TabsTrigger
                        value={tab.id}
                        className={cn(
                          "group relative flex h-8 items-center gap-2 rounded-md px-4 text-sm font-medium transition-all duration-200 ease-out select-none",
                          "data-[state=active]:bg-background data-[state=active]:text-foreground data-[state=active]:shadow-sm data-[state=active]:ring-1 data-[state=active]:ring-border/50",
                          "data-[state=inactive]:text-muted-foreground hover:data-[state=inactive]:text-foreground"
                        )}
                      >
                        {getTabIcon(tab.type, isActive)}
                        <span>{tab.title}</span>

                        <div
                          role="button"
                          className={cn(
                            "ml-1 flex size-[18px] items-center justify-center rounded-[4px] transition-all",
                            isActive ? "opacity-100 hover:bg-muted" : "opacity-0 group-hover:opacity-100 hover:bg-muted-foreground/10"
                          )}
                          onClick={(e) => {
                            e.stopPropagation()
                          }}
                        >
                          <XIcon className="size-3.5" />
                        </div>
                      </TabsTrigger>
                    </div>
                  }
                />
                <ContextMenuContent className="w-48 text-xs font-sans">
                  <ContextMenuItem>关闭</ContextMenuItem>
                  <ContextMenuSeparator />
                  <ContextMenuItem>关闭其他</ContextMenuItem>
                  <ContextMenuItem>关闭全部</ContextMenuItem>
                  <ContextMenuSeparator />
                  <ContextMenuItem>关闭左侧标签页</ContextMenuItem>
                  <ContextMenuItem>关闭右侧标签页</ContextMenuItem>
                </ContextMenuContent>
              </ContextMenu>
            )
          })}
        </TabsList>
      </Tabs>
    </div>
  )
}
