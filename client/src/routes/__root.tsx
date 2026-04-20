// __root.tsx
import { createRootRoute, Outlet } from '@tanstack/react-router'
import { TooltipProvider } from '@/components/ui/tooltip'
import { Toaster } from '@/components/ui/sonner'
import { SettingsDialog } from '@/features/settings/settings-dialog'
import { useAutoSelectDefaultModel } from '@/features/session/hooks/use-auto-select-default-model'
import { useTheme } from '@/hooks/use-theme'

export const Route = createRootRoute({
  component: RootComponent,
})

function RootComponent() {
  useTheme()
  useAutoSelectDefaultModel()
  return (
    <TooltipProvider>
      <Outlet />
      <SettingsDialog />
      <Toaster richColors position="top-right" />
    </TooltipProvider>
  )
}
