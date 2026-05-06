import { memo, useEffect, useRef, useState } from 'react'
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react'
import {
  ChevronDownIcon,
  KeyRoundIcon,
  LinkIcon,
  LockIcon,
  TableIcon,
  Trash2Icon,
} from 'lucide-react'
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
export type ErDesignerDialect = 'mysql' | 'postgresql' | 'h2' | 'sqlite' | 'mariadb'

export interface ErTableNodeData extends ErNodeData {
  mode: ErTableNodeMode
  dialect?: ErDesignerDialect
  shouldFocusName?: boolean
  onNameFocusHandled?: (tableId: string) => void
  onUpdateTable?: (tableId: string, updates: { name?: string }) => void
  onUpdateColumn?: (columnId: string, updates: Partial<ErColumnMeta>) => void
  onAddColumn?: () => void
  onDeleteColumn?: (columnId: string) => void
  onOpenContextMenu?: (menu: { tableId: string; x: number; y: number }) => void
}

type ErTableReactFlowNode = Node<ErTableNodeData, 'erTable'>
type RowRole = 'pk' | 'fk' | 'pkfk' | 'regular'

const COLUMN_PREVIEW_LIMIT = 12

export function ErTableNode({ id, data, selected }: NodeProps<ErTableReactFlowNode>) {
  const { t } = useI18n()
  const label = useFallbackLabel(t)
  const [expanded, setExpanded] = useState(data.columns.length <= COLUMN_PREVIEW_LIMIT)
  const nameInputRef = useRef<HTMLInputElement>(null)
  const emptyLabel = label('erCanvas.node.empty', 'No columns yet')
  const visibleColumns = data.collapsed
    ? []
    : expanded
      ? data.columns
      : data.columns.slice(0, COLUMN_PREVIEW_LIMIT)
  const hiddenColumnCount = data.collapsed ? 0 : data.columns.length - visibleColumns.length

  const focusTableNameInput = () => {
    nameInputRef.current?.focus()
    nameInputRef.current?.select()
  }

  useEffect(() => {
    if (data.mode !== 'designer' || !data.shouldFocusName) return
    focusTableNameInput()
    data.onNameFocusHandled?.(id)
  }, [data.mode, data.onNameFocusHandled, data.shouldFocusName, id])

  return (
    <section
      onContextMenu={(event) => {
        if (data.mode !== 'designer' || !data.onOpenContextMenu) return
        event.preventDefault()
        // Pass the React Flow node id (which equals the designer table id),
        // not the display name — patch paths address tables via [id=…].
        data.onOpenContextMenu({ tableId: id, x: event.clientX, y: event.clientY })
      }}
      data-er-mode={data.mode}
      data-er-table-id={id}
      data-er-table-name={data.table.name}
      className={[
        'w-80 overflow-hidden rounded-[10px] border bg-bg-canvas font-sans transition-colors',
        data.mode === 'designer'
          ? 'border-border-default shadow-sm'
          : 'border-border-subtle',
        selected
          ? 'border-accent-primary ring-2 ring-accent-primary-surface'
          : '',
      ].join(' ')}
      aria-label={`Table ${data.table.name}`}
    >
      <header
        className={[
          'flex items-center justify-between gap-2 border-b bg-bg-subtle px-3 py-2',
          data.mode === 'designer' && !data.collapsed ? 'border-border-default' : 'border-border-subtle',
        ].join(' ')}
      >
        <div className="flex min-w-0 items-center gap-2">
          <TableIcon className="size-3.5 shrink-0 text-text-muted" aria-hidden="true" />
          {data.mode === 'designer' ? (
            <input
              ref={nameInputRef}
              type="text"
              value={data.table.name}
              aria-label={`Table name ${data.table.name}`}
              onChange={(event) => data.onUpdateTable?.(id, { name: event.target.value })}
              className="nodrag min-w-0 flex-1 rounded-sm border border-transparent bg-transparent px-1 text-sm font-medium leading-5 text-text-strong outline-none focus:border-border-default focus:bg-bg-panel"
            />
          ) : (
            <span className="truncate text-sm font-medium leading-5 text-text-strong">
              {data.table.name}
            </span>
          )}
        </div>
        <div className="flex shrink-0 items-center gap-1.5">
          {data.collapsed && (
            <ChevronDownIcon className="size-3.5 text-text-soft" aria-hidden="true" />
          )}
          {data.mode === 'inspector' ? (
            <LockIcon
              className="size-3.5 shrink-0 text-text-soft"
              aria-label="read-only inspector view"
              data-testid="er-mode-indicator"
              data-er-mode="inspector"
            />
          ) : null}
        </div>
      </header>

      {!data.collapsed && (
        <ul className="flex flex-col">
          {visibleColumns.length === 0 && (
            <li className="px-3 py-5 text-center text-xs leading-4 text-text-soft">
              {emptyLabel}
            </li>
          )}
          {visibleColumns.map((column) => (
            <ColumnRow
              key={getColumnId(column)}
              column={column}
              mode={data.mode}
              dialect={data.dialect}
              onUpdateColumn={data.onUpdateColumn}
              onDeleteColumn={data.onDeleteColumn}
            />
          ))}
          {hiddenColumnCount > 0 && (
            <li>
              <button
                type="button"
                className="nodrag w-full px-3 py-2 text-left text-xs leading-4 text-text-muted outline-none transition-colors hover:bg-interaction-hover hover:text-text-strong focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-interaction-focusRing"
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
                className="nodrag w-full border-t border-border-subtle px-3 py-2 text-center text-xs font-medium text-accent-primary outline-none transition-colors hover:bg-accent-primary-surface focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-interaction-focusRing"
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
  dialect,
  onUpdateColumn,
  onDeleteColumn,
}: {
  column: ErColumnMeta
  mode: ErTableNodeMode
  dialect?: ErDesignerDialect
  onUpdateColumn?: (columnId: string, updates: Partial<ErColumnMeta>) => void
  onDeleteColumn?: (columnId: string) => void
}) {
  const columnId = getColumnId(column)
  const role = rowRole(column)
  const rowMinHeight = mode === 'designer' ? 'min-h-8' : 'min-h-7'
  const rowPadding = mode === 'designer' ? 'py-1.5' : 'py-1'
  const rowHorizontalPadding = mode === 'designer' ? 'pl-0 pr-8' : 'px-3'
  const rowGap = mode === 'designer' ? 'gap-2.5' : 'gap-2'

  // Outer Handle: transparent anchor positioned by ReactFlow; the visible ball
  // is an inner <span> so direct-hover scaling stays centered (Tailwind's
  // transform would otherwise replace ReactFlow's translateY(-50%)).
  const handleAnchor = mode === 'designer'
    ? 'er-handle !border-none !bg-transparent !cursor-crosshair z-20 flex items-center justify-center'
    : 'er-handle !border-none !bg-transparent z-20 flex items-center justify-center'
  const ballBase = 'block rounded-full transition duration-150'
  const visibilityClass = mode === 'designer'
    ? 'opacity-100'
    : 'opacity-0 group-hover:opacity-100'
  const handleSizeClass = mode === 'designer' ? 'size-[18px]' : 'size-4'
  const targetBallClassName = [
    ballBase,
    visibilityClass,
    handleSizeClass,
    'bg-border-strong ring-2 ring-bg-canvas hover:scale-125 hover:ring-accent-primary',
  ].join(' ')
  const sourceBallClassName = [
    ballBase,
    visibilityClass,
    handleSizeClass,
    'border-2 border-border-strong bg-bg-canvas hover:scale-125 hover:border-accent-primary hover:ring-2 hover:ring-accent-primary',
  ].join(' ')
  const railClassName = role === 'pk' || role === 'pkfk'
    ? [
        'absolute left-0 top-0 bottom-0 w-[3px] bg-border-strong',
      ].join(' ')
    : role === 'fk'
      ? 'absolute left-0 top-1 bottom-1 border-l-[1.5px] border-dashed border-border-default'
      : 'hidden'
  const handleAnchorStyle = { width: 20, height: 20 } as const

  return (
    <li
      className={[
        'group relative flex items-center justify-between border-b border-border-subtle last:border-b-0 hover:bg-interaction-hover',
        rowMinHeight,
        rowPadding,
        rowHorizontalPadding,
        rowGap,
      ].join(' ')}
      data-testid={`er-row-${column.name}`}
      data-er-row-role={role}
    >
      <span className={railClassName} aria-hidden="true" />
      {role === 'pkfk' && (
        <span
          className="absolute left-[2px] top-1 bottom-1 border-l border-dashed border-border-default"
          aria-hidden="true"
        />
      )}
      <Handle
        type="target"
        position={Position.Left}
        id={`${columnId}-target`}
        className={handleAnchor}
        style={handleAnchorStyle}
        data-er-column-handle={`${columnId}:target`}
      >
        <span className={targetBallClassName} data-er-handle-shape="solid" />
      </Handle>
      <div className="flex min-w-0 flex-1 items-center gap-1">
        <div className="flex w-6 shrink-0 items-center gap-1">
          {(role === 'pk' || role === 'pkfk') && (
            <KeyRoundIcon
              className="size-3 shrink-0 text-text-muted group-hover:text-accent-primary"
              aria-label="primary key"
            />
          )}
          {(role === 'fk' || role === 'pkfk') && (
            <LinkIcon
              className="size-3 shrink-0 text-text-muted group-hover:text-accent-primary"
              aria-label="foreign key"
            />
          )}
        </div>
        {mode === 'designer' ? (
          <input
            type="text"
            value={column.name}
            aria-label={`Column name ${column.name}`}
            onChange={(event) => onUpdateColumn?.(columnId, { name: event.target.value })}
            className={[
              'nodrag min-w-0 flex-1 rounded-sm border border-transparent bg-transparent px-1 text-sm leading-5 outline-none focus:border-border-default focus:bg-bg-panel',
              column.isPK ? 'font-medium text-text-strong' : 'text-text-base',
            ].join(' ')}
          />
        ) : (
          <span
            className={[
              'truncate text-sm leading-5',
              column.isPK ? 'font-medium text-text-strong' : 'text-text-base',
            ].join(' ')}
          >
            {column.name}
          </span>
        )}
      </div>
      {mode === 'designer' ? (
        <ColumnTypeSelect
          value={column.type}
          dialect={dialect}
          label={`Type for ${column.name}`}
          onValueChange={(type) => onUpdateColumn?.(columnId, { type })}
        />
      ) : (
        <span className="shrink-0 rounded-sm border border-border-subtle bg-bg-subtle px-1.5 font-mono text-xs leading-4 text-text-muted">
          {column.type}
        </span>
      )}
      {column.nullable === false && (
        <span
          className="shrink-0 rounded-sm border border-border-subtle px-1.5 text-[10px] font-medium leading-4 text-text-soft"
          aria-label="NOT NULL"
          data-er-nn-pill
        >
          NN
        </span>
      )}
      {mode === 'designer' && (
        <button
          type="button"
          aria-label={`Delete column ${column.name}`}
          onClick={() => onDeleteColumn?.(columnId)}
          className="nodrag -mr-1 rounded p-1 text-text-soft opacity-0 transition-colors hover:bg-[var(--dt-status-danger-surface)] hover:text-status-danger group-hover:opacity-100 focus-visible:opacity-100 focus-visible:ring-2 focus-visible:ring-interaction-focusRing"
        >
          <Trash2Icon className="size-3" aria-hidden="true" />
        </button>
      )}
      <Handle
        type="source"
        position={Position.Right}
        id={`${columnId}-source`}
        className={handleAnchor}
        style={handleAnchorStyle}
        data-er-column-handle={`${columnId}:source`}
      >
        <span className={sourceBallClassName} data-er-handle-shape="ring" />
      </Handle>
    </li>
  )
}

export const MemoErTableNode = memo(ErTableNode)
MemoErTableNode.displayName = 'ErTableNode'

function rowRole(column: ErColumnMeta): RowRole {
  if (column.isPK && column.isFK) return 'pkfk'
  if (column.isPK) return 'pk'
  if (column.isFK) return 'fk'
  return 'regular'
}

const COLUMN_TYPE_OPTIONS = ['BIGINT', 'INT', 'VARCHAR(255)', 'TEXT', 'BOOLEAN', 'DATE', 'TIMESTAMP']

const DIALECT_COLUMN_TYPE_OPTIONS: Record<ErDesignerDialect, string[]> = {
  mysql: [
    'TINYINT',
    'SMALLINT',
    'MEDIUMINT',
    'INT',
    'INTEGER',
    'BIGINT',
    'SERIAL',
    'DECIMAL',
    'DECIMAL(10,2)',
    'DEC',
    'FIXED',
    'NUMERIC',
    'NUMERIC(10,2)',
    'FLOAT',
    'DOUBLE',
    'DOUBLE PRECISION',
    'REAL',
    'BIT(1)',
    'BOOL',
    'BOOLEAN',
    'CHAR',
    'CHAR(255)',
    'NCHAR(255)',
    'VARCHAR(255)',
    'NVARCHAR(255)',
    'TINYTEXT',
    'TEXT',
    'MEDIUMTEXT',
    'LONGTEXT',
    'BINARY(16)',
    'VARBINARY(255)',
    'TINYBLOB',
    'BLOB',
    'MEDIUMBLOB',
    'LONGBLOB',
    'DATE',
    'TIME',
    'DATETIME',
    'TIMESTAMP',
    'YEAR',
    'JSON',
    "ENUM('value')",
    "SET('value')",
    'GEOMETRY',
    'POINT',
    'LINESTRING',
    'POLYGON',
    'MULTIPOINT',
    'MULTILINESTRING',
    'MULTIPOLYGON',
    'GEOMETRYCOLLECTION',
  ],
  postgresql: [
    'SMALLINT',
    'INTEGER',
    'BIGINT',
    'SMALLSERIAL',
    'SERIAL',
    'BIGSERIAL',
    'DECIMAL',
    'DECIMAL(10,2)',
    'NUMERIC',
    'NUMERIC(10,2)',
    'REAL',
    'DOUBLE PRECISION',
    'BOOLEAN',
    'BIT(1)',
    'BIT VARYING(255)',
    'CHAR(255)',
    'VARCHAR(255)',
    'TEXT',
    'DATE',
    'TIME',
    'TIMETZ',
    'TIMESTAMP',
    'TIMESTAMPTZ',
    'INTERVAL',
    'UUID',
    'JSON',
    'JSONB',
    'BYTEA',
    'INET',
    'CIDR',
    'MACADDR',
    'MACADDR8',
    'MONEY',
    'POINT',
    'LINE',
    'LSEG',
    'BOX',
    'PATH',
    'POLYGON',
    'CIRCLE',
    'TSVECTOR',
    'TSQUERY',
    'XML',
  ],
  h2: [
    'TINYINT',
    'SMALLINT',
    'INT',
    'BIGINT',
    'DECIMAL(10,2)',
    'NUMERIC(10,2)',
    'REAL',
    'DOUBLE',
    'BOOLEAN',
    'CHAR(255)',
    'VARCHAR(255)',
    'CLOB',
    'BINARY(16)',
    'VARBINARY(255)',
    'BLOB',
    'DATE',
    'TIME',
    'TIMESTAMP',
    'UUID',
    'JSON',
  ],
  sqlite: [
    'INTEGER',
    'REAL',
    'NUMERIC',
    'TEXT',
    'BLOB',
  ],
  mariadb: [
    'TINYINT',
    'SMALLINT',
    'MEDIUMINT',
    'INT',
    'INTEGER',
    'BIGINT',
    'SERIAL',
    'DECIMAL',
    'DECIMAL(10,2)',
    'DEC',
    'FIXED',
    'NUMERIC',
    'NUMERIC(10,2)',
    'FLOAT',
    'DOUBLE',
    'DOUBLE PRECISION',
    'REAL',
    'BIT(1)',
    'BOOL',
    'BOOLEAN',
    'CHAR',
    'CHAR(255)',
    'NCHAR(255)',
    'VARCHAR(255)',
    'NVARCHAR(255)',
    'TINYTEXT',
    'TEXT',
    'MEDIUMTEXT',
    'LONGTEXT',
    'BINARY(16)',
    'VARBINARY(255)',
    'TINYBLOB',
    'BLOB',
    'MEDIUMBLOB',
    'LONGBLOB',
    'DATE',
    'TIME',
    'DATETIME',
    'TIMESTAMP',
    'YEAR',
    'JSON',
    "ENUM('value')",
    "SET('value')",
    'GEOMETRY',
    'POINT',
    'LINESTRING',
    'POLYGON',
    'MULTIPOINT',
    'MULTILINESTRING',
    'MULTIPOLYGON',
    'GEOMETRYCOLLECTION',
  ],
}

export function getColumnTypeOptions(dialect?: ErDesignerDialect, currentValue?: string): string[] {
  const baseOptions = dialect ? DIALECT_COLUMN_TYPE_OPTIONS[dialect] : COLUMN_TYPE_OPTIONS
  const options = [...baseOptions]
  if (currentValue && !options.includes(currentValue)) {
    options.unshift(currentValue)
  }
  return options
}

function ColumnTypeSelect({
  value,
  dialect,
  label,
  onValueChange,
}: {
  value: string
  dialect?: ErDesignerDialect
  label: string
  onValueChange: (value: string) => void
}) {
  const options = getColumnTypeOptions(dialect, value)

  return (
    <Select value={value} onValueChange={(nextValue) => {
      if (nextValue) onValueChange(nextValue)
    }}>
      <SelectTrigger
        aria-label={label}
        size="sm"
        className="nodrag h-6 w-32 border-border-default bg-bg-panel px-1.5 font-mono text-xs text-text-muted"
      >
        <SelectValue />
      </SelectTrigger>
      <SelectContent className="bg-bg-panel">
        {options.map((type) => (
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
