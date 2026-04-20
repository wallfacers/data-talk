import { useState } from 'react'
import { useI18n } from '@/i18n/use-i18n'

export function UnknownPart(props: { part: { type: string; [k: string]: unknown } }) {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  return (
    <div className="my-2 rounded border border-dashed border-yellow-500/40 p-2 text-xs">
      <button onClick={() => setOpen(!open)} className="flex items-center gap-2 text-yellow-700 dark:text-yellow-400">
        <span>⚠</span>
        <span>{t('chat.unknownPart', { type: props.part.type }).replace(props.part.type, '')}<code className="font-mono">{props.part.type}</code></span>
      </button>
      {open && (
        <pre className="mt-2 overflow-x-auto text-[11px] text-muted-foreground">{JSON.stringify(props.part, null, 2)}</pre>
      )}
    </div>
  )
}
