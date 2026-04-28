import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ArrowLeftIcon, PlusIcon, RotateCwIcon, Trash2Icon } from 'lucide-react'
import { fetchProviders, fetchProviderAuth, putCredentials, deleteCredentials, aiQueryKeys } from '../shared/api'
import { ProviderIcon } from '../shared/provider-icon'
import { RECOMMENDED_PROVIDERS, EXCLUDED_PROVIDERS, getProviderDescription } from '../shared/recommended-providers'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { useI18n } from '@/i18n/use-i18n'

type RawProvider = { id: string; name: string }

export function ProvidersPage() {
  const { t } = useI18n()
  const { data, isLoading, error } = useQuery({
    queryKey: aiQueryKeys.providers,
    queryFn: fetchProviders,
  })
  const [connecting, setConnecting] = useState<{ id: string; name: string } | null>(null)

  const [search, setSearch] = useState('')

  const { connected, popular } = useMemo(() => {
    if (!data) return { connected: [], popular: [] }
    const connSet = new Set<string>(data.connected ?? [])
    const all: RawProvider[] = data.all ?? []
    return {
      connected: all.filter(p => connSet.has(p.id)),
      popular: all
        .filter(p => !connSet.has(p.id) && !EXCLUDED_PROVIDERS.has(p.id))
        .filter(p => p.name.toLowerCase().includes(search.toLowerCase()))
        .sort((a, b) => a.name.localeCompare(b.name)),
    }
  }, [data, search])

  if (isLoading) return <div className="text-sm text-muted-foreground">{t('common.loading')}</div>

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
      {t('providers.unavailable')}
    </div>
  )

  return (
    <div className="max-w-3xl">
      <h1 className="mb-6 text-2xl font-semibold">{t('providers.title')}</h1>

      <Section title={t('providers.connected')} empty={t('providers.connectedEmpty')}>
        {connected.map(p => (
          <Row key={p.id} p={p} action="reconfigure" onClick={() => setConnecting(p)} onRemove={p.id} />
        ))}
      </Section>

      <Section title={t('providers.popular')} headerRight={
        <Input
          placeholder={t('providers.searchPlaceholder')}
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="h-7 w-48 text-sm"
        />
      }>
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
  const { t } = useI18n()
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
  const desc = getProviderDescription(provider.id)

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
      toast.success(t('providers.credentialSaved'))
      onSaved()
    },
  })

  return (
    <div className="max-w-2xl">
      <button
        className="mb-6 flex items-center gap-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing rounded"
        onClick={onBack}
      >
        <ArrowLeftIcon className="size-3.5" />
        {t('providers.backToList')}
      </button>

      <div className="overflow-hidden rounded-xl border bg-card shadow-sm">
        {/* 头部 */}
        <div className="border-b bg-muted/40 px-6 py-5">
          <div className="flex items-center gap-3">
            <ProviderIcon id={provider.id} className="size-9" />
            <div>
              <h2 className="text-base font-medium">{t('providers.connectTitle', { name: provider.name })}</h2>
              <p className="mt-0.5 text-sm text-muted-foreground">{desc}</p>
            </div>
          </div>
        </div>

        {hasOauth && !hasApi && (
          <div className="mx-6 mt-4 rounded-lg border border-dashed border-border/60 bg-muted/30 px-4 py-3 text-sm text-muted-foreground">
            {t('providers.oauthOnly')}
            <pre className="mt-2 rounded-md bg-muted px-3 py-1.5 text-xs font-mono">opencode auth login {provider.id}</pre>
          </div>
        )}

        {hasApi && (
          <div className="px-6 py-6">
            <div className="space-y-5">
              <div className="space-y-2">
                <Label className="text-sm font-medium">{t('providers.apiKey')}</Label>
                <Input
                  type="password"
                  value={apiKey}
                  onChange={(e) => setApiKey(e.target.value)}
                  placeholder={t('providers.apiKeyPlaceholder')}
                  className="font-mono"
                  autoFocus
                />
              </div>
              <div className="space-y-2">
                <Label className="text-sm font-medium">{t('providers.baseUrl')}<span className="text-muted-foreground font-normal">{t('providers.optional')}</span></Label>
                <Input
                  value={baseUrl}
                  placeholder={t('providers.baseUrlPlaceholder')}
                  onChange={(e) => setBaseUrl(e.target.value)}
                  className="font-mono"
                />
              </div>
            </div>

            {hasOauth && (
              <p className="mt-5 text-xs text-muted-foreground">
                {t('providers.oauthCliHint')} <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-[11px]">opencode auth login {provider.id}</code>
              </p>
            )}
          </div>
        )}

        {/* 底部操作栏 */}
        <div className="flex items-center justify-between border-t bg-muted/20 px-6 py-4">
          <p className="text-[11px] text-muted-foreground/70 leading-relaxed max-w-sm">
            {t('providers.authStoredIn', { file: 'auth.json' })}
          </p>
          <div className="flex gap-2">
            <Button variant="ghost" size="sm" onClick={onBack}>{t('common.cancel')}</Button>
            <Button
              size="sm"
              onClick={() => save.mutate()}
              disabled={!hasApi || apiKey.length === 0 || save.isPending}
            >
              {save.isPending ? t('common.saving') : t('common.save')}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

function Section({ title, empty, headerRight, children }: { title: string; empty?: string; headerRight?: React.ReactNode; children: React.ReactNode }) {
  const isEmpty = !Array.isArray(children) || (children as any[]).length === 0
  return (
    <section className="mb-8">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-medium">{title}</h2>
        {headerRight}
      </div>
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
  const { t } = useI18n()
  const qc = useQueryClient()
  const recommended = RECOMMENDED_PROVIDERS.has(p.id)
  const desc = getProviderDescription(p.id, 'providers.desc.defaultShort')

  const remove = useMutation({
    mutationFn: () => deleteCredentials(onRemove!),
    onSuccess: () => {
      // 乐观更新：从 connected 数组移除该 provider
      qc.setQueryData(aiQueryKeys.providers, (old: any) => {
        if (!old) return old
        const connected = (old.connected ?? []) as string[]
        if (!connected.includes(onRemove!)) return old
        return { ...old, connected: connected.filter(id => id !== onRemove) }
      })
      qc.invalidateQueries({ queryKey: aiQueryKeys.providers })
      qc.invalidateQueries({ queryKey: aiQueryKeys.models })
      toast.success(t('providers.credentialRemoved'))
    },
  })

  return (
    <div className="flex items-center gap-3 p-3">
      <ProviderIcon id={p.id} className="size-6" />
      <div className="flex-1">
        <div className="flex items-center gap-2 text-sm font-medium">
          {p.name}
          {recommended && <span className="rounded bg-accent px-1.5 py-0.5 text-xs">{t('providers.recommended')}</span>}
        </div>
        <div className="text-xs text-muted-foreground">{desc}</div>
      </div>
      <div className="flex items-center gap-2">
        <Button size="sm" variant="outline" onClick={onClick}>
          {action === 'connect' ? <><PlusIcon className="size-4" />{t('providers.connect')}</>
                                 : <><RotateCwIcon className="size-4" />{t('providers.reconfigure')}</>}
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
