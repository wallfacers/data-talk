import { X } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useWorkspaceStore } from '../store'

export function TabBar() {
  const tabs = useWorkspaceStore((s) => s.tabs)
  const activeTabId = useWorkspaceStore((s) => s.activeTabId)
  const setActive = useWorkspaceStore((s) => s.setActive)
  const closeTab = useWorkspaceStore((s) => s.closeTab)

  if (tabs.length === 0) return null

  return (
    <div className="flex items-center gap-1 border-b bg-muted/30 px-2">
      {tabs.map((tab) => {
        const active = tab.id === activeTabId
        return (
          <button
            key={tab.id}
            onClick={() => setActive(tab.id)}
            className={cn(
              'group flex items-center gap-2 rounded-t-md px-3 py-1.5 text-sm',
              active ? 'bg-background font-medium' : 'text-muted-foreground hover:bg-background/50',
            )}
          >
            <span>{tab.title}</span>
            <span
              role="button"
              aria-label="关闭"
              onClick={(e) => {
                e.stopPropagation()
                closeTab(tab.id)
              }}
              className="opacity-0 transition-opacity group-hover:opacity-100 hover:text-destructive"
            >
              <X className="size-3" />
            </span>
          </button>
        )
      })}
    </div>
  )
}
