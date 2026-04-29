import { useEffect, type CSSProperties } from 'react'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'
import { useBootstrapActions } from '@/features/actions/use-bootstrap-actions'
import { SessionCanvas } from '@/features/session/session-canvas'
import { ensureStageAutoOpenSubscribed } from '@/features/stage/use-stage-auto-open'
import { AppSidebar } from './components/app-sidebar'

export function HomePage() {
  useBootstrapActions()
  useEffect(() => {
    ensureStageAutoOpenSubscribed()
  }, [])

  return (
    <SidebarProvider
      style={
        {
          '--sidebar-width': 'calc(var(--spacing) * 65)',
        } as CSSProperties
      }
    >
      <AppSidebar variant="inset" />
      <SidebarInset className="md:m-0! md:ml-0! md:rounded-none! md:border-0! md:shadow-none!">
        <div className="relative min-h-0 flex-1">
          <SessionCanvas />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
