import { useLayoutEffect, useMemo, useState } from 'react'
import { SearchIcon } from 'lucide-react'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { ProviderIcon } from '@/features/settings/shared/provider-icon'
import type { ProviderDto } from '@/features/settings/shared/api'
import { filterProvidersBySearch, formatModelId, parseModelId, SETTINGS_DIALOG_DIMENSIONS } from '@/features/settings/shared/utils'
import { cn } from '@/lib/utils'
import { useSettingsDialogStore } from '@/features/settings/settings-dialog-store'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  providers: ProviderDto[]
  currentModelId: string | null
  onPick: (modelId: string) => void
}

export function ModelPickerDialog({ open, onOpenChange, providers, currentModelId, onPick }: Props) {
  const [q, setQ] = useState('')
  const [activeProviderId, setActiveProviderId] = useState<string | null>(null)

  const filteredProviders = useMemo(
    () => filterProvidersBySearch(providers, q, { enabledOnly: true }),
    [providers, q],
  )

  // 打开时：清空搜索并初始化 activeProviderId（useLayoutEffect 确保在渲染前执行）
  useLayoutEffect(() => {
    if (!open) return
    setQ('')
    const fromCurrent = currentModelId ? parseModelId(currentModelId)?.providerId ?? null : null
    const defaults = filterProvidersBySearch(providers, '', { enabledOnly: true })
    const fallback = defaults[0]?.id ?? null
    const next = defaults.find(p => p.id === fromCurrent)?.id ?? fallback
    setActiveProviderId(next)
  }, [open, currentModelId, providers])

  // 当前 provider 若被搜索过滤掉则自动修正为第一个（跳过 null，由初始化 effect 处理）
  useLayoutEffect(() => {
    if (!open) return
    if (!filteredProviders.length) return
    if (activeProviderId === null) return
    const activeProvider = filteredProviders.find(p => p.id === activeProviderId)
    if (!activeProvider) {
      setActiveProviderId(filteredProviders[0].id)
    }
  }, [open, filteredProviders, activeProviderId])

  const activeProvider = filteredProviders.find(p => p.id === activeProviderId) ?? null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className={`flex flex-col !p-0 overflow-hidden ${SETTINGS_DIALOG_DIMENSIONS}`}>
        <DialogHeader className="flex flex-row items-center justify-between gap-4 px-6 py-4 border-b">
          <DialogTitle className="text-lg font-medium">选择模型</DialogTitle>
          <div className="relative w-60">
            <SearchIcon className="absolute left-2 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-8 h-8"
              placeholder="搜索模型"
              value={q}
              onChange={e => setQ(e.target.value)}
            />
          </div>
        </DialogHeader>
        <div className="flex flex-1 overflow-hidden">
          <nav className="flex w-52 flex-col gap-1 border-r p-3 overflow-y-auto text-sm">
            {filteredProviders.map(p => (
              <button
                key={p.id}
                type="button"
                onClick={() => setActiveProviderId(p.id)}
                className={cn(
                  'flex w-full items-center gap-2 rounded px-2 py-1.5 hover:bg-accent text-left',
                  activeProviderId === p.id && 'bg-accent font-medium',
                )}
              >
                <ProviderIcon id={p.id} className="size-4" />
                <span className="truncate">{p.name}</span>
              </button>
            ))}
          </nav>
          <main className="flex-1 overflow-y-auto p-4">
            {activeProvider ? (
              <div className="flex flex-col gap-1">
                {activeProvider.models.map(m => {
                  const id = formatModelId(activeProvider.id, m.id)
                  const active = id === currentModelId
                  return (
                    <button
                      key={id}
                      type="button"
                      onClick={() => { onPick(id); onOpenChange(false) }}
                      className={cn(
                        'block w-full rounded px-3 py-2 text-left text-sm hover:bg-accent',
                        active && 'bg-accent',
                      )}
                    >
                      {m.name}
                    </button>
                  )
                })}
              </div>
            ) : q ? (
              <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
                没有匹配的模型
              </div>
            ) : (
              <div className="flex h-full flex-col items-center justify-center gap-3 text-center">
                <p className="text-sm text-muted-foreground">
                  尚未启用任何模型，请先在设置 → 模型中启用
                </p>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    onOpenChange(false)
                    useSettingsDialogStore.getState().openDialog('models')
                  }}
                >
                  前往设置
                </Button>
              </div>
            )}
          </main>
        </div>
      </DialogContent>
    </Dialog>
  )
}
