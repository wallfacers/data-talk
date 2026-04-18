import { useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  aiQueryKeys,
  fetchModels,
  getCurrentModel,
  setCurrentModel,
  type ProviderDto,
} from '@/features/settings/shared/api'
import { formatModelId } from '@/features/settings/shared/utils'

export function useAutoSelectDefaultModel() {
  const qc = useQueryClient()
  const fired = useRef(false)

  const { data: modelsData, isSuccess: modelsLoaded } = useQuery({
    queryKey: aiQueryKeys.models,
    queryFn: fetchModels,
    staleTime: Infinity,
  })
  const { data: current, isSuccess: currentLoaded } = useQuery({
    queryKey: aiQueryKeys.currentModel,
    queryFn: getCurrentModel,
    staleTime: Infinity,
  })

  const mut = useMutation({
    mutationFn: (id: string) => setCurrentModel(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: aiQueryKeys.currentModel }),
  })

  useEffect(() => {
    if (fired.current) return
    if (!modelsLoaded || !currentLoaded) return
    if (current?.modelId) {
      fired.current = true
      return
    }
    const picked = pickFirstAvailable(modelsData?.providers ?? [])
    if (!picked) return
    fired.current = true
    mut.mutate(picked)
  }, [modelsLoaded, currentLoaded, current?.modelId, modelsData?.providers, mut])
}

function pickFirstAvailable(providers: ProviderDto[]): string | null {
  for (const p of providers) {
    if (!p.connected) continue
    const m = p.models.find((x) => x.enabled)
    if (m) return formatModelId(p.id, m.id)
  }
  return null
}
