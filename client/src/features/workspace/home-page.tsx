import type { CSSProperties } from 'react'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'
import { useBootstrapActions } from '@/features/actions/use-bootstrap-actions'
import { SessionCanvas } from '@/features/session/session-canvas'
import { AppSidebar } from './components/app-sidebar'
import { SiteHeader } from './components/site-header'

export function HomePage() {
  useBootstrapActions()

  return (
    <SidebarProvider
      style={
        {
          '--sidebar-width': 'calc(var(--spacing) * 65)',
          '--header-height': 'calc(var(--spacing) * 12)',
        } as CSSProperties
      }
    >
      <AppSidebar variant="inset" />
      <SidebarInset>
        <SiteHeader />
        <div className="relative min-h-0 flex-1">
          <SessionCanvas />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
