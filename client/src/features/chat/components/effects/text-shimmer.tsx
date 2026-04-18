import { useEffect, useRef, useState } from 'react'
import './text-shimmer.css'

const SWAP_MS = 220

export function TextShimmer(props: { text: string; active?: boolean; className?: string; offset?: number }) {
  const active = props.active ?? true
  const [run, setRun] = useState(active)
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  useEffect(() => {
    if (timerRef.current) { clearTimeout(timerRef.current); timerRef.current = undefined }
    if (active) { setRun(true); return }
    timerRef.current = setTimeout(() => { timerRef.current = undefined; setRun(false) }, SWAP_MS)
    return () => { if (timerRef.current) clearTimeout(timerRef.current) }
  }, [active])

  return (
    <span
      data-component="text-shimmer"
      data-active={active ? 'true' : 'false'}
      className={props.className}
      style={{ '--text-shimmer-swap': `${SWAP_MS}ms`, '--text-shimmer-index': String(props.offset ?? 0) } as React.CSSProperties}
      aria-label={props.text}
    >
      <span data-slot="text-shimmer-char">
        <span data-slot="text-shimmer-char-base" aria-hidden="true">{props.text}</span>
        <span data-slot="text-shimmer-char-shimmer" data-run={run ? 'true' : 'false'} aria-hidden="true">{props.text}</span>
      </span>
    </span>
  )
}
