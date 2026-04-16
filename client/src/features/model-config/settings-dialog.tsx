import {
  SettingsIcon,
  SlidersHorizontalIcon,
  DatabaseIcon,
  UserIcon,
  TableIcon,
  FileTextIcon,
} from 'lucide-react'
import {
  Dialog,
  DialogContent,
} from '@/components/ui/dialog'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useSettingsDialogStore } from './settings-dialog-store'
import { ProvidersPanel } from './providers-panel'
import { ModelsPanel } from './models-panel'
import { GeneralSettingsPanel } from './general-panel'

const MAIN_TABS = [
  { key: 'general' as const, label: '系统设置', icon: SettingsIcon },
  { key: 'providers' as const, label: '连接配置', icon: DatabaseIcon },
  { key: 'models' as const, label: '模型配置', icon: SlidersHorizontalIcon },
]

const GENERAL_SUB_TABS = [
  { key: 'general-settings', label: '通用设置', icon: SettingsIcon },
  { key: 'account', label: '账号管理', icon: UserIcon },
  { key: 'data', label: '数据管理', icon: TableIcon },
  { key: 'terms', label: '服务协议', icon: FileTextIcon },
] as const

export function SettingsDialog() {
  const { open, tab, generalSubTab, closeDialog, setTab, setGeneralSubTab } =
    useSettingsDialogStore()

  const isGeneral = tab === 'general'

  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && closeDialog()}>
      <DialogContent className="flex flex-col !p-0 overflow-hidden w-[780px] h-[500px] max-w-[780px] max-h-[500px] sm:max-w-[780px]">
        <div className="flex flex-1 overflow-hidden" style={{ height: '500px' }}>
          {/* Left sidebar - always 3 main tabs */}
          <div className="w-[150px] flex-shrink-0 flex flex-col border-r bg-muted/30">
            <div className="px-2 pt-3 pb-1.5 flex-1">
              <Tabs
                value={tab}
                onValueChange={(t) => setTab(t as 'general' | 'providers' | 'models')}
                orientation="vertical"
                className="flex flex-col gap-0.5"
              >
                <TabsList variant="default" className="flex flex-col gap-0.5 bg-transparent p-0">
                  {MAIN_TABS.map(({ key, label, icon: Icon }) => (
                    <TabsTrigger key={key} value={key} className="justify-start gap-1.5 px-2 py-2 text-sm">
                      <Icon className="size-4 shrink-0" />
                      <span>{label}</span>
                    </TabsTrigger>
                  ))}
                </TabsList>
              </Tabs>
            </div>
            <div className="h-px bg-border/50" />
          </div>

          {/* Right content */}
          <div className="flex-1 flex flex-col overflow-hidden">
            <div className="flex items-center justify-between px-6 py-4 border-b">
              <h2 className="text-lg font-medium">
                {isGeneral ? '系统设置' : tab === 'providers' ? '连接配置' : '模型配置'}
              </h2>
            </div>

            {isGeneral ? (
              <div className="flex flex-1 overflow-hidden">
                {/* Sub-navigation for system settings */}
                <div className="w-[140px] flex-shrink-0 border-r bg-muted/20 p-1.5 pt-3">
                  <Tabs
                    value={generalSubTab}
                    onValueChange={(v) => setGeneralSubTab(v as (typeof GENERAL_SUB_TABS)[number]['key'])}
                    orientation="vertical"
                    className="flex flex-col gap-0.5"
                  >
                    <TabsList variant="default" className="flex flex-col gap-0.5 bg-transparent p-0">
                      {GENERAL_SUB_TABS.map(({ key, label, icon: Icon }) => (
                        <TabsTrigger key={key} value={key} className="justify-start gap-1.5 px-2 py-1.5 text-xs">
                          <Icon className="size-3.5 shrink-0" />
                          <span>{label}</span>
                        </TabsTrigger>
                      ))}
                    </TabsList>
                  </Tabs>
                </div>

                {/* System settings content */}
                <div className="flex-1 overflow-y-auto p-6">
                  {generalSubTab === 'general-settings' && <GeneralSettingsPanel />}
                  {generalSubTab === 'account' && <AccountPanel />}
                  {generalSubTab === 'data' && <DataPanel />}
                  {generalSubTab === 'terms' && <TermsPanel />}
                </div>
              </div>
            ) : (
              <div className="flex-1 overflow-y-auto p-6">
                {tab === 'providers' && <ProvidersPanel />}
                {tab === 'models' && <ModelsPanel />}
              </div>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

function AccountPanel() {
  return (
    <div className="text-muted-foreground py-4">账号管理内容开发中</div>
  )
}

function DataPanel() {
  return (
    <div className="text-muted-foreground py-4">数据管理内容开发中</div>
  )
}

function TermsPanel() {
  return (
    <div className="text-muted-foreground py-4">服务协议内容开发中</div>
  )
}
