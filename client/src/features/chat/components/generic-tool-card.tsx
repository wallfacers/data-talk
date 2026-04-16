export function GenericToolCard({ part, descriptor }: any) {
  const status = (part.state?.status ?? 'pending') as string
  const badgeColor = ({
    pending: 'bg-gray-200',
    running: 'bg-blue-200',
    completed: 'bg-green-200',
    error: 'bg-red-200',
  } as Record<string, string>)[status] ?? 'bg-gray-200'
  return (
    <div className="my-2 rounded border bg-background p-2 text-xs">
      <div className="flex items-center gap-2">
        <span className={`rounded px-2 py-0.5 ${badgeColor}`}>{status}</span>
        <span className="font-mono">{descriptor.id}</span>
      </div>
      <div className="mt-1 text-muted-foreground">{descriptor.description}</div>
    </div>
  )
}
