import { type FC } from 'react'
import { useI18n } from '@/i18n/use-i18n'
import { Label } from '@/components/ui/label'

export type CompatibilityMode = 'mysql' | 'oracle' | 'pg'

interface Props {
  kind: string
  mode: CompatibilityMode | null
  onModeChange: (mode: CompatibilityMode) => void
  modeOptions: CompatibilityMode[]
  modeDisabled: CompatibilityMode[]
}

/**
 * Kind-neutral multi-mode selector rendered as radio-like chips.
 * Uses the 5-state token contract from client/DESIGN.md semantic tokens:
 * - normal (bg.panel + border.default)
 * - hover (interaction.hover)
 * - selected (interaction.selected)
 * - focus (interaction.focusRing)
 * - disabled (opacity-50 + cursor-not-allowed)
 */
export const MultiModeConnectionFields: FC<Props> = ({
  kind,
  mode,
  onModeChange,
  modeOptions,
  modeDisabled,
}) => {
  const { t } = useI18n()

  return (
    <div className="grid grid-cols-[120px_1fr] items-center gap-2">
      <Label>{t('connection.compatibilityMode')}</Label>
      <div className="flex gap-2">
        {modeOptions.map((option) => {
          const disabled = modeDisabled.includes(option)
          return (
            <button
              key={option}
              type="button"
              role="radio"
              aria-checked={mode === option}
              aria-label={t(`connection.compatibilityMode.${option}`)}
              aria-disabled={disabled}
              disabled={disabled}
              onClick={() => !disabled && onModeChange(option)}
              className={[
                'flex items-center gap-2 px-3 py-2 rounded text-sm transition-colors',
                'border border-[var(--color-border-default)]',
                disabled
                  ? 'opacity-50 cursor-not-allowed'
                  : mode === option
                    ? 'bg-[var(--color-interaction-selected)] text-[var(--color-text-strong)]'
                    : 'hover:bg-[var(--color-interaction-hover)]',
                'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-interaction-focusRing)]',
              ].join(' ')}
            >
              <span className="h-2.5 w-2.5 rounded-full border border-current">
                {mode === option && (
                  <span className="block h-full w-full rounded-full bg-[var(--color-accent-primary)]" />
                )}
              </span>
              <span>{t(`connection.compatibilityMode.${option}`)}</span>
            </button>
          )
        })}
      </div>
    </div>
  )
}
