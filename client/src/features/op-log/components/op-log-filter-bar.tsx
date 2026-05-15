import { useCallback } from 'react'
import { SearchIcon, XIcon } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useI18n } from '@/i18n/use-i18n'
import type { MessageKey } from '@/i18n/messages'
import { useConnections } from '@/features/connection/hooks/use-connections'
import { openOrFocusOpLogTab } from '@/features/op-log/utils/open-op-log-tab'
import { useStageStore } from '@/stores/stage-store'
import type { OpLogFilters } from '@/services/api/connection-op-log'

interface OpLogFilterBarProps {
  filters: OpLogFilters
  onFiltersChange: (filters: OpLogFilters) => void
  onClear: () => void
  connectionId: string
  connectionName: string
}

const STATUS_OPTIONS: { value: string, labelKey: MessageKey }[] = [
  { value: 'active', labelKey: 'opLog.status.active' },
  { value: 'undone', labelKey: 'opLog.status.undone' },
  { value: 'expired', labelKey: 'opLog.status.expired' },
  { value: 'pending', labelKey: 'opLog.status.pending' },
]

const OPERATION_OPTIONS: { value: string, labelKey: MessageKey }[] = [
  { value: 'INSERT', labelKey: 'opLog.operation.insert' },
  { value: 'UPDATE', labelKey: 'opLog.operation.update' },
  { value: 'DELETE', labelKey: 'opLog.operation.delete' },
]

export function OpLogFilterBar({ filters, onFiltersChange, onClear, connectionId, connectionName }: OpLogFilterBarProps) {
  const { t } = useI18n()
  const { data: connections } = useConnections()

  const update = useCallback(<K extends keyof OpLogFilters>(key: K, value: OpLogFilters[K]) => {
    onFiltersChange({ ...filters, [key]: value })
  }, [filters, onFiltersChange])

  const hasFilters = Object.values(filters).some(v =>
    v != null && (Array.isArray(v) ? v.length > 0 : v !== ''),
  )

  const handleConnectionSwitch = useCallback((newConnId: string | null) => {
    if (!newConnId || newConnId === connectionId) return
    const conn = connections?.find(c => c.id === newConnId)
    if (!conn) return
    openOrFocusOpLogTab({
      getState: useStageStore.getState,
      connectionId: conn.id,
      connectionName: conn.name,
    })
  }, [connectionId, connections])

  return (
    <div className="flex items-center gap-2 border-b border-border-default bg-bg-soft px-3 py-2">
      <Select
        value={filters.status?.[0] ?? ''}
        onValueChange={(val) => {
          update('status', val ? [val!] : undefined)
        }}
      >
        <SelectTrigger size="sm" className="h-7 w-auto min-w-28 text-[13px]">
          <SelectValue placeholder={t('opLog.filter.allStatus')} />
        </SelectTrigger>
        <SelectContent>
          {STATUS_OPTIONS.map(o => (
            <SelectItem key={o.value} value={o.value}>{t(o.labelKey)}</SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Select
        value={filters.operation?.[0] ?? ''}
        onValueChange={(val) => {
          update('operation', val ? [val!] : undefined)
        }}
      >
        <SelectTrigger size="sm" className="h-7 w-auto min-w-28 text-[13px]">
          <SelectValue placeholder={t('opLog.filter.allOperations')} />
        </SelectTrigger>
        <SelectContent>
          {OPERATION_OPTIONS.map(o => (
            <SelectItem key={o.value} value={o.value}>{t(o.labelKey)}</SelectItem>
          ))}
        </SelectContent>
      </Select>

      <div className="relative">
        <SearchIcon className="absolute left-2 top-1/2 size-3.5 -translate-y-1/2 text-text-muted" />
        <Input
          className="h-7 w-48 pl-7 border-border-default bg-bg-panel text-[13px]"
          placeholder={t('opLog.filter.searchPlaceholder')}
          value={filters.q ?? ''}
          onChange={e => update('q', e.target.value || undefined)}
        />
      </div>

      {hasFilters && (
        <Button variant="ghost" size="sm" className="h-7 gap-1 text-[13px] text-text-muted" onClick={onClear}>
          <XIcon className="h-3 w-3" />
          {t('opLog.filter.clear')}
        </Button>
      )}

      <div className="flex-1" />

      {connections && connections.length > 0 && (
        <Select value={connectionId} onValueChange={handleConnectionSwitch}>
          <SelectTrigger size="sm" className="h-7 w-auto max-w-48 text-[13px]">
            <span className="truncate">{connectionName}</span>
          </SelectTrigger>
          <SelectContent>
            {connections.map(conn => (
              <SelectItem key={conn.id} value={conn.id}>{conn.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      )}
    </div>
  )
}
