import { useMemo, useState } from 'react'
import { useShallow } from 'zustand/react/shallow'
import { PanelLeftIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import {
  AlertDialog, AlertDialogContent, AlertDialogHeader, AlertDialogTitle, AlertDialogDescription,
  AlertDialogFooter, AlertDialogCancel, AlertDialogAction,
} from '@/components/ui/alert-dialog'
import { useStageStore } from '@/stores/stage-store'
import { useI18n } from '@/i18n/use-i18n'
import { StageRailSearch } from './stage-rail-search'
import { StageRailGroup } from './stage-rail-group'
import { StageRailRow } from './stage-rail-row'
import type { StageTab } from '@/stores/stage-store'

export function StageLeftRail() {
  const { t } = useI18n()

  const collapsed = useStageStore((s) => s.leftRailCollapsed)
  const toggleCollapsed = useStageStore((s) => s.toggleLeftRailCollapsed)

  const allTabs = useStageStore(useShallow((s) => s.tabs))
  const openTabIds = useStageStore((s) => s.openTabIds)
  const activeTabId = useStageStore((s) => s.activeTabId)
  const focusTab = useStageStore((s) => s.focusTab)
  const archiveTab = useStageStore((s) => s.archiveTab)

  const [query, setQuery] = useState('')
  const [pendingUnarchiveTab, setPendingUnarchiveTab] = useState<StageTab | null>(null)

  const { active, archived } = useMemo(() => {
    const term = query.trim().toLowerCase()
    const filter = (tab: StageTab) => !term || tab.title.toLowerCase().includes(term)
    const active = allTabs.filter((t) => !t.archived).filter(filter)
      .sort((a, b) => {
        if (!!b.pinned !== !!a.pinned) return (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0)
        return (b.createdAt ?? 0) - (a.createdAt ?? 0)
      })
    const archived = allTabs.filter((t) => t.archived).filter(filter)
    return { active, archived }
  }, [allTabs, query])

  function handleClick(tab: StageTab) {
    if (tab.archived) {
      setPendingUnarchiveTab(tab)
      return
    }
    focusTab(tab.tabId)
  }

  if (collapsed) {
    return (
      <div className="flex h-full w-9 flex-col bg-bg-subtle border-r border-border-subtle">
        <Tooltip>
          <TooltipTrigger
            render={
              <button
                type="button"
                aria-label={t('stage.leftRail.expand')}
                onClick={toggleCollapsed}
                className="h-8 w-full flex items-center justify-center text-text-muted hover:bg-interaction-hover hover:text-text-base focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing"
              >
                <PanelLeftIcon className="size-4" />
              </button>
            }
          />
          <TooltipContent>{t('stage.leftRail.expand')}</TooltipContent>
        </Tooltip>
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col bg-bg-subtle border-r border-border-subtle">
      <div className="flex h-[41px] shrink-0 items-center gap-1 px-2 border-b border-border-subtle">
        <span className="flex-1 text-[13px] font-medium uppercase tracking-wide text-text-soft">
          {t('stage.leftRail.title')}
        </span>
        <Tooltip>
          <TooltipTrigger
            render={
              <Button
                type="button" variant="ghost" size="icon-xs"
                aria-label={t('stage.leftRail.collapse')}
                onClick={toggleCollapsed}
                className="text-text-muted hover:bg-interaction-hover hover:text-text-base focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing"
              >
                <PanelLeftIcon className="size-3.5" />
              </Button>
            }
          />
          <TooltipContent>{t('stage.leftRail.collapse')}</TooltipContent>
        </Tooltip>
      </div>

      <div className="px-2 pt-2">
        <StageRailSearch value={query} onChange={setQuery} />
      </div>

      <div className="flex-1 overflow-y-auto px-1 py-2 flex flex-col gap-2">
        {active.length > 0 && (
          <StageRailGroup label={t('stage.leftRail.group.active')} count={active.length} defaultOpen>
            {active.map((tab) => (
              <StageRailRow
                key={tab.tabId}
                tab={tab}
                active={tab.tabId === activeTabId}
                inWorkset={openTabIds.has(tab.tabId)}
                onClick={() => handleClick(tab)}
              />
            ))}
          </StageRailGroup>
        )}

        {archived.length > 0 ? (
          <StageRailGroup label={t('stage.leftRail.group.archived')} count={archived.length} defaultOpen={false}>
            {archived.map((tab) => (
              <StageRailRow
                key={tab.tabId}
                tab={tab}
                active={false}
                inWorkset={false}
                onClick={() => handleClick(tab)}
              />
            ))}
          </StageRailGroup>
        ) : null}
      </div>

      <AlertDialog open={!!pendingUnarchiveTab} onOpenChange={(o) => !o && setPendingUnarchiveTab(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('stage.leftRail.confirmUnarchive.title')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('stage.leftRail.confirmUnarchive.body', { title: pendingUnarchiveTab?.title ?? '' })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                if (!pendingUnarchiveTab) return
                archiveTab(pendingUnarchiveTab.tabId, false)
                focusTab(pendingUnarchiveTab.tabId)
                setPendingUnarchiveTab(null)
              }}
            >
              {t('stage.leftRail.confirmUnarchive.confirm')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
