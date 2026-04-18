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
import { useSessionStore } from '@/stores/session-store'
import { renameSession, deleteSession, type Session } from '@/services/api/session'

type SessionGroup = {
  label: string
  items: Session[]
}

function groupSessions(sessions: Session[]): SessionGroup[] {
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

  for (const s of sessions) {
    const t = new Date(s.updatedAt).getTime()
    if (t >= todayStart) today.push(s)
    else if (t >= yesterdayStart) yesterday.push(s)
    else if (t >= weekStart) week.push(s)
    else if (t >= monthStart) month.push(s)
  }

  return [
    { label: '今天', items: today },
    { label: '昨天', items: yesterday },
    { label: '7 天内', items: week },
    { label: '一个月内', items: month },
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
  const activeSessionId = useSessionStore((s) => s.activeSessionId)
  const openSession = useSessionStore((s) => s.openSession)
  const sessions = useSessions()
  const qc = useQueryClient()

  const renameMut = useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) => renameSession(id, title),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sessions'] })
      toast.success('已重命名')
    },
  })

  const deleteMut = useMutation({
    mutationFn: (id: string) => deleteSession(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sessions'] })
      toast.success('已删除')
    },
  })

  if (sessions.isLoading) {
    return (
      <SidebarGroup className="group-data-[collapsible=icon]:hidden">
        <SidebarGroupLabel>会话</SidebarGroupLabel>
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
    return <EmptyHint text="加载失败" />
  }

  const groups = groupSessions(sessions.data ?? [])

  if (groups.length === 0) {
    return <EmptyHint text="暂无历史对话" />
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
  onRename,
  onDelete,
}: {
  label: string
  items: Session[]
  activeId: string | null
  onSelect: (id: string) => void
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
                    tooltip={s.title}
                    className="data-active:bg-border data-active:ring-1 data-active:ring-border"
                  >
                    <span className="truncate">{s.title}</span>
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
                      <span className="sr-only">更多</span>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent
                      className="w-32"
                      side={isMobile ? 'bottom' : 'right'}
                      align={isMobile ? 'end' : 'start'}
                    >
                      <DropdownMenuItem onClick={() => {
                        setEditingId(s.id)
                        setEditTitle(s.title)
                      }}>
                        <PencilIcon />
                        <span>重命名</span>
                      </DropdownMenuItem>
                      <DropdownMenuItem
                        variant="destructive"
                        onClick={() => setDeleteTarget(s)}
                      >
                        <Trash2Icon />
                        <span>删除</span>
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
            <AlertDialogTitle>确认删除</AlertDialogTitle>
            <AlertDialogDescription>
              确定要删除「{deleteTarget?.title}」吗？此操作不可撤销。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter className="bg-transparent border-t-0 pt-2">
            <AlertDialogCancel className="border-0 bg-transparent hover:bg-muted/50">取消</AlertDialogCancel>
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
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
