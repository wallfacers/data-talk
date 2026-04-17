import { createFileRoute } from '@tanstack/react-router'
import { SettingsLayout } from '@/features/settings/settings-layout'

export const Route = createFileRoute('/settings')({
  component: SettingsLayout,
  validateSearch: (search: Record<string, unknown>) => ({
    section: (search.section as string) ?? 'general',
  }),
})
