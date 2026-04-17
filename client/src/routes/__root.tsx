// __root.tsx
import { createRootRoute, Outlet } from '@tanstack/react-router'
import { TooltipProvider } from '@/components/ui/tooltip'
import { Toaster } from '@/components/ui/sonner'
import { SettingsDialog } from '@/features/settings/settings-dialog'

export const Route = createRootRoute({
  component: RootComponent,
})

function RootComponent() {
  return (
    <TooltipProvider>
      <Outlet />
      <SettingsDialog />
      <Toaster richColors position="top-right" />
    </TooltipProvider>
  )
}
