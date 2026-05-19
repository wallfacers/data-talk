import { useState, useMemo, useRef } from 'react'
import { SearchIcon } from 'lucide-react'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

const COMMON_TIMEZONES = [
  'Asia/Shanghai',
  'America/New_York',
  'Europe/London',
  'Asia/Tokyo',
  'UTC',
]

function getAllTimezones(): string[] {
  try {
    const values = Intl.supportedValuesOf?.('timeZone')
    if (!values) return COMMON_TIMEZONES
    return Array.from(new Set(values)).sort() as string[]
  } catch {
    return COMMON_TIMEZONES
  }
}

interface TimezoneSelectorProps {
  value: string
  onChange: (value: string) => void
}

export function TimezoneSelector({ value, onChange }: TimezoneSelectorProps) {
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  const allTimezones = useMemo(() => getAllTimezones(), [])

  const filtered = useMemo(() => {
    if (!search.trim()) return allTimezones
    const q = search.toLowerCase()
    return allTimezones.filter((tz) => tz.toLowerCase().includes(q))
  }, [search, allTimezones])

  const displayValue = value || 'UTC'

  return (
    <Popover open={open} onOpenChange={(v) => { setOpen(v); if (!v) setSearch('') }}>
      <PopoverTrigger>
        <button
          type="button"
          role="combobox"
          aria-expanded={open}
          className={cn(
            'flex h-9 w-60 items-center justify-between rounded-lg border border-input bg-transparent px-3 py-1 text-sm',
            'hover:bg-accent hover:text-accent-foreground',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
            'disabled:cursor-not-allowed disabled:opacity-50'
          )}
        >
          <span className="truncate">{displayValue}</span>
          <SearchIcon className="ml-2 size-3.5 shrink-0 opacity-50" />
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-72 p-0">
        <div className="flex items-center border-b border-border px-3 py-2">
          <SearchIcon className="mr-2 size-3.5 shrink-0 text-muted-foreground" />
          <Input
            ref={inputRef}
            placeholder="Search timezone"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="h-7 border-0 bg-transparent p-0 text-sm focus-visible:ring-0 focus-visible:ring-offset-0"
          />
        </div>
        <div className="max-h-64 overflow-y-auto p-1">
          {!search.trim() && (
            <div className="px-2 py-1.5 text-xs font-medium text-muted-foreground">
              Common
            </div>
          )}
          {!search.trim() &&
            COMMON_TIMEZONES.map((tz) => (
              <button
                key={tz}
                type="button"
                onClick={() => { onChange(tz); setOpen(false) }}
                className={cn(
                  'w-full rounded-sm px-2 py-1.5 text-left text-sm',
                  tz === value && 'bg-accent'
                )}
              >
                {tz}
              </button>
            ))}
          {!search.trim() && (
            <div className="px-2 py-1.5 text-xs font-medium text-muted-foreground">
              All ({allTimezones.length})
            </div>
          )}
          {filtered.map((tz) => (
            <button
              key={tz}
              type="button"
              onClick={() => { onChange(tz); setOpen(false) }}
              className={cn(
                'w-full rounded-sm px-2 py-1.5 text-left text-sm',
                tz === value && 'bg-accent'
              )}
            >
              {tz}
            </button>
          ))}
        </div>
      </PopoverContent>
    </Popover>
  )
}
