import { SunIcon, MoonIcon, MonitorIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { useUISettingsStore } from '@/stores/ui-settings-store'
import { useI18n } from '@/i18n/use-i18n'
import type { LanguageOption } from '@/i18n/messages'
import type { Theme } from '@/stores/theme-store'

interface GeneralPanelProps {
  theme?: Theme
  language?: LanguageOption
  onThemeChange?: (theme: Theme) => void
  onLanguageChange?: (language: LanguageOption) => void
}

export function GeneralSettingsPanel({
  theme = 'system',
  language = 'zh-CN',
  onThemeChange,
  onLanguageChange,
}: GeneralPanelProps) {
  const { t } = useI18n()
  const splitResizable = useUISettingsStore((s) => s.splitResizable)
  const setSplitResizable = useUISettingsStore((s) => s.setSplitResizable)
  const themeOptions: { value: Theme; label: string; icon: typeof SunIcon }[] = [
    { value: 'light', label: t('general.theme.light'), icon: SunIcon },
    { value: 'dark', label: t('general.theme.dark'), icon: MoonIcon },
    { value: 'system', label: t('general.theme.system'), icon: MonitorIcon },
  ]
  const languageOptions: { value: LanguageOption; label: string }[] = [
    { value: 'zh-CN', label: t('general.language.zh-CN') },
    { value: 'en-US', label: t('general.language.en-US') },
  ]

  return (
    <div className="space-y-8">
      {/* Theme selection */}
      <div>
        <label className="text-sm font-medium text-foreground mb-3 block">{t('general.theme')}</label>
        <div className="grid grid-cols-3 gap-3">
          {themeOptions.map((option) => {
            const Icon = option.icon
            const active = theme === option.value
            return (
              <button
                key={option.value}
                type="button"
                onClick={() => onThemeChange?.(option.value)}
                className={cn(
                  'flex flex-col items-center justify-center gap-2 rounded-lg border p-4 text-sm transition-colors',
                  active
                    ? 'border-primary bg-primary/5 text-foreground'
                    : 'border-input bg-background text-muted-foreground hover:text-foreground hover:border-foreground/30'
                )}
              >
                <Icon className="size-5" />
                <span>{option.label}</span>
              </button>
            )
          })}
        </div>
      </div>

      {/* Language selection */}
      <div>
        <label className="text-sm font-medium text-foreground mb-3 block">{t('general.language')}</label>
        <Select value={language} onValueChange={(v) => onLanguageChange?.(v as LanguageOption)}>
          <SelectTrigger className="w-40">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {languageOptions.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Split view resizable */}
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-foreground">{t('general.splitResizable')}</p>
          <p className="text-xs text-muted-foreground mt-0.5">{t('general.splitResizableDesc')}</p>
        </div>
        <Switch
          checked={splitResizable}
          onCheckedChange={setSplitResizable}
          aria-label={t('general.splitResizable')}
        />
      </div>
    </div>
  )
}
