import { useState, useRef, useEffect, type KeyboardEvent } from 'react'
import { MoreHorizontalIcon, ExternalLinkIcon, PinIcon, PinOffIcon, ArchiveIcon, ArchiveRestoreIcon, PencilIcon, Trash2Icon } from 'lucide-react'
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator,
} from '@/components/ui/dropdown-menu'
import {
  AlertDialog, AlertDialogContent, AlertDialogHeader, AlertDialogTitle, AlertDialogDescription,
  AlertDialogFooter, AlertDialogCancel, AlertDialogAction,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useI18n } from '@/i18n/use-i18n'
import { useStageStore } from '@/stores/stage-store'
import type { StageTab } from '@/stores/stage-store'

type Props = { tab: StageTab }

export function StageRailRowMenu({ tab }: Props) {
  const { t } = useI18n()
  const [confirmTrashOpen, setConfirmTrashOpen] = useState(false)
  const [editing, setEditing] = useState(false)
  const [editTitle, setEditTitle] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  const focusTab = useStageStore((s) => s.focusTab)
  const setTabPinned = useStageStore((s) => s.setTabPinned)
  const archiveTab = useStageStore((s) => s.archiveTab)
  const trashTab = useStageStore((s) => s.trashTab)
  const setTabTitle = useStageStore((s) => s.setTabTitle)

  useEffect(() => {
    if (editing) inputRef.current?.focus()
  }, [editing])

  const commitRename = () => {
    const trimmed = editTitle.trim()
    if (trimmed) {
      setTabTitle(tab.tabId, trimmed)
    }
    setEditing(false)
  }

  const startRename = () => {
    setEditTitle(tab.title)
    setEditing(true)
  }

  if (editing) {
    return (
      <span onClick={(e) => e.stopPropagation()} onMouseDown={(e) => e.stopPropagation()}>
        <Input
          ref={inputRef}
          value={editTitle}
          onChange={(e) => setEditTitle(e.target.value)}
          onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
            e.stopPropagation()
            if (e.key === 'Enter') commitRename()
            if (e.key === 'Escape') setEditing(false)
          }}
          onBlur={commitRename}
          onClick={(e) => e.stopPropagation()}
          className="h-6 max-w-[160px] px-1.5 py-0 text-xs"
        />
      </span>
    )
  }

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger
          render={
            <Button
              type="button"
              variant="ghost"
              size="icon-xs"
              aria-label={t('stage.leftRail.row.menu')}
              onClick={(e: React.MouseEvent) => e.stopPropagation()}
              className={[
                'text-text-muted',
                'hover:bg-interaction-hover hover:text-text-base',
                'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
                'data-[state=open]:bg-interaction-selected data-[state=open]:text-accent-primary',
              ].join(' ')}
            >
              <MoreHorizontalIcon className="size-3.5" />
            </Button>
          }
        />
        <DropdownMenuContent align="end" className="bg-bg-elevated border border-border-default shadow-sm">
          <DropdownMenuItem onClick={() => focusTab(tab.tabId)} className="focus:bg-accent focus:text-accent-foreground">
            <ExternalLinkIcon className="size-4 mr-2 text-text-muted" />
            {t('stage.leftRail.row.menu.open')}
          </DropdownMenuItem>
          <DropdownMenuItem
            onClick={() => setTabPinned(tab.tabId, !tab.pinned)}
            className="focus:bg-accent focus:text-accent-foreground"
          >
            {tab.pinned ? <PinOffIcon className="size-4 mr-2 text-text-muted" /> : <PinIcon className="size-4 mr-2 text-text-muted" />}
            {tab.pinned ? t('stage.leftRail.row.menu.unpin') : t('stage.leftRail.row.menu.pin')}
          </DropdownMenuItem>
          <DropdownMenuItem
            onClick={() => archiveTab(tab.tabId, !tab.archived)}
            className="focus:bg-accent focus:text-accent-foreground"
          >
            {tab.archived ? <ArchiveRestoreIcon className="size-4 mr-2 text-text-muted" /> : <ArchiveIcon className="size-4 mr-2 text-text-muted" />}
            {tab.archived ? t('stage.leftRail.row.menu.unarchive') : t('stage.leftRail.row.menu.archive')}
          </DropdownMenuItem>
          <DropdownMenuItem onClick={startRename} className="focus:bg-accent focus:text-accent-foreground">
            <PencilIcon className="size-4 mr-2 text-text-muted" />
            {t('common.rename')}
          </DropdownMenuItem>
          <DropdownMenuSeparator className="bg-border-subtle" />
          <DropdownMenuItem
            onClick={() => setConfirmTrashOpen(true)}
            className="text-status-danger focus:bg-destructive/10 focus:text-destructive"
          >
            <Trash2Icon className="size-4 mr-2" />
            {t('stage.leftRail.row.menu.trash')}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <AlertDialog open={confirmTrashOpen} onOpenChange={setConfirmTrashOpen}>
        {/* The dialog renders in a portal but its React parent is the row's
            trailingMenu, so synthetic events bubble up to the row's <li>
            onClick (handleClick → focusTab). That re-focuses the tab being
            deleted right after detachFromWorkset clears it, leaving stale
            activeTabId / workset entries pointing at a deleted id. Stop
            propagation at the dialog wrapper to keep clicks isolated. */}
        <AlertDialogContent onClick={(e) => e.stopPropagation()}>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('stage.leftRail.confirmTrash.title')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('stage.leftRail.confirmTrash.body', { title: tab.title })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter className="border-t-0 bg-transparent pt-2">
            <AlertDialogCancel className="border-0 bg-transparent hover:bg-muted/50">
              {t('common.cancel')}
            </AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              className="border-0 bg-transparent"
              onClick={() => { void trashTab(tab.tabId); setConfirmTrashOpen(false) }}
            >
              {t('stage.leftRail.confirmTrash.confirm')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
