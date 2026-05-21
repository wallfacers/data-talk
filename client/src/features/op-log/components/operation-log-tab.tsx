import { useCallback, useEffect, useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import type { StageTab } from '@/stores/stage-store'
import { listOpLogs, batchUndoOpLogs, type OpLogFilters, type OpLogListResponse } from '@/services/api/connection-op-log'
import { subscribeOpLogStream } from '@/services/api/connection-op-log-sse'
import { useConnections } from '@/features/connection/hooks/use-connections'
import { OpLogFilterBar } from './op-log-filter-bar'
import { OpLogTable } from './op-log-table'
import { BatchUndoConfirmDialog } from './batch-undo-confirm-dialog'

interface OperationLogTabProps {
  tab: StageTab
}

export function OperationLogTab({ tab }: OperationLogTabProps) {
  const connectionId = tab.connectionId!
  const { data: connections } = useConnections()
  const connectionName = tab.connectionName ?? connections?.find(c => c.id === connectionId)?.name ?? ''
  const queryClient = useQueryClient()

  const [page, setPage] = useState(0)
  const [size] = useState(50)
  const [filters, setFilters] = useState<OpLogFilters>({})
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [confirmDialogOpen, setConfirmDialogOpen] = useState(false)
  const [undoLoading, setUndoLoading] = useState(false)

  const queryKey = useMemo(
    () => ['op-log', connectionId, page, size, filters] as const,
    [connectionId, page, size, filters],
  )

  const { data, isLoading } = useQuery<OpLogListResponse>({
    queryKey,
    queryFn: () => listOpLogs(connectionId, page, size, filters),
  })

  // SSE subscription - invalidate on events
  useEffect(() => {
    const sub = subscribeOpLogStream(connectionId, (event) => {
      if (event.event === 'undo_log.created' || event.event === 'undo_log.status_changed') {
        queryClient.invalidateQueries({ queryKey: ['op-log', connectionId] })
      }
    })
    return () => sub.dispose()
  }, [connectionId, queryClient])

  // Clear selection when connection changes
  useEffect(() => {
    setSelectedIds(new Set())
  }, [connectionId])

  const toggleRow = useCallback((id: string) => {
    setSelectedIds(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }, [])

  const selectedItems = useMemo(
    () => (data?.items ?? []).filter(item => selectedIds.has(item.id)),
    [data?.items, selectedIds],
  )

  const undoableSelected = useMemo(
    () => selectedItems.filter(item => item.status === 'active' && item.undoable),
    [selectedItems],
  )

  const handleBatchUndo = useCallback(async () => {
    setUndoLoading(true)
    try {
      await batchUndoOpLogs(connectionId, undoableSelected.map(i => i.id))
      setConfirmDialogOpen(false)
      setSelectedIds(new Set())
      queryClient.invalidateQueries({ queryKey: ['op-log', connectionId] })
    } finally {
      setUndoLoading(false)
    }
  }, [connectionId, undoableSelected, queryClient])

  const clearFilters = useCallback(() => setFilters({}), [])

  return (
    <div className="flex h-full flex-col bg-bg-canvas">
      <OpLogFilterBar filters={filters} onFiltersChange={setFilters} onClear={clearFilters} connectionId={connectionId} connectionName={connectionName} />
      <OpLogTable
        data={data?.items ?? []}
        isLoading={isLoading}
        selectedIds={selectedIds}
        onToggleRow={toggleRow}
        connectionId={connectionId}
        total={data?.total ?? 0}
        page={page}
        size={size}
        onPageChange={setPage}
        batchUndoCount={undoableSelected.length}
        onBatchUndo={() => setConfirmDialogOpen(true)}
      />
      <BatchUndoConfirmDialog
        open={confirmDialogOpen}
        items={undoableSelected}
        loading={undoLoading}
        onConfirm={handleBatchUndo}
        onCancel={() => setConfirmDialogOpen(false)}
      />
    </div>
  )
}
