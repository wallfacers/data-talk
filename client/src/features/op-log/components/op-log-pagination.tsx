import { ChevronLeftIcon, ChevronRightIcon, Undo2Icon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'

interface OpLogPaginationProps {
  total: number
  page: number
  size: number
  onPageChange: (page: number) => void
  batchUndoCount: number
  onBatchUndo: () => void
}

export function OpLogPagination({ total, page, size, onPageChange, batchUndoCount, onBatchUndo }: OpLogPaginationProps) {
  const { t } = useI18n()
  const totalPages = Math.max(1, Math.ceil(total / size))
  return (
    <div className="flex items-center justify-between border-t border-border-default bg-bg-soft px-3 py-1.5">
      <div className="flex items-center gap-3">
        <span className="text-xs text-text-muted">
          {total > 0
            ? t('opLog.pagination.info', { page: String(page + 1), totalPages: String(totalPages), total: String(total) })
            : t('opLog.pagination.empty')}
        </span>
        {batchUndoCount > 0 && (
          <>
            <span className="text-xs text-text-soft">|</span>
            <span className="text-xs font-medium text-text-strong">
              {t('opLog.batchUndo.selected', { count: String(batchUndoCount) })}
            </span>
            <Button
              size="sm"
              className="h-6 gap-1 bg-status-danger text-text-inverse hover:bg-red-600"
              onClick={onBatchUndo}
            >
              <Undo2Icon className="h-3.5 w-3.5" />
              {t('opLog.batchUndo.undoSelected')}
            </Button>
          </>
        )}
      </div>
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
