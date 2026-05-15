import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface OpLogPaginationProps {
  total: number
  page: number
  size: number
  onPageChange: (page: number) => void
}

export function OpLogPagination({ total, page, size, onPageChange }: OpLogPaginationProps) {
  const totalPages = Math.max(1, Math.ceil(total / size))
  return (
    <div className="flex items-center justify-between border-t border-border-default bg-bg-soft px-3 py-1.5">
      <span className="text-xs text-text-muted">
        {total > 0 ? `Page ${page + 1} of ${totalPages} · ${total} operations` : 'No operations'}
      </span>
      <div className="flex items-center gap-1">
        <Button
          variant="ghost"
          size="sm"
          className="h-6 w-6 p-0"
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
        >
          <ChevronLeftIcon className="h-3.5 w-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="sm"
          className="h-6 w-6 p-0"
          disabled={page >= totalPages - 1}
          onClick={() => onPageChange(page + 1)}
        >
          <ChevronRightIcon className="h-3.5 w-3.5" />
        </Button>
      </div>
    </div>
  )
}
