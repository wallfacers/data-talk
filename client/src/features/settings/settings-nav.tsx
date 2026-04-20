import { cn } from '@/lib/utils'
import { Settings, Database, Box, Sparkles } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'
import type { Section } from './settings-dialog-store'

interface SettingsNavProps {
  activeSection: Section
  onSectionChange: (section: Section) => void
}

export function SettingsNav({ activeSection, onSectionChange }: SettingsNavProps) {
  const { t } = useI18n()
  const groups: { title: string; items: { key: Section; label: string; icon: React.ComponentType<{ className?: string }> }[] }[] = [
    { title: t('settings.group.desktop'), items: [
      { key: 'general', label: t('settings.general'), icon: Settings },
    ]},
    { title: t('settings.group.server'), items: [
      { key: 'data-sources', label: t('settings.dataSources'), icon: Database },
      { key: 'providers', label: t('settings.providers'), icon: Box },
      { key: 'models', label: t('settings.models'), icon: Sparkles },
    ]},
  ]
  return (
    <nav className="flex w-48 flex-col gap-4 border-r p-4 text-sm">
      {groups.map(g => (
        <div key={g.title}>
          <div className="px-2 pb-1 text-xs text-muted-foreground">{g.title}</div>
          {g.items.map(it => {
            const Icon = it.icon
            return (
              <button
                key={it.key}
                type="button"
                onClick={() => onSectionChange(it.key)}
                className={cn(
                  'flex w-full items-center gap-2 rounded px-2 py-1.5 hover:bg-accent text-left',
                  activeSection === it.key && 'bg-accent font-medium',
                )}
              >
                <Icon className="size-4" />{it.label}
              </button>
            )
          })}
        </div>
      ))}
    </nav>
  )
}
