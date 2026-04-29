import { useState } from 'react'
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react'
import { KeyRoundIcon, LinkIcon, LockIcon, TableIcon } from 'lucide-react'
import type { ErColumnMeta } from '@/features/stage/stores/er-tabs-payload-types'
import type { ErNodeData } from './utils/payload-to-graph'

export type ErTableNodeMode = 'inspector' | 'designer'

export interface ErTableNodeData extends ErNodeData {
  mode: ErTableNodeMode
}

type ErTableReactFlowNode = Node<ErTableNodeData, 'erTable'>

const COLUMN_PREVIEW_LIMIT = 12

export function ErTableNode({ data, selected }: NodeProps<ErTableReactFlowNode>) {
  const [expanded, setExpanded] = useState(data.columns.length <= COLUMN_PREVIEW_LIMIT)
  const visibleColumns = data.collapsed
    ? []
    : expanded
      ? data.columns
      : data.columns.slice(0, COLUMN_PREVIEW_LIMIT)
  const hiddenColumnCount = data.collapsed ? 0 : data.columns.length - visibleColumns.length

  return (
    <section
      className={[
        'w-72 overflow-hidden rounded-md border bg-[var(--dt-bg-canvas)] font-sans shadow-sm transition-colors',
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
        <LockIcon
          className="size-3.5 shrink-0 text-[var(--dt-text-soft)]"
          aria-label="read-only inspector view"
        />
      </header>

      {!data.collapsed && (
        <ul className="flex flex-col">
          {visibleColumns.map((column) => (
            <ColumnRow key={column.name} column={column} />
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
        </ul>
      )}
    </section>
  )
}

function ColumnRow({ column }: { column: ErColumnMeta }) {
  return (
    <li className="relative flex min-h-8 items-center justify-between gap-2 border-b border-[var(--dt-border-subtle)] px-3 py-1.5 last:border-b-0 hover:bg-[var(--dt-interaction-hover)]">
      <Handle
        type="target"
        position={Position.Left}
        id={`${column.name}-target`}
        className="!h-2 !w-2 !border-[var(--dt-bg-canvas)] !bg-[var(--dt-border-strong)]"
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
        <span
          className={[
            'truncate text-sm leading-5',
            column.isPK ? 'font-medium text-[var(--dt-text-strong)]' : 'text-[var(--dt-text-base)]',
          ].join(' ')}
        >
          {column.name}
        </span>
      </div>
      <span className="shrink-0 font-mono text-xs leading-4 text-[var(--dt-text-muted)]">
        {column.type}
      </span>
      <Handle
        type="source"
        position={Position.Right}
        id={`${column.name}-source`}
        className="!h-2 !w-2 !border-[var(--dt-bg-canvas)] !bg-[var(--dt-border-strong)]"
      />
    </li>
  )
}
