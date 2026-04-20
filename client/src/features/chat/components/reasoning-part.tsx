import { useState } from 'react'
import { useI18n } from '@/i18n/use-i18n'
export function ReasoningPart({ part }: { part: any }) {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  return (
    <details open={open} onToggle={e => setOpen((e.target as HTMLDetailsElement).open)}
      className="rounded border bg-muted/40 p-2 text-xs text-muted-foreground">
      <summary className="cursor-pointer">{t('chat.thinking')}</summary>
      <div className="mt-1 whitespace-pre-wrap">{part.text}</div>
    </details>
  )
}
