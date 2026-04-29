import { memo, useState } from 'react'
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react'
import { KeyRoundIcon, LinkIcon, LockIcon, PencilIcon, TableIcon, Trash2Icon } from 'lucide-react'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useI18n } from '@/i18n/use-i18n'
import type { ErColumnMeta } from '@/features/stage/stores/er-tabs-payload-types'
import type { ErNodeData } from './utils/payload-to-graph'

export type ErTableNodeMode = 'inspector' | 'designer'

export interface ErTableNodeData extends ErNodeData {
  mode: ErTableNodeMode
  onUpdateColumn?: (columnId: string, updates: Partial<ErColumnMeta>) => void
  onAddColumn?: () => void
  onDeleteColumn?: (columnId: string) => void
  onOpenContextMenu?: (menu: { tableId: string; x: number; y: number }) => void
}

type ErTableReactFlowNode = Node<ErTableNodeData, 'erTable'>

const COLUMN_PREVIEW_LIMIT = 12

export function ErTableNode({ id, data, selected }: NodeProps<ErTableReactFlowNode>) {
  const { t } = useI18n()
  const label = useFallbackLabel(t)
  const [expanded, setExpanded] = useState(data.columns.length <= COLUMN_PREVIEW_LIMIT)
  const visibleColumns = data.collapsed
    ? []
    : expanded
      ? data.columns
      : data.columns.slice(0, COLUMN_PREVIEW_LIMIT)
  const hiddenColumnCount = data.collapsed ? 0 : data.columns.length - visibleColumns.length

  return (
    <section
      onContextMenu={(event) => {
        if (data.mode !== 'designer' || !data.onOpenContextMenu) return
        event.preventDefault()
        // Pass the React Flow node id (which equals the designer table id),
        // not the display name — patch paths address tables via [id=…].
        data.onOpenContextMenu({ tableId: id, x: event.clientX, y: event.clientY })
      }}
      className={[
        'w-80 overflow-hidden rounded-md border bg-[var(--dt-bg-canvas)] font-sans shadow-sm transition-colors',
        selected
          ? 'border-[var(--dt-accent-primary)] ring-2 ring-[var(--dt-accent-primary-surface)]'
          : 'border-[var(--dt-border-default)]',
      ].join(' ')}
      aria-label={`Table ${data.table.name}`}
    >
      <header className="flex items-center justify-between gap-2 border-b border-[var(--dt-border-default)] bg-[var(--dt-bg-subtle)] px-3 py-2">
        <div className="flex min-w-0 items-center gap-2">
          <TableIcon className="size-3.5 shrink-0 text-[var(--dt-text-muted)]" aria-hidden="true" />
          <span className="truncate text-sm font-medium leading-5 text-[var(--dt-text-strong)]">
            {data.table.name}
          </span>
        </div>
        {data.mode === 'designer' ? (
          <PencilIcon
            className="size-3.5 shrink-0 text-[var(--dt-accent-primary)]"
            aria-label="editable designer table"
          />
        ) : (
          <LockIcon
            className="size-3.5 shrink-0 text-[var(--dt-text-soft)]"
            aria-label="read-only inspector view"
          />
        )}
      </header>

      {!data.collapsed && (
        <ul className="flex flex-col">
          {visibleColumns.map((column) => (
            <ColumnRow
              key={getColumnId(column)}
              column={column}
              mode={data.mode}
              onUpdateColumn={data.onUpdateColumn}
              onDeleteColumn={data.onDeleteColumn}
            />
          ))}
          {hiddenColumnCount > 0 && (
            <li>
              <button
                type="button"
                className="nodrag w-full px-3 py-2 text-left text-xs leading-4 text-[var(--dt-text-muted)] outline-none transition-colors hover:bg-[var(--dt-interaction-hover)] hover:text-[var(--dt-text-strong)] focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--dt-interaction-focus-ring)]"
                onClick={() => setExpanded(true)}
              >
                {hiddenColumnCount} more
              </button>
            </li>
          )}
          {data.mode === 'designer' && (
            <li>
              <button
                type="button"
                onClick={data.onAddColumn}
                className="nodrag w-full border-t border-[var(--dt-border-subtle)] px-3 py-2 text-center text-xs font-medium text-[var(--dt-accent-primary)] outline-none transition-colors hover:bg-[var(--dt-accent-primary-surface)] focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--dt-interaction-focus-ring)]"
              >
                {label('erCanvas.contextMenu.addColumn', 'Add column')}
              </button>
            </li>
          )}
        </ul>
      )}
    </section>
  )
}

