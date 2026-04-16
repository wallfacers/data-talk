import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ProviderIcon } from './provider-icon'
import type { Provider } from './types'

interface ProviderItemProps {
  provider: Provider
  onConnect?: () => void
  onDisconnect?: () => void
}

export function ProviderItem({ provider, onConnect, onDisconnect }: ProviderItemProps) {
  const sourceLabel = () => {
    if (provider.source === 'env') return 'Environment'
    if (provider.source === 'api') return 'API Key'
    if (provider.type === 'custom') return 'Custom'
    return null
  }

  return (
    <div className="flex items-center justify-between gap-4 py-3 border-b border-border last:border-none">
      <div className="flex items-center gap-3 min-w-0">
        <ProviderIcon id={provider.id} />
        <span className="text-sm font-medium truncate">{provider.name}</span>
        {sourceLabel() && (
          <Badge variant="secondary" className="text-xs">
            {sourceLabel()}
          </Badge>
        )}
      </div>
      {provider.connected ? (
        onDisconnect ? (
          <Button variant="ghost" size="sm" onClick={onDisconnect}>
            Disconnect
          </Button>
        ) : (
          <span className="text-sm text-muted-foreground">System config</span>
        )
      ) : (
        onConnect && (
          <Button variant="secondary" size="sm" onClick={onConnect}>
            + Connect
          </Button>
        )
      )}
    </div>
  )
}
