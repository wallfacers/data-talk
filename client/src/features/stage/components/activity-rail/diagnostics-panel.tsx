import { AlertTriangleIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'

export interface DiagnosticsPanelEntry {
  sqlSnippet: string
  warningCount: number
  recommendationCount: number
}

type DiagnosticsPanelProps = {
  entry: DiagnosticsPanelEntry | null
  onOpenInWorkbench: () => void
}

export function DiagnosticsPanel({ entry, onOpenInWorkbench }: DiagnosticsPanelProps) {

  if (!entry) {
    return (
      <div data-testid="diagnostics-panel" className="space-y-3">
        <div className="rounded-lg border border-dashed border-border/60 bg-muted/20 px-3 py-4 text-xs text-muted-foreground">
          Run Explain to view the execution plan.
        </div>
      </div>
    )
  }

  const truncatedSql = entry.sqlSnippet.length > 60
    ? entry.sqlSnippet.slice(0, 60) + '...'
    : entry.sqlSnippet

  return (
    <div data-testid="diagnostics-panel" className="space-y-3">
      <div className="space-y-2">
        <div className="truncate text-xs font-mono text-foreground">
          {truncatedSql}
        </div>

        <div className="flex items-center gap-3">
          {entry.warningCount > 0 && (
            <div className="flex items-center gap-1">
              <AlertTriangleIcon className="size-3.5 text-amber-500" />
              <span className="text-xs text-amber-600 dark:text-amber-400">
                {entry.warningCount} warning{entry.warningCount !== 1 ? 's' : ''}
              </span>
            </div>
          )}

          {entry.recommendationCount > 0 && (
            <span className="text-xs text-muted-foreground">
              {entry.recommendationCount} recommendation{entry.recommendationCount !== 1 ? 's' : ''}
            </span>
          )}
        </div>
      </div>

      <div className="flex justify-end">
        <Button size="sm" variant="ghost" onClick={onOpenInWorkbench}>
          Open in Workbench
        </Button>
      </div>
    </div>
  )
}
