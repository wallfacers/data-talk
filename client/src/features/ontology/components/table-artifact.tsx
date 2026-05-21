import { useMemo } from 'react'
import { flexRender, getCoreRowModel, useReactTable } from '@tanstack/react-table'
import type { Artifact } from '@/services/channel/event-reducer'

export function TableArtifact({ artifact }: { artifact: Artifact }) {
  const payload = artifact.payload as any
  const columns = useMemo(() => (payload?.columns ?? []).map((c: string) => ({
    header: c, accessorKey: c, id: c,
  })), [payload])
  const data = payload?.preview ?? []
  const table = useReactTable({ data, columns, getCoreRowModel: getCoreRowModel() })
  return (
    <div className="h-full overflow-auto">
      <table className="w-full text-xs">
        <thead>
          {table.getHeaderGroups().map(hg => (
            <tr key={hg.id} className="border-b bg-muted">
              {hg.headers.map(h => (
                <th key={h.id} className="p-2 text-left font-medium">
                  {flexRender(h.column.columnDef.header, h.getContext())}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody>
          {table.getRowModel().rows.map(r => (
            <tr key={r.id} className="border-b">
              {r.getVisibleCells().map(c => (
                <td key={c.id} className="p-2">{flexRender(c.column.columnDef.cell, c.getContext())}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
