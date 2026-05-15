import { Undo2Icon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'

interface BatchUndoBarProps {
  count: number
  onUndo: () => void
}

export function BatchUndoBar({ count, onUndo }: BatchUndoBarProps) {
  const { t } = useI18n()
  return (
    <>
      <span className="text-xs font-medium text-text-strong">
        {t('opLog.batchUndo.selected', { count: String(count) })}
      </span>
      <Button
        size="sm"
        className="h-6 gap-1 bg-status-danger text-text-inverse hover:bg-red-600"
        onClick={onUndo}
      >
        <Undo2Icon className="h-3.5 w-3.5" />
        {t('opLog.batchUndo.undoSelected')}
      </Button>
    </>
  )
}
