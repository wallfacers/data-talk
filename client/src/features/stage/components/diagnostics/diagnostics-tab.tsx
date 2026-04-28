import { useState } from 'react'
import { ChevronRightIcon, DatabaseIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { ExplainPlanTree } from './explain-plan-tree'
import { IndexRecommendationList } from './index-recommendation-list'
import type { ExplainNode, IndexRecommendation } from '@/features/stage/types/diagnostics'

export interface DiagnosticsTabPayload {
  sql: string
  connectionName?: string
  dialect?: string
  plan?: ExplainNode[]
  recommendations?: IndexRecommendation[]
  explainSummary?: string
  rawExplainText?: string
  loading?: boolean
  error?: string
}

type DiagnosticsTabProps = {
  payload: DiagnosticsTabPayload
}

export function DiagnosticsTab({ payload }: DiagnosticsTabProps) {
  const [rawExpanded, setRawExpanded] = useState(false)
  const { t } = useI18n()
  const {
    sql,
    connectionName,
    dialect,
    plan,
    recommendations,
    explainSummary,
    rawExplainText,
    loading,
    error,
  } = payload

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center">
        <p className="text-sm text-muted-foreground">{t('diagnostics.running')}</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex h-full items-center justify-center">
        <p className="text-sm text-destructive">{error}</p>
      </div>
    )
  }

  const sqlSnippet = sql.length > 80 ? sql.slice(0, 80) + '…' : sql

  return (
    <div className="flex h-full flex-col">
      {/* Top chrome */}
      <div className="flex items-center gap-2 border-b bg-subtle px-3 py-2">
        {connectionName && (
          <div className="flex items-center gap-1 text-xs text-muted-foreground">
            <DatabaseIcon className="size-3" />
            <span>{connectionName}</span>
          </div>
        )}
        {dialect && <Badge variant="secondary">{dialect}</Badge>}
        <code className="truncate text-xs text-muted-foreground">{sqlSnippet}</code>
      </div>

      {/* Main area */}
      <div className="flex min-h-0 flex-1">
        {/* Left: Execution Plan */}
        <div className="w-3/5 overflow-auto border-r p-3">
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            {t('diagnostics.executionPlan')}
          </h3>
          <ExplainPlanTree nodes={plan ?? []} />
        </div>

        {/* Right: Index Recommendations */}
        <div className="w-2/5 overflow-auto p-3">
          <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            {t('diagnostics.indexRecommendations')}
          </h3>
          <IndexRecommendationList recommendations={recommendations ?? []} />
          {explainSummary && (
            <p className="mt-3 text-xs text-muted-foreground">{explainSummary}</p>
          )}
        </div>
      </div>

      {/* Bottom: collapsible raw EXPLAIN */}
      {rawExplainText && (
        <div className="border-t">
          <Tooltip>
            <TooltipTrigger
              render={
                <button
                  type="button"
                  className="flex w-full items-center gap-1.5 px-3 py-1.5 text-xs text-muted-foreground hover:bg-muted"
                  onClick={() => setRawExpanded((prev) => !prev)}
                  aria-expanded={rawExpanded}
                  aria-label={t('diagnostics.toggleRaw')}
                >
                  <ChevronRightIcon
                    className={cn('size-3 transition-transform', rawExpanded && 'rotate-90')}
                  />
                  {t('diagnostics.rawExplain')}
                </button>
              }
            />
            <TooltipContent>{t('diagnostics.toggleRaw')}</TooltipContent>
          </Tooltip>
          {rawExpanded && (
            <pre className="max-h-40 overflow-auto bg-muted/50 px-3 py-2 text-xs font-mono">
              {rawExplainText}
            </pre>
          )}
        </div>
      )}
    </div>
  )
}
