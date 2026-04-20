import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ChevronDownIcon } from 'lucide-react'
import { ProviderIcon } from '@/features/settings/shared/provider-icon'
import { aiQueryKeys, fetchModels, getCurrentModel, setCurrentModel, type ProviderDto } from '@/features/settings/shared/api'
import { parseModelId } from '@/features/settings/shared/utils'
import { useI18n } from '@/i18n/use-i18n'
import { ModelPickerDialog } from './model-picker-dialog'

export function ModelPicker() {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const qc = useQueryClient()
  const { data: models } = useQuery({ queryKey: aiQueryKeys.models, queryFn: fetchModels })
  const { data: current } = useQuery({ queryKey: aiQueryKeys.currentModel, queryFn: getCurrentModel })
  const mut = useMutation({
    mutationFn: (id: string | null) => setCurrentModel(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: aiQueryKeys.currentModel }),
  })

  const selected = useMemo(
    () => resolveSelected(models?.providers ?? [], current?.modelId ?? null),
    [models?.providers, current?.modelId],
  )

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="flex h-7 items-center gap-1.5 rounded px-2 text-xs text-foreground hover:bg-accent/50"
      >
        {selected ? (
          <>
            <ProviderIcon id={selected.providerId} className="size-3.5" />
            <span className="max-w-[140px] truncate">{selected.modelName}</span>
          </>
        ) : (
          <span className="text-muted-foreground">{t('models.select')}</span>
        )}
        <ChevronDownIcon className="size-3" />
      </button>
      <ModelPickerDialog
        open={open}
        onOpenChange={setOpen}
        providers={models?.providers ?? []}
        currentModelId={current?.modelId ?? null}
        onPick={(id) => { mut.mutate(id); setOpen(false) }}
      />
    </>
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
