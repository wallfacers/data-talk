import { useStageStore } from '@/stores/stage-store'
import { useI18n } from '@/i18n/use-i18n'
import { useState } from 'react'
import { NavTabsRow } from './nav-tabs-row'
import { NavTabsSearch } from './nav-tabs-search'
import { useStageFind } from '@/services/find/use-stage-find'
import { Button } from '@/components/ui/button'
import { MoreHorizontal } from 'lucide-react'
import type { StageTab } from '@/stores/stage-store'

export function NavTabs() {
  const { t } = useI18n()
  const [showArchived, setShowArchived] = useState(false)
  const [query, setQuery] = useState('')
  const focusTab = useStageStore((s) => s.focusTab)
  const activeId = useStageStore((s) => s.activeWorkspaceTabId)
  const { tabs, isLoading } = useStageFind({ query, includeArchived: showArchived })

  return (
    <div className="rounded-md border border-border-subtle bg-bg-subtle p-2">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-xs font-medium uppercase tracking-wide text-text-soft">{t('sidebar.tabs.title')}</span>
        <Button
          aria-label={t('sidebar.tabs.archive.toggle', { count: 0 })}
          variant="ghost" size="icon" className="size-6"
          onClick={() => setShowArchived((v) => !v)}>
          <MoreHorizontal className="size-3.5" />
        </Button>
      </div>
      <NavTabsSearch value={query} onChange={setQuery} />
      <ul role="list" tabIndex={0} className="mt-2 flex flex-col gap-0.5"
          onKeyDown={(e) => handleArrowKeys(e, tabs, focusTab)}>
        {isLoading ? null : tabs.length === 0 ? (
          <li className="px-2 py-3 text-center text-xs text-text-soft">{t('sidebar.tabs.empty')}</li>
        ) : (
          tabs.map((tab) => (
            <NavTabsRow key={tab.tabId} tab={tab} focused={tab.tabId === activeId} onClick={() => focusTab(tab.tabId)} />
          ))
        )}
      </ul>
    </div>
  )
}

function handleArrowKeys(e: React.KeyboardEvent, tabs: StageTab[], focusTab: (id: string) => void) {
  if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return
  e.preventDefault()
  const items = [...e.currentTarget.querySelectorAll<HTMLLIElement>('[role="button"]')]
  const currentIdx = items.findIndex((el) => el === document.activeElement)
  const next = (currentIdx + (e.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length
  items[next]?.focus()
}
