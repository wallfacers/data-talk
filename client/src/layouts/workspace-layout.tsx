import { useNavigate } from '@tanstack/react-router'
import { PanelGroup, Panel, PanelResizeHandle } from 'react-resizable-panels'
import { Button } from '@/components/ui/button'
import { ConnectionList } from '@/features/connection/components/connection-list'
import { SessionList } from '@/features/session/components/session-list'
import { ChatPanel } from '@/features/chat/components/chat-panel'
import { Workspace } from '@/features/workspace/components/workspace'
import { LayoutDashboardIcon } from 'lucide-react'

export function WorkspaceLayout() {
  const navigate = useNavigate()

  return (
    <PanelGroup direction="horizontal" className="h-screen w-screen">
      <Panel defaultSize={18} minSize={12} maxSize={30}>
        <aside className="flex h-full flex-col border-r">
          <ConnectionList />
          <SessionList />
          <div className="border-t px-2 py-2">
            <Button
              variant="ghost"
              size="sm"
              className="w-full justify-start gap-2 text-xs"
              onClick={() => navigate({ to: '/dashboard' })}
            >
              <LayoutDashboardIcon className="size-3.5" />
              仪表盘
            </Button>
          </div>
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
