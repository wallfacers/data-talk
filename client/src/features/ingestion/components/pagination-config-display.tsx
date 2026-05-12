interface PaginationConfigDisplayProps {
  pagination?: {
    type: string
    maxPages?: number
    pageSize?: number
  } | null
}

export function PaginationConfigDisplay({ pagination }: PaginationConfigDisplayProps) {
  if (!pagination) return null
  return (
    <div className="flex items-center gap-2 text-ui-xs text-text-muted">
      <span className="px-1.5 py-0.5 rounded bg-bg-subtle uppercase">{pagination.type}</span>
      {pagination.pageSize && <span>page size: {pagination.pageSize}</span>}
      {pagination.maxPages && <span>max pages: {pagination.maxPages}</span>}
    </div>
  )
}
