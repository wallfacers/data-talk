import { useEffect, useState, useMemo } from 'react'
import './text-reveal.css'

export function TextReveal(props: { text?: string; className?: string; travel?: number; duration?: number }) {
  const [renderedKey, setRenderedKey] = useState(0)
  const words = useMemo(() => (props.text ?? '').split(/(\s+)/).filter((w) => w.length > 0), [props.text])

  useEffect(() => { setRenderedKey((k) => k + 1) }, [props.text])

  if (!props.text) return null
  return (
    <span
      key={renderedKey}
      data-component="text-reveal"
      className={props.className}
      style={{ '--text-reveal-travel': `${props.travel ?? 25}px`, '--text-reveal-duration': `${props.duration ?? 700}ms` } as React.CSSProperties}
    >
      {words.map((w, i) => (
        <span key={i} data-slot="text-reveal-word" style={{ animationDelay: `${i * 30}ms` }}>{w}</span>
      ))}
    </span>
  )
}
