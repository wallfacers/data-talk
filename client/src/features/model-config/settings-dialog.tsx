import { SettingsIcon, SlidersHorizontalIcon, DatabaseIcon } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { useSettingsDialogStore } from './settings-dialog-store'
import { ProvidersPanel } from './providers-panel'
import { ModelsPanel } from './models-panel'

export function SettingsDialog() {
  const { open, tab, closeDialog, setTab } = useSettingsDialogStore()

  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && closeDialog()}>
      <DialogContent className="max-w-2xl h-[70vh] flex flex-col">
        <DialogHeader>
          <DialogTitle>设置</DialogTitle>
        </DialogHeader>

        <Tabs value={tab} onValueChange={(t) => setTab(t as 'providers' | 'models' | 'general')} className="flex-1 overflow-hidden">
          <TabsList variant="line">
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

          <TabsContent value="providers" className="flex-1 overflow-y-auto">
            <ProvidersPanel />
          </TabsContent>

          <TabsContent value="models" className="flex-1 overflow-y-auto">
            <ModelsPanel />
          </TabsContent>

          <TabsContent value="general" className="flex-1 overflow-y-auto">
            <div className="text-muted-foreground py-4">General settings placeholder</div>
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  )
}