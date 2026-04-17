import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ArrowLeftIcon, PlusIcon, RotateCwIcon, Trash2Icon } from 'lucide-react'
import { fetchProviders, fetchProviderAuth, putCredentials, deleteCredentials, aiQueryKeys } from '../shared/api'
import { ProviderIcon } from '../shared/provider-icon'
import { RECOMMENDED_PROVIDERS, PROVIDER_DESCRIPTIONS } from '../shared/recommended-providers'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

type RawProvider = { id: string; name: string }

export function ProvidersPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: aiQueryKeys.providers,
    queryFn: fetchProviders,
  })
  const [connecting, setConnecting] = useState<{ id: string; name: string } | null>(null)

  const { connected, popular } = useMemo(() => {
    if (!data) return { connected: [], popular: [] }
    const connSet = new Set<string>(data.connected ?? [])
    const all: RawProvider[] = data.all ?? []
    return {
      connected: all.filter(p => connSet.has(p.id)),
      popular: all.filter(p => !connSet.has(p.id)),
    }
  }, [data])

  if (isLoading) return <div className="text-sm text-muted-foreground">加载中...</div>

  if (connecting) {
    return (
      <ConnectPage
        provider={connecting}
        onBack={() => setConnecting(null)}
        onSaved={() => setConnecting(null)}
      />
    )
  }

  if (error) return (
    <div className="rounded border border-dashed p-8 text-center text-sm text-red-600">
      OpenCode 服务未连接，请检查后端 / OpenCode 进程后重试
    </div>
  )

  return (
    <div className="max-w-3xl">
      <h1 className="mb-6 text-2xl font-semibold">提供商</h1>

      <Section title="已连接的提供商" empty="没有已连接的提供商">
        {connected.map(p => (
          <Row key={p.id} p={p} action="reconfigure" onClick={() => setConnecting(p)} onRemove={p.id} />
        ))}
      </Section>

      <Section title="热门提供商">
        {popular.map(p => <Row key={p.id} p={p} action="connect" onClick={() => setConnecting(p)} />)}
      </Section>
    </div>
  )
}

