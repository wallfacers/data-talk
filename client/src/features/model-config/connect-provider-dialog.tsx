import { useState } from 'react'
import { EyeIcon, EyeOffIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { ProviderIcon } from './provider-icon'
import type { Provider } from './types'

interface ConnectProviderDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  provider: Provider
  onSubmit: (apiKey: string) => void
}

export function ConnectProviderDialog({
  open,
  onOpenChange,
  provider,
  onSubmit,
}: ConnectProviderDialogProps) {
  const [apiKey, setApiKey] = useState('')
  const [showKey, setShowKey] = useState(false)
  const [error, setError] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!apiKey.trim()) {
      setError('请输入 API Key')
      return
    }
    onSubmit(apiKey.trim())
    setApiKey('')
    setError('')
  }

  const handleOpenChange = (open: boolean) => {
    if (!open) {
      setApiKey('')
      setError('')
    }
    onOpenChange(open)
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ProviderIcon id={provider.id} />
            Connect {provider.name}
          </DialogTitle>
          <DialogDescription>
            输入您的 {provider.name} API Key 以连接服务。
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="api-key">API Key</Label>
            <div className="relative">
              <Input
                id="api-key"
                type={showKey ? 'text' : 'password'}
                value={apiKey}
                onChange={(e) => {
                  setApiKey(e.target.value)
                  setError('')
                }}
                placeholder="sk-..."
                className="pr-10"
              />
              <button
                type="button"
                onClick={() => setShowKey(!showKey)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              >
                {showKey ? <EyeOffIcon className="size-4" /> : <EyeIcon className="size-4" />}
              </button>
            </div>
            {error && <span className="text-xs text-destructive">{error}</span>}
          </div>

          <p className="text-xs text-muted-foreground">
            您的 API Key 将安全存储在系统 keyring 中。
          </p>

          <Button type="submit" className="w-full">
            Connect
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  )
}
