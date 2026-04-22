import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select'

export type SqlLimitValue = 10 | 100 | 1000 | null

type SqlLimitSelectProps = {
  value: SqlLimitValue
  onValueChange: (value: SqlLimitValue) => void
}

const LIMIT_OPTIONS: Array<{ value: SqlLimitValue; label: string }> = [
  { value: null, label: 'No limit' },
  { value: 10, label: '10 rows' },
  { value: 100, label: '100 rows' },
  { value: 1000, label: '1,000 rows' },
]

export function SqlLimitSelect({ value, onValueChange }: SqlLimitSelectProps) {
  const selectedLabel = LIMIT_OPTIONS.find((option) => option.value === value)?.label ?? 'No limit'

  return (
    <Select
      value={value == null ? 'none' : String(value)}
      onValueChange={(nextValue) => {
        if (nextValue === 'none') {
          onValueChange(null)
          return
        }
        if (nextValue === '10' || nextValue === '100' || nextValue === '1000') {
          onValueChange(Number(nextValue) as 10 | 100 | 1000)
        }
      }}
    >
      <SelectTrigger size="sm" aria-label="Execution limit">
        <span className="flex flex-1 text-left">{selectedLabel}</span>
      </SelectTrigger>
      <SelectContent>
        {LIMIT_OPTIONS.map((option) => (
          <SelectItem
            key={option.label}
            value={option.value == null ? 'none' : String(option.value)}
          >
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
