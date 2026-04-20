import { useThemeStore } from '@/stores/theme-store'
import { GeneralSettingsPanel } from './general-panel'

export function GeneralPage() {
  const theme = useThemeStore((s) => s.theme)
  const setTheme = useThemeStore((s) => s.setTheme)

  return (
    <div className="max-w-2xl">
      <h1 className="mb-6 text-2xl font-semibold">通用</h1>
      <GeneralSettingsPanel theme={theme} onThemeChange={setTheme} />
    </div>
  )
}
