import { SunIcon, MoonIcon, MonitorIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

type ThemeOption = 'light' | 'dark' | 'system'
type LanguageOption = 'zh-CN' | 'en-US'

interface GeneralPanelProps {
  theme?: ThemeOption
  language?: LanguageOption
  onThemeChange?: (theme: ThemeOption) => void
  onLanguageChange?: (language: LanguageOption) => void
}

const themeOptions: { value: ThemeOption; label: string; icon: typeof SunIcon }[] = [
  { value: 'light', label: '浅色', icon: SunIcon },
  { value: 'dark', label: '深色', icon: MoonIcon },
  { value: 'system', label: '跟随系统', icon: MonitorIcon },
]

const languageOptions: { value: LanguageOption; label: string }[] = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en-US', label: 'English' },
]

export function GeneralSettingsPanel({
  theme = 'system',
  language = 'zh-CN',
  onThemeChange,
  onLanguageChange,
}: GeneralPanelProps) {
  return (
    <div className="space-y-8">
      {/* Theme selection */}
      <div>
        <label className="text-sm font-medium text-foreground mb-3 block">主题</label>
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
        <label className="text-sm font-medium text-foreground mb-3 block">语言</label>
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
    </div>
  )
}
