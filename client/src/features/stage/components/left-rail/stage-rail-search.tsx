import { SearchIcon, XIcon } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'

type Props = {
  value: string
  onChange: (next: string) => void
  disabled?: boolean
}

export function StageRailSearch({ value, onChange, disabled }: Props) {
  const { t } = useI18n()
  return (
    <div
      className={[
        'group relative flex h-8 items-center gap-1.5 rounded-md px-2',
        'bg-bg-panel border border-border-default',
        'hover:bg-interaction-hover',
        'focus-within:border-border-strong focus-within:ring-2 focus-within:ring-interaction-focusRing',
        disabled ? 'opacity-50 pointer-events-none' : '',
      ].join(' ')}
    >
      <SearchIcon className="size-3.5 text-text-muted shrink-0" aria-hidden />
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        placeholder={t('stage.leftRail.search.placeholder')}
        className={[
          'flex-1 bg-transparent text-sm text-text-base outline-none',
          'placeholder:text-text-muted',
          'disabled:text-text-soft',
        ].join(' ')}
        aria-label={t('stage.leftRail.search.placeholder')}
      />
      {value ? (
        <button
          type="button"
          aria-label={t('common.clear')}
          onClick={() => onChange('')}
          className="text-text-muted hover:text-text-base focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing rounded"
        >
          <XIcon className="size-3.5" />
        </button>
      ) : null}
    </div>
  )
}
