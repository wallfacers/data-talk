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
  pending: 'bg-muted text-muted-foreground',
  running: 'bg-blue-100 text-blue-800 dark:bg-blue-900/50 dark:text-blue-300',
  completed: 'bg-green-100 text-green-800 dark:bg-green-900/50 dark:text-green-300',
  error: 'bg-destructive/10 text-destructive dark:bg-destructive/20 dark:text-destructive-foreground',
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
