import { AppSidebar } from '@/features/dashboard/components/app-sidebar'
import { ChartAreaInteractive } from '@/features/dashboard/components/chart-area-interactive'
import { DataTable } from '@/features/dashboard/components/data-table'
import { SectionCards } from '@/features/dashboard/components/section-cards'
import { SiteHeader } from '@/features/dashboard/components/site-header'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'

import mockData from './data/mock-data.json'

export function DashboardPage() {
  return (
    <SidebarProvider
      style={
        {
          '--sidebar-width': 'calc(var(--spacing) * 72)',
          '--header-height': 'calc(var(--spacing) * 12)',
        } as React.CSSProperties
      }
    >
      <AppSidebar variant="inset" />
      <SidebarInset>
        <SiteHeader />
        <div className="@container/main flex flex-1 flex-col gap-2">
          <div className="flex flex-col gap-4 py-4 md:gap-6 md:py-6">
              <SectionCards />
              <div className="px-4 lg:px-6">
                <ChartAreaInteractive />
              </div>
              <DataTable data={mockData} />
            </div>
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
