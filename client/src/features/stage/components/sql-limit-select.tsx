import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select'
import { useI18n } from '@/i18n/use-i18n'
import { cn } from '@/lib/utils'

export type SqlLimitValue = 10 | 100 | 1000 | null

type SqlLimitSelectProps = {
  value: SqlLimitValue
  onValueChange: (value: SqlLimitValue) => void
  ariaLabel?: string
  triggerClassName?: string
}

export function SqlLimitSelect({
  value,
  onValueChange,
  ariaLabel,
  triggerClassName,
}: SqlLimitSelectProps) {
  const { t } = useI18n()
  const limitOptions: Array<{ value: SqlLimitValue; label: string }> = [
    { value: null, label: t('stage.limit.none') },
    { value: 10, label: t('stage.limit.rows', { count: 10 }) },
    { value: 100, label: t('stage.limit.rows', { count: 100 }) },
    { value: 1000, label: t('stage.limit.rows', { count: 1000 }) },
  ]
  const selectedLabel = limitOptions.find((option) => option.value === value)?.label ?? t('stage.limit.none')

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
      <SelectTrigger
        size="sm"
        aria-label={ariaLabel ?? t('stage.limit.aria')}
        className={cn(triggerClassName)}
      >
        <span className="flex flex-1 text-left">{selectedLabel}</span>
      </SelectTrigger>
      <SelectContent>
        {limitOptions.map((option) => (
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
