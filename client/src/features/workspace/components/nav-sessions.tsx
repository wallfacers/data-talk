import { useState, useRef, useEffect, type KeyboardEvent } from 'react'
import {
  MessageSquare,
  MoreHorizontalIcon,
  PencilIcon,
  Trash2Icon,
} from 'lucide-react'
import { toast } from 'sonner'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Input } from '@/components/ui/input'
import {
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuAction,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from '@/components/ui/sidebar'
import { Skeleton } from '@/components/ui/skeleton'
import {
  invalidateSessionLists,
  filterVisibleSessions,
  useSessions,
} from '@/features/session/hooks/use-sessions'
import { useOpenBlankSession } from '@/features/session/hooks/use-open-blank-session'
import { displaySessionTitle } from '@/features/session/session-title'
import { useSessionStore } from '@/stores/session-store'
import { useStageStore } from '@/stores/stage-store'
import { renameSession, deleteSession, type Session, type BlockedByCandidates } from '@/services/api/session'
import { useI18n } from '@/i18n/use-i18n'
import { DeleteSessionModal, type DeleteSessionCandidate } from '@/features/session/components/delete-session-modal'
import { useConnectionStore } from '@/features/connection/store'

type SessionGroup = {
  label: string
  items: Session[]
}

function canManageSession(session: Session): boolean {
  return session.hasEverSent
}

function groupSessions(sessions: Session[], t: ReturnType<typeof useI18n>['t']): SessionGroup[] {
  // 不再过滤空白会话，始终显示，让用户可以随时切换回来
  const realSessions = filterVisibleSessions(sessions)

  const now = new Date()
  const todayStart = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate(),
  ).getTime()
  const yesterdayStart = todayStart - 24 * 3600 * 1000
  const weekStart = todayStart - 7 * 24 * 3600 * 1000
  const monthStart = todayStart - 30 * 24 * 3600 * 1000

  const today: Session[] = []
  const yesterday: Session[] = []
  const week: Session[] = []
  const month: Session[] = []

  for (const s of realSessions) {
    const t = new Date(s.updatedAt).getTime()
    if (t >= todayStart) today.push(s)
    else if (t >= yesterdayStart) yesterday.push(s)
    else if (t >= weekStart) week.push(s)
    else if (t >= monthStart) month.push(s)
  }

  return [
    { label: t('workspace.today'), items: today },
    { label: t('workspace.yesterday'), items: yesterday },
    { label: t('workspace.last7Days'), items: week },
    { label: t('workspace.last30Days'), items: month },
  ].filter((g) => g.items.length > 0)
}

function EmptyHint({ text }: { text: string }) {
  return (
    <div className="flex h-full w-full flex-col items-center justify-center text-muted-foreground">
      <MessageSquare className="mb-2 h-5 w-5 text-muted-foreground/50" />
      <div className="text-sm">{text}</div>
    </div>
  )
}

export function NavSessions() {
  const { t } = useI18n()
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const openSession = useSessionStore((s) => s.openSession)
  const openBlankSession = useOpenBlankSession()
  const sessions = useSessions('all')
  const connections = useConnectionStore((s) => s.connections)
  const qc = useQueryClient()

  // Delete modal state
  const [deleteModal, setDeleteModal] = useState<{
    sessionId: string
    candidates: DeleteSessionCandidate[]
    connectionName: string
  } | null>(null)

  const renameMut = useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) => renameSession(id, title),
    onSuccess: () => {
      invalidateSessionLists(qc)
      toast.success(t('common.renamed'))
    },
  })

  const deleteMut = useMutation({
    mutationFn: (id: string) => deleteSession(id),
    onSuccess: (result, id) => {
      localStorage.removeItem(`dt.draft.${id}`)
      invalidateSessionLists(qc)
      // Check if we got blocked by candidates
      if (result && 'candidates' in result) {
        const blocked = result as BlockedByCandidates
        const session = (sessions.data ?? []).find((s) => s.id === blocked.sessionId)
        const connName = connections.find((c) => c.id === session?.connectionId)?.name ?? ''
        setDeleteModal({
          sessionId: blocked.sessionId,
          candidates: blocked.candidates.map((c) => ({
            id: c.id,
            filename: c.filename,
            kind: c.kind,
            sizeBytes: c.sizeBytes,
            title: c.title,
            summary: c.summary,
          })),
          connectionName: connName,
        })
        return
      }
      if (useSessionStore.getState().activeSessionId === id) {
        void openBlankSession(id)
      }
      useStageStore.getState().closeSessionTabs(id)
      toast.success(t('common.deleted'))
    },
  })

  if (sessions.isLoading) {
    return (
      <SidebarGroup className="group-data-[collapsible=icon]:hidden">
        <SidebarGroupLabel>{t('workspace.sessions')}</SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu>
            {Array.from({ length: 3 }).map((_, i) => (
              <SidebarMenuItem key={i}>
                <Skeleton className="h-8 w-full" />
              </SidebarMenuItem>
            ))}
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
    )
  }

  if (sessions.isError) {
    return <EmptyHint text={t('workspace.loadFailed')} />
  }

  const groups = groupSessions(sessions.data ?? [], t)

  if (groups.length === 0) {
    return <EmptyHint text={t('workspace.noHistory')} />
  }

  return (
    <>
      {groups.map((g) => (
        <SessionGroupView
          key={g.label}
          label={g.label}
          items={g.items}
          activeId={activeSessionId}
          onSelect={(id) => {
            const target = (sessions.data ?? []).find((x) => x.id === id)
            openSession(id, target?.hasEverSent ?? false)
          }}
          t={t}
          onRename={(id, title) => renameMut.mutate({ id, title })}
          onDelete={(id) => deleteMut.mutate(id)}
        />
      ))}
      {deleteModal && (
        <DeleteSessionModal
          sessionId={deleteModal.sessionId}
          connectionName={deleteModal.connectionName}
          candidates={deleteModal.candidates}
          open={deleteModal !== null}
          onOpenChange={(open) => {
            if (!open) setDeleteModal(null)
          }}
        />
      )}
    </>
  )
}

