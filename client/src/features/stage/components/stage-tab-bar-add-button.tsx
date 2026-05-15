import { PlusIcon, DatabaseIcon, LineChartIcon, NetworkIcon, Table2Icon, ScrollText } from 'lucide-react'
import { useState } from 'react'
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem,
} from '@/components/ui/dropdown-menu'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useI18n } from '@/i18n/use-i18n'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { openOrFocusOpLogTab } from '@/features/op-log/utils/open-op-log-tab'
import { listConnections } from '@/services/api/connection'
import { WorkspaceAdapter } from '../adapters/WorkspaceAdapter'

export function StageTabBarAddButton() {
  const { t } = useI18n()
  const sessionId = useSessionStore((s) => s.activeSessionId)
  const [opLogPickerOpen, setOpLogPickerOpen] = useState(false)
  const [connections, setConnections] = useState<{id: string, name: string}[]>([])

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

  function openOperationLog() {
    void listConnections().then(conns => {
      if (conns.length === 1) {
        openOrFocusOpLogTab({
          getState: useStageStore.getState,
          connectionId: conns[0].id,
          connectionName: conns[0].name,
        })
      } else {
        setConnections(conns.map(c => ({ id: c.id, name: c.name })))
        setOpLogPickerOpen(true)
      }
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
          <span className="whitespace-nowrap">Operation Log</span>
        </DropdownMenuItem>
        <DropdownMenuItem disabled aria-disabled className="text-text-soft data-[disabled]:opacity-60 data-[disabled]:cursor-not-allowed">
          <LineChartIcon className="size-4 mr-2" />
          <span className="flex-1 whitespace-nowrap line-through decoration-text-soft/60">{t('stage.tabBar.addNew.menu.report')}</span>
          <span className="ml-2 whitespace-nowrap rounded border border-border-subtle bg-bg-subtle px-1.5 py-0.5 text-[10px] text-text-soft">{t('stage.empty.pending')}</span>
        </DropdownMenuItem>
        <DropdownMenuItem disabled aria-disabled className="text-text-soft data-[disabled]:opacity-60 data-[disabled]:cursor-not-allowed">
          <Table2Icon className="size-4 mr-2" />
          <span className="flex-1 whitespace-nowrap line-through decoration-text-soft/60">{t('stage.tabBar.addNew.menu.dashboard')}</span>
          <span className="ml-2 whitespace-nowrap rounded border border-border-subtle bg-bg-subtle px-1.5 py-0.5 text-[10px] text-text-soft">{t('stage.empty.pending')}</span>
        </DropdownMenuItem>
      </DropdownMenuContent>
      <Dialog open={opLogPickerOpen} onOpenChange={setOpLogPickerOpen}>
        <DialogContent className="bg-bg-panel border border-border-default">
          <DialogHeader>
            <DialogTitle className="text-text-strong">Select Connection</DialogTitle>
          </DialogHeader>
          <div className="max-h-64 overflow-auto space-y-1">
            {connections.map(conn => (
              <button
                key={conn.id}
                className="w-full rounded-md px-3 py-2 text-left text-[13px] text-text-base hover:bg-interaction-hover"
                onClick={() => {
                  openOrFocusOpLogTab({
                    getState: useStageStore.getState,
                    connectionId: conn.id,
                    connectionName: conn.name,
                  })
                  setOpLogPickerOpen(false)
                }}
              >
                {conn.name}
              </button>
            ))}
          </div>
        </DialogContent>
      </Dialog>
    </DropdownMenu>
  )
}
