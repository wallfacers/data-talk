import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ArrowLeftIcon, PlusIcon, RotateCwIcon } from 'lucide-react'
import { fetchProviders, fetchProviderAuth, putCredentials, aiQueryKeys } from '../shared/api'
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
        {connected.map(p => <Row key={p.id} p={p} action="reconfigure" onClick={() => setConnecting(p)} />)}
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
  const { data: auth } = useQuery({
    queryKey: aiQueryKeys.providerAuth,
    queryFn: fetchProviderAuth,
  })
  const [apiKey, setApiKey] = useState('')
  const [baseUrl, setBaseUrl] = useState('')

  const methods = auth?.[provider.id] ?? []
  const hasApi = methods.some(m => m.type === 'api')
  const hasOauth = methods.some(m => m.type === 'oauth')
  const desc = PROVIDER_DESCRIPTIONS[provider.id] || '使用 API 密钥连接此服务'

  const save = useMutation({
    mutationFn: async () => {
      const payload: any = { type: 'api', key: apiKey }
      if (baseUrl.trim()) payload.baseURL = baseUrl.trim()
      await putCredentials(provider.id, payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: aiQueryKeys.providers })
      qc.invalidateQueries({ queryKey: aiQueryKeys.models })
      toast.success('已保存凭证')
      onSaved()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  return (
    <div className="max-w-3xl">
      <button
        className="mb-4 flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        onClick={onBack}
      >
        <ArrowLeftIcon className="size-4" />
        返回提供商列表
      </button>

      <div className="rounded-lg border bg-card p-6">
        <div className="mb-6 flex items-center gap-3">
          <ProviderIcon id={provider.id} className="size-8" />
          <div>
            <h2 className="text-lg font-medium">连接 {provider.name}</h2>
            <p className="text-sm text-muted-foreground">{desc}</p>
          </div>
        </div>

        {hasOauth && !hasApi && (
          <div className="mb-4 rounded border border-dashed p-4 text-sm text-muted-foreground">
            此提供商仅支持 OAuth 登录。请在终端运行：
            <pre className="mt-2 rounded bg-muted p-2 text-xs">opencode auth login {provider.id}</pre>
          </div>
        )}

        {hasApi && (
          <div className="grid gap-4">
            <div>
              <Label>API Key</Label>
              <Input
                type="password"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder="输入你的 API 密钥"
                autoFocus
              />
            </div>
            <div>
              <Label>Base URL（可选）</Label>
              <Input
                value={baseUrl}
                placeholder="自定义或兼容网关地址"
                onChange={(e) => setBaseUrl(e.target.value)}
              />
            </div>
            {hasOauth && (
              <p className="text-xs text-muted-foreground">
                如需使用 OAuth 登录，请在 CLI 运行 <code>opencode auth login {provider.id}</code>
              </p>
            )}
            <p className="text-xs text-muted-foreground">
              注：凭证存储在 OpenCode（<code>~/.local/share/opencode/auth.json</code>）。
              如需移除请编辑该文件或使用 opencode CLI。
            </p>
          </div>
        )}

        <div className="mt-6 flex justify-end gap-2">
          <Button variant="ghost" onClick={onBack}>取消</Button>
          <Button
            onClick={() => save.mutate()}
            disabled={!hasApi || apiKey.length === 0 || save.isPending}
          >
            {save.isPending ? '保存中…' : '保存'}
          </Button>
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

function Row({ p, action, onClick }: { p: RawProvider; action: 'connect' | 'reconfigure'; onClick: () => void }) {
  const recommended = RECOMMENDED_PROVIDERS.has(p.id)
  const desc = PROVIDER_DESCRIPTIONS[p.id] ?? '使用 API 密钥连接'
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
      <Button size="sm" variant="outline" onClick={onClick}>
        {action === 'connect' ? <><PlusIcon className="size-4" />连接</>
                               : <><RotateCwIcon className="size-4" />重新配置</>}
      </Button>
    </div>
  )
}
