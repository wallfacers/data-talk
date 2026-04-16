import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import type { Model } from './types'

interface ModelItemProps {
  model: Model
  onVisibilityChange?: (visible: boolean) => void
}

export function ModelItem({ model, onVisibilityChange }: ModelItemProps) {
  return (
    <div className="flex items-center justify-between gap-4 py-3 border-b border-border last:border-none">
      <div className="flex items-center gap-2 min-w-0">
        <span className="text-sm truncate">{model.name}</span>
        {model.latest && (
          <Badge variant="secondary" className="text-xs">
            Latest
          </Badge>
        )}
      </div>
      <Switch checked={model.visible} onCheckedChange={onVisibilityChange} />
    </div>
  )
}
