import { useState } from 'react'
import { PlusIcon, Trash2Icon, ChevronDownIcon, ChevronUpIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PasswordInput } from '@/components/ui/password-input'
import { ProviderIcon } from './provider-icon'
import type { Provider } from './types'

interface ModelEntry {
  id: string
  name: string
}

interface HeaderEntry {
  key: string
  value: string
}

interface CustomProviderFormProps {
  onSubmit: (provider: Provider) => void
}

export function CustomProviderForm({ onSubmit }: CustomProviderFormProps) {
  const [open, setOpen] = useState(false)
  const [providerId, setProviderId] = useState('')
  const [name, setName] = useState('')
  const [baseURL, setBaseURL] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [models, setModels] = useState<ModelEntry[]>([{ id: '', name: '' }])
  const [headers, setHeaders] = useState<HeaderEntry[]>([])
  const [errors, setErrors] = useState<Record<string, string>>({})

  const validate = () => {
    const newErrors: Record<string, string> = {}
    if (!providerId) newErrors.providerId = '请输入 Provider ID'
    else if (!/^[\w-]+$/.test(providerId)) newErrors.providerId = '只能包含字母、数字、下划线、连字符'
    if (!name) newErrors.name = '请输入名称'
    if (!baseURL) newErrors.baseURL = '请输入 Base URL'
    const validModels = models.filter((m) => m.id && m.name)
    if (validModels.length === 0) newErrors.models = '请至少添加一个模型'
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!validate()) return

    onSubmit({
      id: providerId,
      name,
      type: 'custom',
      baseURL,
      apiKey: apiKey || undefined,
      models: models.filter((m) => m.id && m.name).map((m) => ({
        id: m.id,
        name: m.name,
        providerId,
        visible: true,
      })),
      headers: headers.length > 0 ? Object.fromEntries(headers.filter((h) => h.key).map((h) => [h.key, h.value])) : undefined,
      connected: true,
      source: 'api',
    })
    resetForm()
    setOpen(false)
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

  const addModel = () => setModels([...models, { id: '', name: '' }])
  const removeModel = (i: number) => { if (models.length > 1) setModels(models.filter((_, j) => j !== i)) }
  const updateModel = (i: number, field: 'id' | 'name', value: string) =>
    setModels(models.map((m, j) => (j === i ? { ...m, [field]: value } : m)))

  const addHeader = () => setHeaders([...headers, { key: '', value: '' }])
  const removeHeader = (i: number) => setHeaders(headers.filter((_, j) => j !== i))
  const updateHeader = (i: number, field: 'key' | 'value', value: string) =>
    setHeaders(headers.map((h, j) => (j === i ? { ...h, [field]: value } : h)))

  const ErrorText = ({ field }: { field: string }) =>
    errors[field] ? <span className="text-xs text-destructive">{errors[field]}</span> : null

  return (
    <div className="border-t border-border mt-3 pt-3">
      <button
        type="button"
        onClick={() => { if (!open) resetForm(); setOpen(!open) }}
        className="flex w-full items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
      >
        <ProviderIcon id="custom" />
        <span>Custom Provider</span>
        {open ? <ChevronUpIcon className="size-3.5 ml-auto" /> : <ChevronDownIcon className="size-3.5 ml-auto" />}
      </button>

      {open && (
        <form onSubmit={handleSubmit} className="mt-3 space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div className="flex flex-col gap-1">
              <Label className="text-xs">Provider ID</Label>
              <Input
                value={providerId}
                onChange={(e) => { setProviderId(e.target.value); setErrors((s) => ({ ...s, providerId: '' })) }}
                placeholder="my-custom-provider"
              />
              <ErrorText field="providerId" />
            </div>
            <div className="flex flex-col gap-1">
              <Label className="text-xs">名称</Label>
              <Input
                value={name}
                onChange={(e) => { setName(e.target.value); setErrors((s) => ({ ...s, name: '' })) }}
                placeholder="My Custom Provider"
              />
              <ErrorText field="name" />
            </div>
          </div>

          <div className="flex flex-col gap-1">
            <Label className="text-xs">Base URL</Label>
            <Input
              value={baseURL}
              onChange={(e) => { setBaseURL(e.target.value); setErrors((s) => ({ ...s, baseURL: '' })) }}
              placeholder="https://api.example.com/v1"
            />
            <ErrorText field="baseURL" />
          </div>

          <div className="flex flex-col gap-1">
            <Label className="text-xs">API Key（可选）</Label>
            <PasswordInput
              value={apiKey}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setApiKey(e.target.value)}
              placeholder="your-api-key"
            />
          </div>

          {/* Models */}
          <div className="flex flex-col gap-1.5">
            <Label className="text-xs">模型列表</Label>
            {models.map((model, i) => (
              <div key={i} className="flex gap-2 items-center">
                <Input value={model.id} onChange={(e) => updateModel(i, 'id', e.target.value)} placeholder="model-id" className="flex-1" />
                <Input value={model.name} onChange={(e) => updateModel(i, 'name', e.target.value)} placeholder="Model Name" className="flex-1" />
                <button type="button" onClick={() => removeModel(i)} disabled={models.length <= 1} className="size-7 flex items-center justify-center text-muted-foreground hover:text-foreground disabled:opacity-50">
                  <Trash2Icon className="size-3.5" />
                </button>
              </div>
            ))}
            <Button type="button" variant="ghost" size="sm" onClick={addModel} className="self-start h-6 px-1 text-xs">
              <PlusIcon className="size-3" />
              Add Model
            </Button>
            <ErrorText field="models" />
          </div>

          {/* Headers */}
          {headers.length > 0 && (
            <div className="flex flex-col gap-1.5">
              <Label className="text-xs">请求头（可选）</Label>
              {headers.map((header, i) => (
                <div key={i} className="flex gap-2 items-center">
                  <Input value={header.key} onChange={(e) => updateHeader(i, 'key', e.target.value)} placeholder="Header-Key" className="flex-1" />
                  <Input value={header.value} onChange={(e) => updateHeader(i, 'value', e.target.value)} placeholder="Header-Value" className="flex-1" />
                  <button type="button" onClick={() => removeHeader(i)} className="size-7 flex items-center justify-center text-muted-foreground hover:text-foreground">
                    <Trash2Icon className="size-3.5" />
                  </button>
                </div>
              ))}
            </div>
          )}
          {headers.length < 3 && (
            <Button type="button" variant="ghost" size="sm" onClick={addHeader} className="self-start h-6 px-1 text-xs">
              <PlusIcon className="size-3" />
              Add Header
            </Button>
          )}

          <Button type="submit" className="w-full mt-2">
            Connect
          </Button>
        </form>
      )}
    </div>
  )
}
