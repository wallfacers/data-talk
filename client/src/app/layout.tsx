import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar"
import { AppSidebar } from "@/features/sidebar/components/app-sidebar"
import { Separator } from "@/components/ui/separator"

interface LayoutProps {
  children: React.ReactNode
}

export function Layout({ children }: LayoutProps) {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <header className="flex h-12 shrink-0 items-center gap-2 border-b px-4">
          <h1 className="text-lg font-semibold">数据库查询助手</h1>
        </header>
        <Separator />
        <main className="flex-1">{children}</main>
      </SidebarInset>
    </SidebarProvider>
  )
}
