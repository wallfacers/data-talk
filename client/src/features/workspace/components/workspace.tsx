import { useWorkspaceStore } from '../store'
import { TabBar } from './tab-bar'
import { EmptyState } from './empty-state'
import { QueryResultTab } from './query-result-tab'

export function Workspace() {
  const tabs = useWorkspaceStore((s) => s.tabs)
  const activeTabId = useWorkspaceStore((s) => s.activeTabId)
  const activeTab = tabs.find((t) => t.id === activeTabId)

  return (
    <div className="flex h-full flex-col">
      <TabBar />
      <div className="flex-1 overflow-hidden">
        {!activeTab && <EmptyState />}
        {activeTab?.kind === 'query-result' && <QueryResultTab tab={activeTab} />}
        {activeTab?.kind === 'er-diagram' && (
          <div className="p-4 text-sm text-muted-foreground">ER 图（未实装）</div>
        )}
        {activeTab?.kind === 'sql-editor' && (
          <div className="p-4 text-sm text-muted-foreground">SQL 编辑器（未实装）</div>
        )}
      </div>
    </div>
  )
}
