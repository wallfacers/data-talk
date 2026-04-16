import { useState } from 'react'
import { toast } from 'sonner'
import { Card, CardContent } from '@/components/ui/card'
import { ProviderItem } from './provider-item'
import { ConnectProviderDialog } from './connect-provider-dialog'
import { CustomProviderForm } from './custom-provider-form'
import { useModelConfigStore } from './store'
import { sortByProviderOrder } from './mock-data'
import type { Provider } from './types'

export function ProvidersPanel() {
  const providers = useModelConfigStore((s) => s.providers)
  const connectProvider = useModelConfigStore((s) => s.connectProvider)
  const disconnectProvider = useModelConfigStore((s) => s.disconnectProvider)
  const addCustomProvider = useModelConfigStore((s) => s.addCustomProvider)

  const [connectDialogOpen, setConnectDialogOpen] = useState(false)
  const [selectedProvider, setSelectedProvider] = useState<Provider | null>(null)

  const connectedProviders = providers.filter((p) => p.connected)

  const unconnectedProviders = sortByProviderOrder(
    providers.filter((p) => !p.connected && p.type === 'builtin')
  )

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
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <h2 className="text-sm font-medium text-foreground">已连接</h2>
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

      <div className="flex flex-col gap-2">
        <h2 className="text-sm font-medium text-foreground">可用提供商</h2>
        <Card>
          <CardContent className="px-4">
            {unconnectedProviders.map((provider) => (
              <ProviderItem
                key={provider.id}
                provider={provider}
                onConnect={() => handleConnect(provider)}
              />
            ))}

            {/* Custom Provider inline form */}
            <CustomProviderForm onSubmit={handleAddCustom} />
          </CardContent>
        </Card>
      </div>

      {selectedProvider && (
        <ConnectProviderDialog
          open={connectDialogOpen}
          onOpenChange={setConnectDialogOpen}
          provider={selectedProvider}
          onSubmit={handleConnectSubmit}
        />
      )}
    </div>
  )
}
