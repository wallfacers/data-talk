import { Button } from '@/components/ui/button'
import { Undo2Icon } from 'lucide-react'

interface BatchUndoBarProps {
  count: number
  onUndo: () => void
}

export function BatchUndoBar({ count, onUndo }: BatchUndoBarProps) {
  return (
    <div
      className="flex items-center justify-between border-t border-border-default bg-bg-elevated px-4 py-2 shadow-[0_-4px_12px_rgba(0,0,0,0.08)]"
      style={{ animation: 'slideUp 120ms cubic-bezier(0.16, 1, 0.3, 1)' }}
    >
      <span className="text-[13px] font-medium text-text-strong">{count} selected</span>
      <Button
        size="sm"
        className="gap-1.5 bg-status-danger text-text-inverse hover:bg-red-600"
        onClick={onUndo}
      >
        <Undo2Icon className="h-3.5 w-3.5" />
        Undo Selected
      </Button>
    </div>
  )
}
