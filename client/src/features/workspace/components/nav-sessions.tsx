import {
  MessageSquare,
  MoreHorizontalIcon,
  PencilIcon,
  Share2Icon,
  Trash2Icon,
} from 'lucide-react'
import { toast } from 'sonner'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
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
import type { Session } from '@/services/api/session'

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
}: {
  label: string
  items: Session[]
  activeId: string | null
  onSelect: (id: string) => void
}) {
  const { isMobile } = useSidebar()
  return (
    <SidebarGroup className="group-data-[collapsible=icon]:hidden">
      <SidebarGroupLabel>{label}</SidebarGroupLabel>
      <SidebarMenu>
        {items.map((s) => (
          <SidebarMenuItem key={s.id}>
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
                <DropdownMenuItem
                  onClick={() => toast.info(`重命名 "${s.title}"：占位`)}
                >
                  <PencilIcon />
                  <span>重命名</span>
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={() => toast.info(`分享 "${s.title}"：占位`)}
                >
                  <Share2Icon />
                  <span>分享</span>
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  variant="destructive"
                  onClick={() => toast.info(`删除 "${s.title}"：占位`)}
                >
                  <Trash2Icon />
                  <span>删除</span>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </SidebarMenuItem>
        ))}
      </SidebarMenu>
    </SidebarGroup>
  )
}
