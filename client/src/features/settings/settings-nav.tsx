import { Link, useSearch } from '@tanstack/react-router'
import { cn } from '@/lib/utils'
import { Settings, Database, Box, Sparkles } from 'lucide-react'

type Section = 'general' | 'data-sources' | 'providers' | 'models'

const GROUPS: { title: string; items: { key: Section; label: string; icon: React.ComponentType<{ className?: string }> }[] }[] = [
  { title: '桌面', items: [
    { key: 'general', label: '通用', icon: Settings },
  ]},
  { title: '服务器', items: [
    { key: 'data-sources', label: '数据源', icon: Database },
    { key: 'providers', label: '提供商', icon: Box },
    { key: 'models', label: '模型', icon: Sparkles },
  ]},
]

export function SettingsNav() {
  const search = useSearch({ from: '/settings' }) as { section?: Section }
  const current = search.section ?? 'general'
  return (
    <nav className="flex w-48 flex-col gap-4 border-r p-4 text-sm">
      {GROUPS.map(g => (
        <div key={g.title}>
          <div className="px-2 pb-1 text-xs text-muted-foreground">{g.title}</div>
          {g.items.map(it => {
            const Icon = it.icon
            return (
              <Link
                key={it.key}
                to="/settings"
                search={{ section: it.key }}
                className={cn(
                  'flex items-center gap-2 rounded px-2 py-1.5 hover:bg-accent',
                  current === it.key && 'bg-accent font-medium',
                )}
              >
                <Icon className="size-4" />{it.label}
              </Link>
            )
          })}
        </div>
      ))}
    </nav>
  )
}
