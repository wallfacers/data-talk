import { AlertTriangleIcon, SearchCodeIcon } from 'lucide-react'
import { BasicTool } from '../basic-tool'
import type { ToolRendererProps } from '../tool-registry'
import { ToolRegistry } from '../tool-registry'
import { Button } from '@/components/ui/button'
import type { ExplainResult, IndexHintsResponse, IndexRecommendation } from '@/features/stage/types/diagnostics'

type DiagnosticsOutput = ExplainResult | IndexHintsResponse

export function DiagnosticsCard(props: ToolRendererProps) {
  const { part } = props
  const output = part.state.output as DiagnosticsOutput | undefined
  const unsupported = Boolean(output && 'unsupported' in output && output.unsupported === true)
  const errorMessage = output && 'error' in output ? output.error.message : null

  const warnings: string[] = (() => {
    if (unsupported || errorMessage) return []
    if (output && 'warnings' in output) return output.warnings ?? []
    return []
  })()

  const recommendations: IndexRecommendation[] = (() => {
    if (unsupported || errorMessage) return []
    if (output && 'recommendations' in output) return output.recommendations ?? []
    return []
  })()

  return (
    <BasicTool
      icon="code"
      status={part.state.status}
      trigger={{
        title: unsupported ? 'Diagnostics' : 'Query Diagnostics',
        subtitle: warnings.length > 0 ? `${warnings.length} warning${warnings.length !== 1 ? 's' : ''}` : undefined,
      }}
      defaultOpen={props.defaultOpen}
    >
      <div className="space-y-3">
        {unsupported && (
          <div className="text-sm text-muted-foreground">
            Execution plan analysis is not supported for this query or database dialect.
          </div>
        )}

        {errorMessage && (
          <div className="text-sm text-destructive">
            {errorMessage}
          </div>
        )}

        {!unsupported && warnings.length > 0 && (
          <div className="flex items-center gap-2">
            <AlertTriangleIcon className="size-4 shrink-0 text-amber-500" />
            <span className="text-sm font-medium text-amber-600 dark:text-amber-400">
              {warnings.length} warning{warnings.length !== 1 ? 's' : ''}
            </span>
          </div>
        )}

        {recommendations.length > 0 && (
          <div className="space-y-1">
            <div className="text-xs font-medium text-foreground">
              Recommendations ({recommendations.length})
            </div>
            <ul className="space-y-1">
              {recommendations.map((rec, i) => (
                <li key={i} className="text-xs text-muted-foreground">
                  {rec.table} ({rec.columns.join(', ')}) — {rec.rationale}
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="flex justify-end">
          <Button size="sm" variant="ghost" onClick={() => {}}>
            <SearchCodeIcon className="mr-1 size-3.5" />
            Open in Workbench
          </Button>
        </div>
      </div>
    </BasicTool>
  )
}

ToolRegistry.register('datatalk.explain_query', DiagnosticsCard)
ToolRegistry.register('datatalk.index_hints', DiagnosticsCard)
