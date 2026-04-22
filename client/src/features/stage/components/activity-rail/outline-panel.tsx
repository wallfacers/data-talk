import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'
import { cn } from '@/lib/utils'
import type { SqlOutlineStatement } from '../../utils/parse-sql-outline'

type OutlinePanelProps = {
  statements: SqlOutlineStatement[]
  onJumpToLine: (line: number) => void
}

export function OutlinePanel({ statements, onJumpToLine }: OutlinePanelProps) {
  const { t } = useI18n()

  return (
    <div data-testid="outline-panel" className="space-y-3">
      {statements.length > 0 ? (
        <div className="space-y-2">
          {statements.map((statement) => (
            <Button
              key={`${statement.line}-${statement.kind}-${statement.summary}`}
              type="button"
              variant="ghost"
              aria-label={`${t('stage.activityRail.outline.line', { line: statement.line })} ${statement.kind}`}
              className={cn(
                'h-auto w-full justify-between gap-3 rounded-md px-2 py-2 text-left',
                statement.highRiskHint ? 'text-amber-700 dark:text-amber-300' : 'text-foreground',
              )}
              onClick={() => onJumpToLine(statement.line)}
            >
              <div className="min-w-0 space-y-1">
                <div className="flex items-center gap-2">
                  <Badge variant={statement.highRiskHint ? 'secondary' : 'outline'} className="shrink-0">
                    {t('stage.activityRail.outline.line', { line: statement.line })}
                  </Badge>
                  <span className="truncate text-xs font-medium">{statement.kind}</span>
                </div>
                <div className="truncate text-[11px] text-muted-foreground">{statement.summary}</div>
              </div>
              {statement.highRiskHint ? (
                <Badge variant="destructive">{t('stage.activityRail.outline.highRisk')}</Badge>
              ) : null}
            </Button>
          ))}
        </div>
      ) : (
        <div className="rounded-lg border border-dashed border-border/60 bg-muted/20 px-3 py-4 text-xs text-muted-foreground">
          {t('stage.activityRail.outline.empty')}
        </div>
      )}
    </div>
  )
}
