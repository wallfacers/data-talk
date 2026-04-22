import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { SqlOutlineStatement } from '../../utils/parse-sql-outline'

type OutlinePanelProps = {
  statements: SqlOutlineStatement[]
  onJumpToLine: (line: number) => void
}

export function OutlinePanel({ statements, onJumpToLine }: OutlinePanelProps) {
  return (
    <div data-testid="outline-panel" className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <div className="text-sm font-medium text-foreground">Outline</div>
          <div className="text-xs text-muted-foreground">Jump to any parsed SQL statement.</div>
        </div>
        <Badge variant="outline" className="shrink-0">
          {statements.length}
        </Badge>
      </div>

      {statements.length > 0 ? (
        <div className="space-y-2">
          {statements.map((statement) => (
            <Button
              key={`${statement.line}-${statement.kind}-${statement.summary}`}
              type="button"
              variant="ghost"
              aria-label={`Ln ${statement.line} ${statement.kind}`}
              className={cn(
                'h-auto w-full justify-between gap-3 rounded-md px-2 py-2 text-left',
                statement.highRiskHint ? 'text-amber-700 dark:text-amber-300' : 'text-foreground',
              )}
              onClick={() => onJumpToLine(statement.line)}
            >
              <div className="min-w-0 space-y-1">
                <div className="flex items-center gap-2">
                  <Badge variant={statement.highRiskHint ? 'secondary' : 'outline'} className="shrink-0">
                    Ln {statement.line}
                  </Badge>
                  <span className="truncate text-xs font-medium">{statement.kind}</span>
                </div>
                <div className="truncate text-[11px] text-muted-foreground">{statement.summary}</div>
              </div>
              {statement.highRiskHint ? <Badge variant="destructive">High risk</Badge> : null}
            </Button>
          ))}
        </div>
      ) : (
        <div className="rounded-lg border border-dashed border-border/60 bg-muted/20 px-3 py-4 text-xs text-muted-foreground">
          No parsed statements yet.
        </div>
      )}
    </div>
  )
}
