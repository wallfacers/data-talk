import { useI18n } from '@/i18n/use-i18n'
import { getTabTypeDescriptor } from '@/features/stage/registry/tab-type-registry'
import type { StageTab } from '@/stores/stage-store'

type Props = {
  tab: StageTab
  active: boolean
  inWorkset: boolean
  onClick: () => void
  trailingMenu?: React.ReactNode
}

export function StageRailRow({ tab, active, inWorkset, onClick, trailingMenu }: Props) {
  const { t } = useI18n()
  const desc = getTabTypeDescriptor(tab.type)
  const labelKey = desc.railLabelKey ?? desc.labelKey
  const Icon = desc.icon

  return (
    <li
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick() }
      }}
      aria-pressed={active}
      data-in-workset={inWorkset}
      data-archived={tab.archived ? true : undefined}
      className={[
        'group relative flex h-8 cursor-pointer select-none items-center gap-2 rounded-md px-2',
        'transition-[background,color] duration-[180ms] ease-[var(--easing-standard)]',
        'text-text-muted',
        inWorkset && !active ? 'font-medium text-text-base' : '',
        'hover:bg-sidebar-accent hover:text-sidebar-accent-foreground',
        active ? 'bg-interaction-selected text-text-strong' : '',
        tab.archived ? 'opacity-60 text-text-soft' : '',
        // focus ring renders for both selected and non-selected rows so keyboard
        // focus is always visible (DESIGN.md focusRing is a stable token).
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
      ].filter(Boolean).join(' ')}
    >
      {active && <span aria-hidden className="absolute inset-y-1 left-0 w-0.5 rounded bg-accent-primary" />}

      <Icon className={`size-4 shrink-0 ${active ? 'text-text-strong' : 'text-text-muted'}`} aria-hidden />

      <span className="flex-1 truncate text-sm">{tab.title}</span>

      <span className="shrink-0 rounded bg-bg-subtle px-1.5 py-0.5 text-[10px] uppercase tracking-wide text-text-soft">
        {t(labelKey as Parameters<typeof t>[0])}
      </span>

      {trailingMenu ? (
        <span
          onClick={(e) => e.stopPropagation()}
          onMouseDown={(e) => e.stopPropagation()}
          className="opacity-0 group-hover:opacity-100 group-focus-within:opacity-100 transition-opacity duration-[180ms]"
        >
          {trailingMenu}
        </span>
      ) : null}
    </li>
  )
}
