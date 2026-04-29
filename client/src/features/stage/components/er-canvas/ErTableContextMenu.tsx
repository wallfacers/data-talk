import { useEffect, useRef, type ReactNode } from 'react'
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
  const label = useFallbackLabel(t)

  useEffect(() => {
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        onClose()
      }
    }

    document.addEventListener('mousedown', closeOnOutsideClick)
    return () => document.removeEventListener('mousedown', closeOnOutsideClick)
  }, [onClose])

  return (
    <div
      ref={ref}
      role="menu"
      style={{ left: x, top: y }}
      className="fixed z-50 min-w-36 rounded-lg border border-[var(--dt-border-default)] bg-[var(--dt-bg-panel)] p-1 shadow-md"
    >
      <MenuItem onClick={() => { onRename(tableId); onClose() }}>
        {label('erCanvas.contextMenu.rename', 'Rename')}
      </MenuItem>
      <MenuItem onClick={() => { onAddColumn(tableId); onClose() }}>
        {label('erCanvas.contextMenu.addColumn', 'Add column')}
      </MenuItem>
      <MenuItem danger onClick={() => { onDeleteTable(tableId); onClose() }}>
        {label('erCanvas.contextMenu.deleteTable', 'Delete table')}
      </MenuItem>
    </div>
  )
}

function MenuItem({ children, danger, onClick }: { children: ReactNode; danger?: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onClick}
      className={[
        'w-full rounded-md px-2 py-1.5 text-left text-xs outline-none transition-colors',
        danger
          ? 'text-[var(--dt-status-danger)] hover:bg-[var(--dt-status-danger-surface)] focus-visible:bg-[var(--dt-status-danger-surface)]'
          : 'text-[var(--dt-text-base)] hover:bg-[var(--dt-interaction-hover)] focus-visible:bg-[var(--dt-interaction-hover)]',
      ].join(' ')}
    >
      {children}
    </button>
  )
}

function useFallbackLabel(t: ReturnType<typeof useI18n>['t']) {
  return (key: string, fallback: string) => {
    const translated = t(key as Parameters<typeof t>[0])
    return translated === key ? fallback : translated
  }
}
