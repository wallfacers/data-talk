import { SettingsIcon, SlidersHorizontalIcon, DatabaseIcon } from 'lucide-react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ProvidersPanel } from './providers-panel'
import { ModelsPanel } from './models-panel'
import { GeneralSettingsPanel } from './general-panel'

export function SettingsPage() {
  const navigate = useNavigate()
  const search = useSearch({ from: '/settings' })
  const activeTab = search.tab ?? 'general'

  const handleTabChange = (tab: string) => {
    navigate({ to: '/settings', search: { tab } })
  }

  return (
    <div className="flex h-full overflow-hidden">
      {/* Left sidebar navigation */}
      <div className="w-48 flex-shrink-0 border-r bg-muted/30 p-2 pt-4">
        <Tabs
          value={activeTab}
          onValueChange={handleTabChange}
          orientation="vertical"
          className="flex flex-col gap-1"
        >
          <TabsList variant="default" className="flex flex-col gap-1 bg-transparent p-0">
            <TabsTrigger value="general" className="justify-start gap-2 px-3 py-2 text-sm">
              <SettingsIcon className="size-4 shrink-0" />
              <span>系统设置</span>
            </TabsTrigger>
            <TabsTrigger value="providers" className="justify-start gap-2 px-3 py-2 text-sm">
              <DatabaseIcon className="size-4 shrink-0" />
              <span>连接配置</span>
            </TabsTrigger>
            <TabsTrigger value="models" className="justify-start gap-2 px-3 py-2 text-sm">
              <SlidersHorizontalIcon className="size-4 shrink-0" />
              <span>模型配置</span>
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      {/* Right content area */}
      <div className="flex-1 flex flex-col overflow-hidden">
        <div className="flex items-center justify-between px-6 py-4 border-b">
          <h2 className="text-lg font-medium">
            {activeTab === 'general' ? '系统设置' : activeTab === 'providers' ? '连接配置' : '模型配置'}
          </h2>
          <Button variant="ghost" size="sm" onClick={() => navigate({ to: '/' })}>
            返回
          </Button>
        </div>

        <div className="flex-1 overflow-y-auto p-6">
          {activeTab === 'general' && <GeneralSettingsPanel />}
          {activeTab === 'providers' && <ProvidersPanel />}
          {activeTab === 'models' && <ModelsPanel />}
        </div>
      </div>
    </div>
  )
}
