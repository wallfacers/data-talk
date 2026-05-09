import { type FC } from 'react'
import { useI18n } from '@/i18n/use-i18n'
import { MultiModeConnectionFields, type CompatibilityMode } from './multi-mode-connection-fields'

interface Props {
  kindInput: string
  mode: CompatibilityMode
  onModeChange: (mode: CompatibilityMode) => void
}

/**
 * KingbaseES connection fields: reuses the multi-mode selector skeleton
 * with pg (enabled) and oracle (disabled with tooltip) modes.
 * Shows an alias hint chip when kindInput === "kingbasees".
 */
export const KingbaseConnectionFields: FC<Props> = ({
  kindInput,
  mode,
  onModeChange,
}) => {
  const { t } = useI18n()

  return (
    <>
      <MultiModeConnectionFields
        kind="kingbase"
        mode={mode}
        onModeChange={onModeChange}
        modeOptions={['pg', 'oracle']}
        modeDisabled={['oracle']}
      />
      {kindInput === 'kingbasees' && (
        <div className="col-start-2">
          <span className="inline-flex items-center gap-1 rounded border border-[var(--color-border-default)] bg-[var(--color-bg-subtle)] px-2 py-0.5 text-xs text-[var(--color-text-muted)]">
            {t('connection.kind.kingbase.alias_normalized')}
          </span>
        </div>
      )}
    </>
  )
}
