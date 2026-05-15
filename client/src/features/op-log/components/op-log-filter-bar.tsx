import { useCallback } from 'react'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { XIcon } from 'lucide-react'
import type { OpLogFilters } from '@/services/api/connection-op-log'

interface OpLogFilterBarProps {
  filters: OpLogFilters
  onFiltersChange: (filters: OpLogFilters) => void
  onClear: () => void
}

const STATUS_OPTIONS = [
  { value: 'active', label: 'Active' },
  { value: 'undone', label: 'Undone' },
  { value: 'expired', label: 'Expired' },
  { value: 'pending', label: 'Pending' },
]

const OPERATION_OPTIONS = [
  { value: 'INSERT', label: 'INSERT' },
  { value: 'UPDATE', label: 'UPDATE' },
  { value: 'DELETE', label: 'DELETE' },
]

export function OpLogFilterBar({ filters, onFiltersChange, onClear }: OpLogFilterBarProps) {
  const update = useCallback(<K extends keyof OpLogFilters>(key: K, value: OpLogFilters[K]) => {
    onFiltersChange({ ...filters, [key]: value })
  }, [filters, onFiltersChange])

  const hasFilters = Object.values(filters).some(v =>
    v != null && (Array.isArray(v) ? v.length > 0 : v !== ''),
  )

  return (
    <div className="flex items-center gap-2 border-b border-border-default bg-bg-soft px-3 py-2">
      {/* Status filter */}
      <select
        className="h-7 rounded-md border border-border-default bg-bg-panel px-2 text-[13px] leading-[18px] text-text-base"
        value={filters.status?.[0] ?? ''}
        onChange={e => {
          const val = e.target.value
          update('status', val ? [val] : undefined)
        }}
      >
        <option value="">All Status</option>
        {STATUS_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>

      {/* Operation filter */}
      <select
        className="h-7 rounded-md border border-border-default bg-bg-panel px-2 text-[13px] leading-[18px] text-text-base"
        value={filters.operation?.[0] ?? ''}
        onChange={e => {
          const val = e.target.value
          update('operation', val ? [val] : undefined)
        }}
      >
        <option value="">All Operations</option>
        {OPERATION_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>

      {/* Table name filter */}
      <Input
        className="h-7 w-32 border-border-default bg-bg-panel text-[13px]"
        placeholder="Table name..."
        value={filters.table ?? ''}
        onChange={e => update('table', e.target.value || undefined)}
      />

      {/* SQL search */}
      <Input
        className="h-7 w-40 border-border-default bg-bg-panel text-[13px]"
        placeholder="Search SQL..."
        value={filters.q ?? ''}
        onChange={e => update('q', e.target.value || undefined)}
      />

      {hasFilters && (
        <Button variant="ghost" size="sm" className="h-7 gap-1 text-[13px] text-text-muted" onClick={onClear}>
          <XIcon className="h-3 w-3" />
          Clear
        </Button>
      )}
    </div>
  )
}
