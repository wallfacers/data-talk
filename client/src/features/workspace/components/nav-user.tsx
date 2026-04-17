'use client'

import { cn } from '@/lib/utils'
import { DatabaseIcon, EllipsisVerticalIcon, Settings2Icon, SlidersHorizontalIcon } from 'lucide-react'
import { useSettingsDialogStore } from '@/features/settings/settings-dialog-store'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from '@/components/ui/sidebar'

const GROUPS = [
  {
    title: '桌面',
    items: [{ key: 'general', label: '系统设置', icon: Settings2Icon, section: 'general' as const }],
  },
  {
    title: '服务器',
    items: [
      { key: 'data-sources', label: '连接配置', icon: DatabaseIcon, section: 'data-sources' as const },
      { key: 'models', label: '模型配置', icon: SlidersHorizontalIcon, section: 'models' as const },
    ],
  },
]

export function NavUser({
  user,
}: {
  user: {
    name: string
    email: string
  }
}) {
  const { isMobile } = useSidebar()
  const openDialog = useSettingsDialogStore((s) => s.openDialog)
  const initials = user.name.slice(0, 2).toUpperCase()

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger
            render={
              <SidebarMenuButton size="lg" className="aria-expanded:bg-muted" />
            }
          >
            <Avatar className="size-8 rounded-lg">
              <AvatarFallback className="rounded-lg">{initials}</AvatarFallback>
            </Avatar>
            <div className="grid flex-1 text-left text-sm leading-tight">
              <span className="truncate font-medium">{user.name}</span>
              <span className="truncate text-xs text-foreground/70">
                {user.email}
              </span>
            </div>
            <EllipsisVerticalIcon className="ml-auto size-4" />
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="w-56 p-2"
            side={isMobile ? 'bottom' : 'right'}
            align="end"
            sideOffset={4}
          >
            {GROUPS.map(g => (
              <div key={g.title} className="mb-2 last:mb-0">
                <div className="px-2 pb-1 text-xs text-muted-foreground">{g.title}</div>
                {g.items.map(it => {
                  const Icon = it.icon
                  return (
                    <button
                      key={it.key}
                      type="button"
                      onClick={() => openDialog(it.section)}
                      className={cn(
                        'flex w-full items-center gap-2 rounded px-2 py-1.5 hover:bg-accent text-left text-sm',
                      )}
                    >
                      <Icon className="size-4" />
                      <span>{it.label}</span>
                    </button>
                  )
                })}
              </div>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
