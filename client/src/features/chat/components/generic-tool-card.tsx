import { cn } from '@/lib/utils'
import { Badge } from '@/components/ui/badge'
import type { ActionDescriptor } from '@/features/actions/registry'

type PartStatus = 'pending' | 'running' | 'completed' | 'error'

interface PartState {
  status?: PartStatus
}

interface PartWithState {
  state?: PartState
}

const statusStyles: Record<PartStatus, string> = {
  pending: 'bg-gray-200 text-gray-800',
  running: 'bg-blue-200 text-blue-800',
  completed: 'bg-green-200 text-green-800',
  error: 'bg-red-200 text-red-800',
}

export function GenericToolCard({ part, descriptor }: { part: PartWithState; descriptor: ActionDescriptor }) {
  const status = (part.state?.status ?? 'pending') as PartStatus
  return (
    <div className="my-2 rounded border bg-background p-2 text-xs">
      <div className="flex items-center gap-2">
        <Badge variant="outline" className={cn('rounded', statusStyles[status])}>{status}</Badge>
        <span className="font-mono">{descriptor.id}</span>
      </div>
      <div className="mt-1 text-muted-foreground">{descriptor.description}</div>
    </div>
  )
}
