import { useWorkspaceStore } from '../store'
import { TabBar } from './tab-bar'
import { EmptyState } from './empty-state'
import { QueryResultTab } from './query-result-tab'

export function Workspace() {
  const { tabs, activeTabId } = useWorkspaceStore((s) => s)
  const activeTab = tabs.find((t) => t.id === activeTabId)

  return (
    <div className="flex h-full flex-col">
      <TabBar />
      <div className="flex-1 overflow-hidden">
        {!activeTab && <EmptyState />}
        {activeTab?.kind === 'query-result' && <QueryResultTab tab={activeTab} />}
        {activeTab?.kind === 'er-diagram' && (
          <div className="p-4 text-sm text-muted-foreground">ER 图</div>
        )}
        {activeTab?.kind === 'sql-editor' && (
          <div className="p-4 text-sm text-muted-foreground">SQL 编辑器</div>
        )}
      </div>
    </div>
  )
}
