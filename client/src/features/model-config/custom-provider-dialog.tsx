import { useState } from 'react'
import { PlusIcon, Trash2Icon, EyeIcon, EyeOffIcon } from 'lucide-react'
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

interface CustomProviderDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (provider: Provider) => void
}

interface ModelEntry {
  id: string
  name: string
}

interface HeaderEntry {
  key: string
  value: string
}

export function CustomProviderDialog({
  open,
  onOpenChange,
  onSubmit,
}: CustomProviderDialogProps) {
  const [providerId, setProviderId] = useState('')
  const [name, setName] = useState('')
  const [baseURL, setBaseURL] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [showKey, setShowKey] = useState(false)
  const [models, setModels] = useState<ModelEntry[]>([{ id: '', name: '' }])
  const [headers, setHeaders] = useState<HeaderEntry[]>([])
  const [errors, setErrors] = useState<Record<string, string>>({})

  const validateProviderId = (id: string) => {
    if (!id) return '请输入 Provider ID'
    if (!/^[\w-]+$/.test(id)) return '只能包含字母、数字、下划线、连字符'
    return ''
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()

    const newErrors: Record<string, string> = {}
    const idError = validateProviderId(providerId)
    if (idError) newErrors.providerId = idError
    if (!name) newErrors.name = '请输入名称'
    if (!baseURL) newErrors.baseURL = '请输入 Base URL'

    const validModels = models.filter((m) => m.id && m.name)
    if (validModels.length === 0) newErrors.models = '请至少添加一个模型'

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors)
      return
    }

    const provider: Provider = {
      id: providerId,
      name,
      type: 'custom',
      baseURL,
      apiKey: apiKey || undefined,
      models: validModels.map((m) => ({
        id: m.id,
        name: m.name,
        providerId,
        visible: true,
      })),
      headers: headers.length > 0
        ? headers.reduce((acc, h) => ({ ...acc, [h.key]: h.value }), {})
        : undefined,
      connected: true,
      source: 'api',
    }

    onSubmit(provider)
    resetForm()
  }

  const resetForm = () => {
    setProviderId('')
    setName('')
    setBaseURL('')
    setApiKey('')
    setModels([{ id: '', name: '' }])
    setHeaders([])
    setErrors({})
  }

  const handleOpenChange = (open: boolean) => {
    if (!open) resetForm()
    onOpenChange(open)
  }

  const addModel = () => setModels([...models, { id: '', name: '' }])
  const removeModel = (index: number) => {
    if (models.length > 1) setModels(models.filter((_, i) => i !== index))
  }
  const updateModel = (index: number, field: 'id' | 'name', value: string) => {
    setModels(models.map((m, i) => (i === index ? { ...m, [field]: value } : m)))
  }

  const addHeader = () => setHeaders([...headers, { key: '', value: '' }])
  const removeHeader = (index: number) => setHeaders(headers.filter((_, i) => i !== index))
  const updateHeader = (index: number, field: 'key' | 'value', value: string) => {
    setHeaders(headers.map((h, i) => (i === index ? { ...h, [field]: value } : h)))
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-lg max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ProviderIcon id="custom" />
            Custom Provider
          </DialogTitle>
          <DialogDescription>
            配置自定义 API 端点
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {/* Provider ID */}
          <div className="flex flex-col gap-2">
            <Label htmlFor="provider-id">Provider ID</Label>
            <Input
              id="provider-id"
              value={providerId}
              onChange={(e) => {
                setProviderId(e.target.value)
                setErrors((err) => ({ ...err, providerId: '' }))
              }}
              placeholder="my-custom-provider"
            />
            {errors.providerId && (
              <span className="text-xs text-destructive">{errors.providerId}</span>
            )}
            <span className="text-xs text-muted-foreground">唯一标识符</span>
          </div>

          {/* Name */}
          <div className="flex flex-col gap-2">
            <Label htmlFor="name">名称</Label>
            <Input
              id="name"
              value={name}
              onChange={(e) => {
                setName(e.target.value)
                setErrors((err) => ({ ...err, name: '' }))
              }}
              placeholder="My Custom Provider"
            />
            {errors.name && <span className="text-xs text-destructive">{errors.name}</span>}
          </div>

          {/* Base URL */}
          <div className="flex flex-col gap-2">
            <Label htmlFor="base-url">Base URL</Label>
            <Input
              id="base-url"
              value={baseURL}
              onChange={(e) => {
                setBaseURL(e.target.value)
                setErrors((err) => ({ ...err, baseURL: '' }))
              }}
              placeholder="https://api.example.com/v1"
            />
            {errors.baseURL && (
              <span className="text-xs text-destructive">{errors.baseURL}</span>
            )}
          </div>

          {/* API Key */}
          <div className="flex flex-col gap-2">
            <Label htmlFor="api-key">API Key（可选）</Label>
            <div className="relative">
              <Input
                id="api-key"
                type={showKey ? 'text' : 'password'}
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder="your-api-key"
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
            <span className="text-xs text-muted-foreground">
              如果端点无需认证可不填
            </span>
          </div>

          {/* Models */}
          <div className="flex flex-col gap-2">
            <Label>模型列表</Label>
            {models.map((model, index) => (
              <div key={index} className="flex gap-2 items-center">
                <Input
                  value={model.id}
                  onChange={(e) => updateModel(index, 'id', e.target.value)}
                  placeholder="model-id"
                  className="flex-1"
                />
                <Input
                  value={model.name}
                  onChange={(e) => updateModel(index, 'name', e.target.value)}
                  placeholder="Model Name"
                  className="flex-1"
                />
                <button
                  type="button"
                  onClick={() => removeModel(index)}
                  disabled={models.length <= 1}
                  className="size-8 flex items-center justify-center text-muted-foreground hover:text-foreground disabled:opacity-50"
                >
                  <Trash2Icon className="size-4" />
                </button>
              </div>
            ))}
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={addModel}
              className="self-start"
            >
              <PlusIcon className="size-4" />
              Add Model
            </Button>
            {errors.models && (
              <span className="text-xs text-destructive">{errors.models}</span>
            )}
          </div>

          {/* Headers */}
          <div className="flex flex-col gap-2">
            <Label>请求头（可选）</Label>
            {headers.map((header, index) => (
              <div key={index} className="flex gap-2 items-center">
                <Input
                  value={header.key}
                  onChange={(e) => updateHeader(index, 'key', e.target.value)}
                  placeholder="Header-Key"
                  className="flex-1"
                />
                <Input
                  value={header.value}
                  onChange={(e) => updateHeader(index, 'value', e.target.value)}
                  placeholder="Header-Value"
                  className="flex-1"
                />
                <button
                  type="button"
                  onClick={() => removeHeader(index)}
                  className="size-8 flex items-center justify-center text-muted-foreground hover:text-foreground"
                >
                  <Trash2Icon className="size-4" />
                </button>
              </div>
            ))}
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={addHeader}
              className="self-start"
            >
              <PlusIcon className="size-4" />
              Add Header
            </Button>
          </div>

          <Button type="submit" className="w-full mt-4">
            Connect
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  )
}
