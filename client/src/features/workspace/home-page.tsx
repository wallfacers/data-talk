import type { CSSProperties } from 'react'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'
import { useBootstrapActions } from '@/features/actions/use-bootstrap-actions'
import { SessionCanvas } from '@/features/session/session-canvas'
import { useSessionMode } from '@/features/session/use-session-mode'
import { AppSidebar } from './components/app-sidebar'
import { SiteHeader } from './components/site-header'

export function HomePage() {
  useBootstrapActions()
  const { mode } = useSessionMode()
  const heroQuiet = mode === 'HERO' || mode === 'NOSESS'

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
      <SidebarInset className="md:m-0! md:ml-0! md:rounded-none! md:shadow-none!">
        {!heroQuiet && <SiteHeader />}
        <div className="relative min-h-0 flex-1">
          <SessionCanvas />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
