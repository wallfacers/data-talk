import type { ComponentProps } from 'react'
import { DatabaseIcon, PlusIcon } from 'lucide-react'
import { toast } from 'sonner'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarTrigger,
  useSidebar,
} from '@/components/ui/sidebar'
import { Button } from '@/components/ui/button'
import { createSession } from '@/services/api/session'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { NavSessions } from './nav-sessions'
import { NavUser } from './nav-user'

const USER = {
  name: 'DataTalk',
  email: 'wallfacerswu@gmail.com',
}

export function AppSidebar(props: ComponentProps<typeof Sidebar>) {
  const qc = useQueryClient()
  const activeConnectionId = useConnectionStore((s) => s.activeConnectionId)
  const openSession = useSessionStore((s) => s.openSession)
  const { state } = useSidebar()

  const createMut = useMutation({
    mutationFn: async () => {
      if (!activeConnectionId) throw new Error('请先在连接列表中选择一个连接')
      return createSession(activeConnectionId, '新会话')
    },
    onSuccess: (sess) => {
      openSession(sess.id, sess.hasEverSent)
      qc.invalidateQueries({ queryKey: ['sessions', activeConnectionId] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  return (
    <>
      {/* 浮动按钮组：边栏收起时显示在左上角 */}
      {state === 'collapsed' && (
        <div className="fixed left-4 top-4 z-50 flex items-center gap-1 rounded-full bg-sidebar p-1 shadow-lg ring-1 ring-sidebar-border">
          <SidebarTrigger className="size-8 rounded-full" />
          <Button
            variant="ghost"
            size="icon-sm"
            className="size-8 rounded-full"
            onClick={() => createMut.mutate()}
            disabled={createMut.isPending}
          >
            <PlusIcon className="size-4" />
          </Button>
        </div>
      )}

      <Sidebar collapsible="offcanvas" {...props}>
        <SidebarHeader>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton
                className="data-[slot=sidebar-menu-button]:p-1.5!"
                render={<a href="/" />}
              >
                <DatabaseIcon className="size-5!" />
                <span className="text-base font-semibold">DataTalk</span>
                <SidebarTrigger className="ml-auto size-7" />
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupContent className="flex flex-col gap-2">
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarMenuButton
                  tooltip="创建会话"
                  className="min-w-8 justify-center bg-primary text-primary-foreground duration-200 ease-linear hover:bg-primary/90 hover:text-primary-foreground active:bg-primary/90 active:text-primary-foreground"
                  onClick={() => createMut.mutate()}
                  disabled={createMut.isPending}
                >
                  <PlusIcon />
                  <span>{createMut.isPending ? '创建中…' : '创建会话'}</span>
                </SidebarMenuButton>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        <NavSessions />
      </SidebarContent>

      <SidebarFooter>
        <NavUser user={USER} />
      </SidebarFooter>
    </Sidebar>
    </>
  )
}
