// 极简侧边栏：不 fetch /api/sessions 或 /api/connections，只展示 demo 占位项 + 一个 reset 回调。

import type { ComponentProps } from 'react'
import { DatabaseIcon, RotateCcwIcon } from 'lucide-react'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarTrigger,
} from '@/components/ui/sidebar'

type Props = ComponentProps<typeof Sidebar> & {
  onReset?: () => void
}

export function PreviewSidebar({ onReset, ...props }: Props) {
  return (
    <Sidebar collapsible="offcanvas" {...props}>
      <SidebarHeader className="flex-row items-center gap-1">
        <SidebarMenu className="flex-1">
          <SidebarMenuItem>
            <SidebarMenuButton className="data-[slot=sidebar-menu-button]:p-1.5!">
              <DatabaseIcon className="size-5!" />
              <span className="text-base font-semibold">DataTalk · Preview</span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
        <SidebarTrigger className="size-7 shrink-0" />
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>演示</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarMenuButton isActive>
                  <span>注册趋势 demo</span>
                </SidebarMenuButton>
              </SidebarMenuItem>
              <SidebarMenuItem>
                <SidebarMenuButton onClick={() => onReset?.()}>
                  <RotateCcwIcon />
                  <span>重置到 HERO</span>
                </SidebarMenuButton>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter>
        <div className="px-2 py-1 text-xs text-muted-foreground">
          预览模式 · 纯前端 mock，不打任何后端接口
        </div>
      </SidebarFooter>
    </Sidebar>
  )
}
