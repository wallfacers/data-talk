import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Switch } from '@/components/ui/switch'
import { ProviderIcon } from '../shared/provider-icon'
import {
  patchModelEnabled,
  aiQueryKeys,
  type ProviderDto,
} from '../shared/api'

export function ModelsGroup({ provider }: { provider: ProviderDto }) {
  const qc = useQueryClient()
  const toggle = useMutation({
    mutationFn: (v: { modelId: string; enabled: boolean }) =>
      patchModelEnabled(provider.id, v.modelId, v.enabled),
    onSuccess: () => qc.invalidateQueries({ queryKey: aiQueryKeys.models }),
  })
  return (
    <div className="mb-6">
      <div className="mb-2 flex items-center gap-2 text-sm font-medium">
        <ProviderIcon id={provider.id} className="size-4" />
        {provider.name}
      </div>
      <div className="divide-y rounded border">
        {provider.models.map((m) => (
          <div key={m.id} className="flex items-center justify-between p-3">
            <span className="text-sm">{m.name}</span>
            <Switch
              checked={m.enabled}
              onCheckedChange={(v) =>
                toggle.mutate({ modelId: m.id, enabled: v })
              }
            />
          </div>
        ))}
      </div>
    </div>
  )
}
