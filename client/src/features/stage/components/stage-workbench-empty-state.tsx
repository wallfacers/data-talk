import {
  DatabaseIcon,
  LineChartIcon,
  NetworkIcon,
  ScrollText,
  Table2Icon,
} from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'
import { cn } from '@/lib/utils'
import { useDataSourcePickerStore } from '@/features/session/data-source-picker/data-source-picker-store'
import { openOrFocusOpLogTab } from '@/features/op-log/utils/open-op-log-tab'
import { useStageStore } from '@/stores/stage-store'
import { StageTabBarAddButton } from './stage-tab-bar-add-button'

type Props = {
  onOpenSqlEditor?: () => void
  onOpenErDesigner?: () => void
  onOpenDashboard?: () => void
}

type StageEmptyAction = {
  id: 'sql' | 'er' | 'oplog' | 'report' | 'dashboard'
  label: string
  Icon: typeof DatabaseIcon
  enabled: boolean
  onClick?: () => void
}

export function StageWorkbenchEmptyState({
  onOpenSqlEditor,
  onOpenErDesigner,
  onOpenDashboard,
}: Props) {
  const { t } = useI18n()
  const requestPick = useDataSourcePickerStore((s) => s.requestPick)

  async function handleOpenOpLog() {
    const result = await requestPick({ reason: 'oplog' })
    if ('cancelled' in result) return
    openOrFocusOpLogTab({
      getState: useStageStore.getState,
      connectionId: result.connectionId,
      connectionName: result.connectionName,
    })
  }

  const actions: StageEmptyAction[] = [
    {
      id: 'sql',
      label: t('stage.toolRow.sql'),
      Icon: DatabaseIcon,
      enabled: Boolean(onOpenSqlEditor),
      onClick: onOpenSqlEditor,
    },
    {
      id: 'er',
      label: t('stage.toolRow.er'),
      Icon: NetworkIcon,
      enabled: Boolean(onOpenErDesigner),
      onClick: onOpenErDesigner,
    },
    {
      id: 'oplog',
      label: t('stage.toolRow.oplog'),
      Icon: ScrollText,
      enabled: true,
      onClick: handleOpenOpLog,
    },
    {
      id: 'report',
      label: t('stage.toolRow.report'),
      Icon: LineChartIcon,
      enabled: false,
    },
    {
      id: 'dashboard',
      label: t('stage.toolRow.dashboard'),
      Icon: Table2Icon,
      enabled: Boolean(onOpenDashboard),
      onClick: onOpenDashboard,
    },
  ] as const

  return (
    <div
      data-testid="stage-empty-workbench"
      className="flex min-h-0 w-full flex-1 items-center justify-center overflow-auto p-4"
    >
      <div className="w-full max-w-sm space-y-4">
        <div className="flex items-center justify-center gap-2">
          <StageTabBarAddButton />
          <span className="text-xs text-text-soft">{t('stage.leftRail.cta.openNew')}</span>
        </div>

        <ul className="space-y-1">
          {actions.map(({ id, label, Icon, enabled, onClick }) => (
            <li key={id}>
              <button
                type="button"
                disabled={!enabled}
                onClick={enabled ? onClick : undefined}
                className={cn(
                  'flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm transition-colors',
                  enabled
                    ? 'text-text-base hover:bg-interaction-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing'
                    : 'cursor-not-allowed text-text-soft',
                )}
              >
                <Icon className="size-4 shrink-0" />
                <span className="flex-1">{label}</span>
                {!enabled && (
                  <span className="shrink-0 text-[10px] uppercase tracking-wide text-text-soft">
                    {t('stage.empty.pending')}
                  </span>
                )}
              </button>
            </li>
          ))}
        </ul>

        <p className="text-center text-xs text-text-soft">
          {t('stage.empty.helper')}
        </p>
      </div>
    </div>
  )
}
