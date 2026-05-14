import { useEffect, useId, useRef, useState } from 'react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

const DATE_FORMAT_PRESETS = [
  { value: 'yyyy-MM-dd HH:mm:ss', label: 'CN (ISO)', example: '2025-06-15 16:00:00' },
  { value: 'MM/dd/yyyy hh:mm:ss a', label: 'US', example: '06/15/2025 04:00:00 PM' },
  { value: 'dd/MM/yyyy HH:mm:ss', label: 'EU', example: '15/06/2025 16:00:00' },
  { value: 'yyyy年MM月dd日 HH:mm:ss', label: 'CN Long', example: '2025年06月15日 16:00:00' },
]

type Mode = 'preset' | 'custom'

interface DateFormatSelectorProps {
  value: string
  onChange: (value: string) => void
  error?: string
}

export function DateFormatSelector({ value, onChange, error }: DateFormatSelectorProps) {
  const customInputId = useId()
  const isPreset = DATE_FORMAT_PRESETS.some((p) => p.value === value)
  const [mode, setMode] = useState<Mode>(isPreset ? 'preset' : 'custom')
  const [customValue, setCustomValue] = useState(isPreset ? '' : value)
  const debounceRef = useRef<ReturnType<typeof setTimeout>>(null)

  useEffect(() => {
    if (isPreset) {
      setMode('preset')
    } else {
      setMode('custom')
      setCustomValue(value)
    }
  }, [isPreset, value])

  useEffect(() => () => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
  }, [])

  return (
    <div className="space-y-2">
      <div className="grid grid-cols-2 gap-2">
        {DATE_FORMAT_PRESETS.map((preset) => (
          <button
            key={preset.value}
            type="button"
            onClick={() => { setMode('preset'); onChange(preset.value) }}
            className={cn(
              'flex flex-col items-start gap-0.5 rounded-lg border px-3 py-2 text-left transition-colors',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
              mode === 'preset' && value === preset.value
                ? 'border-primary bg-primary/5 text-foreground'
                : 'border-input bg-transparent text-muted-foreground hover:text-foreground hover:border-foreground/30'
            )}
          >
            <span className="text-sm font-medium">{preset.label}</span>
            <span className="text-xs opacity-60">{preset.example}</span>
          </button>
        ))}
        <button
          type="button"
          onClick={() => setMode('custom')}
          className={cn(
            'flex flex-col items-start gap-0.5 rounded-lg border px-3 py-2 text-left transition-colors',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
            mode === 'custom'
              ? 'border-primary bg-primary/5 text-foreground'
              : 'border-input bg-transparent text-muted-foreground hover:text-foreground hover:border-foreground/30'
          )}
        >
          <span className="text-sm font-medium">Custom</span>
          <span className="text-xs opacity-60">Custom format</span>
        </button>
      </div>
      {mode === 'custom' && (
        <div>
          <label htmlFor={customInputId} className="text-xs text-muted-foreground mb-1 block">
            Custom format pattern
          </label>
          <Input
            id={customInputId}
            value={mode === 'custom' && !isPreset ? value : customValue}
            onChange={(e) => {
              const v = e.target.value
              setCustomValue(v)
              if (debounceRef.current) clearTimeout(debounceRef.current)
              debounceRef.current = setTimeout(() => onChange(v), 500)
            }}
            placeholder="yyyy-MM-dd HH:mm:ss"
            className="h-9 w-60 font-mono text-sm"
          />
          {error && <p className="text-destructive text-xs mt-1">{error}</p>}
        </div>
      )}
    </div>
  )
}
