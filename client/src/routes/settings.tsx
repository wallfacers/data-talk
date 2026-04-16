import { createFileRoute } from '@tanstack/react-router'
import { SettingsPage } from '@/features/model-config/settings-page'

export const Route = createFileRoute('/settings')({
  component: SettingsPage,
  validateSearch: (search: Record<string, unknown>) => ({
    tab: (search.tab as string) ?? 'providers',
  }),
})
