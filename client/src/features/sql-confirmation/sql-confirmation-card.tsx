import { useEffect, useRef } from 'react'
import { Button } from '@/components/ui/button'
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
  pending?: boolean
  onCancel: () => void
  onExecute: () => void
}

export function SqlConfirmationCard({
  risk,
  sqlPreview,
  pending = false,
  onCancel,
  onExecute,
}: SqlConfirmationCardProps) {
  const { t } = useI18n()
  const cancelRef = useRef<HTMLButtonElement | null>(null)

  useEffect(() => {
    cancelRef.current?.focus()
  }, [])

  const isL3 = risk.level === 'L3'
  const objectsLabel = risk.affectedObjects.length > 0
    ? risk.affectedObjects.join(', ')
    : '—'

  return (
    <div
      className={cn(
        'rounded-md border p-4 space-y-3',
        isL3
          ? 'border-[var(--dt-status-danger)]/40 bg-[var(--dt-status-danger-surface)] text-[var(--dt-status-danger)]'
          : 'border-[var(--dt-accent-warn)]/40 bg-[var(--dt-accent-warn-surface)] text-[var(--dt-accent-warn)]',
      )}
      role="group"
      aria-label={isL3 ? t('sqlConfirmation.l3.title') : t('sqlConfirmation.l2.title')}
    >
      <div className="flex items-center gap-2 font-medium">
        <span
          aria-hidden
          className={cn(
            'inline-block h-2 w-2 rounded-full',
            isL3 ? 'bg-[var(--dt-status-danger)]' : 'bg-[var(--dt-accent-warn)]',
          )}
        />
        {isL3 ? t('sqlConfirmation.l3.title') : t('sqlConfirmation.l2.title')}
      </div>

      <pre className="rounded bg-[var(--dt-bg-canvas)] p-2 font-mono text-sm overflow-x-auto whitespace-pre text-[var(--foreground)]">
        {sqlPreview}
      </pre>

      <div className="text-sm">
        <div className="font-medium mb-1">{t('sqlConfirmation.affectedObjects')}</div>
        <ul className="font-mono text-xs space-y-0.5">
          {risk.affectedObjects.map((obj) => (
            <li key={obj}>{obj}</li>
          ))}
        </ul>
      </div>

      <div className="text-sm">
        {isL3
          ? t('sqlConfirmation.l3.body', { objects: objectsLabel })
          : t('sqlConfirmation.l2.body', { objects: objectsLabel })}
      </div>

      {isL3 && (
        <div className="text-sm font-semibold text-[var(--dt-status-danger)]">
          {t('sqlConfirmation.l3.irreversible')}
        </div>
      )}

      <div className="flex justify-end gap-2 pt-1">
        <Button
          ref={cancelRef}
          variant="outline"
          size="sm"
          disabled={pending}
          onClick={onCancel}
        >
          {t('sqlConfirmation.cancel')}
        </Button>
        <Button
          size="sm"
          disabled={pending}
          variant={isL3 ? 'destructive' : 'default'}
          onClick={onExecute}
        >
          {pending ? t('sqlConfirmation.executing') : t('sqlConfirmation.execute')}
        </Button>
      </div>
    </div>
  )
}
