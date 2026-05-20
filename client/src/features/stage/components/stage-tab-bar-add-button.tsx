import { PlusIcon, DatabaseIcon, NetworkIcon, ScrollText } from 'lucide-react'
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useI18n } from '@/i18n/use-i18n'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useDataSourcePickerStore } from '@/features/session/data-source-picker/data-source-picker-store'
import { openOrFocusOpLogTab } from '@/features/op-log/utils/open-op-log-tab'
import { WorkspaceAdapter } from '../adapters/WorkspaceAdapter'

export function StageTabBarAddButton() {
  const { t } = useI18n()
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const requestPick = useDataSourcePickerStore((s) => s.requestPick)

  function openSqlEditor() {
    useStageStore.getState().openQueryEditor({
      sessionId: sessionId ?? null,
      baseTitle: t('stage.tabBar.addNew.menu.sql'),
      openMode: 'always_new',
      entryMode: 'blank',
    })
  }

  function openErDesigner() {
    void new WorkspaceAdapter(() => sessionId ?? null).exec('open_er_designer', {
      title: t('stage.tabBar.addNew.menu.er'),
    })
  }

  async function openOperationLog() {
    const result = await requestPick({ reason: 'oplog' })
    if ('cancelled' in result) return
    openOrFocusOpLogTab({
      getState: useStageStore.getState,
      connectionId: result.connectionId,
      connectionName: result.connectionName,
    })
  }

  return (
    <DropdownMenu>
      <Tooltip>
        <TooltipTrigger
          render={
            <DropdownMenuTrigger
              render={
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-xs"
                  aria-label={t('stage.tabBar.addNew')}
                  className={[
                    'text-text-muted',
                    'hover:bg-interaction-hover hover:text-text-base',
                    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
                    'data-[state=open]:bg-interaction-selected data-[state=open]:text-accent-primary',
                  ].join(' ')}
                >
                  <PlusIcon className="size-4" />
                </Button>
              }
            />
          }
        />
        <TooltipContent>{t('stage.tabBar.addNew')}</TooltipContent>
      </Tooltip>
      <DropdownMenuContent align="end" className="w-auto min-w-56 bg-bg-elevated border border-border-default shadow-sm">
        <DropdownMenuItem onClick={openSqlEditor} className="text-text-base focus:bg-accent focus:text-accent-foreground">
          <DatabaseIcon className="size-4 mr-2 text-text-muted" />
          <span className="whitespace-nowrap">{t('stage.tabBar.addNew.menu.sql')}</span>
        </DropdownMenuItem>
        <DropdownMenuItem onClick={openErDesigner} className="text-text-base focus:bg-accent focus:text-accent-foreground">
          <NetworkIcon className="size-4 mr-2 text-text-muted" />
          <span className="whitespace-nowrap">{t('stage.tabBar.addNew.menu.er')}</span>
        </DropdownMenuItem>
        <DropdownMenuItem onClick={openOperationLog} className="text-text-base focus:bg-accent focus:text-accent-foreground">
          <ScrollText className="size-4 mr-2 text-text-muted" />
          <span className="whitespace-nowrap">{t('stage.tabBar.addNew.menu.oplog')}</span>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
