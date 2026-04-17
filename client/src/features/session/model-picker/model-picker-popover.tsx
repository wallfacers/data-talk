import { useMemo, useState } from 'react'
import { Input } from '@/components/ui/input'
import { ProviderIcon } from '@/features/settings/shared/provider-icon'
import type { ProviderDto } from '@/features/settings/shared/api'
import { cn } from '@/lib/utils'

type Props = {
  providers: ProviderDto[]
  currentModelId: string | null
  onPick: (modelId: string) => void
}

export function ModelPickerPopover({ providers, currentModelId, onPick }: Props) {
  const [q, setQ] = useState('')
  const groups = useMemo(() => {
    const needle = q.trim().toLowerCase()
    return providers.filter(p => p.connected)
      .map(p => ({
        ...p,
        models: p.models
          .filter(m => m.enabled)
          .filter(m => !needle || m.name.toLowerCase().includes(needle) || m.id.toLowerCase().includes(needle))
      }))
      .filter(p => p.models.length > 0)
  }, [providers, q])

  return (
    <div className="w-[280px]">
      <div className="border-b p-2">
        <Input className="h-7" placeholder="搜索模型"
          value={q} onChange={(e) => setQ(e.target.value)} />
      </div>
      <div className="max-h-[320px] overflow-y-auto p-1">
        {groups.length === 0 && (
          <div className="p-3 text-center text-xs text-muted-foreground">
            {providers.length === 0 ? '请先在设置中配置提供商' : '没有匹配的模型'}
          </div>
        )}
        {groups.map(p => (
          <div key={p.id} className="mb-2">
            <div className="flex items-center gap-1.5 px-2 py-1 text-xs text-muted-foreground">
              <ProviderIcon id={p.id} className="size-3.5" />{p.name}
            </div>
            {p.models.map(m => {
              const id = `${p.id}/${m.id}`
              const active = id === currentModelId
              return (
                <button key={id} type="button" onClick={() => onPick(id)}
                  className={cn(
                    'block w-full rounded px-2 py-1 text-left text-sm hover:bg-accent',
                    active && 'bg-accent'
                  )}>{m.name}</button>
              )
            })}
          </div>
        ))}
      </div>
    </div>
  )
}