function SessionGroupView({
  label,
  items,
  activeId,
  onSelect,
  t,
  onRename,
  onDelete,
}: {
  label: string
  items: Session[]
  activeId: string | null
  onSelect: (id: string) => void
  t: ReturnType<typeof useI18n>['t']
  onRename: (id: string, title: string) => void
  onDelete: (id: string) => void
}) {
  const { isMobile } = useSidebar()
  const [deleteTarget, setDeleteTarget] = useState<Session | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editTitle, setEditTitle] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (editingId) inputRef.current?.focus()
  }, [editingId])

  const commitRename = (id: string) => {
    const session = items.find((item) => item.id === id)
    const t = editTitle.trim()
    if (session && canManageSession(session) && t) {
      onRename(id, t)
    }
    setEditingId(null)
  }

  return (
    <>
      <SidebarGroup className="group-data-[collapsible=icon]:hidden">
        <SidebarGroupLabel>{label}</SidebarGroupLabel>
        <SidebarMenu>
          {items.map((s) => {
            const title = displaySessionTitle(s.title, t('workspace.nav.newSession'))

            return (
              <SidebarMenuItem key={s.id}>
                {editingId === s.id ? (
                  <Input
                    ref={inputRef}
                    value={editTitle}
                    onChange={(e) => setEditTitle(e.target.value)}
                    onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
                      if (e.key === 'Enter') commitRename(s.id)
                      if (e.key === 'Escape') setEditingId(null)
                    }}
                    onBlur={() => commitRename(s.id)}
                    className="h-8 text-sm"
                  />
                ) : (
                  <>
                    <SidebarMenuButton
                      isActive={s.id === activeId}
                      onClick={() => onSelect(s.id)}
                      tooltip={title}
                      className="data-active:bg-accent-primary/10 data-active:font-medium"
                    >
                      <span className="truncate">{title}</span>
                    </SidebarMenuButton>
                    {canManageSession(s) ? (
                      <DropdownMenu>
                        <DropdownMenuTrigger
                          render={
                            <SidebarMenuAction
                              showOnHover
                              className="aria-expanded:bg-muted"
                            />
                          }
                        >
                          <MoreHorizontalIcon />
                          <span className="sr-only">{t('workspace.more')}</span>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent
                          className="w-32"
                          side={isMobile ? 'bottom' : 'right'}
                          align={isMobile ? 'end' : 'start'}
                        >
                          <DropdownMenuItem onClick={() => {
                            if (!canManageSession(s)) return
                            setEditingId(s.id)
                            setEditTitle(title)
                          }}>
                            <PencilIcon />
                            <span>{t('common.rename')}</span>
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            variant="destructive"
                            onClick={() => {
                              if (!canManageSession(s)) return
                              setDeleteTarget(s)
                            }}
                          >
                            <Trash2Icon />
                            <span>{t('common.delete')}</span>
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    ) : null}
                  </>
                )}
              </SidebarMenuItem>
            )
          })}
        </SidebarMenu>
      </SidebarGroup>

      {/* 删除确认框 */}
      <AlertDialog open={!!deleteTarget} onOpenChange={(open) => {
        if (!open) setDeleteTarget(null)
      }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('workspace.confirmDeleteTitle')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('workspace.confirmDeleteDescription', {
                title: deleteTarget ? displaySessionTitle(deleteTarget.title, t('workspace.nav.newSession')) : '',
              })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={() => {
                if (deleteTarget && canManageSession(deleteTarget)) {
                  onDelete(deleteTarget.id)
                }
                setDeleteTarget(null)
              }}
              disabled={!deleteTarget}
            >
              {t('common.delete')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
