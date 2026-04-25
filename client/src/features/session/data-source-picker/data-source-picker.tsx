import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ChevronDownIcon } from 'lucide-react'
import { listConnections } from '@/services/api/connection'
import { useConnectionStore } from '@/features/connection/store'
import { useI18n } from '@/i18n/use-i18n'
import { useDataSourcePickerStore } from './data-source-picker-store'
import { useSessionDataContext } from '../hooks/use-session-data-context'

type DataSourcePickerProps = {
  sessionId?: string | null
}

export function DataSourcePicker({ sessionId = null }: DataSourcePickerProps) {
  const { t } = useI18n()
  const activeConnectionId = useConnectionStore((s) => s.activeConnectionId)
  const setActive = useConnectionStore((s) => s.setActive)
  const sessionDataContext = useSessionDataContext(sessionId)
  const { data: connections = [] } = useQuery({
    queryKey: ['connections'],
    queryFn: listConnections,
  })
  const selectedConnectionId = sessionId
    ? sessionDataContext.context?.connectionId ?? null
    : activeConnectionId

  const selected = useMemo(
    () => connections.find((connection) => connection.id === selectedConnectionId) ?? null,
    [connections, selectedConnectionId],
  )

  return (
    <button
      type="button"
      onClick={async () => {
        const result = await useDataSourcePickerStore.getState().requestPick({
          reason: 'manual',
          preferredConnectionId: selectedConnectionId ?? activeConnectionId,
        })
        if ('cancelled' in result) return
        if (sessionId) {
          await sessionDataContext.setSessionDataContext({
            connectionId: result.connectionId,
            database: null,
            schema: null,
            selectedLevel: 'connection',
          })
        }
        setActive(result.connectionId)
      }}
      className="flex h-7 items-center gap-1.5 rounded px-2 text-xs text-foreground hover:bg-accent/50"
    >
      {selected ? (
        <span className="max-w-[140px] truncate">{selected.name}</span>
      ) : (
        <span className="text-muted-foreground">{t('dataSources.select')}</span>
      )}
      <ChevronDownIcon className="size-3" />
    </button>
  )
}
