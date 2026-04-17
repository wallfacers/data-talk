import { useSearch } from '@tanstack/react-router'
import { SettingsNav } from './settings-nav'
import { GeneralPage } from './general/general-page'
import { DataSourcesPage } from './data-sources/data-sources-page'
import { ProvidersPage } from './providers/providers-page'
import { ModelsPage } from './models/models-page'

export function SettingsLayout() {
  const search = useSearch({ from: '/settings' }) as { section?: string }
  const section = search.section ?? 'general'
  return (
    <div className="flex h-screen">
      <SettingsNav />
      <main className="flex-1 overflow-y-auto p-6">
        {section === 'general' && <GeneralPage />}
        {section === 'data-sources' && <DataSourcesPage />}
        {section === 'providers' && <ProvidersPage />}
        {section === 'models' && <ModelsPage />}
      </main>
    </div>
  )
}
