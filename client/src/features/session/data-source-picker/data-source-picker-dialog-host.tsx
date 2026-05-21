import { useEffect } from 'react'
import { useConnections } from '@/features/connection/hooks/use-connections'
import { useConnectionStore } from '@/features/connection/store'
import { useDataSourcePickerStore } from './data-source-picker-store'
import { DataSourcePickerDialog } from './data-source-picker-dialog'
import { readRecentConnectionIds, rememberRecentConnection } from './recent-connections'

export function DataSourcePickerDialogHost() {
  const open = useDataSourcePickerStore((s) => s.open)
  const preferredConnectionId = useDataSourcePickerStore((s) => s.preferredConnectionId)
  const resolvePick = useDataSourcePickerStore((s) => s.resolvePick)
  const cancelPick = useDataSourcePickerStore((s) => s.cancelPick)
  const { data: connections } = useConnections()
  const setConnections = useConnectionStore((s) => s.setConnections)

  useEffect(() => {
    if (connections === undefined) return
    setConnections(connections)
  }, [connections, setConnections])

  return (
    <DataSourcePickerDialog
      open={open}
      onOpenChange={(next) => {
        if (!next) cancelPick()
      }}
      connections={connections ?? []}
      recentConnectionIds={readRecentConnectionIds()}
      preferredConnectionId={preferredConnectionId}
      onPick={(connection) => {
        rememberRecentConnection(connection.id)
        resolvePick({
          connectionId: connection.id,
          connectionName: connection.name,
        })
      }}
    />
  )
}
