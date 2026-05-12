import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useI18n } from '@/i18n/use-i18n'
import { SETTINGS_DIALOG_DIMENSIONS } from './shared/utils'
import { SettingsNav } from './settings-nav'
import { GeneralPage } from './general/general-page'
import { DataSourcesPage } from './data-sources/data-sources-page'
import { ProvidersPage } from './providers/providers-page'
import { ModelsPage } from './models/models-page'
import { MaintenancePage } from './maintenance/maintenance-page'
import { CredentialsPage } from './credentials/credentials-page'
import { useSettingsDialogStore, type Section } from './settings-dialog-store'

const PAGE_BY_SECTION: Record<Section, React.ReactNode> = {
  'general': <GeneralPage />,
  'data-sources': <DataSourcesPage />,
  'providers': <ProvidersPage />,
  'models': <ModelsPage />,
  'credentials': <CredentialsPage />,
  'maintenance': <MaintenancePage />,
}

export function SettingsDialog() {
  const { t } = useI18n()
  const { open, activeSection, closeDialog, setActiveSection } = useSettingsDialogStore()

  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && closeDialog()}>
      <DialogContent className={`flex flex-col !p-0 overflow-hidden ${SETTINGS_DIALOG_DIMENSIONS}`}>
        <DialogHeader className="px-6 py-4 border-b">
          <DialogTitle className="text-lg font-medium">{t('settings.title')}</DialogTitle>
        </DialogHeader>
        <div className="flex flex-1 overflow-hidden">
          <SettingsNav activeSection={activeSection} onSectionChange={setActiveSection} />
          <main className="flex-1 overflow-y-auto p-6">
            {PAGE_BY_SECTION[activeSection]}
          </main>
        </div>
      </DialogContent>
    </Dialog>
  )
}
