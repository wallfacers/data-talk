import { type CSSProperties } from 'react'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'
import { useBootstrapActions } from '@/features/actions/use-bootstrap-actions'
import { SessionCanvas } from '@/features/session/session-canvas'
import { AppSidebar } from './components/app-sidebar'

export function HomePage() {
  useBootstrapActions()

  return (
    <SidebarProvider
      style={
        {
          '--sidebar-width': 'calc(var(--spacing) * 65)',
        } as CSSProperties
      }
    >
      <AppSidebar />
      <SidebarInset>
        <div className="relative min-h-0 flex-1">
          <SessionCanvas />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
