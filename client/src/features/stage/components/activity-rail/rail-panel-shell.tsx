import type { ReactNode } from 'react'
import { XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { cn } from '@/lib/utils'

type RailPanelShellProps = {
  title: string
  onClose?: () => void
  children: ReactNode
  className?: string
}

export function RailPanelShell({ title, onClose, children, className }: RailPanelShellProps) {
  const { t } = useI18n()

  return (
    <section
      data-testid="rail-panel-shell"
      className={cn(
        'flex h-full w-[280px] shrink-0 flex-col overflow-hidden border-l border-border/50 bg-background/95',
        className,
      )}
    >
      <div className="flex h-10 shrink-0 items-center justify-between gap-2 border-b border-border/40 px-3">
        <div className="min-w-0 truncate text-sm font-medium text-foreground">{title}</div>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          className="shrink-0"
          aria-label={t('stage.activityRail.closePanel')}
          onClick={onClose}
        >
          <XIcon className="size-3.5" />
        </Button>
      </div>

      <div className="min-h-0 flex-1 overflow-auto p-3">{children}</div>
    </section>
  )
}
