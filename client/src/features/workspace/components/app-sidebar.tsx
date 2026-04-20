import { useEffect, useState, useRef, type ComponentProps } from 'react'
import { DatabaseIcon, PlusIcon } from 'lucide-react'
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
import type { Session } from '@/services/api/session'
import { useConnectionStore } from '@/features/connection/store'
import { useSessionStore } from '@/stores/session-store'
import { useHasActiveModel } from '@/features/session/hooks/use-has-active-model'
import { NavSessions } from './nav-sessions'
import { NavUser } from './nav-user'
import { useI18n } from '@/i18n/use-i18n'

const USER = {
  name: 'DataTalk',
  email: 'wallfacerswu@gmail.com',
}

export function AppSidebar(props: ComponentProps<typeof Sidebar>) {
  const { t } = useI18n()
  const qc = useQueryClient()
  const activeConnectionId = useConnectionStore((s) => s.activeConnectionId)
  const openSession = useSessionStore((s) => s.openSession)
  const hasActiveModel = useHasActiveModel()
  const { state } = useSidebar()

  // 浮动按钮组：等 sidebar 收起动画完成（200ms，与 sidebar-container 的 duration-200 对齐）后再 fade-in；
  // 展开时立刻隐藏，避免按钮挡住展开动画造成卡顿感。
  // 初始化若已是 collapsed（刷新时从 cookie 读出）则直接显示，因为此时没有收起动画要等。
  const [showFloating, setShowFloating] = useState(state === 'collapsed')
  useEffect(() => {
    if (state === 'collapsed') {
      const t = window.setTimeout(() => setShowFloating(true), 200)
      return () => window.clearTimeout(t)
    }
    setShowFloating(false)
  }, [state])

  const createMut = useMutation({
    mutationFn: async () => {
      if (!hasActiveModel) throw new Error(t('workspace.configureModelFirst'))
      const cached = qc.getQueryData<Session[]>(['sessions', activeConnectionId ?? null]) ?? []
      const empty = cached.find((s) => !s.hasEverSent)
      if (empty) return { ...empty, reusedEmpty: true }
      return createSession(activeConnectionId ?? undefined)
    },
    onSuccess: (sess) => {
      openSession(sess.id, sess.hasEverSent)
      if (!sess.reusedEmpty) {
        qc.invalidateQueries({ queryKey: ['sessions', activeConnectionId ?? null] })
      }
    },
    onSettled: () => {
      createInProgressRef.current = false
    },
  })

  // ref 防抖：mutate() 到 isPending 经 React 渲染生效之间有一窗口，
  // 同一 tick 内的连点会绕过 disabled。
  const createInProgressRef = useRef(false)
  const handleCreate = () => {
    if (createInProgressRef.current || createMut.isPending) return
    createInProgressRef.current = true
    createMut.mutate()
  }

  return (
    <>
      {/* 浮动按钮组：边栏完全收起后才 fade-in 显示在左上角 */}
      {showFloating && (
        <div className="fixed left-4 top-1.5 z-50 flex items-center gap-1 rounded-full bg-sidebar p-1 shadow-lg ring-1 ring-sidebar-border animate-in fade-in-0 duration-150">
          <SidebarTrigger className="size-7 rounded-full" />
          <Button
            variant="ghost"
            size="icon-sm"
            className="size-7 rounded-full"
            onClick={handleCreate}
            disabled={createMut.isPending}
          >
            <PlusIcon className="size-4" />
          </Button>
        </div>
      )}

      <Sidebar collapsible="offcanvas" {...props}>
        <SidebarHeader className="flex-row items-center gap-1">
          <SidebarMenu className="flex-1">
            <SidebarMenuItem>
              <SidebarMenuButton
                className="data-[slot=sidebar-menu-button]:p-1.5!"
                render={<a href="/" />}
              >
                <DatabaseIcon className="size-5!" />
                <span className="text-base font-semibold">DataTalk</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
          <SidebarTrigger className="size-7 shrink-0" />
        </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupContent className="flex flex-col gap-2">
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarMenuButton
                  tooltip={t('workspace.createSession')}
                  className="min-w-8 justify-center bg-primary text-primary-foreground duration-200 ease-linear hover:bg-primary/90 hover:text-primary-foreground active:bg-primary/90 active:text-primary-foreground"
                  onClick={handleCreate}
                  disabled={createMut.isPending}
                >
                  <PlusIcon />
                  <span>{createMut.isPending ? t('workspace.creatingSession') : t('workspace.createSession')}</span>
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
