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
import { useSessions } from '@/features/session/hooks/use-sessions'
import { useOpenBlankSession } from '@/features/session/hooks/use-open-blank-session'
import { useSessionStore } from '@/stores/session-store'
import { useConnectionStore } from '@/features/connection/store'
import { renameSession, deleteSession, type Session } from '@/services/api/session'
import { useI18n } from '@/i18n/use-i18n'

type SessionGroup = {
  label: string
  items: Session[]
}

// OpenCode 生成的临时标题格式，不应展示
const OPENCODE_TEMP_TITLE_REGEX = /^New session - /

/** 过滤临时标题，返回实际展示的标题 */
function displayTitle(title: string): string {
  if (OPENCODE_TEMP_TITLE_REGEX.test(title)) {
    return title
  }
  return title
}

function groupSessions(sessions: Session[], t: ReturnType<typeof useI18n>['t']): SessionGroup[] {
  // 不再过滤空白会话，始终显示，让用户可以随时切换回来
  const realSessions = sessions

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
  const sessions = useSessions()
  const qc = useQueryClient()
  const connectionId = useConnectionStore((s) => s.activeConnectionId)

  const renameMut = useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) => renameSession(id, title),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sessions', connectionId ?? null] })
      toast.success(t('common.renamed'))
    },
  })

  const deleteMut = useMutation({
    mutationFn: (id: string) => deleteSession(id),
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: ['sessions', connectionId ?? null] })
      if (useSessionStore.getState().activeSessionId === id) {
        void openBlankSession(id)
      }
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
    const t = editTitle.trim()
    if (t) {
      onRename(id, t)
    }
    setEditingId(null)
  }

  return (
    <>
      <SidebarGroup className="group-data-[collapsible=icon]:hidden">
        <SidebarGroupLabel>{label}</SidebarGroupLabel>
        <SidebarMenu>
          {items.map((s) => (
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
                    tooltip={displayTitle(s.title)}
                    className="data-active:bg-border data-active:ring-1 data-active:ring-border"
                  >
                    <span className="truncate">{displayTitle(s.title)}</span>
                  </SidebarMenuButton>
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
                        setEditingId(s.id)
                        setEditTitle(OPENCODE_TEMP_TITLE_REGEX.test(s.title) ? t('workspace.nav.newSession') : s.title)
                      }}>
                        <PencilIcon />
                        <span>{t('common.rename')}</span>
                      </DropdownMenuItem>
                      <DropdownMenuItem
                        variant="destructive"
                        onClick={() => setDeleteTarget(s)}
                      >
                        <Trash2Icon />
                        <span>{t('common.delete')}</span>
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </>
              )}
            </SidebarMenuItem>
          ))}
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
                title: OPENCODE_TEMP_TITLE_REGEX.test(deleteTarget?.title ?? '')
                  ? t('workspace.nav.newSession')
                  : deleteTarget?.title ?? '',
              })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter className="bg-transparent border-t-0 pt-2">
            <AlertDialogCancel className="border-0 bg-transparent hover:bg-muted/50">{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              className="border-0 bg-transparent"
              onClick={() => {
                if (deleteTarget) {
                  onDelete(deleteTarget.id)
                }
                setDeleteTarget(null)
              }}
            >
              {t('common.delete')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
