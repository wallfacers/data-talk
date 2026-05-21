import { useThemeStore } from '@/stores/theme-store'
import { useUISettingsStore } from '@/stores/ui-settings-store'
import { useI18n } from '@/i18n/use-i18n'
import { GeneralSettingsPanel } from './general-panel'

export function GeneralPage() {
  const { t } = useI18n()
  const theme = useThemeStore((s) => s.theme)
  const setTheme = useThemeStore((s) => s.setTheme)
  const language = useUISettingsStore((s) => s.language)
  const setLanguage = useUISettingsStore((s) => s.setLanguage)

  return (
    <div className="max-w-2xl">
      <h1 className="mb-6 text-2xl font-semibold">{t('general.title')}</h1>
      <GeneralSettingsPanel
        theme={theme}
        language={language}
        onThemeChange={setTheme}
        onLanguageChange={setLanguage}
      />
    </div>
  )
}