function ConnectPage({ provider, onBack, onSaved }: {
  provider: { id: string; name: string }
  onBack: () => void
  onSaved: () => void
}) {
  const qc = useQueryClient()
  const { data: auth } = useQuery<Record<string, { type: string; label?: string }[]>>({
    queryKey: aiQueryKeys.providerAuth,
    queryFn: fetchProviderAuth,
  })
  const [apiKey, setApiKey] = useState('')
  const [baseUrl, setBaseUrl] = useState('')

  const methods = auth?.[provider.id] ?? []
  const hasApi = methods.some(m => m.type === 'api') || methods.length === 0
  const hasOauth = methods.some(m => m.type === 'oauth')
  const desc = PROVIDER_DESCRIPTIONS[provider.id] || '使用 API 密钥连接此服务'

  const save = useMutation({
    mutationFn: async () => {
      const payload: any = { type: 'api', key: apiKey }
      if (baseUrl.trim()) payload.baseURL = baseUrl.trim()
      await putCredentials(provider.id, payload)
    },
    onSuccess: () => {
      // 乐观更新：把刚保存的 provider 加入已连接列表
      qc.setQueryData(aiQueryKeys.providers, (old: any) => {
        if (!old) return old
        const existing = old.connected ?? []
        if (existing.includes(provider.id)) return old
        return { ...old, connected: [...existing, provider.id] }
      })
      qc.invalidateQueries({ queryKey: aiQueryKeys.models })
      toast.success('已保存凭证')
      onSaved()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  return (
    <div className="max-w-2xl">
      <button
        className="mb-6 flex items-center gap-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground"
        onClick={onBack}
      >
        <ArrowLeftIcon className="size-3.5" />
        返回提供商列表
      </button>

      <div className="overflow-hidden rounded-xl border bg-card shadow-sm">
        {/* 头部 */}
        <div className="border-b bg-muted/40 px-6 py-5">
          <div className="flex items-center gap-3">
            <ProviderIcon id={provider.id} className="size-9" />
            <div>
              <h2 className="text-base font-medium">连接 {provider.name}</h2>
              <p className="mt-0.5 text-sm text-muted-foreground">{desc}</p>
            </div>
          </div>
        </div>

        {hasOauth && !hasApi && (
          <div className="mx-6 mt-4 rounded-lg border border-dashed border-border/60 bg-muted/30 px-4 py-3 text-sm text-muted-foreground">
            此提供商仅支持 OAuth 登录。请在终端运行：
            <pre className="mt-2 rounded-md bg-muted px-3 py-1.5 text-xs font-mono">opencode auth login {provider.id}</pre>
          </div>
        )}

        {hasApi && (
          <div className="px-6 py-6">
            <div className="space-y-5">
              <div className="space-y-2">
                <Label className="text-sm font-medium">API Key</Label>
                <Input
                  type="password"
                  value={apiKey}
                  onChange={(e) => setApiKey(e.target.value)}
                  placeholder="输入你的 API 密钥"
                  className="font-mono"
                  autoFocus
                />
              </div>
              <div className="space-y-2">
                <Label className="text-sm font-medium">Base URL<span className="text-muted-foreground font-normal">（可选）</span></Label>
                <Input
                  value={baseUrl}
                  placeholder="https://api.example.com"
                  onChange={(e) => setBaseUrl(e.target.value)}
                  className="font-mono"
                />
              </div>
            </div>

            {hasOauth && (
              <p className="mt-5 text-xs text-muted-foreground">
                如需使用 OAuth 登录，请在 CLI 运行 <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-[11px]">opencode auth login {provider.id}</code>
              </p>
            )}
          </div>
        )}

        {/* 底部操作栏 */}
        <div className="flex items-center justify-between border-t bg-muted/20 px-6 py-4">
          <p className="text-[11px] text-muted-foreground/70 leading-relaxed max-w-sm">
            凭证存储在 OpenCode <code className="rounded bg-muted/50 px-1 font-mono text-[10px]">auth.json</code>
          </p>
          <div className="flex gap-2">
            <Button variant="ghost" size="sm" onClick={onBack}>取消</Button>
            <Button
              size="sm"
              onClick={() => save.mutate()}
              disabled={!hasApi || apiKey.length === 0 || save.isPending}
            >
              {save.isPending ? '保存中…' : '保存'}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

function Section({ title, empty, children }: { title: string; empty?: string; children: React.ReactNode }) {
  const isEmpty = !Array.isArray(children) || (children as any[]).length === 0
  return (
    <section className="mb-8">
      <h2 className="mb-3 text-sm font-medium">{title}</h2>
      {isEmpty && empty ? (
        <div className="rounded border border-dashed p-6 text-center text-sm text-muted-foreground">
          {empty}
        </div>
      ) : <div className="divide-y rounded border">{children}</div>}
    </section>
  )
}

function Row({ p, action, onClick, onRemove }: {
  p: RawProvider
  action: 'connect' | 'reconfigure'
  onClick: () => void
  onRemove?: string
}) {
  const qc = useQueryClient()
  const recommended = RECOMMENDED_PROVIDERS.has(p.id)
  const desc = PROVIDER_DESCRIPTIONS[p.id] ?? '使用 API 密钥连接'

  const remove = useMutation({
    mutationFn: () => deleteCredentials(onRemove!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: aiQueryKeys.providers })
      qc.invalidateQueries({ queryKey: aiQueryKeys.models })
      toast.success('已移除凭证')
    },
    onError: (e: Error) => toast.error(e.message),
  })

  return (
    <div className="flex items-center gap-3 p-3">
      <ProviderIcon id={p.id} className="size-6" />
      <div className="flex-1">
        <div className="flex items-center gap-2 text-sm font-medium">
          {p.name}
          {recommended && <span className="rounded bg-accent px-1.5 py-0.5 text-xs">推荐</span>}
        </div>
        <div className="text-xs text-muted-foreground">{desc}</div>
      </div>
      <div className="flex items-center gap-2">
        <Button size="sm" variant="outline" onClick={onClick}>
          {action === 'connect' ? <><PlusIcon className="size-4" />连接</>
                                 : <><RotateCwIcon className="size-4" />重新配置</>}
        </Button>
        {onRemove && (
          <Button
            size="sm"
            variant="ghost"
            className="text-destructive hover:text-destructive hover:bg-destructive/10"
            onClick={() => remove.mutate()}
            disabled={remove.isPending}
          >
            <Trash2Icon className="size-4" />
          </Button>
        )}
      </div>
    </div>
  )
}
