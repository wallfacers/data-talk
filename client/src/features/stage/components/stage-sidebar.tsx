import type { ReactNode } from 'react'
import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { cn } from '@/lib/utils'

type StageSidebarProps = {
  sessionId?: string
  collapsed: boolean
  onToggleCollapsed?: () => void
  toolRowSlot?: ReactNode
  resourceBrowserSlot?: ReactNode
  className?: string
}

function Placeholder({ label }: { label: string }) {
  return (
    <div className="rounded-lg border border-dashed border-border/60 bg-background/60 px-3 py-6 text-center text-xs text-muted-foreground">
      {label}
    </div>
  )
}

export function StageSidebar({
  sessionId,
  collapsed,
  onToggleCollapsed,
  toolRowSlot,
  resourceBrowserSlot,
  className,
}: StageSidebarProps) {
  const { t } = useI18n()

  return (
    <aside
      data-testid="stage-sidebar"
      data-session-id={sessionId}
      data-state={collapsed ? 'collapsed' : 'expanded'}
      className={cn(
        'flex h-full shrink-0 flex-col overflow-hidden border-r border-border/50 bg-muted/10 transition-[width,background-color] duration-200 ease-out',
        collapsed ? 'w-16' : 'w-80',
        className,
      )}
    >
      <div className="flex shrink-0 items-center justify-between gap-2 border-b border-border/40 px-3 py-2">
        {!collapsed ? (
          <div className="min-w-0">
            <div className="text-xs font-medium text-foreground/80">{t('stage.sidebar.title')}</div>
            <div className="text-[11px] text-muted-foreground">{t('stage.sidebar.subtitle')}</div>
          </div>
        ) : (
          <div className="flex-1 text-center text-[10px] font-medium uppercase tracking-[0.22em] text-muted-foreground">
            {t('stage.sidebar.title')}
          </div>
        )}
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="size-8 shrink-0"
          onClick={onToggleCollapsed}
          aria-label={collapsed ? t('stage.sidebar.expand') : t('stage.sidebar.collapse')}
        >
          {collapsed ? <ChevronRightIcon className="size-4" /> : <ChevronLeftIcon className="size-4" />}
        </Button>
      </div>

      {collapsed ? (
        <div className="flex flex-1 flex-col items-center gap-3 px-2 py-3">
          <div className="flex w-full justify-center" data-slot="stage-tool-row-collapsed">
            {toolRowSlot ?? <Placeholder label="工具" />}
          </div>
          <div className="h-2 w-full" aria-hidden="true" />
        </div>
      ) : (
        <div className="flex min-h-0 flex-1 flex-col gap-3 p-3">
          <section className="flex shrink-0 flex-col gap-2" data-slot="stage-tool-row">
            {toolRowSlot ?? <Placeholder label="顶部工具行占位" />}
          </section>
          <section className="flex min-h-0 flex-1 flex-col gap-2" data-slot="stage-resource-browser">
            {resourceBrowserSlot ?? <Placeholder label="资源浏览器占位" />}
          </section>
        </div>
      )}
    </aside>
  )
}
