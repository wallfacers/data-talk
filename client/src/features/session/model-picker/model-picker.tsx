import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ChevronDownIcon } from 'lucide-react'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { ProviderIcon } from '@/features/settings/shared/provider-icon'
import { aiQueryKeys, fetchModels, getCurrentModel, setCurrentModel, type ProviderDto } from '@/features/settings/shared/api'
import { parseModelId } from '@/features/settings/shared/utils'
import { ModelPickerPopover } from './model-picker-popover'

export function ModelPicker() {
  const [open, setOpen] = useState(false)
  const qc = useQueryClient()
  const { data: models } = useQuery({ queryKey: aiQueryKeys.models, queryFn: fetchModels })
  const { data: current } = useQuery({ queryKey: aiQueryKeys.currentModel, queryFn: getCurrentModel })
  const mut = useMutation({
    mutationFn: (id: string | null) => setCurrentModel(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: aiQueryKeys.currentModel }),
  })

  const selected = resolveSelected(models?.providers ?? [], current?.modelId ?? null)

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        className="flex h-7 items-center gap-1.5 rounded px-2 text-xs hover:bg-accent/50"
      >
        {selected ? (
          <>
            <ProviderIcon id={selected.providerId} className="size-3.5" />
            <span className="max-w-[140px] truncate">{selected.modelName}</span>
          </>
        ) : (
          <span className="text-muted-foreground">选择模型</span>
        )}
        <ChevronDownIcon className="size-3" />
      </PopoverTrigger>
      <PopoverContent className="p-0">
        <ModelPickerPopover
          providers={models?.providers ?? []}
          currentModelId={current?.modelId ?? null}
          onPick={(id) => { mut.mutate(id); setOpen(false) }}
        />
      </PopoverContent>
    </Popover>
  )
}

function resolveSelected(providers: ProviderDto[], modelId: string | null) {
  if (!modelId) return null
  const parsed = parseModelId(modelId)
  if (!parsed) return null
  const p = providers.find(x => x.id === parsed.providerId && x.connected)
  const m = p?.models.find(x => x.id === parsed.modelId && x.enabled)
  return p && m ? { providerId: p.id, modelName: m.name } : null
}
