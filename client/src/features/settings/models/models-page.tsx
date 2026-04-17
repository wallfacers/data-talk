import { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Input } from '@/components/ui/input'
import { SearchIcon } from 'lucide-react'
import { fetchModels, aiQueryKeys } from '../shared/api'
import { filterProvidersBySearch } from '../shared/utils'
import { ModelsGroup } from './models-group'

export function ModelsPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: aiQueryKeys.models,
    queryFn: fetchModels,
  })
  const [q, setQ] = useState('')

  const filtered = useMemo(() => {
    if (!data) return []
    return filterProvidersBySearch(data.providers, q)
  }, [data, q])

  if (isLoading)
    return <div className="text-sm text-muted-foreground">加载中...</div>
  if (error)
    return (
      <div className="rounded border border-dashed p-8 text-center text-sm text-red-600">
        OpenCode 服务未连接
      </div>
    )

  return (
    <div className="max-w-3xl">
      <h1 className="mb-6 text-2xl font-semibold">模型</h1>
      <div className="relative mb-4">
        <SearchIcon className="absolute left-2 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          className="pl-8"
          placeholder="搜索模型"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
      </div>
      {filtered.length === 0 ? (
        <div className="rounded border border-dashed p-8 text-center text-sm text-muted-foreground">
          {q ? '没有匹配的模型' : '尚未连接任何提供商'}
        </div>
      ) : (
        filtered.map((p) => <ModelsGroup key={p.id} provider={p} />)
      )}
    </div>
  )
}
