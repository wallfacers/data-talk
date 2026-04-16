import { PanelGroup, Panel, PanelResizeHandle } from 'react-resizable-panels'
import { Button } from '@/components/ui/button'
import { ConnectionList } from '@/features/connection/components/connection-list'
import { SessionList } from '@/features/session/components/session-list'
import { SessionCanvas } from '@/features/session/session-canvas'
import { useBootstrapActions } from '@/features/actions/use-bootstrap-actions'
import { useSessionMode } from '@/features/session/use-session-mode'
import { LayoutDashboardIcon } from 'lucide-react'
import { useNavigate } from '@tanstack/react-router'

export function WorkspaceLayout() {
  useBootstrapActions()
  const { mode } = useSessionMode()
  const navigate = useNavigate()

  return (
    <PanelGroup direction="horizontal" className="h-screen w-screen">
      <Panel defaultSize={18} minSize={12} maxSize={30}>
        <aside className={`flex h-full flex-col border-r transition-opacity ${mode === 'HERO' ? 'opacity-50' : 'opacity-100'}`}>
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
      <Panel>
        <SessionCanvas />
      </Panel>
    </PanelGroup>
  )
}
