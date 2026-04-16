import { SettingsIcon, SlidersHorizontalIcon, DatabaseIcon } from 'lucide-react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { ProvidersPanel } from './providers-panel'
import { ModelsPanel } from './models-panel'

export function SettingsPage() {
  const navigate = useNavigate()
  const search = useSearch({ from: '/settings' })
  const activeTab = search.tab ?? 'providers'

  const handleTabChange = (tab: string) => {
    navigate({ to: '/settings', search: { tab } })
  }

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <div className="flex items-center justify-between px-6 py-4">
        <h1 className="text-lg font-medium">设置</h1>
        <Button variant="ghost" size="sm" onClick={() => navigate({ to: '/' })}>
          返回
        </Button>
      </div>

      <Tabs value={activeTab} onValueChange={handleTabChange} className="flex-1 overflow-hidden">
        <TabsList variant="line" className="px-6">
          <TabsTrigger value="providers">
            <DatabaseIcon data-slot="icon" className="size-4" />
            <span>Providers</span>
          </TabsTrigger>
          <TabsTrigger value="models">
            <SlidersHorizontalIcon data-slot="icon" className="size-4" />
            <span>Models</span>
          </TabsTrigger>
          <TabsTrigger value="general">
            <SettingsIcon data-slot="icon" className="size-4" />
            <span>General</span>
          </TabsTrigger>
        </TabsList>

        <TabsContent value="providers" className="flex-1 overflow-y-auto p-6">
          <ProvidersPanel />
        </TabsContent>

        <TabsContent value="models" className="flex-1 overflow-y-auto p-6">
          <ModelsPanel />
        </TabsContent>

        <TabsContent value="general" className="flex-1 overflow-y-auto p-6">
          <div className="text-muted-foreground">General settings placeholder</div>
        </TabsContent>
      </Tabs>
    </div>
  )
}
