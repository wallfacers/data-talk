import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { HistoryEntry } from '../../stores/sql-workbench-store'

type HistoryPanelProps = {
  entries: HistoryEntry[]
  onAppendSql: (sql: string) => void
  onClear: () => void
}

const STATUS_LABELS: Record<HistoryEntry['status'], string> = {
  ok: 'OK',
  error: 'Error',
  risk_blocked: 'Risk',
}

const STATUS_CLASSES: Record<HistoryEntry['status'], string> = {
  ok: 'border-transparent bg-emerald-500/15 text-emerald-700 dark:text-emerald-300',
  error: 'border-transparent bg-destructive/10 text-destructive',
  risk_blocked: 'border-transparent bg-amber-500/15 text-amber-700 dark:text-amber-300',
}

export function HistoryPanel({ entries, onAppendSql, onClear }: HistoryPanelProps) {
  return (
    <div data-testid="history-panel" className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <div className="text-sm font-medium text-foreground">History</div>
          <div className="text-xs text-muted-foreground">Click an entry to append its SQL into the active editor.</div>
        </div>
        <Button type="button" variant="ghost" size="sm" onClick={onClear} disabled={entries.length === 0}>
          Clear history
        </Button>
      </div>

      {entries.length > 0 ? (
        <div className="space-y-2">
          {entries.map((entry) => (
            <Button
              key={entry.id}
              type="button"
              variant="outline"
              className="h-auto w-full justify-between gap-3 px-3 py-2 text-left"
              onClick={() => onAppendSql(entry.sql)}
            >
              <div className="min-w-0 space-y-1">
                <div className="flex items-center gap-2">
                  <Badge variant="outline" className={cn('shrink-0', STATUS_CLASSES[entry.status])}>
                    {STATUS_LABELS[entry.status]}
                  </Badge>
                  <span className="truncate text-xs text-foreground">{entry.sql}</span>
                </div>
                <div className="text-[11px] text-muted-foreground">
                  {entry.resultCount != null ? `${entry.resultCount} results` : 'No result count'} · {new Date(entry.at).toLocaleString()}
                </div>
              </div>
            </Button>
          ))}
        </div>
      ) : (
        <div className="rounded-lg border border-dashed border-border/60 bg-muted/20 px-3 py-4 text-xs text-muted-foreground">
          No history yet.
        </div>
      )}
    </div>
  )
}
