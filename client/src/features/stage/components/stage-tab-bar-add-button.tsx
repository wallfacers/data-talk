import { PlusIcon, DatabaseIcon, NetworkIcon, LineChartIcon, Table2Icon } from 'lucide-react'
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useI18n } from '@/i18n/use-i18n'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'

export function StageTabBarAddButton() {
  const { t } = useI18n()
  const sessionId = useSessionStore((s) => s.activeSessionId)

  function openSqlEditor() {
    useStageStore.getState().openQueryEditor({
      sessionId: sessionId ?? null,
      baseTitle: t('stage.tabBar.addNew.menu.sql'),
      openMode: 'always_new',
      entryMode: 'blank',
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
      <DropdownMenuContent align="end" className="bg-bg-elevated border border-border-default shadow-sm">
        <DropdownMenuItem onClick={openSqlEditor} className="text-text-base focus:bg-accent focus:text-accent-foreground">
          <DatabaseIcon className="size-4 mr-2 text-text-muted" />
          {t('stage.tabBar.addNew.menu.sql')}
        </DropdownMenuItem>
        <DropdownMenuItem disabled aria-disabled className="text-text-soft data-[disabled]:opacity-60 data-[disabled]:cursor-not-allowed">
          <NetworkIcon className="size-4 mr-2" />
          <span className="flex-1 line-through decoration-text-soft/60">{t('stage.tabBar.addNew.menu.er')}</span>
          <span className="ml-2 rounded border border-border-subtle bg-bg-subtle px-1.5 py-0.5 text-[10px] text-text-soft">{t('stage.empty.pending')}</span>
        </DropdownMenuItem>
        <DropdownMenuItem disabled aria-disabled className="text-text-soft data-[disabled]:opacity-60 data-[disabled]:cursor-not-allowed">
          <LineChartIcon className="size-4 mr-2" />
          <span className="flex-1 line-through decoration-text-soft/60">{t('stage.tabBar.addNew.menu.report')}</span>
          <span className="ml-2 rounded border border-border-subtle bg-bg-subtle px-1.5 py-0.5 text-[10px] text-text-soft">{t('stage.empty.pending')}</span>
        </DropdownMenuItem>
        <DropdownMenuItem disabled aria-disabled className="text-text-soft data-[disabled]:opacity-60 data-[disabled]:cursor-not-allowed">
          <Table2Icon className="size-4 mr-2" />
          <span className="flex-1 line-through decoration-text-soft/60">{t('stage.tabBar.addNew.menu.dashboard')}</span>
          <span className="ml-2 rounded border border-border-subtle bg-bg-subtle px-1.5 py-0.5 text-[10px] text-text-soft">{t('stage.empty.pending')}</span>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
