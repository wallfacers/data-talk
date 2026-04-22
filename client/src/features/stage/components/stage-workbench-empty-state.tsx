import {
  DatabaseIcon,
  LineChartIcon,
  NetworkIcon,
  Table2Icon,
} from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'
import { cn } from '@/lib/utils'

type Props = {
  onOpenSqlEditor?: () => void
}

type StageEmptyAction = {
  id: 'sql' | 'er' | 'report' | 'dashboard'
  label: string
  description: string
  Icon: typeof DatabaseIcon
  enabled: boolean
  onClick?: () => void
}

export function StageWorkbenchEmptyState({
  onOpenSqlEditor,
}: Props) {
  const { t } = useI18n()
  const actions: StageEmptyAction[] = [
    {
      id: 'sql',
      label: t('stage.toolRow.sql'),
      description: t('stage.empty.card.sql.description'),
      Icon: DatabaseIcon,
      enabled: true,
      onClick: onOpenSqlEditor,
    },
    {
      id: 'er',
      label: t('stage.toolRow.er'),
      description: t('stage.empty.card.er.description'),
      Icon: NetworkIcon,
      enabled: false,
    },
    {
      id: 'report',
      label: t('stage.toolRow.report'),
      description: t('stage.empty.card.report.description'),
      Icon: LineChartIcon,
      enabled: false,
    },
    {
      id: 'dashboard',
      label: t('stage.toolRow.dashboard'),
      description: t('stage.empty.card.dashboard.description'),
      Icon: Table2Icon,
      enabled: false,
    },
  ] as const

  return (
    <div
      data-testid="stage-empty-workbench"
      className="flex min-h-0 w-full flex-1 items-center justify-center overflow-auto p-6"
    >
      <div className="w-full max-w-3xl space-y-6">
        <div className="flex flex-col items-center gap-3 text-center">
          <div className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <DatabaseIcon className="size-5" />
          </div>
          <p className="text-sm text-muted-foreground">{t('stage.empty.pickTool')}</p>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          {actions.map(({ id, label, description, Icon, enabled, onClick }) => (
            <button
              key={id}
              type="button"
              disabled={!enabled}
              data-state={enabled ? 'ready' : 'pending'}
              onClick={enabled ? onClick : undefined}
              className={cn(
                'group flex min-h-[96px] w-full items-start justify-between gap-4 rounded-xl border px-4 py-3 text-left transition-colors',
                enabled
                  ? 'border-border/60 bg-background/95 hover:border-primary/45 hover:bg-primary/5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40'
                  : 'cursor-not-allowed border-border/45 bg-muted/35 text-muted-foreground opacity-75',
              )}
            >
              <div className="flex min-w-0 items-start gap-3">
                <div
                  className={cn(
                    'mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-lg border',
                    enabled
                      ? 'border-primary/25 bg-primary/10 text-primary'
                      : 'border-border/50 bg-background/50 text-muted-foreground',
                  )}
                >
                  <Icon className="size-4" />
                </div>
                <div className="min-w-0">
                  <div className="text-sm font-medium text-foreground">{label}</div>
                  <div className="mt-1 text-xs text-muted-foreground">{description}</div>
                </div>
              </div>

              {!enabled ? (
                <span className="shrink-0 rounded-md border border-border/55 bg-background/75 px-2 py-1 text-[11px] text-muted-foreground">
                  {t('stage.empty.pending')}
                </span>
              ) : null}
            </button>
          ))}
        </div>
        <div className="text-center text-xs text-muted-foreground">
          {t('stage.empty.helper')}
        </div>
      </div>
    </div>
  )
}
