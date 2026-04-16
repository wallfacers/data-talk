import { useState, useMemo } from 'react'
import { SearchIcon, XIcon } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
import { ProviderIcon } from './provider-icon'
import { ModelItem } from './model-item'
import { useModelConfigStore } from './store'
import { sortByProviderOrder } from './mock-data'

export function ModelsPanel() {
  const providers = useModelConfigStore((s) => s.providers)
  const setModelVisibility = useModelConfigStore((s) => s.setModelVisibility)
  const [search, setSearch] = useState('')

  const filteredProviders = useMemo(() => {
    const query = search.toLowerCase().trim()
    if (!query) return sortByProviderOrder([...providers])

    return sortByProviderOrder(
      providers
        .map((p) => ({
          ...p,
          models: p.models.filter(
            (m) =>
              m.name.toLowerCase().includes(query) ||
              p.name.toLowerCase().includes(query) ||
              m.id.toLowerCase().includes(query)
          ),
        }))
        .filter((p) => p.models.length > 0)
    )
  }, [providers, search])

  const handleVisibilityChange = (providerId: string, modelId: string, visible: boolean) => {
    setModelVisibility(providerId, modelId, visible)
  }

  const clearSearch = () => setSearch('')

  return (
    <div className="flex flex-col gap-6 max-w-2xl h-full overflow-hidden">
      <div className="sticky top-0 z-10 bg-gradient-to-b from-background to-background/0 pb-2">
        <div className="relative flex items-center gap-2">
          <SearchIcon className="size-4 text-muted-foreground absolute left-3" />
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="搜索模型..."
            className="pl-9 pr-9"
          />
          {search && (
            <button
              onClick={clearSearch}
              className="size-4 text-muted-foreground absolute right-3 hover:text-foreground cursor-pointer"
            >
              <XIcon className="size-full" />
            </button>
          )}
        </div>
      </div>

      <div className="flex flex-col gap-4 overflow-y-auto">
        {filteredProviders.length === 0 ? (
          <div className="py-8 text-center text-sm text-muted-foreground">
            没有找到匹配的模型
          </div>
        ) : (
          filteredProviders.map((provider) => (
            <div key={provider.id} className="flex flex-col gap-2">
              <div className="flex items-center gap-2 pb-2">
                <ProviderIcon id={provider.id} />
                <span className="text-sm font-medium">{provider.name}</span>
              </div>
              <Card>
                <CardContent className="px-4">
                  {provider.models.map((model) => (
                    <ModelItem
                      key={model.id}
                      model={model}
                      onVisibilityChange={(visible) =>
                        handleVisibilityChange(provider.id, model.id, visible)
                      }
                    />
                  ))}
                </CardContent>
              </Card>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
