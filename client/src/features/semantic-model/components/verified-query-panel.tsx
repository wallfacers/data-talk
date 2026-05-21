import { useSemanticVqQuery } from '../hooks/use-semantic-queries'

interface VerifiedQueryPanelProps {
  connectionId: string
}

export function VerifiedQueryPanel({ connectionId }: VerifiedQueryPanelProps) {
  const { data, isLoading } = useSemanticVqQuery(connectionId, 20)

  if (isLoading) return <div className="p-4 text-muted-foreground">Loading verified queries...</div>
  const queries = data?.queries ?? []

  if (queries.length === 0) {
    return <div className="p-4 text-muted-foreground">No verified queries yet</div>
  }

  return (
    <div className="space-y-2 p-2">
      {queries.filter((q) => !q.stale).map((vq) => (
        <div key={vq.id} className="rounded border p-3">
          <div className="text-sm font-medium mb-1">{vq.question}</div>
          <pre className="text-xs bg-muted p-2 rounded overflow-x-auto max-h-20">{vq.sql}</pre>
          <div className="flex gap-4 mt-1 text-xs text-muted-foreground">
            <span>Model: {vq.modelRef}</span>
            <span>Hits: {vq.hitCount}</span>
            {vq.stale && <span className="text-orange-500">Stale</span>}
          </div>
        </div>
      ))}
    </div>
  )
}
