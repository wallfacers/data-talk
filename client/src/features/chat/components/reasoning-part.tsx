import { useState } from 'react'
export function ReasoningPart({ part }: { part: any }) {
  const [open, setOpen] = useState(false)
  return (
    <details open={open} onToggle={e => setOpen((e.target as HTMLDetailsElement).open)}
      className="rounded border bg-muted/40 p-2 text-xs text-muted-foreground">
      <summary className="cursor-pointer">思考中…</summary>
      <div className="mt-1 whitespace-pre-wrap">{part.text}</div>
    </details>
  )
}
