import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import type { OpLogItem } from '@/services/api/connection-op-log'

interface BatchUndoConfirmDialogProps {
  open: boolean
  items: OpLogItem[]
  loading: boolean
  onConfirm: () => void
  onCancel: () => void
}

export function BatchUndoConfirmDialog({ open, items, loading, onConfirm, onCancel }: BatchUndoConfirmDialogProps) {
  return (
    <AlertDialog open={open} onOpenChange={(v) => !v && onCancel()}>
      <AlertDialogContent className="bg-bg-panel">
        <AlertDialogHeader>
          <AlertDialogTitle className="text-text-strong">
            Undo {items.length} operation{items.length !== 1 ? 's' : ''}?
          </AlertDialogTitle>
          <AlertDialogDescription className="text-text-muted">
            This will execute the inverse SQL for each selected operation. This action cannot be reversed.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <div className="max-h-48 space-y-1.5 overflow-auto">
          {items.map(item => (
            <div key={item.id} className="flex items-center gap-3 rounded-md bg-bg-subtle px-3 py-1.5 text-[13px]">
              <span className="font-medium text-text-base">{item.tableName}</span>
              <span className="font-mono text-xs text-text-muted">{item.operation}</span>
              <span className="ml-auto font-mono text-text-muted">{item.affectedRows} rows</span>
            </div>
          ))}
        </div>
        <AlertDialogFooter>
          <Button variant="outline" onClick={onCancel} disabled={loading}>Cancel</Button>
          <Button
            className="bg-status-danger text-text-inverse hover:bg-red-600"
            onClick={onConfirm}
            disabled={loading}
          >
            {loading ? 'Undoing' : 'Confirm Undo'}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
