import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { PlusIcon, RotateCwIcon } from 'lucide-react'
import { fetchProviders, aiQueryKeys } from '../shared/api'
import { ProviderIcon } from '../shared/provider-icon'
import { RECOMMENDED_PROVIDERS, PROVIDER_DESCRIPTIONS } from '../shared/recommended-providers'
import { ConnectPanel } from './connect-dialog'

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
  if (error) return (
    <div className="rounded border border-dashed p-8 text-center text-sm text-red-600">
      OpenCode 服务未连接，请检查后端 / OpenCode 进程后重试
    </div>
  )

  return (
    <div className="max-w-3xl">
      <h1 className="mb-6 text-2xl font-semibold">提供商</h1>

      {connecting && (
        <div className="mb-6">
          <ConnectPanel
            providerId={connecting.id}
            providerName={connecting.name}
            onCancel={() => setConnecting(null)}
            onSaved={() => setConnecting(null)}
          />
        </div>
      )}

      <Section title="已连接的提供商" empty="没有已连接的提供商">
        {connected.map(p => <Row key={p.id} p={p} action="reconfigure" onClick={() => setConnecting(p)} />)}
      </Section>

      <Section title="热门提供商">
        {popular.map(p => <Row key={p.id} p={p} action="connect" onClick={() => setConnecting(p)} />)}
      </Section>
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