function ColumnRow({
  column,
  mode,
  onUpdateColumn,
  onDeleteColumn,
}: {
  column: ErColumnMeta
  mode: ErTableNodeMode
  onUpdateColumn?: (columnId: string, updates: Partial<ErColumnMeta>) => void
  onDeleteColumn?: (columnId: string) => void
}) {
  const columnId = getColumnId(column)
  const handleClassName = [
    '!h-2.5 !w-2.5 !border-[var(--dt-bg-canvas)] transition-all duration-150 group-hover:scale-[1.8] hover:scale-[1.8]',
    mode === 'designer'
      ? '!bg-[var(--dt-accent-primary)] !opacity-35 hover:!bg-[var(--dt-accent-primary)] hover:!opacity-100'
      : '!bg-[var(--dt-border-strong)]',
  ].join(' ')

  return (
    <li className="group relative flex min-h-8 items-center justify-between gap-2 border-b border-[var(--dt-border-subtle)] px-3 py-1.5 last:border-b-0 hover:bg-[var(--dt-interaction-hover)]">
      <Handle
        type="target"
        position={Position.Left}
        id={`${columnId}-target`}
        className={handleClassName}
      />
      <div className="flex min-w-0 items-center gap-1.5">
        {column.isPK && (
          <KeyRoundIcon
            className="size-3 shrink-0 text-[var(--dt-accent-primary)]"
            aria-label="primary key"
          />
        )}
        {column.isFK && (
          <LinkIcon
            className="size-3 shrink-0 text-[var(--dt-text-muted)]"
            aria-label="foreign key"
          />
        )}
        {mode === 'designer' ? (
          <input
            type="text"
            value={column.name}
            aria-label={`Column name ${column.name}`}
            onChange={(event) => onUpdateColumn?.(columnId, { name: event.target.value })}
            className={[
              'nodrag min-w-0 flex-1 rounded-sm border border-transparent bg-transparent px-1 text-sm leading-5 outline-none focus:border-[var(--dt-border-default)] focus:bg-[var(--dt-bg-panel)]',
              column.isPK ? 'font-medium text-[var(--dt-text-strong)]' : 'text-[var(--dt-text-base)]',
            ].join(' ')}
          />
        ) : (
          <span
            className={[
              'truncate text-sm leading-5',
              column.isPK ? 'font-medium text-[var(--dt-text-strong)]' : 'text-[var(--dt-text-base)]',
            ].join(' ')}
          >
            {column.name}
          </span>
        )}
      </div>
      {mode === 'designer' ? (
        <ColumnTypeSelect
          value={column.type}
          label={`Type for ${column.name}`}
          onValueChange={(type) => onUpdateColumn?.(columnId, { type })}
        />
      ) : (
        <span className="shrink-0 font-mono text-xs leading-4 text-[var(--dt-text-muted)]">
          {column.type}
        </span>
      )}
      {mode === 'designer' && (
        <button
          type="button"
          aria-label={`Delete column ${column.name}`}
          onClick={() => onDeleteColumn?.(columnId)}
          className="nodrag -mr-1 rounded p-1 text-[var(--dt-text-soft)] transition-colors hover:bg-[var(--dt-status-danger-surface)] hover:text-[var(--dt-status-danger)]"
        >
          <Trash2Icon className="size-3" aria-hidden="true" />
        </button>
      )}
      <Handle
        type="source"
        position={Position.Right}
        id={`${columnId}-source`}
        className={handleClassName}
      />
    </li>
  )
}

export const MemoErTableNode = memo(ErTableNode)
MemoErTableNode.displayName = 'ErTableNode'

const COLUMN_TYPE_OPTIONS = ['BIGINT', 'INT', 'VARCHAR(255)', 'TEXT', 'BOOLEAN', 'DATE', 'TIMESTAMP']

function ColumnTypeSelect({
  value,
  label,
  onValueChange,
}: {
  value: string
  label: string
  onValueChange: (value: string) => void
}) {
  return (
    <Select value={value} onValueChange={(nextValue) => {
      if (nextValue) onValueChange(nextValue)
    }}>
      <SelectTrigger
        aria-label={label}
        size="sm"
        className="nodrag h-6 max-w-28 border-[var(--dt-border-default)] bg-[var(--dt-bg-panel)] px-1.5 font-mono text-xs text-[var(--dt-text-muted)]"
      >
        <SelectValue />
      </SelectTrigger>
      <SelectContent className="bg-[var(--dt-bg-panel)]">
        {COLUMN_TYPE_OPTIONS.map((type) => (
          <SelectItem key={type} value={type}>
            {type}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

function getColumnId(column: ErColumnMeta) {
  const maybeId = (column as ErColumnMeta & { id?: unknown }).id
  return typeof maybeId === 'string' ? maybeId : column.name
}

function useFallbackLabel(t: ReturnType<typeof useI18n>['t']) {
  return (key: string, fallback: string) => {
    const translated = t(key as Parameters<typeof t>[0])
    return translated === key ? fallback : translated
  }
}
