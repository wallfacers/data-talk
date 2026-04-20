import { DatabaseIcon, NetworkIcon, PieChartIcon, LayoutDashboardIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'
import { useStageStore } from '@/stores/stage-store'
import { useConnectionStore } from '@/features/connection/store'

type Props = { sessionId?: string }

export function StageDock({ sessionId }: Props) {
  const { t } = useI18n()
  const openTab = useStageStore((s) => s.openTab)
  const activeConnectionId = useConnectionStore((s) => s.activeConnectionId)

  const handleOpenSqlEditor = () => {
    if (!sessionId) return
    openTab({
      tabId: `query_editor_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      type: 'query_editor',
      title: t('stage.dock.sql'),
      scope: 'session',
      originSessionId: sessionId,
      connectionId: activeConnectionId ?? undefined,
      payload: { sql: '', source: 'user' },
      createdAt: Date.now(),
    })
  }

  const tools = [
    { id: 'sql', name: t('stage.dock.sql'), icon: DatabaseIcon, color: 'text-blue-500', onClick: handleOpenSqlEditor },
    { id: 'er', name: t('stage.dock.er'), icon: NetworkIcon, color: 'text-emerald-500', onClick: undefined },
    { id: 'report', name: t('stage.dock.report'), icon: PieChartIcon, color: 'text-purple-500', onClick: undefined },
    { id: 'dashboard', name: t('stage.dock.dashboard'), icon: LayoutDashboardIcon, color: 'text-orange-500', onClick: undefined },
  ]

  return (
    <div className="flex items-center gap-2 rounded-2xl border border-border/50 bg-background/80 p-2 shadow-2xl transition-all hover:bg-background/95 backdrop-blur-md">
      {tools.map((tool) => (
        <button
          key={tool.id}
          type="button"
          disabled={!tool.onClick}
          onClick={tool.onClick}
          className="group relative flex size-12 flex-col items-center justify-center rounded-xl transition-all duration-200 hover:-translate-y-2 hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:translate-y-0"
        >
          <tool.icon className={cn('size-6 transition-transform duration-200 group-hover:scale-110', tool.color)} />
          <span className="absolute -top-12 left-1/2 -translate-x-1/2 scale-0 whitespace-nowrap rounded-lg border border-border/50 bg-popover px-3 py-1.5 text-xs font-medium text-popover-foreground shadow-lg transition-all duration-200 group-hover:scale-100">
            {tool.name}
            <span className="absolute -bottom-1 left-1/2 -z-10 size-2 -translate-x-1/2 rotate-45 border-b border-r border-border/50 bg-popover" />
          </span>
        </button>
      ))}
    </div>
  )
}
