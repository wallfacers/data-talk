import { useI18n } from '@/i18n/use-i18n'
import { cn } from '@/lib/utils'

export type SqlRisk = {
  level: 'L1' | 'L2' | 'L3'
  reason: string
  affectedObjects: string[]
}

export type SqlConfirmationCardProps = {
  risk: SqlRisk
  sqlPreview: string
}

export function SqlConfirmationCard({ risk, sqlPreview }: SqlConfirmationCardProps) {
  const { t } = useI18n()

  const isL3 = risk.level === 'L3'
  const objectsLabel = risk.affectedObjects.length > 0
    ? risk.affectedObjects.join(', ')
    : '—'

  return (
    <div
      className="space-y-3"
      data-testid="sql-risk-panel"
      role="group"
      aria-label={isL3 ? t('sqlConfirmation.l3.title') : t('sqlConfirmation.l2.title')}
    >
      <div
        aria-hidden
        className={cn(
          'h-1 w-full rounded-full',
          isL3 ? 'bg-[var(--dt-status-danger)]' : 'bg-[var(--dt-accent-warn)]',
        )}
      />

      <pre
        className={cn(
          'rounded-md border p-3 font-mono text-sm overflow-x-auto whitespace-pre text-[var(--dt-text-strong)]',
          isL3
            ? 'border-[color-mix(in_srgb,var(--dt-status-danger)_30%,transparent)] bg-[var(--dt-status-danger-surface)]'
            : 'border-[color-mix(in_srgb,var(--dt-accent-warn)_30%,transparent)] bg-[var(--dt-accent-warn-surface)]',
        )}
      >
        {sqlPreview}
      </pre>

      <div className="text-sm">
        <div className="mb-1 font-medium text-[var(--dt-text-strong)]">
          {t('sqlConfirmation.affectedObjects')}
        </div>
        <ul className="space-y-0.5 font-mono text-xs text-[var(--dt-text-muted)]">
          {risk.affectedObjects.map((obj) => (
            <li key={obj}>{obj}</li>
          ))}
        </ul>
      </div>

      <div className="text-sm text-[var(--dt-text-base)]">
        {isL3
          ? t('sqlConfirmation.l3.body', { objects: objectsLabel })
          : t('sqlConfirmation.l2.body', { objects: objectsLabel })}
      </div>

      {isL3 && (
        <div className="text-sm font-semibold text-[var(--dt-status-danger)]">
          {t('sqlConfirmation.l3.irreversible')}
        </div>
      )}
    </div>
  )
}
