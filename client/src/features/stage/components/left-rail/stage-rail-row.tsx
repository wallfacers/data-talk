import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Input } from '@/components/ui/input'
import { getTabTypeDescriptor } from '@/features/stage/registry/tab-type-registry'
import { useStageStore } from '@/stores/stage-store'
import type { StageTab } from '@/stores/stage-store'
import { StageRailRowMenu } from './stage-rail-row-menu'

type Props = {
  tab: StageTab
  active: boolean
  inWorkset: boolean
  onClick: () => void
}

export function StageRailRow({ tab, active, inWorkset, onClick }: Props) {
  const desc = getTabTypeDescriptor(tab.type)
  const Icon = desc.icon
  const [editing, setEditing] = useState(false)
  const [editTitle, setEditTitle] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)
  const setTabTitle = useStageStore((s) => s.setTabTitle)

  useEffect(() => {
    if (editing) inputRef.current?.focus()
  }, [editing])

  const startRename = () => {
    setEditTitle(tab.title)
    setEditing(true)
  }

  const commitRename = () => {
    const trimmed = editTitle.trim()
    if (trimmed && trimmed !== tab.title) {
      setTabTitle(tab.tabId, trimmed)
    }
    setEditing(false)
  }

  if (editing) {
    return (
      <li className="relative flex h-8 items-center rounded-md bg-interaction-selected px-1">
        <Input
          ref={inputRef}
          value={editTitle}
          onChange={(e) => setEditTitle(e.target.value)}
          onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
            if (e.key === 'Enter') commitRename()
            if (e.key === 'Escape') setEditing(false)
          }}
          onBlur={commitRename}
          className="h-6 w-full rounded border-0 bg-surface-base px-2 text-sm focus:ring-0 focus:outline-none"
        />
      </li>
    )
  }

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
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
      ].filter(Boolean).join(' ')}
    >
      {active && <span aria-hidden className="absolute inset-y-1 left-0 w-0.5 rounded bg-accent-primary" />}

      <Icon className={`size-4 shrink-0 ${active ? 'text-text-strong' : 'text-text-muted'}`} aria-hidden />

      <span className="flex-1 truncate text-sm">{tab.title}</span>

      <span
        onClick={(e) => e.stopPropagation()}
        onMouseDown={(e) => e.stopPropagation()}
        className="opacity-0 group-hover:opacity-100 group-focus-within:opacity-100 transition-opacity duration-[180ms]"
      >
        <StageRailRowMenu tab={tab} onStartRename={startRename} />
      </span>
    </li>
  )
}
