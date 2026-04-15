import { PanelGroup, Panel, PanelResizeHandle } from 'react-resizable-panels'
import { ConnectionList } from '@/features/connection/components/connection-list'
import { SessionList } from '@/features/session/components/session-list'
import { ChatPanel } from '@/features/chat/components/chat-panel'
import { Workspace } from '@/features/workspace/components/workspace'

export function WorkspaceLayout() {
  return (
    <PanelGroup direction="horizontal" className="h-screen w-screen">
      <Panel defaultSize={18} minSize={12} maxSize={30}>
        <aside className="flex h-full flex-col border-r">
          <ConnectionList />
          <SessionList />
        </aside>
      </Panel>
      <PanelResizeHandle className="w-px bg-border transition-colors hover:bg-primary/50" />
      <Panel defaultSize={42} minSize={25}>
        <ChatPanel />
      </Panel>
      <PanelResizeHandle className="w-px bg-border transition-colors hover:bg-primary/50" />
      <Panel defaultSize={40} minSize={25}>
        <Workspace />
      </Panel>
    </PanelGroup>
  )
}
