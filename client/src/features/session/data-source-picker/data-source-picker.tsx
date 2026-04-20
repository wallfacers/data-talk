import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ChevronDownIcon } from 'lucide-react'
import { listConnections } from '@/services/api/connection'
import { useConnectionStore } from '@/features/connection/store'
import { useI18n } from '@/i18n/use-i18n'
import { useDataSourcePickerStore } from './data-source-picker-store'

export function DataSourcePicker() {
  const { t } = useI18n()
  const activeConnectionId = useConnectionStore((s) => s.activeConnectionId)
  const setActive = useConnectionStore((s) => s.setActive)
  const { data: connections = [] } = useQuery({
    queryKey: ['connections'],
    queryFn: listConnections,
  })

  const selected = useMemo(
    () => connections.find((connection) => connection.id === activeConnectionId) ?? null,
    [connections, activeConnectionId],
  )

  return (
    <button
      type="button"
      onClick={async () => {
        const result = await useDataSourcePickerStore.getState().requestPick({
          reason: 'manual',
          preferredConnectionId: activeConnectionId,
        })
        if ('cancelled' in result) return
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
