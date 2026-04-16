import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { ProviderIcon } from './provider-icon'
import { ProviderItem } from './provider-item'
import { ConnectProviderDialog } from './connect-provider-dialog'
import { CustomProviderDialog } from './custom-provider-dialog'
import { useModelConfigStore } from './store'
import { POPULAR_PROVIDER_ORDER } from './mock-data'
import type { Provider } from './types'

export function ProvidersPanel() {
  const providers = useModelConfigStore((s) => s.providers)
  const connectProvider = useModelConfigStore((s) => s.connectProvider)
  const disconnectProvider = useModelConfigStore((s) => s.disconnectProvider)
  const addCustomProvider = useModelConfigStore((s) => s.addCustomProvider)

  const [connectDialogOpen, setConnectDialogOpen] = useState(false)
  const [customDialogOpen, setCustomDialogOpen] = useState(false)
  const [selectedProvider, setSelectedProvider] = useState<Provider | null>(null)

  const connectedProviders = providers.filter((p) => p.connected)

  const handleConnect = (provider: Provider) => {
    setSelectedProvider(provider)
    setConnectDialogOpen(true)
  }

  const handleConnectSubmit = (apiKey: string) => {
    if (selectedProvider) {
      connectProvider(selectedProvider.id, apiKey)
      toast.success(`已连接 ${selectedProvider.name}`)
      setConnectDialogOpen(false)
      setSelectedProvider(null)
    }
  }

  const handleDisconnect = (providerId: string) => {
    const provider = providers.find((p) => p.id === providerId)
    disconnectProvider(providerId)
    toast.success(`已断开 ${provider?.name}`)
  }

  const handleAddCustom = (provider: Provider) => {
    addCustomProvider(provider)
    toast.success(`已添加自定义提供商 ${provider.name}`)
    setCustomDialogOpen(false)
  }

  return (
    <div className="flex flex-col gap-6 max-w-2xl">
      {/* Connected Section */}
      <div className="flex flex-col gap-2">
        <h2 className="text-sm font-medium text-foreground">Connected</h2>
        <Card>
          <CardContent className="px-4">
            {connectedProviders.length === 0 ? (
              <div className="py-4 text-sm text-muted-foreground">暂无已连接的提供商</div>
            ) : (
              connectedProviders.map((provider) => (
                <ProviderItem
                  key={provider.id}
                  provider={provider}
                  onDisconnect={() => handleDisconnect(provider.id)}
                />
              ))
            )}
          </CardContent>
        </Card>
      </div>

      {/* Popular Providers Section */}
      <div className="flex flex-col gap-2">
        <h2 className="text-sm font-medium text-foreground">Popular Providers</h2>
        <Card>
          <CardContent className="px-4">
            {providers
              .filter((p) => !p.connected && p.type === 'builtin')
              .sort((a, b) => {
                const aIdx = POPULAR_PROVIDER_ORDER.indexOf(a.id)
                const bIdx = POPULAR_PROVIDER_ORDER.indexOf(b.id)
                return aIdx - bIdx
              })
              .map((provider) => (
                <ProviderItem
                  key={provider.id}
                  provider={provider}
                  onConnect={() => handleConnect(provider)}
                />
              ))}

            {/* Custom Provider Entry */}
            <div className="flex items-center justify-between gap-4 py-3 border-b border-border last:border-none">
              <div className="flex flex-col min-w-0 gap-1">
                <div className="flex items-center gap-3">
                  <ProviderIcon id="custom" />
                  <span className="text-sm font-medium">Custom Provider</span>
                  <Badge variant="secondary" className="text-xs">Custom</Badge>
                </div>
                <span className="text-xs text-muted-foreground pl-8">
                  添加自定义 API 端点
                </span>
              </div>
              <Button variant="secondary" size="sm" onClick={() => setCustomDialogOpen(true)}>
                + Connect
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Connect Dialog */}
      {selectedProvider && (
        <ConnectProviderDialog
          open={connectDialogOpen}
          onOpenChange={setConnectDialogOpen}
          provider={selectedProvider}
          onSubmit={handleConnectSubmit}
        />
      )}

      {/* Custom Provider Dialog */}
      <CustomProviderDialog
        open={customDialogOpen}
        onOpenChange={setCustomDialogOpen}
        onSubmit={handleAddCustom}
      />
    </div>
  )
}
