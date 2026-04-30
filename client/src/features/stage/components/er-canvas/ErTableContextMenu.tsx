import { PencilIcon, PlusIcon, Trash2Icon, type LucideIcon } from 'lucide-react'
import { useEffect, useRef, type KeyboardEvent } from 'react'
import { useI18n } from '@/i18n/use-i18n'

interface ErTableContextMenuProps {
  x: number
  y: number
  tableId: string
  onRename: (tableId: string) => void
  onAddColumn: (tableId: string) => void
  onDeleteTable: (tableId: string) => void
  onClose: () => void
}

export function ErTableContextMenu({
  x,
  y,
  tableId,
  onRename,
  onAddColumn,
  onDeleteTable,
  onClose,
}: ErTableContextMenuProps) {
  const { t } = useI18n()
  const ref = useRef<HTMLDivElement>(null)
  const itemRefs = useRef<Array<HTMLButtonElement | null>>([])
  const label = useFallbackLabel(t)

  const items = [
    {
      icon: PencilIcon,
      label: label('erCanvas.contextMenu.rename', 'Rename'),
      onSelect: () => onRename(tableId),
    },
    {
      icon: PlusIcon,
      label: label('erCanvas.contextMenu.addColumn', 'Add column'),
      onSelect: () => onAddColumn(tableId),
    },
    {
      danger: true,
      icon: Trash2Icon,
      label: label('erCanvas.contextMenu.deleteTable', 'Delete table'),
      onSelect: () => onDeleteTable(tableId),
    },
  ]

  useEffect(() => {
    itemRefs.current[0]?.focus()
  }, [])

  useEffect(() => {
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        onClose()
      }
    }

    document.addEventListener('mousedown', closeOnOutsideClick)
    return () => document.removeEventListener('mousedown', closeOnOutsideClick)
  }, [onClose])

  const selectItem = (onSelect: () => void) => {
    onSelect()
    onClose()
  }

  const focusItem = (index: number) => {
    const itemCount = items.length
    itemRefs.current[(index + itemCount) % itemCount]?.focus()
  }

  return (
    <div
      ref={ref}
      role="menu"
      aria-orientation="vertical"
      style={{ left: x, top: y }}
      className="fixed z-50 min-w-44 rounded-[10px] border border-border-subtle bg-bg-panel p-1.5 shadow-md"
    >
      {items.map((item, index) => (
        <MenuItem
          key={item.label}
          buttonRef={(node) => {
            itemRefs.current[index] = node
          }}
          danger={item.danger}
          icon={item.icon}
          label={item.label}
          onClick={() => selectItem(item.onSelect)}
          onKeyDown={(event) => {
            if (event.key === 'ArrowDown') {
              event.preventDefault()
              focusItem(index + 1)
              return
            }

            if (event.key === 'ArrowUp') {
              event.preventDefault()
              focusItem(index - 1)
              return
            }

            if (event.key === 'Escape') {
              event.preventDefault()
              onClose()
              return
            }

            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault()
              selectItem(item.onSelect)
            }
          }}
        />
      ))}
    </div>
  )
}

function MenuItem({
  buttonRef,
  danger,
  icon: Icon,
  label,
  onClick,
  onKeyDown,
}: {
  buttonRef: (node: HTMLButtonElement | null) => void
  danger?: boolean
  icon: LucideIcon
  label: string
  onClick: () => void
  onKeyDown: (event: KeyboardEvent<HTMLButtonElement>) => void
}) {
  return (
    <>
      {danger ? <div role="separator" className="my-1 border-t border-border-subtle" /> : null}
      <button
        ref={buttonRef}
        type="button"
        role="menuitem"
        data-er-menu-variant={danger ? 'danger' : undefined}
        onClick={onClick}
        onKeyDown={onKeyDown}
        className={[
          'flex h-7 w-full items-center gap-2 rounded-md px-2 text-left text-xs outline-none transition-colors focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
          danger
            ? 'text-status-danger hover:bg-[var(--dt-status-danger-surface)] focus:bg-[var(--dt-status-danger-surface)]'
            : 'text-text-base hover:bg-interaction-hover focus:bg-interaction-hover',
        ].join(' ')}
      >
        <Icon className="size-3.5 shrink-0" aria-hidden="true" />
        <span>{label}</span>
      </button>
    </>
  )
}

function useFallbackLabel(t: ReturnType<typeof useI18n>['t']) {
  return (key: string, fallback: string) => {
    const translated = t(key as Parameters<typeof t>[0])
    return translated === key ? fallback : translated
  }
}
