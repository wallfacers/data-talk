import { cn } from '@/lib/utils'
import { getTabTypeDescriptor } from '@/features/stage/registry/tab-type-registry'
import { useI18n } from '@/i18n/use-i18n'
import type { StageTab } from '@/stores/stage-store'

export function NavTabsRow({ tab, focused, onClick }: { tab: StageTab; focused: boolean; onClick: () => void }) {
  const { t } = useI18n()
  const desc = getTabTypeDescriptor(tab.type)
  const Icon = desc.icon
  return (
    <li
      role="button"
      tabIndex={focused ? 0 : -1}
      onClick={onClick}
      className={cn(
        'group relative flex h-8 items-center gap-2 rounded-md px-2 transition-[background,color] duration-[180ms] ease-[var(--easing-standard)]',
        'hover:bg-interaction-hover',
        focused && 'bg-interaction-selected text-text-strong',
        tab.archived && 'opacity-60 text-text-soft',
      )}
    >
      {focused && <span aria-hidden className="absolute inset-y-1 left-0 w-0.5 rounded bg-accent-primary" />}
      <Icon className="size-4 shrink-0 text-text-muted" aria-hidden />
      <span className="flex-1 truncate text-sm text-text-muted group-data-[focused=true]:text-text-strong">{tab.title}</span>
      <span className="shrink-0 rounded bg-bg-subtle px-1.5 py-0.5 text-[10px] uppercase tracking-wide text-text-soft">
        {t(desc.labelKey as Parameters<typeof t>[0])}
      </span>
    </li>
  )
}
