import { useCallback, useMemo, useState } from 'react'
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  flexRender,
  type ColumnDef,
  type SortingState,
} from '@tanstack/react-table'
import { ChevronDownIcon, ChevronUpIcon, ChevronsUpDownIcon, Undo2Icon } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { Checkbox } from '@/components/ui/checkbox'
import { Button } from '@/components/ui/button'
import type { OpLogItem } from '@/services/api/connection-op-log'
import { batchUndoOpLogs } from '@/services/api/connection-op-log'
import { OpLogStatusBadge } from './op-log-status-badge'
import { OpLogOperationBadge } from './op-log-operation-badge'
import { OpLogPagination } from './op-log-pagination'
import { OpLogDetailRow } from './op-log-detail-row'

interface OpLogTableProps {
  data: OpLogItem[]
  isLoading: boolean
  selectedIds: Set<string>
  onToggleRow: (id: string) => void
  connectionId: string
  total: number
  page: number
  size: number
  onPageChange: (page: number) => void
}

function formatTime(ts: number): string {
  const d = new Date(ts)
  return d.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function SortHeader({ column, title }: {
  column: { getIsSorted: () => false | 'asc' | 'desc'; toggleSorting: (desc: boolean) => void }
  title: string
}) {
  const sorted = column.getIsSorted()
  return (
    <button
      className="flex items-center gap-1 text-text-muted hover:text-text-base"
      onClick={() => column.toggleSorting(sorted === 'asc')}
    >
      <span>{title}</span>
      {sorted === 'asc' ? <ChevronUpIcon className="h-3 w-3 text-accent-primary" />
        : sorted === 'desc' ? <ChevronDownIcon className="h-3 w-3 text-accent-primary" />
        : <ChevronsUpDownIcon className="h-3 w-3" />}
    </button>
  )
}

export function OpLogTable({ data, isLoading, selectedIds, onToggleRow, connectionId, total, page, size, onPageChange }: OpLogTableProps) {
  const [sorting, setSorting] = useState<SortingState>([])
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const handleSingleUndo = useCallback(async (id: string) => {
    await batchUndoOpLogs(connectionId, [id])
    queryClient.invalidateQueries({ queryKey: ['op-log', connectionId] })
  }, [connectionId, queryClient])

  const columns = useMemo<ColumnDef<OpLogItem, unknown>[]>(() => [
    {
      id: 'select',
      header: () => (
        <Checkbox
          checked={data.length > 0 && data.every(d => selectedIds.has(d.id))}
          onCheckedChange={() => {
            const allSelected = data.every(d => selectedIds.has(d.id))
            for (const d of data) {
              if (allSelected === selectedIds.has(d.id)) onToggleRow(d.id)
            }
          }}
          className="h-3.5 w-3.5"
        />
      ),
      cell: ({ row }) => (
        <Checkbox
          checked={selectedIds.has(row.original.id)}
          onCheckedChange={() => onToggleRow(row.original.id)}
          className="h-3.5 w-3.5"
        />
      ),
      size: 32,
    },
    {
      accessorKey: 'createdAt',
      header: ({ column }) => <SortHeader column={column} title="Time" />,
      cell: ({ getValue }) => (
        <span className="text-[13px] leading-[18px] text-text-base">{formatTime(getValue<number>())}</span>
      ),
      size: 80,
    },
    {
      accessorKey: 'operation',
      header: 'Operation',
      cell: ({ row }) => <OpLogOperationBadge operation={row.original.operation} />,
      size: 100,
      enableSorting: false,
    },
    {
      accessorKey: 'tableName',
      header: ({ column }) => <SortHeader column={column} title="Table" />,
      cell: ({ getValue }) => (
        <span className="text-[13px] leading-[18px] text-text-base">{getValue<string>()}</span>
      ),
    },
    {
      accessorKey: 'affectedRows',
      header: ({ column }) => <SortHeader column={column} title="Rows" />,
      cell: ({ getValue }) => (
        <span className="font-mono text-[13px] leading-[18px] text-text-base">{getValue<number>()}</span>
      ),
      size: 64,
    },
    {
      accessorKey: 'status',
      header: ({ column }) => <SortHeader column={column} title="Status" />,
      cell: ({ row }) => <OpLogStatusBadge status={row.original.status} />,
      size: 100,
    },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) => {
        const item = row.original
        if (item.status !== 'active' || !item.undoable) return null
        return (
          <Button
            variant="ghost"
            size="sm"
            className="h-6 gap-1 text-xs text-text-muted hover:text-status-danger"
            onClick={(e) => { e.stopPropagation(); handleSingleUndo(item.id) }}
          >
            <Undo2Icon className="h-3 w-3" />
            Undo
          </Button>
        )
      },
      size: 80,
    },
  ], [data, selectedIds, onToggleRow, handleSingleUndo])

  const table = useReactTable({
    data,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  })

  if (isLoading) {
    return <div className="flex flex-1 items-center justify-center text-sm text-text-muted">Loading...</div>
  }

  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      <div className="flex-1 overflow-auto">
        <table className="w-full border-collapse">
          <thead className="sticky top-0 z-10 bg-bg-subtle">
            {table.getHeaderGroups().map(hg => (
              <tr key={hg.id} className="border-b border-border-default">
                {hg.headers.map(header => (
                  <th key={header.id} className="px-3 py-1.5 text-left text-xs font-medium text-text-muted" style={{ width: header.getSize() }}>
                    {flexRender(header.column.columnDef.header, header.getContext())}
                  </th>
                ))}
              </tr>
            ))}
          </thead>
          <tbody>
            {table.getRowModel().rows.map(row => {
              const item = row.original
              const isSelected = selectedIds.has(item.id)
              const isExpanded = expandedId === item.id
              return (
                <tbody key={item.id}>
                  <tr
                    className={`cursor-pointer border-b border-border-subtle transition-colors ${
                      isSelected ? 'bg-interaction-selected' : 'hover:bg-interaction-hover'
                    }`}
                    style={{ transitionDuration: '120ms' }}
                    onClick={() => setExpandedId(isExpanded ? null : item.id)}
                  >
                    {row.getVisibleCells().map(cell => (
                      <td key={cell.id} className="px-3 py-1.5">
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </td>
                    ))}
                  </tr>
                  {isExpanded && (
                    <tr>
                      <td colSpan={row.getVisibleCells().length} className="border-t border-border-subtle bg-bg-panel p-0">
                        <div style={{
                          animation: 'expandIn 180ms cubic-bezier(0.16, 1, 0.3, 1)',
                        }}>
                          <OpLogDetailRow item={item} connectionId={connectionId} />
                        </div>
                      </td>
                    </tr>
                  )}
                </tbody>
              )
            })}
            {data.length === 0 && (
              <tr>
                <td colSpan={columns.length} className="py-8 text-center text-sm text-text-muted">
                  No operations found
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <OpLogPagination total={total} page={page} size={size} onPageChange={onPageChange} />
    </div>
  )
}
