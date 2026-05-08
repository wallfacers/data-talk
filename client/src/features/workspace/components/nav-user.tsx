'use client'

import { useState } from 'react'

import { cn } from '@/lib/utils'
import { DatabaseIcon, EllipsisVerticalIcon, Settings2Icon, SlidersHorizontalIcon, BoxIcon, WrenchIcon } from 'lucide-react'
import { useSettingsDialogStore } from '@/features/settings/settings-dialog-store'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from '@/components/ui/sidebar'
import { useI18n } from '@/i18n/use-i18n'

export function NavUser({
  user,
}: {
  user: {
    name: string
    email: string
  }
}) {
  const { t } = useI18n()
  const { isMobile } = useSidebar()
  const openDialog = useSettingsDialogStore((s) => s.openDialog)
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const initials = user.name.slice(0, 2).toUpperCase()
  const groups = [
    {
      title: t('settings.group.desktop'),
      items: [{ key: 'general', label: t('settings.general'), icon: Settings2Icon, section: 'general' as const }],
    },
    {
      title: t('settings.group.server'),
      items: [
        { key: 'data-sources', label: t('settings.dataSources'), icon: DatabaseIcon, section: 'data-sources' as const },
        { key: 'providers', label: t('settings.providers'), icon: BoxIcon, section: 'providers' as const },
        { key: 'models', label: t('settings.models'), icon: SlidersHorizontalIcon, section: 'models' as const },
        { key: 'maintenance', label: t('maintenance.tab.title'), icon: WrenchIcon, section: 'maintenance' as const },
      ],
    },
  ]

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu open={dropdownOpen} onOpenChange={setDropdownOpen}>
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
            <DropdownMenuGroup>
              <DropdownMenuLabel className="p-0 font-normal">
                <div className="flex items-center gap-2 px-2 py-1.5 text-left text-sm">
                  <Avatar className="size-8 rounded-lg">
                    <AvatarImage src="/avatar-placeholder.svg" />
                    <AvatarFallback className="rounded-lg">{initials}</AvatarFallback>
                  </Avatar>
                  <div className="grid flex-1 text-left text-sm leading-tight">
                    <span className="truncate font-medium">{user.name}</span>
                    <span className="truncate text-xs text-foreground/70">
                      {user.email}
                    </span>
                  </div>
                </div>
              </DropdownMenuLabel>
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            {groups.map(g => (
              <div key={g.title} className="mb-2 last:mb-0">
                <div className="px-2 pb-1 text-xs text-muted-foreground">{g.title}</div>
                {g.items.map(it => {
                  const Icon = it.icon
                  return (
                    <button
                      key={it.key}
                      type="button"
                      onClick={() => {
                        setDropdownOpen(false)
                        requestAnimationFrame(() => openDialog(it.section))
                      }}
                      className={cn(
                        'flex w-full items-center gap-2 rounded px-2 py-1.5 hover:bg-accent text-left text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
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
