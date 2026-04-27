import { Input } from '@/components/ui/input'
import { Search } from 'lucide-react'
import { useI18n } from '@/i18n/use-i18n'

export function NavTabsSearch({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const { t } = useI18n()
  return (
    <div className="relative">
      <Search aria-hidden className="absolute left-2 top-1/2 size-3.5 -translate-y-1/2 text-text-soft" />
      <Input
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={t('sidebar.tabs.search.placeholder')}
        className="h-7 pl-7 text-xs bg-bg-canvas border-border-default focus-visible:ring-[var(--ring-focus)]"
      />
    </div>
  )
}
