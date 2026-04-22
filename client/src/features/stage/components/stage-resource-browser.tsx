import type { Connection } from '@/services/api/connection'
import type { SidebarSelection } from '@/stores/stage-store'

export type StageResourceBrowserProps = {
  sessionId: string | null
  connections: Connection[]
  expandedNodeIds: string[]
  selection: SidebarSelection | null
  onSelectionChange: (selection: SidebarSelection | null) => void
  onExpandedChange: (nodeId: string, expanded: boolean) => void
  onToolAction: (action: {
    kind: 'resource_tool'
    tool: 'sql' | 'er'
    connectionId: string
    database: string
    schema?: string | null
  }) => void
}

/** @deprecated Use StageActivityRail instead. */
export function StageResourceBrowser(_props: StageResourceBrowserProps) {
  return null
}
