import { cn } from '@/lib/utils'
import { useI18n } from '@/i18n/use-i18n'
import type { Impact, IndexRecommendation } from '@/features/stage/types/diagnostics'

type IndexRecommendationListProps = {
  recommendations: IndexRecommendation[]
}

function impactStyle(impact: Impact): string {
  switch (impact) {
    case 'HIGH':
      return 'bg-destructive/15 text-destructive'
    case 'MEDIUM':
      return 'bg-amber-500/15 text-amber-700 dark:text-amber-400'
    case 'LOW':
      return 'bg-sky-500/15 text-sky-700 dark:text-sky-400'
  }
}

function ImpactBadge({ impact }: { impact: Impact }) {
  const { t } = useI18n()
  const labels: Record<Impact, string> = {
    HIGH: t('diagnostics.impact.high'),
    MEDIUM: t('diagnostics.impact.medium'),
    LOW: t('diagnostics.impact.low'),
  }
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide',
        impactStyle(impact),
      )}
    >
      {labels[impact]}
    </span>
  )
}

export function IndexRecommendationList({ recommendations }: IndexRecommendationListProps) {
  const { t } = useI18n()

  if (recommendations.length === 0) {
    return <p className="px-2 py-4 text-xs text-muted-foreground">{t('diagnostics.noIndexRecommendations')}</p>
  }

  return (
    <ul role="list" className="space-y-2">
      {recommendations.map((rec, i) => (
        <li
          key={i}
          role="listitem"
          className="rounded-lg border bg-card p-3 space-y-1"
        >
          <div className="flex items-center gap-2">
            <ImpactBadge impact={rec.impact} />
            <code className="text-xs font-mono">
              {rec.table}({rec.columns.join(', ')})
            </code>
          </div>
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-[10px]">
              {rec.indexType}
            </span>
          </div>
          <p className="text-xs text-muted-foreground">{rec.rationale}</p>
        </li>
      ))}
    </ul>
  )
}
