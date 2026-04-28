import { AlertTriangleIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n/use-i18n'

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
  const { t } = useI18n()

  if (!entry) {
    return (
      <div data-testid="diagnostics-panel" className="space-y-3">
        <div className="rounded-lg border border-dashed border-border/60 bg-muted/20 px-3 py-4 text-xs text-muted-foreground">
          {t('diagnostics.panel.runExplain')}
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
              <AlertTriangleIcon className="size-3.5 text-warning" />
              <span className="text-xs text-warning">
                {t('diagnostics.panel.warningCount', { count: entry.warningCount })}
              </span>
            </div>
          )}

          {entry.recommendationCount > 0 && (
            <span className="text-xs text-muted-foreground">
              {t('diagnostics.panel.recommendationCount', { count: entry.recommendationCount })}
            </span>
          )}
        </div>
      </div>

      <div className="flex justify-end">
        <Button size="sm" variant="ghost" onClick={onOpenInWorkbench}>
          {t('diagnostics.panel.openInWorkbench')}
        </Button>
      </div>
    </div>
  )
}
