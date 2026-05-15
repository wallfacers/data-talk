import { useSemanticPendingQuery } from '../hooks/use-semantic-queries'
import { useSemanticModelStore } from '../stores/use-semantic-model-store'
import { acceptPending, rejectPending } from '../api/semantic-api'
import { useMutation, useQueryClient } from '@tanstack/react-query'

interface PendingListProps {
  connectionId: string
}

export function PendingList({ connectionId }: PendingListProps) {
  const { data, isLoading } = useSemanticPendingQuery(connectionId)
  const selectDomain = useSemanticModelStore((s) => s.selectDomain)
  const queryClient = useQueryClient()

  const acceptMutation = useMutation({
    mutationFn: (domain: string) => acceptPending(connectionId, domain),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['semantic-pending', connectionId] })
      queryClient.invalidateQueries({ queryKey: ['semantic-domains', connectionId] })
    },
  })

  const rejectMutation = useMutation({
    mutationFn: (domain: string) => rejectPending(connectionId, domain),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['semantic-pending', connectionId] })
    },
  })

  if (isLoading) return <div className="p-4 text-muted-foreground">Loading pending proposals...</div>
  const pending = data?.pending ?? []

  if (pending.length === 0) {
    return <div className="p-4 text-muted-foreground">No pending proposals</div>
  }

  return (
    <div className="space-y-2 p-2">
      {pending.map((item) => (
        <div
          key={item.domain}
          className="flex items-center justify-between rounded border p-3 hover:bg-accent/50 cursor-pointer"
          onClick={() => selectDomain(item.domain)}
        >
          <span className="font-medium text-sm">{item.domain}</span>
          <div className="flex gap-2">
            <button
              className="px-3 py-1 text-xs rounded bg-green-600 text-white hover:bg-green-700"
              onClick={(e) => { e.stopPropagation(); acceptMutation.mutate(item.domain) }}
              disabled={acceptMutation.isPending}
            >
              Accept
            </button>
            <button
              className="px-3 py-1 text-xs rounded bg-red-600 text-white hover:bg-red-700"
              onClick={(e) => { e.stopPropagation(); rejectMutation.mutate(item.domain) }}
              disabled={rejectMutation.isPending}
            >
              Reject
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}
